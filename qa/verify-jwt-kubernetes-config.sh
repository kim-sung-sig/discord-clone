#!/usr/bin/env bash
set -euo pipefail

case "$#:$*" in
  0:) server_dry_run=false ;;
  1:--server-dry-run) server_dry_run=true ;;
  *)
    printf 'usage: %s [--server-dry-run]\n' "$0" >&2
    exit 2
    ;;
esac

command -v kubectl >/dev/null || {
  printf 'kubectl is required\n' >&2
  exit 1
}

root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
config_dir="$root/infra/kubernetes/jwt-config"
test_overlay="$config_dir/overlays/test"
secret_template="$root/infra/kubernetes/jwt-config/identity-private-key-secret.example.yaml"
identity_patch="$root/infra/kubernetes/jwt-config/identity-volume-mount.patch.yaml"
consumer_patch="$root/infra/kubernetes/jwt-config/consumer-volume-mount.patch.yaml"
rendered="$(mktemp)"
trap 'rm -f "$rendered"' EXIT

kubectl kustomize "$test_overlay" >"$rendered"
config_map="$rendered"

if ! awk '
  /^[[:space:]]*kind:[[:space:]]*/ {
    if ($2 != "ConfigMap" || ++count > 1) bad = 1
  }
  END { exit bad || count != 1 }
' "$config_map"; then
  printf 'test overlay must render exactly one ConfigMap and no Secret\n' >&2
  exit 1
fi

if rg -q '^[[:space:]]*stringData:' "$config_map"; then
  printf 'test overlay must not render Secret stringData\n' >&2
  exit 1
fi

for key in \
  'discord.auth.jwt.issuer:' \
  'discord.auth.jwt.audience:' \
  'discord.auth.jwt.key-id:' \
  'discord.auth.jwt.private-key-location:' \
  'discord.auth.jwt.public-key-locations.'; do
  rg -Fq "$key" "$config_map" || {
    printf 'missing ConfigMap key: %s\n' "$key" >&2
    exit 1
  }
done

if rg -q 'PRIVATE KEY|replace-before-deploy|REPLACE_BEFORE_DEPLOY' "$config_map"; then
  printf 'ConfigMap contains a private-key marker or deployment placeholder\n' >&2
  exit 1
fi

if rg -q '^[[:space:]]*(data|stringData):' "$secret_template"; then
  printf 'Secret reference template must not contain data or stringData\n' >&2
  exit 1
fi

rg -Fq 'configMap:' "$identity_patch" \
  && rg -Fq 'name: discord-jwt-public-config' "$identity_patch" \
  && awk '
    function check() {
      if (block ~ /mountPath:[[:space:]]*\/etc\/discord-secret/ && block ~ /readOnly:[[:space:]]*true/) mount_ok = 1
      if (block ~ /secret:/ && block ~ /secretName:[[:space:]]*discord-identity-jwt-private-key/) volume_ok = 1
    }
    /^[[:space:]]*-[[:space:]]+name:[[:space:]]+discord-identity-jwt-private-key[[:space:]]*$/ {
      check(); block = $0; active = 1; next
    }
    active && /^[[:space:]]*-[[:space:]]+name:/ { check(); block = ""; active = 0 }
    active { block = block "\n" $0 }
    END { check(); exit !(mount_ok && volume_ok) }
  ' "$identity_patch" || {
  printf 'identity patch must read-only mount the ConfigMap and private-key Secret\n' >&2
  exit 1
}

if rg -Fq 'discord-identity-jwt-private-key' "$consumer_patch"; then
  printf 'consumer patch must not reference the private-key Secret\n' >&2
  exit 1
fi

if "$server_dry_run"; then
  kubectl apply --dry-run=server -k "$test_overlay"
fi

printf 'JWT Kubernetes configuration verification passed\n'

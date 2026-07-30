# Kubernetes JWT 설정 계약

`kubectl kustomize infra/kubernetes/jwt-config`는 배포 전 template인 공개 JWT 설정 ConfigMap만 렌더한다. `jwt-public.pem`과 `replace-before-deploy`는 배포 전 실제 공개키와 활성 `kid`로 교체한다. private PEM, 토큰, password는 이 저장소에 넣지 않는다. `discord.auth.jwt.private-key-location`은 Secret 값이 아닌 identity 전용 mount 경로이며 consumer는 이 설정을 무시한다.

`overlays/test`는 검증 전용 공개 Ed25519 키와 `kid=test-ed25519`로 base ConfigMap을 교체한다. 운영 배포에 사용하지 않으며 private key나 Secret 값도 포함하지 않는다.

## Identity Secret

운영 Secret은 클러스터의 Secret 관리 절차로 생성한다. 예를 들어 private PEM이 안전한 로컬 경로에 있을 때 다음처럼 생성한다.

```bash
kubectl create secret generic discord-identity-jwt-private-key \
  --from-file=jwt-private.pem=/secure/path/jwt-private.pem
```

`identity-private-key-secret.example.yaml`은 이름과 type만 고정하는 참조 템플릿이며 `data`나 `stringData`를 포함하지 않는다.

## Deployment overlay

현재 base에는 Deployment가 없으므로 두 `*.patch.yaml`은 `kustomization.yaml`의 resource나 patch에 포함하지 않는다. 실제 overlay에서 대상 Deployment와 컨테이너 이름에 맞춰 patch의 `metadata.name` 및 `containers[].name`을 변경한 뒤 strategic-merge patch로 연결한다.

- `identity-volume-mount.patch.yaml`: ConfigMap을 `/etc/discord-config`, Secret `discord-identity-jwt-private-key`를 `/etc/discord-secret`에 read-only mount한다.
- `consumer-volume-mount.patch.yaml`: ConfigMap만 `/etc/discord-config`에 read-only mount한다. consumer에는 private-key Secret을 연결하지 않으며 `discord.auth.jwt.private-key-location`을 사용하지 않는다.

## 검증

CI와 로컬 계약 검증은 test overlay를 대상으로 한다.

```bash
qa/verify-jwt-kubernetes-config.sh
```

선택한 클러스터가 있을 때만 server-side dry-run을 추가한다. test overlay에는 Secret resource가 없으므로 운영 Secret을 요구하지 않는다.

```bash
qa/verify-jwt-kubernetes-config.sh --server-dry-run
```

스크립트는 test overlay ConfigMap의 private-key marker와 배포 전 placeholder, Secret 참조 template의 `data`/`stringData`, consumer의 private-key Secret 참조를 거부하고 `kubectl kustomize infra/kubernetes/jwt-config/overlays/test`를 실행한다. 운영 overlay는 실제 공개키와 활성 `kid`를 제공한 뒤 같은 계약을 만족해야 한다.

## 키 순환

1. 새 공개키와 새 `kid`를 ConfigMap에 배포한다.
2. identity-service의 활성 `kid`를 새 값으로 변경한다.
3. access-token TTL이 끝날 때까지 기다린다.
4. 이전 공개키와 이전 `kid`를 ConfigMap에서 제거한다.

# Kubernetes JWT 설정 전달 구현 계획

> **에이전트 작업자용:** 이 계획은 task 단위로 구현한다. 단계별 체크박스를 갱신한다.

**목표:** Kubernetes ConfigMap과 identity 전용 Secret mount로 Ed25519 JWT 설정을 모든 런타임에 안전하게 전달하고, cluster 없이도 manifest 계약을 검증한다.

**아키텍처:** Spring Boot 서비스는 `/etc/discord-config/`의 `configtree:`를 선택적으로 import한다. Kustomize base는 공개 JWT 설정 ConfigMap과 private key Secret 계약을 분리하며, 검증 스크립트가 private key의 ConfigMap 유입과 consumer Secret mount를 차단한다.

**기술:** Spring Boot 3.5 `configtree:`, Kubernetes ConfigMap/Secret, Kustomize, Git Bash shell.

---

### Task 1: 모든 JWT 런타임의 Config Tree import

**파일:**

- 수정: `backend/boot/src/main/resources/application.yml`
- 수정: `backend/services/identity/src/main/resources/application.yml`
- 수정: `backend/services/message/src/main/resources/application.yml`
- 수정: `backend/services/websocket/src/main/resources/application.yml`
- 수정: `backend/services/community/src/main/resources/application.yml`
- 테스트: `backend/boot/src/test/java/com/example/discord/auth/AuthConfigurationTest.java`

- [ ] **Step 1: 실패하는 configtree import 계약 테스트 작성**

`AuthConfigurationTest`에 임시 설정 디렉터리를 만들고 `discord.auth.jwt.*` 키 파일을 둔 뒤, `spring.config.import=optional:configtree:<dir>/`가 `BearerTokenVerifier`를 생성하는 테스트를 추가한다.

```java
assertThat(context).hasSingleBean(BearerTokenVerifier.class);
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run:

```bash
cd backend && ./gradlew :backend:boot:test --tests com.example.discord.auth.AuthConfigurationTest
```

Expected: configtree import가 없어 context가 JWT property를 bind하지 못해 실패.

- [ ] **Step 3: 최소 설정 import 추가**

모든 대상 `application.yml`의 `spring` 아래에 다음을 추가한다.

```yaml
  config:
    import: optional:configtree:/etc/discord-config/
```

identity-service의 private-key file location은 ConfigMap key `discord.auth.jwt.private-key-location`이 제공한다. 이 값은 Secret 값이 아니며, private PEM은 `/etc/discord-secret/` Secret mount에만 존재한다.

- [ ] **Step 4: GREEN 확인**

Run:

```bash
cd backend && ./gradlew :backend:boot:test --tests com.example.discord.auth.AuthConfigurationTest
```

Expected: PASS.

### Task 2: Kubernetes JWT config base와 mount 계약 작성

**파일:**

- 생성: `infra/kubernetes/jwt-config/kustomization.yaml`
- 생성: `infra/kubernetes/jwt-config/jwt-public-configmap.yaml`
- 생성: `infra/kubernetes/jwt-config/identity-private-key-secret.example.yaml`
- 생성: `infra/kubernetes/jwt-config/identity-volume-mount.patch.yaml`
- 생성: `infra/kubernetes/jwt-config/consumer-volume-mount.patch.yaml`
- 생성: `infra/kubernetes/jwt-config/README.md`

- [ ] **Step 1: Kustomize render 실패 테스트 작성**

`qa/verify-jwt-kubernetes-config.sh`가 없는 상태에서 다음 명령을 실행한다.

```bash
kubectl kustomize infra/kubernetes/jwt-config
```

Expected: directory 또는 `kustomization.yaml` 부재로 실패.

- [ ] **Step 2: 공개/비밀 설정 manifest 작성**

`jwt-public-configmap.yaml`은 다음 키만 가진다. `jwt-public.pem` 값은 placeholder이며 production key를 commit하지 않는다.

```yaml
data:
  discord.auth.jwt.issuer: discord-identity
  discord.auth.jwt.audience: discord-api
  discord.auth.jwt.key-id: replace-before-deploy
  discord.auth.jwt.public-key-locations.replace-before-deploy: file:/etc/discord-config/jwt-public.pem
  jwt-public.pem: |-
    -----BEGIN PUBLIC KEY-----
    REPLACE_BEFORE_DEPLOY
    -----END PUBLIC KEY-----
```

`identity-private-key-secret.example.yaml`은 `stringData`/`data`를 갖지 않는 Secret reference template이다. README는 운영 Secret 생성 명령을 예시 값 없이 설명한다. identity patch만 `discord-identity-jwt-private-key`를 `/etc/discord-secret`에 `readOnly: true`로 mount한다. consumer patch는 `discord-jwt-public-config`만 `/etc/discord-config`에 mount한다.

- [ ] **Step 3: Kustomize entrypoint 작성**

`kustomization.yaml`은 public ConfigMap만 resource로 포함한다. Secret template과 Deployment patch는 base resource가 없으므로 README에서 적용 대상 Deployment에 overlay patch로 연결하는 계약 문서로 유지한다.

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - jwt-public-configmap.yaml
```

- [ ] **Step 4: GREEN 확인**

Run:

```bash
kubectl kustomize infra/kubernetes/jwt-config
```

Expected: ConfigMap YAML render 성공.

### Task 3: Kubernetes JWT 설정 검증 스크립트와 문서 계약

**파일:**

- 생성: `qa/verify-jwt-kubernetes-config.sh`
- 수정: `docs/superpowers/specs/2026-07-30-kubernetes-jwt-config-delivery-design.md`
- 수정: `C:\tmp\ObsidianVaults\discord-llm-wiki\wiki\Backend Architecture.md`
- 수정: `C:\tmp\ObsidianVaults\discord-llm-wiki\log.md`

- [ ] **Step 1: 실패하는 manifest 보안 검사 작성**

스크립트는 test overlay로 렌더한 ConfigMap의 private-key marker와 배포 전 placeholder, Secret 참조 템플릿의 Secret 값 블록을 분리해 검사한다. base ConfigMap은 배포 전 template이므로 이 검증 대상이 아니다.

```bash
kubectl kustomize infra/kubernetes/jwt-config/overlays/test >/tmp/jwt-kustomize.yaml
! rg -q 'PRIVATE KEY|replace-before-deploy|REPLACE_BEFORE_DEPLOY' /tmp/jwt-kustomize.yaml
! rg -q '^\s*(data|stringData):' infra/kubernetes/jwt-config/identity-private-key-secret.example.yaml
```

test overlay ConfigMap에 private key marker 또는 배포 전 placeholder가 있거나 Secret 참조 템플릿에 `data`/`stringData` 블록이 있으면 non-zero로 종료한다. ConfigMap의 공개 설정 `data` 블록은 허용한다. 초기에는 스크립트가 없으므로 실행이 실패해야 한다.

- [ ] **Step 2: 최소 검증 스크립트 구현**

스크립트는 다음을 순서대로 수행한다.

```bash
kubectl kustomize infra/kubernetes/jwt-config/overlays/test >/tmp/jwt-kustomize.yaml
rg -q 'discord.auth.jwt.issuer:' /tmp/jwt-kustomize.yaml
rg -q 'discord.auth.jwt.audience:' /tmp/jwt-kustomize.yaml
! rg -q 'PRIVATE KEY|replace-before-deploy|REPLACE_BEFORE_DEPLOY' /tmp/jwt-kustomize.yaml
! rg -q '^\s*(data|stringData):' infra/kubernetes/jwt-config/identity-private-key-secret.example.yaml
rg -q 'discord-identity-jwt-private-key' infra/kubernetes/jwt-config/identity-volume-mount.patch.yaml
! rg -q 'discord-identity-jwt-private-key' infra/kubernetes/jwt-config/consumer-volume-mount.patch.yaml
```

`--server-dry-run` 인자가 있을 때만 `kubectl apply --dry-run=server -k infra/kubernetes/jwt-config/overlays/test`를 추가 실행한다. 선택되고 연결 가능한 cluster가 필요하며 Secret은 필요하지 않다.

- [ ] **Step 3: GREEN 및 기존 JWT 회귀 확인**

base ConfigMap은 배포 전 placeholder를 보존한다. `infra/kubernetes/jwt-config/overlays/test/`는 private key나 Secret 값 없이 공개 Ed25519 테스트키와 `kid=test-ed25519`로 ConfigMap을 교체하며, QA 스크립트와 선택적 server dry-run은 이 overlay를 대상으로 한다.

Run:

```bash
qa/verify-jwt-kubernetes-config.sh
cd backend && ./gradlew :backend:modules:identity:test :backend:services:identity:test :backend:services:message:test --tests com.example.discord.messageservice.BearerTokenVerifierTest :backend:services:websocket:test --tests com.example.discord.websocketservice.BearerTokenVerifierTest :backend:services:community:test --tests com.example.discord.communityservice.BearerTokenVerifierTest
```

Expected: 모두 성공. `--server-dry-run`은 선택되고 연결 가능한 cluster가 있을 때만 실행하며 test overlay만 apply하고 Secret은 필요하지 않다.

- [ ] **Step 4: 운영 문서 최종화**

README와 설계문서에 key rotation 순서를 기록한다: new public key 배포 → identity active `kid` 변경 → access-token TTL 대기 → old key 제거. Wiki에는 변경된 검증 명령과 K8s Secret RBAC/암호화-at-rest 잔여 위험만 기록한다.

- [ ] **Step 5: task-owned 파일만 커밋·push**

```bash
git add backend/boot/src/main/resources/application.yml backend/services/*/src/main/resources/application.yml infra/kubernetes/jwt-config qa/verify-jwt-kubernetes-config.sh docs/superpowers/specs/2026-07-30-kubernetes-jwt-config-delivery-design.md
git commit -m "feat(infra): deliver JWT config through Kubernetes"
git push origin main
```

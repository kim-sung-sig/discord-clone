# T202 Kubernetes JWT QA 보완 Merge Review

## 대상

- Branch: `task_T202-jwt-k8s-review-fix`
- Base: `main`의 `0d6bbf4`
- 대상 변경: `qa/verify-jwt-kubernetes-config.sh`, `infra/kubernetes/jwt-config/README.md`

## 승인된 근거

- 원인: QA가 test overlay만 검사해 base ConfigMap의 private-key/Secret 유입을 놓쳤고, Git 실행권한과 rollout 단계가 누락됐다.
- 수정: base render에서 private marker·Secret·`stringData`를 차단하고, script Git mode를 `100755`로 고정했으며, key rotation 각 변경 후 rollout을 문서화했다.

## 검수

- Preset: Security Review
- Score: 100/100
- P0/P1/P2: 없음
- Acceptance: 승인

## 검증

- RED: base ConfigMap에 synthetic `PRIVATE KEY` marker를 넣어도 기존 QA가 성공함을 재현.
- GREEN: 수정 QA가 같은 marker를 non-zero로 거부.
- `bash qa/verify-jwt-kubernetes-config.sh` 성공.
- `bash -n qa/verify-jwt-kubernetes-config.sh` 성공.
- `git diff --check` 성공.
- `cd backend && ./gradlew :backend:modules:identity:test :backend:services:identity:test :backend:boot:test --tests com.example.discord.auth.AuthConfigurationTest` 성공.

## 잔여 위험

- `--server-dry-run`은 연결 가능한 Kubernetes cluster가 있어야 실행한다. 이번 task에서는 local/offline 계약만 검증했다.

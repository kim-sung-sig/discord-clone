# docs/ 를 Obsidian Vault로 열기

`docs/` 폴더 자체가 Obsidian vault입니다. Obsidian → **Open folder as vault** → 이 폴더(`docs/`) 선택.

리포 루트가 아니라 `docs/`를 선택하세요. 루트를 잡으면 `node_modules` 안의 마크다운까지 인덱싱합니다.

## 구조

PDCA 문서는 `<slug>.<phase>.md` 규칙을 따르며(`phase` = plan / design / analysis / report / feedback),
각 문서 상단의 frontmatter와 `🧭` 블록이 같은 slug의 다른 단계 문서와 허브를 연결합니다.

```yaml
---
slug: T01-identity-user
ticket: T01
phase: design
hub: "[[T01-identity-user]]"
---
```

- `hubs/<slug>.md` — slug당 하나의 허브 노트. 5단계 문서를 한 표로 모으고, 티켓 ID(`T01` 등)를 alias로 가집니다.
  본문에서 `[[T01]]`처럼 티켓 ID만 적어도 허브로 연결됩니다. (같은 ID를 여러 slug가 쓰는 5건은 alias 없음: T19, T23, T32, T120, T171)
- 그래프뷰 색상: 허브 = 보라, plan = 파랑, design = 초록, analysis = 노랑, report = 주황, feedback = 빨강.
  한 클러스터에 빠진 색이 곧 빠진 PDCA 단계입니다.

## 새 문서를 추가할 때

파일명 규칙만 지키고 상단 블록은 생성 스크립트로 채우거나 위 형식을 그대로 복사하세요.
링크 대상이 없는 단계는 `~~report~~`처럼 취소선 텍스트로 두어 그래프에 유령 노드가 생기지 않게 합니다.

## 추천 플러그인 (선택)

- **Dataview** — 예: 아직 report가 없는 티켓 목록

  ```dataview
  TABLE ticket, phase FROM "05-feedback" WHERE !contains(file.outlinks.file.name, slug + ".report")
  ```

- **Graph Analysis** — 명시적 링크 없이도 유사도 기반 연결을 제안합니다.

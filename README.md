# AI Code Review Server

GitHub Push 이벤트가 발생하면 변경된 코드와 함께 관련도가 높은 코드를 AI가 분석하여 코드 리뷰를 생성하고 Slack으로 전달하는 백엔드 서비스입니다.

---

## 프로젝트 소개

개인 프로젝트를 진행하면서 코드 리뷰를 받기 위해 Pull Request를 생성하거나 별도 AI 도구에 코드를 복사하여 붙여넣는 과정이 반복적으로 발생했습니다.

이 프로젝트는 이러한 과정을 자동화하기 위해 시작되었습니다.

GitHub Repository에 Push가 발생하면 GitHub Actions가 변경된 Diff를 추출하여 Review Server로 전달하고, Review Server는 AI를 통해 코드 리뷰를 생성한 뒤 Slack으로 전달합니다.

Push 요청은 Redis Stream 기반 큐에 적재되어 비동기로 처리되며, 실패한 리뷰는 자동 재시도 후 DLQ(Dead Letter Queue)로 이동해 안전하게 관리됩니다. 생성된 리뷰는 PostgreSQL에 이력으로 저장되어 Slack 채널을 뒤지지 않아도 API로 언제든 조회할 수 있습니다.

여러 Repository의 Push를 함께 수신하며, Repository별로 지정된 Slack 채널로 리뷰 결과를 분리하여 전달합니다.

변경된 클래스와 연관된 프로젝트 내 다른 클래스를 pgvector 기반 유사도 검색으로 함께 조회하여 프로젝트 구조를 이해하는 RAG(Retrieval Augmented Generation) 기반 리뷰 시스템입니다.

**RAG 인덱싱은 GitHub Actions가 변경/전체 파일의 원본 코드를 서버로 직접 전송하는 방식으로 동작합니다.** 서버가 로컬 디스크나 GitHub API에서 파일을 가져오지 않으므로, 다른 사람이 원격 레포에 Push하는 경우에도 정상적으로 인덱싱됩니다.

---

## 프로젝트 아키텍처

### 전체 흐름

```text
GitHub Repository A, B, ... (push)
      │
      ├─ GitHub Actions: 리뷰 요청 (diff + 커밋 정보)
      │       ↓
      │   Review Server (Controller) → Redis Stream (review-stream)
      │
      └─ GitHub Actions: 인덱싱 요청 (변경/전체 파일의 repoName, filePath, content)
              ↓
          Review Server (Controller) → Redis Stream (indexing-stream)

review-stream ─→ ReviewStreamWorker
                     ↓ (인덱싱 완료 여부 확인 → RAG 컨텍스트 조회)
                  OpenRouter (리뷰 생성)
                     ↓
                  Slack 전송 (Repository별 채널 분기)
                     ↓ (성공 시)
                  PostgreSQL (Review History 저장)
                     ↓
                  ACK

indexing-stream ─→ IndexingStreamWorker
                       ↓
                    시그니처 추출 → OpenRouter 임베딩 → pgvector upsert
                       ↓
                    ACK

실패 시 (review-stream):
Worker → Retry (최대 5회, PEL 기반 재처리)
       → 초과 시 DlqService가 DLQ(review-dlq)로 이동 + Slack 실패 알림
       → DLQ 목록 조회 / 수동 재처리 API 제공
```

### 컴포넌트별 역할

#### 두 개의 독립된 Redis Stream

리뷰 생성과 코드 인덱싱은 서로 다른 책임을 가지므로 별도의 Stream(`review-stream`, `indexing-stream`)과 Worker(`ReviewStreamWorker`, `IndexingStreamWorker`)로 분리되어 있습니다. 인덱싱은 실패하더라도 다음 Push 때 자연히 재시도되는 성격이라 리뷰만큼 무거운 재시도/DLQ 정책을 두지 않고, 실패 시 로그만 남기고 ACK 처리합니다.

#### ReviewStreamWorker — 인덱싱 완료 대기 게이트

Redis Stream에 저장된 리뷰 요청을 주기적으로 소비합니다. Diff에서 변경된 파일 경로를 추출한 뒤, 해당 파일들이 이번 Push 이후 실제로 인덱싱(재임베딩)됐는지 임베딩된 벡터의 `updated_at` 필드와 메시지 발행 시각을 비교해 확인합니다. 아직 인덱싱이 끝나지 않았다면 ACK하지 않고 PEL에 남겨 재확인 대상으로 삼습니다.

인덱싱 대기 판단은 처리 실패 재시도와 동일한 PEL 카운터(`deliveryCount` 기반)를 공유하지만, 인덱싱 대기 임계값(`MAX_INDEXING_WAIT_RETRY = 3`)이 DLQ 이동 임계값(`MAX_RETRY = 5`)보다 낮게 설정되어 있습니다. 그래서 인덱싱이 계속 늦어지더라도 그 전에 컨텍스트 없이(또는 일부만으로) 리뷰가 먼저 진행되고, 인덱싱 지연만으로 DLQ까지 이동하지는 않습니다.

#### IndexingStreamWorker

`indexing-stream`을 소비해 `CodeIndexingService`의 재인덱싱 메서드를 호출하는 얇은 컴포넌트입니다. GitHub Actions가 전송한 파일 내용을 그대로 받아 시그니처 추출·임베딩·upsert까지 수행하며, 재시도/DLQ 없이 처리합니다.

#### RAG 인덱싱 · 컨텍스트 검색 (CodeIndexingService / RagContextService)

전달받은 파일 내용에서 클래스/필드/메서드 시그니처(구현 로직 제외)를 추출해 임베딩 벡터로 변환한 뒤 pgvector에 upsert합니다. 인덱싱 트리거(최초 전체 인덱싱 vs Push별 재인덱싱)에 따른 차이는 아래 [코드 인덱싱 흐름](#코드-인덱싱-흐름-최초-인덱싱-vs-재인덱싱) 표를 참고하세요. 변경 파일의 벡터를 기준으로 같은 레포 내에서 코사인 거리 기반 유사도 검색을 수행해, 연관성 높은 클래스의 시그니처를 리뷰 컨텍스트로 제공합니다.

#### DlqService

`review-stream` 재시도 실패 요청을 DLQ에 적재하고 Slack으로 알리며, DLQ 조회 API와 개별 항목 수동 재처리 API(중복 처리 방지 락 포함)를 제공합니다. `indexing-stream`은 별도의 DLQ 정책을 두지 않습니다.

#### SlackClient / SlackProperties

Repository별 Slack 채널 분기를 담당합니다. `slack.webhooks` 맵에 등록된 Repository(`owner/repo` 형식)가 있으면 해당 채널로, 없으면 기본 webhook-url로 fallback하여 전송합니다.

#### Review History

성공적으로 생성된 리뷰(OpenRouter 응답에 실제 토큰 사용량이 있는 경우)는 PostgreSQL에 저장됩니다.

---

### 코드 인덱싱 흐름 (최초 인덱싱 vs 재인덱싱)

| | 최초 전체 인덱싱 | Push마다 재인덱싱 |
|---|---|---|
| 트리거 | `workflow_dispatch` (수동, 온보딩 시 1회) | `push` (자동) |
| 대상 | 레포 내 전체 `.java` (`src/main` 기준, 테스트 제외) | 이번 커밋에서 변경된 `.java` 파일 |
| 전송 방식 | 파일마다 `{repoName, filePath, content}` 요청을 반복 전송 | 동일 |
| 엔드포인트 | `POST /api/v1/index` (공용) | 동일 |
| 서버 처리 | `IndexingStreamWorker` → `CodeIndexingService.reindexFile` (upsert, 멱등) | 동일 |

두 경우 모두 서버 입장에서는 "파일 하나 + 내용"을 받아 upsert하는 동일한 처리이며, 최초 인덱싱과 매 Push의 차이는 트리거 방식과 Actions가 순회하는 파일 범위뿐입니다. 서버는 로컬 디스크나 GitHub API에서 파일을 조회하지 않으므로, 다른 사람이 소유한 원격 레포에 Push가 발생해도 인덱싱이 정상 동작합니다.

---

## 주요 기능

내부 동작의 자세한 설명은 [프로젝트 아키텍처](#프로젝트-아키텍처)를 참고하세요.

- **GitHub Push 기반 자동 리뷰** — Push 시 Actions가 Diff·commitId·runId를 전달해 리뷰를 시작합니다.
- **AI 코드 리뷰** — OpenRouter API로 심각도([HIGH]/[MEDIUM]/[LOW]) 기준 리뷰를 생성하고, 토큰 사용량을 이력에 함께 저장합니다.
- **RAG 기반 코드 인덱싱 및 컨텍스트 검색** — 변경 파일의 인덱싱 완료(freshness) 여부를 확인한 뒤, 유사도 검색으로 찾은 연관 클래스 시그니처를 Diff와 함께 리뷰 프롬프트에 반영합니다.
- **Slack 알림 (Repository별 채널 분기)** — 지정된 채널이 없으면 기본 채널로 전송합니다.
- **대용량 Diff 보호** — 1000줄 이상의 Diff는 리뷰를 생략하고 알림만 전송합니다.
- **비동기 처리 (Redis Stream, 리뷰/인덱싱 분리)** — 두 책임의 재시도/실패 정책을 독립적으로 관리해 GitHub Actions Timeout을 방지합니다.
- **자동 재시도 (Retry)** — `review-stream`은 PEL 기반으로 최대 5회까지 자동 재시도 후 DLQ로 이동합니다. `indexing-stream`은 재시도 없이 처리되며, 실패해도 다음 Push 때 자연 복구됩니다.
- **DLQ 관리 및 수동 재처리** — 재시도 실패 요청은 DLQ로 이동해 보관하며, 목록 조회 및 개별 항목 수동 재처리 API를 제공합니다.
- **Multi Repository 지원** — 여러 Repository의 Push를 하나의 서버가 함께 수신하며, Repository별로 Slack 채널을 분리합니다.
- **요청 추적 로깅** — Repository 단위 MDC 로깅으로 요청 흐름을 추적하고, OpenRouter 응답 시간·토큰 사용량을 로깅합니다.

### API

| Method | Endpoint | 설명 |
|---|---|---|
| POST | `/api/v1/index` | 인덱싱 요청 (indexing-stream에 등록, 최초/변경 파일 공용) |
| POST | `/api/v1/reviews` | 리뷰 요청 (review-stream에 등록) |
| GET | `/api/v1/reviews` | 리뷰 이력 목록 조회 (페이징) |
| GET | `/api/v1/reviews?repository={repo}` | 레포지토리별 리뷰 이력 조회 (페이징) |
| GET | `/api/v1/reviews/{id}` | 리뷰 단건 조회 |
| GET | `/api/v1/reviews/failed` | DLQ(실패한 리뷰) 목록 조회 (페이징) |
| POST | `/api/v1/reviews/{recordId}/retry` | DLQ 항목 수동 재처리 |

---

## 기술 스택

### Backend
- Java 17
- Spring Boot 4.1
- Spring Data JPA
- Lombok
- JavaParser (시그니처 추출)

### AI
- OpenRouter API (리뷰 생성 및 임베딩, 무료 모델 사용)

### DevOps
- GitHub Actions (`push` / `workflow_dispatch` 이중 트리거)
- Docker Compose

### Messaging
- Slack Webhook (Repository별 채널 분기)

### Queue
- Redis Stream (리뷰용/인덱싱용 분리, Consumer Group + PEL 기반 Retry/DLQ)

### Database
- PostgreSQL 16 (Review History 저장)

### Vector Search
- pgvector (`pgvector/pgvector:pg16` 이미지, Hibernate Vector 연동)

---

## 향후 개선 예정

- Top-K 축소 전략 적용 (Top5 → Top3 → Top1) 및 프롬프트 토큰 기준 컨텍스트 관리
- RAG 컨텍스트 반영 리뷰 품질에 대한 통합 테스트
- run_id 기반 중복 요청 차단 (실제 중복 발생 시 도입)
- DLQ 항목을 원본 Review Stream ID(originalId) 기준으로 조회/재처리하도록 개선
- Page 응답 직렬화 안정화 (PagedModel 적용)
- GitHub Actions에서 push 1건에 포함된 여러 커밋을 모두 반영하도록 Diff 추출 로직 개선

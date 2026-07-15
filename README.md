# AI Code Review Server

GitHub Push 이벤트가 발생하면 변경된 코드를 AI가 분석하여 코드 리뷰를 생성하고 Slack으로 전달하는 백엔드 서비스입니다.

---

## 프로젝트 소개

개인 프로젝트를 진행하면서 코드 리뷰를 받기 위해 Pull Request를 생성하거나 별도 AI 도구에 코드를 복사하여 붙여넣는 과정이 반복적으로 발생했습니다.

이 프로젝트는 이러한 과정을 자동화하기 위해 시작되었습니다.

GitHub Repository에 Push가 발생하면 GitHub Actions가 변경된 Diff를 추출하여 Review Server로 전달하고, Review Server는 AI를 통해 코드 리뷰를 생성한 뒤 Slack으로 전달합니다.

Push 요청은 Redis Stream 기반 큐에 적재되어 비동기로 처리되며, 실패한 리뷰는 자동 재시도 후 DLQ(Dead Letter Queue)로 이동해 안전하게 관리됩니다. 생성된 리뷰는 PostgreSQL에 이력으로 저장되어 Slack 채널을 뒤지지 않아도 API로 언제든 조회할 수 있습니다.

여러 Repository의 Push를 함께 수신하며, Repository별로 지정된 Slack 채널로 리뷰 결과를 분리하여 전달합니다.

향후에는 단순 Diff 기반 리뷰를 넘어 프로젝트 구조까지 이해하는 RAG(Retrieval Augmented Generation) 기반 리뷰 시스템으로 확장하는 것을 목표로 합니다.

---

## 프로젝트 아키텍처

### 현재 아키텍처

```text
GitHub Repository A, B, ... (push)
      ↓
GitHub Actions (Repository별 workflow)
      ↓ (Diff + commitId + runId 전송)
Review Server (Controller)
      ↓ (job 등록, XADD)
Redis Stream (review-stream)
      ↓
Worker (Scheduled Consumer)
      ↓ (Review 요청)
OpenRouter
      ↓ (Review + 토큰 사용량 반환)
Worker
      ↓
Slack 전송 (Repository별 채널 분기)
      ↓ (성공 시)
PostgreSQL (Review History 저장)
      ↓
ACK

실패 시:
Worker → Retry (최대 5회, PEL 기반 재처리)
       → 초과 시 DlqService가 DLQ(review-dlq)로 이동 + Slack 실패 알림
       → DLQ 목록 조회 / 수동 재처리 API 제공
```

**Worker** :
Redis Stream에 저장된 리뷰 요청을 주기적으로 소비(Consume)하여 AI 리뷰를 생성하는 백그라운드 컴포넌트입니다. 처리에 실패한 메시지는 ACK 하지 않고 PEL(Pending Entries List)에 남겨, 별도 스케줄러가 idle 시간이 지난 메시지를 재할당(XCLAIM)하여 재시도합니다. 재시도 판단(몇 번째 시도인지, DLQ로 넘길지)은 Worker가 담당하고, 실제 DLQ 적재와 실패 알림은 DlqService에 위임합니다.

**DlqService** :
DLQ 관련 로직을 전담하는 컴포넌트입니다. 재시도를 모두 실패한 요청을 DLQ 스트림에 적재하고 실패를 Slack으로 알리며, DLQ 조회 API와 개별 항목 수동 재처리 API(중복 처리 방지 락 포함)를 제공합니다.

**SlackClient / SlackProperties** :
Repository별 Slack 채널 분기를 담당합니다. `slack.webhooks` 맵에 등록된 Repository(`owner/repo` 형식)가 있으면 해당 채널로, 없으면 기본 webhook-url로 fallback하여 전송합니다. 채널 설정 누락이 재시도/DLQ 로직에 영향을 주지 않도록 예외를 던지지 않고 fallback으로 처리합니다.

**Review History** :
성공적으로 생성된 리뷰(OpenRouter 응답에 실제 토큰 사용량이 있는 경우)는 PostgreSQL에 저장됩니다. Slack 전송 이후 저장하는 구조로, 저장 이력을 통해 최근 리뷰, 레포지토리별 리뷰, 특정 리뷰 단건을 API로 조회할 수 있습니다.

---

### 향후 아키텍처

```text
GitHub Repository (push)
      ↓
GitHub Actions
      ↓
Review Server
      ↓ (job 등록)
Redis Stream
      ↓
Worker
      ↓
RAG Context Search
      ↓
OpenRouter
      ↓ (Review 반환)
Worker
      ↓
Review History 저장
Slack 전송
```

---

## 주요 기능

### GitHub Push 기반 자동 리뷰

GitHub Push 발생 시 Actions가 변경사항 Diff와 커밋 식별자(commitId), 실행 식별자(runId)를 추출해 Review Server로 전달 후 리뷰 시작

### AI 코드 리뷰

OpenRouter API를 활용하여 심각도([HIGH], [MEDIUM], [LOW]) 기준으로 코드 리뷰 생성. 응답에 포함된 토큰 사용량(promptTokens, completionTokens)을 추출해 이력에 함께 저장

### Slack 알림 (Repository별 채널 분기)

리뷰 결과를 Slack 채널로 전송. Repository별로 지정된 채널이 있으면 해당 채널로, 없으면 기본 채널로 전송. 검토할 변경사항이 없는 경우에도 안내 메시지 전송

### 대용량 Diff 보호

토큰 비용 과다 소모 방지를 위해 1000줄 이상의 Diff는 리뷰를 생략하고 알림만 전송

### 비동기 처리 (Redis Stream)

Redis Stream 기반 큐로 Webhook 요청을 즉시 저장하고, Worker가 별도로 소비하여 GitHub Actions Timeout을 방지

### 자동 재시도 (Retry)

일시적 오류(네트워크 오류, OpenRouter Rate Limit 등)에 대해 PEL(Pending Entries List) 기반으로 최대 5회까지 자동 재시도

### DLQ 관리 및 수동 재처리

재시도를 모두 실패한 요청은 DLQ로 이동해 보관하며, 목록 조회 및 개별 항목 수동 재처리 API 제공

### Multi Repository 지원

여러 Repository의 Push를 하나의 서버가 함께 수신하며, Repository별로 Slack 채널을 분리하여 리뷰 결과를 전달

### Review History 관리

성공한 리뷰를 PostgreSQL에 저장하고, 다음 API로 조회 가능합니다.

| Method | Endpoint | 설명 |
|---|---|---|
| POST | `/api/v1/reviews` | 리뷰 요청 (Redis Stream에 등록) |
| GET | `/api/v1/reviews` | 리뷰 이력 목록 조회 (페이징) |
| GET | `/api/v1/reviews?repository={repo}` | 레포지토리별 리뷰 이력 조회 (페이징) |
| GET | `/api/v1/reviews/{id}` | 리뷰 단건 조회 |
| GET | `/api/v1/reviews/failed` | DLQ(실패한 리뷰) 목록 조회 (페이징) |
| POST | `/api/v1/reviews/{recordId}/retry` | DLQ 항목 수동 재처리 |

### 요청 추적 로깅

Repository 단위 MDC 로깅으로 요청 흐름 추적, OpenRouter 응답 시간 및 토큰 사용량 로깅

### RAG 기반 코드 분석 (예정)

변경 코드와 관련 클래스 검색 후 함께 리뷰
Diff 뿐 아니라 프로젝트 맥락을 파악한 리뷰 제공

---

## 기술 스택

### Backend

* Java 17
* Spring Boot 4.1
* Spring Web
* Spring Data JPA
* Lombok

### AI

* OpenRouter API (무료모델 사용중)

### DevOps

* GitHub Actions (Repository별 workflow + Secrets)
* Docker Compose

### Messaging

* Slack Webhook (Repository별 채널 분기)

### Queue

* Redis Stream (Consumer Group, PEL, XCLAIM 기반 Retry/DLQ)

### Database

* PostgreSQL 16 (Review History 저장)

### Vector Search

* pgvector (예정)

---

## 현재 구현 완료

### 리뷰 파이프라인
* GitHub Actions 구성 (Diff, commitId, runId 추출 및 전송)
* Review API 호출 / OpenRouter 연동 / AI 리뷰 생성
* OpenRouter 응답에서 토큰 사용량(promptTokens, completionTokens) 추출
* Slack Webhook 연동
* Diff 크기 제한 (1000줄 초과 시 리뷰 생략)
* 리뷰 포맷 고정 / 품질 개선

### 비동기 처리 (Redis Stream)
* Redis Stream 기반 Producer/Worker 구조
* Consumer Group 자동 생성 (애플리케이션 시작 시)
* PEL 기반 재시도 (최대 5회, idle time 기준 재할당)
* 재시도 판단(Worker)과 DLQ 적재/알림(DlqService) 책임 분리
* DLQ 목록 조회 API
* DLQ 개별 항목 수동 재처리 API (동시 처리 방지 락 포함)

### Review History
* PostgreSQL 연동 (Docker Compose)
* ReviewHistory 엔티티 저장 (author, repository, commitId, review, promptTokens, completionTokens, cost, createdAt)
* 리뷰 이력 목록 / 레포지토리별 목록 / 단건 조회 API (페이징 지원)

### Multi Repository 지원
* 각 원격저장소에 GitHub Actions workflow 및 Secrets 등록으로 다중 레포 Push 수신
* `SlackProperties.webhooks` 맵 기반 Repository별 Slack 채널 분기 (`owner/repo` 형식 key, 대괄호 표기 사용)
* 맵에 등록되지 않은 Repository는 예외 없이 기본 채널로 fallback (재시도/DLQ 오적재 방지)
* 실제 다중 레포(`dydhyun/Blog-server`) 대상 실전 테스트 및 채널별 수신 확인 완료

### 운영 안정성
* ApiKeyFilter(인증)
* GlobalExceptionHandler (전역 예외 처리)
* 응답시간 / 토큰 사용량 로깅
* MDC 적용 (Repository 단위 로그 추적)

---

## 향후 개선 예정

* run_id 기반 중복 요청 차단 (실제 중복 발생 시 도입)
* DLQ 항목을 원본 Review Stream ID(originalId) 기준으로 조회/재처리하도록 개선
* Page 응답 직렬화 안정화 (PagedModel 적용)
* GitHub Actions에서 push 1건에 포함된 여러 커밋을 모두 반영하도록 Diff 추출 로직 개선
* RAG 기반 프로젝트 문맥 분석
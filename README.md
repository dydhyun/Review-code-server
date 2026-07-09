# AI Code Review Server

GitHub Push 이벤트가 발생하면 변경된 코드를 AI가 분석하여 코드 리뷰를 생성하고 Slack으로 전달하는 백엔드 서비스입니다.

---

## 프로젝트 소개

개인 프로젝트를 진행하면서 코드 리뷰를 받기 위해 Pull Request를 생성하거나 별도 AI 도구에 코드를 복사하여 붙여넣는 과정이 반복적으로 발생했습니다.

이 프로젝트는 이러한 과정을 자동화하기 위해 시작되었습니다.

GitHub Repository에 Push가 발생하면 GitHub Actions가 변경된 Diff를 추출하여 Review Server로 전달하고, Review Server는 AI를 통해 코드 리뷰를 생성한 뒤 Slack으로 전달합니다.

Push 요청은 Redis Stream 기반 큐에 적재되어 비동기로 처리되며, 실패한 리뷰는 자동 재시도 후 DLQ(Dead Letter Queue)로 이동해 안전하게 관리됩니다.

향후에는 단순 Diff 기반 리뷰를 넘어 프로젝트 구조까지 이해하는 RAG(Retrieval Augmented Generation) 기반 리뷰 시스템으로 확장하는 것을 목표로 합니다.

---

## 프로젝트 아키텍처

### 현재 아키텍처

```text
GitHub Repository (push)
      ↓
GitHub Actions
      ↓ (Diff + runId 전송)
Review Server (Controller)
      ↓ (job 등록, XADD)
Redis Stream (review-stream)
      ↓
Worker (Scheduled Consumer)
      ↓ (Review 요청)
OpenRouter
      ↓ (Review 반환)
Worker
      ↓
Slack 전송 + ACK

실패 시:
Worker → Retry (최대 5회, PEL 기반 재처리)
       → 초과 시 DLQ(review-dlq)로 이동 + Slack 실패 알림
       → DLQ 목록 조회 / 수동 재처리 API 제공
```

**Worker** :
Redis Stream에 저장된 리뷰 요청을 주기적으로 소비(Consume)하여 AI 리뷰를 생성하는 백그라운드 컴포넌트입니다. 처리에 실패한 메시지는 ACK 하지 않고 PEL(Pending Entries List)에 남겨, 별도 스케줄러가 idle 시간이 지난 메시지를 재할당(XCLAIM)하여 재시도합니다. 최대 재시도 횟수를 초과하면 DLQ로 이동하고 Slack으로 실패를 알립니다.

**DLQ (Dead Letter Queue)** :
재시도를 모두 실패한 요청을 별도 스트림에 보관해 유실 없이 원인 분석 및 수동 재처리가 가능하도록 합니다. DLQ 조회 API로 실패 목록을 확인하고, 재처리 API로 개별 항목을 다시 시도할 수 있습니다. 재처리 성공 시 DLQ에서 제거되고, 실패 시 DLQ에 그대로 남아 Slack으로 실패 알림만 전송됩니다.

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

GitHub Push 발생 시 Actions가 변경사항 Diff와 실행 식별자(runId)를 추출해 Review Server로 전달 후 리뷰 시작

### AI 코드 리뷰

OpenRouter API를 활용하여 심각도([HIGH], [MEDIUM], [LOW]) 기준으로 코드 리뷰 생성

### Slack 알림

리뷰 결과를 Slack 채널로 전송. 검토할 변경사항이 없는 경우에도 안내 메시지 전송

### 대용량 Diff 보호

토큰 비용 과다 소모 방지를 위해 1000줄 이상의 Diff는 리뷰를 생략하고 알림만 전송

### 비동기 처리 (Redis Stream)

Redis Stream 기반 큐로 Webhook 요청을 즉시 저장하고, Worker가 별도로 소비하여 GitHub Actions Timeout을 방지

### 자동 재시도 (Retry)

일시적 오류(네트워크 오류, OpenRouter Rate Limit 등)에 대해 PEL(Pending Entries List) 기반으로 최대 5회까지 자동 재시도

### DLQ 관리 및 수동 재처리

재시도를 모두 실패한 요청은 DLQ로 이동해 보관하며, 목록 조회 및 개별 항목 수동 재처리 API 제공

### 요청 추적 로깅

Repository 단위 MDC 로깅으로 요청 흐름 추적, OpenRouter 응답 시간 및 토큰 사용량 로깅

### Review History 관리 (예정)

생성된 리뷰 DB 저장 및 이력 조회

### RAG 기반 코드 분석 (예정)

변경 코드와 관련 클래스 검색 후 함께 리뷰
Diff 뿐 아니라 프로젝트 맥락을 파악한 리뷰 제공

---

## 기술 스택

### Backend

* Java 17
* Spring Boot 4.1
* Spring Web
* Lombok

### AI

* OpenRouter API (무료모델 사용중)

### DevOps

* GitHub Actions
* Docker Compose

### Messaging

* Slack Webhook

### Queue

* Redis Stream (Consumer Group, PEL, XCLAIM 기반 Retry/DLQ)

### Database

* PostgreSQL (예정)

### Vector Search

* pgvector (예정)

---

## 현재 구현 완료

### 리뷰 파이프라인
* GitHub Actions 구성 (Diff, runId 추출 및 전송)
* Review API 호출 / OpenRouter 연동 / AI 리뷰 생성
* Slack Webhook 연동
* Diff 크기 제한 (1000줄 초과 시 리뷰 생략)
* 리뷰 포맷 고정 / 품질 개선

### 비동기 처리 (Redis Stream)
* Redis Stream 기반 Producer/Worker 구조
* Consumer Group 자동 생성 (애플리케이션 시작 시)
* PEL 기반 재시도 (최대 5회, idle time 기준 재할당)
* DLQ 이동 및 실패 알림
* DLQ 목록 조회 API
* DLQ 개별 항목 수동 재처리 API (동시 처리 방지 락 포함)

### 운영 안정성
* ApiKeyFilter(인증)
* GlobalExceptionHandler (전역 예외 처리)
* 응답시간 / 토큰 사용량 로깅
* MDC 적용 (Repository 단위 로그 추적)
* 단위 테스트 작성 (OpenRouterClient, SlackClient, ReviewService)

---

## 향후 개선 예정

* run_id 기반 중복 요청 차단 (실제 중복 발생 시 도입)
* Review History
* Multi Repository 지원
* RAG 기반 프로젝트 문맥 분석

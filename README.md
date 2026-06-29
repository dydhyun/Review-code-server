# AI Code Review Server

GitHub Push 이벤트가 발생하면 변경된 코드를 AI가 분석하여 코드 리뷰를 생성하고 Slack으로 전달하는 백엔드 서비스입니다.

---

## 프로젝트 소개

개인 프로젝트를 진행하면서 코드 리뷰를 받기 위해 Pull Request를 생성하거나 별도 AI 도구에 코드를 복사하여 붙여넣는 과정이 반복적으로 발생했습니다.

이 프로젝트는 이러한 과정을 자동화하기 위해 시작되었습니다.

GitHub Repository에 Push가 발생하면 GitHub Actions가 변경된 Diff를 추출하여 Review Server로 전달하고, Review Server는 AI를 통해 코드 리뷰를 생성한 뒤 Slack으로 전달합니다.

향후에는 단순 Diff 기반 리뷰를 넘어 프로젝트 구조까지 이해하는 RAG(Retrieval Augmented Generation) 기반 리뷰 시스템으로 확장하는 것을 목표로 합니다.

---

## 프로젝트 아키텍쳐

### 현재 아키텍처

```text
GitHub Repository (push)
      ↓
GitHub Actions
      ↓ (Diff 전송)
Review Server
      ↓ (Review 요청)
OpenRouter
      ↓ (Review 반환)
Review Server
      ↓
Slack
```

---

### 향후 아키텍처

```text
GitHub Repository (push)
      ↓
GitHub Actions
      ↓
Review Server
      ↓ (job 등록)
Redis Queue
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
**Worker** : 
Redis Queue에 저장된 리뷰 요청을 소비(Consume)하여 AI 리뷰를 생성하는 백그라운드 컴포넌트입니다.
Review 생성, Review History 저장, Slack 전송을 비동기로 수행하며, Retry 및 DLQ 전략을 통해 실패한 작업을 안전하게 처리합니다.

---

## 주요 기능

### GitHub Push 기반 자동 리뷰

GitHub Push 발생 시 Actions가 변경사항 Diff 추출해 Review Server로 전달 후 리뷰 시작

### AI 코드 리뷰

OpenRouter API를 활용하여 심각도([HIGH], [MEDIUM], [LOW]) 기준으로 코드 리뷰 생성

### Slack 알림

리뷰 결과를 Slack 채널로 전송

### 대용량 Diff 보호

토큰 비용 과다 소모 방지를 위해 1000줄 이상의 Diff는 리뷰를 생략하고 알림만 전송

### 비동기 처리 (예정)

Redis Queue 기반으로 처리하여 GitHub Actions Timeout을 방지

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

### Messaging

* Slack Webhook

### Database

* PostgreSQL (예정)

### Cache / Queue

* Redis (예정)

### Container (예정)

* Docker Compose

### Vector Search

* pgvector (예정)

---

## 현재 구현 완료

* GitHub Actions 구성
* Diff 추출
* Review API 호출
* OpenRouter 연동
* AI 리뷰 생성
* Slack Webhook 연동
* Push → AI Review → Slack 파이프라인 완성
* Diff 크기 제한 (1000줄 초과 시 리뷰 생략)
* 리뷰 포맷 고정 / 품질 개선
* ApiKeyFilter(인증)

---

## 향후 개선 예정

* Queue 기반 비동기 처리
* Retry / DLQ
* Review History
* Multi Repository 지원
* RAG 기반 프로젝트 문맥 분석
* 토큰 비용 추적

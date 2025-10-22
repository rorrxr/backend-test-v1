# 💳 결제 도메인 서버 – Payment Domain Server

## 📖 목차

1. [🔎 프로젝트 개요](#-프로젝트-개요)
2. [💻 기술 스택](#-기술-스택)
3. [🎯 구현 목표](#-구현-목표)
4. [🚀 주요 기능](#-주요-기능)
5. [🧠 기술적 의사결정](#-기술적-의사결정)
6. [🚨 트러블슈팅](#-트러블슈팅)
7. [🧩 아키텍처](#-아키텍처)
8. [🧪 테스트](#-테스트)
9. [✅ 향후 개선 방향](#-향후-개선-방향)
10. [💬 마지막 한마디 및 과제 소감](#-마지막-한마디-및-과제-소감)


---

## 🔎 프로젝트 개요

본 프로젝트는 **나노바나나 페이먼츠(NanoBanana Payments)의 결제 도메인 서버** 구현 과제입니다.

목표는 헥사고널 아키텍처를 기반으로 **제휴사별 수수료 정책**, **결제 내역 조회/통계**, **PG 연동**을 완전하게 구현하는 것입니다.

---

## 💻 기술 스택

| 구분         | 사용 기술                  |
|------------|------------------------|
| Language   | Kotlin (JDK 22)        |
| Framework  | Spring Boot 3.5.x      |
| Database   | H2 / MariaDB           |
| ORM        | Spring Data JPA        |
| Monitoring | Prometheus + Grafana   |
| DevOps     | Docker, docker-compose |
| API Docs   | Springdoc OpenAPI      |

---

## 🎯 구현 목표

| 기능 | 설명 |
  | --- | --- |
| **결제 생성 API** | PG 승인 후 수수료/정산금 계산 및 저장 |
| **결제 내역 조회 API** | 커서 기반 페이지네이션 + 통계 포함 응답 |
| **수수료 정책 반영** | 제휴사별 비율·고정 수수료 및 시점(`effective_from`) 반영 |
| **테스트 보강** | 단위·통합 테스트로 기능 검증 및 회귀 방지 |
| **추가 구현** | Micrometer 기반 메트릭, TestPG 연동, MockPG 전략 패턴 확장 |

## 🚀 주요 기능

### 1️⃣ 결제 생성 API (`POST /api/v1/payments`)

- PG 승인(`PgService`) → 수수료 정책 조회(`FeePolicyOutPort`) → 수수료 계산(`FeeCalculator`)
- `Payment` 도메인 생성 및 영속화
- 반올림 규칙: **HALF_UP**
- 카드 정보: BIN/Last4만 저장, 나머지는 미저장

### 결제 생성 결과 예시

```jsx
{
  "partnerId": 1,
  "amount": 10000,
  "appliedFeeRate": 0.0235,
  "feeAmount": 335,
  "netAmount": 9665,
  "status": "APPROVED"
}

```

### 2️⃣ 결제 내역 조회 API (`GET /api/v1/payments`)

- 필터: `partnerId`, `status`, `from`, `to`
- 커서 기반 페이지네이션(`createdAt DESC, id DESC`)
- `summary` 객체는 조회 결과 집합과 동일한 조건으로 계산

### 결제 내역 조회 예시

```jsx
{
  "items": [{ "id": 1, "amount": 10000, ... }],
  "summary": { "count": 35, "totalAmount": 35000, "totalNetAmount": 33950 },
  "nextCursor": "ey1...",
  "hasNext": true
}
```

**3️⃣ 수수료 정책 적용**

- `partner_fee_policy` 테이블 기반, `effective_from` <= now 중 가장 최근 정책 적용
- 정책 변경 시 기존 결제에는 소급되지 않음

### 수수료 정책 예시

| partner_id | effective_from | percentage | fixed_fee |
|-------------|----------------|-------------|------------|
| 1 | 2024-12-01 | 0.0250 | 100 |
| 1 | 2025-02-01 | 0.0300 | 50 |


---

### 4️⃣ 제휴사 추가 연동

- PG 연동 인터페이스(`PgClientOutPort`)에 새 어댑터 구현 후 DI 등록
- 예시: `TestPayClientAdapter`, `NicePayClientAdapter`, `KakaoPayClientAdapter` 등

---

## 🧠 기술적 의사결정

### 헥사고널 아키텍처 도입

- MSA 및 확장 가능한 구조를 고려하여 **도메인 중심 설계(Domain-Centric)** 채택
- 외부 연동(PG, Repository 등)을 어댑터로 분리해 결합도 최소화

### FeePolicy 기반 수수료 계산

- 기존의 “하드코드 3% + 100원” 로직을 `FeePolicy` 엔티티 기반 계산으로 변경
- `FeeCalculator` 유틸은 순수 함수형 구조로 반올림 방식 및 단위 테스트 완비

### Cursor 기반 Pagination

- 기존 Offset 기반 대비 **일관성·성능 향상**
- 정렬 키: `(createdAt DESC, id DESC)`
- 페이징 커서 암호화 처리 (`Base64` 인코딩)

### Micrometer Metrics

- `PaymentService` 내부 주요 단계에 Timer, Counter 등록
- Prometheus 연동을 고려한 `MeterRegistry` 주입

---

## 🚨 트러블슈팅

### 1️⃣ FeePolicy 반영 안 되는 문제

- **원인:** Partner ID에 따른 정책 쿼리 미적용
- **해결:** JPA `findTopByPartnerIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc()` 로 수정

### 2️⃣ 커서 페이지네이션 불일치

- **원인:** 정렬 기준 불일치(`createdAt ASC` 사용)
- **해결:** 정렬 기준을 `(createdAt DESC, id DESC)` 로 통일하고, 커서 비교 로직 수정

### 3️⃣ 테스트 데이터 간 일관성 문제

- **해결:** 테스트마다 `@DataJpaTest` + `Instant.now()` 고정값 사용
- **결과:** 커버리지 및 결정성 확보

---

## 🧩 아키텍처

본 프로젝트는 **헥사고널(Ports & Adapters)** 구조로 설계되었습니다.

```
modules
 ├── domain                        # 순수 도메인 모델
 │   ├── partner/FeePolicy.kt
 │   ├── payment/Payment.kt
 │   └── calculation/FeeCalculator.kt
 │
 ├── application                    # 유스케이스 (비즈니스 로직)
 │   ├── payment/service/PaymentService.kt
 │   ├── payment/port/in/PaymentUseCase.kt
 │   ├── payment/port/out/PaymentOutPort.kt
 │   ├── partner/port/out/FeePolicyOutPort.kt
 │   └── pg/service/PgService.kt
 │
 ├── infrastructure/persistence     # JPA 기반 어댑터
 │   ├── payment/repository/PaymentJpaRepository.kt
 │   ├── payment/entity/PaymentEntity.kt
 │   └── partner/repository/FeePolicyRepository.kt
 │
 ├── external/pg-client             # 외부 PG 연동(Mock/TestPay)
 │   └── TestPgClientAdapter.kt
 │
 └── bootstrap/api-payment-gateway  # 실제 실행 모듈 (Spring Boot Entry)
     └── PaymentController.kt

```

### 주요 의존성 원칙

- `domain`은 어떠한 외부 의존성도 갖지 않음
- `application`은 도메인 로직 조합 및 외부 포트 호출 담당
- `infrastructure`는 JPA, PG 등 실제 구현체를 제공
- 각 계층은 **의존 역전 원칙(DIP)** 을 준수

---

## 🧪 테스트

### 단위 테스트

| 클래스 | 주요 검증 |
| --- | --- |
| `FeeCalculatorTest` | 비율+고정 수수료 조합 반올림 검증 |
| `PaymentServiceTest` | 수수료 정책 적용 및 정산금 계산 검증 |
| `PgServiceTest` | Mock PG 승인 요청/응답 플로우 검증 |

### 통합 테스트

| 테스트명 | 설명 |
| --- | --- |
| `결제저장소커서페이징Test` | 커서 페이징과 summary 일관성 검증 |
| `PaymentIntegrationTest` | end-to-end 결제 생성 + 조회 API 검증 |
| `FeePolicyEffectiveDateTest` | 정책 시점(`effective_from`) 적용 검증 |

---

## ✅ 향후 개선 방향

| 개선 항목                           | 설명                                 |
|---------------------------------|------------------------------------|
| **Kafka 이벤트 발행**                | 결제 완료 후 비동기 정산 이벤트 발행              |
| **Resilience4j CircuitBreaker** | PG 연동 실패 시 자동 복구 및 fallback 처리     |
| **외부 결제 API 연동**                | 외부 결제 PG API (ex : 카카오페이, 토스페이) 연동 |

## 💬 마지막 한마디 및 과제 소감

이번 과제를 통해 **헥사고널 아키텍처**에 대해 깊이 있게 학습할 수 있는 좋은 기회였습니다.  
평소에도 도메인 중심 설계(DDD)와 같이 확장성을 고려한 설계에 관심이 많았는데 실제 구현 과정을 통해 아키텍처 설계의 중요성을 다시 한번 체감했습니다.

또한 저는 앞으로 개발자로서 성장하기 위해서는 **하나의 언어에 머무르지 않고, 여러 언어 간의 자유로운 전환과 사고의 유연함**이 중요하다고 생각합니다.  
그런 의미에서 이번 과제는 **Kotlin과 Spring Boot (일명 "코프링") 환경**을 본격적으로 탐구할 수 있었던 뜻깊은 경험이었습니다.  
코틀린의 함수형 패러다임과 스프링의 강력한 생태계가 만나면서 얻을 수 있는 생산성과 가독성의 조화를 직접 체감할 수 있었습니다.

이번 테스트 과제를 통해 새로운 기술 스택을 실무 수준으로 익히는 계기가 되었으며
이를 통해 한층 더 성장한 개발자로 나아갈 자신감을 얻었습니다.  
이런 기회를 주셔서 진심으로 감사드립니다.

마지막으로 **JPQL과 QueryDSL의 차이**, 그리고 **Offset 기반 vs Cursor 기반 페이지네이션**을 이전에 정리한 제 블로그 글을 공유합니다.  
해당 내용은 이번 과제의 설계 결정에도 큰 도움이 되었습니다.

- 🔗 [Spring JPQL vs QueryDSL](https://deve1opment-story.tistory.com/entry/Spring-JPQL-vs-QueryDSL)
- 🔗 [Spring 오프셋 기반 vs 커서 기반 페이지네이션 비교](https://deve1opment-story.tistory.com/entry/Spring-%EC%98%A4%ED%94%84%EC%85%8B-%EA%B8%B0%EB%B0%98-vs-%EC%BB%A4%EC%84%9C-%EA%B8%B0%EB%B0%98-%ED%8E%98%EC%9D%B4%EC%A7%95-%EA%B8%B0%EB%B2%95)

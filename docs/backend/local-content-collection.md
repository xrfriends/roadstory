# 지역정보 원본 수집

## 범위

`REQ-CMS-002`, `REQ-CMS-008`을 위한 내부 수집 기반이다. 외부 공개 API나 화면은 추가하지 않으며 Story 생성, 검수, 발행은 수행하지 않는다.

## Architecture

- `collection.domain`: `DataSource`, `CollectionJob`, `RawRecord`와 Job 상태
- `collection.application`: Java `LocalContentSource` port, 저장소 port, 수집 orchestration
- `collection.infrastructure.source`: 외부 Adapter가 구현할 경계와 네트워크 없는 `FakeLocalContentSource`
- `collection.infrastructure.persistence`: PostgreSQL/JPA Adapter

Adapter는 외부 응답을 `CollectedRecord.Valid.rawPayload`에 원형 그대로 담는다. Application service는 이 payload를 먼저 `RawRecord`로 upsert하며 Story 변환을 호출하지 않는다.

## Failure와 retry

- 한 레코드의 파싱·저장 실패는 `failedCount`에 반영하고 다음 레코드를 처리한다.
- `NetworkSourceException`과 5xx인 `HttpServerSourceException`만 최대 3회 시도한다.
- 4xx와 데이터 오류는 재시도하지 않는다.
- 같은 Batch의 중복 external ID는 실패 레코드로 집계한다.
- 기존 hash와 같으면 payload와 수집 시각을 갱신하지 않는다.

API Key는 Adapter 설정에서 환경 변수 또는 Secret provider로 주입해야 하며 payload나 로그에 기록하지 않는다.

## 추적성

- Requirement: `REQ-CMS-002`, `REQ-CMS-008`
- Screen/Flow: 내부 Batch 기반이므로 해당 없음
- Test Scenario: `TS-COLLECT-001` ~ `TS-COLLECT-008`

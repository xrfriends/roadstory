# ADR-002: API First 개발

- Status: Accepted
- Date: 2026-08-04

## Decision

`contracts/openapi.yaml`을 외부 HTTP API의 Source of Truth로 사용한다.

## 변경 순서

1. OpenAPI 수정
2. Compatibility 검토
3. Client·Server Type 생성
4. 구현
5. Contract·E2E Test

## 규칙

- Version Migration 없이 Field 삭제 금지
- 새 Response Field는 가능하면 Optional
- Enum 변경 주의
- Problem Details와 Error Code 사용
- ISO 8601, WGS84, meter, second 사용

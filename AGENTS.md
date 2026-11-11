# AGENTS.md

## 목적

이 저장소는 **Road Story(로드 스토리)**의 제품 기획, UX, API 계약, Frontend, Backend, E2E 테스트를 함께 관리한다. Codex와 모든 기여자는 `docs/`와 `contracts/openapi.yaml`을 구현의 기준으로 사용한다.

## 제품 원칙

1. **Audio First**: 주행 중 긴 탐색이나 읽기를 요구하지 않는다.
2. **Safety First**: `DRIVING` 상태에서는 재생, 일시정지, 다음, 저장, 관심 없음, 종료만 제공한다.
3. **Verified Content**: AI는 초안을 만들 수 있지만 자동 발행하지 않는다.
4. **Context-aware Playback**: 위치, 방향, 속도, 경로, 남은 시간, 청취 이력을 고려한다.
5. **Platform Separation**: Vehicle은 듣기, Mobile/Web은 상세 탐색과 관리에 집중한다.
6. **API First**: 외부 API 변경 전 `contracts/openapi.yaml`을 먼저 수정한다.

## 공통 ID

| 유형 | Prefix | 예시 |
|---|---|---|
| 요구사항 | `REQ-` | `REQ-PLAY-001` |
| 화면 | `SCR-` | `SCR-HOME-001` |
| 사용자 흐름 | `FLW-` | `FLW-FREE-001` |
| Persona | `PER-` | `PER-FAMILY-001` |
| Metric | `MET-` | `MET-ACT-001` |
| 인수 조건 | `AC-` | `AC-PLAY-001` |
| Test Scenario | `TS-` | `TS-PLAY-001` |

## Domain 용어

| 한글명 | Domain Name |
|---|---|
| 이야기 | `Story` |
| 장소 | `Place` |
| 재생 조건 | `PlaybackTrigger` |
| 주행 세션 | `JourneySession` |
| 목적지 재생 목록 | `DestinationPlaylist` |
| 테마 코스 | `ThemeCourse` |
| 검수 상태 | `VerificationStatus` |
| 주행 상태 | `DrivingState` |

## 개발 규칙

- 관련 문서를 읽은 뒤 코드를 수정한다.
- 문서에 없는 PLEOS SDK API를 임의로 만들지 않는다.
- Platform API는 Interface 뒤에 격리한다.
- 원본 위치, Token, CRN, 개인정보를 로그에 기록하지 않는다.
- 기능 변경에는 테스트와 문서 변경을 포함한다.
- Consumer API는 `PUBLISHED` Story만 반환한다.
- 오류 응답은 Problem Details와 안정적인 Error Code를 사용한다.

## 필수 검증

```bash
cd frontend/web-harness && npm ci && npm run lint && npm run test && npm run build
cd backend && ./gradlew test && ./gradlew build
cd e2e && npm ci && npx playwright test
```

실행하지 못한 항목은 완료 보고에 명시한다.

## Definition of Done

- Requirement ID, Screen ID, Flow ID가 연결되어 있다.
- 관련 Acceptance Criteria를 충족한다.
- 자동화 테스트가 있다.
- API 변경이 OpenAPI에 반영되었다.
- 주행 안전 제한을 유지한다.
- Secret과 개인정보가 Commit되지 않았다.

## 금지 사항

- AI 생성 역사 정보 자동 발행
- 주행 중 긴 Text·Image Gallery 제공
- 전설을 사실처럼 표현
- Source 검증 생략
- `.env`, API Key, Signing Key Commit

## Codex 완료 보고

1. 작업 목적
2. 변경 파일
3. 관련 Requirement·Screen·Flow ID
4. 설계 판단
5. 실행 테스트와 결과
6. 알려진 제한
7. 권장 후속 작업

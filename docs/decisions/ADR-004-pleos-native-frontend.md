# ADR-004: Pleos Native Frontend와 Web 검증 Harness 분리

- Status: Accepted
- Date: 2026-08-06

## Context

Road Story의 차량 UI는 Pleos Connect의 멀티 윈도우, `CarUxRestrictions`, Audio Focus, MediaSession과 물리 Controller를 따라야 한다. React Web UI만으로는 차량 Platform 동작을 검증할 수 없다.

## Decision

- 실제 차량 Frontend는 `frontend/pleos-app`의 Kotlin Android Automotive 앱으로 구현한다.
- 기존 React 앱은 API Contract와 E2E Flow를 빠르게 검증하는 Web Harness로 유지한다.
- PLEOS 전용 SDK는 `pleos` Product Flavor에만 연결하고, 일반 CI와 개발은 `fake` Flavor를 사용한다.
- 차량 하드웨어와 Navigation은 Interface 뒤에서만 접근한다.

## PLEOS 정책 적용

- 앱 카테고리는 `Music/Audio`로 선언한다.
- `CarUxRestrictionsManager`가 전달한 규제를 그대로 반영하고 지역이나 속도로 자체 판정하지 않는다.
- portrait와 landscape를 모두 지원하며 landscape에서는 Player를 보조 영역에 표시한다.
- Audio Focus를 얻은 경우에만 재생 상태로 전환하고 상실하면 즉시 일시정지한다.
- `MediaBrowserService`, `MediaSession`, 물리 미디어 Key를 지원한다.
- 앱 종료 시 PLEOS SDK Listener/리소스를 해제한다.

## API 가용성 판단

공식 문서상 `NaviHelper`는 전체 호환이고 `Vehicle` API는 모델별 Capability 확인이 필요하다. `FusedLocation`과 `ADAS`는 Pleos Connect 대상 차량에서 미지원이므로 현재 앱에 연결하지 않는다. 위치·목적지 실제 연결은 대상 차량과 공개 Capability가 확정된 뒤 추가한다.

## Consequences

두 Frontend가 존재하지만 책임은 명확하다. Web은 제품이 아니라 검증 Harness이며, 배포 APK는 Android 모듈에서 생성한다. 실제 Emulator/차량의 `CarUxRestrictions`와 Suspend Event 검증은 PLEOS 개발 환경이 필요하다.


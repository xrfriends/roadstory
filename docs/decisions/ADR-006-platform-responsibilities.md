# ADR-006 — PLEOS·AAOS·Road Story 구현 책임 분리

- Status: Proposed
- Date: 2026-11-09
- 목적: Figma Make 기능 점검에서 확인한 공백을 플랫폼 연동과 제품 구현으로 나누고 구현 순서를 정한다.
- 범위: 구현 책임 선별. SDK 업그레이드, 실행 코드, OpenAPI 변경은 포함하지 않는다.

## 판단 원칙

차량의 신호·내비게이션·음성 엔진은 PLEOS/AAOS에서 받고, Story 선택·재생 순서·저장·화면 상태는 Road Story에서 구현한다. SDK 호출이 성공해도 제품 기능이 완성되는 것은 아니다. PLEOS 전용 SDK, Android/AAOS 표준 API, 앱, Backend를 서로 구분한다.

공식 문서 확인일은 2026-11-09다. 당시 앱 의존성은 Vehicle 2.0.3, NaviHelper 2.0.3, TTS 2.1.5.1이었다. 아래 최신 문서의 메서드와 이벤트를 당시 SDK에서 그대로 사용할 수 있다고 가정하지 않는다.

## 플랫폼에서 가져올 기능과 앱의 책임

| 영역 | 사용할 플랫폼 | 플랫폼 역할 | 직접 구현할 역할 | 현재 상태 / 결정 |
|---|---|---|---|---|
| 주행 UX 규제 | AAOS `CarUxRestrictionsManager` | DO 요구와 ActiveRestrictions 변경 전달 | `VehicleUiPolicy`로 허용 조작만 노출, 이미지·탐색 차단, UNKNOWN/실패 시 제한, 화면 상태 갱신 | Source 있음. 정책 강화 최우선. 속도·기어로 규제를 자체 판정하지 않음 |
| 위치·방향·신뢰도 | PLEOS FusedLocation SDK 조건부 | 지원 환경에서 위치/heading/confidence/timestamp 수신 | 최신성·신뢰도 검사, 자동 트리거 중단, 데이터 최소화, 후보 요청 | 새 위치 Adapter 필요. `isSupported()`, SDK 버전, 프로젝트 사용 가능 상태, 권한 확인 후 사용 |
| 속도 등 추가 차량 신호 | 필요한 AAOS Vehicle Property 또는 Vehicle SDK | 지원·접근 가능한 신호 제공 | 추천 입력으로 정규화, 누락 처리 | 선택 사항. SDK 명세/Capability로 확인된 신호만 사용. FusedLocation이 속도까지 보장한다고 가정하지 않음 |
| 목적지·내비 상태 | PLEOS NaviHelper | 목적지 조회·설정, 내비 상태/경로 이벤트, 내비 화면 요청 | 목적지 없음·이벤트 누락 처리, RouteContext 변환 | 현재 initialize/release뿐. Listener·결과·오류 연결 필요 |
| 경로·ETA | PLEOS NaviHelper에서 제공되는 범위 | 대상 버전이 제공하는 내비 정보 수신 | 정보 최신성 관리, 변경 감지, Playlist 재편성 요청 | 상세 Geometry·ETA·거리·도착 이벤트의 실제 제공 필드는 버전/API Reference/차량에서 확인. 부족한 Geometry는 별도 RouteProvider 필요 여부 판단 |
| 첫 코스 장소 길 안내 | PLEOS NaviHelper | 확인된 목적지/경유지 요청을 내비 앱에 전달 | 사용자의 시작 확인, 첫 Stop 선택, 도착 후 다음 Stop 제안 | Course 진행 상태는 앱 구현. 모든 Stop을 자동 Waypoint로 넣지 않음 |
| 검수문 음성 합성 | PLEOS TextToSpeech | 텍스트 합성·출력, 준비/시작/완료/중지/오류 이벤트 | `PUBLISHED` 검수문 공급, 다음 Story, 오류 복구, 세션 상태 | 기존 `StorySpeechPlayer` 유지. ON_DEVICE를 현재 기준으로 사용하되 프로젝트/차량 실행 가능성 검증 |
| 녹음/사전 생성 음원 재생 | Android 미디어 재생 라이브러리 | 파일/스트림 디코딩·재생·지원되는 pause/seek | Audio Variant 선택, 캐시, 재생 큐, 이어듣기, 다운로드 권한 | 정확한 시간·seek·오프라인 음원이 필요한 경우 별도 AudioPlayer Adapter 도입 권장. TTS가 파일 저장/seek를 제공한다고 추정하지 않음 |
| 내비·통화·안전 안내 오디오 경합 | AAOS `AudioManager` / `AudioFocusRequest` | Focus 허용·거부 및 변경 통지 | Pleos 정책에 맞는 duck/pause/stop, 사용자 종료 후 재개 금지, 로딩 취소 | `AudioFocusController` 있음. 구체적인 전이·정책 구현과 차량 검증 필요 |
| 시스템 재생 제어·물리 미디어 키 | Android `MediaSession`, `MediaBrowserService`, `MediaController` | 시스템과 앱 사이 명령·상태 전달 | 실제 큐/Story에 명령 적용, 메타데이터·PlaybackState 발행, UI 구독 | 서비스는 있음. UI 상태 구독과 Story 선택 연결 필요 |
| 멀티윈도우·시스템 Driving view | PLEOS 창 관리 / Android 창 크기 변경 | 시스템 영역·앱 창 배치 | 실제 앱 창 크기에 맞춘 단일/확장 레이아웃, 상태 보존 | Figma의 Connect-S·창 선택·시나리오 제어판은 제품 UI에 구현할 기능이 아님 |
| Suspend·복귀 | 대상 플랫폼 수명주기/공식 Suspend 연동 | 전원·수명주기 신호 | 재생/Focus 해제, 복귀 정책, 불필요한 자동 재개 방지 | 기존 screen-off 대응과 실제 Suspend는 동일하다고 보장하지 않음. OEM/Emulator에서 확인 |
| 음성 명령 | 선택적 PLEOS SpeechToText / Gleo 연동 | STT는 발화를 텍스트로 변환 | 명령 해석·재생/저장 실행·상태 검증 | 후속 범위. STT만 붙이면 명령이 실행되는 것은 아님 |

근거: [주행 규제](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide), [FusedLocation](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro), [API 가용성](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability), [NaviHelper](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro), [TTS](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro), [Audio Focus](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development), [창 구조](https://pleos.ai/playground/resources/en/docs/docs-design-pleos/getting-started/layout), [STT](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro).

## Road Story가 직접 구현할 기능

| 기능 | 앱 책임 | Backend 책임 | 기존 계약 / 계약 공백 |
|---|---|---|---|
| 주변 Story | 위치 샘플 유효성 검사, 후보 프리패치, 오프라인 후보 평가, 최종 자동 트리거·중복 억제 | PUBLISHED/출처 필터, 공간 후보 검색·추천 순위, Preference/이력 기반 제외 | `GET /v1/stories/nearby`. 사용자 이력·cooldown 동기화 정책 보완 필요 |
| 재생 | 선택 ID, 큐, 시작/다음/완료, 일시정지, 자동재생 종료, 수동 종료, 오류/재시도, UI 상태 | Story 상세·Audio Variant·검수 상태 제공, 청취 이벤트 수집 | `GET /v1/stories/{storyId}`. Journey/청취 이벤트 계약 보완 필요 |
| 목적지 Playlist | RouteContext 수집, 모드 선택, 현재 Story 유지, 이후 큐 교체 | 시간 예산, 경로 순서, 안전한 우회 배제, SHORT/STANDARD/FAMILY 편성 | `POST /v1/journeys/destination-playlist` 계약 있음. 실제 Composer 미구현, reroute 계약 표현 검토 |
| 테마 코스 | Course ID 기반 상세, 필터 UI, 시작 확인, Stop 진행·종료/재개 | Published Course 목록/상세·Stop 데이터, 검색/필터 | `GET /v1/courses`, `GET /v1/courses/{courseId}`. 시간/가족 필터는 기존 필드로 로컬 처리 가능 범위와 서버 검색을 구분 |
| 저장·내 이야기·이력 | 즉시 표시, 로컬 저장, 동기화 대기·재시도, 중복 요청 방지 | 사용자별 영속 저장·목록·이력·다기기 동기화 | Story 저장 PUT/DELETE 있음. 저장 목록/이력 조회·코스 저장 계약 없음 |
| 관심 없음·설정 | 카테고리 선택, Auto-play/Family 설정, 즉시 추천 반영 | Preference 저장·동기화와 추천 적용 | Preferences GET/PUT 있음. Story 단위 dislike를 의미 손실 없이 저장할 계약은 별도 필요 |
| 출처·신뢰성 | 정차/모바일에서 Source·검수일·사실/해석/전설 표시 | 원문·출처·저작권·검수·발행 Workflow | Story 상세 및 신고 계약 사용. AI 자동 발행 금지 |
| 오프라인 | 다운로드 큐, 파일/텍스트 무결성, 용량/만료 관리, 오프라인 표시 | 허용 콘텐츠·버전·자산 배포 | ON_DEVICE TTS는 확보한 텍스트를 읽는 기능. 콘텐츠 다운로드/저장/정책을 대신하지 않음 |
| 계정·결제 | OIDC/PKCE 흐름, 안전한 Token 보관, 정차 중 구매 UI | 인증·사용자 경계, 영수증 검증·Entitlement·복원 | ADR-003 기준. PLEOS 차량 프로필을 Road Story 계정과 동일시하지 않음. 구매 Provider 연동은 별도 설계 |

PLEOS는 Road Story의 콘텐츠 DB, 추천 엔진, 저장 동기화 또는 발행 서비스를 대신하지 않는다. API가 없는 기능은 `contracts/openapi.yaml`부터 정의한다.

## 앱의 경계 제안

아래는 새로 제안하는 **앱 내부 인터페이스 역할**이며 PLEOS SDK의 실제 클래스·메서드명이 아니다.

- 기존 `DrivingRestrictionSource`: 규제 및 UNKNOWN/지원 불가 상태를 제품 정책에 전달.
- 제안 `LocationSource`: 위치/방향/신뢰도/샘플 시각과 지원/권한/오류 상태 전달.
- 제안 `NavigationGateway`: 목적지·경로 Context 관측, 사용자 확인 후 길 안내 요청, 지원 불가 결과 전달.
- 기존 `StorySpeechPlayer` 및 제안 `AudioPlayer`: TTS/음원 엔진을 재생 로직에서 격리.
- 제안 `PlaybackCoordinator`: 큐·선택 Story·사용자 의도·Focus·비동기 요청 취소를 하나의 상태 모델로 관리.
- 제안 `StoryRepository` / `UserLibraryRepository`: HTTP·로컬 캐시·동기화 구현 격리.

`PleosRuntimeGateway`의 initialize/release만으로 제품 기능이 완료되었다고 판단하지 않는다. 위 역할의 실제 이벤트·데이터가 앱 상태와 연결되어야 한다. PLEOS Adapter는 `pleos` Flavor에만 두고 동일 계약의 Fake로 정책을 검증한다.

## 버전·가용성 판단과 기존 문서 보완

- 2026-10-19 릴리스 문서는 NaviHelper 3.0.5, TTS 3.3.1, FusedLocation 1.1.2를 안내한다. Vehicle 개요는 2.3.2를 설명하지만 같은 릴리스 문서는 2.3.1이므로 공통 ‘최신 버전’ 하나를 단정하지 않고 Artifact와 대상 Emulator/차량을 기준으로 고정한다.
- ADR-004/개발 환경 문서의 ‘FusedLocation 미지원’은 현재 공식 문서의 환경별 `isSupported()` 확인 정책과 다르다. 과거 환경에 대한 기록으로 보고 신규 연동은 실제 지원 여부를 다시 확인한다. 최신 문서가 현재 프로젝트/기기의 실행 가능성을 보장하지 않는다.
- SDK 접근 안내는 Vehicle State/NaviHelper를 Ready to Use로, FusedLocation/TTS/STT 등은 Not Available Yet 상태의 예로 기술한다. API 문서 존재·Artifact 다운로드·프로젝트 사용 가능 상태·차량 지원은 별개 조건이다. 프로젝트 콘솔 상태와 권한을 확인하기 전에는 신규 FusedLocation 연동 완료를 약속하지 않는다.
- 최신 TTS 문서의 `onError(ErrorType)`와 현재 코드의 `onError(String)`가 다르므로 SDK 버전만 바꾸는 업데이트는 피한다. 현재 `speak(text, false)`도 최신 문서의 완결 요청 `isFinal=true` 규칙과 비교 검증해야 한다.
- TTS 문서에서 확인한 `speak/stop`과 이벤트를 정확한 ms 위치·seek·pause 보장으로 해석하지 않는다. 정확한 이어듣기를 제품 요구로 확정하면 사전 생성 음원+플레이어를 우선 검토하고, TTS는 문장 경계 재개 등 별도 정책을 정의한다.

근거: [SDK 릴리스](https://document.pleos.ai/en/docs/pleos-only/release-note/sdk-release-note), [Vehicle 개요](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro), [SDK 접근](https://document.pleos.ai/en/docs/pleos-only/create-my-project), [TTS 이벤트](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/event-listeners), [합성 요청](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/TextToSpeech/start-and-stop).

## 구현 순서와 완료 기준

| 순서 | 작업 | 완료 증거 | 연결 ID |
|---|---|---|---|
| 1 | AAOS 규제와 앱 안전 정책 | DRIVING/UNKNOWN/조회 실패에서 허용 조작만 표시, 주차 복귀 정상 | REQ-STORY-005, AC-PLAY-002 / SCR-DRIVE-LIMIT-001, SCR-PLAYER-001 / FLW-PLAY-001 |
| 2 | 앱 재생 Coordinator·UI 구독·저장 | 시작→선택 ID 재생→다른 ID로 다음→완료 후 다음, 저장 조회 반영, 사용자 종료 후 재개 없음 | REQ-PLAY-003,004,007,009, REQ-USER-001 / SCR-PLAYER-001 / FLW-PLAY-001, FLW-SAVE-001, FLW-INTERRUPT-001 |
| 3 | FusedLocation 가용성/권한 검증 후 Adapter와 후보 추천 연결 | 유효 위치에서 후보 재생, 신뢰도 낮음·오래된 위치·권한 거부 시 신규 트리거 중단. 지원 불가 환경은 수동 선택/다운로드 청취로 제한 | REQ-PLAY-001,002,005, REQ-OFF-003 / SCR-HOME-001, SCR-FREE-001 / FLW-FREE-001 |
| 4 | NaviHelper 목적지/이벤트 + Backend Composer | 실제 목적지 편성, SHORT/FAMILY 적용, reroute 시 현재 Story 유지 | REQ-ROUTE-001~007 / SCR-DEST-001 / FLW-DEST-001 |
| 5 | Course 데이터·Stop 진행·내비 요청 | 다른 Course는 다른 상세/큐, 확인 후 첫 Stop만 길 안내, 도착 후 다음 제안 | REQ-COURSE-001~007 / SCR-COURSE-LIST-001, SCR-COURSE-DETAIL-001 / FLW-COURSE-001 |
| 6 | 오프라인 음원·정확한 이어듣기·선택적 음성 명령 | 단절 중 캐시 재생, 시간 표시 정확성, 미디어 키/Focus/차량 Suspend 통합 검증 | REQ-OFF-001~005, REQ-PLAY-004,006,007 / SCR-PLAYER-001 / FLW-PLAY-001, FLW-INTERRUPT-001 |

1~2는 위치·내비 SDK 신규 연동을 기다리지 않고 구현할 수 있다. 3의 SDK 가용성 확인은 병행하되 지원 불가를 가짜 위치로 감추지 않는다.

## 이번 변경과 검증

- 변경 파일: 이 문서만 추가. 실행 코드·SDK 버전·OpenAPI는 변경하지 않음.
- 수행: 저장소 PRD/Flow/Acceptance Criteria/OpenAPI, 플랫폼 Adapter/재생 서비스/의존성, 공식 SDK 문서 교차 확인.
- 제한: 프로젝트 콘솔의 SDK 사용 가능 상태, 신규 SDK Artifact/실행 호환성, 상세 Route 정보, 실제 차량의 위치·Focus·Suspend는 미검증.

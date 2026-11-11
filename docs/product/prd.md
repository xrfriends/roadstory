# 제품 요구사항 문서(PRD)

## 1. 목적

Road Story MVP의 기능·비기능 요구사항, 주행 제한, Analytics Event, 외부 의존성을 정의한다.

## 2. 제품 목표

- 검색 없이 검증된 지역 Story를 제공한다.
- 차량 환경에서 안전한 UX를 제공한다.
- 위치 기반 Audio의 반복 사용 가치를 검증한다.
- 실제 결제 가능한 MVP로 지불 의향을 검증한다.
- 재사용 가능한 Content, Route, Analytics Infrastructure를 구축한다.

## 3. 기능 요구사항

### 3.1 Onboarding 및 Consent

| ID | 요구사항 | 우선순위 |
|---|---|---|
| `REQ-ONB-001` | 핵심 가치를 3단계 이내로 설명한다. | Must |
| `REQ-ONB-002` | 위치 Permission 요청 전에 사용 목적을 설명한다. | Must |
| `REQ-ONB-003` | 초기 관심 Category를 선택할 수 있다. | Must |
| `REQ-ONB-004` | 상세 Content는 정차 또는 Mobile에서 확인함을 안내한다. | Should |
| `REQ-ONB-005` | 관심 Category 선택을 건너뛸 수 있다. | Must |

### 3.2 자유 주행 듣기(Free Drive)

| ID | 요구사항 | 우선순위 |
|---|---|---|
| `REQ-PLAY-001` | 이동 경로 주변의 재생 가능 Story를 탐색한다. | Must |
| `REQ-PLAY-002` | 위치, 방향, 속도, 청취 이력, Preference를 고려한다. | Must |
| `REQ-PLAY-003` | Auto-play가 켜져 있으면 다음 적합 Story를 자동 재생한다. | Must |
| `REQ-PLAY-004` | 재생, 일시정지, 다음, 저장, 관심 없음, 종료를 제공한다. | Must |
| `REQ-PLAY-005` | `cooldown` 이내 동일 Story를 반복 재생하지 않는다. | Must |
| `REQ-PLAY-006` | Navigation 안내·통화 중 Audio를 pause 또는 duck 처리한다. | Must |
| `REQ-PLAY-007` | 사용자가 종료하지 않았다면 Interrupt 후 안전하게 Resume한다. | Should |
| `REQ-PLAY-008` | 다음 Story를 재생 전 Preview한다. | Should |
| `REQ-PLAY-009` | Auto-play를 끌 수 있다. | Must |

### 3.3 목적지까지 이야기(Destination Stories)

| ID | 요구사항 | 우선순위 |
|---|---|---|
| `REQ-ROUTE-001` | 현재 위치, 목적지, Route Context를 조회한다. | Must |
| `REQ-ROUTE-002` | 남은 시간에 맞는 `DestinationPlaylist`를 생성한다. | Must |
| `REQ-ROUTE-003` | Story를 Route 진행 순서로 정렬한다. | Must |
| `REQ-ROUTE-004` | 안전하지 않은 우회를 요구하는 Story는 제외한다. | Must |
| `REQ-ROUTE-005` | `SHORT`, `STANDARD`, `FAMILY` 모드를 제공한다. | Should |
| `REQ-ROUTE-006` | 유의미한 Re-route 시 이후 Story를 재구성한다. | Should |
| `REQ-ROUTE-007` | 가능하면 목적지 도착 전에 마지막 Story를 종료한다. | Must |

### 3.4 테마 드라이브(Theme Drive)

| ID | 요구사항 | 우선순위 |
|---|---|---|
| `REQ-COURSE-001` | Published ThemeCourse 목록을 제공한다. | Must |
| `REQ-COURSE-002` | 시간, 거리, Stop 수, Story 수, 적합성을 표시한다. | Must |
| `REQ-COURSE-003` | 첫 Course Stop으로 Navigation을 시작한다. | Must |
| `REQ-COURSE-004` | 도착 후 다음 Stop을 제안한다. | Must |
| `REQ-COURSE-005` | Course 종료와 Resume를 지원한다. | Should |
| `REQ-COURSE-006` | Course Offline 준비를 지원한다. | Should |
| `REQ-COURSE-007` | 명시적 확인 없이 모든 Stop을 Waypoint로 추가하지 않는다. | Must |

### 3.5 우리 동네(My Neighborhood)

| ID | 요구사항 | 우선순위 |
|---|---|---|
| `REQ-AREA-001` | 자주 방문하는 지역을 저장한다. | Should |
| `REQ-AREA-002` | 미청취·신규 Story를 지역별로 표시한다. | Should |
| `REQ-AREA-003` | 지역별 청취 진행률을 표시한다. | Could |
| `REQ-AREA-004` | 동의한 사용자에게만 신규 Story 알림을 보낸다. | Could |

### 3.6 Story 상세와 신뢰성

| ID | 요구사항 | 우선순위 |
|---|---|---|
| `REQ-STORY-001` | 모든 `PUBLISHED` Story에 하나 이상의 Source가 있어야 한다. | Must |
| `REQ-STORY-002` | Source 기관, 검수일, `VerificationStatus`를 표시한다. | Must |
| `REQ-STORY-003` | 사실, 해석, 전설을 구분한다. | Must |
| `REQ-STORY-004` | 가능한 경우 30초·60초 Audio Variant를 제공한다. | Must |
| `REQ-STORY-005` | 전문과 Image는 정차 또는 Mobile에서만 제공한다. | Must |
| `REQ-STORY-006` | Content 오류 신고를 지원한다. | Must |
| `REQ-STORY-007` | 정정 Story는 Audit History를 보존하며 `RETIRED` 처리할 수 있다. | Must |

### 3.7 저장, 이력, Preference

| ID | 요구사항 | 우선순위 |
|---|---|---|
| `REQ-USER-001` | Story를 저장할 수 있다. | Must |
| `REQ-USER-002` | 청취 이력을 제공한다. | Must |
| `REQ-USER-003` | 관심 Category Preference를 저장한다. | Must |
| `REQ-USER-004` | 특정 Category를 줄이거나 제외할 수 있다. | Must |
| `REQ-USER-005` | 로그인 후 저장·이력·Preference·Purchase를 Sync한다. | Should |

### 3.8 Offline 및 Resilience

| ID | 요구사항 | 우선순위 |
|---|---|---|
| `REQ-OFF-001` | 향후 10~20분의 재생 후보를 Cache한다. | Should |
| `REQ-OFF-002` | Network 단절 시 Download Audio를 재생한다. | Must |
| `REQ-OFF-003` | Location Confidence가 낮으면 신규 Auto Trigger를 중단한다. | Must |
| `REQ-OFF-004` | Offline 상태를 명확하게 표시한다. | Must |
| `REQ-OFF-005` | Playback을 막지 않고 실패한 Sync를 재시도한다. | Should |

### 3.9 Monetization Experiment

| ID | 요구사항 | 우선순위 |
|---|---|---|
| `REQ-PAY-001` | 무료 Content 이용량을 제공한다. | Must |
| `REQ-PAY-002` | 월간, 연간, Course Purchase 상품을 실험한다. | Must |
| `REQ-PAY-003` | Paywall, Purchase, Cancel, Renewal Event를 기록한다. | Must |
| `REQ-PAY-004` | Purchase Status를 Server에서 검증한다. | Must |
| `REQ-PAY-005` | Purchase Restore를 지원한다. | Must |
| `REQ-PAY-006` | 주행 중 Blocking Paywall을 표시하지 않는다. | Must |

### 3.10 Content Operations

| ID | 요구사항 | 우선순위 |
|---|---|---|
| `REQ-CMS-001` | Place와 Story를 생성·수정한다. | Must |
| `REQ-CMS-002` | Source와 Usage Rights를 연결한다. | Must |
| `REQ-CMS-003` | Trigger Radius, Direction, Lead Distance를 설정한다. | Must |
| `REQ-CMS-004` | AI Draft를 지원하되 자동 발행하지 않는다. | Should |
| `REQ-CMS-005` | Reviewer Approval Workflow를 제공한다. | Must |
| `REQ-CMS-006` | Publish, Unpublish, Retire, Versioning을 지원한다. | Must |
| `REQ-CMS-007` | Story별 Completion, Skip, Report 지표를 제공한다. | Should |
| `REQ-CMS-008` | 외부 공공 데이터 원본을 출처·라이선스·수집 이력과 함께 보존하고, Story 생성·발행과 분리한다. | Must |

## 4. 비기능 요구사항

| ID | 요구사항 |
|---|---|
| `REQ-NFR-001` | 외부 Route Provider 시간을 제외한 API P95는 500ms 이내를 목표로 한다. |
| `REQ-NFR-002` | Cache Audio는 2초 이내 재생 시작을 목표로 한다. |
| `REQ-NFR-003` | `PUBLISHED`가 아닌 Story를 Consumer API에 노출하지 않는다. |
| `REQ-NFR-004` | 원본 위도·경도를 Application Log에 기록하지 않는다. |
| `REQ-NFR-005` | Location History 보존을 최소화한다. |
| `REQ-NFR-006` | User-facing API는 인증된 HTTPS를 사용한다. |
| `REQ-NFR-007` | 한국어 우선, 향후 Localization 가능 구조를 사용한다. |
| `REQ-NFR-008` | 핵심 Playback·Purchase Event를 관측할 수 있어야 한다. |
| `REQ-NFR-009` | `docs/ux/accessibility.md`를 준수한다. |
| `REQ-NFR-010` | Problem Details와 안정적인 Error Code를 사용한다. |

## 5. 주행 제한

### 주행 중 허용

AAOS가 주행 제한을 요구하면 차량 앱은 재생·일시정지, 다음, 저장, 관심 없음, 종료만 제공한다. 제한 상태 확인 전, 차량 서비스 연결 단절, 조회 또는 관측 실패에도 같은 정책을 적용한다.

### 정차 상태에서만 허용

앱 메뉴·설정·내 이야기, 이전 Story, 재생 위치 이동, 다음 Story 상세 미리보기, ThemeCourse 시작·편집, 긴 Text, Image 탐색, Text Input, 상세 Filter, Source 원문, 결제 관리, 상세 신고

## 6. Analytics Event

```text
app_opened
onboarding_completed
permission_result
free_drive_started
story_impression
story_started
story_completed
story_skipped
story_saved
story_disliked
destination_playlist_created
theme_course_viewed
theme_course_started
theme_course_completed
paywall_viewed
plan_selected
trial_started
purchase_completed
subscription_cancelled
subscription_renewed
content_error_reported
```

## 7. 외부 의존성

PLEOS 또는 Device Location, Navigation Destination·Route, Route Provider, Audio Storage/CDN, PostgreSQL/PostGIS, Purchase Provider, 검수 Content Pipeline

## 8. 미결정 사항

- PLEOS Route Geometry 제공 범위
- Navigation Interrupt Callback
- Pilot 지역
- Content License
- Subscription과 Course Purchase 우선순위
- Background Location 관련 법적 요구사항

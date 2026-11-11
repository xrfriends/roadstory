# Android 주변 이야기 선호 반영 검증

후속 작업 4번. 차량에 저장된 관심 주제, 가족 우선, 관심 없음이 주변 재생의 첫 후보와 이후 후보에 적용된다.

- 요구사항: `REQ-PLAY-002~004`, `REQ-USER-003~004`
- 화면: `SCR-SETTINGS-001`, `SCR-PLAYER-001`, `SCR-DRIVE-LIMIT-001`, `SCR-EMPTY-001`
- 흐름: `FLW-NAV-001`, `FLW-FREE-001`, `FLW-PLAY-001`
- 인수 조건: `AC-PLAY-002`, `AC-PLAY-006`; 테스트 시나리오: `TS-PLAY-016~017`, `TS-DRIVE-005`

| 변경 파일 | 적용 내용 |
|---|---|
| `contracts/openapi.yaml`, `frontend/web-harness/src/generated/api.ts`, Backend `Story.java`, `StoryController.java`, `InMemoryStoryRepository.java` | 기존 공개 Story의 주제와 선택 필드 `familyFriendly` 제공. 명시적으로 `true`인 후보만 가족 우선을 받는다. 기존 공개 Story는 분류하지 않았으며 가족 우선 검증용 분류는 기존 로컬 데모에만 부여했다. |
| `StoryApiClient.kt`, `VehicleUserStore.kt` | 주제와 가족 적합 표시를 읽는다. 차량의 관심 없음 기록에 Story ID와 알 수 있는 주제를 저장한다. 기존 ID만 있는 기록은 그대로 적용한다. |
| `NearbyStoryPreferences.kt`, `PlaybackCoordinator.kt`, `RoadStoryPlaybackService.kt`, `PlaybackSessionContract.kt` | 남은 후보에 선호 규칙을 적용하고 MediaSession의 다음 제목과 다음 조작 가능 여부를 갱신한다. 필터로 인한 빈 상태를 별도로 전달한다. |
| `MainActivity.kt`, `RoadStoryScreens.kt` | 정차 플레이어에 관심 없음 버튼과 필터로 인한 빈 상태 안내를 추가한다. 정차·주행 양쪽에서 화면에 표시된 이야기의 ID를 기록하고 주행 중에는 기존 다섯 조작을 유지한다. |
| Backend Story 계약·발행 테스트, Android 후보·파서·저장소·재생·화면 테스트, UX/검증 문서 | API 호환, 관심 없음 데이터 보존, 재생 중 설정 전환 및 명시적 재생 회귀를 검증한다. |

`끄기` 주제와 관심 없음 Story ID는 후보에서 제외한다. 가족 우선이 켜져 있으면 가족 적합 표시를 먼저 고려한다. 같은 우선순위 안에서는 관심 없음과 같은 주제는 뒤로, `자주`는 `가끔`보다 앞으로 둔다. 동점일 때는 서버 순서를 유지한다. 현재 재생은 유지하고 이미 선택했던 ID는 세션에서 다시 추천하지 않는다. 남은 후보는 보존하므로 설정을 되돌리면 아직 재생하지 않은 후보가 복구된다. 저장·청취 기록에서 사용자가 직접 지정한 ID는 필터와 관계없이 재생할 수 있다.

## 검증 결과

- Web: npm ci, lint, 테스트 7개, 프로덕션 빌드 성공. OpenAPI의 생성 타입을 갱신했다.
- E2E: npm ci 및 Playwright 시나리오 1개 성공.
- Backend: JDK 17 테스트 20개와 빌드 성공. 로컬 API에서 `INDUSTRY / familyFriendly=false`, `DAILY_LIFE / familyFriendly=true` 응답을 확인했다.
- Android: JVM 테스트 40개, `fakeDebug`, `pleosDebug`, 기기 검사 APK 빌드 성공.
- PLEOS: 가족 우선 및 수동 다음 2개가 첫 실행에서 통과했다. 이후 호스트의 에뮬레이터 프로세스가 멈춰 기기 연결이 끊겼다. Android Studio에서 기기를 재시작한 뒤 선호 변경 중 현재 재생 유지·다음 갱신·후보 모두 제외·명시적 재생, 로컬 저장, 설정과 주행 제한 화면 검사 11개가 통과했다.
- 종료 예약·자동 전환, 재생·일시정지·이력, 연속 한국어 합성 회귀 검사 3개가 통과했다. 총 16개 기기 시나리오를 분할 검증했다. `git diff --check`도 통과했다.

## 제한 및 다음 순서

- 주변 재생은 여전히 성수동 예시 위치를 사용한다. 차량이 가진 설정은 로컬에서만 적용하며 목적지·코스 편성과 서버 동기화는 별도 범위다.
- 기존 ID 전용 관심 없음 기록은 같은 Story만 제외한다. 주제가 확인된 기록부터 같은 주제 후보를 뒤로 보낸다.
- 운영에서 가족 우선을 활용하려면 실제 공개 Story에 가족 적합 표시가 필요하다. 이 단계는 가족 적합성을 자동 추론하지 않는다.
- 실제 차량과 실주행은 이번 단계에서 재검증하지 않았다.
- 다음은 실제 위치·내비 상태 및 코스 데이터를 재생 후보와 연결하는 단계다.

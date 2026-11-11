# Android 재생 큐와 자동 재생 종료 검증

후속 작업 3번. 현재 Story를 다시 재생하던 `다음`을 실제 공개 후보의 다음 ID로 연결하고, 현재 이야기 완료 후의 자동 전환을 제어한다.

- 요구사항: `REQ-PLAY-003`, `REQ-PLAY-004`, `REQ-PLAY-007~009`
- 화면: `SCR-HOME-001`, `SCR-PLAYER-001`, `SCR-DRIVE-LIMIT-001`
- 흐름: `FLW-FREE-001`, `FLW-PLAY-001`, `FLW-INTERRUPT-001`
- 인수 조건: `AC-PLAY-002`, `AC-PLAY-005`; 테스트 시나리오 `TS-PLAY-012~015`

| 변경 파일 | 적용 내용 |
|---|---|
| `PlaybackCoordinator.kt`, `RoadStoryPlaybackService.kt`, `PlaybackSessionContract.kt` | 서버 응답 순서 유지, ID 중복 제거, 개별 선택, 수동 다음, 완료 시 현재 설정 반영, 세션 종료 예약, 빈 큐·실패·재시도. MediaSession의 상태·현재 ID·다음 미리보기를 같은 세션에서 발행. |
| `AudioFocusController.kt`, 두 `StorySpeechPlayerFactory.kt` | 취소/종료한 이전 낭독의 콜백을 무시하고, 일시적인 Focus 상실 중 사용자 종료 시 재개 요청을 해제. PLEOS SDK 버전과 ko-KR 설정은 유지. |
| `StoryApiClient.kt`, `InMemoryStoryRepository.java`, `StoryControllerTest.java` | 기존 주변 후보/상세 계약 사용. 두 번째 합성 데모 Story는 `local` 단독 프로필에서만 제공하며 운영 또는 혼합 프로필에 노출하지 않는다. 새 역사 정보를 발행하지 않는다. |
| `MainActivity.kt`, `RoadStoryScreens.kt`, `ViewFactory.kt` | 주변 시작, 실제 다음 제목·예상 길이, 마지막의 다음 비활성화, 자동 재생 종료 예약 및 즉시 종료 표시. 주행 중은 기존 다섯 조작만 유지. |
| 큐·API JVM 테스트, `PlaybackQueueDeviceTest`, 주행/정차 UI 테스트 | 사용자 의도와 비동기 응답 경합, 서버 공개 필터, 실제 ID 전환·완료, 화면 동작 검증. |

OpenAPI와 외부 SDK는 변경하지 않았다. 앱은 기존 화면의 성수동 예시 위치로 주변 후보를 조회하며 각 낭독은 공개 상세 API에서 가져온다. 위치, 방향, 실제 내비 경로, 관심 주제 순위는 이 단계에서 추정하지 않는다. 시안의 고정 진행률 대신 서버가 제공한 예상 길이를 표시한다.

[Android의 Focus 콜백 전달](https://android.googlesource.com/platform/frameworks/base/+/f5098669d78516fd5ec9adfb1fb7dc7b2b2364bc/media/java/android/media/AudioManager.java#3745)은 리스너 ID를 다시 찾으므로, 새로운 요청은 새 리스너로 분리한다. 내비·통화 등에 의한 일시 중단 동안 재생 서비스는 포그라운드를 유지할 수 있고, 사용자 일시정지·종료는 대기 중인 재개를 취소한다.

## 검증 결과

- Web: npm ci / lint / 테스트 7개 / build 성공.
- Backend: JDK 17 테스트 19개 / build 성공. 로컬 API에서 `story-published-1`, `story-published-2` 응답 확인.
- E2E: npm ci / Playwright 1개 성공.
- Android: JVM 32개, fakeDebug·pleosDebug 및 기기 검사 APK 빌드 성공. 초기 빌드의 `onCustomAction` Kotlin 시그니처 오류를 설치 SDK에 맞춰 수정한 뒤 통과했다.
- PLEOS 재생 4개 통과: Auto-play OFF에서 수동 다음 → 두 번째 ID → 큐 끝에서 반복 없음, 현재 Story 종료 예약 → 완료 후 멈춤, 새 세션의 자연 완료 → 자동 다음, 기존 재생/이력/일시정지 및 연속 한국어 합성.
- PLEOS UI 묶음 9개 중 8개 성공(큐 미리보기·종료 명령·제한 화면·포커스·설정 포함). 나머지 메뉴 1개는 시스템 `ai.umos.homescreen`의 TaskView 리스너 중복 예외로 Activity가 파괴되어 중단됐다.
- 마지막 Focus 보완 이후 Android 단위 검사와 APK 빌드, 재생 4개와 메뉴 재실행 1개 모두 통과했다. 앞선 UI 8개와 합쳐 기기 시나리오 13개를 분할 확인했다. 이 단계에서 실차 Focus 재현, 주행 중 외부 미디어 전용 재시험, 캡처·저장소 단독 회귀는 별도로 실행하지 않았다.
- Android Studio의 Run으로 최신 `pleosDebug`를 설치하고, PLEOS 에뮬레이터 홈에서 성수동 예시 위치와 `주변 이야기 시작` 버튼을 확인했다. 내장 에뮬레이터 화면에 직접 입력을 전달하지 못해 버튼 조작과 재생 전환은 위 기기 자동화 결과로 검증했다.

## 제한 및 다음 순서

- 다음 Story 선택은 서버의 예시 위치 후보 순서다. 정차 시 보이는 목적지·코스의 예시 목록은 아직 실제 재생 큐를 구성하지 않는다.
- TTS의 정확한 위치와 이어듣기, 실차 Focus 검증은 후속 범위다. 일시정지 이후 TTS는 선택된 문장을 다시 시작할 수 있다.
- 다음 순서는 관심 주제·가족 우선·관심 없음 설정을 실제 후보 선택에 반영하는 작업이다.

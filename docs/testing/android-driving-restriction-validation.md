# Android 주행 제한 검증

후속 작업 2번. AAOS가 제공하는 현재 UX 규제와 변경 이벤트를 사용해 차량 화면과 조작을 제한한다. SDK, 외부 API 및 OpenAPI 계약 변경은 없다.

- 요구사항: `REQ-PLAY-004`, `REQ-STORY-005`
- 화면: `SCR-DRIVE-LIMIT-001`, `SCR-PLAYER-001`
- 흐름: `FLW-NAV-001`, `FLW-PLAY-001`, `FLW-SAVE-001`
- 인수 조건: `AC-PLAY-002`; 테스트 시나리오 `TS-DRIVE-001~007`, `TS-PLAY-012`

| 변경 | 설계와 검증 |
|---|---|
| `CarUxRestrictionSource.kt`, `DrivingRestriction.kt`, `FailClosedDrivingRestrictionSource.kt` | 차량 서비스 준비 전·단절·조회 실패에 제한 상태. 재연결 후 새 관찰을 등록한다. 최적화 플래그 또는 활성 규제 플래그가 있으면 제한한다. |
| `VehicleUiPolicy.kt`, `RoadStoryScreens.kt`, `MainActivity.kt` | 모든 경로를 짧은 주행 재생 화면으로 전환한다. 열린 메뉴를 닫고 정차 상태가 확인되면 이전 화면을 복구한다. 설정·탐색·개별 항목 조작은 실행 직전 다시 확인한다. |
| `RoadStoryPlaybackService.kt`, `VehicleUserStore.kt` | 외부 MediaSession의 ID 지정 선택은 정차에서만 실행한다. 종료는 요청과 재개를 취소하고 리소스를 해제한다. `관심 없음`은 현재 Story의 로컬 표시다. |
| `VehicleUiPolicyTest`, `VehicleAppMenuTest`, `VehicleDrivingRestrictionDeviceTest`, `DrivingMediaCommandDeviceTest` | 화면 매트릭스, 메뉴 닫힘·설정 복구, 이전 콜백, 두 화면 크기의 버튼, 실제 규제 상태에서 외부 미디어 명령을 검증한다. |

AAOS 차량 상태는 속도·기어를 추측하지 않고 [CarUxRestrictionsManager](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager)의 신호를 사용한다. [차량 서비스 연결 상태](https://developer.android.com/reference/android/car/Car.CarServiceLifecycleListener)도 함께 관찰한다.

## 검증 결과

- Web: npm ci / lint / 테스트 7개 / build 성공.
- Backend: JDK 17 test / build 성공(기존 Gradle 테스트 결과 17개 재사용).
- E2E: npm ci / Playwright 1개 성공.
- Android: JVM 15개, fakeDebug·pleosDebug 및 기기 검사 APK 빌드 성공.
- PLEOS 에뮬레이터: 전체 14개 중 13개 통과(새 주행 제한 UI 3개 포함). 콜드 부팅 직후 음성 완료 1개는 CAAS `TextToSpeechConfig.dat` 준비 오류로 실패했고, 동일 부팅에서 재실행하여 연속 한국어 합성 완료까지 통과했다.
- 실제 주행 규제 미디어 검사 1개 추가 통과: 제한 플래그가 켜진 상태에서 외부 `playFromMediaId`의 존재하지 않는 ID를 무시하고, 일반 재생은 현재 공개 Story의 `PLAYING`, 종료는 `STOPPED`에 도달했다. 초기 13개와 재실행 2개를 합쳐 총 15개 시나리오를 확인했다.
- Android Studio에서 `pleosDebug` 설치·실행. [PLEOS 가상 콘솔](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)에서 P → D로 전환해 가속(콘솔 28km/h)하자 앱은 제목·상태·다섯 조작만 표시했다. 정지 후 P로 전환하자 같은 홈 화면으로 복귀했다. 검사 후 가상 차량은 P, 0km/h이고 앱이 열려 있다.
- `git diff --check` 성공.

외부 미디어 검사는 로컬 Story API가 실행 중인 PLEOS 에뮬레이터에서 실제 규제 플래그가 활성화된 뒤 아래처럼 지정한다. 기본 정차 회귀 실행에는 포함하지 않는다.

```bash
cd frontend/pleos-app
./gradlew connectedPleosDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.roadstory.pleos.playback.DrivingMediaCommandDeviceTest \
  -Pandroid.testInstrumentationRunnerArguments.verifyDrivingMediaSession=true
```

## 제한 및 다음 순서

- 다음 버튼은 기존 재생 서비스 동작을 호출한다. 다른 Story 선택, 자동 재생 큐, 자동 재생 종료 시점은 후속 작업 3번이다.
- 관심 없음은 로컬 저장만 구현했다. 추천 순위 변경, 서버 동기화 및 실차 검증은 별도 범위다.
- 초기 실패·늦은 콜백은 단위 검사에서 검증했다. 차량 시스템 서비스를 강제로 중단하거나 실차 단절·재연결을 일으키지는 않았다. 에뮬레이터에서는 콜드 부팅 후 음성 엔진 준비 지연이 재발할 수 있다.

# Android 기기 화면·한국어 재생 재검증

## 목적과 연결

후속 작업 1번인 실제 SDK를 사용하는 PLEOS 에뮬레이터의 메뉴·설정·내 이야기·재생을 재검증한다. 이전의 흰 화면과 앱 자체 오류를 구분하고, 화면 확인 중 발견한 오류를 수정한다.

- Requirement: `REQ-USER-001~004`, `REQ-PLAY-004`
- Screen: `SCR-APP-MENU-001`, `SCR-SETTINGS-001`, `SCR-SAVED-001`, `SCR-PLAYER-001`
- Flow: `FLW-NAV-001`, `FLW-SAVE-001`, `FLW-PLAY-001`
- Acceptance: `AC-PLAY-002`
- Test Scenario: `TS-PLAY-010`, `TS-PLAY-011`, `TS-DRIVE-002`
- 환경: Android Studio Running Devices, Pleos Connect v2.0 / Android 14 / arm64, 실제 `pleosDebug` 패키지 `com.roadstory.pleos`, PLEOS TTS 2.1.5.1.

## 발견과 변경

1. **한국어 음성이 시작 후 실패**: `Locale.KOREAN`은 언어만 지정하여 엔진에서 `unsupported locale: ko-`로 실패했다. `StorySpeechPlayerFactory.kt`에서 `Locale.KOREA`(ko-KR)로 지정했다. 기존 `onStart`만 기다리던 검사를 동일 엔진으로 연속 두 문장의 `onDone`과 오류 부재까지 검증하도록 강화했다.
2. **저장 후 키보드 포커스 이동**: 저장 상태에 따라 접근성 문구가 바뀌면 기존 문구 기반 복원이 헤더로 이동했다. `MainActivity.kt`, `RoadStoryScreens.kt`, `ViewFactory.kt`에서 버튼의 안정적인 태그를 우선 사용하고 접근성 문구는 상태에 맞게 계속 갱신한다. 새 `VehicleFocusDeviceTest`는 두 번 저장을 전환하며 포커스와 원래 저장 상태를 확인한다.
3. **초기 프레임과 플랫폼 연결 실패**: `MainActivity`가 플랫폼 관찰 전 첫 프레임을 렌더링하고, 새 `FailClosedDrivingRestrictionSource`가 생성·조회·등록 예외와 지연/종료 후 콜백에서 제한 상태를 유지한다. 등록 중 안전 해제 콜백 뒤 예외가 나도 안전 해제를 전달하지 않는다. JVM 회귀 7개를 추가했다. 이는 과거 흰 화면의 원인이 입증됐다는 의미가 아니다.

외부 API·OpenAPI·PLEOS SDK 버전 변경은 없다. `docs/ux/screen-specs.md`에 초기 렌더링·실패 시 제한·포커스·한국어 완료 검증을 반영했다.

| 변경 파일 | 역할 |
|---|---|
| `MainActivity.kt`, `ui/RoadStoryScreens.kt`, `ui/ViewFactory.kt` | 초기 프레임, 규제 소스 격리, 동적 접근성 문구의 포커스 유지 |
| `platform/FailClosedDrivingRestrictionSource.kt`, 해당 JVM 테스트 | 규제 소스 실패·지연·종료 보호와 7개 회귀 |
| `src/pleos/.../StorySpeechPlayerFactory.kt`, `src/androidTestPleos/.../StorySpeechPlayerDeviceTest.kt` | ko-KR 및 연속 시작·완료·오류 검증 |
| `src/androidTest/.../VehicleFocusDeviceTest.kt` | 터치 후 키보드 모드와 저장 상태 변경의 포커스 회귀 |
| 화면 명세·테스트 시나리오·이 검증 문서·기존 검증 문서의 후속 링크 | 요구사항과 결과 연결 |

## Studio 직접 조작

Studio의 Hardware Input을 통해 키보드로 실제 앱을 조작했다.

- 헤더 메뉴의 아이콘·선택 배경·체크와 4개 목적지를 확인했다.
- 설정의 관심 주제/청취 설정 2열, 주제 ‘끄기’, 자동 재생 OFF, 가족 우선 ON의 화면 반영을 확인했다.
- 내 이야기의 저장·코스·청취 기록 탭, 빈 상태, 실제 기록 `성수동 수제화 거리`를 확인했다.
- 기록의 저장으로 저장 개수가 0→1로 갱신되는 것을 확인했다.
- 재생에서 실제 Story API 제목이 표시됐으나 음성 오류가 발생하여 위 ko-KR 수정을 적용했다. 수정 후 수동 재생 화면 재검증은 아래 최종 상태에 기록한다.

## 자동 검증과 인프라 중단

- Web: npm ci / lint / test 7개 / build 성공.
- Backend: JDK 17 test / build 성공. 테스트 17개, Gradle 결과 재사용.
- E2E: npm ci / Playwright 1개 성공.
- Native: JVM 15개 성공. fakeDebug·pleosDebug 및 기기 테스트 APK 컴파일 성공.
- 실제 PLEOS 지정 검사 2개 성공: 연속 한국어 2문장 완료·오류 없음, API→PLAYING→실제 청취 기록→PAUSED.
- 전체 11개 기기 검사 시도는 6개 완료 통과 후 7번째에서 기기 연결이 끊겼다. 새 포커스 회귀는 완료 통과했다. `libraryTabsUseActualVehicleStoreCounts`의 빈 실패 표시는 Assertion 실패로 확정할 수 없다.
- Studio 로그에서 qemu exit 139(SIGSEGV), macOS crash report에서 AppleMetalOpenGLRenderer→gfxstream `readPixelsScaled`→`getColorBufferScreenshot` 경로가 확인됐다. 에뮬레이터 호스트 그래픽 충돌로 분류한다. 과거 흰 화면과 같은 원인이라고 단정하지 않는다.
- 수동 검증 재개 중 00:46에도 같은 exit 139가 반복됐다. Studio Device Manager의 Additional settings에서 Graphics acceleration을 Automatic→Software로 변경해 비교한다. Cold boot, RAM 4GB, 기존 시스템 이미지 경로와 앱 데이터는 유지했다. 설정 저장 뒤 `hw.gpu.mode=software`를 읽어 확인했다.
- 재부팅 후 실제 Graphics Adapter도 Apple M1 OpenGL Translator에서 ANGLE/SwiftShader로 바뀐 것을 Studio 로그에서 확인했다. Studio의 Active Build Variant는 `pleosDebug`로 확인했다.
- 재시작 후 `VehiclePreferencesDeviceTest,VehicleUserStoreDeviceTest` 지정 검사 5개 모두 성공했다. 재시도 첫 명령에는 저장소 클래스 패키지를 잘못 지정해 로딩 오류가 있었고, 올바른 `com.roadstory.pleos.ui` 경로로 고친 최종 명령에서 성공했다.
- 최신 `installPleosDebug` 성공. `git diff --check` 성공.
- Software 모드 전체 재실행은 기기 종료 없이 11개를 완료했다(9개 성공, 2개 실패). 한국어 완료 검사는 CAAS의 `airspeech/TextToSpeechConfig.dat` 누락으로 실패했다. 포커스 검사는 터치 모드에서 최초 `requestFocus()`가 실패하여, `requestFocusFromTouch()`로 키보드 포커스 모드를 명시하도록 보완했다. 저장 후 포커스 복원 assertion이 실패한 것은 아니다. 보완 후 결과는 최종 상태에 기록한다.
- 보완한 `VehicleFocusDeviceTest` 지정 재실행 1개 성공. 전체 실행에서 통과한 9개와 별도로 기록하며 전체 11개 일괄 성공이라고 보고하지 않는다.

## 최종 재검증 상태

SwiftShader에서는 전체 11개 실행 동안 호스트 그래픽 충돌이 재현되지 않았다. 기존 GPU 충돌의 영구 해결을 보장하는 결과는 아니다.

한국어 완료 검사는 재부팅 직후 CAAS 환경에서 `ON_DEVICE_ENGINE_ERROR`로 한 번 실패했다. 시스템 로그의 직접 원인은 엔진 구성 파일 `airspeech/TextToSpeechConfig.dat`의 `ErrorNotExists`다. 이후 동일 부팅에서 수동 재생·일시정지가 정상 전환됐고, `StorySpeechPlayerDeviceTest` 최종 지정 재실행도 성공했다(연속 두 문장 모두 시작·완료, 오류 없음). 모델 파일이나 SDK를 바꾸지 않고 성공했으므로 일시적인 엔진 준비 상태로 분리한다. 구체적인 파일 준비 시점과 모든 cold boot의 안정성을 입증한 것은 아니다.

공식 [TTS 개요](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro)는 ON_DEVICE가 네트워크·앱 등록 없이 동작한다고 설명하며, [오류 코드](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/TextToSpeech/error-code)는 엔진 상태 확인을 안내한다. 설치된 2.1.5.1에는 모델 복구 public API가 없고 공개 문서에서도 해당 파일을 앱이 직접 복구하는 절차를 찾지 못했다. 임의 파일 생성·복사, 서비스 데이터 초기화, 서버 음성 전환은 하지 않았다.

Studio의 `pleosDebug` Run에서 최신 APK 설치·실행을 확인했다. Hardware Input으로 Player에서 재생을 요청해 실제 API 제목 `성수동 수제화 거리`, ‘이야기 준비 중’→‘지금 듣는 이야기’와 일시정지 아이콘을 확인했다. 같은 조작을 다시 눌러 ‘이어들을 이야기’·재생 아이콘으로 돌아왔으며 MediaSession도 `PAUSED(2), error=null`이었다. 스피커 음량·음질과 정확한 이어듣기 위치를 검증한 결과는 아니다.

최종 결과: JVM 15개, 기기 11개 시나리오를 분할 실행해 모두 성공을 확인했다. Software 전체 실행의 초기 9/11 성공 기록, 보완 후 포커스 1개 성공, 환경 준비 후 한국어 완료 1개 성공을 합친 결과이며, 최종 전체 11개를 한 번에 성공했다고 해석하지 않는다. Web 7개·Backend 17개·E2E 1개와 lint/build도 성공했다. 최신 pleosDebug APK는 설치되어 있다.

## 제한과 다음 순서

- 차량 로컬 저장과 화면 반영까지 검증한다. 추천에 주제/가족 선호 반영, 자동 다음 큐, 서버 동기화, 실제 진행 시간은 아직 별도 후속 작업이다.
- Software 렌더러는 이 환경의 진단/검증용 우회다. 실차·스피커 품질·모든 cold boot 안정성을 검증한 결과는 아니다. 음성 준비 지연은 오류/재시도 상태로 처리하며 성공하지 않은 합성을 완료로 표시하지 않는다.
- 현재 작업 트리의 release 변형은 debug signing 설정을 사용한다. 이번 검증은 pleosDebug이며, 배포용 release 서명 구성은 별도로 정리해야 한다. 기존 작업 트리의 다른 변경은 되돌리지 않았다.
- 후속 작업 2번은 모든 탐색 화면의 주행 안전 제한 보완이다. 이 문서의 시작 실패 보호만으로 전체 안전 정책이 완성됐다고 간주하지 않는다.

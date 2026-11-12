# Road Story (로드 스토리)

Road Story는 차가 지나가는 장소의 검수된 지역 이야기를 짧은 오디오로 들려주는 Pleos Connect 차량 앱입니다.

현대자동차그룹 차량용 앱 개발 공모전 출품팀은 **XRfriends**입니다.

## Pleos Connect에서 실행

| 항목 | 설정 |
| --- | --- |
| 제출 APK | [XRfriends_RoadStory_release.apk](apk/XRfriends_RoadStory_release.apk) |
| 차량 앱 | Kotlin · Android Automotive, `com.roadstory.pleos`, 버전 0.1.0 |
| 에뮬레이터 | Pleos Connect 2.1.0 이미지 · Android 14 (API 34) ARM64, AVD `Pleos_Connect_v2.0` |
| 화면 | 2560 × 1440 가로 화면, 160 dpi, 분할 화면 앱 패널 1682 × 1210 |
| 호스트 | JDK 17, Android SDK Platform Tools (`adb`) |
| 앱 빌드 | Android SDK Platform 34, Gradle Wrapper 8.13, Android Gradle Plugin 8.13.2 |

1. 저장소 루트에서 공개 데모 API를 시작합니다. 첫 Gradle 실행에는 의존성을 내려받을 인터넷 연결이 필요합니다. 이 구성은 데이터베이스, Redis, 계정, API 키 없이 시작합니다.

   ```bash
   cd backend
   ROAD_STORY_AUDIO_DIR="$PWD/../demo-audio" \
     ./gradlew bootRun --args='--spring.profiles.active=local,contest --server.address=127.0.0.1'
   ```

   서버 상태는 호스트에서 `curl -fsS http://127.0.0.1:8080/actuator/health`로 확인할 수 있습니다. 에뮬레이터는 같은 호스트의 `10.0.2.2:8080`으로 연결합니다.

2. Pleos Connect AVD를 실행한 뒤 다른 터미널에서 저장소 루트로 이동해 APK를 설치하고 실행합니다.

   ```bash
   adb install -r apk/XRfriends_RoadStory_release.apk
   adb shell am start -n com.roadstory.pleos/.MainActivity
   ```

   APK는 에뮬레이터 설치용 개발 인증서로 서명했습니다. Release에서 HTTP는 에뮬레이터의 호스트 주소 `10.0.2.2`에 허용합니다.

3. 정차 상태에서 홈을 스크롤해 **취향 따라, 테마 드라이브 → 성수 산업유산 드라이브 → 코스 이야기 듣기**를 선택합니다. **성수동 수제화 거리**의 재생 상태를 확인하고, 일시정지·이어듣기·저장 후 **내 이야기**에서 저장한 항목을 확인합니다. 이 흐름은 위치 권한과 내비게이션 안내를 설정하지 않아도 재현할 수 있습니다.
4. 주행 상태로 바꾸면 화면 탐색이 제한되고 재생 조작이 남습니다. 정차 상태로 돌아오면 코스와 저장 목록을 다시 열 수 있습니다. 주변 이야기를 시험할 때는 위치 권한을 허용하고 에뮬레이터 위치를 성수역 근처로 설정합니다.

## 소스에서 빌드

```bash
cd frontend/pleos-app
./gradlew assemblePleosRelease
```

빌드 결과는 `app/build/outputs/apk/pleos/release/`에 생성됩니다. Pleos SDK 의존성은 [Pleos Playground Maven 저장소](https://nexus-playground.pleos.ai/repository/maven-releases/)에서 가져옵니다. 차량 앱은 Vehicle 2.0.3, NaviHelper 3.0.5, TextToSpeech 2.1.5.1, FusedLocation 1.1.3과 Android Automotive 주행 제한 API를 사용합니다. 위치 기능의 제공 여부는 기기 지원 상태에 따릅니다.

내비게이션 안내가 연결되면 앱은 목적지와 남은 시간을 받아 도착 전 들을 수 있는 이야기를 편성합니다. 현재 차량 연동의 편성 기준은 목적지 주변입니다. 비회원 보관함은 앱에 저장됩니다. 기본 데모는 공개 이야기 본문을 AI로 낭독한 [제출용 음원](demo-audio/)을 사용합니다. 이 음원으로 파일 재생과 오프라인 저장을 시험할 수 있습니다. 파일이 연결되지 않은 이야기는 Pleos TTS 서비스가 준비된 환경에서 재생됩니다.

| 참고 자료 | 내용 |
| --- | --- |
| [개발 환경](docs/development.md) | 소스 실행과 테스트 |
| [API 계약](contracts/openapi.yaml) | 공개 콘텐츠, 재생 편성, 보관함 |
| [에뮬레이터 검증](docs/testing/pleos-emulator-validation.md) | 차량 화면과 주행 제한 |
| [주행 후보 검증](docs/testing/android-journey-candidates-validation.md) | 위치, 내비게이션, 코스 |
| [음원 검증](docs/testing/android-file-audio-validation.md) | 파일 음원, 이어듣기, 오프라인 |
| [사진 출처](docs/decisions/ADR-005-figma-make-pleos-sync.md) · [글꼴 라이선스](frontend/pleos-app/app/src/main/res/raw/asta_sans_ofl.txt) | 차량 화면에 포함한 에셋 |

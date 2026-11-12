# 개발 환경

이 문서는 `REQ-NFR-003`, `REQ-NFR-004`, `REQ-NFR-010`과 `FLW-FREE-001`, `FLW-PLAY-001`, `FLW-SAVE-001`을 검증할 수 있는 로컬 기반을 설명한다.

## 필수 도구

- Node.js 22 이상과 npm
- JDK 17 이상(Backend Java 및 PLEOS Kotlin 빌드)
- Docker Desktop 또는 Docker Engine + Compose
- Android SDK Platform 34, Build Tools 35.0.0, JDK 17(Pleos App Build 시)

## 실행 순서

1. `.env.example`을 참고해 로컬 환경 변수를 설정한다. `.env`는 Commit하지 않는다.
2. `docker compose up -d`로 PostgreSQL/PostGIS와 Redis를 실행한다.
3. `cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'`을 실행한다.
4. 별도 Terminal에서 `cd frontend/web-harness && npm ci && npm run dev`를 실행한다.
5. `http://localhost:5173`을 연다. Backend Health Check는 `http://localhost:8080/actuator/health`다.

Backend API 문서는 `http://localhost:8080/swagger-ui.html`에서 확인한다. 동적으로 생성된 OpenAPI JSON은 `http://localhost:8080/v3/api-docs`에서 제공된다. API 변경의 기준은 계속 `contracts/openapi.yaml`이며 Swagger UI는 구현 상태 확인과 수동 호출에 사용한다.

기본 `local` 프로필은 비회원 공개 API를 실행하며 Database와 Redis 없이 사용할 수 있다. 계정별 서버 보관함을 검증하려면 `local-sync` 프로필을 사용한다.

## 서버 보관함 개발 검증

`local-sync`는 PostgreSQL에 저장하고 Flyway 마이그레이션을 적용한다. 테스트용 Bearer 토큰 두 개를 서버 프로세스에 제공한다. 토큰은 각자 새로 생성한 32바이트 난수의 16진수 값이며 저장소·테스트 출력에 기록하지 않는다.

```bash
docker compose up -d postgres
export SPRING_PROFILES_ACTIVE=local-sync SERVER_PORT=18080
export ROAD_STORY_DEV_ACCOUNT_A_TOKEN="$(openssl rand -hex 32)"
export ROAD_STORY_DEV_ACCOUNT_B_TOKEN="$(openssl rand -hex 32)"
cd backend && ./gradlew bootRun
```

- 차량 앱은 `frontend/pleos-app`에서 `./gradlew -PROAD_STORY_DEV_SYNC=true -PROAD_STORY_API_BASE_URL=http://10.0.2.2:18080 assemblePleosDebug`로 빌드한다. 정차 중 내 이야기에서 `개발 계정 연결`로 접속한다.
- Web Harness는 `VITE_DEV_LIBRARY_SYNC=true VITE_DEV_LIBRARY_SYNC_API_PORT=18080 npm run dev -- --host 127.0.0.1`로 실행하고 `http://127.0.0.1:5173/dev/library`를 연다. 같은 계정 토큰으로 저장 목록을 확인한다.
- 두 토큰과 `VITE_DEV_LIBRARY_SYNC=true`, `VITE_DEV_LIBRARY_SYNC_API_PORT=18080`을 E2E 프로세스에 전달하면 로컬 서버를 쓰는 보관함 검사가 실행된다.
- `DevelopmentLibrarySyncDeviceTest`는 `verifyDevelopmentLibrary=true`와 앱 전용 `files/library-sync-test-credentials`의 테스트 토큰 두 줄을 사용한다. 전용 개발 계정으로 실행하면 테스트는 파일을 읽은 직후 삭제하고 선택한 비회원 저장의 가져오기, 계정별 이야기·코스·청취 이력을 검사한다. 이 테스트를 먼저 실행한 뒤 E2E에 `ROAD_STORY_VERIFY_DEVICE_HISTORY=true`를 전달하면 웹에서 차량 청취 이력도 검사한다.
- 운영용 Pleos Account 연결은 Road Story 프로젝트에서 허용된 인증 절차와 서버 검증 방법을 확보한 뒤 적용한다. 비회원 공개 재생과 차량 내 저장은 기본 앱에서 사용할 수 있다.

2026-11-11 검증에서 Pleos Connect 2.0 에뮬레이터의 개발 계정 보관함·비회원 로컬 저장·재생·화면 검사 8개가 통과했다. PostgreSQL에 저장된 차량의 청취 이력을 웹에서 같은 계정으로 조회했고 다른 계정에서는 보이지 않았다. 오프라인 대기 요청과 인증 만료 중 재생은 JVM 테스트에서 검증했다. 대상 차량의 OS 게스트 프로필에서 종료 후 로컬 기록을 보존하는지는 기기별 확인이 필요하다.

## Pleos Connect App

실제 차량 Frontend는 `frontend/pleos-app`이다. React 앱은 `frontend/web-harness`에 격리된 API와 E2E Flow 검증용 Web Harness다.

Android 빌드 도구는 **AGP 8.13.2 · Gradle 8.13 · JDK 17** 조합으로 고정한다. Android Studio에서는 `frontend/pleos-app`을 열고 Gradle Distribution은 프로젝트 Wrapper를 사용한다. Gradle 9.6 이상에서는 AGP 8.x가 사용하던 내부 API가 제거되어 동기화가 실패한다. AGP와 Wrapper를 각각 최신 버전으로 올리지 않고 [공식 호환 표](https://developer.android.com/build/releases/agp-8-13-0-release-notes#compatibility)를 함께 확인한다.

Backend와 Android는 각각의 Wrapper를 사용한다. 아래 명령과 CI/Makefile도 Android 전용 Wrapper를 사용해 Studio와 같은 버전으로 검증한다.

```bash
# PLEOS SDK 없이 UI·주행 제한 정책 검증
ANDROID_HOME=/path/to/android-sdk frontend/pleos-app/gradlew -p frontend/pleos-app \
  testFakeDebugUnitTest assembleFakeDebug

# 공식 Vehicle 2.0.3, NaviHelper 3.0.5 SDK를 포함한 APK 검증
ANDROID_HOME=/path/to/android-sdk frontend/pleos-app/gradlew -p frontend/pleos-app \
  assemblePleosDebug
```

생성 APK는 `frontend/pleos-app/app/build/outputs/apk/` 아래에 생성된다. `fake` Flavor는 일반 AAOS Emulator용이며, `pleos` Flavor는 PLEOS SDK Repository에 접근 가능한 환경에서 사용한다.

Pleos 에뮬레이터에서 심사용 release APK를 실행하려면 호스트에서 JDK 17로 `cd backend && ./gradlew bootRun --args='--spring.profiles.active=local --server.address=127.0.0.1'`을 실행한다. 비회원 공개 이야기에는 DB·Redis·계정이 필요 없다. `frontend/pleos-app`에서 `./gradlew :app:assemblePleosRelease`로 빌드하고 `adb install app/build/outputs/apk/pleos/release/app-pleos-release.apk`로 설치한다. Release에서는 에뮬레이터 호스트의 `10.0.2.2`에만 HTTP를 허용한다. 다른 서버에 연결하는 APK는 `-PROAD_STORY_API_BASE_URL=https://…`를 지정해 빌드한다. 저장소의 release 빌드는 에뮬레이터 설치용 서명 설정을 사용한다.

차량 앱은 `CarUxRestrictionsManager`의 규제 상태와 변경 알림을 사용한다. AAOS가 주의 분산 최적화를 요구하거나 활성 규제 플래그가 하나라도 있으면 모든 화면을 `SCR-DRIVE-LIMIT-001`로 표시한다. 규제 상태 확인 전, 차량 서비스 연결 단절, 조회 또는 관측 실패에도 같은 화면을 표시한다. 정차 시에만 허용되는 화면 이동과 조작은 실행 직전에 현재 규제를 확인한다.

`NaviHelper`는 안내 상태, 목적지와 남은 시간을 재생 후보에 연결한다. 위치 조회에는 기기가 지원하는 경우 `FusedLocation`을 사용하고, 그 외에는 Android GPS를 사용한다. 사용 조건과 검증 결과는 [Android 위치·내비·코스 재생 후보 검증](testing/android-journey-candidates-validation.md)에 기록했다.

## 검증 명령

```bash
docker compose config
cd frontend/web-harness && npm ci && npm run lint && npm run test && npm run build
cd backend && ./gradlew test && ./gradlew build
ANDROID_HOME=/path/to/android-sdk frontend/pleos-app/gradlew -p frontend/pleos-app testFakeDebugUnitTest assembleFakeDebug assemblePleosDebug
cd e2e && npm ci && npx playwright install chromium && npx playwright test
```

Root에서는 `make install`, `make test`, `make e2e`를 사용할 수 있다.

## OpenAPI Client

`contracts/openapi.yaml`을 변경한 뒤 `cd frontend/web-harness && npm run generate:api`를 실행한다. 결과는 `frontend/web-harness/src/generated/api.ts`에 생성되며 직접 수정하지 않는다. Backend API는 Contract에 있는 Path만 구현하며 Contract 변경은 구현보다 먼저 한다.

## Fake DrivingState

Frontend 실행 전에 `VITE_FAKE_DRIVING_STATE=DRIVING`, `STOPPED`, `UNKNOWN` 중 하나를 지정한다. 기본값은 `STOPPED`다. `DRIVING` 또는 `UNKNOWN`에서는 Transcript, Image Gallery, Payment Management, Text Input, 상세 Filter가 `SCR-DRIVE-LIMIT-001`로 대체된다.

웹 검증 도구는 `VehiclePlatform` Interface와 `FakeVehiclePlatform`으로 차량 상태를 모의한다. 차량 앱의 `pleos` Flavor는 실제 SDK를 사용한다.

## 현재 구현 범위와 제한

- `GET /v1/stories/nearby`, Story 상세, Course, Preference, 저장, 신고 API의 Fake 기반 실행
- `PUBLISHED` Story만 Consumer API에 노출하고 Problem Details에 안정적인 `code` 제공
- Pleos Native `SCR-HOME-001`, `SCR-FREE-001`, `SCR-PLAYER-001`, `SCR-DEST-001`, Course 화면과 주행 제한 UI
- Web Harness의 동일 Screen·Flow Prototype
- 목적지 Playlist는 Port Skeleton만 있으며 Route Algorithm은 구현하지 않음
- Backend Story 상세의 검수 완료 `fullText`를 PLEOS TextToSpeech SDK 2.1.5.1의 `ON_DEVICE` 모드로 실시간 합성하며 MediaSession/Audio Focus와 연동
- PLEOS TTS는 문장 완료 지점을 기준으로 이어듣는다. 계정별·비회원별 문장 위치를 차량에 저장하고, 앱을 다시 연 뒤 `재생`을 누르면 복원한다. [재생 시간·이어듣기 검증](testing/android-playback-time-resume-validation.md)
- 발행된 Story에 파일 음원을 공급하려면 서버의 `ROAD_STORY_AUDIO_DIR`을 설정한다. 차량에서 파일 음원의 위치 복원과 오프라인 재생을 확인할 수 있다. [음원 등록과 기기 검증](testing/android-file-audio-validation.md)
- 보관함은 `local-sync`에서 PostgreSQL 영속 저장과 두 개발 계정을 검증한다. 실제 Pleos Account 연동은 공식 외부 앱 인증 명세가 필요하다.

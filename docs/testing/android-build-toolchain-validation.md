# Android 빌드 도구 호환성 복구

- 일자: 2026-11-10
- 목적: Android Studio의 Gradle Wrapper 업그레이드 실패와 프로젝트 동기화 오류를 해결한다.
- 연결: `REQ-NFR-003`, `REQ-NFR-004`, `REQ-NFR-010`; 기존 `FLW-FREE-001`, `FLW-PLAY-001`, `FLW-SAVE-001` 검증 기반. 제품 화면·API 변경 없음.

## 원인과 설계 판단

Studio에 표시된 오류를 현재 Wrapper의 `./gradlew help`로 재현했다. 현재 AGP는 8.13.2이고 Wrapper가 Gradle 9.8.2로 변경되어 있었다. 플러그인 적용 단계에서 `InternalProblems` 내부 API를 찾지 못해 실패했다. Gradle 9.6 이후 제거된 API를 AGP 8.x가 사용하면서 발생한 오류이며 Kotlin 앱 소스 컴파일 전 단계다.

기존 AGP 8.13.2 변경은 유지하고 [공식 호환 기준](https://developer.android.com/build/releases/agp-8-13-0-release-notes#compatibility)에 맞춰 Gradle 8.13과 JDK 17 daemon을 사용한다. Kotlin 플러그인·PLEOS SDK·compileSdk/targetSdk·OpenAPI는 변경하지 않았다.

## 변경 파일

- `frontend/pleos-app/gradle/wrapper/gradle-wrapper.properties`: Gradle 8.13 배포본 고정.
- `frontend/pleos-app/gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`: Gradle 8.13으로 Wrapper 재생성.
- `frontend/pleos-app/gradle.properties`: Gradle 9.4 이상용 병렬 sync 설정 제거.
- `Makefile`, `.github/workflows/ci.yml`: Android는 Backend Wrapper 대신 Android 전용 Wrapper 사용. CI Build Tools 35.0.0 명시.
- `docs/development.md`: 동일 빌드 버전·Wrapper·SDK 도구와 실행 명령 문서화.

## 실행 결과

- 실패 재현: Gradle 9.8.2 `help`에서 `com.android.internal.application` 적용 실패 / `InternalProblems` 오류.
- Wrapper 8.13 재생성 성공.
- Android Studio의 `Sync Project with Gradle Files`를 직접 실행해 `BUILD SUCCESSFUL in 47s`와 프로젝트 모듈/Run 버튼 복구를 확인했다. 이후 표시된 AGP 업그레이드 권장은 실패가 아닌 별도 안내다.
- `clean testFakeDebugUnitTest assembleFakeDebug assemblePleosDebug assemblePleosDebugAndroidTest`: 성공. 단위 테스트 8개, 두 Debug APK와 PLEOS 기기 테스트 APK 생성. 112개 Task 중 96개 실행, 16개 캐시 사용.
- Web: npm ci, lint, test 7개, build 성공.
- Backend: JDK 17에서 test/build 성공(기존 Task 결과 재사용).
- E2E: npm ci 후 Playwright 1개 성공. 최초 실행은 동시에 수행한 Web npm ci와 겹쳐 Vite를 찾지 못했고 Web 설치 완료 후 재실행해 통과했다.
- `git diff --check`: 성공.
- `make pleos-test`: Android 전용 Wrapper 호출과 단위 테스트 Task 성공(이전 통과 결과 재사용).

## 제한과 후속 작업

- 이 작업은 빌드·동기화 복구다. 에뮬레이터의 Surface 표시 문제나 미연결 제품 기능이 해결됐다는 의미는 아니다. 기기 실행 테스트는 이 작업에서 재실행하지 않았다.
- CLI의 기본 Launcher JVM은 로컬 JDK 26이며 빌드는 daemon 기준 JDK 17을 사용한다. CLI에서는 `JAVA_HOME`을 JDK 17로 맞추면 Launcher의 native-access 경고도 피할 수 있다.
- 기존 `scaledDensity` deprecated 경고는 빌드를 막지 않는다.
- 다음 빌드 도구 업그레이드 시 AGP·Gradle·JDK의 조합과 Studio 동기화를 함께 확인한다.

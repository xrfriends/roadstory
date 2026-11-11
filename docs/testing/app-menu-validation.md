# 차량 앱 메뉴 수정 검증

- 일자: 2026-11-09
- 목적: 상단 `로드스토리 ⌄` 클릭이 홈 이동으로만 연결되어 메뉴가 열리지 않는 문제 수정.
- 연결: `SCR-APP-MENU-001`, `SCR-HOME-001`, `SCR-PLAYER-001`, `SCR-SAVED-001`, `SCR-SETTINGS-001`; `FLW-NAV-001`, `FLW-SAVE-001`; `REQ-USER-001~003`, `REQ-STORY-005`, `AC-PLAY-002`.

## 변경 파일과 설계

- `MainActivity.kt`: 홈 이동 대신 Anchor 기반 PopupWindow 표시, 선택 후 이동, 현재 화면 표시, 팝업 취소/수명주기 정리. 규제 조회 실패는 제한 상태로 처리.
- `ui/ViewFactory.kt`: 제목·아이콘을 메뉴에 연결, 설정 버튼 연결, 주행 중 비활성화 및 접근성 펼침/접힘 상태.
- `ui/VehicleAppMenu.kt`: 홈/지금 듣기/내 이야기/설정 항목과 주행 제한 정책.
- `ui/RoadStoryScreens.kt`, `ui/VehicleUiPolicy.kt`: 내 이야기·설정 진입 화면과 규제 차단. 설정은 현재 패키지의 시스템 앱 설정을 열도록 연결.
- `app/build.gradle.kts`: AndroidX 기기 테스트 Runner와 테스트 전용 의존성.
- `VehicleAppMenuTest.kt`, `VehicleAppMenuDeviceTest.kt`: 항목별 이동·메뉴 열기/닫기·정책 회귀 검증.
- `docs/ux/screen-specs.md`, `docs/ux/user-flows.md`: 메뉴 동작과 제한 명세.
- 외부 API·OpenAPI·PLEOS SDK 버전은 변경하지 않음. 기존 작업 트리의 디자인 변경은 유지.

## 검증 결과

- Native 단위 테스트: `./gradlew testFakeDebugUnitTest` 성공, 총 8개(신규 3개).
- APK: `assemblePleosDebug`, `assembleFakeDebug`, `installFakeDebug` 성공. 사용 중인 `Pleos_Connect_v2.0` Emulator의 fake 패키지에 수정 반영.
- Web Harness: `npm ci`, lint, 단위 테스트 7개, build 성공.
- Backend: JDK 17로 test/build 성공. Task는 UP-TO-DATE 결과 재사용.
- E2E: `npm ci`, `npx playwright test` 성공, 기존 흐름 1개.
- 기기 테스트: `./gradlew connectedFakeDebugAndroidTest` 최종 2개 성공. 실제 Activity의 제목 View 클릭 이벤트 → Popup 표시 → 네 항목별 Screen ID 변경, 현재 화면 선택 표시, Back/바깥 Touch 이벤트 취소 후 홈 유지를 확인. Back/바깥 터치 검증은 Popup Decor의 입력 연결 이후 수행.
- `git diff --check` 성공.

## 제한과 후속 작업

- ‘내 이야기’ 진입은 연결했지만 저장 목록 API는 미연결이다. 성공한 빈 목록/가짜 데이터 대신 미연결 안내를 표시한다. 저장 목록·이력·Preference 동기화는 별도 구현 필요.
- 설정은 권한·알림 시스템 화면 진입을 제공한다. Auto-play/가족 모드/관심 주제 동기화 토글은 추가하지 않았다.
- CUA는 Android Studio 창을 읽지 못했다. 초기 전역 접근성/좌표 기반 테스트에서는 PLEOS 시스템 `ai.umos.drivingview`의 반복 SIGSEGV와 앱 창 공백을 관측했다. 최종 기기 테스트는 임베디드 앱 창의 전역 좌표에 의존하지 않고 ActivityScenario와 실제 Android View 클릭/키/Touch 이벤트를 사용한다. 실제 터치·Rotary와 최종 시각 배치는 안정된 Emulator/차량에서 별도 확인해야 한다.

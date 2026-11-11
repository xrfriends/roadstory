# Android 청취 시간과 문장 이어듣기 검증

후속 작업 7번. PLEOS TTS 재생에서 누적 청취 시간을 표시하고, 사용자 일시정지와 Audio Focus 중단 뒤 문장 단위로 이어듣는다.

- `REQ-PLAY-004`, `REQ-PLAY-006`, `REQ-PLAY-007`에 연결한다.
- 화면은 `SCR-PLAYER-001`, `SCR-DRIVE-LIMIT-001`과 고정 Mini Player를 사용한다.
- 흐름은 `FLW-PLAY-001`, `FLW-INTERRUPT-001`에 연결한다.
- 인수 조건은 `AC-PLAY-004`, 시나리오는 `TS-PLAY-020~023`이다.

## SDK 근거와 설계 판단

설치한 `ai.pleos.playground:TextToSpeech:2.1.5.1`의 공개 클래스 메서드를 확인했다. `TextToSpeech`는 초기화·해제, 리스너 등록·해제, `speak(String, boolean)`, `stop()`, 언어·음성·오디오 속성 설정을 제공한다. `EventListener`는 시작·준비·완료·정지·오류·RMS 콜백을 제공한다. 현재 공식 가이드는 3.3.1 기준이며 `ON_DEVICE`는 차량 엔진에서 네트워크 연결 없이 합성·재생한다. 이 단계는 기존 2.1.5.1과 `ON_DEVICE`를 사용한다. [SDK 개요](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro)

공식 `speak()`는 입력을 완결 요청으로 보내거나 여러 조각을 마지막 `isFinal=true` 요청까지 연결한다. 이번 이어듣기는 각 문장을 완결 요청으로 처리하고 성공한 `onDone()`으로 체크포인트를 진행시킨다. 정지할 때는 현재 요청을 중단하고, 다음 재생은 마지막 완료 문장 다음부터 요청한다. 중단된 문장의 앞부분을 다시 들을 수 있다. [합성 시작과 정지](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/TextToSpeech/start-and-stop) · [이벤트 리스너](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/event-listeners)

`들은 시간`은 실제 `PLAYING` 구간의 단조 시계 간격을 누적한다. 준비·일시정지·Focus 대기는 제외하고 같은 문장의 재청취 시간은 포함한다. 낭독이 남아 있고 서버 예상 길이보다 누적 청취 시간이 짧을 때 `예상 남은 시간`을 표시한다. 정확한 음원 위치 기반 탐색·진행률을 제공하려면 파일 음원과 플레이어의 위치 계약을 추가로 확보해야 한다.

사용자 `종료`, 다른 Story 선택과 세션 교체는 현재 사용자의 이전 체크포인트를 해제한다. 화면 꺼짐·Suspend 진입은 체크포인트를 유지하고 재생 자원을 해제하며 사용자의 재생 입력으로 이어듣는다. 일시적 Focus 복귀는 활성 재개 의도와 현재 요청에만 적용한다. 인증 만료는 6번에서 정한 현재 공개 이야기 완료·대기열 해제 정책을 적용한다.

재생 엔진은 수명 동안 인스턴스를 재사용하며, 해제 전에 등록 리스너를 제거한다. 자원을 해제한 뒤 다음 요청에서 새 인스턴스를 생성하고 초기화한다. [초기화와 해제](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/initialization-and-release)

낭독은 Android 오디오 포커스에서 음성 콘텐츠로 분류한다. 다른 앱이 볼륨 감소가 허용된 일시적 포커스를 요청해도 `setWillPauseWhenDucked(true)`로 중단 콜백을 받아 문장 이어듣기 정책을 적용한다. [Android Audio Focus](https://developer.android.com/media/optimize/audio-focus#automatic-ducking) · [AAOS Audio Focus](https://source.android.com/docs/automotive/audio/audio-focus)

## 변경 범위

| 파일 | 검증할 내용 |
|---|---|
| 재생 Coordinator | 문장 체크포인트, 취소한 이전 요청의 콜백 차단, 누적 청취 시간 |
| Playback Service와 MediaSession 계약 | 화면 꺼짐·Suspend 처리, Focus 재개 의도, 현재 Story의 시간·이어듣기 상태 전달 |
| 차량 재생 화면과 Mini Player | 들은 시간·예상 남은 시간, 문장 이어듣기 안내, 주행 제한 조작 유지 |
| JVM·PLEOS 기기 테스트 | 중단·재개, 사용자 종료·Story 변경, 시간 계산, 실제 음성 시작·완료·오류 |

## 검증 결과

- Android JVM 67개와 Fake·Pleos Debug 및 기기 테스트 APK 빌드가 통과했다. 완료한 문장의 재생 생략, 중단 문장 재생, 중단 중 시간 정지, 청취 이력 한 번 기록, 지연 콜백 무시를 검사했다.
- Pleos Connect 2.0 에뮬레이터의 재생 검사 7개가 통과했다. 공개 이야기와 코스의 한국어 낭독·일시정지, 연속 한국어 합성 완료, 내비 안내·통화 속성으로 발급한 Audio Focus 중단·복귀, 종료 뒤 자동 재개 차단, 화면 꺼짐·깨우기 뒤 명시적 재개를 확인했다.
- 화면 검사 6개가 통과했다. 멈춘 시간과 이어듣기 안내, 예상 시간이 지났거나 낭독이 끝났을 때의 표시, 시간 갱신 중 포커스 유지, 320dp·1024dp 주행 화면의 터치 영역을 확인했다. 포커스 검사는 창 준비 뒤에 포커스를 요청하도록 수정하고 에뮬레이터에서 단독 실행해 통과했다.
- 백엔드 테스트 40개와 빌드, 웹 설치·린트·테스트 7개·빌드, Playwright 자유 주행 및 PostgreSQL 보관함 동기화 2개가 통과했다. 외부 API 계약 변경은 없다.

## 추가 확인 범위

실제 차량의 내비게이션·통화 Focus와 전원 Suspend는 대상 차량에서 통합 확인한다. 앱은 Android 화면 꺼짐 신호에 반응한다. AAOS의 차량 전원 상태 알림은 시스템 앱과 서비스에서 제공되는 경로다. [AAOS 전원 관리](https://source.android.com/docs/automotive/power/power)

파일 음원의 위치 복원과 오프라인 재생은 [파일 음원 검증](android-file-audio-validation.md)에 기록한다.

## 앱 재시작 후 문장 위치 복원

`REQ-PLAY-004`, `REQ-PLAY-007`, `SCR-PLAYER-001`, `SCR-DRIVE-LIMIT-001`, `FLW-PLAY-001`, `FLW-INTERRUPT-001`, `AC-PLAY-004`, `TS-PLAY-024~025`에 연결한다.

`PlaybackCheckpointStore`는 앱 전용 저장소에 비회원 또는 검증된 계정마다 현재 Story 한 건의 문장 위치를 보관한다. `PlaybackCoordinator`는 재생 시작·문장 완료·일시정지 때 위치를 기록한다. 앱을 다시 열면 저장한 Story를 일시정지 상태로 표시하고, `재생`을 누른 뒤 공개 API에서 낭독문을 다시 받아 문장 구성이 맞는지 확인한다. 낭독문 원문과 인증 토큰은 위치 기록에 포함하지 않는다.

로그아웃이나 계정 전환 시 각 사용자의 위치를 유지한다. 계정의 세션 인증이 만료되면 이전 계정의 위치를 보관하고, 마저 낭독하는 동안 비회원의 위치 기록을 변경하지 않는다. 다시 연결한 계정의 위치는 해당 계정이 확인된 뒤 불러온다. 재생이 끝나거나 사용자가 종료·다른 Story 선택을 실행하면 현재 사용자의 이전 위치를 해제한다. 이전 재생 큐와 내비게이션 안내는 각 기능에서 새로 시작한다.

Pleos Connect 2.0 에뮬레이터에서 `PlaybackRestartDeviceTest#seedBeforeRestart`로 비회원은 첫 문장, 개발 계정 A는 두 번째 문장에 일시정지했다. `adb shell am force-stop --user 10 com.roadstory.pleos` 후 `PlaybackRestartDeviceTest#verifyAfterRestart`를 다른 프로세스에서 실행했다. 비회원과 계정 A의 각 저장 위치, 계정 B의 빈 상태, 재생 요청 전 음성·조회 대기 상태를 확인했다. 계정 A는 실제 화면의 재생 버튼을 눌러 두 번째 문장부터 재생을 시작했다. 비회원은 MediaSession 재생 명령으로 첫 문장을 시작했다.

재시작 검사 2개와 저장소 격리 검사 2개, Android JVM 71개, Fake·Pleos Debug 빌드, 백엔드 40개, 웹 7개, Playwright 2개가 통과했다. 운영 인증이 도입되기 전의 개발 빌드에서는 앱 재시작 뒤 계정을 다시 연결한다. 강제 종료 직전 청취 시간은 마지막으로 기록한 값에서 이어진다.

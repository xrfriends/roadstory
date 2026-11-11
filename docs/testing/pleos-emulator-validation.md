# PLEOS Emulator 검증

이 문서는 `REQ-PLAY-001`, `REQ-SAFE-001`, `SCR-HOME-001`, `SCR-FREE-001`, `SCR-PLAYER-001`, `FLW-FREE-001`, `FLW-PLAY-001`, `TS-DRIVE-001`, `TS-PLAY-007`의 PLEOS Connect Emulator 검증 결과를 기록한다.

## 환경

- AVD: `Pleos_Connect_v2.1.5_S`
- PLEOS Connect: 2.1.5 / Android 14
- APK: `pleosDebug`
- 화면: 2560×1440 landscape, 앱 영역 840×1184 portrait pane
- Backend: local profile, Emulator host `10.0.2.2:8080`

## 결과

| 항목 | 결과 | 근거 |
|---|---|---|
| 설치·실행 | PASS | `com.roadstory.pleos/.MainActivity`, cold launch 약 720ms |
| 화면 모델 | PASS | Home → Free → Player에서 Screen ID와 64px 이상 Action 확인 |
| Native Visual System | PASS | 840×1184 PLEOS 앱 Pane에서 Home·Player·목적지·코스 목록·코스 상세의 Context→제목→Primary Action 계층, Mint Accent, 고대비 Card와 잘림 없는 배치를 확인 |
| Rotary | PASS | `car_service inject-rotary`와 동등한 DPAD focus/click 이동 확인 |
| 주행 제한 ON | PASS | speed 30, parking brake false, gear D에서 `Driving State: 2`, `DO: true`, `UxR: 255` |
| 주행 제한 OFF | PASS | speed 0, parking brake true, gear P 복귀 |
| MediaSession | PASS | `RoadStory` active session과 `PLAYING`, Story metadata 확인 |
| Backend Text + TTS | BUILD PASS / DEVICE PENDING | Story 상세의 `fullText`를 받아 PLEOS TextToSpeech SDK 2.1.5.1 `ON_DEVICE` 모드로 합성하도록 구현. 실제 음성 출력은 Emulator 재검증 필요 |
| Suspend | PARTIAL | screen-off/영구 focus loss 시 재생·focus·player·foreground 해제 구현. 이 Emulator의 simulated suspend는 앱에 해당 event를 전달하지 않아 통합 검증 불가 |

## 재현 명령

```bash
adb shell cmd car_service emulate-driving-state drive
adb shell cmd car_service emulate-driving-state park
adb shell cmd car_service inject-rotary -d 0 -i 10 -c true
adb shell dumpsys media_session
adb shell dumpsys audio
adb shell cmd car_service suspend --simulate --skip-garagemode --wakeup-after 3
```

실제 Audio Focus 경합은 Navigation guidance가 transient focus를 획득하는 조건으로 최종 차량 통합 시험에서 반복한다. 앱 정책은 transient loss에서 TTS를 중지하고 gain에서 현재 Story의 낭독을 처음부터 재개하며, permanent loss와 screen-off에서는 TTS 리소스를 해제한다. PLEOS TTS SDK에는 pause/seek API가 없으므로 문장 단위 이어듣기는 후속 범위다. PLEOS 2.1.5 Emulator의 `suspend --simulate`는 screen-off나 focus loss를 앱에 전달하지 않았으므로, 실제 차량의 Suspend event 연결 지점은 OEM 통합 시험이 필요하다.

## 증적

로컬 검증 산출물은 `tmp/pleos-validation/`에 있으며 제품 데이터, 원본 위치, Token, CRN 또는 개인정보를 포함하지 않는다.

# 파일 음원 위치 복원과 오프라인 재생

`REQ-PLAY-004`, `REQ-PLAY-007`, `REQ-OFF-002`, `REQ-OFF-004`, `SCR-PLAYER-001`, `SCR-OFFLINE-001`, `FLW-PLAY-001`, `FLW-INTERRUPT-001`, `AC-OFF-001`, `TS-PLAY-026`, `TS-OFF-001`에 연결한다.

서버의 `ROAD_STORY_AUDIO_DIR`에 검수된 `<storyId>.m4a`와 `<storyId>.json`을 함께 둔다. JSON의 `durationMs`는 음원 길이, `narrationSha256`은 공개 API의 정확한 `fullText`를 UTF-8로 인코딩해 계산한 SHA-256이다. 발행된 Story에 최신 낭독문과 일치하는 음원이 있으면 `StoryDetail.audio`에 다운로드 주소, MIME, 크기, SHA-256, 길이를 제공한다. 음원을 교체할 때 파일과 메타데이터를 함께 반영한다. 크기는 파일당 20 MiB, 길이는 최대 1시간이다.

차량은 파일 음원을 처음 재생하거나 정차 중 `오프라인 저장`을 선택할 때 같은 서버에서 파일을 받는다. 내려받은 바이트 수와 SHA-256을 확인하고 차량의 앱 전용 저장소에 둔다. 용량은 50 MiB로 제한하며 공간이 필요하면 오래 사용하지 않은 음원부터 지운다. 공개 Story가 사라졌거나 파일 버전이 교체됐다는 응답을 받으면 이전 파일을 지운다. 서버 연결에 실패하면 최근 30일 이내에 검증한 저장 음원을 재생하고 오프라인 상태를 표시한다.

`AndroidStoryAudioPlayer`는 플랫폼 플레이어가 반환한 밀리초 위치를 사용한다. 앱은 재생 중 5초마다, 일시정지·시스템 중단·정상 서비스 종료 때 현재 위치를 기록한다. 재시작 뒤에는 사용자가 재생을 누르면 같은 SHA-256의 파일에서 저장 위치에 가장 가까운 프레임으로 이동한 뒤 재생한다. 재생 위치는 기존과 같이 비회원·계정별로 저장한다. 음원이 연결되지 않은 공개 Story는 PLEOS TTS와 문장 이어듣기를 사용한다. [Android MediaPlayer seekTo](https://developer.android.com/reference/android/media/MediaPlayer#seekTo(long,%20int))

기기 통합 검사는 별도 `18081` 서버에서 공개 Story의 한국어 시험 음원으로 실행한다. `OfflineAudioPlaybackDeviceTest#seedOnlinePosition`에서 다운로드 및 일시정지 위치를 확인한다. 앱을 강제 종료하고 시험 서버를 내린 뒤 `#resumeOfflineAfterRestart`로 재생 버튼, 오프라인 상태, 실제 재생 위치를 검사한다. 두 단계는 `verifyOfflineAudio=true`로 실행한다.

Pleos 에뮬레이터에서 14,171ms 시험 음원을 재생하고 2,016ms에 일시정지했다. 앱을 강제 종료하고 시험 서버를 내려 연결 실패를 확인했다. 새 프로세스의 일시정지 상태에서 화면의 재생 버튼을 눌렀을 때 오프라인 음원으로 전환했고, 저장 위치에서 300ms 이내로 시작한 뒤 재생 위치가 증가했다. 정차 중 `오프라인 저장`은 재생을 시작하지 않고 파일을 저장했다. 초기 통합 시도에서 기존 AAOS 차량 서비스 조회가 멈췄으며 에뮬레이터를 새로 부팅한 뒤 같은 검사를 완료했다.

Pleos 기기 검사 8개, Android JVM 79개와 Fake·Pleos APK 빌드, 백엔드 43개와 빌드, 웹 7개와 린트·빌드, Playwright 2개를 통과했다. 운영에 사용할 검수 음원은 배포 환경에 공급한다. 개발 계정의 앱 재시작 후 위치 복원에는 해당 계정의 재연결이 필요하며, 서버를 내린 상태의 프로세스 재시작은 비회원으로 검증했다.

# Android 위치·내비·코스 재생 후보 검증

후속 작업 5번. 주변 이야기에는 현재 위치를, 목적지 편성에는 내비게이션 안내 정보를, 코스 재생에는 선택한 공개 코스의 Story를 사용한다.

- 요구사항 `REQ-PLAY-001~002`, `REQ-ROUTE-001~007`, `REQ-COURSE-001~003`, `REQ-COURSE-007`, `REQ-STORY-005`
- 화면 `SCR-HOME-001`, `SCR-FREE-001`, `SCR-PLAYER-001`, `SCR-DEST-001`, `SCR-COURSE-LIST-001`, `SCR-COURSE-DETAIL-001`, `SCR-DRIVE-LIMIT-001`
- 흐름 `FLW-FREE-001`, `FLW-PLAY-001`, `FLW-DEST-001`, `FLW-COURSE-001`
- 인수 조건 `AC-PLAY-002`, `AC-PLAY-007`, `AC-ROUTE-001~003`, `AC-COURSE-001~002`
- 테스트 시나리오 `TS-PLAY-018~019`, `TS-ROUTE-005~006`, `TS-COURSE-006~007`

| 변경 파일 | 적용 내용 |
|---|---|
| `VehicleLocationSource.kt`, `VehicleLocationSourceFactory.kt`, Android 권한·의존성 설정 | 요청 시 위치 취득, 제공자 선택, 권한·신선도·정확도 검증, 일회 조회 종료 처리 |
| `NavigationGateway.kt`, `NavigationGatewayFactory.kt`, `NavigationSession.kt` | 안내 상태, 목적지, 남은 시간, 경로 변경과 종료 수신. 확인된 첫 코스 정차점의 안내 요청 |
| `MainActivity.kt`, `RoadStoryScreens.kt`, `JourneyScreensState.kt`, `JourneyCatalog.kt`, `ViewFactory.kt` | 실제 내비 상태, 편성 모드, 공개 코스 목록과 상세, 개별 및 전체 재생, 이야기 저장, 첫 장소 안내 확인 |
| `StoryApiClient.kt`, `PlaybackCoordinator.kt`, `RoadStoryPlaybackService.kt`, `PlaybackSessionContract.kt`, `NearbyStoryPreferences.kt` | 위치·목적지·코스별 후보 구성. 재탐색 시 현재 Story를 유지하고 이후 후보 갱신 |
| `contracts/openapi.yaml`, `frontend/web-harness/src/generated/api.ts`, Backend Story·Journey·Course 구현 | 실제 거리 계산, 공개 필터, 목적지 편성 기준, 코스 정차점과 Story 응답, Problem Details |

## 설계

- 주변 조회는 사용자의 명시적 요청에서만 위치를 취득한다. PLEOS에서 지원이 확인되면 FusedLocation 1.1.3을 사용하고, 미지원 기기에서는 Android GPS로 현재 위치를 확인한다. 위치는 30초 이내여야 한다. FusedLocation 신뢰도 하한은 0.5, Android GPS 정확도 상한은 100m다. FusedLocation 샘플에는 속도를 부여하지 않는다. 위치를 사용할 수 없으면 안내하고, 사용자가 직접 선택한 공개 Story는 재생할 수 있다. [PLEOS FusedLocation](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro), [Android LocationManager](https://developer.android.com/reference/android/location/LocationManager)
- 주변 API는 기본 1,500m 반경의 공개 Story를 반환한다. 속도와 방향이 제공되어 속도가 3m/s 이상이면 50m보다 멀리 있는 후방 Story를 뒤로 보낸다. 응답의 거리는 사용자가 조회한 위치에서 공개 지점까지 계산한다. 원본 위치·경로는 오류 문구에 포함하지 않는다.
- NaviHelper 2.0.3에서는 실제 안내 상태, 목적지, 남은 시간, 경로 변경 신호를 사용한다. 이 버전이 경로 좌표를 제공하지 않아 차량은 목적지·남은 시간·모드만 보낸다. 서버는 목적지 300m 이내의 공개 Story를 편성하고 `DESTINATION_NEARBY`를 반환한다. 경로 좌표가 있는 클라이언트는 출발점과 유효한 Polyline5를 보내 100m 이내의 후보를 경로 진행 순서로 받을 수 있다. 화면은 편성 기준을 표시한다. [PLEOS NaviHelper](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro), [Polyline5 형식](https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
- 편성에는 도착 전 30초를 남긴다. `SHORT`는 나머지 시간의 절반과 180초 중 작은 값을 사용하고, `FAMILY`는 가족 적합이 명시된 Story만 포함한다. 재탐색·목적지·남은 시간 변경은 이후 큐에 반영한다. 현재 재생 중인 Story의 예상 길이는 보수적으로 전체를 남겨 둔다. 안내가 종료되거나 남은 시간이 부족하면 이후 후보를 비운다.
- 공개 성수 코스는 성수역 한 곳과 기존 공개 이야기 한 편을 사용한다. 성수역의 대표 지점은 서울교통공사 역사 좌표 CSV의 2호선 211번을 사용한다. 서울관광 자료는 수제화 거리와 성수역의 인접 관계를 확인하는 데 사용했다. 사용자가 정차 중 별도로 확인할 때만 첫 장소의 내비 안내를 요청한다. [서울교통공사 역사 좌표 OA-22534](https://data.seoul.go.kr/dataList/OA-22534/F/1/datasetView.do), [서울관광 성수동 수제화 거리](https://stage-kr.visitseoul.net/shopping/%EC%84%B1%EC%88%98%EB%8F%99-%EC%88%98%EC%A0%9C%ED%99%94-%EA%B1%B0%EB%A6%AC/KOP027649)

## 검증 결과

- Backend: JDK 17 테스트 30개와 빌드 성공. 실제 거리·반경·방향, 목적지 주변 편성과 시간 예산, Polyline5 경로 순서·오류, 공개 코스 목록·상세를 검증했다. OpenAPI의 Web 생성 타입을 갱신했다.
- Android: JVM 테스트 49개, `fakeDebug`, `pleosDebug` 및 기기 검사 APK 빌드 성공.
- PLEOS 에뮬레이터: 권한 미허용 처리, PLEOS 위치 서비스의 공개 시험 좌표 수신, 위치에 맞춘 주변 재생 시나리오 4개, 위치 권한 없이 코스 Story와 개별 Story의 실제 낭독·일시정지가 통과했다. 시험 위치 권한은 종료 후 원래 상태로 복구했다.
- NaviHelper 실제 상태 콜백과 게이트웨이의 일치 검사는 첫 실행에서 통과했다. 이후 재부팅한 에뮬레이터에서는 SDK 자체의 상태 콜백이 오지 않았다. 첫 장소 안내의 긍정 검사는 내비 준비 및 기존 경로 없음 전제를 충족하지 않아 건너뛰었다. 실제 안내 시작으로 기록하지 않았다.
- 연결 Gradle 검사가 APK 설치 전에 대기해 동일 앱·검사 APK를 Android SDK로 설치하고 기기 테스트를 나눠 실행했다. 초기 UI 검사는 에뮬레이터의 `HardwareRenderer.nSyncAndDrawFrame`에서 멈춰 Android Studio에서 기기를 재시작했다. 중복 화면 갱신을 묶고 서비스 연결 전에 들어온 코스 재생 요청을 보존하도록 보완했다.
- 보완한 APK에서 실제 공개 코스 목록 → 상세 → 선택 Story 낭독과 목적지 화면(목적지 상태 모델의 ETA·모드·행 선택·목적지 없음)을 확인하는 UI 검사 3개와 주행 제한 회귀 4개가 통과했다. `git diff --check`도 통과했다.
- Web: npm ci, lint, 테스트 7개, 프로덕션 빌드 성공. E2E: npm ci 및 Playwright 시나리오 1개 성공.

## 범위

- 코스 정차점은 공개 역 좌표로, 운전자 승하차 지점이나 주차장 데이터는 별도로 필요하다.
- 다중 정차점의 도착 후 진행·재개와 오프라인 재생은 후속 범위다. PLEOS 실차 주행은 별도 검증이 필요하다.
- 내비 안내가 가능한 장치에서 첫 장소 안내 시작 및 실제 재탐색을 확인한다. 다음 개발 순서는 저장·청취 기록의 서버 동기화다.

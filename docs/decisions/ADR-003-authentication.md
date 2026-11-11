# ADR-003: OIDC와 Anonymous Journey 지원

- Status: Accepted
- Date: 2026-08-04

## Decision

- 기존 사용자 인증 설계는 OAuth 2.0 / OIDC Authorization Code Flow with PKCE를 기준으로 한다.
- 로그인 전 제한된 Anonymous 이용 지원
- CMS는 Role-based Access 사용
- Road Story의 운영 로그인은 Pleos Account를 사용한다. 실제 인증 흐름과 PKCE 적용 여부는 Pleos가 제공하는 외부 앱 공식 연동 명세를 확인한 뒤 확정한다.

## Consumer 상태

### Anonymous
로그인 없이 Onboarding과 무료 범위의 공개 이야기를 이용한다. 저장한 이야기·코스, 청취 기록, 청취 설정은 현재 앱의 차량 로컬 저장소에 남긴다. 비회원의 내 이야기에는 이 차량에서만 보인다는 점을 표시한다. 기기 간 동기화와 계정용 `/v1/users/me` 쓰기는 제공하지 않는다. 무료 한도에 Installation ID가 필요하더라도 차량 고유 번호나 계정 식별자로 사용하지 않는다.

### Authenticated
저장, 이력, Preference, Purchase, 저장 지역 Sync.

6번에서는 저장한 이야기·코스·청취 기록을 먼저 동기화한다. 서버는 검증한 인증 결과로 계정 소유자를 정한다. 차량 프로필 이름, Android 사용자 ID, VIN·CRN이나 앱이 보낸 임의의 사용자 ID를 계정 인증에 사용하지 않는다. 로그아웃 또는 계정 전환 시 이전 계정의 목록을 숨기고, 대기 중인 쓰기 요청이 다른 계정으로 전송되지 않도록 분리한다.

## 6번 서버 저장에 적용할 기준

- 차량 앱은 설치된 상태에서 **비회원으로 계속 이용**할 수 있어야 한다. 차량의 OS 게스트 프로필, Pleos App Market의 설치·업데이트 로그인, Road Story의 비회원 상태는 서로 다른 범위다. OS 게스트는 기본 AAOS에서 임시 사용자이므로 대상 Pleos 차량에서 설치 앱 실행과 프로필 종료 후 기록 보존 여부를 별도로 확인한다. [AAOS 다중 사용자](https://source.android.com/docs/automotive/users_accounts/multi_user) · [Pleos App Market 업데이트](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/app-update-guide)
- 비회원 이용을 막는 로그인 화면을 띄우지 않는다. 로그인과 기록 가져오기는 정차 중 사용자가 직접 시작한다. 처음 로그인해도 차량에 있던 비회원 기록은 자동으로 업로드하지 않는다. 가져오기를 제공하는 경우 사용자가 선택한 항목만 계정에 복사하고, 기존 기록에 없던 재생 시각을 생성하지 않는다.
- Pleos는 통합 계정과 차량 프로필 동기화를 공개하고 있다. 확인한 공개 Pleos Connect SDK 문서에는 외부 앱이 계정 인증 결과를 발급받고 자체 서버에서 검증하는 계약이 안내되어 있지 않다. Playground SDK 등록 정보와 Vehicle Data API의 OAuth 권한은 Road Story 계정 로그인 근거로 사용하지 않는다. [Pleos Account](https://pleos.ai/service-solution/pleos-account?locale=ko-KR) · [Pleos Connect](https://document.pleos.ai/en/docs/connect/guide/connect) · [공개 SDK 목록](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/) · [Vehicle Data API 인증](https://document.pleos.ai/en/api-reference/vehicle-data-api/getting-started/for-business-users/authorization)
- 운영 연동 전에 Road Story 프로젝트의 계정 연동 허용 여부, 공식 로그인 시작·복귀 방식, 서버 검증 방법, 차량과 웹에서 같은 계정을 매핑할 수 있는 식별자, 세션 만료·로그아웃·전환 동작, 테스트 환경을 확인한다. 그 전에도 로컬 테스트 인증으로 계정별 저장과 격리를 검증할 수 있다.
- 6번 검증에서는 비회원 재생·로컬 저장, 같은 계정의 차량/웹 동기화, 두 계정과 비회원의 기록 분리, 로그아웃 후 이전 계정의 목록과 대기 요청이 노출되지 않는지 확인한다.

## CMS Role

`EDITOR`, `REVIEWER`, `PUBLISHER`, `ADMIN`, `PARTNER_VIEWER`

## 보안·Privacy

- 짧은 Access Token
- Secure Storage의 Refresh Token
- Token URL·Log 금지
- 정밀 Route와 Identity 분리
- Location Retention 최소화
- Account 삭제와 Purchase Restore 지원

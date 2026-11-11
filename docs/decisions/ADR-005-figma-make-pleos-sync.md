# ADR-005 — Figma Make 시안의 PLEOS Native 반영

## 상태

Accepted — 2026-11-08

## 맥락

Figma Make의 `로드스토리 차량용 오디오 앱`은 PLEOS Design Guideline을 적용한 실행 가능한 React 시안이다. 실제 Vehicle 앱은 `frontend/pleos-app`의 Android Native 구현이며 React 런타임을 포함하지 않는다.

## 결정

- Figma Make의 React 소스를 Android 앱에 복사하지 않고, 화면 구조와 디자인 토큰을 Kotlin View 구현으로 변환한다.
- `pleos-tokens.css`의 Dark Neutral 팔레트와 8px spacing grid, 100/80/64px 버튼, 32/24/16px radius 체계를 네이티브 토큰의 기준으로 사용한다.
- 상단 Page Navigation은 앱 아이콘·현재 섹션·카테고리·차량 상태·설정 Action을 포함하며, 하위 화면에서는 Back/Depth Title 구조로 전환한다.
- 홈·목적지·테마 화면에는 Figma와 동일한 고정 Mini Player를 제공한다.
- 확장 화면은 홈의 2열 추천 영역, 이미지 오버레이 Course Card, Player의 Cover/Control 2열 구조를 그대로 사용한다.
- 확장 창은 Figma Make의 1230px 기준 캔버스를 PLEOS 실행 창 1682px에 비례 확장하고, 세로로 넘치는 콘텐츠는 시안처럼 스크롤로 유지한다.
- Figma Make의 Asta Sans 가변 폰트와 `app-icon.svg`의 벡터 경로를 Android 자원으로 동일하게 적용한다.
- `SCR-HOME-001`, `SCR-PLAYER-001`, `SCR-DEST-001`, `SCR-COURSE-LIST-001`, `SCR-COURSE-DETAIL-001`의 정보 구조와 문구를 Figma 시안에 맞춘다.
- `DRIVING`에서는 기존 `VehicleUiPolicy`를 유지하며 상세 탐색을 열지 않는다.
- 사진은 분위기를 전달하는 참고 이미지일 뿐 역사적 기록 사진으로 취급하지 않는다.

## 원본

- Make source: `src/App.tsx`, `src/VehicleUI.tsx`, `src/pleos-tokens.css`

## 이미지 출처

Unsplash License: <https://unsplash.com/license>

- `road_story_seongsu.jpg`: rawkkim — <https://unsplash.com/@rawkkim>
- `road_story_hangang.jpg`: Minku Kang — <https://unsplash.com/@minkus>
- `road_story_seoul_forest.jpg`: lee seunghyub — <https://unsplash.com/@sobalc>

## 결과

네이티브 앱은 Figma 시안과 같은 PLEOS 시각 언어를 사용하면서 MediaSession, TextToSpeech, Driving Restriction 등 기존 플랫폼 경계를 유지한다.

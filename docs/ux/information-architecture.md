# 정보 구조(Information Architecture)

## Platform 역할

### Vehicle
- 자유 주행·목적지 Story 시작
- Playback Control
- ThemeCourse 시작
- Story 저장
- 최소 Preference 조정

### Mobile/Web
- Story 상세, Map, Image, Source, Timeline
- 저장·이력·지역·Preference
- Subscription·Purchase
- 오류 신고

### CMS
- Place, Story, Source, Rights, Audio, Trigger, Course, Review, Publish, Analytics

## Vehicle IA

```text
SCR-HOME-001 차량 홈
├─ SCR-FREE-001 자유 주행
├─ SCR-PLAYER-001 현재 재생
│  └─ SCR-NEXT-001 다음 Story
├─ SCR-DEST-001 목적지 Playlist
├─ SCR-COURSE-LIST-001 ThemeCourse 목록
│  └─ SCR-COURSE-DETAIL-001 ThemeCourse 상세
├─ SCR-SAVED-001 저장 Story
├─ SCR-PREF-001 관심 주제
├─ SCR-OFFLINE-001 Offline
└─ SCR-ERROR-001 오류
```

## Mobile IA

```text
SCR-M-HOME-001
├─ SCR-M-MAP-001
├─ SCR-M-STORY-001
├─ SCR-M-HISTORY-001
├─ SCR-M-SAVED-001
├─ SCR-M-COURSE-001
├─ SCR-M-AREA-001
├─ SCR-M-PAY-001
└─ SCR-M-REPORT-001
```

## Content Status

```text
DRAFT → IN_REVIEW → VERIFIED → PUBLISHED → RETIRED
```

`PUBLISHED` Story는 직접 수정하지 않고 새 Version을 만든다.

## Category Enum

`PLACE_NAME`, `HISTORY`, `LANDMARK`, `ARCHITECTURE`, `PERSON`, `EVENT`, `INDUSTRY`, `DAILY_LIFE`, `MARKET_COMMERCE`, `NATURE_GEOGRAPHY`, `FOOD_CULTURE`, `FILM_LITERATURE`, `CHILDREN`

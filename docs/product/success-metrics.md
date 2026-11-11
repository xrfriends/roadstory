# 성공 지표(Success Metrics)

## North Star Metric

### `MET-NSM-001` — 활성 Journey당 의미 있는 Story 완료 수

의미 있는 완료:
- 유효한 Journey Context에서 시작
- Audio 80% 이상 재생
- 즉시 Dislike 또는 재생 시점 오류 신고 없음

## Activation

| ID | 지표 | 목표 |
|---|---|---:|
| `MET-ACT-001` | 첫 Story 완료율 | ≥ 60% |
| `MET-ACT-002` | 세 번째 Story 완료율 | ≥ 35% |
| `MET-ACT-003` | 첫 Session 저장률 | ≥ 10% |
| `MET-ACT-004` | Onboarding 완료율 | ≥ 80% |

## Engagement

| ID | 지표 | 목표 |
|---|---|---:|
| `MET-ENG-001` | Story 완료율 | ≥ 70% |
| `MET-ENG-002` | Skip 비율 | ≤ 20% |
| `MET-ENG-003` | MAU당 Journey 수 | ≥ 3 |
| `MET-ENG-004` | ThemeCourse 시작률 | ≥ 15% |
| `MET-ENG-005` | ThemeCourse 완료율 | ≥ 30% |

## Retention·Revenue

| ID | 지표 | 목표 |
|---|---|---:|
| `MET-RET-001` | Week-1 Retention | ≥ 25% |
| `MET-RET-002` | Month-1 Retention | ≥ 15% |
| `MET-REV-001` | Paywall Click Rate | 8~15% |
| `MET-REV-002` | Activated-to-paid | 2~5% |
| `MET-REV-003` | Trial-to-paid | 15~30% |
| `MET-REV-004` | 첫 달 Paid Retention | 50~70% |

위 수치는 MVP 내부 검증 기준이며 업계 Benchmark가 아니다.

## Content·Technical Quality

| ID | 지표 | 목표 |
|---|---|---:|
| `MET-CONT-001` | Source가 있는 Published Story | 100% |
| `MET-CONT-003` | 사실 오류 신고 | 1,000회당 1건 미만 |
| `MET-TECH-001` | 재생 시점 오류 | 3% 미만 |
| `MET-TECH-002` | Wrong-road Trigger | 1% 미만 |
| `MET-TECH-003` | Cache Audio 시작 | P95 2초 미만 |
| `MET-TECH-004` | Consumer API | P95 500ms 미만 |
| `MET-TECH-005` | Crash-free Journey | ≥ 99.5% |

## Event Property

`storyId`, Journey Mode, 대략적 지역, Playback Variant, Category, Trigger Type, `DrivingState`, Completion Percentage, Experiment Variant를 기록한다. 연속적인 원본 Route History는 일반 Analytics에 저장하지 않는다.

# 접근성(Accessibility)

## Vehicle

- 큰 Touch Target
- 정밀 Gesture 금지
- 핵심 Action은 1 Tap 또는 Voice
- 주행 중 Text Input 금지
- Control 위치 일관성
- Color만으로 상태 표현 금지

## Visual

- 높은 Contrast
- System Font Scaling
- Image 내 필수 Text 금지
- Icon과 Label 병기
- 주행 화면 Text Hierarchy 최대 3단계
- 명확한 Focus 표시

## Audio

- Mobile·정차 상태 Transcript
- 이해를 방해하는 Background Music 금지
- Volume Normalization
- Navigation·Call Audio Focus 처리
- Interrupt 상태 시각 표시

## Cognitive

- 짧은 문장
- 한 번에 하나의 결정
- 일관된 Action 문구
- 구체적이고 복구 가능한 Error
- 종료 후 예기치 않은 Auto-play 금지

## Screen Reader

- 모든 Control에 Accessible Name
- Playback Progress 상태 제공
- 장식 Image 제외
- Dynamic Update 알림
- List 위치와 총개수 제공

## 검증

- 긴 Text 없이 Vehicle 핵심 Flow 완료
- Screen Reader로 Mobile 핵심 Flow 완료
- Font Scaling 시 Action 유지
- High Contrast에서 정보 유지
- Interrupt 후 Playback Position 유지

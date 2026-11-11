import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { App } from '../src/App';
import { ErrorState } from '../src/components/States';
import { DrivingStateProvider } from '../src/context/DrivingStateContext';

function renderAt(path: string, state: 'DRIVING' | 'STOPPED' | 'UNKNOWN' = 'STOPPED') { return render(<DrivingStateProvider state={state}><MemoryRouter initialEntries={[path]}><App /></MemoryRouter></DrivingStateProvider>); }

test('SCR-HOME-001 Primary Card는 최대 4개다', () => { renderAt('/'); expect(screen.getAllByRole('link')).toHaveLength(4); });
test('Vehicle 화면은 관리용 Screen ID를 사용자에게 노출하지 않는다', () => { const { container } = renderAt('/'); expect(container.querySelector('[data-screen-id="SCR-HOME-001"]')).toBeInTheDocument(); expect(screen.queryByText(/SCR-HOME-001/)).not.toBeInTheDocument(); });
test('DRIVING에서는 Transcript 접근을 SCR-DRIVE-LIMIT-001로 차단한다', () => { renderAt('/mobile/story/story-published-1', 'DRIVING'); expect(screen.getByText(/안전을 위해 자세한 내용/)).toBeInTheDocument(); expect(screen.queryByRole('heading', { name: 'Transcript' })).not.toBeInTheDocument(); });
test('STOPPED에서는 Story 상세를 표시한다', () => { renderAt('/mobile/story/story-published-1'); expect(screen.getByRole('heading', { name: 'Transcript' })).toBeInTheDocument(); });
test('Player 핵심 Control을 표시한다', () => { renderAt('/player'); ['재생·일시정지', '다음', '저장', '관심 없음', '종료'].forEach(name => expect(screen.getByRole('button', { name })).toBeInTheDocument()); });
test('목적지 화면은 시간과 Story 수 및 하나의 Primary Action을 제공한다', () => { renderAt('/destination'); expect(screen.getByRole('heading', { name: /이야기 3편/ })).toBeInTheDocument(); expect(screen.getByText('12분')).toBeInTheDocument(); expect(screen.getByRole('button', { name: /전체 재생/ })).toBeInTheDocument(); });
test('API Error State가 복구 Action을 제공한다', () => { const retry = vi.fn(); render(<ErrorState retry={retry} />); fireEvent.click(screen.getByRole('button', { name: '다시 시도' })); expect(retry).toHaveBeenCalledOnce(); });

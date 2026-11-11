import { Link } from 'react-router-dom';

const primaryCards = [
  { label: '주변 이야기 듣기', detail: '성수동 이야기 3개', icon: '▶', to: '/free-drive', primary: true },
  { label: '목적지까지 이야기', detail: '서울숲까지 18분', icon: '⌁', to: '/destination' },
  { label: '테마 드라이브', detail: '추천 코스 둘러보기', icon: '◇', to: '/courses' },
  { label: '최근 이어듣기', detail: '수제화 거리 · 1분 남음', icon: '↻', to: '/player' },
];
export function HomePage() { return <main className="vehicle-shell home-page" data-screen-id="SCR-HOME-001"><header className="topline"><div><span className="location-dot" />성수동</div><div className="trip-status">서울숲까지 18분</div></header><section className="hero"><p className="eyebrow">지금 이 길에 어울리는 이야기</p><h1>성수동을<br />들으며 달려보세요</h1></section><nav className="card-grid" aria-label="Road Story 주요 메뉴">{primaryCards.slice(0, 4).map(card => <Link className={`primary-card${card.primary ? ' featured' : ''}`} to={card.to} key={card.label}><span className="card-icon" aria-hidden="true">{card.icon}</span><span className="card-copy"><strong>{card.label}</strong><small>{card.detail}</small></span><span className="card-arrow" aria-hidden="true">→</span></Link>)}</nav></main>; }

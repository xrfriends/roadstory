export function LoadingState() { return <p role="status">이야기를 불러오고 있습니다.</p>; }
export function EmptyState() { return <p>이 지역의 이야기는 아직 준비 중입니다.</p>; }
export function ErrorState({ retry }: { retry?: () => void }) { return <section role="alert"><p>이야기를 불러오지 못했습니다.</p>{retry && <button onClick={retry}>다시 시도</button>}</section>; }
export function DriveLimit() { return <section aria-labelledby="drive-limit-title"><h1 id="drive-limit-title">안전한 주행을 위한 제한</h1><p>안전을 위해 자세한 내용은 차량이 정차한 후 확인할 수 있습니다.</p></section>; }


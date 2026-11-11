import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import type { components } from '../generated/api';

type Library = components['schemas']['UserLibrary'];
type LibraryItem = components['schemas']['LibraryItem'];

function LibraryList({ title, items }: { title: string; items: LibraryItem[] }) {
  return <section className="library-group" aria-label={title}>
    <h2>{title} {items.length}</h2>
    {items.length ? <ul>{items.map(item => <li key={item.id}>{item.title}</li>)}</ul>
      : <p>표시할 기록이 없어요.</p>}
  </section>;
}

export function DevelopmentLibraryPage() {
  const [library, setLibrary] = useState<Library | null>(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const token = useRef<string | null>(null);
  const request = useRef<AbortController | null>(null);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => () => { request.current?.abort(); }, []);

  async function load(credential: string) {
    request.current?.abort();
    const nextRequest = new AbortController();
    request.current = nextRequest;
    setLoading(true);
    setMessage('');
    try {
      const response = await fetch('/v1/users/me/library', {
        headers: { Authorization: `Bearer ${credential}` },
        signal: nextRequest.signal,
        cache: 'no-store',
        redirect: 'error',
      });
      if (nextRequest.signal.aborted) return;
      if (response.status === 401) {
        token.current = null;
        setLibrary(null);
        setMessage('개발 계정 연결을 확인해 주세요.');
        return;
      }
      if (!response.ok) throw new Error('Library request failed');
      const result = await response.json() as Library;
      if (nextRequest.signal.aborted) return;
      token.current = credential;
      setLibrary(result);
    } catch {
      if (!nextRequest.signal.aborted) setMessage('서버에 연결하지 못했어요. 다시 확인해 주세요.');
    } finally {
      if (!nextRequest.signal.aborted) setLoading(false);
    }
  }

  function signIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const credential = input.current?.value.trim();
    if (input.current) input.current.value = '';
    if (credential) void load(credential);
  }

  function signOut() {
    request.current?.abort();
    token.current = null;
    setLibrary(null);
    setLoading(false);
    setMessage('');
  }

  return <main className="vehicle-shell" data-screen-id="SCR-SAVED-001">
    <header className="topline"><span>개발 환경</span><Link className="text-action" to="/">홈</Link></header>
    <section className="page-heading compact"><h1>내 이야기</h1></section>
    {library ? <>
      <div className="library-controls">
        <button className="quiet-button" disabled={loading} onClick={() => {
          if (token.current) void load(token.current);
        }}>동기화</button>
        <button className="quiet-button" onClick={signOut}>로그아웃</button>
      </div>
      <div className="library-groups">
        <LibraryList title="저장한 이야기" items={library.savedStories} />
        <LibraryList title="저장한 코스" items={library.savedCourses} />
        <LibraryList title="청취 기록" items={library.history} />
      </div>
    </> : <form className="library-controls" onSubmit={signIn}>
      <label>개발용 토큰<input type="password" autoComplete="off" maxLength={128} required ref={input} /></label>
      <button type="submit" className="quiet-button" disabled={loading}>개발 계정 연결</button>
    </form>}
    {loading && <p role="status">서버 기록을 확인하고 있어요.</p>}
    {message && <p role="alert">{message}</p>}
  </main>;
}

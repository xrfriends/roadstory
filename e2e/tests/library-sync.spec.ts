import { expect, test } from '@playwright/test';

const firstAccount = process.env.ROAD_STORY_DEV_ACCOUNT_A_TOKEN;
const secondAccount = process.env.ROAD_STORY_DEV_ACCOUNT_B_TOKEN;
const port = process.env.VITE_DEV_LIBRARY_SYNC_API_PORT ?? '8080';
const backend = `http://127.0.0.1:${port}`;
const publishedStory = 'story-published-1';
const storyTitle = '성수동 수제화 거리';

test.use({ trace: 'off' });

test('차량과 같은 서버 보관함을 읽고 로그아웃 후 다른 계정의 기록만 표시한다', async ({ page, request }) => {
  test.skip(process.env.VITE_DEV_LIBRARY_SYNC !== 'true' || !firstAccount || !secondAccount ||
    !/^[1-9][0-9]{0,4}$/.test(port) || Number(port) > 65535,
    '로컬 동기화 서버와 두 개발 계정이 필요합니다.');

  const libraryPath = `${backend}/v1/users/me/library`;
  const storyPath = `${backend}/v1/users/me/saved-stories/${publishedStory}`;
  const headers = { Authorization: `Bearer ${firstAccount}` };
  const before = await request.get(libraryPath, { headers });
  expect(before.status()).toBe(200);
  const previous = await before.json() as { savedStories: { id: string }[] };
  const alreadySaved = previous.savedStories.some(item => item.id === publishedStory);
  const other = await request.get(libraryPath, { headers: { Authorization: `Bearer ${secondAccount}` } });
  expect(other.status()).toBe(200);
  const otherLibrary = await other.json() as { savedStories: { id: string }[] };
  test.skip(otherLibrary.savedStories.some(item => item.id === publishedStory), '두 번째 계정에 같은 시험 이야기 기록이 있습니다.');

  try {
    expect((await request.put(storyPath, { headers })).status()).toBe(204);
    await page.goto('/dev/library');
    const connect = async (credential: string) => {
      await page.getByLabel('개발용 토큰').fill(credential);
      await page.getByRole('button', { name: '개발 계정 연결' }).click();
      await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();
    };
    await connect(firstAccount!);
    const saved = page.getByRole('region', { name: '저장한 이야기' });
    const history = page.getByRole('region', { name: '청취 기록' });
    await expect(saved.getByText(storyTitle, { exact: true })).toBeVisible();
    if (process.env.ROAD_STORY_VERIFY_DEVICE_HISTORY === 'true') {
      await expect(history.getByText(storyTitle, { exact: true })).toBeVisible();
    }

    await page.getByRole('button', { name: '로그아웃' }).click();
    await expect(saved).toHaveCount(0);
    await connect(secondAccount!);
    await expect(saved.getByText(storyTitle, { exact: true })).toHaveCount(0);
    if (process.env.ROAD_STORY_VERIFY_DEVICE_HISTORY === 'true') {
      await expect(history.getByText(storyTitle, { exact: true })).toHaveCount(0);
    }
  } finally {
    if (!alreadySaved) expect((await request.delete(storyPath, { headers })).status()).toBe(204);
  }
});

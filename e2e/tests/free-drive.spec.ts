import { expect, test } from '@playwright/test';

test('FLW-FREE-001 → FLW-PLAY-001 → FLW-SAVE-001', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('[data-screen-id="SCR-HOME-001"]')).toBeVisible();
  await page.getByRole('link', { name: /주변 이야기 듣기/ }).click();
  await expect(page.getByText('SCR-FREE-001')).toBeVisible();
  await page.getByRole('button', { name: '이야기 시작' }).click();
  await expect(page.locator('[data-screen-id="SCR-PLAYER-001"]')).toBeVisible();
  await page.getByTestId('save-story').click();
  await expect(page.getByTestId('save-story')).toBeVisible();
});

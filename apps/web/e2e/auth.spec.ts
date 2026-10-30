import { expect, test } from '@playwright/test';

test('dashboard yêu cầu phiên đăng nhập', async ({ page }) => {
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login/);
  await expect(page.getByRole('heading', { name: 'Đăng nhập' })).toBeVisible();
});

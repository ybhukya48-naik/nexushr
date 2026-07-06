import { test, expect } from '@playwright/test';

test('login flow works', async ({ page }) => {
  await page.goto('/');
  
  // Should redirect to login
  await expect(page).toHaveURL('/login');
  
  // Login with demo user
  await page.fill('input[type="text"]', 'admin');
  await page.fill('input[type="password"]', 'password');
  await page.click('button[type="submit"]');
  
  // Should redirect to dashboard
  await expect(page).toHaveURL('/dashboard');
  
  // Should show nav
  await expect(page.getByRole('navigation')).toBeVisible();
});

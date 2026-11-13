import type { Page } from '@playwright/test';

export async function openNavigationMenu(page: Page) {
  if ((page.viewportSize()?.width ?? 1280) < 1000) {
    const open = page.getByRole('button', { name: /^Open campus menu/ });
    if (await open.count()) await open.click();
  }
}

export async function goToNavigation(page: Page, label: string) {
  await openNavigationMenu(page);
  await page.getByRole('tab', { name: label, exact: true }).click();
}

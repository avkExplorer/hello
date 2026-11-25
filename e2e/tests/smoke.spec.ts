import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

// Mirrors the contract in avk.yaml (the deployer runs the same checks after a traffic switch).

test('home page loads', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle('hello');
  await expect(page.getByRole('heading', { name: 'hello' })).toBeVisible();
});

test('liveness and readiness answer 200', async ({ request }) => {
  expect((await request.get('/healthz')).status()).toBe(200);
  const ready = await request.get('/readyz');
  expect(ready.status()).toBe(200);
  expect(await ready.json()).toEqual({ status: 'ready' });
});

test('ping returns the value seeded by the database migration', async ({ request }) => {
  const res = await request.get('/api/v1/ping');
  expect(res.status()).toBe(200);
  expect(await res.json()).toEqual({ message: 'pong' });
});

test('unknown paths do not leak internals', async ({ request }) => {
  // Anything that is not public needs a login first, so an unknown path looks like a protected one: 401 here (the ci
  // stack has no login configured) or a redirect to the login page when Keycloak is configured. Never a page or a stack trace.
  const res = await request.get('/definitely-not-here', { maxRedirects: 0 });
  expect([401, 404, 302]).toContain(res.status());
  expect(await res.text()).not.toMatch(/Exception|at com\.|org\.springframework/);
});

test('protected pages are never served without a login', async ({ request }) => {
  for (const path of ['/admin', '/api/v1/me', '/profile']) {
    const res = await request.get(path, { maxRedirects: 0 });
    expect([401, 302], path).toContain(res.status());
  }
});

// Accessibility (axe-core). Reported always; it fails the run only when AXE_ENFORCE=1 (turned on in Phase 6).
test('home page accessibility', async ({ page }, testInfo) => {
  await page.goto('/');
  const results = await new AxeBuilder({ page }).analyze();
  await testInfo.attach('axe-violations', { body: JSON.stringify(results.violations, null, 2), contentType: 'application/json' });
  if (process.env.AXE_ENFORCE === '1') {
    expect(results.violations).toEqual([]);
  } else if (results.violations.length > 0) {
    console.warn(`axe: ${results.violations.length} violation(s) (not enforced yet)`);
  }
});

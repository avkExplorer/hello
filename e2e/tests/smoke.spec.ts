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

// Accessibility gate (axe-core, WCAG 2.1 A and AA). Any serious or critical violation fails the run; lesser ones are
// attached to the report. Covers the home page, the contact page and every policy page (the login page lives on the
// shared login host and is checked in the platform's own browser tests).
const PAGES = ['/', '/legal/terms.html', '/legal/privacy.html', '/legal/cookies.html', '/legal/accessibility.html', '/legal/contact.html'];

for (const path of PAGES) {
  test(`accessibility: ${path}`, async ({ page }, testInfo) => {
    await page.goto(path);
    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    await testInfo.attach('axe-violations', { body: JSON.stringify(results.violations, null, 2), contentType: 'application/json' });
    const blocking = results.violations.filter(v => v.impact === 'serious' || v.impact === 'critical');
    expect(blocking.map(v => `${v.id} (${v.impact}): ${v.nodes.length} element(s)`), `${path} has blocking accessibility violations`).toEqual([]);
  });
}

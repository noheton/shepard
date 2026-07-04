import { chromium } from 'playwright';

const URL_BASE = 'https://shepard.nuclide.systems';
const SCREENSHOT_DIR = '/opt/shepard/.claude/worktrees/agent-a24dfc718ee8d81e5/aidocs/agent-findings/screenshots/bug-coll-appid-route-002-fix';

(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 3840, height: 2160 } });
  const page = await ctx.newPage();

  // 1. Sign in as alice
  await page.goto(`${URL_BASE}/api/auth/signin`);
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: `${SCREENSHOT_DIR}/00-signin.png`, fullPage: true });

  // Click Keycloak / OIDC button
  try {
    await page.click('button:has-text("Sign in"), a:has-text("Sign in"), button:has-text("keycloak")', { timeout: 5000 });
  } catch (e) { /* maybe already on Keycloak form */ }

  await page.waitForLoadState('networkidle');
  // Keycloak login form
  await page.fill('input[name="username"]', 'alice');
  await page.fill('input[name="password"]', 'alice-demo');
  await page.click('button:has-text("Sign In"), input[type="submit"]');
  await page.waitForLoadState('networkidle');

  // 2. Navigate to /collections list
  await page.goto(`${URL_BASE}/collections`);
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: `${SCREENSHOT_DIR}/01-collections-list.png`, fullPage: true });

  // Find first collection link and click
  const firstCollLink = await page.locator('a[href*="/collections/"][href*="01"]').first();
  const href = await firstCollLink.getAttribute('href');
  console.log('LIVE collection URL:', href);

  // Navigate to it
  await page.goto(`${URL_BASE}${href}`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(3000);
  await page.screenshot({ path: `${SCREENSHOT_DIR}/02-live-current-state-bug-visible.png`, fullPage: true });

  // Capture the URL + the network responses
  console.log('Current URL:', page.url());

  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });

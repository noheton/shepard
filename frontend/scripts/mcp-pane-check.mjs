import { chromium } from 'playwright';

const browser = await chromium.launch();
const ctx = await browser.newContext({ ignoreHTTPSErrors: true });
const page = await ctx.newPage();

// Pump the OIDC password flow to get a token, then attach it as a cookie to skip login.
// Actually simpler: use the demo admin credentials to log in via the Keycloak page.
await page.goto('https://shepard.nuclide.systems/me#mcp');
await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});

// If a Keycloak login form appears, fill it.
if (await page.locator('input[name="username"]').count()) {
  await page.fill('input[name="username"]', 'admin');
  await page.fill('input[name="password"]', 'admin-demo');
  await page.click('input[type="submit"], button[type="submit"]');
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
}

// Navigate to /me#mcp after login redirect.
await page.goto('https://shepard.nuclide.systems/me#mcp');
await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});

await page.screenshot({ path: '/tmp/mcp-pane.png', fullPage: true });

// Look for the SSE URL field
const sse = await page.locator('input[aria-label="MCP SSE endpoint URL"]').first();
const present = await sse.count() > 0;
const value = present ? await sse.inputValue() : null;
console.log(JSON.stringify({ panePresent: present, sseUrl: value }));

// Also check that the menu item is visible
const menuMcp = await page.locator('text="MCP"').first().isVisible().catch(() => false);
console.log(JSON.stringify({ menuMcpVisible: menuMcp }));

await browser.close();

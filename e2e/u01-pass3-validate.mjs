import { chromium } from 'playwright';
import fs from 'fs';

const OUT = '/opt/shepard/aidocs/agent-findings/validation-screenshots';
fs.mkdirSync(OUT, { recursive: true });

const COLL = 'https://shepard.nuclide.systems/collections/493423';
const DO = 'https://shepard.nuclide.systems/collections/493423/dataobjects/495374';
const FR = 'https://shepard.nuclide.systems/collections/493423/dataobjects/495374/filereferences/495420';

const USERNAME = 'claude-opus-4-7';
const PASSWORD = 'f-ai-r-2026';

const VIEWPORTS = [
  { name: '4k', width: 3840, height: 2160 },
  { name: '1920', width: 1920, height: 1080 },
  { name: '1440', width: 1440, height: 900 },
];

async function loginIfNeeded(page) {
  // If we land on a keycloak login form, fill it.
  const userField = page.locator('input[name="username"]');
  if (await userField.count()) {
    await userField.fill(USERNAME);
    await page.fill('input[name="password"]', PASSWORD);
    await page.click('input[type="submit"], button[type="submit"]');
    await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  }
}

async function checkSidebar(page, label) {
  return await page.evaluate(() => {
    const sb = document.querySelector('.sidebar-container');
    const mainCol = document.querySelector('.v-col-9, [class*="v-col-9"]') || document.querySelector('main .v-row > .v-col:nth-child(2)');
    const cols = Array.from(document.querySelectorAll('.v-row > .v-col'));
    const sidebarStyle = sb ? sb.getAttribute('style') : null;
    const sidebarRect = sb ? sb.getBoundingClientRect() : null;
    // find any main content area: try the second col of the layout row
    let mainRect = null;
    let mainText = '';
    let mainHasContent = false;
    if (cols.length >= 2) {
      const second = cols[1];
      mainRect = second.getBoundingClientRect();
      mainText = (second.innerText || '').trim().slice(0, 200);
      mainHasContent = (second.innerText || '').trim().length > 5;
    }
    // Look for the file table — name column header
    const nameHeaders = Array.from(document.querySelectorAll('th, .v-data-table-column--name, [class*="data-table"] th')).map(e => (e.textContent || '').trim()).filter(t => t.toLowerCase().includes('name'));
    return {
      sidebarPresent: !!sb,
      sidebarStyle,
      sidebarRect,
      mainRect,
      mainHasContent,
      mainTextSample: mainText,
      colsCount: cols.length,
      nameHeaders,
    };
  });
}

const consoleErrors = [];
const browser = await chromium.launch();

const results = [];

for (const vp of VIEWPORTS) {
  const ctx = await browser.newContext({
    ignoreHTTPSErrors: true,
    viewport: { width: vp.width, height: vp.height },
    deviceScaleFactor: 1,
  });
  const page = await ctx.newPage();
  page.on('console', msg => {
    if (msg.type() === 'error' || (msg.text() && msg.text().includes('Hydration'))) {
      consoleErrors.push({ viewport: vp.name, type: msg.type(), text: msg.text() });
    }
  });

  // First navigate, login once per context
  await page.goto(COLL, { waitUntil: 'networkidle', timeout: 30000 }).catch(() => {});
  await loginIfNeeded(page);
  // After login go to collection page
  await page.goto(COLL, { waitUntil: 'networkidle', timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(2000);
  let info = await checkSidebar(page, `${vp.name}-collection`);
  results.push({ vp: vp.name, page: 'collection', info });
  if (vp.name === '4k') {
    await page.screenshot({ path: `${OUT}/u01-pass3-4k-collection.png`, fullPage: false });
  }

  await page.goto(DO, { waitUntil: 'networkidle', timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(2000);
  info = await checkSidebar(page, `${vp.name}-dataobject`);
  results.push({ vp: vp.name, page: 'dataobject', info });
  if (vp.name === '4k') {
    await page.screenshot({ path: `${OUT}/u01-pass3-4k-dataobject.png`, fullPage: false });
  }

  await page.goto(FR, { waitUntil: 'networkidle', timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(2500);
  info = await checkSidebar(page, `${vp.name}-filereference`);
  results.push({ vp: vp.name, page: 'filereference', info });

  // Screenshot FR for each viewport
  let shotName;
  if (vp.name === '4k') shotName = 'u01-pass3-4k-filereference.png';
  else if (vp.name === '1920') shotName = 'u01-pass3-1920-filereference.png';
  else shotName = 'u01-pass3-1440-filereference.png';
  await page.screenshot({ path: `${OUT}/${shotName}`, fullPage: false });

  // For 4k, also do an after-scroll screenshot
  if (vp.name === '4k') {
    await page.evaluate(() => window.scrollTo(0, 800));
    await page.waitForTimeout(800);
    await page.screenshot({ path: `${OUT}/u01-pass3-4k-after-scroll.png`, fullPage: false });
    const afterScroll = await checkSidebar(page, '4k-after-scroll');
    results.push({ vp: vp.name, page: 'after-scroll', info: afterScroll });
  }

  await ctx.close();
}

await browser.close();

console.log(JSON.stringify({ results, consoleErrors }, null, 2));

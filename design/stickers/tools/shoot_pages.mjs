// Screenshots whole HTML pages with Chromium: node shoot_pages.mjs PAGES.json, where PAGES.json is
// [{"html": "/abs/page.html", "png": "/abs/out.png", "width": 1400}] (width optional). Used by baskets_contact.py.
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
// PLAYWRIGHT_MODULE may point at a global install (ESM ignores NODE_PATH), e.g. /usr/lib/node_modules/playwright/index.mjs
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright');

const pages = JSON.parse(readFileSync(process.argv[2], 'utf8'));
const browser = await chromium.launch();
const page = await browser.newPage({ deviceScaleFactor: 1, viewport: { width: 1400, height: 900 } });
for (const p of pages) {
  if (p.width) await page.setViewportSize({ width: p.width, height: 900 });
  await page.goto(pathToFileURL(p.html).href);
  await page.screenshot({ path: p.png, fullPage: true });
}
await browser.close();

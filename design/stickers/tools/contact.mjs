// Renders the contact-sheet pages written by contact.py: node contact.mjs JOBS (lines of "html<TAB>png<TAB>scale").
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
// PLAYWRIGHT_MODULE may point at a global install (ESM ignores NODE_PATH), e.g. /usr/lib/node_modules/playwright/index.mjs
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright');

const jobs = readFileSync(process.argv[2], 'utf8').trim().split('\n').map((l) => l.split('\t'));
const browser = await chromium.launch(process.env.HTTPS_PROXY ? { proxy: { server: process.env.HTTPS_PROXY } } : {});
for (const [src, png, scale] of jobs) {
  const page = await browser.newPage({ deviceScaleFactor: Number(scale), viewport: { width: 400, height: 300 } });
  await page.goto(pathToFileURL(src).href);
  await page.screenshot({ path: png, fullPage: true });
  await page.close();
}
await browser.close();

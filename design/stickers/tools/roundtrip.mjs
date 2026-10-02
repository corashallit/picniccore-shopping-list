// Renders each pair from pairs.json (written by roundtrip.py) with Chromium: <name>.dsl.png and <name>.vd.png.
// node roundtrip.mjs DIR [BACKGROUND]
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { chromium } from 'playwright';

const [dir, bg = '#7A7A7A'] = process.argv.slice(2);
const pairs = JSON.parse(readFileSync(join(dir, 'pairs.json'), 'utf8'));
const browser = await chromium.launch();
const page = await browser.newPage({ deviceScaleFactor: 1 });
for (const p of pairs) {
  for (const [kind, svg] of [['dsl', p.dsl], ['vd', p.vd]]) {
    await page.setContent(`<html><body style="margin:0;background:${bg}"><div id="t" style="display:inline-block;line-height:0">${svg}</div></body></html>`);
    await page.locator('#t').screenshot({ path: join(dir, `${p.name}.${kind}.png`) });
  }
}
await browser.close();

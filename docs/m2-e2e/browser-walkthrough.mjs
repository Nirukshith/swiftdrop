// M2 browser walkthrough: drives the real Angular app (served by the gateway) in headless Edge or
// Chrome through the customer tracking, history, receipt and rating pages, against the running
// Docker core stack. No dependencies: it talks to the browser's DevTools protocol with Node's
// built-in fetch and WebSocket (Node 22).
//
//   docker compose -f deploy/docker-compose.yml --env-file deploy/.env --profile core up -d
//   node docs/m2-e2e/browser-walkthrough.mjs            # screenshots go to ./m2-browser-out
//
// Optional environment: GW (default http://localhost:8080), PARCEL (http://localhost:8082, the
// internal status endpoint), BROWSER (path to msedge/chrome), OUT (output folder).
// Every check prints "ok" or "FAIL"; the exit code is the number of failures.
import { spawn, execFileSync } from 'node:child_process';
import { randomBytes, randomUUID } from 'node:crypto';
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { tmpdir } from 'node:os';

const GW = process.env.GW ?? 'http://localhost:8080';
const PARCEL = process.env.PARCEL ?? 'http://localhost:8082';
const OUT = resolve(process.env.OUT ?? './m2-browser-out');
const PORT = 9300 + Math.floor(Math.random() * 500);
const BROWSERS = [
  process.env.BROWSER,
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
  '/usr/bin/microsoft-edge',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
].filter(Boolean);

mkdirSync(OUT, { recursive: true });
let failures = 0;
const check = (name, expected, actual) => {
  const ok = expected === actual;
  if (!ok) failures++;
  console.log(ok ? `ok    ${name} (${actual})` : `FAIL  ${name}: expected ${expected}, got ${actual}`);
  return ok;
};
const say = (text) => console.log(`\n### ${text}`);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---------------------------------------------------------------------------- data via the APIs

async function api(method, url, { token, body } = {}) {
  const res = await fetch(url, {
    method,
    headers: { 'Content-Type': 'application/json; charset=utf-8', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* not JSON */ }
  return { status: res.status, json };
}

const PASSWORD = `E2e-${randomBytes(6).toString('hex')}!`;
async function customer(label) {
  const email = `m2-browser-${label}-${Date.now()}@example.com`;
  await api('POST', `${GW}/api/v1/auth/register`, { body: { email, password: PASSWORD, fullName: `Customer ${label}`, phoneNumber: '+94771234567' } });
  const login = await api('POST', `${GW}/api/v1/auth/login`, { body: { email, password: PASSWORD } });
  return { email, token: login.json?.accessToken };
}

function seedPricing() {
  // Demo pricing for Colombo and Kandy (no admin user is seeded); skipped when present.
  const pg = execFileSync('docker', ['ps', '--format', '{{.Names}}']).toString().split(/\r?\n/).find((n) => n.includes('postgres'));
  const sql = `INSERT INTO zones (name, served, base_fee_lkr) VALUES ('E2E Western', true, 300), ('E2E Central', true, 400) ON CONFLICT (name) DO NOTHING;
INSERT INTO zone_districts (zone_id, district) SELECT id, 'Colombo' FROM zones WHERE name = 'E2E Western' ON CONFLICT DO NOTHING;
INSERT INTO zone_districts (zone_id, district) SELECT id, 'Kandy' FROM zones WHERE name = 'E2E Central' ON CONFLICT DO NOTHING;
INSERT INTO pricing_rules (kind, rule_key, minimum_weight_kg, maximum_weight_kg, charge_lkr) VALUES ('WEIGHT_BAND', 'E2E_0_10', 0, 10, 200) ON CONFLICT DO NOTHING;
INSERT INTO pricing_rules (kind, rule_key, charge_lkr) VALUES ('SIZE', 'MEDIUM', 100) ON CONFLICT DO NOTHING;`;
  execFileSync('docker', ['exec', '-i', pg, 'psql', '-q', '-U', 'swiftdrop', '-d', 'ops_db'], { input: sql });
}

const COURIER = randomUUID();
async function changeStatus(id, from, to) {
  const body = { status: to, expectedCurrentStatus: from, courierId: COURIER };
  if (to === 'DELIVERED') body.proof = { recipientName: 'Recipient', proofPhotoPath: 'proofs/browser.jpg' };
  return api('PATCH', `${PARCEL}/internal/v1/parcels/${id}/status`, { body });
}

// ---------------------------------------------------------------------------- browser (DevTools protocol)

class Page {
  constructor(ws) { this.ws = ws; this.id = 0; this.pending = new Map(); this.events = []; }
  static async open(wsUrl) {
    const ws = new WebSocket(wsUrl);
    await new Promise((ok, fail) => { ws.onopen = ok; ws.onerror = fail; });
    const page = new Page(ws);
    ws.onmessage = (m) => {
      const msg = JSON.parse(m.data);
      if (msg.id && page.pending.has(msg.id)) {
        const { ok, fail } = page.pending.get(msg.id);
        page.pending.delete(msg.id);
        msg.error ? fail(new Error(msg.error.message)) : ok(msg.result);
      } else if (msg.method) {
        page.events.push(msg);
      }
    };
    return page;
  }
  send(method, params = {}, sessionId) {
    const id = ++this.id;
    this.ws.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }));
    return new Promise((ok, fail) => this.pending.set(id, { ok, fail }));
  }
}

/** One tab (optionally in its own browser context, i.e. a separate login). */
class Tab {
  constructor(browser, sessionId) { this.b = browser; this.s = sessionId; }
  send(method, params) { return this.b.send(method, params, this.s); }
  async eval(expression) {
    const r = await this.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description ?? r.exceptionDetails.text);
    return r.result.value;
  }
  async go(url) {
    await this.send('Page.navigate', { url });
    await this.waitFor('document.readyState === "complete"', 15000);
    await sleep(400);
  }
  async waitFor(expression, timeout = 10000) {
    const end = Date.now() + timeout;
    while (Date.now() < end) {
      try { if (await this.eval(`!!(${expression})`)) return true; } catch { /* page still loading */ }
      await sleep(100);
    }
    return false;
  }
  text() { return this.eval('document.body.innerText'); }
  async shot(name) {
    const { data } = await this.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    writeFileSync(join(OUT, `${name}.png`), Buffer.from(data, 'base64'));
    console.log(`      screenshot ${name}.png`);
  }
  /** Sets an input's value the way typing does, so Angular forms see it. */
  type(selector, value) {
    return this.eval(`(() => { const e = document.querySelector(${JSON.stringify(selector)});
      e.value = ${JSON.stringify(value)}; e.dispatchEvent(new Event('input', { bubbles: true }));
      e.dispatchEvent(new Event('change', { bubbles: true })); return true; })()`);
  }
  click(selector) {
    return this.eval(`(() => { const e = document.querySelector(${JSON.stringify(selector)}); e.click(); return true; })()`);
  }
  viewport(width, height, mobile = false) {
    return this.send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile });
  }
}

async function newTab(browser, { isolated = false } = {}) {
  const ctx = isolated ? (await browser.send('Target.createBrowserContext')).browserContextId : undefined;
  const { targetId } = await browser.send('Target.createTarget', { url: 'about:blank', ...(ctx ? { browserContextId: ctx } : {}) });
  const { sessionId } = await browser.send('Target.attachToTarget', { targetId, flatten: true });
  const tab = new Tab(browser, sessionId);
  await tab.send('Page.enable');
  await tab.send('Runtime.enable');
  await tab.viewport(1280, 900);
  return tab;
}

async function login(tab, email) {
  await tab.go(`${GW}/login`);
  await tab.waitFor('document.querySelector(\'[formcontrolname="email"]\')');
  await tab.type('[formcontrolname="email"]', email);
  await tab.type('[formcontrolname="password"]', PASSWORD);
  await tab.click('form button[type="submit"]');
  return tab.waitFor('!location.pathname.startsWith("/login")', 15000);
}

// ---------------------------------------------------------------------------- walkthrough

const browserPath = BROWSERS.find((p) => existsSync(p));
if (!browserPath) {
  console.log('No Edge or Chrome found; set BROWSER to its path.');
  process.exit(99);
}
const profile = join(tmpdir(), `m2-browser-${Date.now()}`);
const proc = spawn(browserPath, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank'], { stdio: 'ignore' });

let browser;
try {
  let version;
  for (let i = 0; i < 50 && !version; i++) {
    try { version = await (await fetch(`http://127.0.0.1:${PORT}/json/version`)).json(); } catch { await sleep(200); }
  }
  browser = await Page.open(version.webSocketDebuggerUrl);
  console.log(`browser: ${version.Browser}`);

  say('1. Setup through the APIs: pricing data, customers A and B, A\'s parcel (Tamil recipient, Sinhala city)');
  seedPricing();
  const A = await customer('a');
  const B = await customer('b');
  const created = await api('POST', `${GW}/api/v1/parcels`, {
    token: A.token,
    body: {
      pickupAddress: { line1: '45 Galle Road', city: 'Colombo 03', district: 'Colombo', postalCode: '00300' },
      dropoffAddress: { line1: '12 Temple Road', city: 'මහනුවර', district: 'Kandy', postalCode: '20000' },
      weightKg: 2.5, size: 'MEDIUM', recipientName: 'சுனில் குமார்', recipientPhone: '+94771234567', notes: 'Gate code 4321',
    },
  });
  check('customer A creates a parcel (API)', 201, created.status);
  const { id, trackingNumber: tn } = created.json;
  console.log(`      parcel ${tn} (${id}), fee LKR ${created.json.fee}`);
  await api('PUT', `${PARCEL}/internal/v1/parcels/${id}/assignment`, { body: { courierId: COURIER, assignedBy: 'dispatcher-demo' } });

  say('2. Customer A signs in through the UI');
  const tab = await newTab(browser);
  check('login redirects away from /login', true, await login(tab, A.email));

  say('3. My parcels: list, then filter by tracking number');
  await tab.go(`${GW}/customer/parcels`);
  check('history shows the parcel', true, await tab.waitFor(`document.body.innerText.includes(${JSON.stringify(tn)})`));
  check('history says newest first', true, (await tab.text()).includes('Your parcels, newest first') || await tab.eval('!!document.querySelector(\'[aria-label="Your parcels, newest first"], caption\')'));
  await tab.shot('01-history');
  await tab.type('#search-filter', tn.slice(-6).toLowerCase());
  await tab.eval('(() => { document.querySelector(\'[data-testid="history-filters"]\').requestSubmit(); return true; })()');
  check('filter keeps the parcel', true, await tab.waitFor(`location.search.includes("q=") && document.body.innerText.includes(${JSON.stringify(tn)})`));
  check('URL carries the filter', true, (await tab.eval('location.search')).includes(`q=${tn.slice(-6).toLowerCase()}`));
  await tab.shot('02-history-filtered');
  await tab.type('#search-filter', 'NO-SUCH-PARCEL');
  await tab.eval('(() => { document.querySelector(\'[data-testid="history-filters"]\').requestSubmit(); return true; })()');
  check('no match shows the filtered empty state', true, await tab.waitFor('document.body.innerText.includes("No parcels match these filters")'));

  say('4. Parcel detail: the timeline updates live while the status changes (no reload)');
  await tab.go(`${GW}/customer/parcels/${id}`);
  check('detail shows the tracking number', true, await tab.waitFor(`document.querySelector('[data-testid="tracking-number"]')?.textContent.includes(${JSON.stringify(tn)})`));
  check('stream connects (Live)', true, await tab.waitFor('document.querySelector("app-live-indicator [data-state]")?.getAttribute("data-state") === "live"', 10000));
  await tab.shot('03-detail-created');
  const steps = [['CREATED', 'PICKED_UP', 'Picked up'], ['PICKED_UP', 'IN_TRANSIT', 'In transit'],
    ['IN_TRANSIT', 'OUT_FOR_DELIVERY', 'Out for delivery'], ['OUT_FOR_DELIVERY', 'DELIVERED', 'Delivered']];
  for (const [from, to, label] of steps) {
    const started = Date.now();
    await changeStatus(id, from, to);
    const seen = await tab.waitFor(`document.querySelector('[data-testid="current-status"]')?.textContent.trim() === ${JSON.stringify(label)}`, 5000);
    const ms = Date.now() - started;
    check(`${label} appears on the open page within 2 s`, true, seen && ms < 2000);
    console.log(`      ${label}: ${ms} ms from the status change to the page`);
    if (to === 'OUT_FOR_DELIVERY') await tab.shot('04-detail-live-out-for-delivery');
  }
  check('receipt button appears once delivered', true, await tab.waitFor('document.querySelector(\'[data-testid="receipt-button"]\')'));
  check('rating card appears once delivered', true, await tab.waitFor('document.querySelector(\'[data-testid="rating-form"]\')', 5000));
  await tab.shot('05-detail-delivered');

  say('5. Download the PDF receipt from the page');
  const downloads = join(OUT, 'downloads');
  rmSync(downloads, { recursive: true, force: true });
  mkdirSync(downloads, { recursive: true });
  await browser.send('Browser.setDownloadBehavior', { behavior: 'allow', downloadPath: downloads });
  await tab.click('[data-testid="receipt-button"] button');
  let file;
  for (let i = 0; i < 100 && !file; i++) {
    file = readdirSync(downloads).find((f) => f.endsWith('.pdf'));
    if (!file) await sleep(100);
  }
  check('receipt file downloaded', `receipt-${tn}.pdf`, file);
  if (file) {
    const pdf = readFileSync(join(downloads, file));
    check('receipt is a PDF', '%PDF-', pdf.subarray(0, 5).toString('latin1'));
    check('receipt embeds the Tamil and Sinhala fonts', true, pdf.includes('NotoSansTamil') && pdf.includes('NotoSansSinhala'));
  }

  say('6. Rate the delivery, then reload: the rating is still there');
  await tab.eval('(() => { const e = document.querySelector(\'[data-testid="rating-score-5"]\'); e.checked = true; e.dispatchEvent(new Event("change", { bubbles: true })); return true; })()');
  await tab.type('#rating-comment', 'Courier was on time');
  await tab.eval('(() => { document.querySelector(\'[data-testid="rating-form"]\').requestSubmit(); return true; })()');
  check('thank-you message after rating', true, await tab.waitFor('document.querySelector(\'[data-testid="rating-saved"]\')?.textContent.includes("Thank you for your rating")'));
  await tab.shot('06-rated');
  await tab.go(`${GW}/customer/parcels/${id}`);
  check('rating still shown after reload', true, await tab.waitFor('document.querySelector(\'[data-testid="rating-stars"]\')?.getAttribute("aria-label") === "5 out of 5 stars"'));

  say('7. Public tracking page (no login): masked details and the full timeline');
  const guest = await newTab(browser, { isolated: true });
  await guest.go(`${GW}/track`);
  check('/track shows the search box', true, await guest.waitFor('document.querySelector("#tracking-number-input")'));
  await guest.type('#tracking-number-input', ` ${tn.toLowerCase()} `);
  await guest.eval('(() => { document.querySelector(\'[data-testid="track-search"]\').requestSubmit(); return true; })()');
  check('search opens the parcel', true, await guest.waitFor(`location.pathname === ${JSON.stringify(`/track/${tn}`)} && document.querySelector('[data-testid="tracking-number"]')`, 10000));
  const publicText = await guest.text();
  check('recipient is masked', true, publicText.includes('ச*** க***'));
  check('no address, phone or notes on the public page', false, /Temple|\+9477|Gate code|சுனில்/.test(publicText));
  check('public timeline ends with Delivered', 'Delivered', await guest.eval('document.querySelector(\'[data-testid="current-status"]\')?.textContent.trim()'));
  await guest.shot('07-public-track');
  await guest.go(`${GW}/track/NOT-A-NUMBER`);
  check('a badly formed number gets the format hint', true, await guest.waitFor('document.querySelector(\'[data-testid="track-invalid"]\')'));

  say('8. Customer B cannot see A\'s parcel');
  const other = await newTab(browser, { isolated: true });
  check('customer B signs in', true, await login(other, B.email));
  await other.go(`${GW}/customer/parcels/${id}`);
  check('B gets the no-access message', true, await other.waitFor('document.body.innerText.includes("You do not have access to this parcel")'));
  check('no details leak to B', false, (await other.text()).includes('Temple Road'));
  await other.shot('08-other-customer-denied');

  say('9. Phone width (360 px, NF-04)');
  for (const [name, t, url, root] of [['09-mobile-public-track', guest, `${GW}/track/${tn}`, 'app-track'],
    ['10-mobile-history', tab, `${GW}/customer/parcels`, 'app-customer-parcels']]) {
    await t.viewport(360, 780, true);
    await t.go(url);
    await t.waitFor(`document.body.innerText.includes(${JSON.stringify(tn)})`);
    // The M2 page itself must fit; the shared app header above it is reported separately.
    const widest = await t.eval(`Math.ceil(Math.max(...[...document.querySelectorAll(${JSON.stringify(`${root}, ${root} *`)})]
      .map((e) => e.getBoundingClientRect().right)))`);
    check(`${name}: M2 page fits 360 px`, true, widest <= 360);
    const page = await t.eval('document.documentElement.scrollWidth');
    const over = await t.eval(`[...document.querySelectorAll("body *")].filter((e) => !e.closest(${JSON.stringify(root)})
      && e.getBoundingClientRect().right > 361).map((e) => e.tagName.toLowerCase() + (e.textContent.trim() ? ' "' + e.textContent.trim().slice(0, 20) + '"' : '')).slice(0, 3).join(", ")`);
    console.log(`      whole page width ${page} px${page > 360 ? ` (wider than the screen because of the shared header: ${over})` : ''}`);
    await t.shot(name);
  }
  check('history uses cards on a phone', true, await tab.eval('getComputedStyle(document.querySelector(\'[data-testid="parcel-cards"]\')).display !== "none"'));

  say(`Result: ${failures} failed check(s); screenshots in ${OUT}`);
} catch (e) {
  failures++;
  console.log(`FAIL  walkthrough stopped: ${e.message}`);
} finally {
  try { browser?.ws.close(); } catch { /* ignore */ }
  proc.kill();
  await sleep(500);
  rmSync(profile, { recursive: true, force: true });
}
process.exit(failures);

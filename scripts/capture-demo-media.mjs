/**
 * Capture portfolio screenshots + a short live-demo GIF from the local UI.
 * Usage: node scripts/capture-demo-media.mjs
 */
import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { PNG } from 'pngjs';
import gifenc from 'gifenc';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');
const outDir = path.join(root, 'docs', 'screenshots');
const baseUrl = process.env.DEMO_URL || 'http://127.0.0.1:18080';
const email = process.env.DEMO_EMAIL || 'berk.mermer@example.com';
const password = process.env.DEMO_PASSWORD || 'securePass123';

const { GIFEncoder, quantize, applyPalette } = gifenc;

async function waitForMap(page) {
  await page.waitForSelector('.leaflet-container', { timeout: 30000 });
  await page.waitForSelector('.leaflet-tile-loaded', { timeout: 30000 });
  await page.waitForTimeout(3000);
}

async function login(page) {
  await page.goto(baseUrl, { waitUntil: 'networkidle' });
  await page.getByPlaceholder('mert.kaya@example.com').fill(email);
  await page.getByPlaceholder('Şifrenizi girin').fill(password);
  await page.getByRole('button', { name: 'Giriş yap' }).click();
  await page.waitForSelector('text=Sipariş takibi', { timeout: 30000 });
}

async function captureLogin(page) {
  await page.goto(baseUrl, { waitUntil: 'networkidle' });
  await page.getByPlaceholder('mert.kaya@example.com').fill(email);
  await page.getByPlaceholder('Şifrenizi girin').fill(password);
  await page.waitForTimeout(400);
  await page.screenshot({
    path: path.join(outDir, 'login-screen.png'),
    fullPage: true,
  });
}

async function captureDashboard(page) {
  await page.setViewportSize({ width: 1440, height: 900 });
  // Move courier to ~35% along the road so the blue polyline is visible
  await page.evaluate(async (url) => {
    const loginRes = await fetch(`${url}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'emre.kaya@example.com', password: 'securePass123' }),
    });
    const { token } = await loginRes.json();
    const start = { lat: 40.9755, lng: 29.055 };
    const pickup = { lat: 40.9901, lng: 29.0292 };
    const osrm = await fetch(
      `https://router.project-osrm.org/route/v1/driving/${start.lng},${start.lat};${pickup.lng},${pickup.lat}?overview=full&geometries=geojson`
    ).then((r) => r.json());
    const coords = osrm.routes[0].geometry.coordinates;
    const idx = Math.floor(coords.length * 0.35);
    const [lng, lat] = coords[idx];
    await fetch(`${url}/api/v1/couriers/location`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ latitude: lat, longitude: lng }),
    });
  }, baseUrl);
  await page.getByRole('button', { name: /Yenile/i }).click().catch(() => {});
  await waitForMap(page);
  // Wait for OSRM polyline + snap to settle so the moto sits on the blue line
  await page.waitForTimeout(3500);
  await page.screenshot({
    path: path.join(outDir, 'map-live.png'),
    fullPage: false,
  });

  const panel = page.locator('aside').first();
  await panel.screenshot({ path: path.join(outDir, 'order-sidebar.png') });
}

async function captureGif(page, frames = 12) {
  const frameDir = path.join(outDir, '_gif_frames');
  fs.rmSync(frameDir, { recursive: true, force: true });
  fs.mkdirSync(frameDir, { recursive: true });

  const api = baseUrl;
  // Re-walk a few mid-route points via UI refresh by calling location endpoint from page
  const walk = await page.evaluate(async (url) => {
    const loginRes = await fetch(`${url}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'emre.kaya@example.com', password: 'securePass123' }),
    });
    const { token } = await loginRes.json();
    const start = { lat: 40.9755, lng: 29.055 };
    const pickup = { lat: 40.9901, lng: 29.0292 };
    const osrm = await fetch(
      `https://router.project-osrm.org/route/v1/driving/${start.lng},${start.lat};${pickup.lng},${pickup.lat}?overview=full&geometries=geojson`
    ).then((r) => r.json());
    const coords = osrm.routes[0].geometry.coordinates;
    const step = Math.max(1, Math.floor(coords.length / 14));
    const points = [];
    for (let i = 0; i < coords.length; i += step) {
      points.push([coords[i][1], coords[i][0]]);
    }
    points.push([coords[coords.length - 1][1], coords[coords.length - 1][0]]);
    return { token, points };
  }, api);

  const paths = [];
  for (let i = 0; i < Math.min(frames, walk.points.length); i += 1) {
    const [lat, lng] = walk.points[i];
    await page.evaluate(
      async ({ url, token, lat, lng }) => {
        await fetch(`${url}/api/v1/couriers/location`, {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`,
          },
          body: JSON.stringify({ latitude: lat, longitude: lng }),
        });
      },
      { url: api, token: walk.token, lat, lng }
    );
    await page.waitForTimeout(1100);
    const framePath = path.join(frameDir, `frame-${String(i).padStart(2, '0')}.png`);
    await page.screenshot({ path: framePath, fullPage: false });
    paths.push(framePath);
  }

  const gifPath = path.join(outDir, 'live-tracking-demo.gif');
  const gif = GIFEncoder();
  for (const p of paths) {
    const png = PNG.sync.read(fs.readFileSync(p));
    const data = new Uint8ClampedArray(png.data.buffer);
    const palette = quantize(data, 256);
    const index = applyPalette(data, palette);
    gif.writeFrame(index, png.width, png.height, { palette, delay: 180 });
  }
  gif.finish();
  fs.writeFileSync(gifPath, Buffer.from(gif.bytes()));
  fs.rmSync(frameDir, { recursive: true, force: true });
  console.log('Wrote', gifPath);
}

async function main() {
  fs.mkdirSync(outDir, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });

  console.log('login-screen.png');
  await captureLogin(page);

  console.log('dashboard');
  await login(page);
  await captureDashboard(page);

  console.log('gif');
  await captureGif(page);

  await browser.close();
  console.log('Done');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});

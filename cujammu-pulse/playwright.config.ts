import { defineConfig } from '@playwright/test';
const port = Number(process.env.PULSE_QA_PORT ?? 4183);
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid loopback QA port');
const origin = `http://127.0.0.1:${port}`;
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  use: { baseURL: origin, channel: 'msedge', headless: true },
  projects: [
    { name: 'desktop-web', use: { viewport: { width: 1280, height: 900 } } },
    { name: 'small-web', use: { viewport: { width: 390, height: 844 } } },
  ],
  webServer: { command: 'node scripts/test-server.mjs', url: `${origin}/app/`, reuseExistingServer: false },
});

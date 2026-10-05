import { defineConfig } from '@playwright/test';

const launchOptions = { args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] };
const desktop = { viewport: { width: 1280, height: 800 } };
const phone = { viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true };

export default defineConfig({
  testDir: 'tests',
  timeout: 60_000,
  reporter: 'list',
  // Hebrew browser: the site picks its language from the browser (site/js/i18n.js); tests/i18n.spec.ts covers the others
  use: { baseURL: 'http://127.0.0.1:4173', ignoreHTTPSErrors: true, launchOptions, locale: 'he-IL' },
  webServer: { command: 'node tools/serve.mjs 4173', url: 'http://127.0.0.1:4173', reuseExistingServer: true },
  projects: [
    { name: 'desktop-light', use: { ...desktop, colorScheme: 'light' } },
    { name: 'desktop-dark', use: { ...desktop, colorScheme: 'dark' } },
    { name: 'phone-light', use: { ...phone, colorScheme: 'light' } },
    { name: 'phone-dark', use: { ...phone, colorScheme: 'dark' } },
  ],
});

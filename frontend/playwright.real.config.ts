import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e-real',
  fullyParallel: false,
  workers: 1,
  timeout: 240_000,
  reporter: 'list',
  use: {
    ...devices['Desktop Chrome'],
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    video: { mode: 'on', size: { width: 1440, height: 960 } },
    viewport: { width: 1440, height: 960 },
    launchOptions: { slowMo: 80 },
  },
})

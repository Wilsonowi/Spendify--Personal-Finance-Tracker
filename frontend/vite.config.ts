import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: 'Spendify',
        short_name: 'Spendify',
        description: 'Private expense ingestion and transfer reconciliation.',
        theme_color: '#132a25',
        background_color: '#edf0eb',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: '/icons/spendify-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/spendify-512.png', sizes: '512x512', type: 'image/png' },
        ],
      },
      workbox: {
        navigateFallbackDenylist: [/^\/api\//],
        runtimeCaching: [],
      },
    }),
  ],
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/actuator': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})


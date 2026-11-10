import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5175,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'https://hr-api.cyond.com',
        changeOrigin: true,
        secure: true,
        headers: {
          Origin: 'https://hr.cyond.com',
        },
      },
    },
  },
})

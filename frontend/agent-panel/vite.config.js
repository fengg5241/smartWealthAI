import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  base: '/agent/',
  build: {
    outDir: resolve(__dirname, '../../src/main/resources/static/agent'),
    emptyOutDir: true,
    assetsDir: 'assets'
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/login.html': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})

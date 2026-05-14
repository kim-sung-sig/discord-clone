export default defineNuxtConfig({
  compatibilityDate: '2026-05-13',
  modules: ['@pinia/nuxt'],
  css: ['~/assets/css/main.css'],
  devtools: { enabled: false },
  runtimeConfig: {
    public: {
      apiBaseUrl: process.env.NUXT_PUBLIC_API_BASE_URL ?? 'http://127.0.0.1:8080'
    }
  },
  typescript: {
    strict: true
  }
})

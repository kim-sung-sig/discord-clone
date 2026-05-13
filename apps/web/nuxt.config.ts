export default defineNuxtConfig({
  compatibilityDate: '2026-05-13',
  modules: ['@pinia/nuxt'],
  css: ['~/assets/css/main.css'],
  devtools: { enabled: false },
  typescript: {
    strict: true
  }
})

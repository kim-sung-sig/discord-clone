import { fileURLToPath } from 'node:url'

const packageSource = (path: string) => fileURLToPath(new URL(`../../packages/${path}`, import.meta.url))
const workspaceRoot = fileURLToPath(new URL('../..', import.meta.url))
const localWorkspaceRoot = process.env.DISCORD_WORKSPACE_ROOT
const viteFsAllow = localWorkspaceRoot ? [workspaceRoot, localWorkspaceRoot] : [workspaceRoot]

export default defineNuxtConfig({
  compatibilityDate: '2026-05-13',
  modules: ['@pinia/nuxt'],
  css: ['~/assets/css/main.css', '~/assets/css/discord-vscode-theme.css', '~/assets/css/workbench-density.css'],
  devtools: { enabled: false },
  alias: {
    '@discord-clone/ui-contracts/screens': packageSource('ui-contracts/src/screens.ts'),
    '@discord-clone/platform-shell/capabilities': packageSource('platform-shell/src/capabilities.ts')
  },
  vite: {
    server: {
      fs: {
        allow: viteFsAllow
      }
    }
  },
  app: {
    head: {
      title: 'Discord Clone',
      meta: [
        { name: 'viewport', content: 'width=device-width, initial-scale=1, viewport-fit=cover' },
        { name: 'theme-color', content: '#111827' },
        { name: 'mobile-web-app-capable', content: 'yes' },
        { name: 'apple-mobile-web-app-capable', content: 'yes' },
        { name: 'apple-mobile-web-app-title', content: 'DClone' }
      ],
      link: [
        { rel: 'manifest', href: '/manifest.webmanifest' }
      ]
    }
  },
  runtimeConfig: {
    public: {
      apiBaseUrl: process.env.NUXT_PUBLIC_API_BASE_URL ?? 'http://127.0.0.1:8080'
    }
  },
  typescript: {
    strict: true
  }
})

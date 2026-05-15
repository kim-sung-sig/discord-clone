export default defineEventHandler((event) => {
  setHeader(event, 'cache-control', 'no-store')

  return {
    name: 'Discord Clone',
    manifest: '/manifest.webmanifest',
    serviceWorker: '/sw.js',
    offlineShell: '/offline.html'
  }
})

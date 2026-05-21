import { createServer } from 'node:net'
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'

async function getFreePort() {
  return await new Promise((resolve, reject) => {
    const server = createServer()
    server.on('error', reject)
    server.listen(0, '127.0.0.1', () => {
      const address = server.address()
      server.close(() => {
        if (!address || typeof address === 'string') {
          reject(new Error('Could not allocate a local Playwright port'))
          return
        }
        resolve(address.port)
      })
    })
  })
}

const port = await getFreePort()
const repoRoot = fileURLToPath(new URL('..', import.meta.url))
const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm'
const npmCliPath = process.env.npm_execpath
const passthroughArgs = process.argv.slice(2)
const args = ['run', 'e2e', '--workspace', '@discord-clone/web']

if (passthroughArgs.length > 0) {
  args.push('--', ...passthroughArgs)
}

const childEnvironment = {
  ...process.env,
  CI: '1',
  NUXT_DEV_PORT: String(port),
  PLAYWRIGHT_BASE_URL: `http://127.0.0.1:${port}`,
  PLAYWRIGHT_REUSE_EXISTING_SERVER: '0'
}

console.log(`[web-e2e-isolated] port=${port}`)

const command = npmCliPath ? process.execPath : npmCommand
const commandArgs = npmCliPath ? [npmCliPath, ...args] : args
const child = spawn(command, commandArgs, {
  cwd: repoRoot,
  env: childEnvironment,
  stdio: 'inherit'
})

child.on('exit', (code) => {
  process.exitCode = code ?? 1
})

child.on('error', (error) => {
  console.error(error)
  process.exitCode = 1
})

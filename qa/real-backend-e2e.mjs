import { spawnSync } from 'node:child_process'

const command = process.platform === 'win32' ? 'powershell.exe' : 'pwsh'
const args = [
  '-NoProfile',
  '-ExecutionPolicy',
  'Bypass',
  '-File',
  'qa/real-backend-e2e.ps1',
  ...process.argv.slice(2)
]

const result = spawnSync(command, args, {
  stdio: 'inherit',
  shell: process.platform === 'win32'
})

if (result.error) {
  console.error(result.error.message)
  process.exit(1)
}

process.exit(result.status ?? 1)

import { spawnSync } from 'node:child_process'

const command = process.platform === 'win32' ? 'gradlew.bat' : './gradlew'
const result = spawnSync(command, process.argv.slice(2), {
  stdio: 'inherit',
  shell: process.platform === 'win32'
})

if (result.error) {
  console.error(result.error.message)
  process.exit(1)
}

process.exit(result.status ?? 1)

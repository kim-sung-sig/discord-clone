import { describe, expect, it } from 'vitest'
import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { capabilitiesForSurface } from '@discord-clone/platform-shell/capabilities'
import {
  createDesktopShellAdapter,
  desktopShellCapabilities
} from '../src/capabilities'

const testDir = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(testDir, '../../..')
const webRoot = path.join(repoRoot, 'apps/web')
const forbiddenTauriImportPatterns = [
  '@tauri-apps',
  '__TAURI__',
  'window.__TAURI__',
  'tauri://'
]

async function collectTextFiles(directory: string): Promise<string[]> {
  const entries = await readdir(directory, { withFileTypes: true })
  const ignoredDirectories = new Set([
    'node_modules',
    '.nuxt',
    '.output',
    'dist',
    'coverage',
    'test-results'
  ])
  const textExtensions = new Set([
    '.js',
    '.jsx',
    '.mjs',
    '.cjs',
    '.ts',
    '.tsx',
    '.mts',
    '.cts',
    '.vue',
    '.json'
  ])

  const nested = await Promise.all(
    entries.map(async (entry) => {
      const fullPath = path.join(directory, entry.name)

      if (entry.isDirectory()) {
        return ignoredDirectories.has(entry.name) ? [] : collectTextFiles(fullPath)
      }

      return textExtensions.has(path.extname(entry.name)) ? [fullPath] : []
    })
  )

  return nested.flat()
}

describe('desktop shell capability boundary', () => {
  it('aligns desktop adapter capabilities with shared platform-shell contracts', () => {
    const sharedDesktopCapabilities = capabilitiesForSurface('desktop-app')
    const sharedNames = sharedDesktopCapabilities.map((capability) => capability.name)

    expect(sharedNames).toEqual(['notification', 'deep-link', 'tray'])
    expect(
      desktopShellCapabilities
        .filter((capability) => capability.source === 'platform-shell')
        .map((capability) => capability.name)
    ).toEqual(sharedNames)
    expect(desktopShellCapabilities).toContainEqual({
      name: 'window-state',
      source: 'desktop-shell',
      status: 'placeholder'
    })
  })

  it('exposes placeholder operations through a desktop adapter instead of native imports', async () => {
    const adapter = createDesktopShellAdapter()

    expect(adapter.surface).toBe('desktop-app')
    await expect(adapter.notify({ title: 'Message received' })).resolves.toEqual({
      capability: 'notification',
      status: 'placeholder'
    })
    await expect(adapter.openDeepLink('discord-clone://channels/general')).resolves.toEqual({
      capability: 'deep-link',
      status: 'placeholder'
    })
    await expect(adapter.setTrayStatus('connected')).resolves.toEqual({
      capability: 'tray',
      status: 'placeholder'
    })
    await expect(adapter.saveWindowState({ width: 1280, height: 800 })).resolves.toEqual({
      capability: 'window-state',
      status: 'placeholder'
    })
  })

  it('keeps apps/web free of direct Tauri import strings', async () => {
    const files = await collectTextFiles(webRoot)
    const violations: string[] = []

    for (const file of files) {
      const content = await readFile(file, 'utf8')
      const matchedPattern = forbiddenTauriImportPatterns.find((pattern) => content.includes(pattern))

      if (matchedPattern) {
        violations.push(`${path.relative(repoRoot, file)} contains ${matchedPattern}`)
      }
    }

    expect(violations).toEqual([])
  })

  it('keeps Tauri native permissions empty at the desktop shell baseline', async () => {
    const capabilityPath = path.join(
      repoRoot,
      'apps/desktop/src-tauri/capabilities/default.json'
    )
    const packagePath = path.join(repoRoot, 'apps/desktop/package.json')
    const capability = JSON.parse(await readFile(capabilityPath, 'utf8'))
    const packageJson = JSON.parse(await readFile(packagePath, 'utf8'))

    expect(capability.permissions).toEqual([])
    expect(packageJson.scripts.dev).toContain('Requires Tauri CLI and Rust toolchain')
    expect(packageJson.scripts.build).toContain('Requires Tauri CLI and Rust toolchain')
  })
})

import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

// Read the files from disk (vitest does not return stylesheet text for CSS imports).
// Checkouts with core.autocrlf expose CRLF files, so compare on LF.
const readLf = (relativePath: string) =>
  readFileSync(fileURLToPath(new URL(relativePath, import.meta.url)), 'utf8').replace(/\r\n/g, '\n')
const appSource = readLf('../../pages/app.vue')
const mainCss = readLf('../../assets/css/main.css')
const densityCss = readLf('../../assets/css/workbench-density.css')

const ruleBody = (css: string, selector: string) => {
  const start = css.indexOf(`${selector} {`)
  expect(start, `rule "${selector}" exists`).toBeGreaterThanOrEqual(0)
  return css.slice(start, css.indexOf('}', start))
}

describe('T219 shell CSS contracts', () => {
  const workspace = '.vscode-shell.app-shell .workbench.workspace'

  it('places every workspace child in an explicit grid row and column', () => {
    for (const child of ['workspace-explorer', 'editor-workbench', 'side-panel', 'workbench-secondary-view', 'status-bar', 'shell-api-error']) {
      const body = ruleBody(densityCss, `${workspace} > .${child}`)
      expect(body, child).toMatch(/grid-column:/)
      expect(body, child).toMatch(/grid-row:/)
    }
    expect(ruleBody(densityCss, '.vscode-shell.app-shell .editor-workbench > .chat-viewport')).toMatch(/grid-column: 1;/)
    expect(ruleBody(densityCss, '.vscode-shell.app-shell .bottom-panel .gateway-status-panel')).toMatch(/grid-column: 1;/)
    expect(ruleBody(densityCss, '.vscode-shell.app-shell .bottom-panel .voice-panel')).toMatch(/grid-column: 2;/)
  })

  it('places the shell regions explicitly and keeps the status bar a spaced flex row', () => {
    expect(ruleBody(densityCss, '.vscode-shell.app-shell .workbench-titlebar')).toMatch(/grid-row: 1;/)
    expect(ruleBody(densityCss, '.vscode-shell.app-shell .workbench-banner')).toMatch(/grid-row: 2;/)
    expect(ruleBody(densityCss, '.vscode-shell.app-shell .activity-bar')).toMatch(/grid-row: 2 \/ 4;/)
    expect(ruleBody(densityCss, workspace)).toMatch(/grid-row: 3;/)
    const statusBar = ruleBody(densityCss, `${workspace} > .status-bar`)
    expect(statusBar).toMatch(/display: flex;/)
    expect(statusBar).toMatch(/gap: 12px;/)
  })

  it('styles activity buttons for the dark theme with hover, active, and focus states', () => {
    const base = ruleBody(densityCss, '.vscode-shell.app-shell .activity-bar button')
    expect(base).toMatch(/background: transparent;/)
    expect(base).toMatch(/border: 0;/)
    expect(base).toMatch(/color: var\(--activity-fg/)
    expect(densityCss).toContain('.vscode-shell.app-shell .activity-bar button:hover {')
    expect(densityCss).toContain('.vscode-shell.app-shell .activity-bar button[aria-current="page"] {')
    expect(densityCss).toContain('.vscode-shell.app-shell .activity-bar button:focus-visible {')
    // WCAG 1.4.11: the focus ring is white on the dark activity bar (>= 3:1 in all three themes)
    expect(ruleBody(densityCss, '.vscode-shell.app-shell .activity-bar button:focus-visible'))
      .toMatch(/outline: 2px solid var\(--activity-fg-active, #ffffff\);/)
    expect(mainCss).toMatch(/--activity-fg: #c5c5c5;/)
    expect(mainCss).toMatch(/--activity-fg-active: #ffffff;/)
    const pill = ruleBody(densityCss, '.vscode-shell.app-shell .activity-bar .server-pill')
    expect(pill).toMatch(/font-size: 13px;/)
  })

  it('has no rules left for the removed dev panel, banner button, or hidden activity labels', () => {
    for (const css of [mainCss, densityCss]) {
      expect(css).not.toContain('.real-api-panel')
      expect(css).not.toMatch(/workbench-banner button/)
      expect(css).not.toMatch(/activity-bar button span/)
    }
    expect(mainCss).not.toMatch(/\.channel-group h2 \{\n {2}display: none/)
  })

  it('keys activity icons on test ids, not on English titles', () => {
    expect(mainCss).not.toMatch(/button\[title=/)
    expect(densityCss).not.toMatch(/button\[title=/)
    for (const id of ['explorer', 'search', 'calls', 'settings']) {
      expect(mainCss).toContain(`.activity-bar button[data-testid="activity-${id}"]::before {`)
    }
  })

  it('applies the mobile layout from a media query without the JS-set platform attribute', () => {
    const mobileStart = densityCss.indexOf('@media (max-width: 720px) {')
    expect(mobileStart).toBeGreaterThanOrEqual(0)
    const mobile = densityCss.slice(mobileStart)
    expect(mobile).not.toMatch(/\[data-platform-surface/)
    expect(mobile).toMatch(/grid-template-columns: minmax\(0, 1fr\);/)
    expect(mobile).toMatch(/grid-template-columns: 34px minmax\(0, 1fr\) auto;/)
    expect(mobile).toContain('.mobile-pane-voice .bottom-panel {')
  })

  it('keeps the user panel name on one line', () => {
    const body = ruleBody(mainCss, '.user-panel-identity strong,\n.user-panel-identity .voice-state')
    expect(body).toMatch(/white-space: nowrap;/)
    expect(body).toMatch(/text-overflow: ellipsis;/)
  })

  it('does not render the theme through an SSR inline style', () => {
    expect(appSource).not.toMatch(/:style=/)
    expect(appSource).not.toContain('shellThemeStyle')
    expect(mainCss).toMatch(/--workbench-bg: #1e1e1e;/)
    expect(mainCss).toMatch(/--workbench-border: #3c3c3c;/)
  })
})

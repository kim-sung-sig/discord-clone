import { readFileSync, writeFileSync } from 'node:fs'

const args = new Map()
for (let i = 2; i < process.argv.length; i += 2) {
  args.set(process.argv[i], process.argv[i + 1])
}

const sbomPath = args.get('--sbom')
const ecosystem = args.get('--ecosystem')
const outputPath = args.get('--output')
if (!sbomPath || !ecosystem || !outputPath) {
  throw new Error('usage: node qa/security-osv-scan.mjs --sbom <path> --ecosystem <Maven|npm> --output <path>')
}

const sbom = JSON.parse(readFileSync(sbomPath, 'utf8').replace(/^\uFEFF/, ''))
const components = Array.isArray(sbom.components) ? sbom.components : []
const packages = []
const seen = new Set()

for (const component of components) {
  const version = String(component.version ?? '').trim()
  if (!version) {
    continue
  }

  let name = String(component.name ?? '').trim()
  if (ecosystem === 'Maven') {
    const group = String(component.group ?? '').trim()
    if (!group || !name) {
      continue
    }
    name = `${group}:${name}`
  }
  if (!name || name.startsWith('@discord-clone/')) {
    continue
  }

  const key = `${ecosystem}:${name}:${version}`
  if (seen.has(key)) {
    continue
  }
  seen.add(key)
  packages.push({ name, version, ecosystem })
}

function roundUp1(value) {
  return Math.ceil(value * 10) / 10
}

function cvssBaseScore(score) {
  const raw = String(score ?? '').trim()
  const numeric = Number(raw)
  if (Number.isFinite(numeric)) {
    return numeric
  }

  const match = raw.match(/CVSS:3\.[01]\/[^ ]+/)
  if (!match) {
    return null
  }

  const metrics = Object.fromEntries(
    match[0]
      .split('/')
      .slice(1)
      .map((part) => part.split(':'))
      .filter(([key, value]) => key && value)
  )
  const av = { N: 0.85, A: 0.62, L: 0.55, P: 0.2 }[metrics.AV]
  const ac = { L: 0.77, H: 0.44 }[metrics.AC]
  const pr =
    metrics.S === 'C'
      ? { N: 0.85, L: 0.68, H: 0.5 }[metrics.PR]
      : { N: 0.85, L: 0.62, H: 0.27 }[metrics.PR]
  const ui = { N: 0.85, R: 0.62 }[metrics.UI]
  const c = { H: 0.56, L: 0.22, N: 0 }[metrics.C]
  const i = { H: 0.56, L: 0.22, N: 0 }[metrics.I]
  const a = { H: 0.56, L: 0.22, N: 0 }[metrics.A]

  if ([av, ac, pr, ui, c, i, a].some((metric) => metric === undefined)) {
    return null
  }

  const impactSubScore = 1 - (1 - c) * (1 - i) * (1 - a)
  const impact =
    metrics.S === 'C'
      ? 7.52 * (impactSubScore - 0.029) - 3.25 * Math.pow(impactSubScore - 0.02, 15)
      : 6.42 * impactSubScore
  if (impact <= 0) {
    return 0
  }

  const exploitability = 8.22 * av * ac * pr * ui
  return metrics.S === 'C'
    ? roundUp1(Math.min(1.08 * (impact + exploitability), 10))
    : roundUp1(Math.min(impact + exploitability, 10))
}

function severityFromBaseScore(score) {
  if (score === null) {
    return 'unknown'
  }
  if (score >= 9) {
    return 'critical'
  }
  if (score >= 7) {
    return 'high'
  }
  if (score >= 4) {
    return 'moderate'
  }
  if (score > 0) {
    return 'low'
  }
  return 'none'
}

function normalizeSeverity(vuln) {
  const explicit = String(vuln?.database_specific?.severity ?? '').toLowerCase()
  if (['critical', 'high', 'moderate', 'medium', 'low'].includes(explicit)) {
    return explicit === 'medium' ? 'moderate' : explicit
  }
  const order = ['none', 'low', 'moderate', 'high', 'critical']
  let highest = 'unknown'
  for (const severity of vuln?.severity ?? []) {
    const derived = severityFromBaseScore(cvssBaseScore(severity.score))
    if (derived !== 'unknown' && (highest === 'unknown' || order.indexOf(derived) > order.indexOf(highest))) {
      highest = derived
    }
  }
  return highest
}

async function postJson(url, body) {
  const signal = AbortSignal.timeout(30_000)
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
    signal
  })
  if (!response.ok) {
    throw new Error(`${url} failed with ${response.status}`)
  }
  return response.json()
}

async function getJson(url) {
  const response = await fetch(url, { signal: AbortSignal.timeout(30_000) })
  if (!response.ok) {
    throw new Error(`${url} failed with ${response.status}`)
  }
  return response.json()
}

const results = []
const detailCache = new Map()
for (let index = 0; index < packages.length; index += 100) {
  const batch = packages.slice(index, index + 100)
  const response = await postJson('https://api.osv.dev/v1/querybatch', {
    queries: batch.map((pkg) => ({
      version: pkg.version,
      package: {
        name: pkg.name,
        ecosystem: pkg.ecosystem
      }
    }))
  })
  for (let i = 0; i < batch.length; i += 1) {
    const vulns = response.results?.[i]?.vulns ?? []
    for (const vuln of vulns) {
      if (!detailCache.has(vuln.id)) {
        detailCache.set(vuln.id, await getJson(`https://api.osv.dev/v1/vulns/${encodeURIComponent(vuln.id)}`))
      }
      const details = detailCache.get(vuln.id)
      results.push({
        id: vuln.id,
        package: batch[i].name,
        ecosystem: batch[i].ecosystem,
        version: batch[i].version,
        severity: normalizeSeverity(details),
        title: details.summary ?? vuln.id,
        modified: vuln.modified ?? details.modified ?? null,
        aliases: details.aliases ?? [],
        url: `https://osv.dev/vulnerability/${vuln.id}`
      })
    }
  }
}

writeFileSync(
  outputPath,
  `${JSON.stringify(
    {
      scanner: 'osv-api',
      api: 'https://api.osv.dev/v1/querybatch',
      ecosystem,
      packageCount: packages.length,
      vulnerabilityCount: results.length,
      vulnerabilities: results
    },
    null,
    2
  )}\n`,
  'utf8'
)

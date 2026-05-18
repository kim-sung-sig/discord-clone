import type { NormalizedCspReport } from './security-headers'

export interface StoredCspTelemetry {
  report: NormalizedCspReport
  receivedAt: string
}

export interface CspTelemetrySummary {
  total: number
  byEffectiveDirective: Record<string, number>
}

export interface CspTelemetryStore {
  record(report: NormalizedCspReport, receivedAt: Date): void
  recent(limit?: number): StoredCspTelemetry[]
  summary(): CspTelemetrySummary
}

export interface InMemoryCspTelemetryStoreOptions {
  maxEntries?: number
}

export class InMemoryCspTelemetryStore implements CspTelemetryStore {
  private readonly maxEntries: number
  private readonly entries: StoredCspTelemetry[] = []

  constructor(options: InMemoryCspTelemetryStoreOptions = {}) {
    this.maxEntries = Math.max(1, options.maxEntries ?? 1_000)
  }

  record(report: NormalizedCspReport, receivedAt: Date): void {
    this.entries.unshift({
      report: { ...report },
      receivedAt: receivedAt.toISOString()
    })
    if (this.entries.length > this.maxEntries) {
      this.entries.length = this.maxEntries
    }
  }

  recent(limit = this.maxEntries): StoredCspTelemetry[] {
    return this.entries.slice(0, Math.max(0, limit)).map((entry) => ({
      report: { ...entry.report },
      receivedAt: entry.receivedAt
    }))
  }

  summary(): CspTelemetrySummary {
    const byEffectiveDirective: Record<string, number> = {}
    for (const entry of this.entries) {
      byEffectiveDirective[entry.report.effectiveDirective] =
        (byEffectiveDirective[entry.report.effectiveDirective] ?? 0) + 1
    }
    return {
      total: this.entries.length,
      byEffectiveDirective
    }
  }
}

export const defaultCspTelemetryStore = new InMemoryCspTelemetryStore()

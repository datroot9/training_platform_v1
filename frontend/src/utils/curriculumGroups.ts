import type { CurriculumResponse } from '../api/types'

export type CurriculumGroupRow = {
  representative: CurriculumResponse
  versionCount: number
}

function parseUpdatedAt(iso: string): number {
  const t = new Date(iso).getTime()
  return Number.isNaN(t) ? 0 : t
}

/** One row per curriculum family; `representative` is the version with newest `updatedAt`. */
export function groupCurriculaByFamily(items: CurriculumResponse[]): CurriculumGroupRow[] {
  const byGroup = new Map<number, CurriculumResponse[]>()
  for (const c of items) {
    const key = c.curriculumGroupId ?? c.id
    const list = byGroup.get(key) ?? []
    list.push(c)
    byGroup.set(key, list)
  }

  const rows: CurriculumGroupRow[] = []
  for (const versions of byGroup.values()) {
    const sorted = [...versions].sort((a, b) => parseUpdatedAt(b.updatedAt) - parseUpdatedAt(a.updatedAt))
    rows.push({
      representative: sorted[0],
      versionCount: sorted.length,
    })
  }

  rows.sort((a, b) => parseUpdatedAt(b.representative.updatedAt) - parseUpdatedAt(a.representative.updatedAt))
  return rows
}

/**
 * UI21 — Pure helpers for the container list page rework.
 *
 * Orchestration that does not require DOM / Nuxt context. Tested in
 * `tests/unit/containerListPage.test.ts` with the pure-helper pattern
 * (same shape as `Trace3DChannelPicker.test.ts`) because the project
 * does not ship `@vue/test-utils`.
 *
 * Scope:
 *   - filterContainersByQuery — local fallback / advanced-mode in-page
 *     refinement; the search header still calls the v2 search API for
 *     authoritative results.
 *   - filterByColumn          — per-column free-text refinement.
 *   - sortContainers          — explicit sort over BasicContainer fields.
 *   - groupByCollection       — advanced-mode-only accordion view.
 *   - sizeBarFraction         — normalised 0..1 value for the sizebar
 *     column (relative scale; max-per-page baseline).
 *   - partitionOrphans        — separate selectable orphans from
 *     non-orphans for bulk-delete safety.
 *   - urlParamsFromState/stateFromUrlParams — `?q=…&type=…&owner=…`
 *     paste-and-share support.
 */

import type { BasicContainer } from "@dlr-shepard/backend-client";

/** Minimal shape covering everything we read from BasicContainer in this module.
 *  Declared locally so tests don't have to materialise the full generated type. */
export interface ContainerRow {
  id: number;
  name: string;
  type: string;
  createdBy: string;
  createdAt: Date | string;
  updatedAt?: Date | string | null;
  updatedBy?: string | null;
}

/** Result of `partitionOrphans`. Includes the IDs that are safely
 *  deletable now plus the ones blocked by references. */
export interface OrphanPartition {
  /** Orphans: zero referencing-collections, safe to bulk delete. */
  deletable: number[];
  /** Non-orphans: at least one collection references them. */
  blockedByReferences: number[];
  /** Type does not support reference checks; refuse to delete in bulk. */
  unknownReferenceState: number[];
}

const lower = (s: unknown): string =>
  (typeof s === "string" ? s : "").toLowerCase();

/**
 * Free-text query across the columns visible on the list page.
 * Whitespace-trimmed and case-insensitive. A blank query matches all.
 */
export function filterContainersByQuery<T extends ContainerRow>(
  items: T[],
  query: string | null | undefined,
): T[] {
  const needle = (query ?? "").trim().toLowerCase();
  if (!needle) return items.slice();
  return items.filter(c =>
    lower(c.name).includes(needle) ||
    lower(c.type).includes(needle) ||
    lower(c.createdBy).includes(needle) ||
    String(c.id).includes(needle),
  );
}

/**
 * Restrict items to those whose `column` value contains `needle`.
 * Same case-insensitive substring rule as the global query.
 */
export function filterByColumn<T extends ContainerRow>(
  items: T[],
  column: keyof ContainerRow,
  needle: string | null | undefined,
): T[] {
  const q = (needle ?? "").trim().toLowerCase();
  if (!q) return items.slice();
  return items.filter(c => {
    const v = c[column];
    if (v == null) return false;
    if (v instanceof Date) return v.toISOString().toLowerCase().includes(q);
    return String(v).toLowerCase().includes(q);
  });
}

/** Sort by one column; ascending by default. Stable across ties. */
export function sortContainers<T extends ContainerRow>(
  items: T[],
  key: keyof ContainerRow,
  order: "asc" | "desc" = "asc",
): T[] {
  const copy = items.slice();
  const dir = order === "asc" ? 1 : -1;
  copy.sort((a, b) => {
    const av = a[key];
    const bv = b[key];
    if (av == null && bv == null) return 0;
    if (av == null) return 1; // nulls last
    if (bv == null) return -1;
    if (av instanceof Date || bv instanceof Date) {
      const at = av instanceof Date ? av.getTime() : new Date(av as string).getTime();
      const bt = bv instanceof Date ? bv.getTime() : new Date(bv as string).getTime();
      return (at - bt) * dir;
    }
    if (typeof av === "number" && typeof bv === "number") return (av - bv) * dir;
    return String(av).localeCompare(String(bv)) * dir;
  });
  return copy;
}

/** A row plus the IDs of the collections that reference it. */
export interface ContainerWithRefs<T extends ContainerRow = ContainerRow> {
  container: T;
  /** null = unknown / no CC1b endpoint; [] = orphan; [...] = referenced. */
  referencingCollectionIds: number[] | null;
}

/**
 * Group containers by referencing-collection id. A container with N
 * collections appears in N groups; one with zero collections lands in
 * the special "__orphans__" group. Containers whose reference state is
 * unknown land in "__unknown__".
 */
export function groupByCollection<T extends ContainerRow>(
  rows: ContainerWithRefs<T>[],
): Map<string, T[]> {
  const out = new Map<string, T[]>();
  const push = (k: string, c: T) => {
    const arr = out.get(k);
    if (arr) arr.push(c);
    else out.set(k, [c]);
  };
  for (const row of rows) {
    if (row.referencingCollectionIds === null) {
      push("__unknown__", row.container);
      continue;
    }
    if (row.referencingCollectionIds.length === 0) {
      push("__orphans__", row.container);
      continue;
    }
    for (const cid of row.referencingCollectionIds) {
      push(String(cid), row.container);
    }
  }
  return out;
}

/**
 * Normalise `value` against `max` so the sizebar column renders a
 * 0..1 width fraction. Falls back to 0 when max is non-positive (e.g.
 * the whole page is still loading reference counts).
 */
export function sizeBarFraction(value: number, max: number): number {
  if (!Number.isFinite(value) || value <= 0) return 0;
  if (!Number.isFinite(max) || max <= 0) return 0;
  return Math.max(0, Math.min(1, value / max));
}

/**
 * Split a selection into safe-to-delete vs reference-blocked.
 *
 *  - `deletable`: rows whose CC1b lookup returned an empty array (true
 *    orphans). Honours the
 *    `feedback_referenced_data_infinite_retention.md` invariant.
 *  - `blockedByReferences`: rows referenced by ≥1 collection. The
 *    operator must delete those references first.
 *  - `unknownReferenceState`: rows whose container kind has no CC1b
 *    endpoint (BASIC, SPATIALDATA, plugin types without the wire). We
 *    refuse to bulk delete these because we cannot prove they are
 *    orphans; the per-row delete affordance is the explicit path.
 */
export function partitionOrphans(
  selectedIds: number[],
  refsById: Map<number, number[] | null>,
): OrphanPartition {
  const deletable: number[] = [];
  const blockedByReferences: number[] = [];
  const unknownReferenceState: number[] = [];
  for (const id of selectedIds) {
    const refs = refsById.get(id);
    if (refs === undefined || refs === null) {
      unknownReferenceState.push(id);
    } else if (refs.length === 0) {
      deletable.push(id);
    } else {
      blockedByReferences.push(id);
    }
  }
  return { deletable, blockedByReferences, unknownReferenceState };
}

/** State that lives in the URL via `?q=…&type=…&owner=…`. */
export interface ListUrlState {
  q?: string;
  type?: string;
  owner?: string;
  page?: number;
  group?: "flat" | "collection";
}

const TYPE_KEYS = ["TIMESERIES", "FILE", "STRUCTUREDDATA", "SPATIALDATA", "HDF5", "VIDEO", "BASIC"];

/** Build the URL-search-params object from list state. Empty fields are
 *  omitted so the URL stays short. */
export function urlParamsFromState(state: ListUrlState): Record<string, string> {
  const out: Record<string, string> = {};
  if (state.q && state.q.trim()) out.q = state.q.trim();
  if (state.type && TYPE_KEYS.includes(state.type.toUpperCase())) {
    out.type = state.type.toUpperCase();
  }
  if (state.owner && state.owner.trim()) out.owner = state.owner.trim();
  if (state.page && state.page > 1) out.page = String(state.page);
  if (state.group === "collection") out.group = "collection";
  return out;
}

/** Inverse: parse a URLSearchParams-shaped record into typed state.
 *  Unknown / malformed values are silently dropped. */
export function stateFromUrlParams(
  params: Record<string, string | string[] | null | undefined>,
): ListUrlState {
  const read = (k: string): string | undefined => {
    const v = params[k];
    if (Array.isArray(v)) return typeof v[0] === "string" ? v[0] : undefined;
    return typeof v === "string" ? v : undefined;
  };
  const rawType = read("type");
  const type = rawType && TYPE_KEYS.includes(rawType.toUpperCase())
    ? rawType.toUpperCase()
    : undefined;
  const rawPage = read("page");
  const pageN = rawPage ? Number.parseInt(rawPage, 10) : NaN;
  const page = Number.isFinite(pageN) && pageN > 1 ? pageN : undefined;
  const rawGroup = read("group");
  const group =
    rawGroup === "collection" ? "collection" :
    rawGroup === "flat" ? "flat" :
    undefined;
  return {
    q: read("q") || undefined,
    type,
    owner: read("owner") || undefined,
    page,
    group,
  };
}

/** Re-export the BasicContainer type as ContainerRow-compatible —
 *  used to keep the call sites' type narrow. */
export type AnyBasicContainer = BasicContainer & Partial<ContainerRow>;

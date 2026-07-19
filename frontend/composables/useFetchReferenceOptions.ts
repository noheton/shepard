/**
 * UI-GAP-1 slice 2 — fetch references for a DataObject as picker options for
 * the materialize-mapping binding rows.
 *
 * Calls GET /v2/references?kind=...&dataObjectAppId=... for each of the three
 * kinds relevant to MAPPING_RECIPE bindings (file, uri, git) in parallel and
 * merges the results into a flat list of { appId, label, kind } items suitable
 * for a v-combobox. Raw appId text-entry is always still accepted by the
 * combobox so the picker degrades gracefully when no DataObject context is
 * available.
 *
 * URDF-REF-PICKER — useFetchUrdfOptions is a specialized variant that fetches
 * only kind=file and filters to names ending .urdf (case-insensitive), plus
 * any FileReference with a urn:shepard:urdf:role semantic annotation (checked
 * client-side via a name-suffix heuristic — full annotation lookup is deferred).
 *
 * URDF-FILEREF-PICKER-SEARCHABLE — useFetchAccessibleUrdfOptions is the
 * instance-wide, permission-filtered, search-as-you-type variant. It calls
 * GET /v2/references/urdf (a dedicated backend list) so the "Visualize in 3D →
 * URDF" picker is genuinely populated even when the current DataObject has no
 * URDF (the real robot model usually lives in another collection). The user
 * searches by name and never pastes a UUID (the "user never types an ID" rule).
 *
 * Design: aidocs/platform/191 §4, backlog UI-GAP-1 slice 2 + URDF-REF-PICKER +
 * URDF-FILEREF-PICKER-SEARCHABLE.
 */

import { naturalSort } from "~/utils/naturalSort";
import { unwrapList } from "~/utils/unwrapList";

export interface ReferenceOption {
  appId: string;
  label: string;
  kind: string;
}

/** The reference kinds relevant to MAPPING_RECIPE input bindings. */
export const MAPPING_REFERENCE_KINDS = ["file", "uri", "git"] as const;

/**
 * Pure helper — maps raw /v2/references items to typed ReferenceOption entries.
 * Exported for unit testing.
 */
export function mapToReferenceOptions(
  refs: { appId?: string; name?: string }[],
  kind: string,
): ReferenceOption[] {
  return refs.flatMap((r) => {
    if (!r.appId) return [];
    const label = r.name ? `${r.name} (${kind})` : `${r.appId} (${kind})`;
    return [{ appId: r.appId, label, kind }];
  });
}

function buildV2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function fetchKindOptions(
  baseUrl: string,
  dataObjectAppId: string,
  kind: string,
  accessToken: string | undefined,
): Promise<ReferenceOption[]> {
  const url =
    `${baseUrl}/v2/references` +
    `?kind=${encodeURIComponent(kind)}` +
    `&dataObjectAppId=${encodeURIComponent(dataObjectAppId)}`;
  try {
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${accessToken ?? ""}`,
        Accept: "application/json",
      },
    });
    // 403 (no access), 404 (DO missing), unknown-kind 400 → silent empty.
    if (!response.ok) return [];
    const refs = unwrapList<{ appId?: string; name?: string }>(await response.json());
    return mapToReferenceOptions(refs, kind);
  } catch {
    return [];
  }
}

/**
 * Fetches all reference options for a DataObject (file + uri + git in
 * parallel). Returns an empty array when `dataObjectAppId` is absent.
 */
export function useFetchReferenceOptions(
  dataObjectAppIdInput: MaybeRefOrGetter<string | undefined>,
) {
  const options = ref<ReferenceOption[]>([]);
  const isLoading = ref(false);

  async function refresh() {
    const appId = toValue(dataObjectAppIdInput);
    if (!appId) {
      options.value = [];
      return;
    }
    isLoading.value = true;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken as string | undefined;
      const baseUrl = buildV2BaseUrl();
      const perKind = await Promise.all(
        MAPPING_REFERENCE_KINDS.map((k) =>
          fetchKindOptions(baseUrl, appId, k, accessToken),
        ),
      );
      options.value = perKind.flat();
    } catch {
      options.value = [];
    } finally {
      isLoading.value = false;
    }
  }

  watch(
    () => toValue(dataObjectAppIdInput),
    () => void refresh(),
    { immediate: true },
  );

  return { options, isLoading, refresh };
}

/** Filter applied to URDF file-reference picks: name must end with .urdf (case-insensitive). */
export function isUrdfName(name: string): boolean {
  return name.toLowerCase().endsWith(".urdf");
}

// ── URDF-FILEREF-PICKER-SEARCHABLE ──────────────────────────────────────────

/** One accessible-URDF row as returned by GET /v2/references/urdf. */
export interface AccessibleUrdfItem {
  appId: string;
  name: string;
  dataObjectAppId?: string;
  collectionAppId?: string | null;
  collectionName?: string | null;
}

/** Debounce window for search-as-you-type on the accessible-URDF picker (ms). */
export const URDF_SEARCH_DEBOUNCE_MS = 300;

/**
 * Pure helper — maps accessible-URDF API rows to naturally-sorted picker
 * options. The label is "<name> — <collection>" so the user disambiguates the
 * same-named URDF across collections; when the collection is unknown the bare
 * name is used. Exported for unit testing.
 */
export function mapAccessibleUrdfOptions(items: AccessibleUrdfItem[]): ReferenceOption[] {
  const opts = items.flatMap((it): ReferenceOption[] => {
    if (!it.appId) return [];
    const coll = it.collectionName?.trim();
    const label = coll ? `${it.name} — ${coll}` : it.name;
    return [{ appId: it.appId, label, kind: "file" }];
  });
  // UIRULE-DROPDOWN-SEARCH-SORT: numeric-aware order so "kr210-2" precedes "kr210-10".
  return naturalSort(opts, (o) => o.label);
}

/**
 * Search-as-you-type composable for the instance-wide accessible-URDF picker.
 * Debounces the query, calls GET /v2/references/urdf, and exposes the mapped +
 * naturally-sorted options. Fail-soft: a fetch error yields an empty list (the
 * dialog keeps its "advanced: paste appId" fallback).
 */
export function useFetchAccessibleUrdfOptions() {
  const query = ref("");
  const options = ref<ReferenceOption[]>([]);
  const isLoading = ref(false);

  // Accumulate loaded options across queries so an already-selected URDF never
  // vanishes when a later, narrower query returns a different page (the classic
  // remote-autocomplete "selection disappears" glitch). The set is bounded by
  // the instance's URDF count (sparse), so growth is a non-issue.
  const cache = new Map<string, ReferenceOption>();

  let debounce: ReturnType<typeof setTimeout> | null = null;
  let runId = 0;

  async function run(q: string): Promise<void> {
    const myRun = ++runId;
    isLoading.value = true;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken as string | undefined;
      const baseUrl = buildV2BaseUrl();
      const params = new URLSearchParams({ pageSize: "50" });
      if (q.trim().length > 0) params.set("q", q.trim());
      const response = await fetch(`${baseUrl}/v2/references/urdf?${params.toString()}`, {
        headers: {
          Authorization: `Bearer ${accessToken ?? ""}`,
          Accept: "application/json",
        },
      });
      if (myRun !== runId) return; // superseded by a newer query
      if (!response.ok) return; // keep whatever is already cached; fail-soft
      const body = (await response.json()) as { items?: AccessibleUrdfItem[] };
      if (myRun !== runId) return;
      for (const opt of mapAccessibleUrdfOptions(Array.isArray(body.items) ? body.items : [])) {
        cache.set(opt.appId, opt);
      }
      options.value = naturalSort([...cache.values()], (o) => o.label);
    } catch {
      // fail-soft: keep already-cached options
    } finally {
      if (myRun === runId) isLoading.value = false;
    }
  }

  /** Force a fetch now (used to populate on dialog open). */
  function refresh() {
    void run(query.value);
  }

  watch(query, (next) => {
    if (debounce) clearTimeout(debounce);
    debounce = setTimeout(() => void run(next), URDF_SEARCH_DEBOUNCE_MS);
  });

  onUnmounted(() => {
    if (debounce) clearTimeout(debounce);
  });

  return { query, options, isLoading, refresh };
}

/**
 * URDF-REF-PICKER — fetches kind=file references for a DataObject and filters
 * to those whose name ends with .urdf. Falls back gracefully to an empty list
 * when no dataObjectAppId is supplied (free-text appId entry still accepted by
 * the calling v-combobox).
 */
export function useFetchUrdfOptions(
  dataObjectAppIdInput: MaybeRefOrGetter<string | undefined>,
) {
  const options = ref<ReferenceOption[]>([]);
  const isLoading = ref(false);

  async function refresh() {
    const appId = toValue(dataObjectAppIdInput);
    if (!appId) {
      options.value = [];
      return;
    }
    isLoading.value = true;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken as string | undefined;
      const baseUrl = buildV2BaseUrl();
      const fileOptions = await fetchKindOptions(baseUrl, appId, "file", accessToken);
      options.value = fileOptions.filter((o) => isUrdfName(o.label.split(" (")[0]!));
    } catch {
      options.value = [];
    } finally {
      isLoading.value = false;
    }
  }

  watch(
    () => toValue(dataObjectAppIdInput),
    () => void refresh(),
    { immediate: true },
  );

  return { options, isLoading, refresh };
}

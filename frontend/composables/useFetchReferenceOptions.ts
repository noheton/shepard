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
 * Design: aidocs/platform/191 §4, backlog UI-GAP-1 slice 2 + URDF-REF-PICKER.
 */

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
    const refs = (await response.json()) as { appId?: string; name?: string }[];
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

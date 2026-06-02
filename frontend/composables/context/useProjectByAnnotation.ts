/**
 * PROJ-REST-2 — composable wrapping
 * GET /v2/projects/{appId}/by-annotation/{predicate}/{value}.
 *
 * Cross-reference: aidocs/integrations/121-project-and-subcollections.md §3.3.
 */

export interface MatchedAnnotation {
  predicate: string;
  value: string;
  source: "direct" | "inherited";
}

export interface ProjectByAnnotationItemIO {
  appId: string;
  id?: number;
  name: string;
  kind: string;
  collectionAppId?: string | null;
  collectionName?: string | null;
  matchedAnnotations?: MatchedAnnotation[];
}

export interface ProjectByAnnotationIO {
  projectAppId: string;
  predicate: string;
  value: string;
  totalCount: number;
  page: number;
  pageSize: number;
  results: ProjectByAnnotationItemIO[];
}

export interface ProjectByAnnotationQueryOpts {
  inherit?: boolean;
  include?: "identity" | "annotations";
  page?: number;
  pageSize?: number;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

/**
 * Cross-Collection by-annotation roll-up over the Project at `projectAppId`.
 * Reactive on the input refs — re-fetches whenever predicate / value / opts
 * change.
 */
export function useProjectByAnnotation(
  projectAppId: string,
  predicate: Ref<string> | string,
  value: Ref<string> | string,
  opts?: ProjectByAnnotationQueryOpts,
) {
  const result = ref<ProjectByAnnotationIO | null>(null);
  const isLoading = ref(false);
  const isMissing = ref(false);
  const error = ref<string | null>(null);

  const predicateRef = isRef(predicate) ? predicate : ref(predicate);
  const valueRef = isRef(value) ? value : ref(value);

  async function refresh() {
    const pred = predicateRef.value;
    const val = valueRef.value;
    if (!projectAppId || !pred || !val) {
      result.value = null;
      return;
    }
    isLoading.value = true;
    error.value = null;
    isMissing.value = false;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const qs = new URLSearchParams();
      if (opts?.inherit !== undefined) qs.set("inherit", String(opts.inherit));
      if (opts?.include) qs.set("include", opts.include);
      if (opts?.page !== undefined) qs.set("page", String(opts.page));
      if (opts?.pageSize !== undefined) qs.set("pageSize", String(opts.pageSize));

      const url =
        `${v2BaseUrl()}/v2/projects/${projectAppId}` +
        `/by-annotation/${encodeURIComponent(pred)}/${encodeURIComponent(val)}` +
        (qs.toString() ? `?${qs.toString()}` : "");

      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (response.status === 404) {
        result.value = null;
        isMissing.value = true;
        return;
      }
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      result.value = await response.json();
    } catch (e) {
      error.value = "Failed to load by-annotation roll-up";
      handleError(e, "fetching project by-annotation");
    } finally {
      isLoading.value = false;
    }
  }

  watch([predicateRef, valueRef], () => {
    void refresh();
  });

  refresh();

  return { result, isLoading, isMissing, error, refresh };
}

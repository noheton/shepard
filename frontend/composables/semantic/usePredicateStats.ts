/**
 * SEMA-V6-PRED-UI — fetch per-predicate usage statistics from
 * `GET /v2/semantic/predicates/{predicateIriBase64}/stats`.
 *
 * The predicate IRI must be URL-safe Base64 encoded (RFC 4648 §5) in the
 * path segment — URN colons and HTTP-IRI slashes both break naïve JAX-RS
 * path parsing. Server uses {@code Base64.getUrlDecoder()}; the encoder
 * here is the equivalent JS path.
 *
 * Backlog: SEMA-V6-PRED-UI.
 */
import { ref, watch, type Ref } from "vue";

export interface PredicateTopValue {
  objectIri?: string | null;
  objectLabel?: string | null;
  count: number;
}

export interface PredicateSampleEntity {
  appId: string;
  name?: string | null;
  type?: string | null;
}

export interface PredicateStats {
  predicate: string;
  annotationCount: number;
  topValues: PredicateTopValue[];
  sampleEntities: PredicateSampleEntity[];
}

/**
 * URL-safe Base64 encode an IRI (no padding). Pairs with the server's
 * `Base64.getUrlDecoder()`. Exported for testability.
 */
export function encodeIriForPath(iri: string): string {
  // btoa requires Latin-1; use TextEncoder for full UTF-8.
  const bytes = new TextEncoder().encode(iri);
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

function v2Base(): string {
  const { public: publicConfig } = useRuntimeConfig();
  const explicit = (publicConfig as { backendV2ApiUrl?: string }).backendV2ApiUrl;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (publicConfig.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

function authHeaders(): Record<string, string> {
  const { data: session } = useAuth();
  const token = session.value?.accessToken;
  return token
    ? { Authorization: `Bearer ${token}`, Accept: "application/json" }
    : { Accept: "application/json" };
}

export function usePredicateStats(
  predicateIri: Ref<string>,
  opts?: { topValuesLimit?: number; sampleLimit?: number },
) {
  const stats = ref<PredicateStats | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);

  async function load() {
    const iri = predicateIri.value;
    if (!iri || iri.trim().length === 0) {
      stats.value = null;
      loading.value = false;
      error.value = null;
      return;
    }
    loading.value = true;
    error.value = null;
    try {
      const params = new URLSearchParams();
      if (opts?.topValuesLimit !== undefined) {
        params.set("topValuesLimit", String(opts.topValuesLimit));
      }
      if (opts?.sampleLimit !== undefined) {
        params.set("sampleLimit", String(opts.sampleLimit));
      }
      const qs = params.toString();
      const url =
        `${v2Base()}/v2/semantic/predicates/${encodeIriForPath(iri)}/stats` +
        (qs.length > 0 ? `?${qs}` : "");
      const res = await fetch(url, { headers: authHeaders() });
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      stats.value = (await res.json()) as PredicateStats;
    } catch (e) {
      error.value = (e as Error).message ?? "Failed to load predicate stats";
      stats.value = null;
    } finally {
      loading.value = false;
    }
  }

  watch(predicateIri, load, { immediate: true });

  return { stats, loading, error, reload: load };
}

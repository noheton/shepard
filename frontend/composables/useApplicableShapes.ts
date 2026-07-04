/**
 * FORM-UX-ACTIONBUTTON — composable for the unified applicable-shapes
 * discovery endpoint ({@code GET /v2/shapes/applicable?focusAppId=…},
 * doc 125 §5.3 / D4).
 *
 * One discovery for both directions of the shapes UX: renderable views
 * (mode=VIEW → POST /v2/shapes/render) and fillable forms (mode=FORM →
 * GET /v2/templates/{appId}/form). Consumed by `ActionMenuButton.vue`
 * ("View as…" / "Record a…" on entity detail pages).
 *
 * WHY raw fetch instead of the typed client: the generated
 * `@dlr-shepard/backend-client` predates the `listApplicableShapes`
 * operation — there is no typed method for it yet. This is the ONE
 * documented raw call for the endpoint (per the frontend-v2-only rule's
 * regen note); replace with the typed v2 client method on the next
 * OpenAPI client regeneration. Base-URL + bearer-token shape mirrors
 * `PlaceholderRestDump.vue` (the established raw-v2 pattern).
 */

export interface ApplicableShapeItem {
  /** Discriminator: "VIEW" (renderable) | "FORM" (fillable). */
  mode: string;
  templateAppId: string;
  title: string;
  shapeIri?: string | null;
  renderHref?: string | null;
  formHref?: string | null;
  reason?: string | null;
}

export interface ApplicableShapesResponse {
  focusAppId: string;
  items: ApplicableShapeItem[];
}

/**
 * The discovery endpoint path for a focus appId. Centralised so the /v2/
 * prefix lives in exactly one place.
 */
export function applicableShapesPath(focusAppId: string): string {
  return `/v2/shapes/applicable?focusAppId=${encodeURIComponent(focusAppId)}`;
}

/**
 * Reactive applicable-shapes loader. Captures auth/config at setup time
 * (composable rules) and refetches whenever `focusAppId` changes. A fetch
 * failure degrades to an empty list (the button simply hides) — discovery
 * is a secondary affordance, never an error surface.
 */
export function useApplicableShapes(focusAppId: Ref<string | null | undefined>) {
  const { data: auth } = useAuth();
  const config = useRuntimeConfig().public;

  const items = ref<ApplicableShapeItem[]>([]);
  const isLoading = ref(false);

  function v2Base(): string {
    const explicit = config.backendV2ApiUrl as string | undefined;
    return explicit && explicit.length > 0
      ? explicit
      : (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
  }

  async function reload(): Promise<void> {
    const appId = focusAppId.value;
    if (!appId) {
      items.value = [];
      return;
    }
    isLoading.value = true;
    try {
      const headers: Record<string, string> = { Accept: "application/json" };
      if (auth.value?.accessToken) {
        headers["Authorization"] = `Bearer ${auth.value.accessToken}`;
      }
      const res = await fetch(v2Base() + applicableShapesPath(appId), { headers });
      if (!res.ok) {
        // 404 (stale focus) and friends degrade to "nothing applicable".
        items.value = [];
        return;
      }
      const body = (await res.json()) as ApplicableShapesResponse;
      items.value = Array.isArray(body.items) ? body.items : [];
    } catch {
      items.value = [];
    } finally {
      isLoading.value = false;
    }
  }

  watch(focusAppId, () => void reload(), { immediate: true });

  return { items, isLoading, reload };
}

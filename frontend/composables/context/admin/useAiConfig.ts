/**
 * APISIMP-AI-ADMIN-REST composable wrapping GET/PATCH /v2/admin/config/ai.
 *
 * Returns the full list of AiCapabilityConfigIO (one per AiCapability slot).
 * PATCH body is keyed by capability name — only the included slots are touched.
 *
 * Wire shape per slot (AiCapabilityConfigIO):
 *   { capability, endpointUrl, model, apiKey, apiKeySet, transport,
 *     guardrailsPrefix, guardrailsSuffix, maxTokens, temperature, enabled }
 *
 * GET: apiKey returns "***" if set, absent otherwise; apiKeySet signals presence.
 * PATCH semantics (RFC 7396 per slot):
 *   absent field → leave current value
 *   null         → clear field to deploy-time default
 *   value        → replace
 *   apiKey omitted in PATCH → existing key kept; included → replace
 */
import { unwrapList } from "~/utils/unwrapList";

export interface AiCapabilityConfigIO {
  capability: string;
  endpointUrl: string | null;
  model: string | null;
  /** Always absent or "***" on GET (masked). Include a plain value in PATCH to replace. */
  apiKey: string | null;
  apiKeySet: boolean;
  transport: string | null;
  guardrailsPrefix: string | null;
  guardrailsSuffix: string | null;
  maxTokens: number | null;
  temperature: number | null;
  enabled: boolean | null;
}

/** Fields sent in PATCH for a single capability slot. All optional. */
export interface AiCapabilitySlotPatch {
  enabled?: boolean | null;
  endpointUrl?: string | null;
  model?: string | null;
  /** Omit to keep existing key; include to replace. */
  apiKey?: string | null;
  transport?: string | null;
  guardrailsPrefix?: string | null;
  guardrailsSuffix?: string | null;
  maxTokens?: number | null;
  temperature?: number | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

const AI_CONFIG_URL = "/v2/admin/config/ai";

export function useAiConfig() {
  const slots = ref<AiCapabilityConfigIO[] | null>(null);
  const isLoading = ref(false);
  const isSaving = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const response = await fetch(`${v2BaseUrl()}${AI_CONFIG_URL}`, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      slots.value = unwrapList<AiCapabilityConfigIO>(await response.json());
    } catch (e) {
      error.value = "Failed to load AI config";
      handleError(e, "fetching AI config");
    } finally {
      isLoading.value = false;
    }
  }

  /**
   * Apply an RFC-7396 merge-patch to a single capability slot.
   * Returns the updated list of all slots on success, null on error.
   */
  async function patchSlot(
    capability: string,
    updates: AiCapabilitySlotPatch,
  ): Promise<AiCapabilityConfigIO[] | null> {
    isSaving.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const response = await fetch(`${v2BaseUrl()}${AI_CONFIG_URL}`, {
        method: "PATCH",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/merge-patch+json",
          Accept: "application/json",
        },
        body: JSON.stringify({ [capability]: updates }),
      });
      if (!response.ok) {
        const bodyText = await response.text().catch(() => "");
        let detail = `PATCH failed (HTTP ${response.status})`;
        try {
          const parsed = JSON.parse(bodyText);
          if (parsed && typeof parsed.detail === "string") detail = parsed.detail;
          else if (parsed && typeof parsed.title === "string") detail = parsed.title;
        } catch {
          // ignore parse errors — keep generic message
        }
        error.value = detail;
        return null;
      }
      const updated = unwrapList<AiCapabilityConfigIO>(await response.json());
      slots.value = updated;
      return updated;
    } catch (e) {
      error.value = "Failed to save AI config";
      handleError(e, "patching AI config");
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  refresh();

  return { slots, isLoading, isSaving, error, refresh, patchSlot };
}

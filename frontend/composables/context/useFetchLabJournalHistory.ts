import { unwrapList } from "~/utils/unwrapList";

/**
 * J1d — fetches the revision history of a single lab journal entry.
 *
 * Endpoint: GET /v2/lab-journal/{entryAppId}/history
 *
 * Each revision captures the entry content as it existed immediately before
 * the edit that produced it. Revisions are returned newest-first.
 *
 * Lazy: call load() explicitly (e.g. when the history dialog opens).
 * Not called on construction to avoid network traffic on every entry mount.
 */

export interface LabJournalRevisionIO {
  appId: string;
  revisionNumber: number;
  content: string;
  revisedAt: string;
  revisedBy: string;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useFetchLabJournalHistory(entryAppId: string) {
  const revisions = ref<LabJournalRevisionIO[]>([]);
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  async function load() {
    isLoading.value = true;
    error.value = null;

    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      error.value = "Not authenticated";
      isLoading.value = false;
      return;
    }

    const url = `${v2BaseUrl()}/v2/lab-journal/${encodeURIComponent(entryAppId)}/history`;

    try {
      const resp = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!resp.ok) {
        const body = await resp.text().catch(() => "");
        error.value = `Failed to load revision history (HTTP ${resp.status})`;
        handleError(
          error.value + (body ? `: ${body.slice(0, 120)}` : ""),
          "fetchLabJournalHistory",
        );
        return;
      }
      revisions.value = unwrapList<LabJournalRevisionIO>(await resp.json());
    } catch (err) {
      error.value = err instanceof Error ? err.message : "Network error";
      handleError(error.value, "fetchLabJournalHistory");
    } finally {
      isLoading.value = false;
    }
  }

  return { revisions, isLoading, error, load };
}

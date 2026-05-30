/**
 * KRL-INTERPRETER-06 — typed wrapper around POST /v2/krl/interpret.
 *
 * The KRL interpret endpoint is on the v2 surface (not under /shepard/api),
 * so it's not in `@dlr-shepard/backend-client` yet — same pattern as
 * `useFetchHdfReferences.ts` and `useFetchVideoStreamReferences.ts`.
 *
 * Surfaces user-friendly error messages for the documented status codes
 * (aidocs/integrations/117 §7.3):
 *  - 401 → "Sign in expired — refresh the page."
 *  - 403 → "You don't have write access on the target DataObject's Collection."
 *  - 422 → "KRL program contains unsupported constructs that prevented interpretation."
 *  - 501 → "The interpreter hit a HARD-STOP construct in the program."
 *  - 502 → operator hint to bring up the sidecar
 *  - 504 → "Interpretation timed out at the sidecar."
 */

export interface KrlInterpretWarning {
  line: number | null;
  message: string;
  severity: "INFO" | "WARN" | "ERROR";
}

export interface KrlInterpretUnsupported {
  construct: string;
  line: number | null;
  reason: string;
}

export interface KrlInterpretIkStats {
  meanCycleMs: number | null;
  p99CycleMs: number | null;
  maxResidualMeters: number | null;
  maxResidualRadians: number | null;
  failedPoses: number | null;
  totalPoses: number | null;
  solverName: string | null;
  solverVersion: string | null;
}

export interface KrlInterpretResponse {
  trajectoryAppId: string;
  activityAppId: string;
  warnings: KrlInterpretWarning[];
  unsupportedConstructs: KrlInterpretUnsupported[];
  ikSolverStats: KrlInterpretIkStats | null;
  interpreterVersion: string | null;
}

export interface KrlInterpretRequest {
  srcFileAppId: string;
  urdfFileAppId: string;
  targetDataObjectAppId: string;
  /**
   * Optional — when absent (undefined/null), the backend auto-mints a
   * ":TimeseriesContainer" named "KRL Trajectories" under the target
   * DataObject and uses it (KRL-INTERPRETER-05-FOLLOWUP-AUTO-CONTAINER).
   */
  timeseriesContainerAppId?: string | null;
  datFileAppIds?: string[];
  sceneAppId?: string | null;
  baseFrame?: Record<string, number>;
  toolFrame?: Record<string, number>;
  seedPose?: number[];
  timeStep?: number;
  options?: Record<string, unknown>;
}

export interface KrlInterpretError {
  status: number;
  message: string;
  detail: string;
}

/**
 * Map an HTTP status code to a user-friendly message.
 *
 * Exported so the result panel can render the same text the composable surfaces.
 */
export function krlErrorMessageForStatus(status: number, fallback?: string): string {
  switch (status) {
    case 401:
      return "Sign in expired — refresh the page.";
    case 403:
      return "You don't have write access on the target DataObject's Collection.";
    case 422:
      return "KRL program contains unsupported constructs that prevented interpretation.";
    case 501:
      return "The interpreter hit a HARD-STOP construct in the program.";
    case 502:
      return (
        "The KRL interpreter sidecar isn't reachable. Run " +
        "`COMPOSE_PROFILES=krl-interpreter docker compose up -d` to bring it up (operator action)."
      );
    case 504:
      return "Interpretation timed out at the sidecar. Try a smaller program or raise the timeout.";
    case 400:
      return fallback ?? "Malformed request — check the required fields.";
    default:
      return fallback ?? `Unexpected error (HTTP ${status}).`;
  }
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useKrlInterpret() {
  const loading = ref(false);
  const error = ref<KrlInterpretError | null>(null);
  const lastResponse = ref<KrlInterpretResponse | null>(null);

  async function run(
    body: KrlInterpretRequest,
    opts: { aiAgent?: string | null } = {},
  ): Promise<KrlInterpretResponse | null> {
    loading.value = true;
    error.value = null;
    lastResponse.value = null;

    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      error.value = {
        status: 401,
        message: krlErrorMessageForStatus(401),
        detail: "No access token available.",
      };
      loading.value = false;
      return null;
    }

    const headers: Record<string, string> = {
      Authorization: `Bearer ${accessToken}`,
      Accept: "application/json",
      "Content-Type": "application/json",
    };
    if (opts.aiAgent) headers["X-AI-Agent"] = opts.aiAgent;

    try {
      const url = `${v2BaseUrl()}/v2/krl/interpret`;
      const response = await fetch(url, {
        method: "POST",
        headers,
        body: JSON.stringify(body),
      });

      if (response.ok) {
        const json = (await response.json()) as KrlInterpretResponse;
        lastResponse.value = json;
        return json;
      }

      // Try to read the detail field from the error body
      let detail = "";
      try {
        const errBody = (await response.json()) as { detail?: string };
        detail = errBody.detail ?? "";
      } catch {
        try {
          detail = await response.text();
        } catch {
          detail = "";
        }
      }

      error.value = {
        status: response.status,
        message: krlErrorMessageForStatus(response.status, detail),
        detail: detail.slice(0, 500),
      };
      return null;
    } catch (e) {
      const detail = e instanceof Error ? e.message : "Network error";
      error.value = {
        status: 0,
        message:
          "Network error reaching the KRL interpret endpoint — check that the backend is reachable.",
        detail,
      };
      return null;
    } finally {
      loading.value = false;
    }
  }

  function reset() {
    error.value = null;
    lastResponse.value = null;
    loading.value = false;
  }

  return { run, loading, error, lastResponse, reset };
}

/**
 * ADM-USR-GIT composable — admin preseeds / replaces a git credential for
 * another user. Backend exposes only an idempotent create-or-replace per
 * host, NOT explicit issue/rotate/list endpoints:
 *
 *   POST /v2/admin/users/{username}/git-credentials
 *     body: { host, username, pat, displayName? }
 *     response: { appId, host, username }   // PAT is never returned
 *
 * What's missing (tracked in ADM-USR-GIT-BACKEND-1):
 *   - GET-for-other-users (so the pane can list "which hosts is the user
 *     already configured for, and when was each last rotated")
 *   - explicit /rotate endpoint (today: re-POST with same host = rotate)
 *   - lastRotatedAt field on the wire
 *
 * Until those land, the pane is honest about what it can and can't do:
 * it can SET / REPLACE a credential for (user × host) but cannot SHOW
 * what's already there.
 *
 * Backlog row: PLACEHOLDER-REPLACE-ADM-USR-GIT.
 */

export interface AdminGitCredentialBody {
  host: string;
  username: string;
  pat: string;
  displayName?: string;
}

export interface AdminGitCredentialResult {
  appId: string;
  host: string;
  username: string;
}

/**
 * ADM-USR-GIT-BACKEND-1 — list-row shape from
 * `GET /v2/admin/users/{username}/git-credentials`. PAT is never on the
 * wire (record component literally absent).
 */
export interface AdminGitCredentialListItem {
  appId: string;
  host: string;
  username: string;
  displayName: string | null;
  /** ISO-8601 instant; null for pre-feature credentials never rotated. */
  lastRotatedAt: string | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

function authHeader(): Record<string, string> {
  const { data: session } = useAuth();
  const token = session.value?.accessToken;
  if (!token) throw new Error("Not authenticated");
  return { Authorization: `Bearer ${token}` };
}

async function parseProblemDetail(response: Response): Promise<string> {
  const bodyText = await response.text().catch(() => "");
  try {
    const parsed = JSON.parse(bodyText);
    if (parsed && typeof parsed.detail === "string") return parsed.detail;
    if (parsed && typeof parsed.error === "string") return parsed.error;
    if (parsed && typeof parsed.title === "string") return parsed.title;
  } catch {
    // ignore
  }
  return `HTTP ${response.status}${bodyText ? ": " + bodyText.slice(0, 200) : ""}`;
}

export function useAdminUserGitCredential() {
  const isSaving = ref(false);
  const error = ref<string | null>(null);
  const lastResult = ref<AdminGitCredentialResult | null>(null);

  /**
   * Create or replace a git credential for the named user. Backend is
   * idempotent for the same (user × host): re-POST = rotate.
   */
  async function setCredential(
    targetUsername: string,
    body: AdminGitCredentialBody,
  ): Promise<AdminGitCredentialResult | null> {
    isSaving.value = true;
    error.value = null;
    try {
      const response = await fetch(
        `${v2BaseUrl()}/v2/admin/users/${encodeURIComponent(targetUsername)}/git-credentials`,
        {
          method: "POST",
          headers: {
            ...authHeader(),
            "Content-Type": "application/json",
            Accept: "application/json",
          },
          body: JSON.stringify(body),
        },
      );
      if (!response.ok) {
        error.value = await parseProblemDetail(response);
        return null;
      }
      const result = (await response.json()) as AdminGitCredentialResult;
      lastResult.value = result;
      return result;
    } catch (e) {
      error.value =
        e instanceof Error ? e.message : "Failed to save git credential";
      handleError(e, "setAdminUserGitCredential");
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  // ── ADM-USR-GIT-BACKEND-1-FE — list + rotate ─────────────────────────────
  const items = ref<AdminGitCredentialListItem[]>([]);
  const isLoading = ref(false);

  async function listCredentials(
    targetUsername: string,
  ): Promise<AdminGitCredentialListItem[]> {
    isLoading.value = true;
    error.value = null;
    try {
      const response = await fetch(
        `${v2BaseUrl()}/v2/admin/users/${encodeURIComponent(targetUsername)}/git-credentials`,
        { headers: { ...authHeader(), Accept: "application/json" } },
      );
      if (!response.ok) {
        error.value = await parseProblemDetail(response);
        items.value = [];
        return [];
      }
      const json = (await response.json()) as {
        items: AdminGitCredentialListItem[];
      };
      items.value = json.items ?? [];
      return items.value;
    } catch (e) {
      error.value =
        e instanceof Error ? e.message : "Failed to list git credentials";
      handleError(e, "listAdminUserGitCredentials");
      items.value = [];
      return [];
    } finally {
      isLoading.value = false;
    }
  }

  /**
   * Explicit rotate — backend stamps `lastRotatedAt = now`. Returns true
   * on 204, false otherwise.
   */
  async function rotateCredential(
    targetUsername: string,
    credAppId: string,
    newPat: string,
  ): Promise<boolean> {
    isSaving.value = true;
    error.value = null;
    try {
      const response = await fetch(
        `${v2BaseUrl()}/v2/admin/users/${encodeURIComponent(targetUsername)}/git-credentials/${encodeURIComponent(credAppId)}/rotate`,
        {
          method: "POST",
          headers: {
            ...authHeader(),
            "Content-Type": "application/json",
            Accept: "application/json",
          },
          body: JSON.stringify({ newPat }),
        },
      );
      if (!response.ok && response.status !== 204) {
        error.value = await parseProblemDetail(response);
        return false;
      }
      await listCredentials(targetUsername);
      return true;
    } catch (e) {
      error.value =
        e instanceof Error ? e.message : "Failed to rotate git credential";
      handleError(e, "rotateAdminUserGitCredential");
      return false;
    } finally {
      isSaving.value = false;
    }
  }

  return {
    isSaving,
    isLoading,
    error,
    lastResult,
    items,
    setCredential,
    listCredentials,
    rotateCredential,
  };
}

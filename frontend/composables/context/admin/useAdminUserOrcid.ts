/**
 * ADM-USR-ORCID composable — admin sets / clears another user's ORCID.
 *
 * Wraps:
 *   PATCH /v2/admin/users/{username}/orcid    body: { orcid: string | null }
 *
 * Backend resource: `de.dlr.shepard.v2.admin.users.AdminUserOrcidRest`.
 * Self-edit path (separate) is `PATCH /v2/users/me` (shipped in RDM-002).
 *
 * Backlog row: PLACEHOLDER-REPLACE-ADM-USR-ORCID.
 */

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

export function useAdminUserOrcid() {
  const isSaving = ref(false);
  const error = ref<string | null>(null);

  /**
   * Set or clear the ORCID for `username`. Pass `null` to clear.
   * Returns true on 2xx response, false otherwise (error.value populated).
   */
  async function patchOrcid(
    username: string,
    orcid: string | null,
  ): Promise<boolean> {
    isSaving.value = true;
    error.value = null;
    try {
      const response = await fetch(
        `${v2BaseUrl()}/v2/admin/users/${encodeURIComponent(username)}/orcid`,
        {
          method: "PATCH",
          headers: {
            ...authHeader(),
            "Content-Type": "application/json",
            Accept: "application/json",
          },
          body: JSON.stringify({ orcid }),
        },
      );
      if (!response.ok) {
        error.value = await parseProblemDetail(response);
        return false;
      }
      return true;
    } catch (e) {
      error.value =
        e instanceof Error ? e.message : "Failed to save ORCID";
      handleError(e, "patchAdminUserOrcid");
      return false;
    } finally {
      isSaving.value = false;
    }
  }

  return { isSaving, error, patchOrcid };
}

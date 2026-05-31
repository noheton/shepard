/**
 * ADM-MANAGE composable — list / grant / revoke instance-admin grants.
 *
 * Wraps the three v2 endpoints:
 *   GET    /v2/admin/instance-admins
 *   POST   /v2/admin/instance-admins           body: { username }
 *   DELETE /v2/admin/instance-admins/{username}
 *
 * Raw fetch (no generated client yet) — same pattern as
 * `useOntologyBundles` / `useSqlTimeseriesConfig`.
 *
 * Backend resource: `de.dlr.shepard.v2.admin.resources.InstanceAdminRest`.
 * Backlog row: PLACEHOLDER-REPLACE-ADM-MANAGE.
 */

export interface InstanceAdminGrantIO {
  username: string;
  /** "Neo4j" | "IdP" | "both" */
  source: string;
  grantedBy?: string | null;
  /** ISO-8601 instant */
  grantedAt?: string | null;
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
    if (parsed && typeof parsed.title === "string") return parsed.title;
    if (parsed && typeof parsed.message === "string") return parsed.message;
  } catch {
    // ignore
  }
  return `HTTP ${response.status}${bodyText ? ": " + bodyText.slice(0, 200) : ""}`;
}

export function useInstanceAdmins() {
  const grants = ref<InstanceAdminGrantIO[]>([]);
  const isLoading = ref(false);
  const isActing = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    error.value = null;
    try {
      const response = await fetch(`${v2BaseUrl()}/v2/admin/instance-admins`, {
        headers: { ...authHeader(), Accept: "application/json" },
      });
      if (!response.ok) {
        error.value = await parseProblemDetail(response);
        return;
      }
      grants.value = (await response.json()) as InstanceAdminGrantIO[];
    } catch (e) {
      error.value =
        e instanceof Error ? e.message : "Failed to load instance admins";
      handleError(e, "listInstanceAdmins");
    } finally {
      isLoading.value = false;
    }
  }

  async function grant(username: string): Promise<boolean> {
    isActing.value = true;
    error.value = null;
    try {
      const response = await fetch(`${v2BaseUrl()}/v2/admin/instance-admins`, {
        method: "POST",
        headers: {
          ...authHeader(),
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({ username }),
      });
      if (!response.ok) {
        error.value = await parseProblemDetail(response);
        return false;
      }
      await refresh();
      return true;
    } catch (e) {
      error.value =
        e instanceof Error ? e.message : "Failed to grant instance-admin";
      handleError(e, "grantInstanceAdmin");
      return false;
    } finally {
      isActing.value = false;
    }
  }

  async function revoke(username: string): Promise<boolean> {
    isActing.value = true;
    error.value = null;
    try {
      const response = await fetch(
        `${v2BaseUrl()}/v2/admin/instance-admins/${encodeURIComponent(username)}`,
        {
          method: "DELETE",
          headers: { ...authHeader(), Accept: "application/json" },
        },
      );
      if (!response.ok) {
        error.value = await parseProblemDetail(response);
        return false;
      }
      grants.value = grants.value.filter(g => g.username !== username);
      return true;
    } catch (e) {
      error.value =
        e instanceof Error ? e.message : "Failed to revoke instance-admin";
      handleError(e, "revokeInstanceAdmin");
      return false;
    } finally {
      isActing.value = false;
    }
  }

  return {
    grants,
    isLoading,
    isActing,
    error,
    refresh,
    grant,
    revoke,
  };
}

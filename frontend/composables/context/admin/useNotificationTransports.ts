/**
 * NTF1-UI-TRANSPORT-CRUD-FOLLOWUP — composable wrapping the
 * `:NotificationTransport` admin CRUD endpoints shipped 2026-05-31
 * (backend commit `3ca66827b`):
 *
 *   GET    /v2/admin/notifications/transports         — list (credentials omitted)
 *   POST   /v2/admin/notifications/transports         — create
 *   PATCH  /v2/admin/notifications/transports/{appId} — RFC 7396 merge-patch
 *   DELETE /v2/admin/notifications/transports/{appId} — remove
 *   POST   /v2/admin/notifications/test  body.transportId=…  — per-transport smoke-test
 *
 * The write-only credential contract is honored at the request-body
 * level by {@link buildPatchBody}: when a credential field on the form
 * is blank, it is **omitted from the JSON body** (not sent as `null`)
 * — because the backend's `@JsonSetter(nulls = Nulls.SET)` would
 * otherwise treat `null` as "clear the stored value".
 *
 * Raw `fetch` (no generated client) mirrors `useNotificationsAdmin`
 * and `useAdminUserGitCredential`.
 */

export type TransportKind = "SMTP" | "MATRIX" | "INAPP";

/** Read-side wire shape — never carries `smtpPassword` / `matrixAccessToken`. */
export interface NotificationTransport {
  appId: string;
  kind: TransportKind;
  name: string;
  enabled: boolean;
  lastTestResult?: string | null;
  lastTestedAt?: string | null;
  lastTestDetail?: string | null;
  // SMTP (credential field absent by backend contract)
  smtpHost?: string | null;
  smtpPort?: number | null;
  smtpUsername?: string | null;
  smtpFrom?: string | null;
  smtpTls?: boolean | null;
  // Matrix (credential field absent by backend contract)
  matrixHomeserver?: string | null;
  matrixDefaultRoom?: string | null;
}

/** Form-state shape — secret fields are blank-on-edit (write-only). */
export interface TransportFormState {
  kind: TransportKind;
  name: string;
  enabled: boolean;
  // SMTP
  smtpHost?: string;
  smtpPort?: number | null;
  smtpUsername?: string;
  smtpPassword?: string;
  smtpFrom?: string;
  smtpTls?: boolean;
  // Matrix
  matrixHomeserver?: string;
  matrixAccessToken?: string;
  matrixDefaultRoom?: string;
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

/**
 * Build the POST body for create. Omits secret fields when blank so the
 * backend doesn't store empty-string credentials, and omits fields not
 * relevant to the chosen kind. All non-secret fields are sent so the
 * Jackson `@JsonSetter` marks them touched.
 */
export function buildCreateBody(form: TransportFormState): Record<string, unknown> {
  const body: Record<string, unknown> = {
    kind: form.kind,
    name: form.name.trim(),
    enabled: form.enabled,
  };
  if (form.kind === "SMTP") {
    if (form.smtpHost) body.smtpHost = form.smtpHost.trim();
    if (form.smtpPort != null) body.smtpPort = form.smtpPort;
    if (form.smtpUsername) body.smtpUsername = form.smtpUsername.trim();
    if (form.smtpPassword && form.smtpPassword.length > 0) {
      body.smtpPassword = form.smtpPassword;
    }
    if (form.smtpFrom) body.smtpFrom = form.smtpFrom.trim();
    if (form.smtpTls != null) body.smtpTls = form.smtpTls;
  } else if (form.kind === "MATRIX") {
    if (form.matrixHomeserver) body.matrixHomeserver = form.matrixHomeserver.trim();
    if (form.matrixAccessToken && form.matrixAccessToken.length > 0) {
      body.matrixAccessToken = form.matrixAccessToken;
    }
    if (form.matrixDefaultRoom) body.matrixDefaultRoom = form.matrixDefaultRoom.trim();
  }
  return body;
}

/**
 * Build the PATCH body for edit. CRITICAL: credential fields are
 * literally OMITTED from the JSON when blank, not sent as `null` —
 * the backend's `@JsonSetter(nulls = Nulls.SET)` would otherwise mark
 * them touched and CLEAR the stored value.
 *
 * `kind` is intentionally never sent on patch (immutable post-create).
 */
export function buildPatchBody(form: TransportFormState): Record<string, unknown> {
  const body: Record<string, unknown> = {
    name: form.name.trim(),
    enabled: form.enabled,
  };
  if (form.kind === "SMTP") {
    body.smtpHost = form.smtpHost?.trim() || null;
    body.smtpPort = form.smtpPort ?? null;
    body.smtpUsername = form.smtpUsername?.trim() || null;
    // Password: OMIT when blank (preserve stored), SEND when set (rotate).
    if (form.smtpPassword && form.smtpPassword.length > 0) {
      body.smtpPassword = form.smtpPassword;
    }
    body.smtpFrom = form.smtpFrom?.trim() || null;
    body.smtpTls = form.smtpTls ?? null;
  } else if (form.kind === "MATRIX") {
    body.matrixHomeserver = form.matrixHomeserver?.trim() || null;
    // Access token: OMIT when blank (preserve stored), SEND when set (rotate).
    if (form.matrixAccessToken && form.matrixAccessToken.length > 0) {
      body.matrixAccessToken = form.matrixAccessToken;
    }
    body.matrixDefaultRoom = form.matrixDefaultRoom?.trim() || null;
  }
  return body;
}

export function useNotificationTransports() {
  const items = ref<NotificationTransport[]>([]);
  const isLoading = ref(false);
  const isSaving = ref(false);
  const isTesting = ref<string | null>(null);
  const error = ref<string | null>(null);

  const base = `${v2BaseUrl()}/v2/admin/notifications/transports`;

  async function list(): Promise<NotificationTransport[]> {
    isLoading.value = true;
    error.value = null;
    try {
      const response = await fetch(base, {
        headers: { ...authHeader(), Accept: "application/json" },
      });
      if (!response.ok) {
        error.value = await parseProblemDetail(response);
        return items.value;
      }
      const json = (await response.json()) as { items: NotificationTransport[] };
      items.value = json.items ?? [];
      return items.value;
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Failed to list transports";
      return items.value;
    } finally {
      isLoading.value = false;
    }
  }

  async function create(form: TransportFormState): Promise<NotificationTransport | null> {
    isSaving.value = true;
    error.value = null;
    try {
      const response = await fetch(base, {
        method: "POST",
        headers: {
          ...authHeader(),
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify(buildCreateBody(form)),
      });
      if (!response.ok) {
        error.value = await parseProblemDetail(response);
        return null;
      }
      const created = (await response.json()) as NotificationTransport;
      await list();
      return created;
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Failed to create transport";
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  async function patch(appId: string, form: TransportFormState): Promise<NotificationTransport | null> {
    isSaving.value = true;
    error.value = null;
    try {
      const response = await fetch(`${base}/${encodeURIComponent(appId)}`, {
        method: "PATCH",
        headers: {
          ...authHeader(),
          "Content-Type": "application/merge-patch+json",
          Accept: "application/json",
        },
        body: JSON.stringify(buildPatchBody(form)),
      });
      if (!response.ok) {
        error.value = await parseProblemDetail(response);
        return null;
      }
      const updated = (await response.json()) as NotificationTransport;
      await list();
      return updated;
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Failed to update transport";
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  async function remove(appId: string): Promise<boolean> {
    isSaving.value = true;
    error.value = null;
    try {
      const response = await fetch(`${base}/${encodeURIComponent(appId)}`, {
        method: "DELETE",
        headers: { ...authHeader() },
      });
      if (!response.ok && response.status !== 204) {
        error.value = await parseProblemDetail(response);
        return false;
      }
      await list();
      return true;
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Failed to delete transport";
      return false;
    } finally {
      isSaving.value = false;
    }
  }

  /**
   * Smoke-test the transport via `POST /v2/admin/notifications/test` with
   * `transportId` set. Backend returns a plain-text body on the transport
   * branch (not JSON), so this reads `.text()` instead of `.json()`.
   */
  async function testTransport(appId: string): Promise<{ ok: boolean; detail: string }> {
    isTesting.value = appId;
    error.value = null;
    try {
      const response = await fetch(`${v2BaseUrl()}/v2/admin/notifications/test`, {
        method: "POST",
        headers: {
          ...authHeader(),
          "Content-Type": "application/json",
          Accept: "application/json, text/plain",
        },
        body: JSON.stringify({
          transportId: appId,
          title: "Admin smoke-test",
          body: "Verifying transport delivery.",
          audience: "INSTANCE_ADMIN",
          category: "INFO",
        }),
      });
      const text = await response.text().catch(() => "");
      await list(); // refresh lastTestResult / lastTestedAt
      if (!response.ok) {
        const detail = text || `HTTP ${response.status}`;
        error.value = detail;
        return { ok: false, detail };
      }
      return { ok: true, detail: text || "delivered" };
    } catch (e) {
      const detail = e instanceof Error ? e.message : "Failed to test transport";
      error.value = detail;
      return { ok: false, detail };
    } finally {
      isTesting.value = null;
    }
  }

  return {
    items,
    isLoading,
    isSaving,
    isTesting,
    error,
    list,
    create,
    patch,
    remove,
    testTransport,
  };
}

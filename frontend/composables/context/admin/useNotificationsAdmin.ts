/**
 * NTF1 admin composable wrapping `POST /v2/admin/notifications/test` — the
 * one notification admin endpoint that exists today.
 *
 * Transport CRUD (SMTP, Matrix) is not yet implemented in the backend; see
 * NTF1-BACKEND-{TRANSPORT-MODEL,LIST,CRUD,TEST-PER-TRANSPORT,SMTP,MATRIX}
 * in aidocs/16-dispatcher-backlog.md. Once those land this composable grows
 * to wrap them; for now it only covers the working in-app test surface.
 *
 * Raw fetch (no generated client) — same pattern as useSqlTimeseriesConfig
 * and useUnhideAdminConfig.
 *
 * Wire shape mirrors backend TestNotificationIO:
 *   { audience, targetUsername?, category, title, body, actionUrl? }
 */

export type NotificationAudience = "USER" | "INSTANCE_ADMIN" | "ALL";
export type NotificationCategory = "INFO" | "WARNING" | "ACTION_REQUIRED";

export interface TestNotificationRequest {
  audience: NotificationAudience;
  targetUsername?: string | null;
  category: NotificationCategory;
  title: string;
  body: string;
  actionUrl?: string | null;
}

export interface TestNotificationResponse {
  appId?: string;
  title?: string;
  body?: string;
  audience?: string;
  category?: string;
  [key: string]: unknown;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

const TEST_NOTIFICATION_URL = "/v2/admin/notifications/test";

// ─── Pure helpers (extracted so the pane's form logic is testable) ───────────
//
// The pane composes these into Vue refs / computeds, but the rules themselves
// are pure functions so a Vitest unit test can exercise them without mounting
// a Vuetify component.

export function targetUsernameError(
  audience: NotificationAudience,
  raw: string,
): string | null {
  if (audience !== "USER") return null;
  if (raw.trim().length === 0)
    return "Required when audience is a specific user.";
  return null;
}

export function canSendTest(
  title: string,
  audience: NotificationAudience,
  targetUsernameRaw: string,
): boolean {
  if (title.trim().length === 0) return false;
  return targetUsernameError(audience, targetUsernameRaw) === null;
}

/**
 * Build the request payload from the pane's form state. Omits
 * `targetUsername` unless audience is USER, and omits `actionUrl` when blank
 * — matches the backend's nullable-field expectations.
 */
export function buildTestRequest(form: {
  audience: NotificationAudience;
  targetUsername: string;
  category: NotificationCategory;
  title: string;
  body: string;
  actionUrl: string;
}): TestNotificationRequest {
  const payload: TestNotificationRequest = {
    audience: form.audience,
    category: form.category,
    title: form.title.trim(),
    body: form.body.trim(),
  };
  if (form.audience === "USER") payload.targetUsername = form.targetUsername.trim();
  if (form.actionUrl.trim().length > 0) payload.actionUrl = form.actionUrl.trim();
  return payload;
}

export function useNotificationsAdmin() {
  const isSending = ref(false);
  const lastResult = ref<TestNotificationResponse | null>(null);
  const error = ref<string | null>(null);

  async function sendTest(
    body: TestNotificationRequest,
  ): Promise<TestNotificationResponse | null> {
    isSending.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const response = await fetch(`${v2BaseUrl()}${TEST_NOTIFICATION_URL}`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify(body),
      });
      if (!response.ok) {
        const bodyText = await response.text().catch(() => "");
        let detail = `Send failed (HTTP ${response.status})`;
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
      const result = (await response.json()) as TestNotificationResponse;
      lastResult.value = result;
      return result;
    } catch (e) {
      error.value = "Failed to send test notification";
      handleError(e, "sending test notification");
      return null;
    } finally {
      isSending.value = false;
    }
  }

  return { isSending, lastResult, error, sendTest };
}

/**
 * NTF1a — in-app notification polling composable.
 *
 * Polls GET /v2/notifications/count every 30 seconds to drive the bell badge.
 * Full notification list is fetched via load() when the panel opens.
 * Polling starts when startPolling() is called and stops via stopPolling().
 */

export interface NotificationIO {
  appId: string;
  audience: string;
  category: "INFO" | "WARNING" | "ACTION_REQUIRED";
  source: string;
  title: string;
  body: string;
  actionUrl: string | null;
  read: boolean;
  createdAtMillis: number;
  expiresAtMillis: number | null;
}

const POLL_INTERVAL_MS = 30_000;

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function getToken(): Promise<string | null> {
  const { data: session } = useAuth();
  return session.value?.accessToken ?? null;
}

export function useFetchNotifications() {
  const unreadCount = ref(0);
  const notifications = ref<NotificationIO[]>([]);
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  let pollTimer: ReturnType<typeof setInterval> | null = null;

  async function fetchCount() {
    const token = await getToken();
    if (!token) return;
    try {
      const res = await fetch(`${v2BaseUrl()}/v2/notifications/count`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        const data = await res.json() as { unread: number };
        unreadCount.value = data.unread;
      }
    } catch {
      // Silent — badge not updating is acceptable on network glitch
    }
  }

  async function load() {
    const token = await getToken();
    if (!token) {
      error.value = "Not authenticated";
      return;
    }
    isLoading.value = true;
    error.value = null;
    try {
      const res = await fetch(`${v2BaseUrl()}/v2/notifications`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) {
        error.value = `Error ${res.status}: ${await res.text()}`;
        return;
      }
      notifications.value = await res.json() as NotificationIO[];
      unreadCount.value = notifications.value.filter(n => !n.read).length;
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e);
    } finally {
      isLoading.value = false;
    }
  }

  async function markRead(appId: string) {
    const token = await getToken();
    if (!token) return;
    try {
      const res = await fetch(`${v2BaseUrl()}/v2/notifications/${appId}/read`, {
        method: "PATCH",
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        const idx = notifications.value.findIndex(n => n.appId === appId);
        if (idx >= 0) {
          notifications.value[idx] = { ...notifications.value[idx], read: true } as NotificationIO;
          unreadCount.value = notifications.value.filter(n => !n.read).length;
        }
      }
    } catch {
      // Non-fatal
    }
  }

  async function dismiss(appId: string) {
    const token = await getToken();
    if (!token) return;
    try {
      const res = await fetch(`${v2BaseUrl()}/v2/notifications/${appId}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        notifications.value = notifications.value.filter(n => n.appId !== appId);
        unreadCount.value = notifications.value.filter(n => !n.read).length;
      }
    } catch {
      // Non-fatal
    }
  }

  function startPolling() {
    void fetchCount();
    if (pollTimer !== null) return;
    pollTimer = setInterval(() => { void fetchCount(); }, POLL_INTERVAL_MS);
  }

  function stopPolling() {
    if (pollTimer !== null) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
  }

  return {
    unreadCount,
    notifications,
    isLoading,
    error,
    load,
    markRead,
    dismiss,
    startPolling,
    stopPolling,
  };
}

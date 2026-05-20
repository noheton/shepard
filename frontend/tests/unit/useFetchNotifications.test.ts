import { describe, it, expect, vi, beforeEach } from "vitest";
import type { NotificationIO } from "~/composables/context/useFetchNotifications";
import { useFetchNotifications } from "~/composables/context/useFetchNotifications";

const ACCESS_TOKEN = "test-token";

beforeEach(() => {
  vi.clearAllMocks();
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
  vi.stubGlobal("fetch", vi.fn());
});

function mockFetchOk(body: unknown) {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
    ok: true,
    json: () => Promise.resolve(body),
  }));
}

function mockFetchError(status: number, text = "error") {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
    ok: false,
    status,
    text: () => Promise.resolve(text),
  }));
}

function makeNotification(appId: string, read = false): NotificationIO {
  return {
    appId,
    audience: "USER",
    category: "INFO",
    source: "test",
    title: "Test",
    body: "Test body",
    actionUrl: null,
    read,
    createdAtMillis: Date.now(),
    expiresAtMillis: null,
  };
}

describe("useFetchNotifications", () => {
  it("starts with empty state", () => {
    const { unreadCount, notifications, isLoading, error } = useFetchNotifications();
    expect(notifications.value).toEqual([]);
    expect(unreadCount.value).toBe(0);
    expect(isLoading.value).toBe(false);
    expect(error.value).toBeNull();
  });

  it("load() fetches notifications and derives unread count", async () => {
    const data = [makeNotification("n1", false), makeNotification("n2", true)];
    mockFetchOk(data);

    const { notifications, unreadCount, isLoading, error, load } = useFetchNotifications();
    await load();

    expect(notifications.value).toEqual(data);
    expect(unreadCount.value).toBe(1);
    expect(isLoading.value).toBe(false);
    expect(error.value).toBeNull();

    const [url, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/v2/notifications");
    expect((opts.headers as Record<string, string>)["Authorization"]).toBe(`Bearer ${ACCESS_TOKEN}`);
  });

  it("load() sets error on HTTP failure", async () => {
    mockFetchError(403, "Forbidden");

    const { notifications, error, load } = useFetchNotifications();
    await load();

    expect(error.value).toMatch(/403/);
    expect(notifications.value).toEqual([]);
  });

  it("load() sets error on network failure", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("Network down")));

    const { error, load } = useFetchNotifications();
    await load();

    expect(error.value).toBe("Network down");
  });

  it("markRead() updates local state optimistically", async () => {
    const data = [makeNotification("n1", false), makeNotification("n2", false)];
    mockFetchOk(data);

    const { notifications, unreadCount, load, markRead } = useFetchNotifications();
    await load();
    expect(unreadCount.value).toBe(2);

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true }));
    await markRead("n1");

    expect(notifications.value.find(n => n.appId === "n1")?.read).toBe(true);
    expect(unreadCount.value).toBe(1);
  });

  it("dismiss() removes notification from local state", async () => {
    const data = [makeNotification("n1"), makeNotification("n2")];
    mockFetchOk(data);

    const { notifications, load, dismiss } = useFetchNotifications();
    await load();
    expect(notifications.value).toHaveLength(2);

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true }));
    await dismiss("n1");

    expect(notifications.value).toHaveLength(1);
    expect(notifications.value[0].appId).toBe("n2");
  });

  it("load() clears error on successful reload after failure", async () => {
    mockFetchError(500);
    const { error, load } = useFetchNotifications();
    await load();
    expect(error.value).not.toBeNull();

    mockFetchOk([]);
    await load();
    expect(error.value).toBeNull();
  });
});

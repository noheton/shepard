import { describe, it, expect, vi, beforeEach } from "vitest";
import type { LabJournalRevisionIO } from "~/composables/context/useFetchLabJournalHistory";
import { useFetchLabJournalHistory } from "~/composables/context/useFetchLabJournalHistory";

const ACCESS_TOKEN = "test-token-lj";

beforeEach(() => {
  vi.clearAllMocks();
  // Authenticated session by default.
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
  vi.stubGlobal("fetch", vi.fn());
});

const mockRevision = (n: number): LabJournalRevisionIO => ({
  appId: `rev-app-id-${n}`,
  revisionNumber: n,
  content: `<p>Content version ${n}</p>`,
  revisedAt: "2026-05-20T10:00:00Z",
  revisedBy: `editor${n}@test`,
});

function mockFetchOk(body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(body),
    }),
  );
}

function mockFetchError(status: number, text = "error") {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status,
      text: () => Promise.resolve(text),
    }),
  );
}

describe("useFetchLabJournalHistory — J1d", () => {
  it("starts with empty revisions and not loading", () => {
    mockFetchOk([]);
    const { revisions, isLoading, error } = useFetchLabJournalHistory("entry-app-id");
    expect(revisions.value).toEqual([]);
    expect(isLoading.value).toBe(false);
    expect(error.value).toBeNull();
  });

  it("does NOT call fetch until load() is invoked", () => {
    const spy = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve([]) });
    vi.stubGlobal("fetch", spy);
    useFetchLabJournalHistory("entry-app-id");
    expect(spy).not.toHaveBeenCalled();
  });

  it("sets isLoading to true while fetch is in progress", async () => {
    let resolve!: (v: unknown) => void;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockReturnValue(new Promise(r => { resolve = r; })),
    );

    const { isLoading, load } = useFetchLabJournalHistory("entry-app-id");
    const loadPromise = load();
    expect(isLoading.value).toBe(true);

    resolve({ ok: true, json: () => Promise.resolve([]) });
    await loadPromise;
    expect(isLoading.value).toBe(false);
  });

  it("populates revisions on success and sends correct URL + auth header", async () => {
    const data = [mockRevision(2), mockRevision(1)]; // newest-first
    mockFetchOk(data);

    const { revisions, isLoading, error, load } = useFetchLabJournalHistory(
      "my-entry-uuid",
    );
    await load();

    expect(revisions.value).toEqual(data);
    expect(isLoading.value).toBe(false);
    expect(error.value).toBeNull();

    const [url, opts] = (
      globalThis.fetch as ReturnType<typeof vi.fn>
    ).mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/v2/lab-journal/");
    expect(url).toContain("my-entry-uuid");
    expect(url).toContain("/history");
    expect((opts.headers as Record<string, string>)["Authorization"]).toBe(
      `Bearer ${ACCESS_TOKEN}`,
    );
  });

  it("URL-encodes entry appId with special characters", async () => {
    mockFetchOk([]);
    const { load } = useFetchLabJournalHistory("entry with spaces");
    await load();

    const [url] = (
      globalThis.fetch as ReturnType<typeof vi.fn>
    ).mock.calls[0] as [string, RequestInit];
    expect(url).toContain(encodeURIComponent("entry with spaces"));
  });

  it("sets error message on HTTP 403", async () => {
    mockFetchError(403, "Forbidden");
    const { revisions, error, load } = useFetchLabJournalHistory("entry-app-id");
    await load();

    expect(error.value).toMatch(/403/);
    expect(revisions.value).toEqual([]);
  });

  it("sets error on network failure", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new Error("Network down")),
    );
    const { error, load } = useFetchLabJournalHistory("entry-app-id");
    await load();

    expect(error.value).toBe("Network down");
  });

  it("resets error and revisions on successful reload after failure", async () => {
    mockFetchError(500);
    const { revisions, error, load } = useFetchLabJournalHistory("entry-app-id");
    await load();
    expect(error.value).not.toBeNull();

    const fresh = [mockRevision(1)];
    mockFetchOk(fresh);
    await load();

    expect(error.value).toBeNull();
    expect(revisions.value).toEqual(fresh);
  });

  it("sets error when not authenticated", async () => {
    (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
      data: ref<{ accessToken: string } | null>(null),
    });

    const { error, load } = useFetchLabJournalHistory("entry-app-id");
    await load();

    expect(error.value).toMatch(/authenticated/i);
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });
});

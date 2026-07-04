import { describe, it, expect, vi, beforeEach } from "vitest";
import { useAiConfig, type AiCapabilityConfigIO } from "~/composables/context/admin/useAiConfig";

const ACCESS_TOKEN = "test-admin-token";

const MOCK_SLOTS: AiCapabilityConfigIO[] = [
  {
    capability: "TEXT",
    endpointUrl: "https://api.openai.com/v1",
    model: "gpt-4o",
    apiKey: null,
    apiKeySet: true,
    transport: "openai",
    guardrailsPrefix: null,
    guardrailsSuffix: null,
    maxTokens: 4096,
    temperature: 0.7,
    enabled: true,
  },
  {
    capability: "EMBEDDING",
    endpointUrl: null,
    model: "text-embedding-3-small",
    apiKey: null,
    apiKeySet: false,
    transport: null,
    guardrailsPrefix: null,
    guardrailsSuffix: null,
    maxTokens: null,
    temperature: null,
    enabled: false,
  },
];

beforeEach(() => {
  vi.clearAllMocks();
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
  vi.stubGlobal("fetch", vi.fn());
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

function mockFetchError(status: number, bodyText = "error") {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status,
      text: () => Promise.resolve(bodyText),
    }),
  );
}

describe("useAiConfig — initial state", () => {
  it("starts with slots=null and isLoading=true", () => {
    vi.stubGlobal("fetch", vi.fn().mockReturnValue(new Promise(() => {})));
    const { slots, isLoading } = useAiConfig();
    expect(slots.value).toBeNull();
    expect(isLoading.value).toBe(true);
  });
});

describe("useAiConfig — refresh()", () => {
  it("GETs /v2/admin/config/ai and populates slots", async () => {
    mockFetchOk(MOCK_SLOTS);
    const { slots, isLoading, refresh } = useAiConfig();
    await refresh();
    expect(slots.value).toHaveLength(2);
    const list = slots.value as AiCapabilityConfigIO[];
    expect(list[0]?.capability).toBe("TEXT");
    expect(list[0]?.enabled).toBe(true);
    expect(list[1]?.capability).toBe("EMBEDDING");
    expect(isLoading.value).toBe(false);
  });

  it("sets error on fetch failure", async () => {
    mockFetchError(500, "Internal Server Error");
    const { slots, error, refresh } = useAiConfig();
    await refresh();
    expect(slots.value).toBeNull();
    expect(error.value).toBeTruthy();
  });

  it("sends Authorization header with access token", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(MOCK_SLOTS),
    });
    vi.stubGlobal("fetch", fetchMock);
    const { refresh } = useAiConfig();
    await refresh();
    const [, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect((options.headers as Record<string, string>).Authorization).toBe(
      `Bearer ${ACCESS_TOKEN}`,
    );
  });
});

describe("useAiConfig — patchSlot()", () => {
  it("PATCHes /v2/admin/config/ai with capability-keyed body", async () => {
    const updatedSlots = [{ ...MOCK_SLOTS[0], enabled: false }, MOCK_SLOTS[1]];
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(MOCK_SLOTS) }) // auto-refresh
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(updatedSlots) }); // PATCH
    vi.stubGlobal("fetch", fetchMock);

    const { patchSlot, slots } = useAiConfig();
    await patchSlot("TEXT", { enabled: false });

    // calls[1] is the PATCH (calls[0] is the auto-refresh GET)
    const [url, options] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(url).toContain("/v2/admin/config/ai");
    expect(options.method).toBe("PATCH");
    const body = JSON.parse(options.body as string);
    expect(body).toEqual({ TEXT: { enabled: false } });
    expect((slots.value as AiCapabilityConfigIO[])[0]?.enabled).toBe(false);
  });

  it("omits apiKey from PATCH body when not included in updates", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(MOCK_SLOTS) }) // auto-refresh
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(MOCK_SLOTS) }); // PATCH
    vi.stubGlobal("fetch", fetchMock);

    const { patchSlot } = useAiConfig();
    await patchSlot("TEXT", { enabled: true, model: "gpt-4o-mini" });

    // calls[1] is the PATCH
    const [, options] = fetchMock.mock.calls[1] as [string, RequestInit];
    const body = JSON.parse(options.body as string);
    expect(body.TEXT).not.toHaveProperty("apiKey");
  });

  it("returns null and sets error on HTTP error", async () => {
    mockFetchError(400, JSON.stringify({ detail: "Unknown capability" }));
    const { patchSlot, error } = useAiConfig();
    const result = await patchSlot("INVALID", { enabled: true });
    expect(result).toBeNull();
    expect(error.value).toContain("Unknown capability");
  });

  it("sends merge-patch+json Content-Type", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(MOCK_SLOTS) }) // auto-refresh
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(MOCK_SLOTS) }); // PATCH
    vi.stubGlobal("fetch", fetchMock);

    const { patchSlot } = useAiConfig();
    await patchSlot("TEXT", { enabled: true });

    // calls[1] is the PATCH
    const [, options] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect((options.headers as Record<string, string>)["Content-Type"]).toBe(
      "application/merge-patch+json",
    );
  });
});

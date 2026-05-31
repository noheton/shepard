/**
 * NTF1-UI-TRANSPORT-CRUD-FOLLOWUP — composable tests for
 * `useNotificationTransports`.
 *
 * Critical contract under test: `buildPatchBody` must OMIT credential
 * fields (smtpPassword, matrixAccessToken) when blank — sending them as
 * `null` would clear the stored value (backend uses
 * `@JsonSetter(nulls = Nulls.SET)`).
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import type { TransportFormState } from "~/composables/context/admin/useNotificationTransports";
import {
  buildCreateBody,
  buildPatchBody,
  useNotificationTransports,
} from "~/composables/context/admin/useNotificationTransports";

const ACCESS_TOKEN = "test-admin-token";

beforeEach(() => {
  vi.clearAllMocks();
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
});

function mockFetchOk(body: unknown, opts: { text?: string } = {}) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(body),
      text: () => Promise.resolve(opts.text ?? JSON.stringify(body)),
    }),
  );
}

function mockFetchError(status: number, bodyText = "") {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status,
      json: () => Promise.reject(new Error("not ok")),
      text: () => Promise.resolve(bodyText),
    }),
  );
}

function smtpForm(overrides: Partial<TransportFormState> = {}): TransportFormState {
  return {
    kind: "SMTP",
    name: "Primary",
    enabled: true,
    smtpHost: "smtp.example.org",
    smtpPort: 587,
    smtpUsername: "shepard",
    smtpPassword: "secret",
    smtpFrom: "noreply@example.org",
    smtpTls: true,
    ...overrides,
  };
}

function matrixForm(overrides: Partial<TransportFormState> = {}): TransportFormState {
  return {
    kind: "MATRIX",
    name: "Engineering",
    enabled: true,
    matrixHomeserver: "https://matrix.example.org",
    matrixAccessToken: "syt_token",
    matrixDefaultRoom: "!room:example.org",
    ...overrides,
  };
}

describe("buildCreateBody — SMTP", () => {
  it("emits the full SMTP body with credentials when set", () => {
    const body = buildCreateBody(smtpForm());
    expect(body).toEqual({
      kind: "SMTP",
      name: "Primary",
      enabled: true,
      smtpHost: "smtp.example.org",
      smtpPort: 587,
      smtpUsername: "shepard",
      smtpPassword: "secret",
      smtpFrom: "noreply@example.org",
      smtpTls: true,
    });
  });

  it("omits password field when blank (no empty-string credentials in DB)", () => {
    const body = buildCreateBody(smtpForm({ smtpPassword: "" }));
    expect("smtpPassword" in body).toBe(false);
  });

  it("trims name and host", () => {
    const body = buildCreateBody(smtpForm({ name: "  Primary  ", smtpHost: "  smtp.x  " }));
    expect(body.name).toBe("Primary");
    expect(body.smtpHost).toBe("smtp.x");
  });
});

describe("buildCreateBody — MATRIX", () => {
  it("emits the full Matrix body with token when set", () => {
    const body = buildCreateBody(matrixForm());
    expect(body).toEqual({
      kind: "MATRIX",
      name: "Engineering",
      enabled: true,
      matrixHomeserver: "https://matrix.example.org",
      matrixAccessToken: "syt_token",
      matrixDefaultRoom: "!room:example.org",
    });
  });

  it("omits access token when blank", () => {
    const body = buildCreateBody(matrixForm({ matrixAccessToken: "" }));
    expect("matrixAccessToken" in body).toBe(false);
  });
});

describe("buildPatchBody — credential omission contract", () => {
  it("OMITS smtpPassword when blank (preserves stored value)", () => {
    const body = buildPatchBody(smtpForm({ smtpPassword: "" }));
    expect("smtpPassword" in body).toBe(false);
    // Other fields still present as merge-patch values:
    expect(body.smtpHost).toBe("smtp.example.org");
  });

  it("SENDS smtpPassword when non-blank (rotate)", () => {
    const body = buildPatchBody(smtpForm({ smtpPassword: "new-secret" }));
    expect(body.smtpPassword).toBe("new-secret");
  });

  it("OMITS matrixAccessToken when blank (preserves stored token)", () => {
    const body = buildPatchBody(matrixForm({ matrixAccessToken: "" }));
    expect("matrixAccessToken" in body).toBe(false);
  });

  it("SENDS matrixAccessToken when non-blank (rotate)", () => {
    const body = buildPatchBody(matrixForm({ matrixAccessToken: "rotated" }));
    expect(body.matrixAccessToken).toBe("rotated");
  });

  it("never includes kind (immutable post-create)", () => {
    expect("kind" in buildPatchBody(smtpForm())).toBe(false);
    expect("kind" in buildPatchBody(matrixForm())).toBe(false);
  });

  it("emits non-secret fields as null when blank (clears via merge-patch)", () => {
    const body = buildPatchBody(smtpForm({ smtpFrom: "" }));
    expect(body.smtpFrom).toBeNull();
  });
});

describe("useNotificationTransports — list / CRUD round-trips", () => {
  it("GET /v2/admin/notifications/transports populates items", async () => {
    mockFetchOk({
      items: [
        { appId: "a1", kind: "SMTP", name: "Primary", enabled: true },
        { appId: "a2", kind: "MATRIX", name: "Eng", enabled: false },
      ],
    });
    const { list, items } = useNotificationTransports();
    await list();
    expect(items.value.length).toBe(2);
    expect(items.value[0].appId).toBe("a1");
    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.at(
      -1,
    ) as [string, RequestInit];
    expect(url).toContain("/v2/admin/notifications/transports");
  });

  it("POST send-body matches buildCreateBody output", async () => {
    mockFetchOk({ items: [] });
    const fetchSpy = globalThis.fetch as ReturnType<typeof vi.fn>;
    const { create } = useNotificationTransports();
    await create(smtpForm({ smtpPassword: "" }));
    // First call: POST; second call: list refresh — assert on POST
    const postCall = fetchSpy.mock.calls.find(
      ([, init]) => (init as RequestInit)?.method === "POST",
    );
    expect(postCall).toBeDefined();
    const body = JSON.parse((postCall![1] as RequestInit).body as string);
    expect("smtpPassword" in body).toBe(false);
    expect(body.smtpHost).toBe("smtp.example.org");
  });

  it("PATCH uses merge-patch content-type and omits kind", async () => {
    mockFetchOk({ items: [] });
    const fetchSpy = globalThis.fetch as ReturnType<typeof vi.fn>;
    const { patch } = useNotificationTransports();
    await patch("appid-1", smtpForm({ smtpPassword: "" }));
    const patchCall = fetchSpy.mock.calls.find(
      ([, init]) => (init as RequestInit)?.method === "PATCH",
    );
    expect(patchCall).toBeDefined();
    const headers = (patchCall![1] as RequestInit).headers as Record<string, string>;
    expect(headers["Content-Type"]).toContain("merge-patch+json");
    const body = JSON.parse((patchCall![1] as RequestInit).body as string);
    expect("kind" in body).toBe(false);
  });

  it("DELETE returns true on 204", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        // DELETE response
        .mockResolvedValueOnce({
          ok: true,
          status: 204,
          text: () => Promise.resolve(""),
        })
        // list refresh
        .mockResolvedValueOnce({
          ok: true,
          status: 200,
          json: () => Promise.resolve({ items: [] }),
          text: () => Promise.resolve("{}"),
        }),
    );
    const { remove } = useNotificationTransports();
    const ok = await remove("appid-1");
    expect(ok).toBe(true);
  });

  it("testTransport reads text body (per-transport branch returns plain text)", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        // POST /test response (plain text)
        .mockResolvedValueOnce({
          ok: true,
          status: 200,
          text: () => Promise.resolve("delivered via SMTP"),
        })
        // list refresh
        .mockResolvedValueOnce({
          ok: true,
          status: 200,
          json: () => Promise.resolve({ items: [] }),
          text: () => Promise.resolve("{}"),
        }),
    );
    const { testTransport } = useNotificationTransports();
    const result = await testTransport("appid-1");
    expect(result.ok).toBe(true);
    expect(result.detail).toContain("delivered");
  });

  it("testTransport surfaces 502 plain-text error body", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce({
          ok: false,
          status: 502,
          text: () => Promise.resolve("transport send returned false"),
        })
        .mockResolvedValueOnce({
          ok: true,
          status: 200,
          json: () => Promise.resolve({ items: [] }),
          text: () => Promise.resolve("{}"),
        }),
    );
    const { testTransport, error } = useNotificationTransports();
    const result = await testTransport("appid-1");
    expect(result.ok).toBe(false);
    expect(result.detail).toContain("transport send returned false");
    expect(error.value).toContain("transport send returned false");
  });

  it("list() surfaces error message from a 403", async () => {
    mockFetchError(403, JSON.stringify({ title: "Forbidden" }));
    const { list, error } = useNotificationTransports();
    await list();
    expect(error.value).toBe("Forbidden");
  });
});

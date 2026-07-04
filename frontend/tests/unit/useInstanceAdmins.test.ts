/**
 * Tests for ADM-MANAGE composable.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  useInstanceAdmins,
  type InstanceAdminGrantIO,
} from "~/composables/context/admin/useInstanceAdmins";

const ACCESS_TOKEN = "test-admin-token";

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
      text: () => Promise.resolve(""),
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

const sampleGrants: InstanceAdminGrantIO[] = [
  { username: "alice", source: "Neo4j", grantedBy: "bootstrap", grantedAt: "2026-05-31T10:00:00Z" },
  { username: "bob", source: "IdP", grantedBy: null, grantedAt: null },
];

/** Build a PagedResponseIO-shaped mock for the GET /v2/admin/instance-admins response. */
function pagedGrants(items: InstanceAdminGrantIO[]) {
  return { items, total: items.length, page: 0, pageSize: items.length };
}

describe("useInstanceAdmins — refresh()", () => {
  it("populates grants on successful GET", async () => {
    mockFetchOk(pagedGrants(sampleGrants));
    const { grants, error, refresh } = useInstanceAdmins();
    await refresh();
    expect(grants.value).toEqual(sampleGrants);
    expect(error.value).toBeNull();
  });

  it("sets error on HTTP failure", async () => {
    mockFetchError(403, JSON.stringify({ detail: "forbidden" }));
    const { grants, error, refresh } = useInstanceAdmins();
    await refresh();
    expect(grants.value).toEqual([]);
    expect(error.value).toBe("forbidden");
  });

  it("uses the /v2/admin/instance-admins URL with Bearer token", async () => {
    mockFetchOk(pagedGrants(sampleGrants));
    const { refresh } = useInstanceAdmins();
    await refresh();
    const [url, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(url).toContain("/v2/admin/instance-admins");
    expect((opts.headers as Record<string, string>)["Authorization"]).toBe(
      `Bearer ${ACCESS_TOKEN}`,
    );
  });
});

describe("useInstanceAdmins — grant()", () => {
  it("POSTs username and reloads on success", async () => {
    // Sequence: POST returns the new grant, then GET reloads.
    const newGrant: InstanceAdminGrantIO = {
      username: "carol",
      source: "Neo4j",
      grantedBy: "alice",
      grantedAt: "2026-05-31T11:00:00Z",
    };
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(newGrant), text: () => Promise.resolve("") })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(pagedGrants([...sampleGrants, newGrant])), text: () => Promise.resolve("") });
    vi.stubGlobal("fetch", fetchMock);

    const { grant, grants } = useInstanceAdmins();
    const ok = await grant("carol");
    expect(ok).toBe(true);

    const postCall = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(postCall[0]).toContain("/v2/admin/instance-admins");
    expect(postCall[1].method).toBe("POST");
    expect(JSON.parse(postCall[1].body as string)).toEqual({ username: "carol" });
    expect(grants.value).toContainEqual(newGrant);
  });

  it("returns false and sets error on 404 user-not-found", async () => {
    mockFetchError(404, JSON.stringify({ detail: "no such user" }));
    const { grant, error } = useInstanceAdmins();
    const ok = await grant("ghost");
    expect(ok).toBe(false);
    expect(error.value).toBe("no such user");
  });
});

describe("useInstanceAdmins — revoke()", () => {
  it("DELETEs by username and removes from local list", async () => {
    // Seed list first via refresh, then revoke.
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(pagedGrants(sampleGrants)), text: () => Promise.resolve("") })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({}), text: () => Promise.resolve("") });
    vi.stubGlobal("fetch", fetchMock);

    const { refresh, revoke, grants } = useInstanceAdmins();
    await refresh();
    expect(grants.value).toHaveLength(2);
    const ok = await revoke("alice");
    expect(ok).toBe(true);
    expect(grants.value).toEqual([sampleGrants[1]]);

    const delCall = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(delCall[0]).toContain("/v2/admin/instance-admins/alice");
    expect(delCall[1].method).toBe("DELETE");
  });

  it("URL-encodes the username", async () => {
    mockFetchOk({});
    const { revoke } = useInstanceAdmins();
    await revoke("user with spaces");
    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(url).toContain("user%20with%20spaces");
  });

  it("returns false on 404", async () => {
    mockFetchError(404, JSON.stringify({ detail: "no grant" }));
    const { revoke, error } = useInstanceAdmins();
    const ok = await revoke("alice");
    expect(ok).toBe(false);
    expect(error.value).toBe("no grant");
  });
});

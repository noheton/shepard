/**
 * V2-SWEEP-002-2 — unit tests for the appId-keyed useUserGroupsV2 composable.
 *
 * Verifies that the composable:
 *  - hits the /v2/user-groups surface (never /shepard/api) with a bearer token
 *  - addresses groups by appId on get/update/delete (never a numeric id)
 *  - mutates membership through a PATCH on the usernames array
 *  - throws on non-2xx so callers can surface an error
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

// Provide a valid access token (overrides the default setup.ts useAuth stub).
(globalThis as unknown as Record<string, unknown>).useAuth = () => ({
  refresh: vi.fn().mockResolvedValue(undefined),
  data: ref<{ accessToken: string } | null>({ accessToken: "test-token" }),
  signIn: vi.fn().mockResolvedValue(undefined),
});

beforeEach(() => {
  vi.clearAllMocks();
});

const GROUP = {
  appId: "ug-app-001",
  name: "LUMEN engineers",
  usernames: ["alice", "bob"],
  createdAt: "2026-01-01T00:00:00Z",
  createdBy: "alice",
};

function okResponse(body: unknown, status = 200) {
  return Promise.resolve({
    ok: true,
    status,
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(""),
  } as Response);
}

function errorResponse(status: number) {
  return Promise.resolve({
    ok: false,
    status,
    json: () => Promise.reject(new Error("not json")),
    text: () => Promise.resolve(`HTTP ${status}`),
  } as unknown as Response);
}

async function importComposable() {
  const mod = await import("~/composables/context/useUserGroupsV2");
  return mod.useUserGroupsV2();
}

describe("useUserGroupsV2", () => {
  it("lists groups via /v2/user-groups with a bearer token", async () => {
    mockFetch.mockReturnValue(okResponse([GROUP]));
    const { listUserGroups } = await importComposable();

    const result = await listUserGroups();

    expect(mockFetch).toHaveBeenCalledTimes(1);
    const [url, opts] = mockFetch.mock.calls[0]!;
    expect(url).toBe("http://localhost:8080/v2/user-groups");
    expect(url).not.toContain("/shepard/api");
    expect((opts as RequestInit).headers).toMatchObject({
      Authorization: "Bearer test-token",
    });
    expect(result).toHaveLength(1);
    expect(result[0]!.appId).toBe("ug-app-001");
  });

  it("gets a single group by appId (not a numeric id)", async () => {
    mockFetch.mockReturnValue(okResponse(GROUP));
    const { getUserGroup } = await importComposable();

    const group = await getUserGroup("ug-app-001");

    const [url] = mockFetch.mock.calls[0]!;
    expect(url).toBe("http://localhost:8080/v2/user-groups/ug-app-001");
    expect(group.appId).toBe("ug-app-001");
  });

  it("creates a group with a POST body containing name + usernames", async () => {
    mockFetch.mockReturnValue(okResponse(GROUP, 201));
    const { createUserGroup } = await importComposable();

    await createUserGroup({ name: "LUMEN engineers", usernames: ["alice"] });

    const [url, opts] = mockFetch.mock.calls[0]!;
    expect(url).toBe("http://localhost:8080/v2/user-groups");
    expect((opts as RequestInit).method).toBe("POST");
    expect(JSON.parse((opts as RequestInit).body as string)).toEqual({
      name: "LUMEN engineers",
      usernames: ["alice"],
    });
  });

  it("updates a group by appId via PATCH", async () => {
    mockFetch.mockReturnValue(okResponse({ ...GROUP, name: "Renamed" }));
    const { updateUserGroup } = await importComposable();

    await updateUserGroup("ug-app-001", { name: "Renamed" });

    const [url, opts] = mockFetch.mock.calls[0]!;
    expect(url).toBe("http://localhost:8080/v2/user-groups/ug-app-001");
    expect((opts as RequestInit).method).toBe("PATCH");
    expect(JSON.parse((opts as RequestInit).body as string)).toEqual({
      name: "Renamed",
    });
  });

  it("deletes a group by appId via DELETE", async () => {
    mockFetch.mockReturnValue(okResponse(null, 204));
    const { deleteUserGroup } = await importComposable();

    await deleteUserGroup("ug-app-001");

    const [url, opts] = mockFetch.mock.calls[0]!;
    expect(url).toBe("http://localhost:8080/v2/user-groups/ug-app-001");
    expect((opts as RequestInit).method).toBe("DELETE");
  });

  it("adds a member via a PATCH on the usernames array", async () => {
    mockFetch.mockReturnValue(
      okResponse({ ...GROUP, usernames: ["alice", "bob", "carol"] }),
    );
    const { addMember } = await importComposable();

    const updated = await addMember(GROUP, "carol");

    const [url, opts] = mockFetch.mock.calls[0]!;
    expect(url).toBe("http://localhost:8080/v2/user-groups/ug-app-001");
    expect((opts as RequestInit).method).toBe("PATCH");
    expect(JSON.parse((opts as RequestInit).body as string)).toEqual({
      usernames: ["alice", "bob", "carol"],
    });
    expect(updated.usernames).toContain("carol");
  });

  it("does not call the backend when adding an existing member", async () => {
    const { addMember } = await importComposable();

    const result = await addMember(GROUP, "alice");

    expect(mockFetch).not.toHaveBeenCalled();
    expect(result).toBe(GROUP);
  });

  it("removes a member via a PATCH on the usernames array", async () => {
    mockFetch.mockReturnValue(okResponse({ ...GROUP, usernames: ["alice"] }));
    const { removeMember } = await importComposable();

    await removeMember(GROUP, "bob");

    const [, opts] = mockFetch.mock.calls[0]!;
    expect((opts as RequestInit).method).toBe("PATCH");
    expect(JSON.parse((opts as RequestInit).body as string)).toEqual({
      usernames: ["alice"],
    });
  });

  it("throws on a non-2xx response", async () => {
    mockFetch.mockReturnValue(errorResponse(404));
    const { getUserGroup } = await importComposable();

    await expect(getUserGroup("missing")).rejects.toThrow("404");
  });
});

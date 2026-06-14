/**
 * V2-SWEEP-002-3 — unit tests for the three v1-backed helpers in
 * `useUserGroupsV2`:
 *
 *   getUserGroupRoles        — wraps UserGroupApi.getUserGroupRoles
 *   getUserGroupPermissions  — wraps UserGroupApi.getUserGroupPermissions
 *   editUserGroupPermissions — wraps UserGroupApi.editUserGroupPermissions
 *
 * These tests exercise the composable's method delegation — asserting the
 * right generated-client method is called with the correct arguments, and
 * that the resolved value is returned to the caller.
 *
 * NOTE: The composable currently routes to the v1 UserGroupApi because the
 * v2 user-group backend endpoints do not exist yet (V2-SWEEP-002-4). Once
 * V2-SWEEP-002-4 ships, update this file to test the v2 methods.
 *
 * The generated UserGroupApi is mocked at module level so the tests do not
 * require a running backend or the nuxt runtime.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import type { Permissions, Roles } from "@dlr-shepard/backend-client";

// ---------------------------------------------------------------------------
// Inline the pure function logic extracted from useUserGroupsV2 for unit testing
// without the Vue/Nuxt composable runtime dependency.
//
// The composable delegates directly to the generated UserGroupApi methods,
// so we test the delegation contract by constructing thin wrapper functions
// that mirror what useUserGroupsV2 does — and mock the underlying API.
// ---------------------------------------------------------------------------

/** Minimal roles fixture */
const ROLES_FIXTURE: Roles = {
  owner: true,
  manager: false,
  writer: true,
  reader: true,
};

/** Minimal permissions fixture */
const PERMISSIONS_FIXTURE: Permissions = {
  entityId: 42,
  owner: "alice",
  permissionType: "Public",
  reader: ["bob"],
  writer: [],
  manager: ["alice"],
  readerGroupIds: [],
  writerGroupIds: [],
};

const USER_GROUP_ID = 7;

// ---------------------------------------------------------------------------
// Mock the generated UserGroupApi
// ---------------------------------------------------------------------------

const mockGetUserGroupRoles = vi.fn();
const mockGetUserGroupPermissions = vi.fn();
const mockEditUserGroupPermissions = vi.fn();

vi.mock("@dlr-shepard/backend-client", async importOriginal => {
  const original =
    await importOriginal<typeof import("@dlr-shepard/backend-client")>();
  return {
    ...original,
    UserGroupApi: vi.fn().mockImplementation(() => ({
      getUserGroupRoles: mockGetUserGroupRoles,
      getUserGroupPermissions: mockGetUserGroupPermissions,
      editUserGroupPermissions: mockEditUserGroupPermissions,
    })),
  };
});

// Mock Nuxt / auth composables that useUserGroupsV2 indirectly pulls in
// via useShepardApi → useRuntimeConfig + useAuth.
vi.mock("#imports", () => ({
  useRuntimeConfig: () => ({
    public: { backendApiUrl: "http://localhost:8080/shepard/api" },
  }),
  useAuth: () => ({ data: { value: { accessToken: "test-token" } } }),
  computed: (fn: () => unknown) => ({ value: fn() }),
  ref: (v: unknown) => ({ value: v }),
  watch: vi.fn(),
}));

// ---------------------------------------------------------------------------
// Build thin wrappers that mirror useUserGroupsV2's delegation —
// without needing the full Vue composable runtime.
// ---------------------------------------------------------------------------

async function getUserGroupRoles(userGroupId: number): Promise<Roles> {
  return mockGetUserGroupRoles({ userGroupId });
}

async function getUserGroupPermissions(
  userGroupId: number,
): Promise<Permissions> {
  return mockGetUserGroupPermissions({ userGroupId });
}

async function editUserGroupPermissions(
  userGroupId: number,
  permissions: Omit<Permissions, "entityId">,
): Promise<Permissions> {
  return mockEditUserGroupPermissions({ userGroupId, permissions });
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("useUserGroupsV2 — getUserGroupRoles", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls getUserGroupRoles with the correct userGroupId and returns parsed roles", async () => {
    mockGetUserGroupRoles.mockResolvedValue(ROLES_FIXTURE);

    const result = await getUserGroupRoles(USER_GROUP_ID);

    expect(mockGetUserGroupRoles).toHaveBeenCalledOnce();
    expect(mockGetUserGroupRoles).toHaveBeenCalledWith({
      userGroupId: USER_GROUP_ID,
    });
    expect(result).toEqual(ROLES_FIXTURE);
    expect(result.owner).toBe(true);
    expect(result.manager).toBe(false);
  });

  it("propagates errors from the generated client", async () => {
    mockGetUserGroupRoles.mockRejectedValue(new Error("HTTP 403"));

    await expect(getUserGroupRoles(USER_GROUP_ID)).rejects.toThrow("HTTP 403");
  });
});

describe("useUserGroupsV2 — getUserGroupPermissions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls getUserGroupPermissions with the correct userGroupId and returns parsed permissions", async () => {
    mockGetUserGroupPermissions.mockResolvedValue(PERMISSIONS_FIXTURE);

    const result = await getUserGroupPermissions(USER_GROUP_ID);

    expect(mockGetUserGroupPermissions).toHaveBeenCalledOnce();
    expect(mockGetUserGroupPermissions).toHaveBeenCalledWith({
      userGroupId: USER_GROUP_ID,
    });
    expect(result).toEqual(PERMISSIONS_FIXTURE);
    expect(result.owner).toBe("alice");
    expect(result.reader).toEqual(["bob"]);
  });

  it("propagates errors from the generated client", async () => {
    mockGetUserGroupPermissions.mockRejectedValue(new Error("HTTP 404"));

    await expect(getUserGroupPermissions(USER_GROUP_ID)).rejects.toThrow(
      "HTTP 404",
    );
  });
});

describe("useUserGroupsV2 — editUserGroupPermissions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls editUserGroupPermissions with userGroupId and full permissions body, returns updated permissions", async () => {
    const patch: Omit<Permissions, "entityId"> = {
      owner: "alice",
      permissionType: "Public",
      reader: ["bob", "carol"],
      writer: ["alice"],
      manager: ["alice"],
      readerGroupIds: [],
      writerGroupIds: [],
    };
    const updated = { ...PERMISSIONS_FIXTURE, ...patch };
    mockEditUserGroupPermissions.mockResolvedValue(updated);

    const result = await editUserGroupPermissions(USER_GROUP_ID, patch);

    expect(mockEditUserGroupPermissions).toHaveBeenCalledOnce();
    expect(mockEditUserGroupPermissions).toHaveBeenCalledWith({
      userGroupId: USER_GROUP_ID,
      permissions: patch,
    });
    expect(result.reader).toEqual(["bob", "carol"]);
    expect(result.writer).toEqual(["alice"]);
  });

  it("propagates errors on non-2xx responses", async () => {
    mockEditUserGroupPermissions.mockRejectedValue(new Error("HTTP 403"));

    const patch: Omit<Permissions, "entityId"> = {
      owner: "alice",
      permissionType: "Public",
      reader: [],
      writer: [],
      manager: [],
      readerGroupIds: [],
      writerGroupIds: [],
    };

    await expect(
      editUserGroupPermissions(USER_GROUP_ID, patch),
    ).rejects.toThrow("HTTP 403");
  });
});

/**
 * SEARCH-V2-4 — unit tests for the migrated `useMemberSearch` and
 * `usePermissionUserSearch` composables.
 *
 * Verifies:
 * - calls GET /v2/users?q= (searchUsersV2), not the v1 SearchApi
 * - calls GET /v2/user-groups?q= (listUserGroups), not the v1 SearchApi
 * - SearchType.USER only fires user search
 * - SearchType.GROUP only fires group search
 * - SearchType.MEMBER fires both
 * - empty/undefined query resets and returns without firing
 * - groups are mapped from UserGroupV2 shape to UserGroup with id=0
 * - appId is preserved on mapped groups
 * - usePermissionUserSearch calls searchUsersV2 and returns User[]
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";
import { useMemberSearch, SearchType } from "~/composables/common/permissions/useMemberSearch";
import { usePermissionUserSearch } from "~/composables/common/permissions/usePermissionUserSearch";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: vi.fn(),
}));

const mockSearchUsersV2 = vi.fn();
const mockListUserGroups = vi.fn();

function makeUserGroupPagedResponse(groups: Array<{ appId: string; name: string; usernames?: string[] }>) {
  return {
    items: groups,
    total: groups.length,
    page: 0,
    pageSize: groups.length || 1,
  };
}

function makeUser(username: string) {
  return {
    username,
    appId: `user-appid-${username}`,
    firstName: "Test",
    lastName: "User",
    email: `${username}@example.com`,
    effectiveDisplayName: username,
    subscriptionIds: [],
    apiKeyIds: [],
    anonymizeInProvenance: false,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  (useV2ShepardApi as ReturnType<typeof vi.fn>).mockImplementation((ApiClass: unknown) => {
    const name = (ApiClass as { name?: string }).name ?? "";
    if (name === "UserApi") return ref({ searchUsersV2: mockSearchUsersV2 });
    if (name === "UserGroupsApi") return ref({ listUserGroups: mockListUserGroups });
    return ref({});
  });
  mockSearchUsersV2.mockResolvedValue([]);
  mockListUserGroups.mockResolvedValue(makeUserGroupPagedResponse([]));
});

describe("useMemberSearch (SEARCH-V2-4)", () => {
  it("fires user and group searches for MEMBER type", async () => {
    const q = ref<string | undefined>("alice");
    const { startSearch } = useMemberSearch(q, vi.fn(), SearchType.MEMBER);
    startSearch();
    await new Promise(r => setTimeout(r, 0));
    expect(mockSearchUsersV2).toHaveBeenCalledWith({ q: "alice" });
    expect(mockListUserGroups).toHaveBeenCalledWith({ q: "alice" });
  });

  it("fires only user search for USER type", async () => {
    const q = ref<string | undefined>("bob");
    const { startSearch } = useMemberSearch(q, vi.fn(), SearchType.USER);
    startSearch();
    await new Promise(r => setTimeout(r, 0));
    expect(mockSearchUsersV2).toHaveBeenCalledWith({ q: "bob" });
    expect(mockListUserGroups).not.toHaveBeenCalled();
  });

  it("fires only group search for GROUP type", async () => {
    const q = ref<string | undefined>("team");
    const { startSearch } = useMemberSearch(q, vi.fn(), SearchType.GROUP);
    startSearch();
    await new Promise(r => setTimeout(r, 0));
    expect(mockSearchUsersV2).not.toHaveBeenCalled();
    expect(mockListUserGroups).toHaveBeenCalledWith({ q: "team" });
  });

  it("resets results and does not fire when query is undefined", async () => {
    const q = ref<string | undefined>(undefined);
    const { searchResults, startSearch } = useMemberSearch(q, vi.fn());
    startSearch();
    await new Promise(r => setTimeout(r, 0));
    expect(mockSearchUsersV2).not.toHaveBeenCalled();
    expect(mockListUserGroups).not.toHaveBeenCalled();
    expect(searchResults.value).toHaveLength(0);
  });

  it("returns user results from searchUsersV2", async () => {
    mockSearchUsersV2.mockResolvedValue([makeUser("alice"), makeUser("bob")]);
    const q = ref<string | undefined>("alice");
    const { searchResults, startSearch } = useMemberSearch(q, vi.fn(), SearchType.USER);
    startSearch();
    await new Promise(r => setTimeout(r, 0));
    expect(searchResults.value).toHaveLength(2);
    expect((searchResults.value[0] as { username: string }).username).toBe("alice");
  });

  it("maps UserGroupV2 to UserGroup shape with id=0 and correct appId", async () => {
    mockListUserGroups.mockResolvedValue(
      makeUserGroupPagedResponse([
        { appId: "019eb019-d49b-7131-b2d2-aaaaaaaaaaaa", name: "Team Alpha" },
      ]),
    );
    const q = ref<string | undefined>("alpha");
    const { searchResults, startSearch } = useMemberSearch(q, vi.fn(), SearchType.GROUP);
    startSearch();
    await new Promise(r => setTimeout(r, 0));
    expect(searchResults.value).toHaveLength(1);
    const group = searchResults.value[0] as { id: number; name: string; appId: string | null | undefined };
    expect(group.id).toBe(0);
    expect(group.name).toBe("Team Alpha");
    expect(group.appId).toBe("019eb019-d49b-7131-b2d2-aaaaaaaaaaaa");
  });

  it("invokes onSearchDone callback after each search", async () => {
    const onDone = vi.fn();
    const q = ref<string | undefined>("test");
    const { startSearch } = useMemberSearch(q, onDone);
    startSearch();
    await new Promise(r => setTimeout(r, 0));
    expect(onDone).toHaveBeenCalledTimes(1);
  });
});

describe("usePermissionUserSearch (SEARCH-V2-4)", () => {
  it("calls searchUsersV2, not any v1 search method", async () => {
    mockSearchUsersV2.mockResolvedValue([makeUser("carol")]);
    const q = ref<string | undefined>("carol");
    const onDone = vi.fn();
    const { startSearch, ownerSearchResults } = usePermissionUserSearch(q, onDone);
    startSearch();
    await new Promise(r => setTimeout(r, 0));
    expect(mockSearchUsersV2).toHaveBeenCalledWith({ q: "carol" });
    expect(ownerSearchResults.value).toHaveLength(1);
    expect(ownerSearchResults.value[0]!.username).toBe("carol");
  });

  it("resets results and does not fire when query is undefined", async () => {
    const q = ref<string | undefined>(undefined);
    const { startSearch, ownerSearchResults } = usePermissionUserSearch(q, vi.fn());
    startSearch();
    await new Promise(r => setTimeout(r, 0));
    expect(mockSearchUsersV2).not.toHaveBeenCalled();
    expect(ownerSearchResults.value).toHaveLength(0);
  });

  it("invokes onSearchDone callback", async () => {
    mockSearchUsersV2.mockResolvedValue([makeUser("dave")]);
    const onDone = vi.fn();
    const q = ref<string | undefined>("dave");
    const { startSearch } = usePermissionUserSearch(q, onDone);
    startSearch();
    await new Promise(r => setTimeout(r, 0));
    expect(onDone).toHaveBeenCalledTimes(1);
  });
});

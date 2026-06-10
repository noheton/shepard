/**
 * V2-SWEEP-002-2 — appId-keyed user-group CRUD against the `/v2/user-groups`
 * surface shipped by `UserGroupV2Rest` (V2-SWEEP-002 slice 1).
 *
 * Frontend-v2-only rule (CLAUDE.md): this composable hits ONLY `/v2/user-groups`
 * and addresses groups by `appId` (UUID v7) — never the numeric Neo4j id, never
 * `useShepardApi` (the v1 helper). Membership is mutated through the same PATCH
 * surface: the backend treats the full `usernames` array as the member set
 * (RFC 7396 merge-patch — present fields replace, absent fields preserve).
 *
 * The generated `@dlr-shepard/backend-client` does not yet carry a v2 UserGroup
 * client (tracked as V2-SWEEP-001-CLIENT-REGEN), so this calls the endpoint with
 * a bearer-token `fetch`, deriving the v2 base URL exactly like `useV2ShepardApi`.
 */

/** Wire shape of `GET|POST|PATCH /v2/user-groups` — mirrors `UserGroupV2IO`. */
export interface UserGroupV2 {
  /** Application identifier (UUID v7). The only handle the frontend uses. */
  appId: string;
  name: string;
  usernames: string[];
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string | null;
  updatedBy?: string | null;
}

export interface CreateUserGroupV2Body {
  name: string;
  usernames?: string[];
}

export interface PatchUserGroupV2Body {
  name?: string;
  usernames?: string[];
}

function getV2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  return explicit && explicit.length > 0
    ? explicit
    : (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
}

function authHeaders(): Record<string, string> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  return accessToken ? { Authorization: `Bearer ${accessToken}` } : {};
}

const USER_GROUPS_PATH = "/v2/user-groups";

/**
 * Low-level v2 client. Pure functions over `fetch`; each throws on a non-2xx
 * response so callers can surface an error via `handleError`.
 */
export function useUserGroupsV2() {
  const base = () => `${getV2BaseUrl()}${USER_GROUPS_PATH}`;

  async function listUserGroups(): Promise<UserGroupV2[]> {
    const res = await fetch(base(), {
      headers: { ...authHeaders() },
      credentials: "include",
    });
    if (!res.ok) throw new Error(`listUserGroups failed: ${res.status}`);
    return (await res.json()) as UserGroupV2[];
  }

  async function getUserGroup(appId: string): Promise<UserGroupV2> {
    const res = await fetch(`${base()}/${encodeURIComponent(appId)}`, {
      headers: { ...authHeaders() },
      credentials: "include",
    });
    if (!res.ok) throw new Error(`getUserGroup failed: ${res.status}`);
    return (await res.json()) as UserGroupV2;
  }

  async function createUserGroup(
    body: CreateUserGroupV2Body,
  ): Promise<UserGroupV2> {
    const res = await fetch(base(), {
      method: "POST",
      headers: { ...authHeaders(), "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ name: body.name, usernames: body.usernames ?? [] }),
    });
    if (!res.ok) throw new Error(`createUserGroup failed: ${res.status}`);
    return (await res.json()) as UserGroupV2;
  }

  async function updateUserGroup(
    appId: string,
    body: PatchUserGroupV2Body,
  ): Promise<UserGroupV2> {
    const res = await fetch(`${base()}/${encodeURIComponent(appId)}`, {
      method: "PATCH",
      headers: { ...authHeaders(), "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify(body),
    });
    if (!res.ok) throw new Error(`updateUserGroup failed: ${res.status}`);
    return (await res.json()) as UserGroupV2;
  }

  async function deleteUserGroup(appId: string): Promise<void> {
    const res = await fetch(`${base()}/${encodeURIComponent(appId)}`, {
      method: "DELETE",
      headers: { ...authHeaders() },
      credentials: "include",
    });
    if (!res.ok) throw new Error(`deleteUserGroup failed: ${res.status}`);
  }

  /**
   * Adds a member by username, preserving the rest of the set. Membership is a
   * PATCH on the `usernames` array — there is no separate membership endpoint.
   */
  async function addMember(
    group: UserGroupV2,
    username: string,
  ): Promise<UserGroupV2> {
    if (group.usernames.includes(username)) return group;
    return updateUserGroup(group.appId, {
      usernames: [...group.usernames, username],
    });
  }

  /** Removes a member by username, preserving the rest of the set. */
  async function removeMember(
    group: UserGroupV2,
    username: string,
  ): Promise<UserGroupV2> {
    return updateUserGroup(group.appId, {
      usernames: group.usernames.filter(u => u !== username),
    });
  }

  return {
    listUserGroups,
    getUserGroup,
    createUserGroup,
    updateUserGroup,
    deleteUserGroup,
    addMember,
    removeMember,
  };
}

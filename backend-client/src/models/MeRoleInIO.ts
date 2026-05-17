/**
 * Wire shape for GET /v2/me/role-in/{collectionAppId} — the data
 * behind the "Role in current context" sidebar chip (U1c2).
 *
 * Manually maintained: not generated from OpenAPI spec (v2 endpoints
 * not yet in the upstream openapi.json; will be replaced by generated
 * code on next full client-codegen run).
 */
export interface MeRoleInIO {
  /** Collection appId the role applies to. */
  collectionAppId: string;
  /** Caller can read entities under this Collection. */
  read: boolean;
  /** Caller can create / update entities under this Collection. */
  write: boolean;
  /** Caller can edit permissions / delete on this Collection. */
  manage: boolean;
  /** Caller carries the instance-admin role. */
  isInstanceAdmin: boolean;
}

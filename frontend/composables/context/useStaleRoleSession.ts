/**
 * ROLE-GRANT-STALE-SESSION-02 — global stale-role-session flag.
 *
 * The backend `JWTFilter` now rejects JWTs that predate the affected user's
 * most recent role mutation with HTTP 401 + body shape
 * `{"exception":"role_changed", "message":"..."}` (per
 * `de.dlr.shepard.common.exceptions.ApiError`). The frontend's API client
 * middleware lifts this signal into a module-level `useState` flag so any
 * surface that renders `UnauthorizedView` can pass
 * `stale-session-reason="role-changed"` and upgrade the hint copy from the
 * speculative -03 default ("did you just get the grant?") to the
 * definitive -02 wording ("your role just changed").
 *
 * Flag lifecycle:
 *
 * - Cleared on app startup (the `useState` initial value).
 * - Set to `"role-changed"` by `tryParseRoleChangedFromResponse(response)`
 *   when the middleware sees a 401 with the structured body.
 * - Cleared on sign-out (the user is taking the recommended action, so the
 *   next post-auth render should not show the hint).
 *
 * The flag is intentionally global (Nuxt `useState`) so the surface that
 * raised the 401 doesn't have to be the same one that ultimately renders
 * the `UnauthorizedView` (e.g. a 401 on a `/v2/admin/features` GET drives
 * a render of the `/admin` landing's `UnauthorizedView` after the
 * navigation guard kicks in).
 */
export type StaleRoleSessionReason = "role-changed";

interface RoleChangedResponseBody {
  exception?: string;
  error?: string;
  message?: string;
}

/**
 * Returns the shared reactive flag + setter + reset for callers that
 * surface UnauthorizedView. Cleared on sign-out.
 */
export function useStaleRoleSession() {
  const reason = useState<StaleRoleSessionReason | null>(
    "stale-role-session-reason",
    () => null,
  );
  function set(next: StaleRoleSessionReason) {
    reason.value = next;
  }
  function clear() {
    reason.value = null;
  }
  return { reason, set, clear };
}

/**
 * Inspect a 401 response body and return `"role-changed"` when the body
 * matches the ApiError shape emitted by `JWTFilter` for the
 * ROLE-GRANT-STALE-SESSION-02 gate. Otherwise return `null`.
 *
 * Exported as a pure function so the auth-refresh middleware can call it
 * without setting up CDI / Nuxt state machinery — the test suite covers
 * this path independently of the `useState` plumbing.
 *
 * NOTE: the backend `ApiError` shape uses `exception` (Jackson default for
 * Lombok `@Value`) for the error code; older callers may also see `error`
 * (e.g. RFC-style proposals). Both are accepted to keep the matcher
 * tolerant of a future server-side rename.
 */
export function classifyRoleChangedBody(
  body: unknown,
): StaleRoleSessionReason | null {
  if (body === null || typeof body !== "object") return null;
  const b = body as RoleChangedResponseBody;
  if (b.exception === "role_changed" || b.error === "role_changed") {
    return "role-changed";
  }
  return null;
}

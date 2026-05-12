/**
 * Decode a JWT payload without verifying the signature.
 * Used client-side only to read claims (e.g. realm roles) from access tokens.
 */
export function parseJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const payload = parts[1];
    // Convert URL-safe base64 to standard base64
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const json = decodeURIComponent(
      atob(base64)
        .split("")
        .map(c => "%" + c.charCodeAt(0).toString(16).padStart(2, "0"))
        .join(""),
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

/**
 * Returns true when the given access token payload contains "instance-admin"
 * in `realm_access.roles`.
 */
export function hasInstanceAdminRole(token: string | undefined | null): boolean {
  if (!token) return false;
  const payload = parseJwtPayload(token);
  if (!payload) return false;
  const realmRoles =
    (payload.realm_access as { roles?: string[] } | undefined)?.roles ?? [];
  return realmRoles.includes("instance-admin");
}

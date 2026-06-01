export function isPlausibleAppId(input: string | null | undefined): boolean {
  if (!input) return false;
  const trimmed = input.trim().toLowerCase();
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(
    trimmed,
  );
}

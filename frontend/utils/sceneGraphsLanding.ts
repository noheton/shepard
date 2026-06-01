export function formatEpochMillis(epoch: number | null | undefined): string {
  if (epoch === null || epoch === undefined) return "—";
  return new Date(epoch).toLocaleDateString("en-UK", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export type LandingBranch = "table" | "help" | "error";

export function resolveLandingBranch(
  totalRows: number,
  loading: boolean,
  errorMessage: string | null,
): LandingBranch {
  if (errorMessage !== null && totalRows === 0 && !loading) return "error";
  if (totalRows > 0 || loading) return "table";
  return "help";
}

export const compareNullableStrings = (
  a: string | undefined,
  b: string | undefined,
): number => {
  if (!!a && !b) {
    return -1;
  }
  if (!a && !!b) {
    return 1;
  }
  if (!!a && !!b) {
    return a.localeCompare(b);
  }
  return 0;
};

const sortOrderOptions = ["asc", "desc"] as const;

export type SortOrderOption = (typeof sortOrderOptions)[number];

export type SortBy<SortByKey extends string = string> = {
  key: SortByKey;
  order: SortOrderOption;
};

export function instanceOfSortOrderOption(
  value: unknown,
): value is SortOrderOption {
  return typeof value === "string" && sortOrderOptions.some(o => o === value);
}

export function buildQueryString(searchText: string | null) {
  return JSON.stringify({
    OR: [
      {
        property: "name",
        value: searchText ?? "",
        operator: "contains",
      },
      {
        property: "createdBy",
        value: searchText ?? "",
        operator: "contains",
      },
      {
        property: "id",
        value: Number(searchText),
        operator: "eq",
      },
    ],
  });
}

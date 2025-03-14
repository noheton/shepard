export function createSearchQueryFromString(searchText: string): string {
  return JSON.stringify({
    OR: [
      {
        property: "username",
        value: searchText ?? "",
        operator: "contains",
      },
      {
        property: "firstName",
        value: searchText ?? "",
        operator: "contains",
      },
      {
        property: "lastName",
        value: searchText,
        operator: "contains",
      },
      {
        property: "email",
        value: searchText,
        operator: "contains",
      },
    ],
  });
}

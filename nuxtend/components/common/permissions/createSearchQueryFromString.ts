export function createUserSearchQueryFromString(searchText: string): string {
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

export function createUserGroupSearchQueryFromString(
  searchText: string,
): string {
  return JSON.stringify({
    OR: [
      {
        property: "name",
        value: searchText,
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

import type { BasicContainerAttributes } from "@dlr-shepard/backend-client";

export const ContainerSortByOrderOptions = {
  ASC: "asc",
  DESC: "desc",
};
export type ContainerSortByOrder =
  (typeof ContainerSortByOrderOptions)[keyof typeof ContainerSortByOrderOptions];
export type ContainerSortByAttribute = {
  key: BasicContainerAttributes;
  order: ContainerSortByOrder;
};

export function instanceOfContainerSortByOrder(
  value: string,
): value is ContainerSortByOrder {
  return Object.values(ContainerSortByOrderOptions).includes(
    value as ContainerSortByOrder,
  );
}

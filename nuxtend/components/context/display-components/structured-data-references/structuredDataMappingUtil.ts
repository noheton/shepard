import type { StructuredDataDataTableItem } from "./structuredDataDataTableItem";
import type { StructuredDataMeta } from "./structuredDataReferenceTypes";

export const mapStructuredDataListToDataTableItems = (
  structuredDataList: StructuredDataMeta[],
): StructuredDataDataTableItem[] => {
  return structuredDataList.map(structuredData => {
    return mapStructuredDataToDataTableItem(structuredData);
  });
};

export const mapStructuredDataToDataTableItem = (
  structuredData: StructuredDataMeta,
): StructuredDataDataTableItem => {
  return {
    name: {
      structuredDataName: structuredData.name ?? "",
      availability: structuredData.availability ?? "error",
    },
    oid: structuredData.oid ?? "",
    createdAt: structuredData.createdAt ?? new Date(0),
    actions: {
      showPayload: {
        enabled: mapShowDetailsEnabled(structuredData),
        payload: structuredData.payload,
      },
    },
  };
};

export const mapShowDetailsEnabled = (structuredData: StructuredDataMeta) => {
  return structuredData.oid && structuredData.availability === "available"
    ? true
    : false;
};

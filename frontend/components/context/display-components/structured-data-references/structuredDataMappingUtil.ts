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
  const enabled = mapShowDetailsEnabled(structuredData);
  const payload = structuredData.payload;
  return {
    name: {
      structuredDataName: structuredData.name ?? "",
      availability: structuredData.availability ?? "error",
    },
    oid: structuredData.oid ?? "",
    createdAt: structuredData.createdAt ?? new Date(0),
    actions: {
      showPayload: {
        enabled,
        payload,
      },
      download: {
        enabled,
        filename: buildStructuredDataFilename(
          structuredData.name,
          structuredData.oid,
        ),
        payload,
      },
    },
  };
};

export const mapShowDetailsEnabled = (structuredData: StructuredDataMeta) => {
  return structuredData.oid && structuredData.availability === "available"
    ? true
    : false;
};

/**
 * UI-SDREF-NO-CONTENT-001 — produce a sensible download filename.
 * Prefer the structured-data entry's `name` (already user-supplied);
 * fall back to the oid; always append `.json` since structured data
 * is JSON by contract.
 */
export const buildStructuredDataFilename = (
  name: string | null | undefined,
  oid: string | null | undefined,
): string => {
  const base = (name && name.trim()) || oid || "structured-data";
  return /\.json$/i.test(base) ? base : `${base}.json`;
};

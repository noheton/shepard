/**
 * UI-SEARCH-JSON-QUERY-001 — structured query builder serializer for
 * the /search Advanced panel. Replaces hand-written JSON for the
 * common shapes (combine N filters with AND/OR).
 *
 * Backend vocabulary is the wire contract (see
 * `backend/src/main/java/de/dlr/shepard/common/search/query/
 * Neo4jQueryBuilder.java` — KNOWN_PROPERTIES + operatorString).
 * Anything we expose here MUST be in that vocabulary.
 */

/** Property identifiers accepted by `Neo4jQueryBuilder.KNOWN_PROPERTIES`
 * plus the annotation/id specials handled separately. The values here
 * are the literal JSON `property` strings the backend accepts. */
export const SEARCH_PROPERTIES = [
  { value: "name",         label: "Name",                kind: "string" as const },
  { value: "description",  label: "Description",         kind: "string" as const },
  { value: "id",           label: "AppId / Numeric ID",  kind: "string" as const },
  { value: "createdAt",    label: "Created at",          kind: "date"   as const },
  { value: "updatedAt",    label: "Updated at",          kind: "date"   as const },
  { value: "createdBy",    label: "Created by (user)",   kind: "string" as const },
  { value: "propertyIRI",  label: "Annotation predicate", kind: "string" as const },
  { value: "valueIRI",     label: "Annotation value",    kind: "string" as const },
] as const;

export type SearchProperty = (typeof SEARCH_PROPERTIES)[number]["value"];

/** Operators accepted by `Neo4jQueryBuilder.operatorString`. Filtered
 * by property kind in the UI (e.g. `contains` only makes sense for
 * strings; `gt/lt/ge/le` makes sense for ids + dates). */
export const SEARCH_OPERATORS = [
  { value: "contains", label: "contains",           kinds: ["string"]            as const },
  { value: "eq",       label: "equals",             kinds: ["string", "date"]    as const },
  { value: "ne",       label: "not equals",         kinds: ["string", "date"]    as const },
  { value: "regmatch", label: "matches regex",      kinds: ["string"]            as const },
  { value: "gt",       label: "greater than",       kinds: ["date", "string"]    as const },
  { value: "lt",       label: "less than",          kinds: ["date", "string"]    as const },
  { value: "ge",       label: "greater or equal",   kinds: ["date", "string"]    as const },
  { value: "le",       label: "less or equal",      kinds: ["date", "string"]    as const },
] as const;

export type SearchOperator = (typeof SEARCH_OPERATORS)[number]["value"];

export type CombineMode = "AND" | "OR";

export interface QueryFilter {
  property: SearchProperty;
  operator: SearchOperator;
  /** Always a string in the UI; parsed/normalised at serialize-time. */
  value: string;
}

/** Operators valid for a property given its declared `kind`. */
export function operatorsForProperty(
  property: SearchProperty,
): readonly { value: SearchOperator; label: string }[] {
  const def = SEARCH_PROPERTIES.find(p => p.value === property);
  const kind = def?.kind ?? "string";
  return SEARCH_OPERATORS.filter(op =>
    (op.kinds as readonly string[]).includes(kind),
  );
}

/** Coerce a UI string value to the right JSON shape for the property.
 * Date inputs come back as `"2026-05-30"` (HTML date) which the
 * backend already accepts as a string compared lex against ISO
 * timestamps; we leave date strings unchanged. */
function coerceValue(property: SearchProperty, raw: string): string | number {
  if (property === "id") {
    const n = Number(raw);
    if (Number.isFinite(n) && raw.trim() !== "" && !raw.includes("-")) return n;
    return raw;
  }
  return raw;
}

/** Serialize a single filter to the wire JSON shape. */
export function serializeFilter(f: QueryFilter): Record<string, unknown> {
  return {
    property: f.property,
    operator: f.operator,
    value: coerceValue(f.property, f.value),
  };
}

/** Combine N filters into the backend's nested {AND|OR: [...]} shape.
 *
 * - 0 filters → a no-op `{property:"name", operator:"contains", value:""}`
 *   (matches the legacy "broad search" default).
 * - 1 filter  → the bare filter object (omits the boolean wrap).
 * - N filters → `{AND|OR: [filter1, filter2, …]}`.
 */
export function serializeQuery(
  filters: QueryFilter[],
  combine: CombineMode = "AND",
): string {
  const valid = filters.filter(f => f.value.trim() !== "");
  if (valid.length === 0) {
    return JSON.stringify(
      { property: "name", operator: "contains", value: "" },
      null,
      4,
    );
  }
  if (valid.length === 1) {
    return JSON.stringify(serializeFilter(valid[0]!), null, 4);
  }
  return JSON.stringify(
    { [combine]: valid.map(serializeFilter) },
    null,
    4,
  );
}

/**
 * UI-SEARCH-JSON-QUERY-001 — unit tests for the structured query
 * builder serializer. Confirms the wire JSON shape stays
 * byte-compatible with the backend `Neo4jQueryBuilder` vocabulary
 * (KNOWN_PROPERTIES + operatorString).
 */
import { describe, it, expect } from "vitest";
import {
  SEARCH_OPERATORS,
  SEARCH_PROPERTIES,
  operatorsForProperty,
  serializeFilter,
  serializeQuery,
  type QueryFilter,
} from "~/utils/searchQueryBuilder";

describe("SEARCH_PROPERTIES vocabulary", () => {
  it("covers every name in the backend whitelist", () => {
    // Mirror of Neo4jQueryBuilder.KNOWN_PROPERTIES + the id/annotation specials.
    // If the backend whitelist grows, this test reminds us to expose it.
    const exposed: Set<string> = new Set(
      SEARCH_PROPERTIES.map(p => p.value as string),
    );
    for (const required of [
      "name",
      "description",
      "id",
      "createdAt",
      "updatedAt",
      "createdBy",
      "propertyIRI",
      "valueIRI",
    ]) {
      expect(exposed.has(required)).toBe(true);
    }
  });
});

describe("operatorsForProperty", () => {
  it("offers `contains` for string properties", () => {
    const ops = operatorsForProperty("name").map(o => o.value);
    expect(ops).toContain("contains");
  });

  it("offers `gt`/`lt` for date properties", () => {
    const ops = operatorsForProperty("createdAt").map(o => o.value);
    expect(ops).toContain("gt");
    expect(ops).toContain("lt");
  });

  it("never returns operators outside the backend vocabulary", () => {
    const allowed = new Set(SEARCH_OPERATORS.map(o => o.value));
    for (const p of SEARCH_PROPERTIES) {
      for (const op of operatorsForProperty(p.value)) {
        expect(allowed.has(op.value)).toBe(true);
      }
    }
  });
});

describe("serializeFilter", () => {
  it("emits a property/operator/value triple", () => {
    const f: QueryFilter = {
      property: "name",
      operator: "contains",
      value: "TR-004",
    };
    expect(serializeFilter(f)).toEqual({
      property: "name",
      operator: "contains",
      value: "TR-004",
    });
  });

  it("coerces numeric ids to numbers", () => {
    const f: QueryFilter = {
      property: "id",
      operator: "eq",
      value: "42",
    };
    expect(serializeFilter(f).value).toBe(42);
  });

  it("leaves UUID-shaped ids as strings", () => {
    const f: QueryFilter = {
      property: "id",
      operator: "eq",
      value: "019e6ffc-89a4-76b5-8dbb-15888646a904",
    };
    expect(serializeFilter(f).value).toBe(
      "019e6ffc-89a4-76b5-8dbb-15888646a904",
    );
  });
});

describe("serializeQuery", () => {
  it("returns a single bare filter when given one row", () => {
    const out = JSON.parse(
      serializeQuery([
        { property: "name", operator: "contains", value: "hotfire" },
      ]),
    );
    expect(out).toEqual({
      property: "name",
      operator: "contains",
      value: "hotfire",
    });
  });

  it("wraps multiple filters in AND by default", () => {
    const out = JSON.parse(
      serializeQuery([
        { property: "name", operator: "contains", value: "tr" },
        { property: "createdBy", operator: "eq", value: "alice" },
      ]),
    );
    expect(out.AND).toHaveLength(2);
    expect(out.AND[0]).toEqual({
      property: "name",
      operator: "contains",
      value: "tr",
    });
  });

  it("respects OR when requested", () => {
    const out = JSON.parse(
      serializeQuery(
        [
          { property: "name", operator: "contains", value: "tr" },
          { property: "name", operator: "contains", value: "mffd" },
        ],
        "OR",
      ),
    );
    expect(out.OR).toHaveLength(2);
  });

  it("drops filters whose value is empty/whitespace", () => {
    const out = JSON.parse(
      serializeQuery([
        { property: "name", operator: "contains", value: "tr" },
        { property: "description", operator: "contains", value: "   " },
      ]),
    );
    // Single non-empty filter → bare object, not an AND wrapper.
    expect(out).toEqual({
      property: "name",
      operator: "contains",
      value: "tr",
    });
  });

  it("returns the broad-name fallback when all filters are empty", () => {
    const out = JSON.parse(
      serializeQuery([
        { property: "name", operator: "contains", value: "" },
      ]),
    );
    expect(out.property).toBe("name");
    expect(out.value).toBe("");
  });
});

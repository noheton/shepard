/**
 * UX bonus (2026-05-24) — searchQueryFromParams translates URL params
 * on /search into the JSON-query string the Advanced Search form drives.
 *
 * Three cases covered: `?searchQuery=`, `?q=`, neither.
 * Conflict rule: `searchQuery` wins over `q`.
 */
import { describe, it, expect } from "vitest";
import {
  buildNameContainsQuery,
  searchQueryFromParams,
} from "~/utils/searchQueryFromParams";

const FALLBACK = '{"placeholder":true}';

describe("buildNameContainsQuery", () => {
  it("emits a property/contains/value triple as JSON", () => {
    const out = JSON.parse(buildNameContainsQuery("TR-004"));
    expect(out).toEqual({
      property: "name",
      operator: "contains",
      value: "TR-004",
    });
  });

  it("preserves special chars in the needle", () => {
    const out = JSON.parse(buildNameContainsQuery('foo "bar" / baz'));
    expect(out.value).toBe('foo "bar" / baz');
  });
});

describe("searchQueryFromParams", () => {
  it("returns fallback + shouldRun=false when no relevant params", () => {
    const r = searchQueryFromParams(new URLSearchParams(""), FALLBACK);
    expect(r.jsonQuery).toBe(FALLBACK);
    expect(r.shouldRun).toBe(false);
  });

  it("derives jsonQuery from ?q= when only q is present", () => {
    const r = searchQueryFromParams(
      new URLSearchParams("q=TR-004"),
      FALLBACK,
    );
    expect(JSON.parse(r.jsonQuery)).toEqual({
      property: "name",
      operator: "contains",
      value: "TR-004",
    });
    expect(r.shouldRun).toBe(true);
  });

  it("uses ?searchQuery= verbatim when present", () => {
    const sq = '{"property":"id","operator":"eq","value":42}';
    const r = searchQueryFromParams(
      new URLSearchParams(`searchQuery=${encodeURIComponent(sq)}`),
      FALLBACK,
    );
    expect(r.jsonQuery).toBe(sq);
    expect(r.shouldRun).toBe(true);
  });

  it("prefers searchQuery over q when both are present", () => {
    const sq = '{"property":"name","operator":"eq","value":"exact"}';
    const r = searchQueryFromParams(
      new URLSearchParams(
        `q=fuzzy&searchQuery=${encodeURIComponent(sq)}`,
      ),
      FALLBACK,
    );
    expect(r.jsonQuery).toBe(sq);
    expect(r.shouldRun).toBe(true);
  });

  it("ignores ?q= when its value is whitespace-only", () => {
    const r = searchQueryFromParams(
      new URLSearchParams("q=%20%20%20"),
      FALLBACK,
    );
    expect(r.jsonQuery).toBe(FALLBACK);
    expect(r.shouldRun).toBe(false);
  });

  it("trims surrounding whitespace from ?q= before searching", () => {
    const r = searchQueryFromParams(
      new URLSearchParams("q=%20%20TR-004%20%20"),
      FALLBACK,
    );
    expect(JSON.parse(r.jsonQuery).value).toBe("TR-004");
    expect(r.shouldRun).toBe(true);
  });
});

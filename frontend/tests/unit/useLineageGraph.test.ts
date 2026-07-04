/**
 * UI16 — useLineageGraph composable
 *
 * Tests the three pure-function exports:
 *   - truncateLabel
 *   - nodeColor
 *   - baseGraphSeriesConfig (structural shape check)
 */
import { describe, it, expect } from "vitest";
import {
  truncateLabel,
  nodeColor,
  baseGraphSeriesConfig,
  STATUS_COLORS,
  ACTION_COLORS,
  DEFAULT_NODE_COLOR,
} from "~/composables/useLineageGraph";

// ---------------------------------------------------------------------------
// truncateLabel
// ---------------------------------------------------------------------------

describe("truncateLabel", () => {
  // ---- default threshold (maxLen = 18) used by CollectionLineageGraph ----

  it("returns label unchanged when shorter than default threshold", () => {
    expect(truncateLabel("short")).toBe("short");
  });

  it("returns label unchanged when exactly at default threshold (18 chars)", () => {
    const label = "a".repeat(18);
    expect(truncateLabel(label)).toBe(label);
  });

  it("truncates when one char over default threshold (19 chars)", () => {
    const label = "a".repeat(19);
    expect(truncateLabel(label)).toBe("a".repeat(16) + "…");
  });

  it("truncates a realistic long name at default threshold", () => {
    const result = truncateLabel("TR-004_AnomalyInvestigation");
    // slice(0, 16) = "TR-004_AnomalyIn" + "…"
    expect(result).toBe("TR-004_AnomalyIn…");
    expect(result.length).toBe(17); // 16 chars + "…"
  });

  // ---- explicit maxLen = 16 used by DataObjectProvGraph ----

  it("returns label unchanged when shorter than explicit threshold of 16", () => {
    const label = "a".repeat(15);
    expect(truncateLabel(label, 16)).toBe(label);
  });

  it("returns label unchanged when exactly at threshold of 16", () => {
    const label = "a".repeat(16);
    expect(truncateLabel(label, 16)).toBe(label);
  });

  it("truncates when one char over threshold of 16 (17 chars)", () => {
    const label = "a".repeat(17);
    expect(truncateLabel(label, 16)).toBe("a".repeat(14) + "…");
  });

  it("truncates a realistic long name at threshold 16", () => {
    const result = truncateLabel("AnomalyDetection", 16);
    // exactly 16 chars — should not truncate
    expect(result).toBe("AnomalyDetection");
  });

  it("truncates a 17-char name at threshold 16", () => {
    const result = truncateLabel("AnomalyDetection2", 16);
    expect(result).toBe("AnomalyDetecti…");
    expect(result.length).toBe(15); // 14 chars + "…"
  });

  // ---- edge cases ----

  it("handles empty string without error", () => {
    expect(truncateLabel("")).toBe("");
  });

  it("handles a single-character string", () => {
    expect(truncateLabel("X")).toBe("X");
  });

  it("produces exactly maxLen−1 + 1 ellipsis character when truncating", () => {
    // With maxLen=10: result should be 8 chars + "…" = 9 total chars
    const long = "a".repeat(20);
    const result = truncateLabel(long, 10);
    expect(result).toBe("a".repeat(8) + "…");
    // Length in JS: "…" is one code unit
    expect([...result].length).toBe(9);
  });
});

// ---------------------------------------------------------------------------
// nodeColor
// ---------------------------------------------------------------------------

describe("nodeColor", () => {
  // ---- STATUS_COLORS palette (default) ----

  it("returns the correct colour for DRAFT", () => {
    expect(nodeColor("DRAFT")).toBe(STATUS_COLORS.DRAFT);
    expect(nodeColor("DRAFT")).toBe("#8C8C8C");
  });

  it("returns the correct colour for IN_REVIEW", () => {
    expect(nodeColor("IN_REVIEW")).toBe("#FCA54D");
  });

  it("returns the correct colour for READY", () => {
    expect(nodeColor("READY")).toBe("#4097CC");
  });

  it("returns the correct colour for PUBLISHED", () => {
    expect(nodeColor("PUBLISHED")).toBe("#7ECA8F");
  });

  it("returns the correct colour for ARCHIVED", () => {
    expect(nodeColor("ARCHIVED")).toBe("#B799DB");
  });

  it("returns DEFAULT_NODE_COLOR for an unknown status", () => {
    expect(nodeColor("NONEXISTENT")).toBe(DEFAULT_NODE_COLOR);
    expect(nodeColor("")).toBe(DEFAULT_NODE_COLOR);
  });

  // ---- ACTION_COLORS palette (explicit second argument) ----

  it("returns the correct colour for CREATE in ACTION_COLORS", () => {
    expect(nodeColor("CREATE", ACTION_COLORS)).toBe("#7ECA8F");
  });

  it("returns the correct colour for UPDATE in ACTION_COLORS", () => {
    expect(nodeColor("UPDATE", ACTION_COLORS)).toBe("#4097CC");
  });

  it("returns the correct colour for DELETE in ACTION_COLORS", () => {
    expect(nodeColor("DELETE", ACTION_COLORS)).toBe("#E56874");
  });

  it("returns the correct colour for READ in ACTION_COLORS", () => {
    expect(nodeColor("READ", ACTION_COLORS)).toBe("#8C8C8C");
  });

  it("returns the correct colour for EXECUTE in ACTION_COLORS", () => {
    expect(nodeColor("EXECUTE", ACTION_COLORS)).toBe("#B799DB");
  });

  it("returns DEFAULT_NODE_COLOR for an unknown action kind", () => {
    expect(nodeColor("UNKNOWN", ACTION_COLORS)).toBe(DEFAULT_NODE_COLOR);
  });

  // ---- custom palette ----

  it("accepts an arbitrary palette record", () => {
    const palette = { FOO: "#123456", BAR: "#abcdef" };
    expect(nodeColor("FOO", palette)).toBe("#123456");
    expect(nodeColor("BAR", palette)).toBe("#abcdef");
    expect(nodeColor("BAZ", palette)).toBe(DEFAULT_NODE_COLOR);
  });
});

// ---------------------------------------------------------------------------
// baseGraphSeriesConfig — structural shape
// ---------------------------------------------------------------------------

describe("baseGraphSeriesConfig", () => {
  it("returns an object with type 'graph'", () => {
    expect(baseGraphSeriesConfig().type).toBe("graph");
  });

  it("enables roam", () => {
    expect(baseGraphSeriesConfig().roam).toBe(true);
  });

  it("sets edgeSymbol to ['none', 'arrow']", () => {
    expect(baseGraphSeriesConfig().edgeSymbol).toEqual(["none", "arrow"]);
  });

  it("sets lineStyle curveness", () => {
    const cfg = baseGraphSeriesConfig();
    expect((cfg.lineStyle as Record<string, unknown>).curveness).toBe(0.15);
  });

  it("sets emphasis.focus to 'adjacency'", () => {
    const cfg = baseGraphSeriesConfig();
    expect((cfg.emphasis as Record<string, unknown>).focus).toBe("adjacency");
  });

  it("sets label.color to 'inherit'", () => {
    const cfg = baseGraphSeriesConfig();
    expect((cfg.label as Record<string, unknown>).color).toBe("inherit");
  });

  it("does NOT include layout (callers must specify their own)", () => {
    expect("layout" in baseGraphSeriesConfig()).toBe(false);
  });

  it("does NOT include nodes (callers must supply them)", () => {
    expect("nodes" in baseGraphSeriesConfig()).toBe(false);
  });

  it("does NOT include edges (callers must supply them)", () => {
    expect("edges" in baseGraphSeriesConfig()).toBe(false);
  });

  it("returns a new object on each call (no shared mutation risk)", () => {
    const a = baseGraphSeriesConfig();
    const b = baseGraphSeriesConfig();
    expect(a).not.toBe(b);
  });
});

import { describe, it, expect } from "vitest";
import {
  parseSvdxSelector,
  groupSvdxBindingsByDataType,
  extractManifest,
  type SvdxBindingRow,
} from "~/utils/svdxChannelChart";

// ── parseSvdxSelector ─────────────────────────────────────────────────────────

describe("parseSvdxSelector", () => {
  it("parses a full channel selector", () => {
    const json = JSON.stringify({
      channelName: "TC_CH1",
      symbolName: undefined,
      dataType: "REAL32",
      amsNetId: "1.2.3.4.1.1",
      port: "851",
      manifest: {
        channelCount: "10",
        acquisitionCount: "2",
        projectName: "StatorTest",
        dataTypes: "REAL32,INT16",
        amsNetIds: "1.2.3.4.1.1",
        ports: "851",
      },
    });
    const result = parseSvdxSelector(json);
    expect(result?.channelName).toBe("TC_CH1");
    expect(result?.dataType).toBe("REAL32");
    expect(result?.amsNetId).toBe("1.2.3.4.1.1");
    expect(result?.port).toBe("851");
    expect(result?.manifest?.projectName).toBe("StatorTest");
    expect(result?.manifest?.channelCount).toBe("10");
  });

  it("parses an acquisition selector (symbolName present, channelName absent)", () => {
    const json = JSON.stringify({
      symbolName: "ACQ_SYM1",
      dataType: "INT16",
      amsNetId: "1.2.3.4.1.1",
      port: "851",
    });
    const result = parseSvdxSelector(json);
    expect(result?.symbolName).toBe("ACQ_SYM1");
    expect(result?.channelName).toBeUndefined();
    expect(result?.dataType).toBe("INT16");
  });

  it("parses a MISSING binding selector (anchorAppId + reason)", () => {
    const json = JSON.stringify({
      anchorAppId: "019603ab-dead-7fff-beef-000000000001",
      reason: "no .svdx FileReference found on DataObject",
    });
    const result = parseSvdxSelector(json);
    expect(result?.anchorAppId).toBe("019603ab-dead-7fff-beef-000000000001");
    expect(result?.reason).toMatch(/no .svdx/);
  });

  it("returns null for invalid JSON", () => {
    expect(parseSvdxSelector("{invalid}")).toBeNull();
  });

  it("returns null for empty string", () => {
    expect(parseSvdxSelector("")).toBeNull();
  });

  it("returns null for null input", () => {
    expect(parseSvdxSelector(null)).toBeNull();
  });

  it("returns null for undefined input", () => {
    expect(parseSvdxSelector(undefined)).toBeNull();
  });
});

// ── groupSvdxBindingsByDataType ───────────────────────────────────────────────

describe("groupSvdxBindingsByDataType", () => {
  it("groups bindings by dataType", () => {
    const rows: SvdxBindingRow[] = [
      { role: "channel-0", status: "OK", selector: { dataType: "REAL32" } },
      { role: "channel-1", status: "OK", selector: { dataType: "REAL32" } },
      { role: "channel-2", status: "OK", selector: { dataType: "INT16" } },
    ];
    const groups = groupSvdxBindingsByDataType(rows);
    expect(groups.get("REAL32")?.length).toBe(2);
    expect(groups.get("INT16")?.length).toBe(1);
  });

  it("preserves insertion order within each group", () => {
    const rows: SvdxBindingRow[] = [
      { role: "channel-0", status: "OK", selector: { dataType: "REAL32", channelName: "A" } },
      { role: "channel-1", status: "OK", selector: { dataType: "REAL32", channelName: "B" } },
    ];
    const group = groupSvdxBindingsByDataType(rows).get("REAL32")!;
    expect(group[0]?.selector?.channelName).toBe("A");
    expect(group[1]?.selector?.channelName).toBe("B");
  });

  it("uses (unknown) for rows with no dataType", () => {
    const rows: SvdxBindingRow[] = [
      {
        role: "channel-0",
        status: "MISSING",
        selector: { anchorAppId: "abc", reason: "no svdx found" },
      },
    ];
    const groups = groupSvdxBindingsByDataType(rows);
    expect(groups.has("(unknown)")).toBe(true);
    expect(groups.get("(unknown)")?.length).toBe(1);
  });

  it("uses (unknown) for rows with null selector", () => {
    const rows: SvdxBindingRow[] = [{ role: "channel-0", status: "MISSING", selector: null }];
    const groups = groupSvdxBindingsByDataType(rows);
    expect(groups.has("(unknown)")).toBe(true);
  });

  it("returns empty map for empty input", () => {
    expect(groupSvdxBindingsByDataType([]).size).toBe(0);
  });

  it("handles mixed REAL32 and unknown in one call", () => {
    const rows: SvdxBindingRow[] = [
      { role: "channel-0", status: "OK", selector: { dataType: "REAL32" } },
      { role: "channel-1", status: "MISSING", selector: null },
    ];
    const groups = groupSvdxBindingsByDataType(rows);
    expect(groups.size).toBe(2);
    expect(groups.get("REAL32")?.length).toBe(1);
    expect(groups.get("(unknown)")?.length).toBe(1);
  });
});

// ── extractManifest ───────────────────────────────────────────────────────────

describe("extractManifest", () => {
  it("returns the first manifest found", () => {
    const rows: SvdxBindingRow[] = [
      {
        role: "channel-0",
        status: "OK",
        selector: { manifest: { projectName: "P1", channelCount: "5" } },
      },
      {
        role: "channel-1",
        status: "OK",
        selector: { manifest: { projectName: "P2" } },
      },
    ];
    const manifest = extractManifest(rows);
    expect(manifest?.projectName).toBe("P1");
    expect(manifest?.channelCount).toBe("5");
  });

  it("skips rows without a manifest before finding one", () => {
    const rows: SvdxBindingRow[] = [
      { role: "channel-0", status: "OK", selector: { channelName: "CH1" } },
      {
        role: "channel-1",
        status: "OK",
        selector: { manifest: { projectName: "Found" } },
      },
    ];
    expect(extractManifest(rows)?.projectName).toBe("Found");
  });

  it("returns null when no binding carries a manifest", () => {
    const rows: SvdxBindingRow[] = [
      { role: "channel-0", status: "OK", selector: { channelName: "CH1" } },
    ];
    expect(extractManifest(rows)).toBeNull();
  });

  it("returns null for null selectors", () => {
    const rows: SvdxBindingRow[] = [{ role: "channel-0", status: "MISSING", selector: null }];
    expect(extractManifest(rows)).toBeNull();
  });

  it("returns null for empty rows", () => {
    expect(extractManifest([])).toBeNull();
  });
});

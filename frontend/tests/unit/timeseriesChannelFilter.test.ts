import { describe, it, expect } from "vitest";
import {
  channelMatchesSearch,
  filterChannelsBySelection,
  type ChannelSearchable,
} from "../../utils/timeseriesChannelFilter";

const makeChannel = (overrides: Partial<ChannelSearchable> = {}): ChannelSearchable => ({
  measurement: "temperature",
  device: "sensor-A",
  location: "lab",
  symbolicName: "temp_A",
  field: "value",
  ...overrides,
});

describe("channelMatchesSearch", () => {
  it("empty query matches everything", () => {
    expect(channelMatchesSearch(makeChannel(), "")).toBe(true);
  });

  it("matches on measurement (case-insensitive)", () => {
    expect(channelMatchesSearch(makeChannel({ measurement: "Temperature" }), "temp")).toBe(true);
    // All other fields don't contain "xyz" either
    expect(channelMatchesSearch(
      makeChannel({ measurement: "pressure", device: "B", location: "x", symbolicName: "pres_1", field: "raw" }),
      "xyz",
    )).toBe(false);
  });

  it("matches on device", () => {
    expect(channelMatchesSearch(makeChannel({ device: "sensor-B" }), "sensor")).toBe(true);
  });

  it("matches on location", () => {
    expect(channelMatchesSearch(makeChannel({ location: "RoomA" }), "rooma")).toBe(true);
  });

  it("matches on symbolicName", () => {
    expect(channelMatchesSearch(makeChannel({ symbolicName: "T_outer" }), "outer")).toBe(true);
  });

  it("matches on field", () => {
    expect(channelMatchesSearch(makeChannel({ field: "raw_voltage" }), "voltage")).toBe(true);
  });

  it("null/undefined fields do not throw", () => {
    const item: ChannelSearchable = { measurement: null, device: undefined, location: null, symbolicName: null, field: null };
    expect(channelMatchesSearch(item, "x")).toBe(false);
    expect(channelMatchesSearch(item, "")).toBe(true);
  });
});

describe("filterChannelsBySelection", () => {
  const key = (ch: ChannelSearchable) => `${ch.measurement}-${ch.device}`;
  const a = makeChannel({ measurement: "temp", device: "A" });
  const b = makeChannel({ measurement: "pres", device: "B", location: "b" });
  const c = makeChannel({ measurement: "humi", device: "C", location: "c" });
  const items = [a, b, c];

  it("returns all items when selection is empty", () => {
    expect(filterChannelsBySelection(items, new Set(), key)).toEqual(items);
  });

  it("filters to selected keys", () => {
    const result = filterChannelsBySelection(items, new Set(["temp-A", "humi-C"]), key);
    expect(result).toEqual([a, c]);
  });

  it("returns empty when no items match selected keys", () => {
    const result = filterChannelsBySelection(items, new Set(["xyz-Z"]), key);
    expect(result).toEqual([]);
  });
});

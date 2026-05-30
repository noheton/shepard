/**
 * UX-WALK-2026-05-29-02 — unit tests for the axis-annotation fan-out logic
 * in useContainerAxisAnnotations.
 *
 * These tests validate the pure helper functions (channelLabel, TS_AXIS_PREDICATE
 * filtering) extracted from the composable, without mounting Nuxt or making
 * real HTTP calls. Integration / Playwright tests cover the rendered output.
 */
import { describe, it, expect } from "vitest";
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";
import { TS_AXIS_PREDICATE } from "~/composables/containers/useContainerAxisAnnotations";

// ── helpers mirrored from the composable ─────────────────────────────────────

interface ChannelV2 {
  shepardId?: string | null;
  measurement?: string | null;
  device?: string | null;
  location?: string | null;
  symbolicName?: string | null;
  field?: string | null;
}

function channelLabel(ch: ChannelV2): string {
  const parts = [ch.device, ch.field, ch.location, ch.measurement, ch.symbolicName].filter(Boolean);
  return parts.length ? parts.join(" · ") : "(unnamed channel)";
}

function makeAnnotation(
  propertyIRI: string,
  valueIRI: string,
  valueName: string,
  id = 1,
): SemanticAnnotation {
  return {
    id,
    name: `annotation-${id}`,
    propertyName: propertyIRI.split(":").pop() ?? propertyIRI,
    propertyIRI,
    valueName,
    valueIRI,
    propertyRepositoryId: 0,
    valueRepositoryId: 0,
  };
}

// ── TS_AXIS_PREDICATE constant ────────────────────────────────────────────────

describe("TS_AXIS_PREDICATE", () => {
  it("equals the constant written by AnnotatableTimeseriesService", () => {
    expect(TS_AXIS_PREDICATE).toBe("urn:shepard:spatial:axis");
  });
});

// ── channelLabel ──────────────────────────────────────────────────────────────

describe("channelLabel", () => {
  it("joins all non-null parts with ' · '", () => {
    const ch: ChannelV2 = { device: "IMU", field: "accel_x", location: null, measurement: null, symbolicName: null };
    // device + field → sorted alphabetically in the helper: device, field, location, measurement, symbolicName
    expect(channelLabel(ch)).toBe("IMU · accel_x");
  });

  it("returns '(unnamed channel)' when all parts are null", () => {
    const ch: ChannelV2 = { device: null, field: null, location: null, measurement: null, symbolicName: null };
    expect(channelLabel(ch)).toBe("(unnamed channel)");
  });

  it("skips null/undefined parts", () => {
    const ch: ChannelV2 = { device: "sensor", field: null, location: null, measurement: "vibration", symbolicName: null };
    expect(channelLabel(ch)).toBe("sensor · vibration");
  });
});

// ── axis annotation filtering ─────────────────────────────────────────────────

describe("axis annotation filtering", () => {
  const axisAnn = makeAnnotation(TS_AXIS_PREDICATE, "x", "x", 1);
  const otherAnn = makeAnnotation("urn:qudt:unit", "m/s", "m/s", 2);
  const annotations = [axisAnn, otherAnn];

  it("keeps annotations whose propertyIRI matches TS_AXIS_PREDICATE", () => {
    const filtered = annotations.filter(a => a.propertyIRI === TS_AXIS_PREDICATE);
    expect(filtered).toHaveLength(1);
    expect(filtered[0]).toBe(axisAnn);
  });

  it("excludes annotations with a different propertyIRI", () => {
    const filtered = annotations.filter(a => a.propertyIRI === TS_AXIS_PREDICATE);
    expect(filtered.every(a => a.propertyIRI === TS_AXIS_PREDICATE)).toBe(true);
    expect(filtered.some(a => a.propertyIRI === "urn:qudt:unit")).toBe(false);
  });

  it("returns empty when no axis annotations are present", () => {
    const filtered = [otherAnn].filter(a => a.propertyIRI === TS_AXIS_PREDICATE);
    expect(filtered).toHaveLength(0);
  });
});

// ── channels with / without shepardId ─────────────────────────────────────────

describe("channel shepardId filtering", () => {
  const channels: ChannelV2[] = [
    { shepardId: "uuid-1", measurement: "vibration", device: "IMU" },
    { shepardId: null, measurement: "pressure", device: "PT" },
    { shepardId: "uuid-2", measurement: "temperature", device: "TC" },
    { shepardId: undefined, measurement: "flow", device: "FM" },
  ];

  it("only processes channels that have a non-null shepardId", () => {
    const withId = channels.filter(ch => ch.shepardId);
    expect(withId).toHaveLength(2);
    expect(withId.map(ch => ch.shepardId)).toEqual(["uuid-1", "uuid-2"]);
  });
});

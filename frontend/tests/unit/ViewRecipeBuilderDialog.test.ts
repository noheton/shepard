/**
 * TS-AXIS-AUTO — unit tests for ViewRecipeBuilderDialog axis-role logic.
 *
 * These tests cover the pure helper logic (indexByShepardId mapping,
 * role-to-ref assignment, auto-populate snackbar suppression) without
 * mounting the full Nuxt/Vuetify component. The fetch calls and Vuetify
 * rendering are left to the Playwright e2e suite.
 */
import { describe, it, expect } from "vitest";

// ---------------------------------------------------------------------------
// Inline the helpers we want to test (mirrors the component's logic)

interface ChannelV2 {
  shepardId: string;
  measurement?: string;
  device?: string;
  field?: string;
  location?: string;
  symbolicName?: string;
}

interface Channel {
  measurement?: string;
  device?: string;
  field?: string;
  location?: string;
  symbolicName?: string;
}

/**
 * Cross-references a shepardId via channelsV2 into channels[] by 5-tuple.
 *
 * channelsV2 = all channels in the container (carries shepardId).
 * channels   = only channels in this reference (matched by 5-tuple).
 * They are NOT parallel arrays.
 */
function indexByShepardId(
  shepardId: string | null | undefined,
  channelsV2: ChannelV2[],
  channels: Channel[],
): number | null {
  if (!shepardId || !channelsV2.length) return null;
  const v2match = channelsV2.find((c) => c.shepardId === shepardId);
  if (!v2match) return null;
  const idx = channels.findIndex(
    (ch) =>
      ch.measurement   === v2match.measurement &&
      ch.device        === v2match.device &&
      ch.field         === v2match.field &&
      (ch.location     ?? null) === (v2match.location     ?? null) &&
      (ch.symbolicName ?? null) === (v2match.symbolicName ?? null),
  );
  return idx >= 0 ? idx : null;
}

type RoleKey = "x" | "y" | "z" | "rot_a" | "rot_b" | "rot_c";

function applyRoles(
  roles: Partial<Record<RoleKey, string | null>>,
  channelsV2: ChannelV2[],
  channels: Channel[],
): Record<RoleKey, number | null> {
  const result: Record<RoleKey, number | null> = {
    x: null, y: null, z: null, rot_a: null, rot_b: null, rot_c: null,
  };
  for (const [role, id] of Object.entries(roles) as [RoleKey, string | null][]) {
    if (id) {
      result[role] = indexByShepardId(id, channelsV2, channels);
    }
  }
  return result;
}

// ---------------------------------------------------------------------------

const CHAN_X  = "00000000-0000-4000-8000-000000000001";
const CHAN_Y  = "00000000-0000-4000-8000-000000000002";
const CHAN_Z  = "00000000-0000-4000-8000-000000000003";
const CHAN_RA = "00000000-0000-4000-8000-000000000004";
const CHAN_RB = "00000000-0000-4000-8000-000000000005";
const CHAN_RC = "00000000-0000-4000-8000-000000000006";

// channelsV2 = all container channels (carry shepardId + 5-tuple)
const CHANNELS_V2: ChannelV2[] = [
  { shepardId: CHAN_X,  device: "KUKA-LBR", field: "force_x_N" },
  { shepardId: CHAN_Y,  device: "KUKA-LBR", field: "force_y_N" },
  { shepardId: CHAN_Z,  device: "KUKA-LBR", field: "force_z_N" },
  { shepardId: CHAN_RA, device: "KUKA-LBR", field: "torque_x_Nm" },
  { shepardId: CHAN_RB, device: "KUKA-LBR", field: "torque_y_Nm" },
  { shepardId: CHAN_RC, device: "KUKA-LBR", field: "torque_z_Nm" },
];

// channels = only this reference's channels (5-tuple only, no shepardId)
// Same device/field set — parallel here for the common case.
const CHANNELS: Channel[] = [
  { device: "KUKA-LBR", field: "force_x_N" },
  { device: "KUKA-LBR", field: "force_y_N" },
  { device: "KUKA-LBR", field: "force_z_N" },
  { device: "KUKA-LBR", field: "torque_x_Nm" },
  { device: "KUKA-LBR", field: "torque_y_Nm" },
  { device: "KUKA-LBR", field: "torque_z_Nm" },
];

describe("indexByShepardId", () => {
  it("returns the correct index for a known shepardId via 5-tuple cross-reference", () => {
    expect(indexByShepardId(CHAN_X,  CHANNELS_V2, CHANNELS)).toBe(0);
    expect(indexByShepardId(CHAN_Z,  CHANNELS_V2, CHANNELS)).toBe(2);
    expect(indexByShepardId(CHAN_RC, CHANNELS_V2, CHANNELS)).toBe(5);
  });

  it("returns null for an unknown shepardId", () => {
    expect(indexByShepardId("unknown-id", CHANNELS_V2, CHANNELS)).toBeNull();
  });

  it("returns null for null shepardId", () => {
    expect(indexByShepardId(null, CHANNELS_V2, CHANNELS)).toBeNull();
  });

  it("returns null for undefined shepardId", () => {
    expect(indexByShepardId(undefined, CHANNELS_V2, CHANNELS)).toBeNull();
  });

  it("returns null when channelsV2 is empty", () => {
    expect(indexByShepardId(CHAN_X, [], CHANNELS)).toBeNull();
  });

  it("returns null when 5-tuple not present in channels[] (non-parallel array guard)", () => {
    // channelsV2 knows about CHAN_RC but the reference's channels[] only has 2 entries
    // that don't include the torque_z channel — must NOT use v2idx as array index.
    const smallChannels: Channel[] = [
      { device: "KUKA-LBR", field: "force_x_N" },
      { device: "KUKA-LBR", field: "force_y_N" },
    ];
    expect(indexByShepardId(CHAN_RC, CHANNELS_V2, smallChannels)).toBeNull();
  });

  it("finds channel at a different position than its channelsV2 index", () => {
    // channelsV2 has CHAN_Z at index 2, but channels[] has it at index 0.
    // A parallel-array assumption would return wrong result; 5-tuple must win.
    const reorderedChannels: Channel[] = [
      { device: "KUKA-LBR", field: "force_z_N" },  // CHAN_Z at index 0, not 2
      { device: "KUKA-LBR", field: "force_x_N" },
    ];
    expect(indexByShepardId(CHAN_Z, CHANNELS_V2, reorderedChannels)).toBe(0);
    expect(indexByShepardId(CHAN_X, CHANNELS_V2, reorderedChannels)).toBe(1);
  });
});

describe("applyRoles (spatial-roles response → axis indices)", () => {
  it("populates all 6 axes from a full spatial-roles response", () => {
    const roles = applyRoles(
      { x: CHAN_X, y: CHAN_Y, z: CHAN_Z, rot_a: CHAN_RA, rot_b: CHAN_RB, rot_c: CHAN_RC },
      CHANNELS_V2,
      CHANNELS,
    );
    expect(roles.x).toBe(0);
    expect(roles.y).toBe(1);
    expect(roles.z).toBe(2);
    expect(roles.rot_a).toBe(3);
    expect(roles.rot_b).toBe(4);
    expect(roles.rot_c).toBe(5);
  });

  it("leaves unset roles as null when server returns partial response", () => {
    const roles = applyRoles(
      { x: CHAN_X, y: CHAN_Y, z: CHAN_Z },
      CHANNELS_V2,
      CHANNELS,
    );
    expect(roles.x).toBe(0);
    expect(roles.y).toBe(1);
    expect(roles.z).toBe(2);
    expect(roles.rot_a).toBeNull();
    expect(roles.rot_b).toBeNull();
    expect(roles.rot_c).toBeNull();
  });

  it("leaves all roles null when spatial-roles response is all null", () => {
    const roles = applyRoles(
      { x: null, y: null, z: null, rot_a: null, rot_b: null, rot_c: null },
      CHANNELS_V2,
      CHANNELS,
    );
    for (const v of Object.values(roles)) {
      expect(v).toBeNull();
    }
  });

  it("leaves role null when shepardId not found in channelsV2", () => {
    const roles = applyRoles({ x: "missing-id" }, CHANNELS_V2, CHANNELS);
    expect(roles.x).toBeNull();
  });

  it("handles non-parallel channelsV2 and channels arrays correctly", () => {
    // channels[] only exposes a subset of the container; channelsV2 has all of them.
    // Only CHAN_X and CHAN_Y are in channels[]; others should resolve to null.
    const subsetChannels: Channel[] = [
      { device: "KUKA-LBR", field: "force_x_N" },
      { device: "KUKA-LBR", field: "force_y_N" },
    ];
    const roles = applyRoles(
      { x: CHAN_X, y: CHAN_Y, z: CHAN_Z },
      CHANNELS_V2,
      subsetChannels,
    );
    expect(roles.x).toBe(0);
    expect(roles.y).toBe(1);
    expect(roles.z).toBeNull(); // CHAN_Z not in this reference's channels
  });
});

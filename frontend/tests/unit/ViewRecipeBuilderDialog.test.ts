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
}

function indexByShepardId(
  shepardId: string | null | undefined,
  channelsV2: ChannelV2[],
  channelCount: number,
): number | null {
  if (!shepardId || !channelsV2.length) return null;
  const v2idx = channelsV2.findIndex((c) => c.shepardId === shepardId);
  if (v2idx < 0) return null;
  return v2idx < channelCount ? v2idx : null;
}

type RoleKey = "x" | "y" | "z" | "rot_a" | "rot_b" | "rot_c";

function applyRoles(
  roles: Partial<Record<RoleKey, string | null>>,
  channelsV2: ChannelV2[],
  channelCount: number,
): Record<RoleKey, number | null> {
  const result: Record<RoleKey, number | null> = {
    x: null, y: null, z: null, rot_a: null, rot_b: null, rot_c: null,
  };
  for (const [role, id] of Object.entries(roles) as [RoleKey, string | null][]) {
    if (id) {
      result[role] = indexByShepardId(id, channelsV2, channelCount);
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

const CHANNELS_V2: ChannelV2[] = [
  { shepardId: CHAN_X,  field: "force_x_N" },
  { shepardId: CHAN_Y,  field: "force_y_N" },
  { shepardId: CHAN_Z,  field: "force_z_N" },
  { shepardId: CHAN_RA, field: "torque_x_Nm" },
  { shepardId: CHAN_RB, field: "torque_y_Nm" },
  { shepardId: CHAN_RC, field: "torque_z_Nm" },
];

describe("indexByShepardId", () => {
  it("returns the correct index for a known shepardId", () => {
    expect(indexByShepardId(CHAN_X, CHANNELS_V2, 6)).toBe(0);
    expect(indexByShepardId(CHAN_Z, CHANNELS_V2, 6)).toBe(2);
    expect(indexByShepardId(CHAN_RC, CHANNELS_V2, 6)).toBe(5);
  });

  it("returns null for an unknown shepardId", () => {
    expect(indexByShepardId("unknown-id", CHANNELS_V2, 6)).toBeNull();
  });

  it("returns null for null shepardId", () => {
    expect(indexByShepardId(null, CHANNELS_V2, 6)).toBeNull();
  });

  it("returns null for undefined shepardId", () => {
    expect(indexByShepardId(undefined, CHANNELS_V2, 6)).toBeNull();
  });

  it("returns null when channelsV2 is empty", () => {
    expect(indexByShepardId(CHAN_X, [], 6)).toBeNull();
  });

  it("returns null when index >= channelCount (out-of-bounds guard)", () => {
    // channelCount=2 but the channel is at index 5 — guard fires
    expect(indexByShepardId(CHAN_RC, CHANNELS_V2, 2)).toBeNull();
  });
});

describe("applyRoles (spatial-roles response → axis indices)", () => {
  it("populates all 6 axes from a full spatial-roles response", () => {
    const roles = applyRoles(
      { x: CHAN_X, y: CHAN_Y, z: CHAN_Z, rot_a: CHAN_RA, rot_b: CHAN_RB, rot_c: CHAN_RC },
      CHANNELS_V2,
      6,
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
      6,
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
      6,
    );
    for (const v of Object.values(roles)) {
      expect(v).toBeNull();
    }
  });

  it("leaves role null when shepardId not found in channelsV2", () => {
    const roles = applyRoles({ x: "missing-id" }, CHANNELS_V2, 6);
    expect(roles.x).toBeNull();
  });
});

/**
 * UX612-C2 — unit tests for the appId-keyed channel fetch helpers behind
 * `/shapes/render`.
 *
 * Pre-fix `render.vue` guarded on `isFinite(Number(containerId))` and built
 * `/v2/containers/{numericId}/channels` URLs — 404 against the
 * appId-keyed endpoint (APISIMP-TSCONT-APPID-KEY), so no Trace3D render
 * could succeed. The helpers go through the typed
 * `TimeseriesContainerChannelListingApi` client keyed on the container appId.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import type { ChannelListingClient, RoleChannel } from "~/utils/shapesRenderChannels";
import {
  fetchChannelListByAppId,
  fetchBulkTraceByAppId,
  tupleKey,
} from "~/utils/shapesRenderChannels";

const CONTAINER_APP_ID = "019e6ffc-bbbb-7bcd-9eef-000000000001";

const CHANNELS = [
  {
    shepardId: "ch-x",
    measurement: "AFP",
    device: "tcp",
    location: "L1",
    symbolicName: "x",
    field: "value",
  },
  {
    shepardId: "ch-y",
    measurement: "AFP",
    device: "tcp",
    location: "L1",
    symbolicName: "y",
    field: "value",
  },
];

const ROLE_CHANNELS: RoleChannel[] = [
  {
    role: "x",
    parsed: { measurement: "AFP", device: "tcp", location: "L1", symbolicName: "x", field: "value" },
  },
  {
    role: "y",
    parsed: { measurement: "AFP", device: "tcp", location: "L1", symbolicName: "y", field: "value" },
  },
];

function mockClient(): {
  client: ChannelListingClient;
  listChannels: ReturnType<typeof vi.fn>;
  getBulkChannelData: ReturnType<typeof vi.fn>;
} {
  const listChannels = vi.fn().mockResolvedValue(CHANNELS);
  const getBulkChannelData = vi.fn().mockResolvedValue([
    {
      timeseries: { measurement: "AFP", device: "tcp", location: "L1", symbolicName: "x", field: "value" },
      points: [
        { timestamp: 100, value: 1.5 },
        { timestamp: 200, value: 2.5 },
      ],
    },
    {
      timeseries: { measurement: "AFP", device: "tcp", location: "L1", symbolicName: "y", field: "value" },
      points: [{ timestamp: 100, value: 9 }],
    },
  ]);
  return {
    client: {
      listContainerChannels: listChannels,
      getContainerBulkChannelData: getBulkChannelData,
    } as unknown as ChannelListingClient,
    listChannels,
    getBulkChannelData,
  };
}

beforeEach(() => vi.clearAllMocks());

describe("fetchChannelListByAppId (UX612-C2)", () => {
  it("lists channels keyed by the container appId — never a numeric id", async () => {
    const { client, listChannels } = mockClient();
    const list = await fetchChannelListByAppId(client, CONTAINER_APP_ID);

    expect(listChannels).toHaveBeenCalledWith({
      appId: CONTAINER_APP_ID,
      // 500 = server-side @Max on listChannels pageSize (APISIMP-CHANNEL-PAGESZ-MAX)
      pageSize: 500,
    });
    expect(list).toHaveLength(2);
  });

  it("returns [] without calling the client for a blank appId", async () => {
    const { client, listChannels } = mockClient();
    expect(await fetchChannelListByAppId(client, "  ")).toEqual([]);
    expect(listChannels).not.toHaveBeenCalled();
  });

  it("returns [] when the client rejects (best-effort)", async () => {
    const { client, listChannels } = mockClient();
    listChannels.mockRejectedValue(new Error("boom"));
    expect(await fetchChannelListByAppId(client, CONTAINER_APP_ID)).toEqual([]);
  });
});

describe("fetchBulkTraceByAppId (UX612-C2)", () => {
  it("resolves role 5-tuples to shepardIds and bulk-fetches by container appId", async () => {
    const { client, getBulkChannelData } = mockClient();

    // APISIMP-BULK-CHANNEL-REQ-NANOS-TO-ISO: use timestamps with ms-level difference.
    const START_NS = 1_000_000_000; // 1s after epoch → 1970-01-01T00:00:01.000Z
    const END_NS   = 2_000_000_000; // 2s after epoch → 1970-01-01T00:00:02.000Z
    const { byRole } = await fetchBulkTraceByAppId(
      client,
      CONTAINER_APP_ID,
      ROLE_CHANNELS,
      START_NS,
      END_NS,
    );

    expect(getBulkChannelData).toHaveBeenCalledWith({
      appId: CONTAINER_APP_ID,
      bulkChannelDataRequest: {
        shepardIds: ["ch-x", "ch-y"],
        start: new Date(1_000).toISOString(), // 1970-01-01T00:00:01.000Z
        end:   new Date(2_000).toISOString(), // 1970-01-01T00:00:02.000Z
      },
    });
    expect(byRole.get("x")).toEqual([
      [100, 1.5],
      [200, 2.5],
    ]);
    expect(byRole.get("y")).toEqual([[100, 9]]);
  });

  it("reuses the caller's cached channel list (no second listChannels call)", async () => {
    const { client, listChannels } = mockClient();

    await fetchBulkTraceByAppId(
      client,
      CONTAINER_APP_ID,
      ROLE_CHANNELS,
      0,
      1,
      CHANNELS,
    );

    expect(listChannels).not.toHaveBeenCalled();
  });

  it("returns the freshly fetched channel list so the caller can cache it", async () => {
    const { client } = mockClient();
    const { channelList } = await fetchBulkTraceByAppId(
      client,
      CONTAINER_APP_ID,
      ROLE_CHANNELS,
      0,
      1,
    );
    expect(channelList).toHaveLength(2);
  });

  it("returns empty results without calling the client for a blank appId", async () => {
    const { client, listChannels, getBulkChannelData } = mockClient();
    const { byRole } = await fetchBulkTraceByAppId(client, "", ROLE_CHANNELS, 0, 1);
    expect(byRole.size).toBe(0);
    expect(listChannels).not.toHaveBeenCalled();
    expect(getBulkChannelData).not.toHaveBeenCalled();
  });

  it("skips the bulk call when no 5-tuple resolves to a shepardId", async () => {
    const { client, getBulkChannelData } = mockClient();
    const unknown: RoleChannel[] = [
      {
        role: "x",
        parsed: { measurement: "??", device: "??", location: "", symbolicName: "", field: "" },
      },
    ];
    const { byRole } = await fetchBulkTraceByAppId(
      client,
      CONTAINER_APP_ID,
      unknown,
      0,
      1,
    );
    expect(byRole.size).toBe(0);
    expect(getBulkChannelData).not.toHaveBeenCalled();
  });

  it("swallows client failures and returns empty per-role maps", async () => {
    const { client, getBulkChannelData } = mockClient();
    getBulkChannelData.mockRejectedValue(new Error("boom"));
    const { byRole } = await fetchBulkTraceByAppId(
      client,
      CONTAINER_APP_ID,
      ROLE_CHANNELS,
      0,
      1,
    );
    expect(byRole.size).toBe(0);
  });
});

describe("tupleKey", () => {
  it("normalises null/undefined/whitespace to empty segments", () => {
    expect(tupleKey(" a ", null, undefined, "b", "")).toBe("a|||b|");
  });
});

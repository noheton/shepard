/**
 * UX612-C2 — appId-keyed channel fetches for the /shapes/render page.
 *
 * `GET /v2/timeseries-containers/{containerAppId}/channels` and the bulk
 * data endpoint are keyed on the container *appId*
 * (APISIMP-TSCONT-APPID-KEY). The render page previously guarded on
 * `isFinite(Number(containerId))` and built numeric-keyed URLs, so every
 * Trace3D render 404ed with an error blaming the user's container id.
 *
 * These helpers take the typed `TimeseriesContainerChannelListingApi`
 * client (resolved by the caller via `useV2ShepardApi` during setup) as a
 * structural interface so the logic stays unit-testable with a mock.
 */
import type {
  TimeseriesChannelV2,
  TimeseriesWithDataPoints,
} from "@dlr-shepard/backend-client";

export interface ChannelTuple5 {
  measurement: string;
  device: string;
  location: string;
  symbolicName: string;
  field: string;
}

export interface RoleChannel {
  role: string;
  parsed: ChannelTuple5;
}

/**
 * Loose channel shape shared with the picker components' `ChannelV2` —
 * the generated `TimeseriesChannelV2` (all fields required) is a structural
 * subtype, so both flow through these helpers.
 */
export interface ChannelLike {
  shepardId: string;
  measurement?: string;
  device?: string;
  location?: string;
  symbolicName?: string;
  field?: string;
}

/** Structural subset of the generated TimeseriesContainerChannelListingApi. */
export interface ChannelListingClient {
  listChannels(req: {
    containerAppId: string;
    size?: number;
  }): Promise<Array<TimeseriesChannelV2>>;
  getBulkChannelData(req: {
    containerAppId: string;
    bulkChannelDataRequest: { shepardIds: string[]; start: number; end: number };
  }): Promise<Array<TimeseriesWithDataPoints>>;
}

const norm = (s: string | null | undefined) => (s ?? "").trim();

export function tupleKey(
  m: string | null | undefined,
  d: string | null | undefined,
  l: string | null | undefined,
  sn: string | null | undefined,
  f: string | null | undefined,
): string {
  return `${norm(m)}|${norm(d)}|${norm(l)}|${norm(sn)}|${norm(f)}`;
}

/**
 * List a container's channels by appId. Returns [] on failure (best-effort —
 * callers fall back to no auto-populate, matching the previous behaviour).
 */
export async function fetchChannelListByAppId(
  api: ChannelListingClient,
  containerAppId: string,
): Promise<Array<TimeseriesChannelV2>> {
  const appId = containerAppId.trim();
  if (!appId) return [];
  try {
    return await api.listChannels({ containerAppId: appId, size: 1000 });
  } catch {
    return [];
  }
}

/**
 * Bulk-fetch data points for the given role-bound 5-tuples (TS-OPT2), keyed
 * by container appId. Resolves each role's 5-tuple to its channel shepardId
 * via `channelList` (fetched here when the caller's cache is empty), then
 * maps the response entries back to roles.
 *
 * Returns the per-role point arrays plus the (possibly freshly fetched)
 * channel list so the caller can cache it.
 */
export async function fetchBulkTraceByAppId(
  api: ChannelListingClient,
  containerAppId: string,
  channels: RoleChannel[],
  startNs: number,
  endNs: number,
  cachedChannelList: Array<ChannelLike> = [],
): Promise<{
  byRole: Map<string, [number, number][]>;
  channelList: Array<ChannelLike>;
}> {
  const byRole = new Map<string, [number, number][]>();
  const appId = containerAppId.trim();
  if (!appId || channels.length === 0)
    return { byRole, channelList: cachedChannelList };

  let channelList: Array<ChannelLike> = cachedChannelList;
  try {
    if (channelList.length === 0) {
      channelList = await fetchChannelListByAppId(api, appId);
      if (channelList.length === 0) return { byRole, channelList };
    }

    const tupleToShepardId = new Map<string, string>();
    for (const ch of channelList) {
      tupleToShepardId.set(
        tupleKey(ch.measurement, ch.device, ch.location, ch.symbolicName, ch.field),
        ch.shepardId,
      );
    }

    const shepardIdToRole = new Map<string, string>();
    const shepardIds: string[] = [];
    for (const { role, parsed } of channels) {
      const key = tupleKey(
        parsed.measurement,
        parsed.device,
        parsed.location,
        parsed.symbolicName,
        parsed.field,
      );
      const shepardId = tupleToShepardId.get(key);
      if (shepardId) {
        shepardIds.push(shepardId);
        shepardIdToRole.set(shepardId, role);
      }
    }
    if (shepardIds.length === 0) return { byRole, channelList };

    const body = await api.getBulkChannelData({
      containerAppId: appId,
      bulkChannelDataRequest: { shepardIds, start: startNs, end: endNs },
    });

    for (const entry of body) {
      const ts = entry.timeseries;
      const key = tupleKey(
        ts.measurement,
        ts.device,
        ts.location,
        ts.symbolicName,
        ts.field,
      );
      const shepardId = tupleToShepardId.get(key);
      const role = shepardId ? shepardIdToRole.get(shepardId) : undefined;
      if (role) {
        byRole.set(
          role,
          (entry.points ?? []).map(
            p => [p.timestamp ?? 0, p.value as number] as [number, number],
          ),
        );
      }
    }
  } catch {
    /* swallow — caller sees missing keys as empty arrays */
  }
  return { byRole, channelList };
}

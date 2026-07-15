<script setup lang="ts">
/**
 * UX-PIN1 — Pinned channel tile for PersonalDigest.
 *
 * Shows the channel's last known value (fetched once from the last 60 s of
 * data), a compact sparkline of the last minute, and an unpin button.
 * Clicking the tile navigates to the source container.
 *
 * The tile intentionally stays simple — "just show me the values" for
 * shop-floor IME use.  Live polling is deferred to UX-PIN1b.
 */
import type { PinnedChannel } from "~/composables/container/usePinnedChannels";
import type { TimeseriesSeries } from "~/components/common/chart/types";
import { nsToIso } from "~/composables/containers/useCrossDoBulkData";

const props = defineProps<{
  channel: PinnedChannel;
}>();

const emit = defineEmits<{
  (e: "unpin", shepardId: string): void;
}>();

// ── Fetch last 60 s of data via the v2 shepardId endpoint ────────────────────

const { public: publicConfig } = useRuntimeConfig();
const { data: authData } = useAuth();

function v2Base(): string {
  const explicit = publicConfig.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit;
  return (publicConfig.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
}

function authHeaders(): Record<string, string> {
  const token = authData.value?.accessToken;
  return token ? { Authorization: `Bearer ${token}` } : {};
}

interface DataPoint {
  timestamp: number;
  value: number;
}

const loading = ref(true);
const error = ref(false);
const points = ref<DataPoint[]>([]);

const lastValue = computed<number | null>(() => {
  if (!points.value.length) return null;
  return points.value[points.value.length - 1]!.value;
});

const trend = computed<"up" | "down" | "flat">(() => {
  if (points.value.length < 2) return "flat";
  const first = points.value[0]!.value;
  const last = points.value[points.value.length - 1]!.value;
  const delta = last - first;
  const threshold = Math.abs(first) * 0.02; // 2 % change = not flat
  if (delta > threshold) return "up";
  if (delta < -threshold) return "down";
  return "flat";
});

const trendIcon = computed(() => {
  if (trend.value === "up") return "mdi-trending-up";
  if (trend.value === "down") return "mdi-trending-down";
  return "mdi-trending-neutral";
});

const trendColor = computed(() => {
  if (trend.value === "up") return "success";
  if (trend.value === "down") return "error";
  return "medium-emphasis";
});

/** Compact display of the last value with up to 4 significant figures. */
function fmtValue(v: number): string {
  if (!isFinite(v)) return "—";
  if (Math.abs(v) >= 10_000 || (Math.abs(v) > 0 && Math.abs(v) < 0.001)) {
    return v.toExponential(2);
  }
  return v.toPrecision(4).replace(/\.?0+$/, "");
}

const displayValue = computed<string>(() =>
  lastValue.value !== null ? fmtValue(lastValue.value) : "—",
);

// Sparkline series for the mini chart
const sparklineSeries = computed<TimeseriesSeries[]>(() => {
  if (!points.value.length) return [];
  return [
    {
      name: props.channel.channelName,
      data: points.value.map(p => [p.timestamp, p.value] as [number, number]),
      color: "#4097CC",
    },
  ];
});

async function fetchLatest() {
  loading.value = true;
  error.value = false;
  try {
    const endNs = Date.now() * 1_000_000; // ms → ns
    const startNs = endNs - 60_000 * 1_000_000; // 60 s window
    // APISIMP-CONT-NS-COLLAPSE-2: migrated from /v2/timeseries-containers/{id}/channels/...
    // to /v2/containers/{appId}/channels/... (containerAppId is the appId; fall back
    // to containerId for pins stored before the appId migration).
    const containerKey = props.channel.containerAppId ?? props.channel.containerId;
    const url =
      `${v2Base()}/v2/containers/${containerKey}` +
      `/channels/${props.channel.shepardId}/data` +
      `?start=${nsToIso(startNs)}&end=${nsToIso(endNs)}&downsample=lttb&maxPoints=60`;
    const res = await fetch(url, { headers: authHeaders() });
    if (!res.ok) {
      error.value = true;
      return;
    }
    const body: { points?: DataPoint[] } = await res.json();
    points.value = body.points ?? [];
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

onMounted(fetchLatest);

// Navigate to source container when the tile body is clicked.
const router = useRouter();
function goToSource() {
  if (props.channel.containerPath) {
    router.push(props.channel.containerPath);
  }
}
</script>

<template>
  <v-card
    variant="outlined"
    rounded="lg"
    class="pinned-channel-tile"
    :class="{ 'cursor-pointer': !!channel.containerPath }"
    @click="goToSource"
  >
    <!-- Header row: channel name + unpin button -->
    <div class="d-flex align-center justify-space-between pa-3 pb-0">
      <span
        class="text-body-2 font-weight-medium text-truncate"
        style="max-width: calc(100% - 32px)"
        :title="channel.channelName"
      >
        {{ channel.channelName }}
      </span>
      <v-btn
        icon
        variant="text"
        size="x-small"
        density="compact"
        :title="`Unpin ${channel.channelName}`"
        @click.stop="emit('unpin', channel.shepardId)"
      >
        <v-icon size="14">mdi-pin-off</v-icon>
      </v-btn>
    </div>

    <!-- Value + trend chip -->
    <div class="d-flex align-center ga-2 px-3 pb-1 pt-1">
      <span v-if="loading" class="text-h6 text-medium-emphasis">
        <v-progress-circular indeterminate size="18" />
      </span>
      <span v-else-if="error" class="text-caption text-error">unavailable</span>
      <template v-else>
        <span class="text-h6 font-weight-bold font-tabular-nums">
          {{ displayValue }}
        </span>
        <v-icon :color="trendColor" size="18" :icon="trendIcon" />
      </template>
    </div>

    <!-- Sparkline — 60 s mini chart -->
    <div class="px-2 pb-2">
      <div
        v-if="loading"
        class="d-flex align-center justify-center"
        style="height: 60px"
      >
        <v-progress-circular indeterminate size="16" />
      </div>
      <div
        v-else-if="!sparklineSeries.length || error"
        class="d-flex align-center justify-center text-caption text-medium-emphasis"
        style="height: 60px"
      >
        No data (last 60 s)
      </div>
      <ClientOnly v-else>
        <TimeseriesChart
          :series="sparklineSeries"
          height="60px"
          :show-legend="false"
        />
      </ClientOnly>
    </div>

    <!-- Navigate-hint footer (only when containerPath provided) -->
    <div
      v-if="channel.containerPath"
      class="px-3 pb-2 text-caption text-medium-emphasis d-flex align-center ga-1"
    >
      <v-icon size="12">mdi-open-in-new</v-icon>
      Open container
    </div>
  </v-card>
</template>

<style scoped>
.pinned-channel-tile {
  transition: box-shadow 0.15s;
  user-select: none;
}
.pinned-channel-tile.cursor-pointer:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}
.font-tabular-nums {
  font-variant-numeric: tabular-nums;
}
</style>

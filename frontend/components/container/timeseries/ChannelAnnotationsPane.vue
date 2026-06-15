<script setup lang="ts">
// TS-SEMANTIC-REST — channel-level semantic annotations pane on the
// TimeseriesContainer detail page.
//
// Replaces the previous PlaceholderFragmentPane stub (slug
// `ts-channel-annotations`). Lists every channel in the container with its
// TS-SEMANTIC-REST annotations, plus per-channel ADD / per-row DELETE
// affordances.
//
// Distinct from the per-row annotation section inside
// TimeseriesMeasurementsTable, which still uses the legacy numeric-id-keyed
// AnnotatedTimeseries (upstream `/shepard/api/...`). This pane uses the
// shepardId-keyed v2 endpoint
// `/v2/containers/{containerAppId}/channels/{channelShepardId}/annotations`
// (TimeseriesChannelAnnotationRest) which is the post-TS-CORE-SCHEMA-01 contract.
//
// Channels without a backing AnnotatableTimeseries node (pre-TS-SEMANTIC-01
// data) return 200/[] from GET and 404 from POST; the AnnotatedChannel class
// in `composables/annotated.ts` swallows the GET 404 → empty so this pane
// shows "no annotations yet" rather than an error.
//
// Backlog: TS-SEMANTIC-REST (placeholder slug `ts-channel-annotations`).

import { useFetchV2Channels } from "~/composables/container/useFetchV2Channels";
import { AnnotatedChannel } from "~/composables/annotated";

const props = defineProps<{
  containerAppId: string;
  /** Container measurements (same shape as TimeseriesContainerAccessor.measurements). */
  measurements: Array<{
    measurement?: string | null;
    device?: string | null;
    location?: string | null;
    symbolicName?: string | null;
    field?: string | null;
  }>;
  isAllowedToEditData: boolean;
}>();

const { channelMap, loading: channelMapLoading, resolveShepardId } =
  useFetchV2Channels(props.containerAppId);

interface ChannelRow {
  shepardId: string;
  label: string;
}

function labelFor(ch: {
  measurement?: string | null;
  device?: string | null;
  location?: string | null;
  symbolicName?: string | null;
  field?: string | null;
}): string {
  const parts = [ch.device, ch.field, ch.location, ch.measurement, ch.symbolicName].filter(Boolean);
  return parts.length ? parts.join(" · ") : "(unnamed channel)";
}

// Wire channels to shepardIds via the v2 map. Channels without a shepardId
// (pre-TS-SEMANTIC-01 or v2 fetch failed) are dropped — they cannot be
// addressed by the TS-SEMANTIC-REST endpoint.
const channelRows = computed<ChannelRow[]>(() => {
  const out: ChannelRow[] = [];
  for (const ch of props.measurements) {
    const shepardId = resolveShepardId(
      ch.measurement,
      ch.device,
      ch.location,
      ch.symbolicName,
      ch.field,
    );
    if (!shepardId) continue;
    out.push({ shepardId, label: labelFor(ch) });
  }
  return out.sort((a, b) => a.label.localeCompare(b.label));
});

// Channels in the measurements list that did NOT resolve to a shepardId.
// We surface a short note rather than silently hiding them.
const unresolvableCount = computed(
  () => props.measurements.length - channelRows.value.length,
);
</script>

<template>
  <div data-testid="channel-annotations-pane">
    <div v-if="channelMapLoading" class="pa-4 text-medium-emphasis text-body-2">
      Loading channels…
    </div>
    <div
      v-else-if="channelMap.size === 0"
      class="pa-4 text-medium-emphasis text-body-2"
    >
      No channels have been registered with the v2 channel registry yet.
      Channels created before the TS-SEMANTIC-01 dual-write shipped are
      not addressable by shepardId; upload a new measurement to populate
      the registry.
    </div>
    <div v-else>
      <div
        v-if="unresolvableCount > 0"
        class="pa-2 text-caption text-medium-emphasis"
      >
        {{ unresolvableCount }} channel{{ unresolvableCount === 1 ? "" : "s" }}
        lack a shepardId and are not shown (pre-TS-SEMANTIC-01 channels).
      </div>
      <v-list density="compact" class="pa-0">
        <v-list-item
          v-for="row in channelRows"
          :key="row.shepardId"
          class="channel-row align-start"
        >
          <template #title>
            <div class="d-flex align-center ga-2 flex-wrap">
              <span class="font-weight-medium">{{ row.label }}</span>
              <code class="text-caption text-medium-emphasis">{{ row.shepardId }}</code>
            </div>
          </template>
          <template #subtitle>
            <div class="d-flex align-start ga-2 mt-1 flex-wrap">
              <SemanticAnnotationList
                :annotated="new AnnotatedChannel(containerAppId, row.shepardId)"
                :can-delete="isAllowedToEditData"
              />
            </div>
          </template>
          <template #append>
            <AddAnnotationButton
              v-if="isAllowedToEditData"
              :annotated="new AnnotatedChannel(containerAppId, row.shepardId)"
              button-icon="mdi-plus-circle"
              button-text="ADD"
            />
          </template>
        </v-list-item>
      </v-list>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.channel-row {
  border-bottom: 1px solid rgba(var(--v-border-color), 0.12);
}
.channel-row:last-child {
  border-bottom: none;
}
</style>

<script lang="ts" setup>
import type {
  SemanticAnnotation,
  TimeseriesEntity,
} from "@dlr-shepard/backend-client";
import AddAnnotationButton from "~/components/common/button/AddAnnotationButton.vue";
import { usePinnedChannels } from "~/composables/container/usePinnedChannels";
import { useFetchV2Channels } from "~/composables/container/useFetchV2Channels";

interface AnnotatedTimeseries extends TimeseriesEntity {
  annotations: Ref<SemanticAnnotation[] | null>;
}

const props = defineProps<{
  measurements: TimeseriesEntity[];
  containerId: number;
  containerAppId: string;
  isAllowedToEditData: boolean;
  /** Optional: absolute path to this container's page, stored with pins for navigation. */
  containerPath?: string;
}>();

// UX-PIN1 — pin/unpin support
const { pin, unpin, isPinned } = usePinnedChannels();
const { resolveShepardId } = useFetchV2Channels(props.containerAppId);

/**
 * Human-readable label for a channel, matching the format used in
 * ChannelPreviewChart and the container page's `channelLabel()` helper.
 */
function channelLabel(ch: TimeseriesEntity): string {
  const parts = [ch.device, ch.field, ch.location, ch.measurement, ch.symbolicName].filter(Boolean);
  return parts.length ? parts.join(" · ") : "(unnamed channel)";
}

function togglePin(ch: TimeseriesEntity) {
  const shepardId = resolveShepardId(
    ch.measurement, ch.device, ch.location, ch.symbolicName, ch.field,
  );
  if (!shepardId) return; // v2 map not yet ready or channel not found

  if (isPinned(shepardId)) {
    unpin(shepardId);
  } else {
    pin({
      shepardId,
      containerId: props.containerId,
      channelName: channelLabel(ch),
      containerPath: props.containerPath,
    });
  }
}

function channelShepardId(ch: TimeseriesEntity): string | null {
  return resolveShepardId(ch.measurement, ch.device, ch.location, ch.symbolicName, ch.field);
}

const am: Ref<AnnotatedTimeseries[]> = computed(() =>
  props.measurements.map(value => ({
    ...value,
    annotations: ref(null),
  })),
);

// Column widths sized to content — pre-fix the table sprawled with
// equal columns because the v-data-table defaults to "flex auto"
// everywhere. Narrow ID + Field; the wider string columns absorb the
// rest. Improves density on the TimeseriesReference page where this
// table is the main content (user feedback 2026-05-19).
// The "pin" action column (UX-PIN1) is narrow and right-aligned.
const headers = [
  { key: "data-table-expand", width: "32px" },
  { title: "ID", key: "id", sortable: true, width: "60px" },
  { title: "Measurement", key: "measurement", sortable: true },
  { title: "Device", key: "device", sortable: true },
  { title: "Location", key: "location", sortable: true },
  { title: "Symbolic Name", key: "symbolicName", sortable: true },
  { title: "Field", key: "field", sortable: true, width: "100px" },
  { key: "pin-action", width: "40px", sortable: false },
];

const itemsPerPage = 10;
</script>

<template>
  <DataTable
    :cell-props="{ class: 'text-textbody1' }"
    :header-props="{ class: 'text-subtitle-2 text-textbody1' }"
    :headers="headers"
    :items-for-pagination="am"
    :items-per-page="itemsPerPage"
    item-value="id"
    show-expand
  >
    <!-- UX-PIN1: pin/unpin button in every non-expanded row -->
    <template #[`item.pin-action`]="{ item }">
      <v-btn
        :icon="channelShepardId(item) && isPinned(channelShepardId(item)!) ? 'mdi-pin' : 'mdi-pin-outline'"
        :color="channelShepardId(item) && isPinned(channelShepardId(item)!) ? 'primary' : undefined"
        variant="text"
        size="x-small"
        density="compact"
        :title="channelShepardId(item) && isPinned(channelShepardId(item)!) ? 'Unpin channel from home page' : 'Pin channel to home page'"
        :disabled="!channelShepardId(item)"
        @click.stop="togglePin(item)"
      />
    </template>

    <template #[`expanded-row`]="{ item }">
      <tr class="expanded">
        <td :colspan="headers.length">
          <!-- inline channel chart -->
          <ChannelPreviewChart
            :channel="item"
            :container-app-id="containerAppId"
            :channel-shepard-id="channelShepardId(item)"
          />

          <!-- annotations -->
          <v-table>
            <tbody>
              <tr class="semantic-row">
                <td
                  v-if="item.annotations.value && item.annotations.value.length > 0"
                >
                  <strong>Semantic Annotations:</strong>
                </td>
                <td
                  v-else-if="
                    item.annotations.value &&
                    item.annotations.value.length === 0
                  "
                >
                  <strong>No semantic annotations.</strong>
                </td>
                <td class="annotation-list">
                  <SemanticAnnotationList
                    :annotated="new AnnotatedTimeseries(item)"
                    :can-delete="isAllowedToEditData"
                    @annotations="annotations => (item.annotations.value = annotations)"
                  />
                </td>
                <td class="action-buttons">
                  <!-- Set unit: pre-filters the annotation dialog to QUDT
                       vocabulary terms so the user assigns a physical unit in
                       one click. Pre-fills from symbolicName for best UX. UI18 -->
                  <AddAnnotationButton
                    v-if="isAllowedToEditData"
                    :annotated="new AnnotatedTimeseries(item)"
                    :prefill="item.symbolicName ?? undefined"
                    filter-vocab="qudt"
                    button-icon="mdi-ruler"
                    button-text="SET UNIT"
                  />
                  <AddAnnotationButton
                    v-if="isAllowedToEditData"
                    :annotated="new AnnotatedTimeseries(item)"
                    :prefill="item.symbolicName ?? undefined"
                  />
                </td>
              </tr>
            </tbody>
          </v-table>
        </td>
      </tr>
    </template>
  </DataTable>
</template>

<style lang="scss" scoped>
.v-table {
  background-color: inherit;
}

.semantic-row {
  strong {
    font-weight: 500;
    font-size: 16px;
    line-height: 28px;
    white-space: nowrap;
  }

  .annotation-list {
    width: 100%;
  }
}

:deep(tr:has(+ tr.expanded) > td) {
  border-bottom: 0 !important;
}

:deep(tr:where(tr:has(+ tr.expanded:hover))) {
  background-color: rgb(var(--v-theme-focus1));
}

:deep(tr:hover + tr.expanded) {
  background-color: rgb(var(--v-theme-focus1));
}

strong {
  display: block;
  font-weight: 500;
  font-size: 16px;
  line-height: 28px;
  white-space: nowrap;
}
</style>

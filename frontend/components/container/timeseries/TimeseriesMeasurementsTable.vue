<script lang="ts" setup>
import type {
  SemanticAnnotation,
  TimeseriesEntity,
} from "@dlr-shepard/backend-client";
import AddAnnotationButton from "~/components/common/button/AddAnnotationButton.vue";

interface AnnotatedTimeseries extends TimeseriesEntity {
  annotations: Ref<SemanticAnnotation[] | null>;
}

const props = defineProps<{
  measurements: TimeseriesEntity[];
  containerId: number;
  isAllowedToEditData: boolean;
}>();

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
const headers = [
  { key: "data-table-expand", width: "32px" },
  { title: "ID", key: "id", sortable: true, width: "60px" },
  { title: "Measurement", key: "measurement", sortable: true },
  { title: "Device", key: "device", sortable: true },
  { title: "Location", key: "location", sortable: true },
  { title: "Symbolic Name", key: "symbolicName", sortable: true },
  { title: "Field", key: "field", sortable: true, width: "100px" },
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
    <template #[`expanded-row`]="{ item }">
      <tr class="expanded">
        <td :colspan="headers.length">
          <!-- inline channel chart -->
          <ChannelPreviewChart
            :channel="item"
            :container-id="containerId"
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
                <td>
                  <AddAnnotationButton
                    v-if="isAllowedToEditData"
                    :annotated="new AnnotatedTimeseries(item)"
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

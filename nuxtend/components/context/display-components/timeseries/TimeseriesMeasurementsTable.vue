<script lang="ts" setup>
import type {
  SemanticAnnotation,
  TimeseriesEntity,
} from "@dlr-shepard/backend-client";
import AddAnnotationButton from "~/components/common/AddAnnotationButton.vue";

interface AnnotatedTimeseries extends TimeseriesEntity {
  annotations: Ref<SemanticAnnotation[] | null>;
}

const props = defineProps<{
  measurements: TimeseriesEntity[];
  isAllowedToEditData: boolean;
}>();

const am: Ref<AnnotatedTimeseries[]> = computed(() =>
  props.measurements.map(value => {
    return {
      ...value,
      annotations: ref(null),
    };
  }),
);

const columns = ref([
  { key: "data-table-expand" },
  { title: "ID", key: "id", sortable: true },
  { title: "Measurement", key: "measurement", sortable: true },
  { title: "Device", key: "device", sortable: true },
  { title: "Location", key: "location", sortable: true },
  { title: "Symbolic Name", key: "symbolicName", sortable: true },
  { title: "Field", key: "field", sortable: true },
]);
</script>

<template>
  <DataTable
    :cell-props="{
      class: 'text-textbody1',
    }"
    :header-props="{
      class: 'text-subtitle-2 text-textbody1',
    }"
    :headers="columns"
    :items="am"
    hover
    item-value="id"
    show-expand
  >
    <template #[`expanded-row`]="{ item }">
      <tr>
        <td :colspan="columns.length">
          <v-table>
            <tbody>
              <tr class="semantic-row">
                <td
                  v-if="
                    item.annotations.value && item.annotations.value.length > 0
                  "
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
                    @annotations="
                      annotations => (item.annotations.value = annotations)
                    "
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
    <template #bottom>
      <v-divider :thickness="8" color="divider2" opacity="1" />
      <v-pagination :total-visible="6" />
    </template>
  </DataTable>
</template>

<style lang="scss" scoped>
.v-table {
  :deep(thead) > tr > th {
    background-color: rgb(var(--v-theme-divider2));
  }
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

strong {
  display: block;
  font-weight: 500;
  font-size: 16px;
  line-height: 28px;
  white-space: nowrap;
}
</style>

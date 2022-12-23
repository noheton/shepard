<script setup lang="ts">
import type { FilterChangedData, FilterOptions } from "@/utils/helpers";
import { ref, watch, type PropType } from "vue";

const emit = defineEmits(["filter-changed"]);

const props = defineProps({
  maxObjects: {
    type: Number,
    required: true,
  },
  currentPage: {
    type: Number,
    default: 1,
  },
  filterOptions: {
    type: Object as PropType<FilterOptions>,
    default() {
      return {
        perPage: 10,
        orderBy: "createdAt",
        descending: false,
      };
    },
  },
});

const currentPage = ref(props.currentPage);
const perPage = ref(props.filterOptions.perPage);
const sizeOptions = [
  { value: 10, text: "10" },
  { value: 25, text: "25" },
  { value: 50, text: "50" },
  { value: 100, text: "100" },
];
const orderBy = ref(props.filterOptions.orderBy);
const orderByOptions = [
  { value: "createdAt", text: "Created At" },
  { value: "updatedAt", text: "Updated At" },
  { value: "name", text: "Name" },
];
const descending = ref(props.filterOptions.descending);
const descendingOptions = [
  { value: false, text: "Ascending" },
  { value: true, text: "Descending" },
];

watch(
  () => props.currentPage,
  page => {
    currentPage.value = page;
  },
);

function update() {
  const options: FilterChangedData = {
    currentPage: currentPage.value,
    perPage: perPage.value,
    orderBy: orderBy.value,
    descending: descending.value,
  };
  emit("filter-changed", options);
}
function updatePage(nextPage: number) {
  currentPage.value = nextPage;
  update();
}
function updateSize(nextSize: number) {
  perPage.value = nextSize;
  update();
}
function updateOrderBy(nextOrderBy: string) {
  orderBy.value = nextOrderBy;
  update();
}
function updateDescending(nextDescending: boolean) {
  descending.value = nextDescending;
  update();
}
</script>

<template>
  <div>
    <b-row>
      <b-col>
        <b-pagination
          v-model="currentPage"
          class="float-left w-auto"
          :total-rows="maxObjects"
          :per-page="perPage"
          @change="updatePage($event)"
        ></b-pagination>
        <b-form-select
          v-model="descending"
          class="ml-3 float-right w-auto"
          :options="descendingOptions"
          @change="updateDescending($event)"
        ></b-form-select>
        <b-form-select
          v-model="orderBy"
          class="ml-3 float-right w-auto"
          :options="orderByOptions"
          @change="updateOrderBy($event)"
        ></b-form-select>
        <b-form-select
          v-model="perPage"
          class="ml-3 float-right w-auto"
          :options="sizeOptions"
          @change="updateSize($event)"
        ></b-form-select>
      </b-col>
    </b-row>
  </div>
</template>

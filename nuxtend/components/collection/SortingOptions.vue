<script setup lang="ts">
import type { FilterChangedData, FilterOptions } from "~/utils/helpers";

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

// We only need the initial values here
const curPage = ref(props.currentPage);
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
    curPage.value = page;
  },
);

function update() {
  const options: FilterChangedData = {
    currentPage: curPage.value,
    perPage: perPage.value,
    orderBy: orderBy.value,
    descending: descending.value,
  };
  emit("filter-changed", options);
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
  <div
    :style="{
      display: 'flex',
      width: '50%',
      justifyContent: 'space-evenly',
      alignSelf: 'flex-end',
    }"
  >
    <v-select
      v-model="perPage"
      :items="sizeOptions"
      item-title="text"
      @change="updateSize($event)"
    />
    <v-select
      v-model="orderBy"
      class="ml-3 float-right w-auto"
      :items="orderByOptions"
      item-title="text"
      @change="updateOrderBy($event)"
    />
    <v-select
      v-model="descending"
      class="ml-3 float-right w-auto"
      :items="descendingOptions"
      item-title="text"
      @change="updateDescending($event)"
    />
  </div>
</template>

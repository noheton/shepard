<script setup lang="ts">
import type { FilterChangedData } from "@/utils/helpers";
import { ref, watch } from "vue";

const emit = defineEmits(["filter-changed"]);

const props = defineProps({
  maxObjects: {
    type: Number,
    required: true,
  },
  defaultPage: {
    type: Number,
    default: 1,
  },
  defaultSize: {
    type: Number,
    default: 10,
  },
  defaultOrderBy: {
    type: String,
    default: "createdAt",
  },
  defaultDescending: {
    type: Boolean,
    default: false,
  },
});

const currentPage = ref(props.defaultPage);
const currentSize = ref(props.defaultSize);
const sizeOptions = [
  { value: 10, text: "10" },
  { value: 25, text: "25" },
  { value: 50, text: "50" },
  { value: 100, text: "100" },
];
const orderBy = ref(props.defaultOrderBy);
const orderByOptions = [
  { value: "createdAt", text: "Created At" },
  { value: "updatedAt", text: "Updated At" },
  { value: "name", text: "Name" },
];
const descending = ref(props.defaultDescending);
const descendingOptions = [
  { value: false, text: "Ascending" },
  { value: true, text: "Descending" },
];

watch(
  () => props.defaultPage,
  defaultPage => {
    currentPage.value = defaultPage;
  },
);

function update() {
  const options: FilterChangedData = {
    currentPage: currentPage.value,
    currentSize: currentSize.value,
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
  currentSize.value = nextSize;
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
          :per-page="currentSize"
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
          v-model="currentSize"
          class="ml-3 float-right w-auto"
          :options="sizeOptions"
          @change="updateSize($event)"
        ></b-form-select>
      </b-col>
    </b-row>
  </div>
</template>

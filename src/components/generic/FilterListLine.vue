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

<script lang="ts">
import { defineComponent } from "vue";

export interface FilterChangedData {
  currentPage: number;
  currentSize: number;
  orderBy: string;
  descending: boolean;
}

interface FilterListLineData {
  currentPage: number;
  currentSize: number;
  orderBy: string;
  descending: boolean;
  sizeOptions: Array<{ value: number; text: string }>;
  orderByOptions: Array<{ value: string; text: string }>;
  descendingOptions: Array<{ value: boolean; text: string }>;
}

export default defineComponent({
  props: {
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
  },
  emits: ["filter-changed"],
  data() {
    return {
      currentPage: this.defaultPage,
      currentSize: this.defaultSize,
      sizeOptions: [
        { value: 10, text: "10" },
        { value: 25, text: "25" },
        { value: 50, text: "50" },
        { value: 100, text: "100" },
      ],
      orderBy: this.defaultOrderBy,
      orderByOptions: [
        { value: "createdAt", text: "Created At" },
        { value: "updatedAt", text: "Updated At" },
        { value: "name", text: "Name" },
      ],
      descending: this.defaultDescending,
      descendingOptions: [
        { value: false, text: "Ascending" },
        { value: true, text: "Descending" },
      ],
    } as FilterListLineData;
  },
  watch: {
    defaultPage() {
      if (this.currentPage != this.defaultPage)
        this.currentPage = this.defaultPage;
    },
  },
  methods: {
    update() {
      const options: FilterChangedData = {
        currentPage: this.currentPage,
        currentSize: this.currentSize,
        orderBy: this.orderBy,
        descending: this.descending,
      };
      this.$emit("filter-changed", options);
    },
    updatePage(nextPage: number) {
      this.currentPage = nextPage;
      this.update();
    },
    updateSize(nextSize: number) {
      this.currentSize = nextSize;
      this.update();
    },
    updateOrderBy(nextOrderBy: string) {
      this.orderBy = nextOrderBy;
      this.update();
    },
    updateDescending(nextDescending: boolean) {
      this.descending = nextDescending;
      this.update();
    },
  },
});
</script>

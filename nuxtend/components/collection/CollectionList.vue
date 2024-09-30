<script setup lang="ts">
import type { Collection } from "@dlr-shepard/backend-client";

const router = useRouter();

const props = defineProps<{
  maxObjects: number;
  page: number;
  collections: Collection[];
}>();

const page = ref(props.page);
const paginationLength =
  Math.floor(props.collections.length / props.maxObjects) + 1;
</script>

<template>
  <v-data-iterator
    :items="$props.collections"
    :items-per-page="$props.maxObjects"
    :page="page"
  >
    <template #default="{ items }">
      <v-card
        v-for="(item, i) in items"
        :key="i"
        :style="{ padding: '5px' }"
        @click="router.push(collectionsPath + item.raw.id)"
      >
        <v-card-title>
          <b>{{ item.raw.name }}</b>
          ID: {{ item.raw.id }}
        </v-card-title>
        <v-card-subtitle>
          {{ "created at " + item.raw.createdAt + " by " + item.raw.createdBy }}
        </v-card-subtitle>
      </v-card>
      <v-pagination v-model="page" :length="paginationLength" />
    </template>
  </v-data-iterator>
</template>

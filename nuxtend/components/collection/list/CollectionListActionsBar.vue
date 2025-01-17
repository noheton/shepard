<script setup lang="ts">
import { refDebounced } from "@vueuse/core";

const router = useRouter();
const filterInput = ref("");
const filterInputDebounced = refDebounced(filterInput, 700);
const { results, totalResults } = useSearchCollections(filterInputDebounced);
</script>

<template>
  <v-menu :close-on-content-click="false">
    <template #activator="{ props }">
      <v-text-field
        id="userFormInput"
        v-model="filterInput"
        placeholder="Name, Username, ID or Description"
        :style="{ width: '100%', marginTop: '5px' }"
        v-bind="props"
      />
    </template>

    <v-card>
      <v-card-title>
        <h3>Result set ({{ totalResults }} total)</h3>
      </v-card-title>
      <template v-for="result in results" :key="result.id">
        <v-card @click="router.push('/collections/' + result.id)">
          <CollectionListItemContent :collection="result" />
        </v-card>
      </template>
    </v-card>
  </v-menu>
</template>

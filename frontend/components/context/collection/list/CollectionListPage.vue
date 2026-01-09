<script setup lang="ts">
import { useSearchCollections } from "./useSearchCollections";

const router = useRouter();

const itemsPerPage = 20;
const { serverItems, pageCount, loading, searchResultHint } =
  useSearchCollections(itemsPerPage);

const showCreateDialog = ref(false);
</script>

<template>
  <div style="max-width: 1200px; margin: auto">
    <v-container width="100%" fluid>
      <v-row>
        <v-col cols="12" class="py-14">
          <div class="d-flex align-baseline">
            <h1 class="text-h1 text-textbody1 pr-4">Collections</h1>
          </div>
        </v-col>
        <v-col cols="auto" class="pb-4">
          <CollectionSearchField :search-result-hint="searchResultHint" />
        </v-col>
        <v-spacer />
        <v-col cols="auto" class="pb-4" justify-self="end">
          <v-btn
            class="bg-primary text-canvas"
            variant="flat"
            :style="{ marginTop: '3px' }"
            @click="showCreateDialog = true"
          >
            <template #prepend>
              <v-icon icon="mdi-plus-circle" color="canvas" />
            </template>
            Create new collection
          </v-btn>
        </v-col>
        <v-col cols="12" class="pt-4">
          <CollectionList
            :items-per-page="itemsPerPage"
            :server-items="serverItems"
            :loading="loading"
            :page-count="pageCount"
          />
        </v-col>
      </v-row>
    </v-container>
    <CreateCollectionDialog
      v-if="showCreateDialog"
      v-model:show-dialog="showCreateDialog"
      @collection-created="(id: number) => router.push(collectionsPath + id)"
    />
  </div>
</template>

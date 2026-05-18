<script setup lang="ts">
import { useSearchCollections } from "./useSearchCollections";

const router = useRouter();

const itemsPerPage = 20;
const { serverItems, pageCount, loading, searchResultHint } =
  useSearchCollections(itemsPerPage);

const showCreateDialog = ref(false);

const isEmpty = computed(
  () => !loading.value && serverItems.value.length === 0 && !searchResultHint.value,
);
const isSearchEmpty = computed(
  () => !loading.value && serverItems.value.length === 0 && !!searchResultHint.value,
);
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

        <template v-if="isEmpty">
          <v-col cols="12" class="d-flex flex-column align-center py-16">
            <v-icon icon="mdi-folder-open-outline" size="72" color="textbody2" class="mb-4" />
            <div class="text-h4 text-semibold mb-2">No collections yet</div>
            <div class="text-body-1 text-textbody2 mb-6">
              Create your first collection to start organising your research data.
            </div>
            <v-btn
              class="bg-primary text-canvas"
              variant="flat"
              size="large"
              @click="showCreateDialog = true"
            >
              <template #prepend>
                <v-icon icon="mdi-plus-circle" color="canvas" />
              </template>
              Create collection
            </v-btn>
          </v-col>
        </template>

        <template v-else-if="isSearchEmpty">
          <v-col cols="12" sm="auto" class="pb-4">
            <CollectionSearchField :search-result-hint="searchResultHint" />
          </v-col>
          <v-spacer />
          <v-col cols="12" sm="auto" class="pb-4">
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
          <v-col cols="12" class="d-flex flex-column align-center py-12">
            <v-icon icon="mdi-magnify" size="64" color="textbody2" class="mb-4" />
            <div class="text-h5 text-textbody1 mb-2">No collections found</div>
            <div class="text-body-1 text-textbody2">{{ searchResultHint }}</div>
          </v-col>
        </template>

        <template v-else>
          <v-col cols="12" sm="auto" class="pb-4">
            <CollectionSearchField :search-result-hint="searchResultHint" />
          </v-col>
          <v-spacer />
          <v-col cols="12" sm="auto" class="pb-4">
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
        </template>
      </v-row>
    </v-container>
    <CreateCollectionDialog
      v-if="showCreateDialog"
      v-model:show-dialog="showCreateDialog"
      @collection-created="(id: number) => router.push(collectionsPath + id)"
    />
  </div>
</template>

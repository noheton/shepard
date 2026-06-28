<template>
  <v-container fluid>
    <!-- Breadcrumb -->
    <v-breadcrumbs
      :items="[
        { title: 'AAS Shells', to: '/aas/shells' },
        { title: shellTitle },
      ]"
      density="compact"
      class="pa-0 mb-3"
    />

    <!-- AAS integration disabled -->
    <v-alert v-if="isDisabled" type="info" variant="tonal" class="mb-4">
      AAS integration is disabled on this instance. An instance admin can enable
      it via <code class="text-caption">PATCH /v2/admin/aas/config</code>.
    </v-alert>

    <!-- Shell not found -->
    <v-alert v-else-if="isNotFound" type="warning" variant="tonal" class="mb-4">
      Shell not found or you do not have read access to this Collection.
    </v-alert>

    <!-- Fetch error -->
    <v-alert v-else-if="error" type="error" variant="tonal" class="mb-4">
      {{ error }}
    </v-alert>

    <!-- Loading skeleton -->
    <template v-else-if="isLoading">
      <v-skeleton-loader type="article" />
    </template>

    <!-- Shell detail -->
    <template v-else-if="shell">
      <div class="d-flex align-center justify-space-between mb-4">
        <div>
          <h1 class="text-h5 font-weight-bold">{{ shell.idShort }}</h1>
          <div class="d-flex align-center ga-1 flex-wrap mt-1">
            <code class="text-caption text-medium-emphasis">{{ shell.id }}</code>
            <ClipboardButton :text="shell.id" success-message="Shell IRI copied" />
          </div>
        </div>
        <v-btn
          :to="`/collections/${collectionId}`"
          variant="tonal"
          color="primary"
          size="small"
          prepend-icon="mdi-folder-open-outline"
        >
          Open Collection
        </v-btn>
      </div>

      <!-- Shell metadata -->
      <v-card variant="outlined" class="mb-4">
        <v-card-title class="text-body-1 font-weight-medium pa-3 pb-1">
          Asset Information
        </v-card-title>
        <v-list density="compact">
          <v-list-item>
            <template #prepend>
              <span class="text-caption text-medium-emphasis" style="min-width:120px">Asset Kind</span>
            </template>
            <v-chip size="small" variant="outlined" color="secondary">
              {{ shell.assetInformation.assetKind }}
            </v-chip>
          </v-list-item>
          <v-list-item>
            <template #prepend>
              <span class="text-caption text-medium-emphasis" style="min-width:120px">Global Asset ID</span>
            </template>
            <code class="text-caption">{{ shell.assetInformation.globalAssetId }}</code>
          </v-list-item>
          <v-list-item v-if="shell.description && shell.description.length > 0">
            <template #prepend>
              <span class="text-caption text-medium-emphasis" style="min-width:120px">Description</span>
            </template>
            <span v-for="d in shell.description" :key="d.language">
              {{ d.text }}
              <v-chip size="x-small" variant="outlined" class="ml-1">{{ d.language }}</v-chip>
            </span>
          </v-list-item>
        </v-list>
      </v-card>

      <!-- Submodels section -->
      <v-card variant="outlined">
        <v-card-title class="text-body-1 font-weight-medium pa-3 pb-1">
          Submodels
          <v-chip size="x-small" variant="tonal" class="ml-2">
            {{ submodelsTotal }}
          </v-chip>
        </v-card-title>

        <v-progress-linear v-if="isSubmodelsLoading" indeterminate />

        <v-table density="comfortable">
          <thead>
            <tr>
              <th>DataObject</th>
              <th>Reference IRI</th>
              <th>Type</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!isSubmodelsLoading && submodels.length === 0">
              <td colspan="3" class="text-center text-medium-emphasis py-6">
                No Submodels (no top-level DataObjects in this Collection).
              </td>
            </tr>
            <tr v-for="(ref, idx) in submodels" :key="idx">
              <td>
                <NuxtLink
                  v-if="ref.keys.length > 0"
                  :to="`/collections/${collectionId}/dataobjects/${submodelRefToAppId(ref.keys[0].value)}`"
                  class="text-primary text-decoration-none font-weight-medium"
                >
                  {{ ref.displayName ?? submodelRefToAppId(ref.keys[0].value) }}
                </NuxtLink>
              </td>
              <td>
                <div v-if="ref.keys.length > 0" class="d-flex align-center ga-1">
                  <code class="text-caption">{{ ref.keys[0].value }}</code>
                  <ClipboardButton :text="ref.keys[0].value" success-message="Submodel IRI copied" />
                </div>
              </td>
              <td>
                <v-chip size="small" variant="outlined">{{ ref.type }}</v-chip>
              </td>
            </tr>
          </tbody>
        </v-table>

        <div class="d-flex align-center justify-space-between pa-3">
          <span class="text-caption text-medium-emphasis">
            {{ submodelsTotal }} Submodel{{ submodelsTotal === 1 ? "" : "s" }} total
          </span>
          <v-pagination
            v-if="totalSubmodelPages > 1"
            v-model="currentSubmodelPage"
            :length="totalSubmodelPages"
            density="compact"
          />
        </div>
      </v-card>
    </template>
  </v-container>
</template>

<script setup lang="ts">
import { useAasShell, submodelRefToAppId } from "~/composables/aas/useAasShell";

const route = useRoute();
const collectionId = route.params.collectionId as string;

const {
  shell,
  submodels,
  submodelsTotal,
  submodelsPage,
  submodelsPageSize,
  isLoading,
  isSubmodelsLoading,
  isDisabled,
  isNotFound,
  error,
  fetchSubmodels,
} = useAasShell(collectionId);

const shellTitle = computed(() => shell.value?.idShort ?? collectionId);

useHead({ title: computed(() => `AAS Shell — ${shellTitle.value}`) });

const totalSubmodelPages = computed(() =>
  submodelsTotal.value > 0
    ? Math.ceil(submodelsTotal.value / submodelsPageSize.value)
    : 1,
);

// v-pagination is 1-based; composable page ref is 0-based
const currentSubmodelPage = computed({
  get: () => submodelsPage.value + 1,
  set: (v: number) => {
    submodelsPage.value = v - 1;
    fetchSubmodels();
  },
});
</script>

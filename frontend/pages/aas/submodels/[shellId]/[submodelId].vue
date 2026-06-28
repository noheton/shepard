<template>
  <v-container fluid>
    <!-- Breadcrumb -->
    <v-breadcrumbs
      :items="[
        { title: 'AAS Shells', to: '/aas/shells' },
        { title: shellId, to: `/aas/shells/${shellId}` },
        { title: submodelTitle },
      ]"
      density="compact"
      class="pa-0 mb-3"
    />

    <!-- Not found -->
    <v-alert v-if="isNotFound" type="warning" variant="tonal" class="mb-4">
      Submodel not found or you do not have read access to this Collection.
    </v-alert>

    <!-- Fetch error -->
    <v-alert v-else-if="error" type="error" variant="tonal" class="mb-4">
      {{ error }}
    </v-alert>

    <!-- Loading skeleton -->
    <template v-else-if="isLoading">
      <v-skeleton-loader type="article" />
    </template>

    <!-- Submodel detail -->
    <template v-else-if="submodel">
      <div class="d-flex align-center justify-space-between mb-4">
        <div>
          <h1 class="text-h5 font-weight-bold">{{ submodel.idShort }}</h1>
          <div class="d-flex align-center ga-1 flex-wrap mt-1">
            <code class="text-caption text-medium-emphasis">{{ submodel.id }}</code>
            <ClipboardButton :text="submodel.id" success-message="Submodel IRI copied" />
          </div>
        </div>
        <div class="d-flex ga-2">
          <v-btn
            :to="`/aas/shells/${shellId}`"
            variant="tonal"
            color="secondary"
            size="small"
            prepend-icon="mdi-arrow-left"
          >
            Back to Shell
          </v-btn>
          <v-btn
            :to="`/collections/${shellId}/dataobjects/${submodelId}`"
            variant="tonal"
            color="primary"
            size="small"
            prepend-icon="mdi-database-outline"
          >
            Open DataObject
          </v-btn>
        </div>
      </div>

      <!-- Submodel metadata card -->
      <v-card variant="outlined" class="mb-4">
        <v-card-title class="text-body-1 font-weight-medium pa-3 pb-1">
          Submodel Identity
        </v-card-title>
        <v-list density="compact">
          <v-list-item>
            <template #prepend>
              <span class="text-caption text-medium-emphasis" style="min-width:140px">idShort</span>
            </template>
            <span class="text-body-2">{{ submodel.idShort }}</span>
          </v-list-item>
          <v-list-item>
            <template #prepend>
              <span class="text-caption text-medium-emphasis" style="min-width:140px">Submodel IRI</span>
            </template>
            <div class="d-flex align-center ga-1">
              <code class="text-caption">{{ submodel.id }}</code>
              <ClipboardButton :text="submodel.id" success-message="Submodel IRI copied" />
            </div>
          </v-list-item>
          <v-list-item>
            <template #prepend>
              <span class="text-caption text-medium-emphasis" style="min-width:140px">AppId</span>
            </template>
            <div class="d-flex align-center ga-1">
              <code class="text-caption">{{ submodel.appId }}</code>
              <ClipboardButton :text="submodel.appId" success-message="AppId copied" />
            </div>
          </v-list-item>
          <v-list-item v-if="submodel.description">
            <template #prepend>
              <span class="text-caption text-medium-emphasis" style="min-width:140px">Description</span>
            </template>
            <span class="text-body-2">{{ submodel.description }}</span>
          </v-list-item>
        </v-list>
      </v-card>

      <!-- SubmodelElements: semantic annotations as AAS Properties -->
      <v-card variant="outlined" class="mb-4">
        <v-card-title class="text-body-1 font-weight-medium pa-3 pb-1">
          SubmodelElements (Properties)
          <v-chip
            v-if="!isPropertiesLoading"
            size="x-small"
            variant="tonal"
            class="ml-2"
          >
            {{ properties.length }}
          </v-chip>
        </v-card-title>

        <v-progress-linear v-if="isPropertiesLoading" indeterminate />

        <v-table v-if="!isPropertiesLoading && properties.length > 0" density="comfortable">
          <thead>
            <tr>
              <th>idShort</th>
              <th>Predicate IRI</th>
              <th>Value</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(prop, idx) in properties" :key="idx">
              <td>
                <code class="text-caption">{{ prop.idShort }}</code>
              </td>
              <td>
                <div class="d-flex align-center ga-1">
                  <code class="text-caption text-medium-emphasis">{{ prop.predicateIri }}</code>
                  <ClipboardButton
                    :text="prop.predicateIri"
                    success-message="Predicate IRI copied"
                  />
                </div>
              </td>
              <td>
                <span v-if="prop.objectIri && !prop.objectLiteral" class="text-caption">
                  <NuxtLink
                    v-if="prop.objectIri.startsWith('urn:shepard:')"
                    :to="`/semantic/sparql?q=${encodeURIComponent('<' + prop.objectIri + '>')}`"
                    class="text-decoration-none text-primary"
                  >
                    {{ prop.objectIri }}
                  </NuxtLink>
                  <code v-else class="text-caption">{{ prop.objectIri }}</code>
                </span>
                <span v-else class="text-caption">{{ prop.displayValue }}</span>
              </td>
            </tr>
          </tbody>
        </v-table>

        <div
          v-else-if="!isPropertiesLoading && properties.length === 0"
          class="text-center text-medium-emphasis py-4 text-body-2"
        >
          No semantic annotations on this DataObject.
          <NuxtLink
            :to="`/collections/${shellId}/dataobjects/${submodelId}`"
            class="text-decoration-none text-primary ml-1"
          >Add annotations in the DataObject view.</NuxtLink>
        </div>
      </v-card>

      <!-- DataObject view hint -->
      <v-card variant="tonal" color="surface-variant" class="mb-2">
        <v-card-text class="text-body-2 py-2">
          Data references, containers, and child DataObjects are visible in the
          <NuxtLink
            :to="`/collections/${shellId}/dataobjects/${submodelId}`"
            class="text-decoration-none font-weight-medium"
          >DataObject detail view</NuxtLink>.
        </v-card-text>
      </v-card>
    </template>
  </v-container>
</template>

<script setup lang="ts">
import { useAasSubmodel } from "~/composables/aas/useAasSubmodel";

const route = useRoute();
const shellId = route.params.shellId as string;
const submodelId = route.params.submodelId as string;

const { submodel, properties, isLoading, isPropertiesLoading, isNotFound, error } =
  useAasSubmodel(shellId, submodelId);

const submodelTitle = computed(() => submodel.value?.idShort ?? submodelId);

useHead({ title: computed(() => `AAS Submodel — ${submodelTitle.value}`) });
</script>

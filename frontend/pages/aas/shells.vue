<template>
  <v-container fluid>
    <h1 class="text-h5 font-weight-bold mb-1">AAS Shells</h1>
    <p class="text-body-2 text-medium-emphasis mb-4">
      Each Collection is exposed as an IDTA AAS v3 Asset Administration Shell
      (AAS1a). Top-level DataObjects appear as Submodel references (AAS1b).
    </p>

    <!-- AAS integration disabled on this instance -->
    <v-alert
      v-if="isDisabled"
      type="info"
      variant="tonal"
      class="mb-4"
    >
      AAS integration is disabled on this instance. An instance admin can enable
      it via
      <code class="text-caption">PATCH /v2/admin/aas/config</code>.
    </v-alert>

    <!-- Fetch error -->
    <v-alert v-else-if="error" type="error" variant="tonal" class="mb-4">
      {{ error }}
    </v-alert>

    <!-- Shell list -->
    <template v-else>
      <v-card variant="outlined">
        <v-progress-linear v-if="isLoading" indeterminate />

        <v-table density="comfortable">
          <thead>
            <tr>
              <th>Name (idShort)</th>
              <th>Shell IRI</th>
              <th>Asset Kind</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!isLoading && shells.length === 0">
              <td colspan="3" class="text-center text-medium-emphasis py-6">
                No Shells available.
              </td>
            </tr>
            <tr v-for="shell in shells" :key="shell.id">
              <td>
                <NuxtLink
                  :to="`/collections/${shellIdToAppId(shell.id)}`"
                  class="text-primary text-decoration-none font-weight-medium"
                >
                  {{ shell.idShort }}
                </NuxtLink>
              </td>
              <td>
                <code class="text-caption">{{ shell.id }}</code>
              </td>
              <td>
                <v-chip size="small" variant="outlined" color="secondary">
                  {{ shell.assetInformation.assetKind }}
                </v-chip>
              </td>
            </tr>
          </tbody>
        </v-table>
      </v-card>

      <div class="d-flex align-center justify-space-between mt-3">
        <span class="text-caption text-medium-emphasis">
          {{ total }} Shell{{ total === 1 ? "" : "s" }} total
        </span>
        <v-pagination
          v-if="totalPages > 1"
          v-model="currentPage"
          :length="totalPages"
          density="compact"
        />
      </div>
    </template>
  </v-container>
</template>

<script setup lang="ts">
import { useAasShells, shellIdToAppId } from "~/composables/aas/useAasShells";

useHead({ title: "AAS Shells" });

const { shells, total, page, pageSize, isLoading, isDisabled, error, refresh } =
  useAasShells();

const totalPages = computed(() =>
  total.value > 0 ? Math.ceil(total.value / pageSize.value) : 1,
);

// v-pagination is 1-based; composable page ref is 0-based
const currentPage = computed({
  get: () => page.value + 1,
  set: (v: number) => {
    page.value = v - 1;
    refresh();
  },
});
</script>

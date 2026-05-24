<script setup lang="ts">
// /semantic/vocabularies — vocabulary index. Lists the bundles seeded into
// :SemanticConfig.enabledVocabularies (admin-managed via SemanticAdminRest
// at GET /v2/admin/semantic/ontologies). Drill into a vocabulary detail
// page for predicate listings (placeholder until SEMA-V6-UI-FOLLOWUP).
// Design: aidocs/semantics/100 §4.

interface OntologyBundleIO {
  id?: string;
  title?: string;
  enabled?: boolean;
  lastImportedAt?: string;
  shaHash?: string;
  source?: string;
}

useHead({ title: "Vocabularies | semantic | shepard" });

const bundles = ref<OntologyBundleIO[]>([]);
const isLoading = ref(false);
const error = ref<string | null>(null);

async function loadBundles() {
  isLoading.value = true;
  error.value = null;
  try {
    const { data: auth } = useAuth();
    const config = useRuntimeConfig().public;
    const explicit = config.backendV2ApiUrl as string | undefined;
    const v2Base =
      explicit && explicit.length > 0
        ? explicit
        : (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
    const headers: Record<string, string> = { Accept: "application/json" };
    if (auth.value?.accessToken) {
      headers["Authorization"] = `Bearer ${auth.value.accessToken}`;
    }
    const res = await fetch(v2Base + "/v2/admin/semantic/ontologies", { headers });
    if (!res.ok) {
      // 403 expected for non-admin users — fall back to a friendly hint.
      if (res.status === 401 || res.status === 403) {
        error.value =
          "Listing vocabularies currently requires the instance-admin role. SEMA-V6-UI-FOLLOWUP will surface a read-only listing for all users.";
        return;
      }
      error.value = `${res.status} ${res.statusText}`;
      return;
    }
    const body = await res.json();
    bundles.value = body.bundles ?? body ?? [];
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    isLoading.value = false;
  }
}

onMounted(loadBundles);
</script>

<template>
  <v-container>
    <div class="d-flex flex-column ga-2 mb-4">
      <h4 class="text-h4">Vocabularies</h4>
      <p class="text-body-1 text-medium-emphasis">
        Ontology bundles seeded into the internal semantic store. Each bundle
        contributes predicates available to the semantic-annotation picker.
      </p>
    </div>
    <v-progress-linear v-if="isLoading" indeterminate />
    <v-alert v-if="error" type="info" variant="tonal" class="mb-3">
      {{ error }}
    </v-alert>
    <v-list v-if="bundles.length > 0">
      <v-list-item
        v-for="b in bundles"
        :key="b.id ?? b.title"
        :to="`/semantic/vocabularies/${encodeURIComponent(b.id ?? '')}`"
      >
        <template #prepend>
          <v-icon>mdi-library-outline</v-icon>
        </template>
        <v-list-item-title>
          {{ b.title ?? b.id }}
          <v-chip
            v-if="b.enabled === false"
            size="x-small"
            color="warning"
            class="ml-2"
          >disabled</v-chip>
        </v-list-item-title>
        <v-list-item-subtitle>
          {{ b.id }}{{ b.lastImportedAt ? ` · last imported ${b.lastImportedAt}` : "" }}
        </v-list-item-subtitle>
      </v-list-item>
    </v-list>
    <v-alert
      v-else-if="!isLoading && !error"
      type="info"
      variant="tonal"
      text="No vocabularies are currently registered. Instance-admins can upload bundles via the Semantic Repositories admin pane."
    />
  </v-container>
</template>

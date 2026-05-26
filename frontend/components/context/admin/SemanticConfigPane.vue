<script setup lang="ts">
/**
 * SEMA-V6-014 — Admin pane for SemanticConfig settings.
 *
 * Surfaces the personalVocabulariesEnabled toggle and the other
 * SEMA-V6-003/013 knobs via PATCH /v2/admin/semantic/config.
 */

const BASE = "/v2/admin/semantic/config";

interface SemanticConfigIO {
  appId: string;
  preseedEnabled: boolean;
  annotationMode: string;
  suggestionEnabled: boolean;
  suggestionModelId: string | null;
  defaultVocabularyAppId: string | null;
  annotationDeletePolicy: string | null;
  personalVocabulariesEnabled: boolean;
}

const { data: accessToken } = useAuth();

const config = ref<SemanticConfigIO | null>(null);
const loading = ref(false);
const saving = ref(false);
const fetchError = ref<string | null>(null);
const successMsg = ref<string | null>(null);
const errorMsg = ref<string | null>(null);

async function fetchConfig() {
  loading.value = true;
  fetchError.value = null;
  try {
    const token = accessToken.value?.accessToken;
    const res = await fetch(BASE, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) {
      fetchError.value = `Failed to load semantic config (HTTP ${res.status})`;
      return;
    }
    config.value = await res.json();
  } catch (e: unknown) {
    fetchError.value = String(e);
  } finally {
    loading.value = false;
  }
}

async function patchConfig(patch: Partial<SemanticConfigIO>) {
  saving.value = true;
  successMsg.value = null;
  errorMsg.value = null;
  try {
    const token = accessToken.value?.accessToken;
    const res = await fetch(BASE, {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(patch),
    });
    if (!res.ok) {
      const errBody = await res.text().catch(() => "");
      errorMsg.value = `Save failed (HTTP ${res.status}): ${errBody}`;
      return;
    }
    config.value = await res.json();
    successMsg.value = "Semantic config saved.";
  } catch (e: unknown) {
    errorMsg.value = String(e);
  } finally {
    saving.value = false;
  }
}

async function onPersonalVocabToggle(newValue: boolean | null) {
  if (newValue === null) return;
  await patchConfig({ personalVocabulariesEnabled: newValue });
}

async function onPreseedToggle(newValue: boolean | null) {
  if (newValue === null) return;
  await patchConfig({ preseedEnabled: newValue });
}

async function onSuggestionToggle(newValue: boolean | null) {
  if (newValue === null) return;
  await patchConfig({ suggestionEnabled: newValue });
}

onMounted(fetchConfig);
</script>

<template>
  <div>
    <v-row class="mb-2" align="center">
      <v-col>
        <h2 class="text-h6">Semantic Configuration</h2>
        <p class="text-body-2 text-medium-emphasis">
          Runtime knobs for semantic annotation behaviour. Changes take effect immediately without a restart.
        </p>
      </v-col>
      <v-col cols="auto">
        <v-btn variant="tonal" size="small" :loading="loading" @click="fetchConfig">
          Refresh
        </v-btn>
      </v-col>
    </v-row>

    <v-alert v-if="fetchError" type="error" class="mb-4" closable @click:close="fetchError = null">
      {{ fetchError }}
    </v-alert>
    <v-alert v-if="errorMsg" type="error" class="mb-4" closable @click:close="errorMsg = null">
      {{ errorMsg }}
    </v-alert>
    <v-alert v-if="successMsg" type="success" class="mb-4" closable @click:close="successMsg = null">
      {{ successMsg }}
    </v-alert>

    <v-skeleton-loader v-if="loading" type="list-item-two-line, list-item-two-line, list-item-two-line" />

    <v-card v-else-if="config" variant="outlined">
      <v-list>
        <!-- SEMA-V6-014: personalVocabulariesEnabled -->
        <v-list-item>
          <template #prepend>
            <v-icon>mdi-account-tag-outline</v-icon>
          </template>
          <v-list-item-title>Allow personal vocabularies</v-list-item-title>
          <v-list-item-subtitle>
            When enabled, authenticated users may mint their own personal vocabulary namespaces at
            <code>POST /v2/vocabularies/personal</code>.
          </v-list-item-subtitle>
          <template #append>
            <v-switch
              :model-value="config.personalVocabulariesEnabled"
              :loading="saving"
              color="primary"
              hide-details
              density="compact"
              @update:model-value="onPersonalVocabToggle"
            />
          </template>
        </v-list-item>

        <v-divider />

        <!-- preseedEnabled -->
        <v-list-item>
          <template #prepend>
            <v-icon>mdi-seed-outline</v-icon>
          </template>
          <v-list-item-title>Ontology pre-seed on startup</v-list-item-title>
          <v-list-item-subtitle>
            When disabled, the ontology pre-seed pass on startup is skipped.
          </v-list-item-subtitle>
          <template #append>
            <v-switch
              :model-value="config.preseedEnabled"
              :loading="saving"
              color="primary"
              hide-details
              density="compact"
              @update:model-value="onPreseedToggle"
            />
          </template>
        </v-list-item>

        <v-divider />

        <!-- suggestionEnabled -->
        <v-list-item>
          <template #prepend>
            <v-icon>mdi-robot-outline</v-icon>
          </template>
          <v-list-item-title>AI annotation suggestions</v-list-item-title>
          <v-list-item-subtitle>
            When enabled, the "Suggest annotations" button appears in the annotation dialog (SEMA-V6-004).
          </v-list-item-subtitle>
          <template #append>
            <v-switch
              :model-value="config.suggestionEnabled"
              :loading="saving"
              color="primary"
              hide-details
              density="compact"
              @update:model-value="onSuggestionToggle"
            />
          </template>
        </v-list-item>

        <v-divider />

        <!-- annotationMode (read-only display) -->
        <v-list-item>
          <template #prepend>
            <v-icon>mdi-tag-check-outline</v-icon>
          </template>
          <v-list-item-title>Annotation mode</v-list-item-title>
          <v-list-item-subtitle>
            <code>STRICT</code> = only registered vocabulary predicates allowed;
            <code>PERMISSIVE</code> = free-form annotations also allowed.
          </v-list-item-subtitle>
          <template #append>
            <v-chip size="small" :color="config.annotationMode === 'STRICT' ? 'warning' : 'success'">
              {{ config.annotationMode ?? "PERMISSIVE" }}
            </v-chip>
          </template>
        </v-list-item>
      </v-list>
    </v-card>

    <v-alert v-else-if="!loading" type="info" variant="tonal">
      No semantic config loaded. Click Refresh to retry.
    </v-alert>
  </div>
</template>

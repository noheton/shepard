<script setup lang="ts">
import { AdminFragments } from "./adminMenuItems";
import type { AiCapabilityConfigIO, AiCapabilitySlotPatch } from "~/composables/context/admin/useAiConfig";
import { useAiConfig } from "~/composables/context/admin/useAiConfig";

const { slots, isLoading, isSaving, error, refresh, patchSlot } = useAiConfig();

// ─── Edit dialog state ─────────────────────────────────────────────────────
const dialogOpen = ref(false);
const editingCap = ref<string>("");
const editEnabled = ref<boolean>(false);
const editEndpointUrl = ref<string>("");
const editModel = ref<string>("");
const editApiKey = ref<string>("");
const editApiKeySet = ref<boolean>(false);
const editTransport = ref<string>("");
const editGuardrailsPrefix = ref<string>("");
const editGuardrailsSuffix = ref<string>("");
const editMaxTokens = ref<string>("");
const editTemperature = ref<string>("");
const saveError = ref<string | null>(null);

const maxTokensError = computed(() => {
  const val = editMaxTokens.value.trim();
  if (val === "") return null;
  const n = Number(val);
  if (!Number.isInteger(n) || n <= 0) return "Must be a positive integer, or leave blank for the endpoint default.";
  return null;
});

const temperatureError = computed(() => {
  const val = editTemperature.value.trim();
  if (val === "") return null;
  const n = Number(val);
  if (isNaN(n) || n < 0 || n > 2) return "Must be a number between 0 and 2, or leave blank for the endpoint default.";
  return null;
});

const canSave = computed(
  () => maxTokensError.value === null && temperatureError.value === null,
);

function openEdit(slot: AiCapabilityConfigIO) {
  editingCap.value = slot.capability;
  editEnabled.value = slot.enabled ?? false;
  editEndpointUrl.value = slot.endpointUrl ?? "";
  editModel.value = slot.model ?? "";
  editApiKey.value = "";
  editApiKeySet.value = slot.apiKeySet ?? false;
  editTransport.value = slot.transport ?? "";
  editGuardrailsPrefix.value = slot.guardrailsPrefix ?? "";
  editGuardrailsSuffix.value = slot.guardrailsSuffix ?? "";
  editMaxTokens.value = slot.maxTokens != null ? String(slot.maxTokens) : "";
  editTemperature.value = slot.temperature != null ? String(slot.temperature) : "";
  saveError.value = null;
  dialogOpen.value = true;
}

function cancelEdit() {
  dialogOpen.value = false;
  saveError.value = null;
}

async function save() {
  if (!canSave.value) return;
  saveError.value = null;

  const updates: AiCapabilitySlotPatch = {
    enabled: editEnabled.value,
  };

  const url = editEndpointUrl.value.trim();
  updates.endpointUrl = url === "" ? null : url;

  const model = editModel.value.trim();
  updates.model = model === "" ? null : model;

  const apiKey = editApiKey.value.trim();
  if (apiKey !== "") {
    updates.apiKey = apiKey;
  }

  const transport = editTransport.value.trim();
  updates.transport = transport === "" ? null : transport;

  const prefix = editGuardrailsPrefix.value.trim();
  updates.guardrailsPrefix = prefix === "" ? null : prefix;

  const suffix = editGuardrailsSuffix.value.trim();
  updates.guardrailsSuffix = suffix === "" ? null : suffix;

  const maxTok = editMaxTokens.value.trim();
  updates.maxTokens = maxTok === "" ? null : Number(maxTok);

  const temp = editTemperature.value.trim();
  updates.temperature = temp === "" ? null : Number(temp);

  const result = await patchSlot(editingCap.value, updates);
  if (result) {
    dialogOpen.value = false;
  } else {
    saveError.value = error.value ?? "Failed to save. Please try again.";
  }
}

const CAPABILITY_ICONS: Record<string, string> = {
  TEXT: "mdi-text",
  FAST_TEXT: "mdi-lightning-bolt",
  IMAGE_GEN: "mdi-image-outline",
  VISION: "mdi-eye-outline",
  EMBEDDING: "mdi-vector-point",
  STRUCTURED: "mdi-code-json",
  TRANSCRIPTION: "mdi-microphone-outline",
  MODERATION: "mdi-shield-check-outline",
};
</script>

<template>
  <div :id="AdminFragments.AI_CONFIG" class="d-flex flex-column ga-4">
    <!-- Header row -->
    <div class="d-flex align-center justify-space-between flex-wrap ga-2">
      <div class="d-flex align-center ga-3">
        <h4 class="text-h4">AI Configuration</h4>
        <v-btn
          icon="mdi-refresh"
          variant="text"
          size="small"
          :loading="isLoading"
          data-testid="ai-config-refresh"
          @click="refresh"
        />
      </div>
    </div>

    <p class="text-body-2 text-medium-emphasis">
      Per-instance LLM capability slot configurations. Each slot maps a model role to
      an inference endpoint. Changes take effect immediately — no restart required.
      Mutations are recorded in the provenance audit trail via PROV1a.
    </p>

    <v-alert
      v-if="error && !dialogOpen"
      type="error"
      variant="tonal"
      closable
      data-testid="ai-config-error"
      @click:close="error = null"
    >
      {{ error }}
    </v-alert>

    <v-progress-linear v-if="isLoading && !slots" indeterminate />

    <v-table v-if="slots" density="comfortable" data-testid="ai-config-table">
      <thead>
        <tr>
          <th>Capability</th>
          <th>Enabled</th>
          <th>Endpoint URL</th>
          <th>Model</th>
          <th>API key</th>
          <th>Transport</th>
          <th />
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="slot in slots"
          :key="slot.capability"
          :data-testid="`ai-slot-${slot.capability}`"
        >
          <td>
            <v-chip
              :prepend-icon="CAPABILITY_ICONS[slot.capability] ?? 'mdi-robot-outline'"
              size="small"
              variant="tonal"
              label
            >
              {{ slot.capability }}
            </v-chip>
          </td>
          <td>
            <v-chip
              :color="slot.enabled ? 'success' : 'default'"
              size="small"
              variant="tonal"
              :data-testid="`ai-enabled-${slot.capability}`"
            >
              {{ slot.enabled ? "on" : "off" }}
            </v-chip>
          </td>
          <td class="url-cell text-body-2">
            <span v-if="slot.endpointUrl" class="text-mono">{{ slot.endpointUrl }}</span>
            <span v-else class="text-medium-emphasis">—</span>
          </td>
          <td class="text-body-2">
            <span v-if="slot.model">{{ slot.model }}</span>
            <span v-else class="text-medium-emphasis">—</span>
          </td>
          <td>
            <v-chip
              v-if="slot.apiKeySet"
              color="success"
              size="x-small"
              variant="tonal"
              prepend-icon="mdi-key"
              :data-testid="`ai-apikey-${slot.capability}`"
            >
              set
            </v-chip>
            <span v-else class="text-medium-emphasis">—</span>
          </td>
          <td class="text-body-2">
            <span v-if="slot.transport">{{ slot.transport }}</span>
            <span v-else class="text-medium-emphasis">—</span>
          </td>
          <td>
            <v-btn
              icon="mdi-pencil-outline"
              variant="text"
              size="small"
              :data-testid="`ai-edit-btn-${slot.capability}`"
              @click="openEdit(slot)"
            />
          </td>
        </tr>
      </tbody>
    </v-table>

    <!-- Per-slot edit dialog -->
    <v-dialog v-model="dialogOpen" max-width="640" persistent>
      <v-card data-testid="ai-edit-dialog">
        <v-card-title class="text-h6 pa-4 d-flex align-center ga-2">
          <v-icon :icon="CAPABILITY_ICONS[editingCap] ?? 'mdi-robot-outline'" />
          Edit {{ editingCap }} slot
        </v-card-title>

        <v-card-text class="pa-4 d-flex flex-column ga-3">
          <v-alert v-if="saveError" type="error" variant="tonal" data-testid="ai-save-error">
            {{ saveError }}
          </v-alert>

          <v-switch
            v-model="editEnabled"
            label="Enabled"
            color="primary"
            hide-details
            data-testid="ai-edit-enabled"
          />

          <v-text-field
            v-model="editEndpointUrl"
            label="Endpoint URL"
            placeholder="https://api.openai.com/v1"
            hint="Base URL of the inference endpoint. Blank reverts to the deploy-time default."
            persistent-hint
            variant="outlined"
            density="comfortable"
            data-testid="ai-edit-endpointUrl"
          />

          <v-text-field
            v-model="editModel"
            label="Model"
            placeholder="gpt-4o"
            hint="Model identifier sent to the endpoint. Blank uses the endpoint default."
            persistent-hint
            variant="outlined"
            density="comfortable"
            data-testid="ai-edit-model"
          />

          <v-text-field
            v-model="editApiKey"
            label="API key"
            :placeholder="editApiKeySet ? '(unchanged — enter to replace)' : '(not set)'"
            hint="Write-only. Leave blank to keep the existing key; enter a value to replace it."
            persistent-hint
            variant="outlined"
            density="comfortable"
            type="password"
            autocomplete="new-password"
            data-testid="ai-edit-apiKey"
          />

          <v-text-field
            v-model="editTransport"
            label="Transport"
            placeholder="openai"
            hint="Transport adapter (e.g. openai, ollama, tei). Blank uses the endpoint default."
            persistent-hint
            variant="outlined"
            density="comfortable"
            data-testid="ai-edit-transport"
          />

          <v-row dense>
            <v-col cols="6">
              <v-text-field
                v-model="editMaxTokens"
                label="Max tokens"
                :error-messages="maxTokensError ?? undefined"
                placeholder="4096"
                hint="Positive integer or blank for endpoint default."
                persistent-hint
                variant="outlined"
                density="comfortable"
                inputmode="numeric"
                data-testid="ai-edit-maxTokens"
              />
            </v-col>
            <v-col cols="6">
              <v-text-field
                v-model="editTemperature"
                label="Temperature"
                :error-messages="temperatureError ?? undefined"
                placeholder="0.7"
                hint="0–2 or blank for endpoint default."
                persistent-hint
                variant="outlined"
                density="comfortable"
                inputmode="decimal"
                data-testid="ai-edit-temperature"
              />
            </v-col>
          </v-row>

          <v-text-field
            v-model="editGuardrailsPrefix"
            label="Guardrails prefix"
            hint="Text prepended to every prompt sent via this slot. Blank to disable."
            persistent-hint
            variant="outlined"
            density="comfortable"
            data-testid="ai-edit-guardrailsPrefix"
          />

          <v-text-field
            v-model="editGuardrailsSuffix"
            label="Guardrails suffix"
            hint="Text appended to every prompt sent via this slot. Blank to disable."
            persistent-hint
            variant="outlined"
            density="comfortable"
            data-testid="ai-edit-guardrailsSuffix"
          />
        </v-card-text>

        <v-card-actions class="pa-4 pt-0">
          <v-spacer />
          <v-btn variant="text" :disabled="isSaving" @click="cancelEdit">
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            variant="tonal"
            :loading="isSaving"
            :disabled="!canSave"
            data-testid="ai-save-btn"
            @click="save"
          >
            Save
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.text-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, "Liberation Mono", monospace;
  font-size: 0.875rem;
}
.url-cell {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

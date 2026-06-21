<script setup lang="ts">
/**
 * REF-EDIT-SPATIAL — rename dialog for a SpatialDataReference.
 *
 * Calls PATCH /v2/references/{appId} (V2CONV-A2 unified surface) with
 * { name: newName } on save. The mutable field is `name`; geometry
 * filter / measurement filter / time bounds are immmutable after the
 * importer run that minted the reference (SHA256 idempotency handles
 * re-runs with a fresh geometry filter).
 *
 * Emits:
 *   saved(newName: string) — on successful PATCH
 */

const props = defineProps<{
  /** UUID v7 appId of the SpatialDataReference to rename. */
  spatialReferenceAppId: string;
  /** Current display name — pre-fills the text field. */
  currentName: string;
}>();

const emit = defineEmits<{
  saved: [newName: string];
}>();

const showDialog = defineModel<boolean>("showDialog", { default: false });

// ── form state ────────────────────────────────────────────────────────────────

const newName = ref("");
const saving = ref(false);

// Reset to current name whenever dialog opens.
watch(showDialog, open => {
  if (open) newName.value = props.currentName;
});

// ── validation ────────────────────────────────────────────────────────────────

const nameError = computed(() => {
  if (!newName.value.trim()) return "Name is required";
  return "";
});

const isValid = computed(
  () => !nameError.value && newName.value.trim() !== props.currentName.trim(),
);

// ── helpers ───────────────────────────────────────────────────────────────────

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = (config as { backendV2ApiUrl?: string }).backendV2ApiUrl;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function authHeaders(): Promise<Record<string, string>> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  if (!accessToken) throw new Error("Not authenticated");
  return {
    Authorization: `Bearer ${accessToken}`,
    Accept: "application/json",
    "Content-Type": "application/json",
  };
}

// ── save ──────────────────────────────────────────────────────────────────────

async function save() {
  if (!isValid.value || saving.value) return;
  saving.value = true;
  try {
    const headers = await authHeaders();
    // SPATIAL-UNIFY-002 / V2CONV-A2: rename via the unified
    // PATCH /v2/references/{appId} surface — the kind is resolved
    // server-side from the entity; the body shape ({ name }) is shared
    // across all reference kinds.
    const url = `${v2BaseUrl()}/v2/references/${props.spatialReferenceAppId}`;
    const response = await fetch(url, {
      method: "PATCH",
      headers,
      body: JSON.stringify({ name: newName.value.trim() }),
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const trimmed = newName.value.trim();
    emitSuccess(`Renamed to "${trimmed}"`);
    showDialog.value = false;
    emit("saved", trimmed);
  } catch (e) {
    handleError(e as Error, "renaming spatial reference");
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <FormDialog
    v-model:show-dialog="showDialog"
    title="Rename Spatial Reference"
    :loading="saving"
    :submit-disabled="!isValid"
    save-button-text="Rename"
    @submit="save"
  >
    <template #form>
      <v-form @submit.prevent="save">
        <v-row class="pt-6">
          <v-col>
            <v-text-field
              v-model="newName"
              label="Display name *"
              :error-messages="nameError"
              autofocus
              density="comfortable"
              hint="The name shown in the references table. Does not affect the underlying spatial data container."
              persistent-hint
            />
          </v-col>
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>

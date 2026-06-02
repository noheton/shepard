<script setup lang="ts">
/**
 * REF-EDIT-4 — edit dialog for a FileBundleReference.
 *
 * Calls PATCH /v2/bundles/{appId} with { name, description } on save.
 * Both fields are mutable; description may be set to null (cleared) via
 * an explicit empty-string → null coercion in the save handler.
 *
 * Emits:
 *   saved(name: string, description: string | null) — on successful PATCH
 */

const props = defineProps<{
  /** UUID v7 appId of the FileBundleReference to edit. */
  bundleAppId: string;
  /** Current display name — pre-fills the name field. */
  currentName: string;
  /** Current description (may be null/undefined) — pre-fills the description field. */
  currentDescription?: string | null;
}>();

const emit = defineEmits<{
  saved: [name: string, description: string | null];
}>();

const showDialog = defineModel<boolean>("showDialog", { default: false });

// ── form state ────────────────────────────────────────────────────────────────

const newName = ref("");
const newDescription = ref("");
const saving = ref(false);

// Reset to current values whenever dialog opens.
watch(showDialog, open => {
  if (open) {
    newName.value = props.currentName;
    newDescription.value = props.currentDescription ?? "";
  }
});

// ── validation ────────────────────────────────────────────────────────────────

const nameError = computed(() => {
  if (!newName.value.trim()) return "Name is required";
  return "";
});

/** True when at least one mutable field has changed from the original. */
const hasChanged = computed(
  () =>
    newName.value.trim() !== props.currentName.trim() ||
    (newDescription.value.trim() || null) !== (props.currentDescription?.trim() || null),
);

const isValid = computed(() => !nameError.value && hasChanged.value);

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
    "Content-Type": "application/merge-patch+json",
  };
}

/** Build the RFC 7396 merge-patch body. */
function buildPatchBody(): Record<string, string | null> {
  const body: Record<string, string | null> = {};
  const trimmedName = newName.value.trim();
  if (trimmedName !== props.currentName.trim()) {
    body.name = trimmedName;
  }
  const trimmedDesc = newDescription.value.trim() || null;
  const origDesc = props.currentDescription?.trim() || null;
  if (trimmedDesc !== origDesc) {
    body.description = trimmedDesc;
  }
  return body;
}

// ── save ──────────────────────────────────────────────────────────────────────

async function save() {
  if (!isValid.value || saving.value) return;
  saving.value = true;
  try {
    const headers = await authHeaders();
    const url = `${v2BaseUrl()}/v2/bundles/${encodeURIComponent(props.bundleAppId)}`;
    const body = buildPatchBody();
    const response = await fetch(url, {
      method: "PATCH",
      headers,
      body: JSON.stringify(body),
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const savedName = newName.value.trim();
    const savedDescription = newDescription.value.trim() || null;
    emitSuccess(`Bundle "${savedName}" updated`);
    showDialog.value = false;
    emit("saved", savedName, savedDescription);
  } catch (e) {
    handleError(e as Error, "editing file bundle reference");
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <FormDialog
    v-model:show-dialog="showDialog"
    title="Edit File Bundle"
    :loading="saving"
    :submit-disabled="!isValid"
    save-button-text="Save"
    @submit="save"
  >
    <template #form>
      <v-form @submit.prevent="save">
        <v-row class="pt-6">
          <v-col>
            <v-text-field
              v-model="newName"
              label="Name *"
              :error-messages="nameError"
              autofocus
              density="comfortable"
              hint="The display name shown in the UI."
              persistent-hint
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <v-textarea
              v-model="newDescription"
              label="Description"
              density="comfortable"
              hint="Optional free-form description. Leave blank to clear."
              persistent-hint
              rows="3"
              auto-grow
            />
          </v-col>
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>

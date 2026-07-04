<script setup lang="ts">
/**
 * P21-V2-METADATA-EDIT-2 — rename dialog for a v2 container.
 *
 * Calls PUT /v2/containers/{appId} with { name: trimmedName, status? } on save.
 * Status is fetched via GET /v2/containers/{appId} on dialog open so the
 * full-replace PUT doesn't silently clear it.
 *
 * Emits:
 *   saved(newName: string) — on successful PUT
 */

const props = defineProps<{
  /** UUID v7 appId of the container to rename. */
  containerAppId: string;
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
/** Status preserved from GET so the full-replace PUT doesn't clear it. */
const fetchedStatus = ref<string | null>(null);

watch(showDialog, async open => {
  if (!open) return;
  newName.value = props.currentName;
  fetchedStatus.value = null;
  try {
    const headers = await authHeaders();
    const resp = await fetch(
      `${v2BaseUrl()}/v2/containers/${encodeURIComponent(props.containerAppId)}`,
      { method: "GET", headers },
    );
    if (resp.ok) {
      const body = await resp.json() as { status?: string | null };
      fetchedStatus.value = body.status ?? null;
    }
  } catch {
    // Non-fatal — PUT will omit status field; backend clears it.
  }
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
    const trimmed = newName.value.trim();
    const body: Record<string, string> = { name: trimmed };
    if (fetchedStatus.value) body.status = fetchedStatus.value;
    // P21-V2-METADATA-EDIT-1: full-replace via PUT /v2/containers/{appId}
    const resp = await fetch(
      `${v2BaseUrl()}/v2/containers/${encodeURIComponent(props.containerAppId)}`,
      { method: "PUT", headers, body: JSON.stringify(body) },
    );
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    emitSuccess(`Renamed to "${trimmed}"`);
    showDialog.value = false;
    emit("saved", trimmed);
  } catch (e) {
    handleError(e as Error, "renaming container");
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <FormDialog
    v-model:show-dialog="showDialog"
    title="Rename Container"
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
              label="Container name *"
              :error-messages="nameError"
              autofocus
              density="comfortable"
            />
          </v-col>
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>

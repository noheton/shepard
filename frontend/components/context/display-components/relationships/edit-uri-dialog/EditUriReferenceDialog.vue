<script lang="ts" setup>
/**
 * REF-EDIT-6 — Dialog to edit a URIReference's mutable fields.
 * Calls PATCH /v2/uri-references/{appId} with RFC 7396 merge-patch semantics.
 *
 * Fields: name (required), uri (required), relationship (optional / clearable).
 */

interface EditUriReferenceDialogProps {
  showDialog: boolean;
  /** UUID v7 appId of the URIReference to edit */
  appId: string;
  /** Current name label */
  initialName: string;
  /** Current URI */
  initialUri: string;
  /** Current relationship string (may be empty/undefined) */
  initialRelationship?: string;
}

const props = defineProps<EditUriReferenceDialogProps>();
const emit = defineEmits<{
  (e: "update:showDialog", value: boolean): void;
  (e: "saved"): void;
}>();

const name = ref(props.initialName);
const uri = ref(props.initialUri);
const relationship = ref(props.initialRelationship ?? "");

const saving = ref(false);
const errorMessage = ref<string | null>(null);

const isValid = computed(
  () => name.value.trim().length > 0 && uri.value.trim().length > 0,
);

function close() {
  emit("update:showDialog", false);
}

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

async function save() {
  if (!isValid.value) return;
  saving.value = true;
  errorMessage.value = null;
  try {
    const headers = await authHeaders();
    const body: Record<string, string | null> = {
      name: name.value.trim(),
      uri: uri.value.trim(),
      // Send null to clear, empty string to leave unchanged isn't valid per
      // backend validation — send null when field is blanked.
      relationship: relationship.value.trim() || null,
    };
    const url = `${v2BaseUrl()}/v2/uri-references/${props.appId}`;
    const response = await fetch(url, {
      method: "PATCH",
      headers,
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      const msg = await response.text().catch(() => response.statusText);
      errorMessage.value = `Save failed (${response.status}): ${msg}`;
      return;
    }
    emit("saved");
    close();
  } catch (e) {
    errorMessage.value = e instanceof Error ? e.message : String(e);
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <v-dialog
    :model-value="showDialog"
    max-width="560"
    @update:model-value="emit('update:showDialog', $event)"
  >
    <v-card>
      <v-card-title>Edit Link</v-card-title>
      <v-card-text>
        <v-alert
          v-if="errorMessage"
          type="error"
          density="compact"
          class="mb-3"
          >{{ errorMessage }}</v-alert
        >
        <v-text-field
          v-model="name"
          label="Name"
          required
          :rules="[(v) => !!v.trim() || 'Name is required']"
          class="mb-2"
        />
        <v-text-field
          v-model="uri"
          label="URI"
          required
          :rules="[(v) => !!v.trim() || 'URI is required']"
          class="mb-2"
        />
        <v-text-field
          v-model="relationship"
          label="Relationship (optional)"
          clearable
          hint="Leave blank to clear the relationship label"
          persistent-hint
        />
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" @click="close">Cancel</v-btn>
        <v-btn
          color="primary"
          variant="flat"
          :loading="saving"
          :disabled="!isValid"
          @click="save"
          >Save</v-btn
        >
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

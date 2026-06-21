<script setup lang="ts">
/**
 * REF-EDIT-2 — edit dialog for a VideoStreamReference.
 *
 * Mutable fields exposed by PATCH /v2/references/{appId}:
 *   - name          (required, non-blank string)
 *   - wallClockTimestamp (UTC epoch ms; explicit null clears; absent = no-op per RFC 7396)
 *
 * Only changed fields are sent in the PATCH body (RFC 7396 merge-patch).
 *
 * Emits:
 *   saved({ name, wallClockTimestamp }) — on successful PATCH; parent does optimistic update
 *   close                               — on cancel or ESC
 */

const props = defineProps<{
  /** UUID v7 appId of the VideoStreamReference to edit. */
  appId: string;
  /** Current display name — pre-fills the name field. */
  currentName: string;
  /**
   * Current wall-clock timestamp (UTC epoch milliseconds). Null when not set.
   * The entity stores nanoseconds internally; the dialog works in milliseconds
   * (matching the datetime-local <input> resolution) and converts on save.
   */
  currentWallClockTimestampMs: number | null;
}>();

const emit = defineEmits<{
  saved: [{ name: string; wallClockTimestamp: number | null }];
}>();

const showDialog = defineModel<boolean>("showDialog", { default: false });

// ── form state ────────────────────────────────────────────────────────────────

const newName = ref("");
/**
 * Bound to a datetime-local <input> as a local-time string (YYYY-MM-DDTHH:mm).
 * Empty string means "clear the field" when the user had a value before.
 */
const wallClockLocal = ref<string>("");
const saving = ref(false);

// Reset to current values whenever the dialog opens.
watch(showDialog, open => {
  if (open) {
    newName.value = props.currentName;
    wallClockLocal.value =
      props.currentWallClockTimestampMs != null
        ? msToDatetimeLocal(props.currentWallClockTimestampMs)
        : "";
  }
});

// ── UTC epoch ms ↔ datetime-local conversion ──────────────────────────────────

/**
 * Convert UTC epoch milliseconds to a datetime-local string (local time zone).
 * datetime-local inputs work in local time, so we adjust by the timezone offset.
 */
function msToDatetimeLocal(ms: number): string {
  const d = new Date(ms);
  // Build a string in the format YYYY-MM-DDTHH:mm that reflects local time.
  const pad = (n: number) => String(n).padStart(2, "0");
  return (
    d.getFullYear() +
    "-" +
    pad(d.getMonth() + 1) +
    "-" +
    pad(d.getDate()) +
    "T" +
    pad(d.getHours()) +
    ":" +
    pad(d.getMinutes())
  );
}

/**
 * Convert a datetime-local string (local time) back to UTC epoch milliseconds.
 * Returns null for empty / invalid input.
 */
function datetimeLocalToMs(localStr: string): number | null {
  if (!localStr) return null;
  const d = new Date(localStr);
  if (isNaN(d.getTime())) return null;
  return d.getTime();
}

// ── validation ────────────────────────────────────────────────────────────────

const nameError = computed(() => {
  if (!newName.value.trim()) return "Name is required";
  return "";
});

/** True when at least one field has actually changed. */
const hasChanges = computed(() => {
  const nameTrimmed = newName.value.trim();
  if (nameTrimmed !== props.currentName.trim()) return true;
  const newTs = datetimeLocalToMs(wallClockLocal.value);
  if (newTs !== props.currentWallClockTimestampMs) return true;
  // Also detect explicit clear (user had a value, now field is empty).
  if (wallClockLocal.value === "" && props.currentWallClockTimestampMs != null) return true;
  return false;
});

const isValid = computed(() => !nameError.value && hasChanges.value);

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
    const url = `${v2BaseUrl()}/v2/references/${props.appId}`;

    // RFC 7396 merge-patch: only send keys that actually changed.
    const patchBody: Record<string, unknown> = {};

    const nameTrimmed = newName.value.trim();
    if (nameTrimmed !== props.currentName.trim()) {
      patchBody.name = nameTrimmed;
    }

    // wallClockTimestamp: include key when changed (null = explicit clear).
    const localStr = wallClockLocal.value;
    const newTsMs = localStr ? datetimeLocalToMs(localStr) : null;
    if (newTsMs !== props.currentWallClockTimestampMs ||
        (localStr === "" && props.currentWallClockTimestampMs != null)) {
      patchBody.wallClockTimestamp = newTsMs;
    }

    const response = await fetch(url, {
      method: "PATCH",
      headers,
      body: JSON.stringify(patchBody),
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);

    emitSuccess("Video reference updated");
    showDialog.value = false;
    emit("saved", {
      name: nameTrimmed !== props.currentName.trim() ? nameTrimmed : props.currentName,
      wallClockTimestamp: "wallClockTimestamp" in patchBody ? newTsMs : props.currentWallClockTimestampMs,
    });
  } catch (e) {
    handleError(e as Error, "editing video reference");
  } finally {
    saving.value = false;
  }
}

</script>

<template>
  <FormDialog
    v-model:show-dialog="showDialog"
    title="Edit Video Reference"
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
              label="Display name *"
              :error-messages="nameError"
              autofocus
              density="comfortable"
              hint="The name shown in the UI. Does not rename the underlying file."
              persistent-hint
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <!--
              wallClockTimestamp — datetime-local input bound to local time.
              The component converts to/from UTC epoch milliseconds on save.
              Clearing the field and saving will set wallClockTimestamp to null
              (removing the TM1 anchor).
            -->
            <v-text-field
              v-model="wallClockLocal"
              type="datetime-local"
              label="Wall-clock start time (local)"
              density="comfortable"
              clearable
              hint="Aligns video frames with timeseries channels (TM1 anchor). Leave empty to remove the alignment."
              persistent-hint
            />
          </v-col>
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>

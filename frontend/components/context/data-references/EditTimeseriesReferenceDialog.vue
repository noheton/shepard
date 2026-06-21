<script setup lang="ts">
/**
 * REF-EDIT-1 — edit dialog for a TimeseriesReference.
 *
 * Mutable fields:
 *   - name             (display label)
 *   - start            (time window start, nanoseconds since Unix epoch)
 *   - end              (time window end,   nanoseconds since Unix epoch)
 *   - timeseries       (channel 5-tuple list — replaced wholesale when changed)
 *
 * Calls PATCH /v2/references/{appId} with only the changed fields.
 * Channel list is sent only when the selection differs from the current set
 * (comparison is by 5-tuple key to avoid ordering effects).
 *
 * Emits:
 *   saved({ name, start, end }) — on successful PATCH; caller refreshes as needed.
 */
import type { Timeseries } from "@dlr-shepard/backend-client";

const props = defineProps<{
  /** UUID v7 appId of the TimeseriesReference to edit. */
  timeseriesReferenceAppId: string;
  /** Current display name — pre-fills the name field. */
  currentName: string;
  /** Current start of the time window (nanoseconds since Unix epoch). */
  currentStart: number;
  /** Current end of the time window (nanoseconds since Unix epoch). */
  currentEnd: number;
  /** Current channel 5-tuple selection — pre-selects in the picker. */
  currentChannels?: Timeseries[];
  /**
   * Full list of channels available for selection (from the container).
   * When omitted or empty the picker shows only currently-selected channels
   * (user can remove but not add new ones without the full list).
   */
  availableChannels?: Timeseries[];
}>();

const emit = defineEmits<{
  saved: [updates: { name: string; start: number; end: number }];
}>();

const showDialog = defineModel<boolean>("showDialog", { default: false });

// ── channel key helper ───────────────────────────────────────────────────────

function channelKey(ts: Timeseries): string {
  return [ts.measurement, ts.device, ts.location, ts.symbolicName, ts.field].join("|");
}

function channelLabel(ts: Timeseries): string {
  return [ts.device, ts.field, ts.location, ts.measurement, ts.symbolicName]
    .filter(Boolean)
    .join(" · ");
}

// ── form state ────────────────────────────────────────────────────────────────

const newName          = ref("");
const startLocal       = ref(""); // value for <input type="datetime-local">
const endLocal         = ref(""); // value for <input type="datetime-local">
const selectedChannels = ref<Timeseries[]>([]);
const saving           = ref(false);

// Reset to current values whenever dialog opens.
watch(showDialog, open => {
  if (open) {
    newName.value          = props.currentName;
    startLocal.value       = nsToDatetimeLocal(props.currentStart);
    endLocal.value         = nsToDatetimeLocal(props.currentEnd);
    selectedChannels.value = [...(props.currentChannels ?? [])];
  }
});

// All unique channels = available + currently selected (to show selected items even
// when availableChannels is empty / partially loaded).
const allChannelOptions = computed<Timeseries[]>(() => {
  const seen = new Set<string>();
  const result: Timeseries[] = [];
  for (const ch of [...(props.availableChannels ?? []), ...(props.currentChannels ?? [])]) {
    const k = channelKey(ch);
    if (!seen.has(k)) {
      seen.add(k);
      result.push(ch);
    }
  }
  return result;
});

// ── datetime helpers ──────────────────────────────────────────────────────────

/**
 * Convert nanoseconds-since-epoch to the local-time string accepted by
 * <input type="datetime-local"> ("YYYY-MM-DDTHH:MM:SS").
 */
function nsToDatetimeLocal(ns: number): string {
  const ms = Math.floor(ns / 1_000_000);
  const d  = new Date(ms);
  const pad = (n: number) => String(n).padStart(2, "0");
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  );
}

/**
 * Convert the local-time string from <input type="datetime-local"> back to
 * nanoseconds since Unix epoch.
 */
function datetimeLocalToNs(str: string): number {
  return new Date(str).getTime() * 1_000_000;
}

// ── validation ────────────────────────────────────────────────────────────────

const nameError = computed(() => {
  if (!newName.value.trim()) return "Name is required";
  return "";
});

const startNs = computed<number | null>(() => {
  if (!startLocal.value) return null;
  const ns = datetimeLocalToNs(startLocal.value);
  return isFinite(ns) ? ns : null;
});

const endNs = computed<number | null>(() => {
  if (!endLocal.value) return null;
  const ns = datetimeLocalToNs(endLocal.value);
  return isFinite(ns) ? ns : null;
});

const timeRangeError = computed(() => {
  if (startNs.value === null || endNs.value === null) return "";
  if (startNs.value >= endNs.value) return "Start must be before end";
  return "";
});

const channelError = computed(() => {
  if (selectedChannels.value.length === 0) return "At least one channel is required";
  return "";
});

const currentChannelKeys = computed(() =>
  new Set((props.currentChannels ?? []).map(channelKey)),
);

const selectedChannelKeys = computed(() =>
  new Set(selectedChannels.value.map(channelKey)),
);

const channelsChanged = computed(() => {
  if (currentChannelKeys.value.size !== selectedChannelKeys.value.size) return true;
  for (const k of selectedChannelKeys.value) {
    if (!currentChannelKeys.value.has(k)) return true;
  }
  return false;
});

const hasChanges = computed(() => {
  const nameChanged  = newName.value.trim() !== props.currentName.trim();
  const startChanged = startNs.value !== null && startNs.value !== props.currentStart;
  const endChanged   = endNs.value   !== null && endNs.value   !== props.currentEnd;
  return nameChanged || startChanged || endChanged || channelsChanged.value;
});

const isValid = computed(() =>
  !nameError.value &&
  !timeRangeError.value &&
  !channelError.value &&
  startNs.value !== null &&
  endNs.value   !== null &&
  hasChanges.value,
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
    const url     = `${v2BaseUrl()}/v2/references/${props.timeseriesReferenceAppId}`;

    // Build patch body with only the changed fields (RFC 7396 merge-patch shape).
    const body: Record<string, unknown> = {};
    const trimmedName = newName.value.trim();
    if (trimmedName !== props.currentName.trim()) body.name  = trimmedName;
    if (startNs.value !== props.currentStart)      body.start = startNs.value;
    if (endNs.value   !== props.currentEnd)         body.end   = endNs.value;
    // Send the full channel list when the selection changed so the backend
    // replaces it wholesale (RFC 7396 presence-in-map semantics).
    if (channelsChanged.value) {
      body.timeseries = selectedChannels.value.map(ch => ({
        measurement:  ch.measurement,
        device:       ch.device,
        location:     ch.location,
        symbolicName: ch.symbolicName,
        field:        ch.field,
      }));
    }

    const response = await fetch(url, {
      method: "PATCH",
      headers,
      body: JSON.stringify(body),
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);

    emitSuccess(`Timeseries reference "${trimmedName}" updated`);
    showDialog.value = false;
    emit("saved", {
      name:  trimmedName,
      start: startNs.value!,
      end:   endNs.value!,
    });
  } catch (e) {
    handleError(e as Error, "updating timeseries reference");
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <FormDialog
    v-model:show-dialog="showDialog"
    title="Edit Timeseries Reference"
    :loading="saving"
    :submit-disabled="!isValid"
    save-button-text="Save"
    @submit="save"
  >
    <template #form>
      <v-form @submit.prevent="save">
        <!-- name ─────────────────────────────────────────────────────────── -->
        <v-row class="pt-6">
          <v-col>
            <v-text-field
              v-model="newName"
              label="Display name *"
              :error-messages="nameError"
              autofocus
              density="comfortable"
              hint="The name shown in the UI and breadcrumbs."
              persistent-hint
            />
          </v-col>
        </v-row>

        <!-- time window ─────────────────────────────────────────────────── -->
        <v-row class="mt-2">
          <v-col cols="6">
            <v-text-field
              v-model="startLocal"
              type="datetime-local"
              label="Start *"
              density="comfortable"
              step="1"
              hint="Local time (your browser's timezone)."
              persistent-hint
            />
          </v-col>
          <v-col cols="6">
            <v-text-field
              v-model="endLocal"
              type="datetime-local"
              label="End *"
              density="comfortable"
              step="1"
              :error-messages="timeRangeError"
              hint="Must be after start."
              persistent-hint
            />
          </v-col>
        </v-row>

        <!-- channel selection ───────────────────────────────────────────── -->
        <v-row class="mt-2">
          <v-col>
            <v-autocomplete
              v-model="selectedChannels"
              :items="allChannelOptions"
              :item-title="channelLabel"
              :item-value="(ch: Timeseries) => ch"
              :return-object="true"
              multiple
              chips
              closable-chips
              label="Channels *"
              density="comfortable"
              :error-messages="channelError"
              hint="Select one or more channels to include in this reference."
              persistent-hint
              no-data-text="No channels available — load the container first."
            />
          </v-col>
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>

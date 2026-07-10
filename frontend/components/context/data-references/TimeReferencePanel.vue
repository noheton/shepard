<script setup lang="ts">
/**
 * TM1a — TimeReferencePanel
 *
 * Displays and edits the three time-reference fields on a TimeseriesReference:
 *   - timeReference       (WALL_CLOCK | EXPERIMENT_RELATIVE)
 *   - wallClockOffset     (UTC nanoseconds, required when EXPERIMENT_RELATIVE)
 *   - wallClockOffsetSource (free-text provenance)
 *
 * Props:
 *   appId            — appId (UUID v7) of the TimeseriesReference
 *   timeReference    — current mode from the server (may be null/undefined → WALL_CLOCK)
 *   wallClockOffset  — current offset in nanoseconds (null/undefined = not set)
 *   wallClockOffsetSource — provenance tag (null/undefined = not set)
 *   canEdit          — whether the current user may edit (Write permission on parent DO)
 *
 * Emits:
 *   updated(patch)   — emitted after a successful PATCH with the new field values
 */

import {
  useTimeReference,
  type TimeReferenceV2Patch,
} from "~/composables/context/useTimeReference";

interface TimeReferencePanelProps {
  appId: string;
  timeReference?: string | null;
  wallClockOffset?: number | null;
  wallClockOffsetSource?: string | null;
  canEdit?: boolean;
}

const props = defineProps<TimeReferencePanelProps>();
const emit = defineEmits<{
  updated: [patch: TimeReferenceV2Patch];
}>();

// ── constants ────────────────────────────────────────────────────────────────
const TIME_REFERENCE_OPTIONS = [
  {
    value: "WALL_CLOCK",
    title: "Wall clock (UTC)",
    subtitle: "Sample timestamps are already UTC nanoseconds",
  },
  {
    value: "EXPERIMENT_RELATIVE",
    title: "Experiment-relative",
    subtitle: "Timestamps are ns from t=0; wallClockOffset anchors t=0 to UTC",
  },
];

// ── local edit state ─────────────────────────────────────────────────────────
const editing = ref(false);
const editMode = ref<string>("WALL_CLOCK");
const editOffsetDateStr = ref(""); // YYYY-MM-DD
const editOffsetTimeStr = ref(""); // HH:mm:ss
const editOffsetNsRaw = ref<string>(""); // raw ns string (for power users)
const editOffsetInputMode = ref<"datetime" | "raw">("datetime");
const editSource = ref("");
const isValid = ref(true);

// ── formatting helpers ───────────────────────────────────────────────────────
function nsToIsoString(ns: number | null | undefined): string {
  if (ns == null) return "—";
  const ms = ns / 1_000_000;
  return new Date(ms).toISOString().replace("T", " ").slice(0, 23) + " UTC";
}

function nsToDateParts(ns: number | null | undefined): { date: string; time: string } {
  if (ns == null) return { date: "", time: "" };
  const ms = ns / 1_000_000;
  const d = new Date(ms);
  const date = d.toISOString().slice(0, 10);
  const time = d.toISOString().slice(11, 19);
  return { date, time };
}

function dateParsToNs(dateStr: string, timeStr: string): number | null {
  const iso = `${dateStr}T${timeStr}Z`;
  const ms = Date.parse(iso);
  if (isNaN(ms)) return null;
  return ms * 1_000_000;
}

// Derive effective offset NS from either datetime or raw mode.
const effectiveOffsetNs = computed<number | null>(() => {
  if (editOffsetInputMode.value === "raw") {
    const val = parseInt(editOffsetNsRaw.value, 10);
    return isNaN(val) ? null : val;
  }
  return dateParsToNs(editOffsetDateStr.value, editOffsetTimeStr.value);
});

// ── read mode labels ─────────────────────────────────────────────────────────
const displayMode = computed(() => {
  const mode = props.timeReference ?? "WALL_CLOCK";
  return TIME_REFERENCE_OPTIONS.find(o => o.value === mode)?.title ?? mode;
});

const isModeWallClock = computed(() =>
  (props.timeReference ?? "WALL_CLOCK") === "WALL_CLOCK",
);

// ── open / close edit ─────────────────────────────────────────────────────────
function openEdit() {
  editMode.value = props.timeReference ?? "WALL_CLOCK";
  editSource.value = props.wallClockOffsetSource ?? "";
  const parts = nsToDateParts(props.wallClockOffset);
  editOffsetDateStr.value = parts.date;
  editOffsetTimeStr.value = parts.time;
  editOffsetNsRaw.value = props.wallClockOffset?.toString() ?? "";
  editing.value = true;
}

function cancelEdit() {
  editing.value = false;
}

// ── validation ───────────────────────────────────────────────────────────────
const offsetRequired = computed(
  () => editMode.value === "EXPERIMENT_RELATIVE",
);

const offsetValid = computed(() => {
  if (!offsetRequired.value) return true;
  return effectiveOffsetNs.value !== null;
});

const formValid = computed(() => offsetValid.value);

// ── save ─────────────────────────────────────────────────────────────────────
const { saving, save } = useTimeReference();

async function onSave() {
  if (!formValid.value) {
    isValid.value = false;
    return;
  }
  const patch: TimeReferenceV2Patch = {
    timeReference: editMode.value,
    wallClockOffset: offsetRequired.value ? effectiveOffsetNs.value : null,
    wallClockOffsetSource:
      editSource.value.trim().length > 0 ? editSource.value.trim() : null,
  };
  const ok = await save(props.appId, patch);
  if (ok) {
    editing.value = false;
    emit("updated", patch);
  }
}
</script>

<template>
  <section>
    <!-- ── read mode ─────────────────────────────────────────────── -->
    <div v-if="!editing">
      <div class="d-flex align-center ga-2 mb-1">
        <span class="text-body-2 font-weight-medium">Time reference</span>
        <v-chip
          :color="isModeWallClock ? 'default' : 'primary'"
          size="x-small"
          variant="tonal"
        >
          {{ displayMode }}
        </v-chip>
        <v-spacer />
        <v-btn
          v-if="canEdit"
          density="compact"
          variant="text"
          size="small"
          icon="mdi-pencil-outline"
          aria-label="Edit time reference"
          @click="openEdit"
        />
      </div>

      <!-- EXPERIMENT_RELATIVE: show offset details -->
      <div
        v-if="!isModeWallClock"
        class="ml-1 d-flex flex-column ga-1 text-caption text-medium-emphasis"
      >
        <div>
          <span class="font-weight-medium">Wall-clock anchor (t=0):</span>
          {{ nsToIsoString(wallClockOffset) }}
          <span
            v-if="wallClockOffset"
            class="text-mono ml-1"
          >({{ wallClockOffset }} ns)</span>
        </div>
        <div v-if="wallClockOffsetSource">
          <span class="font-weight-medium">Offset source:</span>
          {{ wallClockOffsetSource }}
        </div>
      </div>

      <!-- WALL_CLOCK: brief note -->
      <div
        v-else
        class="ml-1 text-caption text-medium-emphasis"
      >
        Sample timestamps are wall-clock UTC nanoseconds.
      </div>
    </div>

    <!-- ── edit mode ─────────────────────────────────────────────── -->
    <div v-else>
      <div class="text-body-2 font-weight-medium mb-3">Time reference — edit</div>

      <!-- UIRULE-DROPDOWN-SEARCH-SORT: 2-option mode enum — no search/sort needed. -->
      <v-select
        v-model="editMode"
        label="Time-base mode"
        :items="TIME_REFERENCE_OPTIONS"
        item-title="title"
        item-value="value"
        variant="outlined"
        density="compact"
        class="mb-2"
      >
        <template #item="{ props: iProps, item }">
          <v-list-item v-bind="iProps" :subtitle="item.raw.subtitle" />
        </template>
      </v-select>

      <!-- Offset fields — only when EXPERIMENT_RELATIVE -->
      <template v-if="editMode === 'EXPERIMENT_RELATIVE'">
        <v-tabs
          v-model="editOffsetInputMode"
          density="compact"
          class="mb-2"
        >
          <v-tab value="datetime">Date / time</v-tab>
          <v-tab value="raw">Raw nanoseconds</v-tab>
        </v-tabs>

        <v-tabs-window v-model="editOffsetInputMode">
          <!-- datetime entry -->
          <v-tabs-window-item value="datetime">
            <div class="d-flex ga-2">
              <v-text-field
                v-model="editOffsetDateStr"
                label="Date (UTC)"
                placeholder="YYYY-MM-DD"
                variant="outlined"
                density="compact"
                :error="offsetRequired && !offsetValid && editOffsetInputMode === 'datetime'"
                :rules="[
                  (v: string) =>
                    !offsetRequired ||
                    /^\d{4}-\d{2}-\d{2}$/.test(v) ||
                    'Required — use YYYY-MM-DD',
                ]"
                class="flex-1-1"
              />
              <v-text-field
                v-model="editOffsetTimeStr"
                label="Time (UTC)"
                placeholder="HH:mm:ss"
                variant="outlined"
                density="compact"
                :rules="[
                  (v: string) =>
                    !offsetRequired ||
                    /^\d{2}:\d{2}:\d{2}$/.test(v) ||
                    'Required — use HH:mm:ss',
                ]"
                class="flex-1-1"
              />
            </div>
            <div
              v-if="effectiveOffsetNs !== null"
              class="text-caption text-medium-emphasis mb-2"
            >
              = {{ effectiveOffsetNs }} ns
            </div>
          </v-tabs-window-item>

          <!-- raw ns entry -->
          <v-tabs-window-item value="raw">
            <v-text-field
              v-model="editOffsetNsRaw"
              label="Wall-clock offset (nanoseconds)"
              placeholder="e.g. 1700000000000000000"
              variant="outlined"
              density="compact"
              :rules="[
                (v: string) =>
                  !offsetRequired ||
                  (/^\d+$/.test(v) && !isNaN(parseInt(v, 10))) ||
                  'Required — enter integer nanoseconds',
              ]"
              class="mb-0"
            />
            <div
              v-if="editOffsetNsRaw && effectiveOffsetNs !== null"
              class="text-caption text-medium-emphasis mb-2"
            >
              = {{ nsToIsoString(effectiveOffsetNs) }}
            </div>
          </v-tabs-window-item>
        </v-tabs-window>
      </template>

      <v-text-field
        v-model="editSource"
        label="Offset source (optional)"
        placeholder="e.g. GPS sync, NTP_marker, manual"
        variant="outlined"
        density="compact"
        class="mt-1 mb-2"
        hint="How was the wall-clock anchor determined?"
        persistent-hint
      />

      <!-- validation error summary -->
      <v-alert
        v-if="!formValid && !isValid"
        type="error"
        density="compact"
        class="mb-2 text-body-2"
      >
        Wall-clock offset is required when mode is Experiment-relative.
      </v-alert>

      <div class="d-flex justify-end ga-2">
        <v-btn
          variant="text"
          size="small"
          :disabled="saving"
          @click="cancelEdit"
        >
          Cancel
        </v-btn>
        <v-btn
          variant="flat"
          color="primary"
          size="small"
          :loading="saving"
          :disabled="!formValid"
          @click="onSave"
        >
          Save
        </v-btn>
      </div>
    </div>
  </section>
</template>

<style scoped>
.text-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-variant-numeric: tabular-nums;
  font-size: 0.85em;
}
</style>

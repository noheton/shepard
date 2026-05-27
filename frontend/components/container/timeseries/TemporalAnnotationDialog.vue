<script setup lang="ts">
/**
 * TS-ANNOT-B — create / edit a container-level temporal annotation.
 *
 * In create mode (no annotationAppId prop): calls POST and emits `saved`.
 * In edit mode (annotationAppId set): calls PATCH and emits `saved`.
 */
import {
  useTimeseriesContainerAnnotations,
  type PatchAnnotationBody,
} from "~/composables/containers/useTimeseriesContainerAnnotations";

const props = defineProps<{
  containerId: number;
  /** Pre-filled from brush selection (create mode). */
  initialStartNs?: number;
  initialEndNs?: number | null;
  /** Set to enable edit mode. */
  annotationAppId?: string;
  /** Pre-filled values for edit mode. */
  initialLabel?: string;
  initialDescription?: string | null;
}>();

const emit = defineEmits<{
  saved: [];
}>();

const showDialog = defineModel<boolean>("showDialog", { default: false });

const containerIdRef = computed(() => props.containerId);
const { createAnnotation, updateAnnotation, saving } =
  useTimeseriesContainerAnnotations(containerIdRef);

const isEdit = computed(() => !!props.annotationAppId);

// ── form state ────────────────────────────────────────────────────────────

const label = ref("");
const description = ref("");

// Human-readable date strings for input
const startDate = ref("");
const endDate = ref("");

function nsToDateString(ns: number | null | undefined): string {
  if (ns == null) return "";
  return new Date(ns / 1e6).toISOString().slice(0, 19).replace("T", " ");
}

function dateStringToNs(s: string): number | null {
  if (!s.trim()) return null;
  const ms = new Date(s.trim()).getTime();
  return isNaN(ms) ? null : ms * 1e6;
}

// Reset form when dialog opens
watch(showDialog, open => {
  if (!open) return;
  label.value = props.initialLabel ?? "";
  description.value = props.initialDescription ?? "";
  startDate.value = nsToDateString(props.initialStartNs);
  endDate.value = nsToDateString(props.initialEndNs);
});

// ── validation ────────────────────────────────────────────────────────────

const labelError = computed(() => {
  if (!label.value.trim()) return "Label is required";
  return "";
});

const startError = computed(() => {
  if (!startDate.value.trim()) return "Start time is required";
  if (dateStringToNs(startDate.value) == null) return "Invalid date format";
  return "";
});

const endError = computed(() => {
  if (!endDate.value.trim()) return "";
  if (dateStringToNs(endDate.value) == null) return "Invalid date format";
  const sNs = dateStringToNs(startDate.value);
  const eNs = dateStringToNs(endDate.value);
  if (sNs != null && eNs != null && eNs <= sNs) return "End must be after start";
  return "";
});

const isValid = computed(
  () => !labelError.value && !startError.value && !endError.value,
);

// ── save ──────────────────────────────────────────────────────────────────

async function save() {
  if (!isValid.value) return;
  const sNs = dateStringToNs(startDate.value)!;
  const eNs = dateStringToNs(endDate.value);

  let ok: boolean;
  if (isEdit.value && props.annotationAppId) {
    const body: PatchAnnotationBody = {
      startNs: sNs,
      endNs: eNs,
      label: label.value.trim(),
      description: description.value.trim() || null,
    };
    ok = await updateAnnotation(props.annotationAppId, body);
  } else {
    const result = await createAnnotation({
      startNs: sNs,
      endNs: eNs,
      label: label.value.trim(),
      description: description.value.trim() || null,
    });
    ok = result != null;
  }

  if (ok) {
    showDialog.value = false;
    emit("saved");
  }
}
</script>

<template>
  <v-dialog v-model="showDialog" max-width="520" persistent>
    <v-card>
      <v-card-title class="text-h6 pt-4 px-5">
        {{ isEdit ? "Edit annotation" : "New annotation" }}
      </v-card-title>

      <v-card-text class="px-5 pt-2">
        <v-text-field
          v-model="label"
          label="Label"
          :error-messages="labelError"
          autofocus
          density="compact"
          class="mb-2"
        />

        <v-row dense>
          <v-col cols="6">
            <v-text-field
              v-model="startDate"
              label="Start (YYYY-MM-DD HH:MM:SS)"
              :error-messages="startError"
              density="compact"
              hint="UTC"
              persistent-hint
            />
          </v-col>
          <v-col cols="6">
            <v-text-field
              v-model="endDate"
              label="End (optional — blank = point)"
              :error-messages="endError"
              density="compact"
              hint="UTC"
              persistent-hint
            />
          </v-col>
        </v-row>

        <v-text-field
          v-model="description"
          label="Description (optional)"
          density="compact"
          class="mt-3"
        />
      </v-card-text>

      <v-card-actions class="px-5 pb-4">
        <v-spacer />
        <v-btn variant="text" @click="showDialog = false">Cancel</v-btn>
        <v-btn
          color="primary"
          variant="flat"
          :loading="saving"
          :disabled="!isValid"
          @click="save"
        >
          {{ isEdit ? "Save" : "Create" }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

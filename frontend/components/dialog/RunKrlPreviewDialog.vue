<script setup lang="ts">
/**
 * KRL-INTERPRETER-06 — modal that gathers the inputs for POST /v2/krl/interpret
 * and renders the result inline.
 *
 * Sections:
 *   - Required: URDF picker + Target DataObject + TimeseriesContainer.
 *   - Advanced (collapsed): .dat companion files, base / tool frame,
 *     seed pose mode, timeStep, ikTolerance, maxIterations.
 *
 * Honest 502 handling: the backend returns 502 when the operator hasn't
 * brought up the krl-interpreter compose profile. The dialog surfaces that
 * via `KrlInterpretResultPanel` (operator hint shown verbatim).
 *
 * Per `aidocs/integrations/117 §13.1` the trajectory rendered upstream of
 * this dialog is interpreter-resolved offline replay, never as-executed
 * motion — the result panel labels accordingly.
 */
import {
  FileReferenceApi,
  type FileReference,
} from "@dlr-shepard/backend-client";
import { useKrlInterpret } from "~/composables/useKrlInterpret";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import {
  isKrlFormValid,
  buildKrlRequestBody,
  urdfPickerOptions,
  datPickerOptions,
  sameStemDatAppId,
} from "./runKrlPreviewHelpers";

interface Props {
  srcFileReference: FileReference;
  collectionId: number;
  dataObjectId: number;
  /** appId of the parent DataObject. The Run / preview defaults to this. */
  dataObjectAppId: string;
  /** Path back to the DataObject detail page (for "Back to DataObject"). */
  dataObjectPath: string;
}

const props = defineProps<Props>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

// ── Form state ────────────────────────────────────────────────────────────────

const collectionFileReferences = ref<FileReference[]>([]);
const isLoadingRefs = ref(false);
const urdfFileAppId = ref<string | null>(null);
const targetDataObjectAppId = ref<string>(props.dataObjectAppId);
const timeseriesContainerAppId = ref<string>("");
const datFileAppIds = ref<string[]>([]);

const advanced = ref([0]); // collapsed by default; user expands to see frames

const timeStep = ref<number>(0.01);
const ikTolerance = ref<number>(0.001);
const maxIterations = ref<number>(300);

const baseFrame = reactive({ x: 0, y: 0, z: 0, rx: 0, ry: 0, rz: 0 });
const toolFrame = reactive({ x: 0, y: 0, z: 0, rx: 0, ry: 0, rz: 0 });
const useBaseFrame = ref(false);
const useToolFrame = ref(false);

const seedPoseRaw = ref<string>(""); // comma-separated j1,j2,…

// ── Long-running hint ─────────────────────────────────────────────────────────

const longRunningHint = ref(false);
let longRunningTimer: ReturnType<typeof setTimeout> | null = null;

// ── Interpret composable ──────────────────────────────────────────────────────

const { run, loading, error, lastResponse, reset } = useKrlInterpret();

// ── Validation ────────────────────────────────────────────────────────────────

const formValid = computed(() =>
  isKrlFormValid({
    urdfFileAppId: urdfFileAppId.value,
    targetDataObjectAppId: targetDataObjectAppId.value,
    timeseriesContainerAppId: timeseriesContainerAppId.value,
  }),
);

// ── Collection scan: list all FileReferences for this DataObject ──────────────

async function fetchFileReferencesForDataObject() {
  isLoadingRefs.value = true;
  try {
    const refs = await useShepardApi(FileReferenceApi)
      .value.getAllFileReferences({
        collectionId: props.collectionId,
        dataObjectId: props.dataObjectId,
      })
      .catch(e => {
        handleError(e, "fetchFileReferencesForDataObject");
        return [] as FileReference[];
      });
    collectionFileReferences.value = refs;
  } finally {
    isLoadingRefs.value = false;
  }
}

// Filtered pickers
const urdfCandidates = computed(() => urdfPickerOptions(collectionFileReferences.value));
const datCandidates = computed(() => datPickerOptions(collectionFileReferences.value));

// Same-stem default for .dat companion
function applySameStemDatDefault() {
  if (!props.srcFileReference?.name) return;
  const match = sameStemDatAppId(props.srcFileReference.name, datCandidates.value);
  if (match && !datFileAppIds.value.includes(match)) {
    datFileAppIds.value = [match];
  }
}

watch(showDialog, async open => {
  if (open) {
    reset();
    longRunningHint.value = false;
    await fetchFileReferencesForDataObject();
    // If exactly one URDF candidate, preselect it (per design §1.1).
    const onlyUrdf = urdfCandidates.value[0];
    if (urdfCandidates.value.length === 1 && onlyUrdf) {
      urdfFileAppId.value = onlyUrdf.value;
    }
    applySameStemDatDefault();
  } else {
    // Clear long-running timer if the user closed the dialog mid-call.
    if (longRunningTimer !== null) {
      clearTimeout(longRunningTimer);
      longRunningTimer = null;
    }
  }
});

// ── Submit ────────────────────────────────────────────────────────────────────

async function submit() {
  if (!formValid.value) return;
  if (!urdfFileAppId.value) return;

  longRunningHint.value = false;
  if (longRunningTimer !== null) clearTimeout(longRunningTimer);
  longRunningTimer = setTimeout(() => {
    longRunningHint.value = true;
  }, 30_000);

  const srcFileAppId = props.srcFileReference.appId ?? "";
  if (!srcFileAppId) {
    // Defensive guard — the button shouldn't render when the FileReference
    // has no appId, but log loudly if it does.
    handleError("Source FileReference has no appId", "krl-interpret submit");
    return;
  }

  const body = buildKrlRequestBody({
    srcFileAppId,
    urdfFileAppId: urdfFileAppId.value,
    targetDataObjectAppId: targetDataObjectAppId.value,
    timeseriesContainerAppId: timeseriesContainerAppId.value,
    datFileAppIds: datFileAppIds.value,
    timeStep: timeStep.value,
    ikTolerance: ikTolerance.value,
    maxIterations: maxIterations.value,
    useBaseFrame: useBaseFrame.value,
    useToolFrame: useToolFrame.value,
    baseFrame,
    toolFrame,
    seedPoseRaw: seedPoseRaw.value,
  });

  await run(body);

  if (longRunningTimer !== null) {
    clearTimeout(longRunningTimer);
    longRunningTimer = null;
  }
  longRunningHint.value = false;
}

function close() {
  showDialog.value = false;
}

// Defer-built URDF payload URL for the deep-link in the result panel.
const urdfPayloadUrl = computed<string | null>(() => {
  if (!urdfFileAppId.value) return null;
  // We don't have a v2 payload URL by appId, so we link the v1 raw-payload
  // route on the FileReference picked, served at /shepard/api/...
  // The render route accepts ?urdfUrl= as an external-fetchable URL, so this
  // is the right shape per pages/shapes/render.vue lines 497–536.
  const config = useRuntimeConfig().public;
  const base = (config.backendApiUrl as string).replace(/\/$/, "");
  const ref = collectionFileReferences.value.find(
    r => r.appId === urdfFileAppId.value,
  );
  if (!ref) return null;
  // Singleton-style payload URL — same shape used by FilesTable.
  return (
    `${base}/collections/${props.collectionId}` +
    `/dataObjects/${props.dataObjectId}/fileReferences/${ref.id}/payload`
  );
});
</script>

<template>
  <v-dialog
    v-model="showDialog"
    max-width="720"
    persistent
    scrollable
    data-test="krl-run-preview-dialog"
  >
    <v-card class="bg-canvas">
      <v-card-title class="d-flex align-center ga-2">
        <v-icon color="primary">mdi-play-box-multiple-outline</v-icon>
        <span class="text-h5">Run / preview KRL program</span>
      </v-card-title>

      <v-card-subtitle>
        Resolve
        <span class="font-monospace">{{ srcFileReference.name }}</span>
        against a URDF and persist the joint trajectory.
      </v-card-subtitle>

      <v-divider />

      <v-card-text style="max-height: 70vh; overflow-y: auto">
        <!-- ── Required fields ───────────────────────────────────────── -->
        <div class="text-subtitle-2 mb-2">Required</div>

        <v-autocomplete
          v-model="urdfFileAppId"
          :items="urdfCandidates"
          :loading="isLoadingRefs"
          label="URDF FileReference"
          placeholder="Pick a .urdf file in this DataObject"
          variant="outlined"
          density="comfortable"
          class="mb-2"
          required
          data-test="krl-urdf-picker"
        />

        <v-text-field
          v-model="targetDataObjectAppId"
          label="Target DataObject appId"
          variant="outlined"
          density="comfortable"
          class="mb-2"
          required
          hint="Defaults to the parent of the .src file. Change to attach the trajectory elsewhere."
          persistent-hint
          data-test="krl-target-dataobject"
        />

        <v-text-field
          v-model="timeseriesContainerAppId"
          label="TimeseriesContainer appId"
          variant="outlined"
          density="comfortable"
          class="mb-2"
          required
          hint="The container the trajectory is written to. Tier-2 (KRL-INTERPRETER-05-FOLLOWUP-AUTO-CONTAINER) will auto-mint this."
          persistent-hint
          data-test="krl-ts-container"
        />

        <!-- ── Advanced (collapsed) ──────────────────────────────────── -->
        <v-expansion-panels v-model="advanced" class="mt-4">
          <v-expansion-panel>
            <v-expansion-panel-title>
              <span class="text-subtitle-2">Advanced</span>
              <template #actions>
                <v-icon>mdi-chevron-down</v-icon>
              </template>
            </v-expansion-panel-title>
            <v-expansion-panel-text>
              <v-autocomplete
                v-model="datFileAppIds"
                :items="datCandidates"
                label=".dat companion files"
                placeholder="Optional"
                variant="outlined"
                density="comfortable"
                multiple
                chips
                clearable
                class="mb-3"
                data-test="krl-dat-picker"
              />

              <div class="text-caption mb-1">
                Sampling
              </div>
              <v-row dense>
                <v-col cols="12" sm="4">
                  <v-text-field
                    v-model.number="timeStep"
                    label="timeStep (s)"
                    type="number"
                    variant="outlined"
                    density="comfortable"
                    step="0.001"
                  />
                </v-col>
                <v-col cols="12" sm="4">
                  <v-text-field
                    v-model.number="ikTolerance"
                    label="ikTolerance"
                    type="number"
                    variant="outlined"
                    density="comfortable"
                    step="0.0001"
                  />
                </v-col>
                <v-col cols="12" sm="4">
                  <v-text-field
                    v-model.number="maxIterations"
                    label="maxIterations"
                    type="number"
                    variant="outlined"
                    density="comfortable"
                  />
                </v-col>
              </v-row>

              <v-switch
                v-model="useBaseFrame"
                label="Override base frame"
                color="primary"
                density="compact"
                class="mt-2"
              />
              <v-row v-if="useBaseFrame" dense>
                <v-col v-for="axis in ['x', 'y', 'z', 'rx', 'ry', 'rz']" :key="`base-${axis}`" cols="4">
                  <v-text-field
                    v-model.number="(baseFrame as any)[axis]"
                    :label="`base.${axis}`"
                    type="number"
                    variant="outlined"
                    density="compact"
                  />
                </v-col>
              </v-row>

              <v-switch
                v-model="useToolFrame"
                label="Override tool frame"
                color="primary"
                density="compact"
                class="mt-2"
              />
              <v-row v-if="useToolFrame" dense>
                <v-col v-for="axis in ['x', 'y', 'z', 'rx', 'ry', 'rz']" :key="`tool-${axis}`" cols="4">
                  <v-text-field
                    v-model.number="(toolFrame as any)[axis]"
                    :label="`tool.${axis}`"
                    type="number"
                    variant="outlined"
                    density="compact"
                  />
                </v-col>
              </v-row>

              <v-text-field
                v-model="seedPoseRaw"
                label="Seed pose (comma-separated joint angles)"
                placeholder="e.g. 0, -1.57, 1.57, 0, 1.57, 0"
                variant="outlined"
                density="comfortable"
                class="mt-2"
                hint="Leave blank to use the URDF zero pose."
                persistent-hint
              />
            </v-expansion-panel-text>
          </v-expansion-panel>
        </v-expansion-panels>

        <!-- ── Progress + long-running hint ──────────────────────────── -->
        <div v-if="loading" class="mt-4">
          <v-progress-linear indeterminate color="primary" />
          <div class="text-caption text-medium-emphasis mt-1">
            Calling the KRL interpreter sidecar…
          </div>
          <v-alert
            v-if="longRunningHint"
            type="info"
            variant="tonal"
            density="compact"
            class="mt-2"
          >
            Still running… large programs can take 30 s or more.
          </v-alert>
        </div>

        <!-- ── Result panel ──────────────────────────────────────────── -->
        <div v-if="error || lastResponse" class="mt-4">
          <KrlInterpretResultPanel
            :response="lastResponse"
            :error="error"
            :urdf-payload-url="urdfPayloadUrl"
            :data-object-path="dataObjectPath"
          />
        </div>
      </v-card-text>

      <v-divider />

      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" @click="close">Close</v-btn>
        <v-btn
          color="primary"
          variant="flat"
          :disabled="!formValid || loading"
          :loading="loading"
          data-test="krl-submit"
          @click="submit"
        >
          Resolve
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

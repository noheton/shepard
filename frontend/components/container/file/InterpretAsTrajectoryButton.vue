<script setup lang="ts">
/**
 * V2CONV-B5-FE / URSCRIPT-TRAJECTORY-1-FE — "Interpret as joint trajectory"
 * affordance on a KRL .src/.krl OR URScript .urscript/.script FileReference detail
 * page.
 *
 * The bespoke KRL interpret subsystem dissolved into the generic MAPPING_RECIPE
 * mechanism (aidocs/platform/191 decision #2). This button detects the file kind
 * (KRL or URScript), gathers the bindings (URDF + target DataObject +
 * TimeseriesContainer), creates a MAPPING_RECIPE template (targeting the
 * appropriate shape IRI) via `POST /v2/templates`, then materializes it via
 * `POST /v2/mappings/{templateAppId}/materialize` — minting a derived joint-
 * trajectory TimeseriesReference and surfacing a link to it.
 *
 * KRL additionally shows a .dat companion-file picker (URScript has no .dat files).
 *
 * Reachability: in-context on the FileReference detail page. Per the v2-only +
 * appId rules it addresses entities by appId, targets /v2/, and never asks for a
 * path/URL.
 */
import {
  FileReferenceApi,
  type FileReference,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import {
  useKrlTrajectory,
  defaultTrajectoryNameFor as krlDefaultName,
} from "~/composables/useKrlTrajectory";
import {
  useUrScriptTrajectory,
  defaultTrajectoryNameFor as urScriptDefaultName,
} from "~/composables/useUrScriptTrajectory";
import {
  isKrlSrcFile,
  isUrScriptFile,
  isTrajectoryFormValid,
  urdfPickerOptions,
  datPickerOptions,
} from "./interpretAsTrajectoryHelpers";

interface Props {
  fileReference: FileReference;
  collectionId: number;
  dataObjectId: number;
  /** appId of the parent DataObject — the trajectory defaults to attaching here. */
  dataObjectAppId: string;
  /** Path back to the DataObject detail page (for the result link). */
  dataObjectPath: string;
  /** Whether the caller has write access on the parent collection. */
  canEdit: boolean;
}

const props = defineProps<Props>();

/**
 * Detected file kind: "krl" | "urscript" | null (not a robot program source file).
 */
const fileKind = computed<"krl" | "urscript" | null>(() => {
  const name = props.fileReference?.name ?? "";
  if (isKrlSrcFile(name)) return "krl";
  if (isUrScriptFile(name)) return "urscript";
  return null;
});

const isRobotSrc = computed(() => fileKind.value !== null);

const showDialog = ref(false);
const collectionFileReferences = ref<FileReference[]>([]);
const isLoadingRefs = ref(false);

const urdfFileAppId = ref<string | null>(null);
const targetDataObjectAppId = ref<string>(props.dataObjectAppId);
const timeseriesContainerAppId = ref<string>("");
const datFileAppIds = ref<string[]>([]);

const submitError = ref<string | null>(null);
const derivedReferenceAppId = ref<string | null>(null);

const krl = useKrlTrajectory();
const urscript = useUrScriptTrajectory();

/** True while either composable is loading. */
const submitting = computed(() => krl.loading.value || urscript.loading.value);

const urdfCandidates = computed(() => urdfPickerOptions(collectionFileReferences.value));
const datCandidates = computed(() => datPickerOptions(collectionFileReferences.value));

const formValid = computed(() =>
  isTrajectoryFormValid({
    urdfFileAppId: urdfFileAppId.value,
    targetDataObjectAppId: targetDataObjectAppId.value,
    timeseriesContainerAppId: timeseriesContainerAppId.value,
  }),
);

const dialogTitle = computed(() =>
  fileKind.value === "urscript"
    ? "Interpret URScript as joint trajectory"
    : "Interpret as joint trajectory",
);

const tooltipText = computed(() =>
  fileKind.value === "urscript"
    ? "You need write access on this collection to interpret the URScript program."
    : "You need write access on this collection to interpret the KRL program.",
);

const sidecarLabel = computed(() =>
  fileKind.value === "urscript"
    ? "Creating the recipe + calling the URScript interpreter sidecar…"
    : "Creating the recipe + calling the KRL interpreter sidecar…",
);

const targetHintText = computed(() =>
  fileKind.value === "urscript"
    ? "Defaults to the parent of the .urscript file. Change to attach the trajectory elsewhere."
    : "Defaults to the parent of the .src file. Change to attach the trajectory elsewhere.",
);

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

watch(showDialog, async open => {
  if (open) {
    submitError.value = null;
    derivedReferenceAppId.value = null;
    await fetchFileReferencesForDataObject();
    const onlyUrdf = urdfCandidates.value[0];
    if (urdfCandidates.value.length === 1 && onlyUrdf) {
      urdfFileAppId.value = onlyUrdf.value;
    }
  }
});

async function submit() {
  if (!formValid.value || !urdfFileAppId.value) return;
  const srcAppId = props.fileReference?.appId ?? "";
  if (!srcAppId) {
    submitError.value = "Source FileReference has no appId — reload the page and try again.";
    return;
  }
  submitError.value = null;
  derivedReferenceAppId.value = null;

  if (fileKind.value === "urscript") {
    await submitUrScript(srcAppId);
  } else {
    await submitKrl(srcAppId);
  }
}

async function submitKrl(srcAppId: string) {
  const created = await krl.createTemplate({
    name: krlDefaultName(props.fileReference?.name),
    description: null,
    srcFileReferenceAppId: srcAppId,
    urdfFileReferenceAppId: urdfFileAppId.value!,
    targetDataObjectAppId: targetDataObjectAppId.value.trim(),
    timeseriesContainerAppId: timeseriesContainerAppId.value.trim(),
    datFileReferenceAppIds: datFileAppIds.value.length > 0 ? [...datFileAppIds.value] : null,
  });
  if (!created.ok) {
    submitError.value =
      created.status === 403
        ? "You don't have permission to create a trajectory recipe here."
        : `Could not create the trajectory recipe (HTTP ${created.status}): ${created.detail}`;
    return;
  }
  const result = await krl.materialize(created.templateAppId, srcAppId, urdfFileAppId.value!);
  if (!result.ok) {
    submitError.value = `Interpretation failed (HTTP ${result.status}): ${result.detail}`;
    return;
  }
  derivedReferenceAppId.value = result.derivedReferenceAppId;
}

async function submitUrScript(srcAppId: string) {
  const created = await urscript.createTemplate({
    name: urScriptDefaultName(props.fileReference?.name),
    description: null,
    urscriptFileReferenceAppId: srcAppId,
    urdfFileReferenceAppId: urdfFileAppId.value!,
    targetDataObjectAppId: targetDataObjectAppId.value.trim(),
    timeseriesContainerAppId: timeseriesContainerAppId.value.trim(),
  });
  if (!created.ok) {
    submitError.value =
      created.status === 403
        ? "You don't have permission to create a trajectory recipe here."
        : `Could not create the trajectory recipe (HTTP ${created.status}): ${created.detail}`;
    return;
  }
  const result = await urscript.materialize(
    created.templateAppId,
    srcAppId,
    urdfFileAppId.value!,
  );
  if (!result.ok) {
    submitError.value = `Interpretation failed (HTTP ${result.status}): ${result.detail}`;
    return;
  }
  derivedReferenceAppId.value = result.derivedReferenceAppId;
}

function close() {
  showDialog.value = false;
}
</script>

<template>
  <span v-if="isRobotSrc" class="interpret-trajectory-wrap">
    <v-tooltip :disabled="canEdit" location="bottom">
      <template #activator="{ props: tooltipProps }">
        <span v-bind="tooltipProps">
          <v-btn
            color="primary"
            variant="flat"
            prepend-icon="mdi-play-box-multiple-outline"
            :disabled="!canEdit"
            data-test="interpret-as-trajectory-button"
            @click="showDialog = true"
          >
            Interpret as joint trajectory
          </v-btn>
        </span>
      </template>
      {{ tooltipText }}
    </v-tooltip>

    <v-dialog
      v-model="showDialog"
      max-width="640"
      persistent
      scrollable
      data-test="interpret-as-trajectory-dialog"
    >
      <v-card class="bg-canvas">
        <v-card-title class="d-flex align-center ga-2">
          <v-icon color="primary">mdi-play-box-multiple-outline</v-icon>
          <span class="text-h5">{{ dialogTitle }}</span>
        </v-card-title>

        <v-card-subtitle>
          Bind
          <span class="font-monospace">{{ fileReference.name }}</span>
          to a URDF and materialize the joint trajectory as a new
          TimeseriesReference (via a MAPPING_RECIPE).
        </v-card-subtitle>

        <v-divider />

        <v-card-text style="max-height: 70vh; overflow-y: auto">
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
            data-test="trajectory-urdf-picker"
          />

          <v-text-field
            v-model="targetDataObjectAppId"
            label="Target DataObject appId"
            variant="outlined"
            density="comfortable"
            class="mb-2"
            :hint="targetHintText"
            persistent-hint
            data-test="trajectory-target-dataobject"
          />

          <v-text-field
            v-model="timeseriesContainerAppId"
            label="TimeseriesContainer appId"
            variant="outlined"
            density="comfortable"
            class="mb-2"
            hint="The container the trajectory channels are written to."
            persistent-hint
            data-test="trajectory-ts-container"
          />

          <!-- .dat companion files: KRL-only -->
          <v-autocomplete
            v-if="fileKind === 'krl'"
            v-model="datFileAppIds"
            :items="datCandidates"
            label=".dat companion files (optional)"
            variant="outlined"
            density="comfortable"
            multiple
            chips
            clearable
            class="mb-2"
            data-test="trajectory-dat-picker"
          />

          <div v-if="submitting" class="mt-4">
            <v-progress-linear indeterminate color="primary" />
            <div class="text-caption text-medium-emphasis mt-1">
              {{ sidecarLabel }}
            </div>
          </div>

          <v-alert
            v-if="submitError"
            type="error"
            variant="tonal"
            density="compact"
            class="mt-4"
            data-test="trajectory-error"
          >
            {{ submitError }}
          </v-alert>

          <v-alert
            v-if="derivedReferenceAppId"
            type="success"
            variant="tonal"
            density="compact"
            class="mt-4"
            data-test="trajectory-success"
          >
            Trajectory materialized as TimeseriesReference
            <span class="font-monospace">{{ derivedReferenceAppId }}</span
            >.
            <NuxtLink :to="dataObjectPath" class="ml-1">Back to DataObject</NuxtLink>
          </v-alert>
        </v-card-text>

        <v-divider />

        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" :disabled="submitting" @click="close">Close</v-btn>
          <v-btn
            color="primary"
            variant="flat"
            :disabled="!formValid || submitting"
            :loading="submitting"
            data-test="trajectory-submit"
            @click="submit"
          >
            Interpret
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </span>
</template>

<style lang="scss" scoped>
.interpret-trajectory-wrap {
  display: inline-block;
}
</style>

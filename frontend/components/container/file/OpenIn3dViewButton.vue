<script setup lang="ts">
/**
 * V2CONV-B4-FE — "Open in 3D view" affordance on a URDF FileReference detail
 * page. Replaces the bespoke OpenInSceneGraphButton, which routed to the
 * now-deleted `/scene-graphs/*` editor.
 *
 * The bespoke scene-graph subsystem dissolved into the generic MAPPING_RECIPE
 * mechanism (aidocs/platform/191 decision #2). This button:
 *  - if a scene-graph-play MAPPING_RECIPE template already exists for this URDF
 *    (back-annotation `urn:shepard:mapping:scenegraph-template-appId`) → routes
 *    straight to the play page.
 *  - otherwise → creates a MAPPING_RECIPE template (targeting SceneGraphPlayShape,
 *    binding this URDF FileReference) via `POST /v2/templates`, then routes to
 *    the play page, which materializes + renders it.
 *
 * Reachability: this keeps the URDF 3D-view affordance in-context on the
 * FileReference detail page (CLAUDE.md "tool entry points are in-context first"
 * + "every shipped feature reachable from top-nav before beta").
 */
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";
import { hasSceneGraphRole } from "./openIn3dViewButtonHelpers";
import {
  useSceneGraphPlay,
  findSceneGraphTemplateAppId,
  default3dViewNameFor,
  sceneGraphPlayRouteFor,
} from "~/composables/useSceneGraphPlay";

interface Props {
  /** FileReference name — used by the filename eligibility fallback. */
  fileReferenceName: string;
  /** Numeric ids needed to instantiate `AnnotatedReference`. */
  collectionId: number;
  dataObjectId: number;
  fileReferenceId: number;
  /** Singleton FileReference appId — the handle the MAPPING_RECIPE binds. */
  fileReferenceAppId?: string | null;
}
const props = defineProps<Props>();

const annotations = ref<SemanticAnnotation[]>([]);
const isLoading = ref(true);
const showCreateModal = ref(false);
const viewName = ref("");
const viewDescription = ref("");
const submitError = ref<string | null>(null);
const { loading: submitting, createTemplate } = useSceneGraphPlay();

const annotated = computed(
  () =>
    new AnnotatedReference(
      props.collectionId,
      props.dataObjectId,
      props.fileReferenceId,
    ),
);

async function refreshAnnotations() {
  isLoading.value = true;
  try {
    annotations.value = await annotated.value.fetchAnnotations();
  } catch (e) {
    handleError(e, "fetching 3D-view annotations");
    annotations.value = [];
  } finally {
    isLoading.value = false;
  }
}

refreshAnnotations();
onAnnotationsUpdated(refreshAnnotations);

const isEligible = computed(() =>
  hasSceneGraphRole(annotations.value, props.fileReferenceName),
);
const existingTemplateAppId = computed(() =>
  findSceneGraphTemplateAppId(annotations.value),
);
const canSubmit = computed(
  () => !!props.fileReferenceAppId && !submitting.value,
);

function openCreateModal() {
  viewName.value = default3dViewNameFor(props.fileReferenceName);
  viewDescription.value = "";
  submitError.value = null;
  showCreateModal.value = true;
}

function onClick() {
  const id = existingTemplateAppId.value;
  if (id) {
    navigateTo(sceneGraphPlayRouteFor(id));
    return;
  }
  openCreateModal();
}

async function onConfirmCreate() {
  if (!props.fileReferenceAppId) {
    submitError.value =
      "Missing FileReference appId — reload the page and try again.";
    return;
  }
  submitError.value = null;
  const result = await createTemplate({
    name: viewName.value.trim() || default3dViewNameFor(props.fileReferenceName),
    description: viewDescription.value.trim() || null,
    urdfFileReferenceAppId: props.fileReferenceAppId,
  });
  if (result.ok) {
    showCreateModal.value = false;
    navigateTo(sceneGraphPlayRouteFor(result.templateAppId));
    return;
  }
  submitError.value =
    result.status === 403
      ? "You don't have permission to create a 3D view here."
      : `Could not create the 3D view (HTTP ${result.status}): ${result.detail}`;
}
</script>

<template>
  <span v-if="!isLoading && isEligible" class="open-in-3d-view-wrap">
    <v-btn
      color="primary"
      variant="flat"
      prepend-icon="mdi-cube-scan"
      data-test="open-in-3d-view-button"
      @click="onClick"
    >
      {{ existingTemplateAppId ? "Open in 3D view" : "Create 3D view from this URDF" }}
    </v-btn>

    <v-dialog v-model="showCreateModal" max-width="540" persistent>
      <v-card>
        <v-card-title class="d-flex align-center ga-2">
          <v-icon size="small" color="primary">mdi-cube-scan</v-icon>
          Create 3D view from URDF
        </v-card-title>
        <v-card-text>
          <v-text-field
            v-model="viewName"
            label="View name"
            density="comfortable"
            variant="outlined"
            data-test="view-name-input"
            :disabled="submitting"
            autofocus
          />
          <v-textarea
            v-model="viewDescription"
            label="Description (optional)"
            density="comfortable"
            variant="outlined"
            rows="2"
            auto-grow
            data-test="view-description-input"
            :disabled="submitting"
          />
          <v-alert
            v-if="submitError"
            type="error"
            variant="tonal"
            density="compact"
            class="mt-2"
            data-test="view-create-error"
          >
            {{ submitError }}
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn
            variant="text"
            :disabled="submitting"
            data-test="view-create-cancel"
            @click="showCreateModal = false"
          >
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            variant="flat"
            :loading="submitting"
            :disabled="!canSubmit"
            data-test="view-create-confirm"
            @click="onConfirmCreate"
          >
            Create 3D view
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </span>
</template>

<style lang="scss" scoped>
.open-in-3d-view-wrap {
  display: inline-block;
}
</style>

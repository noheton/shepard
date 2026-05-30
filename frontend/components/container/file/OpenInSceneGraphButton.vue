<script setup lang="ts">
/**
 * SCENEGRAPH-NAV-02 + SCENEGRAPH-CREATE-FROM-URDF-2-FE — "Open in
 * scene-graph editor" / "Create scene from this URDF" button on the
 * FileReference detail page.
 *
 * Conditional render: only mounts when one of the scene-graph-eligibility
 * signals are present on the FileReference (see `hasSceneGraphRole` in
 * `./openInSceneGraphButtonHelpers.ts` for the predicate set + rationale).
 *
 * Two button states, switched by the presence of the back-annotation
 * `urn:shepard:scenegraph:scene-appId`:
 *  - scene exists → button reads "Open in scene-graph editor" and
 *    routes to `/scene-graphs/{sceneAppId}`.
 *  - scene does NOT exist → button reads "Create scene from this URDF"
 *    and opens a form modal that POSTs to
 *    `/v2/scene-graphs/from-urdf/{fileReferenceAppId}` (backend
 *    SCENEGRAPH-CREATE-FROM-URDF-1, shipped 2026-05-30 in `fc52785f3`).
 *
 * Test coverage: pure helpers in `openInSceneGraphButtonHelpers.ts` and
 * `useScenegraphFromUrdf.ts` are exhaustively covered by Vitest.
 */
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";
import {
  hasSceneGraphRole,
  findSceneAppId,
  sceneGraphRouteFor,
} from "./openInSceneGraphButtonHelpers";
import {
  defaultSceneNameFor,
  decideAfterCreate,
  useScenegraphFromUrdf,
} from "~/composables/useScenegraphFromUrdf";

interface Props {
  /** FileReference name — used by the cheap filename fallback in the helper. */
  fileReferenceName: string;
  /** Numeric ids needed to instantiate `AnnotatedReference`. */
  collectionId: number;
  dataObjectId: number;
  fileReferenceId: number;
  /** Singleton FileReference appId — the handle the backend POST endpoint wants. */
  fileReferenceAppId?: string | null;
}
const props = defineProps<Props>();

const annotations = ref<SemanticAnnotation[]>([]);
const isLoading = ref(true);
const showCreateModal = ref(false);
const sceneName = ref("");
const sceneDescription = ref("");
const submitError = ref<string | null>(null);
const showRetry = ref(false);
const { loading: submitting, createFromUrdf } = useScenegraphFromUrdf();

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
    handleError(e, "fetching scene-graph annotations");
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
const sceneAppId = computed(() => findSceneAppId(annotations.value));
const canSubmit = computed(
  () => !!props.fileReferenceAppId && !submitting.value,
);

function openCreateModal() {
  sceneName.value = defaultSceneNameFor(props.fileReferenceName);
  sceneDescription.value = "";
  submitError.value = null;
  showRetry.value = false;
  showCreateModal.value = true;
}

function onClick() {
  const id = sceneAppId.value;
  if (id) {
    navigateTo(sceneGraphRouteFor(id));
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
  showRetry.value = false;
  const result = await createFromUrdf({
    fileReferenceAppId: props.fileReferenceAppId,
    name: sceneName.value.trim() || null,
    description: sceneDescription.value.trim() || null,
  });
  const decision = decideAfterCreate(result);
  if (decision.kind === "navigate") {
    showCreateModal.value = false;
    navigateTo(decision.path);
    return;
  }
  if (decision.kind === "retry") {
    showRetry.value = true;
  }
  submitError.value = decision.message;
}
</script>

<template>
  <span v-if="!isLoading && isEligible" class="open-in-scene-graph-wrap">
    <v-btn
      color="primary"
      variant="flat"
      prepend-icon="mdi-graph-outline"
      data-test="open-in-scene-graph-button"
      @click="onClick"
    >
      {{ sceneAppId ? "Open in scene-graph editor" : "Create scene from this URDF" }}
    </v-btn>

    <v-dialog v-model="showCreateModal" max-width="540" persistent>
      <v-card>
        <v-card-title class="d-flex align-center ga-2">
          <v-icon size="small" color="primary">mdi-graph-outline</v-icon>
          Create scene from URDF
        </v-card-title>
        <v-card-text>
          <v-text-field
            v-model="sceneName"
            label="Scene name"
            density="comfortable"
            variant="outlined"
            data-test="scene-name-input"
            :disabled="submitting"
            autofocus
          />
          <v-textarea
            v-model="sceneDescription"
            label="Description (optional)"
            density="comfortable"
            variant="outlined"
            rows="2"
            auto-grow
            data-test="scene-description-input"
            :disabled="submitting"
          />
          <v-alert
            v-if="submitError"
            type="error"
            variant="tonal"
            density="compact"
            class="mt-2"
            data-test="scene-create-error"
          >
            {{ submitError }}
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn
            variant="text"
            :disabled="submitting"
            data-test="scene-create-cancel"
            @click="showCreateModal = false"
          >
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            variant="flat"
            :loading="submitting"
            :disabled="!canSubmit"
            data-test="scene-create-confirm"
            @click="onConfirmCreate"
          >
            {{ showRetry ? "Retry" : "Create scene" }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </span>
</template>

<style lang="scss" scoped>
.open-in-scene-graph-wrap {
  display: inline-block;
}
</style>

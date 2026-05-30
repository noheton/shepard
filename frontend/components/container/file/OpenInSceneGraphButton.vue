<script setup lang="ts">
/**
 * SCENEGRAPH-NAV-02 — "Open in scene-graph editor" / "Create scene from this
 * URDF" button on the FileReference detail page.
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
 *    and opens a confirm dialog that calls
 *    `POST /v2/scene-graphs/from-urdf/{fileReferenceAppId}`.
 *    201 → navigate to new scene; 409 → navigate to existing scene.
 *
 * Annotation source: this component does its own fetch via
 * `AnnotatedReference.fetchAnnotations()` rather than wiring up an event
 * bus on the parent page — the parent already mounts
 * `SemanticAnnotationList` which fires its own fetch, so there are two
 * reads on the same URL; that's fine (cached at the HTTP layer) and the
 * alternative (lifting state into the page just for this button) bloats
 * the page for a single condition.
 *
 * Test coverage: pure helpers in `openInSceneGraphButtonHelpers.ts` are
 * exhaustively covered by Vitest. The component itself is a thin
 * Vuetify wrapper around them.
 */
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";
import { useSceneGraph } from "~/composables/useSceneGraph";
import {
  hasSceneGraphRole,
  findSceneAppId,
  sceneGraphRouteFor,
} from "./openInSceneGraphButtonHelpers";

interface Props {
  /** FileReference name — used by the cheap filename fallback in the helper. */
  fileReferenceName: string;
  /** Numeric ids needed to instantiate `AnnotatedReference`. */
  collectionId: number;
  dataObjectId: number;
  fileReferenceId: number;
  /** appId of the FileReference — passed to POST /v2/scene-graphs/from-urdf/{appId}. */
  fileReferenceAppId?: string;
}
const props = defineProps<Props>();

const annotations = ref<SemanticAnnotation[]>([]);
const isLoading = ref(true);
const showCreateModal = ref(false);
const creating = ref(false);
const createError = ref<string | null>(null);

const { createFromUrdf, error: sgError } = useSceneGraph();

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

function onClick() {
  const id = sceneAppId.value;
  if (id) {
    navigateTo(sceneGraphRouteFor(id));
    return;
  }
  createError.value = null;
  showCreateModal.value = true;
}

async function onConfirmCreate() {
  if (!props.fileReferenceAppId) return;
  creating.value = true;
  createError.value = null;
  const result = await createFromUrdf(props.fileReferenceAppId);
  creating.value = false;
  if (!result) {
    createError.value = sgError.value?.message ?? "Scene creation failed.";
    return;
  }
  showCreateModal.value = false;
  navigateTo(sceneGraphRouteFor(result.appId));
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

    <v-dialog v-model="showCreateModal" max-width="480">
      <v-card>
        <v-card-title class="d-flex align-center ga-2">
          <v-icon size="small" color="primary">mdi-graph-outline</v-icon>
          Create scene from this URDF
        </v-card-title>
        <v-card-text>
          <p class="mb-0">
            Parse <strong>{{ fileReferenceName }}</strong> and mint a new
            scene graph. The scene will appear under Scene Graphs and this
            button will switch to "Open in scene-graph editor".
          </p>
          <v-alert
            v-if="createError"
            type="error"
            variant="tonal"
            class="mt-3 mb-0"
            density="compact"
          >
            {{ createError }}
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn
            variant="text"
            :disabled="creating"
            @click="showCreateModal = false"
          >
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            variant="flat"
            :loading="creating"
            :disabled="!fileReferenceAppId || creating"
            data-test="confirm-create-scene-button"
            @click="onConfirmCreate"
          >
            Create
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

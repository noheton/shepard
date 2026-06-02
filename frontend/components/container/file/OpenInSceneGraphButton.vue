<script setup lang="ts">
/**
 * SCENEGRAPH-NAV-02 — "Open in scene-graph editor" button on the
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
 *  - scene does NOT exist → button reads "Open in scene-graph editor"
 *    and routes to `/scene-graphs/` landing page where the operator
 *    can enter an appId (the backend `POST /v2/scene-graphs/from-urdf`
 *    one-click mint is queued as SCENEGRAPH-CREATE-FROM-URDF-1).
 *
 * Test coverage: pure helpers in `openInSceneGraphButtonHelpers.ts`
 * are exhaustively covered by Vitest.
 */
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";
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
  /** Singleton FileReference appId — available for future use when the
   *  SCENEGRAPH-CREATE-FROM-URDF-1 backend endpoint ships. */
  fileReferenceAppId?: string | null;
}
const props = defineProps<Props>();

const annotations = ref<SemanticAnnotation[]>([]);
const isLoading = ref(true);

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
  // No scene minted yet — navigate to the scene-graphs landing page.
  // SCENEGRAPH-CREATE-FROM-URDF-1 will add a one-click mint here.
  navigateTo("/scene-graphs/");
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
      Open in scene-graph editor
    </v-btn>
  </span>
</template>

<style lang="scss" scoped>
.open-in-scene-graph-wrap {
  display: inline-block;
}
</style>

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
 * `urn:shepard:scenegraph:scene-appId` (written by
 * `examples/mffd-rdk-urdf-showcase/scenegraph/build_mffd_scene.py`):
 *  - scene exists → button reads "Open in scene-graph editor" and
 *    routes to `/scene-graphs/{sceneAppId}`.
 *  - scene does NOT exist → button reads "Create scene from this URDF"
 *    and opens a modal explaining today's bootstrap is script-driven.
 *    Backend "click to mint" flow is filed as
 *    `SCENEGRAPH-CREATE-FROM-URDF-1` in `aidocs/16`.
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
}
const props = defineProps<Props>();

const annotations = ref<SemanticAnnotation[]>([]);
const isLoading = ref(true);
const showCreateModal = ref(false);

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
  showCreateModal.value = true;
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

    <v-dialog v-model="showCreateModal" max-width="540">
      <v-card>
        <v-card-title class="d-flex align-center ga-2">
          <v-icon size="small" color="primary">mdi-information-outline</v-icon>
          Scene bootstrap is currently script-driven
        </v-card-title>
        <v-card-text>
          <p class="mb-3">
            No <code>:DigitalTwinScene</code> exists yet for this FileReference.
            For now, minting a scene from a URDF runs from the command line:
          </p>
          <pre class="bootstrap-snippet">python3 examples/mffd-rdk-urdf-showcase/scenegraph/build_mffd_scene.py \
    --host https://&lt;your-shepard-host&gt; \
    --apikey "$SHEPARD_API_KEY"</pre>
          <p class="mt-3 mb-0 text-medium-emphasis text-body-2">
            The script parses the URDF, POSTs the scene to
            <code>/v2/scene-graphs</code>, and writes the
            <code>urn:shepard:scenegraph:scene-appId</code> back-annotation on
            this FileReference. On the next page load the button switches to
            "Open in scene-graph editor".<br>
            The "click-to-mint" backend flow is tracked as
            <strong>SCENEGRAPH-CREATE-FROM-URDF-1</strong> in
            <code>aidocs/16</code>.
          </p>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="showCreateModal = false">Close</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </span>
</template>

<style lang="scss" scoped>
.open-in-scene-graph-wrap {
  display: inline-block;
}
.bootstrap-snippet {
  font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
  font-size: 0.825rem;
  background: rgba(var(--v-theme-on-surface), 0.04);
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  white-space: pre-wrap;
}
</style>

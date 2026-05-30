<script setup lang="ts">
/**
 * SCENEGRAPH-REST-1-UI — real scene-graph browser + editor.
 *
 * Replaces the placeholder shipped with SCENEGRAPH-REST-1 (see
 * `aidocs/16` SCENEGRAPH-REST-1-UI). Surfaces:
 *
 *   - Frame tree (left column) — recursive `:HAS_PARENT_FRAME` walk.
 *   - Inspector pane (right column, sticky) — transform + parent + kind
 *     edit + Save via `PATCH /v2/scene-graphs/{appId}/frames/{frameAppId}`.
 *   - Joints table (bottom) — sibling table with parent/child labels.
 *   - "Add frame" / "Add joint" / "Delete frame subtree" / "Delete joint"
 *     actions.
 *   - URDF export download.
 *   - 404 state when the scene appId doesn't exist.
 *   - Empty state (no frames) with an "Add root frame" CTA.
 *
 * Write permission today is the same as the backend: `@Authenticated`.
 * The proper per-scene permission walk is queued as SCENEGRAPH-PERMS-1.
 */
import type {
  CreateFrameRequestIO,
  CreateJointRequestIO,
  FrameIO,
  JointIO,
  PatchFrameRequestIO,
  SceneGraphIO,
} from "~/composables/useSceneGraph";
import {
  countDescendants,
  indexFramesByParent,
  urdfDownloadFilename,
  useSceneGraph,
} from "~/composables/useSceneGraph";

useHead({ title: "Scene graph | shepard" });

const route = useRoute();
const sceneAppId = computed(() => String(route.params.appId ?? ""));

const sceneGraph = useSceneGraph();

const scene = ref<SceneGraphIO | null>(null);
const notFound = ref(false);
const selectedFrameAppId = ref<string | null>(null);
const showAddFrame = ref(false);
const showAddJoint = ref(false);
const showDeleteFrame = ref(false);
const frameToDelete = ref<FrameIO | null>(null);

// Today's permission gate matches the backend: any authenticated user can
// write. SCENEGRAPH-PERMS-1 row tracks the per-scene permission walk.
const canWrite = computed(() => true);

const frames = computed<FrameIO[]>(() => scene.value?.frames ?? []);
const joints = computed<JointIO[]>(() => scene.value?.joints ?? []);

const selectedFrame = computed<FrameIO | null>(() => {
  if (!selectedFrameAppId.value) return null;
  return frames.value.find((f) => f.appId === selectedFrameAppId.value) ?? null;
});

const framesByAppId = computed(() => {
  const m = new Map<string, FrameIO>();
  for (const f of frames.value) m.set(f.appId, f);
  return m;
});

async function loadScene(): Promise<void> {
  notFound.value = false;
  const result = await sceneGraph.fetchScene(sceneAppId.value);
  if (!result) {
    if (sceneGraph.error.value?.status === 404) notFound.value = true;
    scene.value = null;
    return;
  }
  scene.value = result;
}

onMounted(loadScene);
watch(sceneAppId, loadScene);

function onSelectFrame(frame: FrameIO): void {
  selectedFrameAppId.value = frame.appId;
}

async function onSaveFrame(patch: Partial<FrameIO>): Promise<void> {
  if (!selectedFrame.value) return;
  const body: PatchFrameRequestIO = { ...patch };
  // Optimistic update — patch local state, revert on failure.
  const prev = { ...selectedFrame.value };
  Object.assign(selectedFrame.value, patch);
  const result = await sceneGraph.patchFrame(
    sceneAppId.value,
    selectedFrame.value.appId,
    body,
  );
  if (!result) {
    Object.assign(selectedFrame.value, prev);
  } else {
    Object.assign(selectedFrame.value, result);
  }
}

async function onAddFrame(body: CreateFrameRequestIO): Promise<void> {
  const created = await sceneGraph.addFrame(sceneAppId.value, body);
  if (created) {
    await loadScene();
    selectedFrameAppId.value = created.appId;
  }
}

function onRequestDelete(frame: FrameIO): void {
  frameToDelete.value = frame;
  showDeleteFrame.value = true;
}

async function onConfirmDeleteFrame(): Promise<void> {
  if (!frameToDelete.value) return;
  const ok = await sceneGraph.deleteFrame(
    sceneAppId.value,
    frameToDelete.value.appId,
  );
  if (ok) {
    if (selectedFrameAppId.value === frameToDelete.value.appId) {
      selectedFrameAppId.value = null;
    }
    frameToDelete.value = null;
    await loadScene();
  }
}

async function onAddJoint(body: CreateJointRequestIO): Promise<void> {
  const created = await sceneGraph.addJoint(sceneAppId.value, body);
  if (created) await loadScene();
}

async function onDeleteJoint(joint: JointIO): Promise<void> {
  if (!window.confirm(`Delete joint ${joint.name ?? joint.appId.slice(0, 8)}?`))
    return;
  const ok = await sceneGraph.deleteJoint(sceneAppId.value, joint.appId);
  if (ok) await loadScene();
}

async function onExportUrdf(): Promise<void> {
  const xml = await sceneGraph.exportUrdf(sceneAppId.value);
  if (!xml) return;
  const blob = new Blob([xml], { type: "application/xml" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = urdfDownloadFilename(scene.value?.name, sceneAppId.value);
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

const descendantCountOfPending = computed<number>(() => {
  if (!frameToDelete.value) return 0;
  const idx = indexFramesByParent(frames.value);
  return countDescendants(frameToDelete.value.appId, idx);
});
</script>

<template>
  <div class="pa-4 d-flex flex-column ga-4" data-test="scene-graph-page">
    <!-- Header -->
    <div class="d-flex align-center ga-3">
      <div class="flex-grow-1">
        <div class="text-h4">
          {{ scene?.name || "Scene graph" }}
        </div>
        <div class="text-caption text-textbody2">
          {{ sceneAppId }}
        </div>
      </div>
      <v-btn
        v-if="scene && frames.length > 0"
        prepend-icon="mdi-download"
        :disabled="sceneGraph.loading.value"
        data-test="urdf-export-button"
        @click="onExportUrdf"
      >
        Export URDF
      </v-btn>
      <v-btn
        v-if="scene"
        color="primary"
        prepend-icon="mdi-plus"
        :disabled="!canWrite"
        data-test="add-frame-button"
        @click="showAddFrame = true"
      >
        {{ frames.length === 0 ? "Add root frame" : "Add frame" }}
      </v-btn>
    </div>

    <!-- Error banner -->
    <v-alert
      v-if="sceneGraph.error.value && !notFound"
      type="error"
      density="compact"
      closable
      data-test="scene-graph-error"
    >
      {{ sceneGraph.error.value.message }}
      <span
        v-if="sceneGraph.error.value.detail"
        class="text-caption d-block mt-1"
      >
        {{ sceneGraph.error.value.detail }}
      </span>
    </v-alert>

    <!-- 404 state -->
    <v-card
      v-if="notFound"
      variant="outlined"
      class="pa-6 text-center"
      data-test="scene-graph-404"
    >
      <div class="text-h5 mb-2">Scene not found</div>
      <div class="text-textbody2 mb-4">
        No scene with appId
        <span class="text-monospace">{{ sceneAppId }}</span>
        exists, or you don't have access.
      </div>
      <v-btn to="/collections" color="primary">Back to Collections</v-btn>
    </v-card>

    <!-- Loading -->
    <div
      v-else-if="sceneGraph.loading.value && !scene"
      class="text-center pa-6"
      data-test="scene-graph-loading"
    >
      <v-progress-circular indeterminate />
    </div>

    <!-- Empty state -->
    <v-card
      v-else-if="scene && frames.length === 0"
      variant="outlined"
      class="pa-6 text-center"
      data-test="scene-graph-empty"
    >
      <div class="text-h5 mb-2">No frames yet</div>
      <div class="text-textbody2 mb-2">
        {{ scene.description || "This scene is empty." }}
      </div>
      <div class="text-textbody2 mb-4">
        Start by adding a root frame — the first frame you add becomes the
        scene's root.
      </div>
      <v-btn
        color="primary"
        prepend-icon="mdi-plus"
        :disabled="!canWrite"
        data-test="add-root-frame-cta"
        @click="showAddFrame = true"
      >
        Add root frame
      </v-btn>
    </v-card>

    <!-- Normal state: tree + inspector + joint list -->
    <div v-else-if="scene" class="scene-content d-flex ga-4">
      <v-card variant="outlined" class="tree-column pa-3">
        <div class="text-h6 mb-2">
          Frames ({{ frames.length }})
        </div>
        <SceneGraphTreeView
          :frames="frames"
          :joints="joints"
          :root-frame-app-id="scene.rootFrameAppId"
          :selected-frame-app-id="selectedFrameAppId"
          @select-frame="onSelectFrame"
        />
      </v-card>
      <div class="inspector-column">
        <div class="inspector-sticky">
          <SceneGraphFrameInspector
            v-if="selectedFrame"
            :frame="selectedFrame"
            :candidate-parents="frames"
            :can-write="canWrite"
            @save="onSaveFrame"
            @request-delete="onRequestDelete"
          />
          <v-card v-else variant="outlined" class="pa-4">
            <div class="text-h6 mb-2">Scene</div>
            <div><strong>Name:</strong> {{ scene.name || "—" }}</div>
            <div><strong>Description:</strong> {{ scene.description || "—" }}</div>
            <div>
              <strong>Source file:</strong>
              <span class="text-monospace">{{ scene.sourceFileAppId || "—" }}</span>
            </div>
            <div>
              <strong>Root frame:</strong>
              <span class="text-monospace">{{ scene.rootFrameAppId || "—" }}</span>
            </div>
            <div><strong>Frames:</strong> {{ frames.length }}</div>
            <div><strong>Joints:</strong> {{ joints.length }}</div>
            <div class="text-caption text-textbody2 mt-3">
              Click a frame in the tree to edit its transform.
            </div>
          </v-card>
        </div>
      </div>
    </div>

    <SceneGraphJointList
      v-if="scene && frames.length > 0"
      :joints="joints"
      :frames-by-app-id="framesByAppId"
      :can-write="canWrite"
      @add-joint="showAddJoint = true"
      @request-delete="onDeleteJoint"
    />

    <AddFrameDialog
      v-model:show-dialog="showAddFrame"
      :existing-frames="frames"
      @submit="onAddFrame"
    />
    <AddJointDialog
      v-model:show-dialog="showAddJoint"
      :existing-frames="frames"
      @submit="onAddJoint"
    />
    <DeleteFrameConfirm
      v-model:show-dialog="showDeleteFrame"
      :frame="frameToDelete"
      :descendant-count="descendantCountOfPending"
      @confirmed="onConfirmDeleteFrame"
    />
  </div>
</template>

<style scoped>
.scene-content {
  align-items: flex-start;
}
.tree-column {
  flex: 1 1 50%;
  min-width: 0;
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}
.inspector-column {
  flex: 1 1 50%;
  min-width: 0;
}
.inspector-sticky {
  position: sticky;
  top: 20px;
}
.text-monospace {
  font-family: var(--v-font-family-mono, monospace);
}
</style>

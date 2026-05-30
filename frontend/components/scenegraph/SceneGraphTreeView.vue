<script setup lang="ts">
/**
 * SCENEGRAPH-REST-1-UI — frame tree (parent → child via `:HAS_PARENT_FRAME`).
 *
 * Uses a tiny recursive `SceneGraphTreeNode` SFC instead of `v-treeview` or a
 * heavy graph lib (vis.js / dagre) — scenes are 5–100 frames per
 * `aidocs/data/85`, recursion is the simplest correct shape and respects the
 * "reuse trusted libraries / no heavy graph lib for a tree" guard in
 * CLAUDE.md.
 *
 * Orphan handling: any frame whose `parentFrameAppId` references a missing
 * frame is rendered under a synthetic "Orphans" group at the root level —
 * never silently dropped (matches the delete-cascade race case called out in
 * SCENEGRAPH-PERMS-1).
 */
import type { FrameIO, JointIO } from "~/composables/useSceneGraph";
import {
  indexFramesByParent,
  countDescendants,
} from "~/composables/useSceneGraph";

interface SceneGraphTreeViewProps {
  frames: FrameIO[];
  joints: JointIO[];
  rootFrameAppId?: string | null;
  selectedFrameAppId?: string | null;
}

const props = withDefaults(defineProps<SceneGraphTreeViewProps>(), {
  rootFrameAppId: null,
  selectedFrameAppId: null,
});

const emit = defineEmits<{
  (e: "select-frame", frame: FrameIO): void;
}>();

const byParent = computed(() => indexFramesByParent(props.frames));
const byAppId = computed(() => {
  const m = new Map<string, FrameIO>();
  for (const f of props.frames) m.set(f.appId, f);
  return m;
});

const renderedRoots = computed<FrameIO[]>(() => {
  const declared = props.rootFrameAppId
    ? byAppId.value.get(props.rootFrameAppId)
    : undefined;
  const nullParented = byParent.value.get("") ?? [];
  const merged: FrameIO[] = [];
  const seen = new Set<string>();
  if (declared && !seen.has(declared.appId)) {
    merged.push(declared);
    seen.add(declared.appId);
  }
  for (const f of nullParented) {
    if (!seen.has(f.appId)) {
      merged.push(f);
      seen.add(f.appId);
    }
  }
  return merged;
});

const orphans = computed<FrameIO[]>(() => {
  const list: FrameIO[] = [];
  for (const f of props.frames) {
    const p = f.parentFrameAppId;
    if (p && !byAppId.value.has(p)) list.push(f);
  }
  return list;
});

function jointCountFor(frameAppId: string): number {
  let n = 0;
  for (const j of props.joints) {
    if (j.parentFrameAppId === frameAppId || j.childFrameAppId === frameAppId)
      n += 1;
  }
  return n;
}

function descendantCountFor(frameAppId: string): number {
  return countDescendants(frameAppId, byParent.value);
}

function onSelect(frame: FrameIO): void {
  emit("select-frame", frame);
}

const kindColour: Record<string, string> = {
  BASE: "primary",
  TCP: "success",
  TOOL: "warning",
  JOINT: "info",
  FRAME: "treeview",
};
</script>

<template>
  <div data-test="scene-graph-tree" class="scene-graph-tree">
    <div v-if="frames.length === 0" class="text-center pa-6 text-textbody2">
      No frames in this scene yet.
    </div>
    <SceneGraphTreeNode
      v-for="root in renderedRoots"
      :key="root.appId"
      :frame="root"
      :by-parent="byParent"
      :joints="joints"
      :selected-frame-app-id="selectedFrameAppId"
      :depth="0"
      :joint-count-for="jointCountFor"
      :descendant-count-for="descendantCountFor"
      :kind-colour="kindColour"
      @select-frame="onSelect"
    />
    <div v-if="orphans.length > 0" class="orphans pt-4">
      <div class="text-caption text-warning px-2">
        Orphans (parent missing — likely a delete race):
      </div>
      <SceneGraphTreeNode
        v-for="o in orphans"
        :key="o.appId"
        :frame="o"
        :by-parent="byParent"
        :joints="joints"
        :selected-frame-app-id="selectedFrameAppId"
        :depth="0"
        :joint-count-for="jointCountFor"
        :descendant-count-for="descendantCountFor"
        :kind-colour="kindColour"
        @select-frame="onSelect"
      />
    </div>
  </div>
</template>

<style scoped>
.scene-graph-tree {
  font-size: 0.9rem;
}
.orphans {
  border-top: 1px dashed rgba(125, 125, 125, 0.3);
}
</style>

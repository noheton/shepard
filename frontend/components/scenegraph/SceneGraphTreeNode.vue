<script setup lang="ts">
/**
 * SCENEGRAPH-REST-1-UI — recursive tree-node renderer.
 *
 * Split out as a sibling SFC so the `<SceneGraphTreeNode>` self-reference
 * works cleanly (Vue auto-imports the component by filename). Carries the
 * single-frame row markup + the recursive `<SceneGraphTreeNode>` call for
 * children.
 */
import type { FrameIO, JointIO } from "~/composables/useSceneGraph";

interface SceneGraphTreeNodeProps {
  frame: FrameIO;
  byParent: Map<string, FrameIO[]>;
  joints: JointIO[];
  selectedFrameAppId?: string | null;
  depth?: number;
  jointCountFor: (id: string) => number;
  descendantCountFor: (id: string) => number;
  kindColour: Record<string, string>;
}

const props = withDefaults(defineProps<SceneGraphTreeNodeProps>(), {
  selectedFrameAppId: null,
  depth: 0,
});

const emit = defineEmits<{
  (e: "select-frame", frame: FrameIO): void;
}>();

const expanded = ref(true);

const children = computed<FrameIO[]>(
  () => props.byParent.get(props.frame.appId) ?? [],
);
const hasChildren = computed(() => children.value.length > 0);
const isSelected = computed(
  () => props.selectedFrameAppId === props.frame.appId,
);
const jc = computed(() => props.jointCountFor(props.frame.appId));
const dc = computed(() => props.descendantCountFor(props.frame.appId));
</script>

<template>
  <div class="tree-node" :style="{ marginLeft: depth * 18 + 'px' }">
    <div
      class="tree-row d-flex align-center pa-2 rounded"
      :class="{ 'tree-row--selected': isSelected }"
      :data-test="'tree-row-' + frame.appId"
      @click="emit('select-frame', frame)"
    >
      <v-btn
        v-if="hasChildren"
        icon
        size="x-small"
        variant="text"
        class="mr-1"
        :data-test="'tree-expand-' + frame.appId"
        @click.stop="expanded = !expanded"
      >
        <v-icon size="small">
          {{ expanded ? "mdi-chevron-down" : "mdi-chevron-right" }}
        </v-icon>
      </v-btn>
      <span v-else class="tree-spacer" />
      <span class="tree-label flex-grow-1">
        {{ frame.name || frame.appId.slice(0, 8) }}
      </span>
      <v-chip
        size="x-small"
        :color="kindColour[frame.kind || 'FRAME'] || 'treeview'"
        class="ml-2"
        variant="tonal"
      >
        {{ frame.kind || "FRAME" }}
      </v-chip>
      <v-chip
        v-if="jc > 0"
        size="x-small"
        color="info"
        variant="tonal"
        class="ml-1"
        :title="`${jc} joint(s) attached`"
      >
        J:{{ jc }}
      </v-chip>
      <v-chip
        v-if="dc > 0"
        size="x-small"
        color="treeview"
        variant="tonal"
        class="ml-1"
        :title="`${dc} descendant frame(s)`"
      >
        ↓{{ dc }}
      </v-chip>
    </div>
    <div v-if="expanded && hasChildren">
      <SceneGraphTreeNode
        v-for="child in children"
        :key="child.appId"
        :frame="child"
        :by-parent="byParent"
        :joints="joints"
        :selected-frame-app-id="selectedFrameAppId"
        :depth="depth + 1"
        :joint-count-for="jointCountFor"
        :descendant-count-for="descendantCountFor"
        :kind-colour="kindColour"
        @select-frame="(f) => emit('select-frame', f)"
      />
    </div>
  </div>
</template>

<style scoped>
.tree-row {
  cursor: pointer;
  transition: background-color 0.15s ease;
}
.tree-row:hover {
  background-color: rgba(125, 125, 125, 0.08);
}
.tree-row--selected {
  background-color: rgba(var(--v-theme-primary), 0.16);
}
.tree-label {
  font-family: var(--v-font-family-mono, monospace);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tree-spacer {
  display: inline-block;
  width: 28px;
}
</style>

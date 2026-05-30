<script setup lang="ts">
/**
 * SCENEGRAPH-REST-1-UI — joints as a sibling table.
 *
 * Joints span two frames (parent + child) plus type/axis/limits — that's
 * tabular data. Inline chips on tree nodes lose the parent/child pairing,
 * so the joints surface lives as a Vuetify data-table below the tree.
 *
 * Frame appIds are joined to their names via the `framesByAppId` prop so the
 * table reads as "shoulder_link → forearm_link" not "9c…f1 → 7a…b3".
 */
import type { FrameIO, JointIO } from "~/composables/useSceneGraph";

interface SceneGraphJointListProps {
  joints: JointIO[];
  framesByAppId: Map<string, FrameIO>;
  canWrite?: boolean;
}

const props = withDefaults(defineProps<SceneGraphJointListProps>(), {
  canWrite: true,
});

const emit = defineEmits<{
  (e: "request-delete", joint: JointIO): void;
  (e: "add-joint"): void;
}>();

function frameLabel(appId: string | null | undefined): string {
  if (!appId) return "—";
  const f = props.framesByAppId.get(appId);
  if (!f) return appId.slice(0, 8);
  return f.name ? f.name : appId.slice(0, 8);
}
</script>

<template>
  <v-card variant="outlined" data-test="joint-list">
    <div class="d-flex align-center pa-3">
      <div class="text-h6 flex-grow-1">Joints ({{ joints.length }})</div>
      <v-btn
        color="primary"
        size="small"
        :disabled="!canWrite"
        data-test="joint-add"
        @click="emit('add-joint')"
      >
        Add joint
      </v-btn>
    </div>
    <v-table density="compact">
      <thead>
        <tr>
          <th>Name</th>
          <th>Type</th>
          <th>Parent</th>
          <th>Child</th>
          <th>Axis</th>
          <th>Limits</th>
          <th>Home</th>
          <th />
        </tr>
      </thead>
      <tbody>
        <tr v-if="joints.length === 0">
          <td colspan="8" class="text-center text-textbody2 pa-4">
            No joints in this scene.
          </td>
        </tr>
        <tr
          v-for="j in joints"
          :key="j.appId"
          :data-test="'joint-row-' + j.appId"
        >
          <td>{{ j.name || j.appId.slice(0, 8) }}</td>
          <td>
            <v-chip size="x-small" variant="tonal" color="info">
              {{ j.type || "FIXED" }}
            </v-chip>
          </td>
          <td>{{ frameLabel(j.parentFrameAppId) }}</td>
          <td>{{ frameLabel(j.childFrameAppId) }}</td>
          <td class="text-monospace">
            ({{ j.axisX ?? 0 }}, {{ j.axisY ?? 0 }}, {{ j.axisZ ?? 0 }})
          </td>
          <td class="text-monospace">
            {{ j.limitMin ?? 0 }} … {{ j.limitMax ?? 0 }}
          </td>
          <td class="text-monospace">{{ j.homeAngle ?? 0 }}</td>
          <td>
            <v-btn
              size="x-small"
              variant="text"
              color="error"
              :disabled="!canWrite"
              :data-test="'joint-delete-' + j.appId"
              @click="emit('request-delete', j)"
            >
              Delete
            </v-btn>
          </td>
        </tr>
      </tbody>
    </v-table>
  </v-card>
</template>

<style scoped>
.text-monospace {
  font-family: var(--v-font-family-mono, monospace);
  font-size: 0.85rem;
}
</style>

<script setup lang="ts">
import type { Timeseries } from "@dlr-shepard/backend-client";
import Trace3DChannelPicker from "./Trace3DChannelPicker.vue";
import type { ChannelV2, Channel5Tuple, Trace3DChannelSelection } from "./Trace3DChannelPicker.vue";

const props = defineProps<{
  containerId: number;
  channels: Timeseries[];
  /** v2 channel list carrying shepardId for auto-populate and annotation save. */
  channelsV2?: ChannelV2[];
  startNs: number;
  endNs: number;
}>();

const open = defineModel<boolean>({ default: false });

// Renderer selector — Trace3D, URDF, and Thermography are separately selectable.
// Future work may compose them (URDF cell with Trace3D path overlaid, or a
// Thermography surface tile registered against the URDF AFP cell); OUT OF SCOPE here.
type RendererKind = "trace-3d" | "urdf" | "thermography";
const rendererKind = ref<RendererKind>("trace-3d");

// URDF binding state — when rendererKind === "urdf", the dialog asks for the
// FileReference appId of the .urdf singleton (V2-SWEEP Wave 2: "UI never
// asks for paths/URLs — pulls from references"; the render page resolves
// the bytes via GET /v2/files/{appId}/content, and the mesh package root
// travels as the `urn:shepard:urdf:package-path` annotation on the
// FileReference — never as a typed path). A context-scoped reference
// picker is the follow-up (URDF-REF-PICKER, aidocs/16).
const urdfFileAppId = ref<string>("");

const pickerRef = ref<InstanceType<typeof Trace3DChannelPicker> | null>(null);
const canOpen = ref(false);
const openCount = ref(0);

watch(open, (v) => { if (v) openCount.value++; });

// When v2 channels haven't loaded yet (or container predates TS-SEMANTIC-01),
// fall back to synthesising ChannelV2 entries from the v1 channel list so the
// picker always has something to show. shepardId is intentionally empty for
// synthesised entries — saveAnnotations skips them (falsy guard).
const resolvedChannels = computed<ChannelV2[]>(() => {
  if (props.channelsV2?.length) return props.channelsV2;
  return props.channels.map(ch => ({
    shepardId: "",
    measurement: ch.measurement,
    device: ch.device,
    location: ch.location,
    symbolicName: ch.symbolicName,
    field: ch.field,
  }));
});

// ── save annotations (TS-AXIS-AUTO) ──────────────────────────────────────────

async function saveAnnotations() {
  if (!pickerRef.value || !resolvedChannels.value.length) return;
  const sel = pickerRef.value.getSelection();
  const roleEntries: [string, Channel5Tuple | null][] = [
    ["x", sel.x], ["y", sel.y], ["z", sel.z],
    ["rot_a", sel.rot_a], ["rot_b", sel.rot_b], ["rot_c", sel.rot_c],
  ];
  for (const [role, tuple] of roleEntries) {
    if (!tuple) continue;
    const ch = resolvedChannels.value.find(
      c =>
        (c.measurement ?? "") === tuple.measurement &&
        (c.device       ?? "") === tuple.device &&
        (c.field        ?? "") === tuple.field &&
        (c.location     ?? "") === tuple.location &&
        (c.symbolicName ?? "") === tuple.symbolicName,
    );
    if (!ch?.shepardId) continue;
    await $fetch(
      `/v2/timeseries-containers/${props.containerId}/channels/${ch.shepardId}/annotations`,
      { method: "POST", body: { value: role } },
    ).catch(() => {});
  }
}

// ── open Trace3D render page ──────────────────────────────────────────────────

function openTrace3D() {
  if (!pickerRef.value) return;
  const sel: Trace3DChannelSelection = pickerRef.value.getSelection();
  if (!sel.x || !sel.y || !sel.z) return;

  const roles: Record<string, Channel5Tuple> = { x: sel.x, y: sel.y, z: sel.z };
  if (sel.value) roles.value = sel.value;
  if (sel.rot_a) roles.rot_a = sel.rot_a;
  if (sel.rot_b) roles.rot_b = sel.rot_b;
  if (sel.rot_c) roles.rot_c = sel.rot_c;

  navigateTo({
    path: "/shapes/render",
    query: {
      containerId: String(props.containerId),
      startNs:    String(props.startNs),
      endNs:      String(props.endNs),
      colormap:   sel.colormap,
      roles:      btoa(JSON.stringify(roles)),
    },
  });
}

// ── open URDF render page ────────────────────────────────────────────────────
// V2-SWEEP Wave 2: the render link carries the FileReference appId only —
// the backend/content endpoint resolves the actual bytes (and the mesh
// package root from the reference's annotations). No raw URL/path on the
// query string. The numeric `containerId` rides the documented TS-ID
// exception (v2 timeseries-container content endpoints are still numeric
// pending aidocs/platform/87).
function openUrdf() {
  if (!urdfFileAppId.value.trim()) return;
  navigateTo({
    path: "/shapes/render",
    query: {
      renderer:      "urdf",
      urdfFileAppId: urdfFileAppId.value.trim(),
      containerId:   String(props.containerId),
      startNs:       String(props.startNs),
      endNs:         String(props.endNs),
    },
  });
}

// ── open Thermography render page (tier-1, metadata-only) ────────────────────
// Tier-1: no channel binding required — the renderer reads annotations
// off the parent DataObject + FileReference directly. The dialog is a
// pass-through: the user clicks Open and the render page shows the
// annotation-driven summary + Three.js placeholder canvas. Tier-2
// (OTVIS-PARSE-2 + THERMO-CHANNELS-1) will add a channel binding panel
// here for IR-sequence playback.
function openThermography() {
  navigateTo({
    path: "/shapes/render",
    query: {
      renderer:    "thermography",
      containerId: String(props.containerId),
      startNs:     String(props.startNs),
      endNs:       String(props.endNs),
    },
  });
}

const canOpenAny = computed(() =>
  (rendererKind.value === "trace-3d" && canOpen.value) ||
  (rendererKind.value === "urdf" && urdfFileAppId.value.trim().length > 0) ||
  (rendererKind.value === "thermography"),
);
</script>

<template>
  <v-dialog v-model="open" max-width="520">
    <v-card>
      <v-card-title class="d-flex align-center ga-2 pt-4">
        <v-icon color="primary">mdi-cube-outline</v-icon>
        Visualize in 3D
      </v-card-title>
      <v-card-subtitle class="pb-2">
        Pick a renderer, configure it, then open the view.
      </v-card-subtitle>
      <v-card-text class="pt-2">
        <!-- Renderer picker — Trace3D vs URDF (separately selectable; future work composes). -->
        <v-btn-toggle
          v-model="rendererKind"
          mandatory
          divided
          density="compact"
          variant="tonal"
          class="mb-3"
        >
          <v-btn value="trace-3d" size="small" prepend-icon="mdi-vector-polyline">
            Trace 3D
          </v-btn>
          <v-btn value="urdf" size="small" prepend-icon="mdi-robot-industrial">
            URDF
          </v-btn>
          <v-btn value="thermography" size="small" prepend-icon="mdi-thermometer-lines">
            Thermography
          </v-btn>
        </v-btn-toggle>

        <Trace3DChannelPicker
          v-if="rendererKind === 'trace-3d'"
          :key="openCount"
          ref="pickerRef"
          :container-id="containerId"
          :channels="resolvedChannels"
          @update:can-confirm="canOpen = $event"
          @save-annotations-requested="saveAnnotations"
        />

        <div v-else-if="rendererKind === 'urdf'" class="d-flex flex-column ga-3">
          <!-- V2-SWEEP Wave 2: no URL/path inputs — the dialog takes the
               FileReference appId; the render page resolves the bytes via
               the v2 content endpoint. Context-scoped picker tracked as
               URDF-REF-PICKER in aidocs/16. -->
          <div class="text-caption text-medium-emphasis">
            Render a robot description (URDF) in the browser. Paste the appId
            of the .urdf FileReference (copy it from the file's detail page —
            or use "Open in URDF view" there directly).
          </div>
          <v-text-field
            v-model="urdfFileAppId"
            label="URDF FileReference appId"
            density="compact"
            variant="outlined"
            hint="UUID of the singleton FileReference holding the .urdf file."
            persistent-hint
          />
        </div>

        <div v-else class="d-flex flex-column ga-2">
          <!-- Thermography (tier-1): no per-channel binding yet. -->
          <div class="text-caption text-medium-emphasis">
            Render the Edevis OTvis annotation summary + Three.js
            placeholder canvas for the parent DataObject. Tier-1 is
            metadata-driven: upload an <code>.OTvis</code> file, the
            parser emits <code>urn:shepard:thermography:*</code>
            annotations, the view reads them. IR-sequence playback
            ships with tier-2 (<code>OTVIS-PARSE-2</code> +
            <code>THERMO-CHANNELS-1</code>).
          </div>
          <v-alert
            type="warning"
            variant="tonal"
            density="compact"
            prepend-icon="mdi-alert-outline"
          >
            Tier-1 stub - no frame data, no channel-bound playback yet.
          </v-alert>
        </div>
      </v-card-text>
      <v-card-actions class="pb-4 px-4">
        <v-spacer />
        <v-btn variant="text" @click="open = false">Cancel</v-btn>
        <v-btn
          v-if="rendererKind === 'trace-3d'"
          color="primary"
          variant="tonal"
          :disabled="!canOpenAny"
          prepend-icon="mdi-cube-outline"
          @click="openTrace3D"
        >
          Open Trace3D
        </v-btn>
        <v-btn
          v-else-if="rendererKind === 'urdf'"
          color="primary"
          variant="tonal"
          :disabled="!canOpenAny"
          prepend-icon="mdi-robot-industrial"
          @click="openUrdf"
        >
          Open URDF
        </v-btn>
        <v-btn
          v-else
          color="primary"
          variant="tonal"
          :disabled="!canOpenAny"
          prepend-icon="mdi-thermometer-lines"
          @click="openThermography"
        >
          Open Thermography
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

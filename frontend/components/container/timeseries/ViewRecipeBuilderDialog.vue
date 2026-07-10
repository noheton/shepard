<script setup lang="ts">
import type { Timeseries } from "@dlr-shepard/backend-client";
import Trace3DChannelPicker from "./Trace3DChannelPicker.vue";
import type { ChannelV2, Channel5Tuple, Trace3DChannelSelection } from "./Trace3DChannelPicker.vue";
import {
  useFetchUrdfOptions,
  useFetchAccessibleUrdfOptions,
  type ReferenceOption,
} from "~/composables/useFetchReferenceOptions";

const props = defineProps<{
  containerId: number;
  containerAppId: string;
  channels: Timeseries[];
  /** v2 channel list carrying shepardId for auto-populate and annotation save. */
  channelsV2?: ChannelV2[];
  startNs: number;
  endNs: number;
  /**
   * appId of the parent DataObject — used to populate the URDF reference picker
   * with FileReferences whose name ends .urdf. Optional: when absent the picker
   * degrades to a free-text appId input.
   */
  dataObjectAppId?: string;
}>();

const open = defineModel<boolean>({ default: false });

// Renderer selector — Trace3D, URDF, and Thermography are separately selectable.
// Future work may compose them (URDF cell with Trace3D path overlaid, or a
// Thermography surface tile registered against the URDF AFP cell); OUT OF SCOPE here.
type RendererKind = "trace-3d" | "urdf" | "thermography";
const rendererKind = ref<RendererKind>("trace-3d");

// URDF binding state — when rendererKind === "urdf", the picker selects the
// FileReference appId of the .urdf singleton. UI-PATHS-FROM-REFERENCES rule:
// no raw URL/path; the render page resolves bytes via GET /v2/files/{appId}/content
// and the mesh package root from the urn:shepard:urdf:package-path annotation.
//
// v-autocomplete with item-value="appId" binds a plain appId string (or null).
const urdfFileAppId = ref<string | null>(null);

// URDF-FILEREF-PICKER-SEARCHABLE — the "advanced: paste appId" fallback. Kept
// for ONE deprecation window per the "user never types an ID" rule; the DEFAULT
// path is the searchable picker below. resolveUrdfAppId() prefers the picker.
const urdfPasteAppId = ref<string>("");
const showAdvancedPaste = ref(false);

// URDF-REF-PICKER: FileReferences with .urdf names on the *current* DataObject
// (usually empty for a timeseries's DataObject — hence the accessible search).
const { options: urdfOptions } = useFetchUrdfOptions(() => props.dataObjectAppId);

// URDF-FILEREF-PICKER-SEARCHABLE: the instance-wide, permission-filtered,
// search-as-you-type accessible-URDF list (GET /v2/references/urdf). This is
// what actually populates the picker when the URDF lives in another collection
// (e.g. the kr210-r2700-urdf in "MFFD RDK → URDF Viewer Showcase").
const {
  query: urdfSearch,
  options: accessibleUrdfOptions,
  isLoading: urdfLoading,
  refresh: refreshAccessibleUrdfs,
} = useFetchAccessibleUrdfOptions();

// Merged picker options: current-DataObject URDFs first (if any), then the
// instance-wide accessible URDFs, de-duplicated by appId.
const urdfPickerOptions = computed<ReferenceOption[]>(() => {
  const seen = new Set<string>();
  const merged: ReferenceOption[] = [];
  for (const o of [...urdfOptions.value, ...accessibleUrdfOptions.value]) {
    if (o.appId && !seen.has(o.appId)) {
      seen.add(o.appId);
      merged.push(o);
    }
  }
  return merged;
});

const pickerRef = ref<InstanceType<typeof Trace3DChannelPicker> | null>(null);
const canOpen = ref(false);
const openCount = ref(0);

watch(open, (v) => { if (v) openCount.value++; });

// Populate the accessible-URDF picker when the dialog opens or the user switches
// to the URDF renderer — so the list is filled before the user types anything.
watch([open, rendererKind], ([isOpen, kind]) => {
  if (isOpen && kind === "urdf") refreshAccessibleUrdfs();
});

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
      `/v2/containers/${props.containerAppId}/channels/${ch.shepardId}/annotations`,
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

  // UX612-C2: the render page's channel endpoints are appId-keyed
  // (APISIMP-TSCONT-APPID-KEY) — carry the container appId through the
  // handoff. The numeric containerId remains for legacy bookmarks only.
  navigateTo({
    path: "/shapes/render",
    query: {
      ...(props.containerAppId ? { containerAppId: props.containerAppId } : {}),
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

/**
 * Resolve the URDF FileReference appId to open. The searchable picker wins; the
 * "advanced: paste appId" fallback only applies when the picker is empty.
 */
function resolveUrdfAppId(): string {
  const picked = urdfFileAppId.value;
  if (picked && picked.trim().length > 0) return picked.trim();
  return urdfPasteAppId.value.trim();
}

function openUrdf() {
  const appId = resolveUrdfAppId();
  if (!appId) return;
  navigateTo({
    path: "/shapes/render",
    query: {
      renderer:      "urdf",
      urdfFileAppId: appId,
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
  (rendererKind.value === "urdf" && resolveUrdfAppId().length > 0) ||
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
          :container-app-id="containerAppId"
          :channels="resolvedChannels"
          @update:can-confirm="canOpen = $event"
          @save-annotations-requested="saveAnnotations"
        />

        <div v-else-if="rendererKind === 'urdf'" class="d-flex flex-column ga-3">
          <div class="text-caption text-medium-emphasis">
            Render a robot description (URDF) in the browser. Search for a
            <code>.urdf</code> FileReference by name — the list spans every
            collection you can read (e.g. type <code>kr210</code>). The renderer
            resolves bytes and mesh paths from the reference — no raw file paths
            needed.
          </div>
          <!-- URDF-FILEREF-PICKER-SEARCHABLE: searchable v-autocomplete over the
               instance-wide, permission-filtered accessible-URDF list. The user
               searches by name; the appId travels invisibly (item-value). -->
          <v-autocomplete
            v-model="urdfFileAppId"
            v-model:search="urdfSearch"
            :items="urdfPickerOptions"
            :loading="urdfLoading"
            item-value="appId"
            item-title="label"
            label="URDF FileReference"
            density="compact"
            variant="outlined"
            placeholder="search a .urdf reference by name (e.g. kr210)"
            hint="Type to search URDF references across all collections you can read."
            persistent-hint
            clearable
            auto-select-first
            spellcheck="false"
            no-data-text="No matching URDF references"
            data-testid="urdf-ref-autocomplete"
          />
          <!-- Advanced escape hatch (one deprecation window): paste an appId when
               the reference genuinely isn't listed. The picker above is the
               default per the "user never types an ID" rule. -->
          <div>
            <v-btn
              variant="text"
              size="x-small"
              density="compact"
              :prepend-icon="showAdvancedPaste ? 'mdi-chevron-up' : 'mdi-chevron-down'"
              data-testid="urdf-advanced-toggle"
              @click="showAdvancedPaste = !showAdvancedPaste"
            >
              Advanced: paste an appId
            </v-btn>
            <v-text-field
              v-if="showAdvancedPaste"
              v-model="urdfPasteAppId"
              label="URDF FileReference appId"
              density="compact"
              variant="outlined"
              class="mt-2"
              placeholder="paste the UUID of a .urdf FileReference"
              hint="Deprecated fallback — prefer the searchable picker above."
              persistent-hint
              clearable
              spellcheck="false"
              data-testid="urdf-ref-paste"
            />
          </div>
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

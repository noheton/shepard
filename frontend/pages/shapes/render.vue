<script setup lang="ts">
/**
 * /shapes/render — VIEW_RECIPE render playground (TPL2b / M1).
 *
 * Calls POST /v2/shapes/render → shows channel binding declarations
 * → fetches TS data per role → dispatches to the renderer named in
 * body.renderer:
 *
 *   "trace-3d" | "tresjs"  → <Trace3DView> (flat-array adapter + legend)
 *   "table"                → inline <v-table> of channel values
 *   (unknown)              → <PlaceholderImplStatus> noting the unsupported hint
 *
 * Beta: bindings come back status=DECLARED (no live resolution).
 * User supplies the TS container ID to complete the pipeline until TPL2c ships.
 *
 * Design: aidocs/platform/83-tpl1-tpl2-shapes-templates-views.md §Frontend dispatch
 */
import { lerpSeries, type ColormapName } from "~/utils/colormap";
import type { Trace3DColorScheme } from "~/components/container/timeseries/Trace3DView.vue";
import Trace3DView from "~/components/container/timeseries/Trace3DView.vue";
import PlaceholderImplStatus from "~/components/common/placeholder/PlaceholderImplStatus.vue";
import Trace3DEditChannelsDialog from "~/components/container/timeseries/Trace3DEditChannelsDialog.vue";
import type { ChannelV2, Channel5Tuple, Trace3DChannelSelection } from "~/components/container/timeseries/Trace3DChannelPicker.vue";

useHead({ title: "Shape render playground | shepard" });

// ── inputs ────────────────────────────────────────────────────────────────────
const templateAppId   = ref("");
const focusShepardId  = ref("");

// When navigated from a TimeseriesReference via ViewRecipeBuilderDialog, these
// are set from query params and the template form is hidden.
const fromReference   = ref(false);
const refStartNs      = ref<number | null>(null);
const refEndNs        = ref<number | null>(null);
const containerId     = ref(""); // numeric TS container ID (legacy v1 path)
const colormapName    = ref<ColormapName>("inferno");
const windowHours     = ref(1);

// ── state ─────────────────────────────────────────────────────────────────────
interface Binding {
  role: string;
  channelSelector: string;
  unit: string | null;
  required: boolean;
  status: string;
  parsed: { measurement: string; device: string; location: string; symbolicName: string; field: string } | null;
}

const bindings      = ref<Binding[]>([]);
const renderer      = ref<string | null>(null);
const fetchError    = ref<string | null>(null);
const isFetching    = ref(false);
const isRendering   = ref(false);
const renderError   = ref<string | null>(null);

interface TracePoint {
  x: number; y: number; z: number; value: number;
  t: number;
  eulerA?: number; eulerB?: number; eulerC?: number;
}
const tracePoints  = ref<TracePoint[]>([]);
const valueLabel   = ref<string>("");

interface ChannelStat { role: string; sourcePoints: number; }
interface RenderStats {
  xPoints: number;
  channelStats: ChannelStat[];
  maxGapMs: number | null;
}
const renderStats = ref<RenderStats | null>(null);

// ── player state ──────────────────────────────────────────────────────────────

const isPlaying       = ref(false);
const playerPosition  = ref(0); // 0–100
const speedPresets    = [0.1, 0.5, 1, 2, 5];
const selectedSpeedIdx = ref(2); // default 1×
const customSpeedInput = ref(3);

const playSpeed = computed(() =>
  selectedSpeedIdx.value < speedPresets.length
    ? speedPresets[selectedSpeedIdx.value]!
    : Math.max(0.01, Number(customSpeedInput.value) || 1),
);

let lastFrameTime: number | null = null;
let rafId: number | null = null;

function playFrame(timestamp: number) {
  if (!isPlaying.value) { lastFrameTime = null; return; }
  if (lastFrameTime !== null) {
    const dt = timestamp - lastFrameTime;
    const advance = dt * playSpeed.value / 300; // 1× ≈ 30 s full sweep
    playerPosition.value = Math.min(100, playerPosition.value + advance);
    if (playerPosition.value >= 100) {
      playerPosition.value = 100;
      isPlaying.value = false;
      lastFrameTime = null;
      return;
    }
  }
  lastFrameTime = timestamp;
  rafId = requestAnimationFrame(playFrame);
}

function play() {
  if (playerPosition.value >= 100) playerPosition.value = 0;
  isPlaying.value = true;
  lastFrameTime = null;
  rafId = requestAnimationFrame(playFrame);
}

function pause() {
  isPlaying.value = false;
  if (rafId !== null) { cancelAnimationFrame(rafId); rafId = null; }
}

function togglePlay() {
  if (isPlaying.value) pause(); else play();
}

function resetPlayer() {
  pause();
  playerPosition.value = 0;
}

onUnmounted(() => { if (rafId !== null) cancelAnimationFrame(rafId); });

const brushRange = computed(() => ({
  from: 0,
  to:   playerPosition.value / 100,
}));

const playerTimeLabel = computed(() => {
  const pts = tracePoints.value;
  if (pts.length === 0) return null;
  const idx = Math.round((playerPosition.value / 100) * (pts.length - 1));
  const t = pts[idx]?.t;
  if (t === undefined) return null;
  return new Date(t / 1e6).toISOString().slice(11, 23) + " UTC";
});

// Timestamps captured from the last actual renderTrace() call — shown on the view.
const lastRenderStartNs = ref<number | null>(null);
const lastRenderEndNs   = ref<number | null>(null);

const renderWindowLabel = computed<string | null>(() => {
  if (!lastRenderStartNs.value || !lastRenderEndNs.value) return null;
  const fmt = (ns: number) => new Date(ns / 1e6).toISOString().slice(0, 19).replace("T", " ");
  return `${fmt(lastRenderStartNs.value)} → ${fmt(lastRenderEndNs.value)} UTC`;
});

// ── v2 base URL ───────────────────────────────────────────────────────────────
function getV2Base(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  return explicit && explicit.length > 0
    ? explicit
    : (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
}

function getAuthHeaders(): Record<string, string> {
  const { data: auth } = useAuth();
  const h: Record<string, string> = { "Content-Type": "application/json", Accept: "application/json" };
  if (auth.value?.accessToken) h["Authorization"] = `Bearer ${auth.value.accessToken}`;
  return h;
}

// ── channel list cache (for edit dialog + fetchBulkTrace) ─────────────────────

const channelList = ref<ChannelV2[]>([]);

async function fetchChannelList() {
  if (!containerId.value.trim()) return;
  const id = Number(containerId.value.trim());
  if (!isFinite(id)) return;
  try {
    const res = await fetch(
      getV2Base() + `/v2/timeseries-containers/${id}/channels?size=1000`,
      { headers: getAuthHeaders() },
    );
    if (!res.ok) return;
    channelList.value = await res.json();
  } catch { /* silent */ }
}

watch(containerId, (v) => { if (v.trim()) void fetchChannelList(); });

// ── edit channels dialog ──────────────────────────────────────────────────────

const showEditDialog = ref(false);

const currentSelection = computed<Partial<Trace3DChannelSelection>>(() => {
  const sel: Partial<Trace3DChannelSelection> = { colormap: colormapName.value };
  for (const b of bindings.value) {
    if (!b.parsed) continue;
    const tuple: Channel5Tuple = {
      measurement:  b.parsed.measurement,
      device:       b.parsed.device,
      location:     b.parsed.location,
      symbolicName: b.parsed.symbolicName,
      field:        b.parsed.field,
    };
    if (b.role === "x")     sel.x     = tuple;
    else if (b.role === "y")     sel.y     = tuple;
    else if (b.role === "z")     sel.z     = tuple;
    else if (b.role === "value") sel.value = tuple;
    else if (b.role === "rot_a") sel.rot_a = tuple;
    else if (b.role === "rot_b") sel.rot_b = tuple;
    else if (b.role === "rot_c") sel.rot_c = tuple;
  }
  return sel;
});

function onEditSave(sel: Trace3DChannelSelection) {
  const roleMap: [string, Channel5Tuple | null, boolean][] = [
    ["x",     sel.x,     true ],
    ["y",     sel.y,     true ],
    ["z",     sel.z,     true ],
    ["value", sel.value, false],
    ["rot_a", sel.rot_a, false],
    ["rot_b", sel.rot_b, false],
    ["rot_c", sel.rot_c, false],
  ];
  bindings.value = roleMap
    .filter(([, t]) => t !== null)
    .map(([role, t, required]) => ({
      role,
      channelSelector: JSON.stringify(t),
      unit: null,
      required,
      status: "DIRECT",
      parsed: t!,
    }));
  colormapName.value = sel.colormap;
  void renderTrace();
}

// ── capture screenshot ────────────────────────────────────────────────────────

const trace3dViewRef = ref<{ captureDataUrl: () => string } | null>(null);

function captureTrace3D() {
  const dataUrl = trace3dViewRef.value?.captureDataUrl();
  if (!dataUrl || dataUrl.length < 10) return;
  const a = document.createElement("a");
  a.href = dataUrl;
  a.download = `trace3d-${new Date().toISOString().slice(0, 19).replace(/[T:]/g, "-")}.png`;
  a.click();
}

// ── fetch bindings from POST /v2/shapes/render ────────────────────────────────
async function fetchBindings() {
  if (!templateAppId.value.trim() || !focusShepardId.value.trim()) return;
  isFetching.value = true;
  fetchError.value = null;
  bindings.value = [];
  tracePoints.value = [];
  try {
    const res = await fetch(getV2Base() + "/v2/shapes/render", {
      method: "POST",
      headers: getAuthHeaders(),
      body: JSON.stringify({
        templateAppId: templateAppId.value.trim(),
        focusShepardId: focusShepardId.value.trim(),
      }),
    });
    const body = await res.json();
    if (!res.ok) {
      fetchError.value = `${res.status} ${res.statusText}: ${JSON.stringify(body)}`;
      return;
    }
    renderer.value = body.renderer ?? null;
    bindings.value = (body.channelBindings ?? []).map((b: {
      role: string; channelSelector: string;
      unit: string | null; required: boolean; status: string;
    }) => {
      let parsed = null;
      try { parsed = JSON.parse(b.channelSelector ?? "null"); } catch { /* bad selector */ }
      return { ...b, parsed } as Binding;
    });
  } catch (e) {
    fetchError.value = e instanceof Error ? e.message : String(e);
  } finally {
    isFetching.value = false;
  }
}

// ── bulk channel fetch (TS-OPT2) — one request for all roles ─────────────────
async function fetchBulkTrace(
  channels: { role: string; parsed: NonNullable<Binding["parsed"]> }[],
  startNs: number,
  endNs: number,
): Promise<Map<string, [number, number][]>> {
  const result = new Map<string, [number, number][]>();
  if (!containerId.value.trim() || channels.length === 0) return result;
  const id = Number(containerId.value.trim());
  if (!isFinite(id)) return result;

  try {
    // Step 1: use cached channel list or fetch + cache
    let localChannelList = channelList.value;
    if (localChannelList.length === 0) {
      const listRes = await fetch(
        getV2Base() + `/v2/timeseries-containers/${id}/channels?size=1000`,
        { headers: getAuthHeaders() },
      );
      if (!listRes.ok) return result;
      localChannelList = await listRes.json();
      channelList.value = localChannelList;
    }

    const norm = (s: string | null | undefined) => (s ?? "").trim();
    const tupleKey = (m: string, d: string, l: string, sn: string, f: string) =>
      `${norm(m)}|${norm(d)}|${norm(l)}|${norm(sn)}|${norm(f)}`;

    const tupleToShepardId = new Map<string, string>();
    for (const ch of localChannelList) {
      tupleToShepardId.set(
        tupleKey(ch.measurement as string, ch.device as string, ch.location as string, ch.symbolicName as string, ch.field as string),
        ch.shepardId,
      );
    }

    // Step 2: resolve each role's 5-tuple; build shepardId → role reverse map
    const shepardIdToRole = new Map<string, string>();
    const shepardIds: string[] = [];
    for (const { role, parsed } of channels) {
      const key = tupleKey(parsed.measurement, parsed.device, parsed.location, parsed.symbolicName, parsed.field);
      const shepardId = tupleToShepardId.get(key);
      if (shepardId) {
        shepardIds.push(shepardId);
        shepardIdToRole.set(shepardId, role);
      }
    }
    if (shepardIds.length === 0) return result;

    // Step 3: bulk fetch
    const res = await fetch(
      getV2Base() + `/v2/timeseries-containers/${id}/channels/data/bulk`,
      {
        method:  "POST",
        headers: getAuthHeaders(),
        body:    JSON.stringify({ shepardIds, start: startNs, end: endNs }),
      },
    );
    if (!res.ok) return result;

    // Step 4: map response entries back to roles via 5-tuple
    const body: {
      timeseries: { measurement: string; device: string; location: string; symbolicName: string; field: string };
      points: { timestamp: number; value: number }[];
    }[] = await res.json();

    for (const entry of body) {
      const ts = entry.timeseries;
      const key = tupleKey(ts.measurement, ts.device, ts.location, ts.symbolicName, ts.field);
      const shepardId = tupleToShepardId.get(key);
      const role = shepardId ? shepardIdToRole.get(shepardId) : undefined;
      if (role) {
        result.set(role, (entry.points ?? []).map(p => [p.timestamp, p.value as number] as [number, number]));
      }
    }
  } catch {
    /* swallow — caller will see missing keys as empty arrays */
  }
  return result;
}

// ── render trace ──────────────────────────────────────────────────────────────
async function renderTrace() {
  if (!containerId.value.trim()) {
    renderError.value = "TS container ID is required to fetch channel data.";
    return;
  }
  isRendering.value = true;
  renderError.value = null;
  tracePoints.value = [];

  const now     = Date.now() * 1_000_000;
  const startNs = refStartNs.value ?? (now - windowHours.value * 3_600_000_000_000);
  const endNs   = refEndNs.value   ?? now;
  lastRenderStartNs.value = startNs;
  lastRenderEndNs.value   = endNs;

  const byRole = new Map<string, Binding>();
  for (const b of bindings.value) {
    if (b.parsed) byRole.set(b.role, b);
  }

  const xB = byRole.get("x");
  const yB = byRole.get("y");
  const zB = byRole.get("z");

  if (!xB?.parsed || !yB?.parsed || !zB?.parsed) {
    renderError.value = "Template must declare roles x, y, and z to render a 3D trace. Found: " +
      bindings.value.map(b => b.role).join(", ");
    isRendering.value = false;
    return;
  }

  try {
    const roleChannels: { role: string; parsed: NonNullable<Binding["parsed"]> }[] = [
      { role: "x", parsed: xB.parsed },
      { role: "y", parsed: yB.parsed },
      { role: "z", parsed: zB.parsed },
    ];
    const vB = byRole.get("value") ?? byRole.get("color") ?? byRole.get("intensity");
    if (vB?.parsed)              roleChannels.push({ role: "value",  parsed: vB.parsed });
    const rotAB = byRole.get("rot_a");
    const rotBB = byRole.get("rot_b");
    const rotCB = byRole.get("rot_c");
    if (rotAB?.parsed) roleChannels.push({ role: "rot_a", parsed: rotAB.parsed });
    if (rotBB?.parsed) roleChannels.push({ role: "rot_b", parsed: rotBB.parsed });
    if (rotCB?.parsed) roleChannels.push({ role: "rot_c", parsed: rotCB.parsed });

    const byRolePts = await fetchBulkTrace(roleChannels, startNs, endNs);
    const empty: [number, number][] = [];
    const xPts    = byRolePts.get("x")     ?? empty;
    const yPts    = byRolePts.get("y")     ?? empty;
    const zPts    = byRolePts.get("z")     ?? empty;
    const vPts    = byRolePts.get("value") ?? empty;
    const rotAPts = byRolePts.get("rot_a") ?? empty;
    const rotBPts = byRolePts.get("rot_b") ?? empty;
    const rotCPts = byRolePts.get("rot_c") ?? empty;

    if (xPts.length < 2) {
      renderError.value = `Channel '${xB.role}' returned ${xPts.length} points. Check the container ID and time window.`;
      isRendering.value = false;
      return;
    }

    const pts: TracePoint[] = xPts.map(([t, xv]) => ({
      x:     xv,
      y:     yPts.length >= 2 ? lerpSeries(yPts, t) : 0,
      z:     zPts.length >= 2 ? lerpSeries(zPts, t) : 0,
      value: vPts.length >= 2 ? lerpSeries(vPts, t) : NaN,
      t,
      ...(rotAPts.length >= 2 ? { eulerA: lerpSeries(rotAPts, t) } : {}),
      ...(rotBPts.length >= 2 ? { eulerB: lerpSeries(rotBPts, t) } : {}),
      ...(rotCPts.length >= 2 ? { eulerC: lerpSeries(rotCPts, t) } : {}),
    }));

    tracePoints.value = pts;
    resetPlayer();

    // Compute interpolation quality stats for the info panel
    let maxGapMs: number | null = null;
    if (xPts.length >= 2) {
      let maxGapNs = 0;
      for (let i = 1; i < xPts.length; i++) {
        const gap = xPts[i]![0] - xPts[i - 1]![0];
        if (gap > maxGapNs) maxGapNs = gap;
      }
      maxGapMs = maxGapNs / 1_000_000;
    }
    const channelStatsArr: ChannelStat[] = [];
    if (yPts.length > 0)    channelStatsArr.push({ role: "y",     sourcePoints: yPts.length });
    if (zPts.length > 0)    channelStatsArr.push({ role: "z",     sourcePoints: zPts.length });
    if (vPts.length > 0)    channelStatsArr.push({ role: "value", sourcePoints: vPts.length });
    if (rotAPts.length > 0) channelStatsArr.push({ role: "rot_a", sourcePoints: rotAPts.length });
    if (rotBPts.length > 0) channelStatsArr.push({ role: "rot_b", sourcePoints: rotBPts.length });
    if (rotCPts.length > 0) channelStatsArr.push({ role: "rot_c", sourcePoints: rotCPts.length });
    renderStats.value = { xPoints: xPts.length, channelStats: channelStatsArr, maxGapMs };

    valueLabel.value = vB
      ? `${vB.role} (${vB.parsed?.symbolicName ?? "?"})${vB.unit ? " [" + vB.unit.split("/").pop() + "]" : ""}`
      : "time gradient";
  } catch (e) {
    renderError.value = e instanceof Error ? e.message : String(e);
  } finally {
    isRendering.value = false;
  }
}

const colormapOptions: ColormapName[] = ["inferno", "viridis", "plasma"];

// ── renderer dispatch helpers ─────────────────────────────────────────────────

const rendererKind = computed<"trace-3d" | "table" | "unknown">(() => {
  const r = renderer.value?.toLowerCase() ?? "";
  if (r === "trace-3d" || r === "tresjs") return "trace-3d";
  if (r === "table")                       return "table";
  return "unknown";
});

const trace3DColorScheme = computed<Trace3DColorScheme>(() => {
  switch (colormapName.value) {
    case "viridis": return "viridis";
    case "plasma":  return "heat";
    default:        return "heat";
  }
});

const xData     = computed(() => tracePoints.value.map(p => p.x));
const yData     = computed(() => tracePoints.value.map(p => p.y));
const zData     = computed(() => tracePoints.value.map(p => p.z));
const valueData = computed(() => tracePoints.value.map(p => p.value));

const canRender = computed(() =>
  bindings.value.some(b => b.role === "x" && b.parsed) &&
  bindings.value.some(b => b.role === "y" && b.parsed) &&
  bindings.value.some(b => b.role === "z" && b.parsed),
);

// ── bootstrap from query params (ViewRecipeBuilderDialog → this page) ─────────
onMounted(() => {
  const q = useRoute().query;
  if (!q.roles || !q.containerId) return;

  try {
    const roles = JSON.parse(atob(String(q.roles))) as Record<string, {
      measurement: string; device: string; location: string;
      symbolicName: string; field: string;
    }>;
    bindings.value = Object.entries(roles).map(([role, ch]) => ({
      role,
      channelSelector: JSON.stringify(ch),
      unit: null,
      required: role !== "value",
      status: "DIRECT",
      parsed: ch,
    }));
  } catch {
    return;
  }

  containerId.value    = String(q.containerId);
  refStartNs.value     = q.startNs ? Number(q.startNs) : null;
  refEndNs.value       = q.endNs   ? Number(q.endNs)   : null;
  colormapName.value   = (q.colormap as ColormapName | undefined) ?? "inferno";
  renderer.value       = q.renderer ? String(q.renderer) : "trace-3d";
  fromReference.value  = true;

  void fetchChannelList();
  void renderTrace();
});
</script>

<template>
  <v-container>
    <!-- ── header ─────────────────────────────────────────────────────────── -->
    <div class="d-flex flex-column ga-2 mb-4">
      <h4 class="text-h4">Shape render playground</h4>
      <p class="text-body-1 text-medium-emphasis">
        Project a <code>VIEW_RECIPE</code> template's channel bindings onto a focus DataObject
        and render the result as a 3D trace. Backed by
        <code>POST /v2/shapes/render</code> (TPL2b).
      </p>
    </div>

    <!-- ── from-reference banner ────────────────────────────────────────────── -->
    <v-alert
      v-if="fromReference"
      type="info"
      variant="tonal"
      density="compact"
      class="mb-4"
      prepend-icon="mdi-cube-outline"
    >
      Rendering from a Timeseries Reference · container {{ containerId }}
      <span v-if="refStartNs && refEndNs" class="text-caption ml-2">
        ({{ new Date(refStartNs / 1e6).toISOString().slice(0, 19).replace("T", " ") }}
        → {{ new Date(refEndNs / 1e6).toISOString().slice(0, 19).replace("T", " ") }} UTC)
      </span>
    </v-alert>

    <!-- ── input form ─────────────────────────────────────────────────────── -->
    <v-row v-if="!fromReference" class="mb-2">
      <v-col cols="12" md="5">
        <v-text-field
          v-model="templateAppId"
          label="Template appId (VIEW_RECIPE)"
          variant="outlined"
          density="compact"
          clearable
          hint="UUID v7 of the ShepardTemplate with templateKind=VIEW_RECIPE"
          persistent-hint
        />
      </v-col>
      <v-col cols="12" md="5">
        <v-text-field
          v-model="focusShepardId"
          label="Focus DataObject appId"
          variant="outlined"
          density="compact"
          clearable
          hint="The DataObject to project the template onto"
          persistent-hint
        />
      </v-col>
      <v-col cols="12" md="2" class="d-flex align-center">
        <v-btn
          color="primary"
          :loading="isFetching"
          :disabled="!templateAppId.trim() || !focusShepardId.trim()"
          block
          @click="fetchBindings"
        >
          <v-icon start>mdi-shape</v-icon> Fetch bindings
        </v-btn>
      </v-col>
    </v-row>

    <v-alert v-if="fetchError" type="error" variant="tonal" class="mb-4">
      <pre class="text-caption">{{ fetchError }}</pre>
    </v-alert>

    <!-- ── binding declarations ───────────────────────────────────────────── -->
    <template v-if="bindings.length > 0">
      <v-card variant="outlined" class="mb-4">
        <v-card-title class="text-subtitle-1 d-flex align-center ga-2">
          <v-icon>mdi-link-variant</v-icon>
          Channel bindings
          <v-chip size="x-small" color="info" variant="tonal" class="ml-1">
            renderer: {{ renderer ?? "none" }}
          </v-chip>
          <v-spacer />
          <v-btn
            v-if="containerId.trim()"
            size="x-small"
            variant="tonal"
            prepend-icon="mdi-pencil-outline"
            @click="showEditDialog = true"
          >
            Edit channels
          </v-btn>
        </v-card-title>
        <v-table density="compact">
          <thead>
            <tr>
              <th>Role</th>
              <th>measurement</th>
              <th>device</th>
              <th>symbolicName</th>
              <th>field</th>
              <th>unit</th>
              <th>required</th>
              <th>status</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="b in bindings" :key="b.role">
              <td><code>{{ b.role }}</code></td>
              <td class="text-caption">{{ b.parsed?.measurement ?? "—" }}</td>
              <td class="text-caption">{{ b.parsed?.device ?? "—" }}</td>
              <td class="text-caption">{{ b.parsed?.symbolicName ?? "—" }}</td>
              <td class="text-caption">{{ b.parsed?.field ?? "—" }}</td>
              <td class="text-caption text-truncate" style="max-width:120px">{{ b.unit ?? "—" }}</td>
              <td>
                <v-icon size="small" :color="b.required ? 'error' : 'success'">
                  {{ b.required ? "mdi-asterisk" : "mdi-minus" }}
                </v-icon>
              </td>
              <td>
                <v-chip size="x-small" color="warning" variant="tonal">{{ b.status }}</v-chip>
              </td>
            </tr>
          </tbody>
        </v-table>
      </v-card>

      <!-- ── 3D render controls ──────────────────────────────────────────── -->
      <v-row class="mb-2" align="center">
        <v-col cols="12" md="4">
          <v-text-field
            v-model="containerId"
            label="TS container ID (numeric)"
            variant="outlined"
            density="compact"
            hint="The timeseries container that holds the x/y/z channels (from /containers/timeseries/{id})"
            persistent-hint
          />
        </v-col>
        <v-col v-if="!fromReference" cols="12" md="2">
          <v-text-field
            v-model.number="windowHours"
            label="Time window (hours)"
            variant="outlined"
            density="compact"
            type="number"
            :min="0.1"
            :max="72"
          />
        </v-col>
        <v-col cols="12" md="2">
          <v-select
            v-model="colormapName"
            label="Colormap"
            :items="colormapOptions"
            variant="outlined"
            density="compact"
          />
        </v-col>
        <v-col cols="12" md="2" class="d-flex align-center">
          <v-btn
            color="secondary"
            :loading="isRendering"
            :disabled="!canRender"
            block
            @click="renderTrace"
          >
            <v-icon start>mdi-cube-outline</v-icon> Render 3D
          </v-btn>
        </v-col>
      </v-row>

      <v-alert v-if="!canRender && bindings.length > 0" type="info" variant="tonal" class="mb-2" density="compact">
        Template must declare roles <code>x</code>, <code>y</code>, and <code>z</code> with parseable
        channelSelectors. Found: {{ bindings.map(b => b.role).join(", ") || "none" }}.
      </v-alert>

      <v-alert v-if="renderError" type="error" variant="tonal" class="mb-2">
        <pre class="text-caption">{{ renderError }}</pre>
      </v-alert>

      <!-- ── renderer dispatch ──────────────────────────────────────────── -->
      <template v-if="tracePoints.length > 0">

        <!-- trace-3d / tresjs → Trace3DView -->
        <ClientOnly v-if="rendererKind === 'trace-3d'">
          <div class="d-flex align-center ga-2 mb-1">
            <template v-if="renderWindowLabel">
              <v-icon size="small" color="primary">mdi-clock-time-four-outline</v-icon>
              <span class="text-caption text-medium-emphasis font-weight-medium">
                {{ renderWindowLabel }}
              </span>
              <v-chip v-if="fromReference" size="x-small" color="primary" variant="tonal">
                from reference
              </v-chip>
            </template>
            <v-spacer />
            <v-btn
              size="x-small"
              variant="tonal"
              prepend-icon="mdi-camera"
              @click="captureTrace3D"
            >
              Capture PNG
            </v-btn>
          </div>
          <Trace3DView
            ref="trace3dViewRef"
            :x-data="xData"
            :y-data="yData"
            :z-data="zData"
            :value-data="valueData"
            :value-label="valueLabel"
            :color-scheme="trace3DColorScheme"
            :brush-range="brushRange"
          />
          <template #fallback>
            <v-skeleton-loader type="image" height="500" />
          </template>
        </ClientOnly>

        <!-- table → inline binding-values table -->
        <v-card v-else-if="rendererKind === 'table'" variant="outlined" class="mt-2">
          <v-card-title class="text-subtitle-2 d-flex align-center ga-1">
            <v-icon size="small">mdi-table</v-icon>
            Channel values — table renderer
          </v-card-title>
          <v-table density="compact">
            <thead>
              <tr>
                <th>role</th>
                <th>x (first)</th>
                <th>y (first)</th>
                <th>z (first)</th>
                <th>value (first)</th>
                <th>points</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td class="text-caption">all roles</td>
                <td class="text-caption">{{ xData[0]?.toFixed(4) ?? "—" }}</td>
                <td class="text-caption">{{ yData[0]?.toFixed(4) ?? "—" }}</td>
                <td class="text-caption">{{ zData[0]?.toFixed(4) ?? "—" }}</td>
                <td class="text-caption">{{ valueData[0]?.toFixed(4) ?? "—" }}</td>
                <td class="text-caption">{{ tracePoints.length }}</td>
              </tr>
            </tbody>
          </v-table>
        </v-card>

        <!-- unknown renderer → placeholder with hint -->
        <v-alert v-else type="warning" variant="tonal" class="mt-2" density="compact">
          <strong>Unsupported renderer: <code>{{ renderer ?? "(none)" }}</code></strong>
          — {{ tracePoints.length }} points were fetched but no frontend component handles this renderer hint yet.
          Supported renderers: <code>trace-3d</code>, <code>tresjs</code>, <code>table</code>.
        </v-alert>

      </template>

      <!-- ── interpolation info ────────────────────────────────────────── -->
      <v-card v-if="renderStats" variant="outlined" class="mt-3">
        <v-card-title class="text-subtitle-2 d-flex align-center ga-2 pt-3 px-4 pb-1">
          <v-icon size="small" color="info">mdi-function-variant</v-icon>
          Interpolation &amp; data validity
        </v-card-title>
        <v-card-text class="pt-1 pb-3 px-4">
          <div class="d-flex flex-wrap ga-2 align-center mb-2">
            <v-chip size="x-small" color="primary" variant="tonal">
              X: {{ renderStats.xPoints }} pts (reference clock)
            </v-chip>
            <v-chip
              v-for="cs in renderStats.channelStats"
              :key="cs.role"
              size="x-small"
              :color="cs.sourcePoints < renderStats.xPoints ? 'warning' : 'success'"
              variant="tonal"
            >
              {{ cs.role }}: {{ cs.sourcePoints }} pts
              <template v-if="cs.sourcePoints < renderStats.xPoints">
                (↑ linear interp to {{ renderStats.xPoints }})
              </template>
            </v-chip>
            <v-chip
              v-if="renderStats.maxGapMs !== null"
              size="x-small"
              :color="renderStats.maxGapMs > 1000 ? 'error' : renderStats.maxGapMs > 100 ? 'warning' : 'success'"
              variant="tonal"
            >
              max gap: {{ renderStats.maxGapMs < 1 ? '<1' : renderStats.maxGapMs.toFixed(0) }} ms
            </v-chip>
          </div>
          <div class="text-caption text-medium-emphasis">
            Method: linear interpolation of all channels onto X timestamps.
            Channels with fewer source points than X are resampled — validity
            depends on original sample rate vs. X rate.
            <span v-if="renderStats.maxGapMs !== null && renderStats.maxGapMs > 1000" class="text-error ml-1">
              Large gap detected ({{ (renderStats.maxGapMs / 1000).toFixed(1) }} s) —
              interpolated values across this gap may not reflect real data.
            </span>
          </div>
        </v-card-text>
      </v-card>

      <!-- ── playback player ────────────────────────────────────────────── -->
      <v-card v-if="tracePoints.length > 0" variant="outlined" class="mt-3">
        <v-card-title class="text-subtitle-2 d-flex align-center ga-2 pt-3 px-4 pb-1">
          <v-icon size="small" color="primary">mdi-play-circle-outline</v-icon>
          Playback
          <v-spacer />
          <v-btn-toggle
            v-model="selectedSpeedIdx"
            mandatory
            density="compact"
            variant="tonal"
          >
            <v-btn :value="0" size="x-small">0.1×</v-btn>
            <v-btn :value="1" size="x-small">0.5×</v-btn>
            <v-btn :value="2" size="x-small">1×</v-btn>
            <v-btn :value="3" size="x-small">2×</v-btn>
            <v-btn :value="4" size="x-small">5×</v-btn>
            <v-btn :value="5" size="x-small">custom</v-btn>
          </v-btn-toggle>
          <v-text-field
            v-if="selectedSpeedIdx === 5"
            v-model.number="customSpeedInput"
            density="compact"
            variant="outlined"
            hide-details
            type="number"
            min="0.01"
            max="100"
            suffix="×"
            style="max-width:72px"
            class="ml-1"
          />
        </v-card-title>
        <v-card-text class="d-flex align-center ga-3 py-2 px-4">
          <v-btn
            :icon="isPlaying ? 'mdi-pause' : 'mdi-play'"
            size="small"
            :color="isPlaying ? 'warning' : 'primary'"
            variant="tonal"
            @click="togglePlay"
          />
          <v-btn
            icon="mdi-skip-previous"
            size="small"
            variant="text"
            density="compact"
            @click="resetPlayer"
          />
          <v-slider
            v-model="playerPosition"
            :min="0"
            :max="100"
            :step="0.1"
            color="primary"
            track-color="grey-darken-3"
            density="compact"
            hide-details
            class="flex-grow-1"
            @update:model-value="isPlaying && pause()"
          />
          <span class="text-caption text-medium-emphasis" style="min-width:36px; text-align:right">
            {{ Math.round(playerPosition) }}%
          </span>
        </v-card-text>
        <div v-if="playerTimeLabel" class="text-caption text-medium-emphasis px-4 pb-2">
          {{ playerTimeLabel }}
        </div>
      </v-card>

      <v-card v-if="tracePoints.length > 0" variant="tonal" color="success" class="mt-2 pa-3">
        <span class="text-body-2">
          <strong>{{ tracePoints.length }}</strong> time-aligned points rendered.
          Drag to orbit · scroll to zoom · right-drag to pan.
        </span>
      </v-card>
    </template>

    <!-- ── placeholder status ─────────────────────────────────────────────── -->
    <PlaceholderImplStatus
      backend="shipped"
      backlog-row="TPL2b"
      design-doc="aidocs/semantics/98-shapes-views-and-process-model.md"
      endpoint="/v2/shapes/render"
      notes="Beta: all bindings return status=DECLARED. Live channel resolution (TPL2c) ships after the TS-ID migration (aidocs/platform/87). Color-mapped 3D trace via Three.js."
    />
  </v-container>

  <!-- ── edit channels dialog ──────────────────────────────────────────────── -->
  <Trace3DEditChannelsDialog
    v-model="showEditDialog"
    :container-id="Number(containerId) || 0"
    :channels="channelList"
    :initial="currentSelection"
    @save="onEditSave"
  />
</template>

<style scoped>
pre {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>

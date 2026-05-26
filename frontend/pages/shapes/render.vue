<script setup lang="ts">
/**
 * /shapes/render — VIEW_RECIPE render playground (TPL2b).
 *
 * Calls POST /v2/shapes/render → shows channel binding declarations
 * → fetches TS data per role → renders a 3D trace via Trace3DCanvas.
 *
 * Beta: bindings come back status=DECLARED (no live resolution).
 * User supplies the TS container ID to complete the pipeline until TPL2c ships.
 */
import { TimeseriesContainerApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { lerpSeries, type ColormapName } from "~/utils/colormap";
import PlaceholderImplStatus from "~/components/common/placeholder/PlaceholderImplStatus.vue";

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

interface TracePoint { x: number; y: number; z: number; value: number }
const tracePoints = ref<TracePoint[]>([]);
const valueLabel  = ref<string>("");

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

// ── fetch one channel's data ───────────────────────────────────────────────────
async function fetchChannel(
  parsed: NonNullable<Binding["parsed"]>,
  startNs: number,
  endNs: number,
): Promise<[number, number][]> {
  if (!containerId.value.trim()) return [];
  const id = Number(containerId.value.trim());
  if (!isFinite(id)) return [];
  try {
    const result = await useShepardApi(TimeseriesContainerApi).value.getTimeseries({
      timeseriesContainerId: id,
      measurement:  parsed.measurement,
      device:       parsed.device,
      location:     parsed.location,
      symbolicName: parsed.symbolicName,
      field:        parsed.field,
      start:        startNs,
      end:          endNs,
      downsample:   "lttb",
      maxPoints:    2000,
    });
    return (result.points ?? []).map(p => [p.timestamp!, p.value!] as [number, number]);
  } catch {
    return [];
  }
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

  const byRole = new Map<string, Binding>();
  for (const b of bindings.value) {
    if (b.parsed) byRole.set(b.role, b);
  }

  const xB = byRole.get("x");
  const yB = byRole.get("y");
  const zB = byRole.get("z");
  const vB = byRole.get("value") ?? byRole.get("color") ?? byRole.get("intensity");

  if (!xB?.parsed || !yB?.parsed || !zB?.parsed) {
    renderError.value = "Template must declare roles x, y, and z to render a 3D trace. Found: " +
      bindings.value.map(b => b.role).join(", ");
    isRendering.value = false;
    return;
  }

  try {
    const [xPts, yPts, zPts, vPts] = await Promise.all([
      fetchChannel(xB.parsed, startNs, endNs),
      fetchChannel(yB.parsed, startNs, endNs),
      fetchChannel(zB.parsed, startNs, endNs),
      vB?.parsed ? fetchChannel(vB.parsed, startNs, endNs) : Promise.resolve([] as [number, number][]),
    ]);

    if (xPts.length < 2) {
      renderError.value = `Channel '${xB.role}' returned ${xPts.length} points. Check the container ID and time window.`;
      isRendering.value = false;
      return;
    }

    // Align y, z, value onto x timestamps by linear interpolation
    const pts: TracePoint[] = xPts.map(([t, xv]) => ({
      x:     xv,
      y:     yPts.length >= 2 ? lerpSeries(yPts, t) : 0,
      z:     zPts.length >= 2 ? lerpSeries(zPts, t) : 0,
      value: vPts.length >= 2 ? lerpSeries(vPts, t) : NaN,
    }));

    tracePoints.value = pts;
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
  fromReference.value  = true;

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

    <!-- ── from-reference banner (replaces the template form) ──────────────── -->
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
        <v-col cols="12" md="2">
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

      <!-- ── 3D canvas ───────────────────────────────────────────────────── -->
      <ClientOnly v-if="tracePoints.length > 0">
        <Trace3DCanvas
          :points="tracePoints"
          :colormap="colormapName"
          :label="valueLabel"
        />
        <template #fallback>
          <v-skeleton-loader type="image" height="500" />
        </template>
      </ClientOnly>

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
</template>

<style scoped>
pre {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>

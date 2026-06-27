<script setup lang="ts">
/**
 * KRL-INTERPRETER-06 — post-run summary panel.
 *
 * Renders the outcome of POST /v2/krl/interpret:
 *  - success/failure status chip
 *  - "Open URDF view" deep-link to the URDF render page (when a
 *    urdfFileAppId is provided alongside the trajectory)
 *  - warnings table
 *  - unsupported constructs table
 *  - IK convergence stats card
 *
 * On error (502 in particular — sidecar opt-in not running), the user-friendly
 * message + operator hint is rendered as the headline.
 *
 * Per `aidocs/integrations/117 §13.1` (IME/AQE persona-board lens), every
 * trajectory rendered here is explicitly labelled as
 *   "interpreter-resolved offline replay"
 * — never as as-executed motion. The label is part of the success chip caption.
 */
import type {
  KrlInterpretError,
  KrlInterpretResponse,
} from "~/composables/useKrlInterpret";

interface Props {
  /** Sidecar response (success path). null when an error happened. */
  response?: KrlInterpretResponse | null;
  /** Error payload (failure path). */
  error?: KrlInterpretError | null;
  /**
   * Pass-through hint so the deep-link can wire ?urdfUrl= to the same
   * URDF FileReference the user picked in the dialog. The result panel
   * itself doesn't fetch a payload — it just builds the link.
   */
  urdfPayloadUrl?: string | null;
  /** Optional path back to the parent DataObject detail page. */
  dataObjectPath?: string | null;
}

const props = withDefaults(defineProps<Props>(), {
  response: null,
  error: null,
  urdfPayloadUrl: null,
  dataObjectPath: null,
});

const isSuccess = computed(() => !!props.response && !props.error);

// ── Build the URDF-view deep-link (shapes/render?renderer=urdf&urdfUrl=…) ─────
// Per the existing render route at frontend/pages/shapes/render.vue lines
// 497–536, the URDF renderer takes ?renderer=urdf&urdfUrl=<encoded>. The
// trajectory is bound at view time via the urn:shepard:urdf:joint annotations
// already on the persisted TimeseriesReference (no trajectory query param is
// consumed by the current render route — auto-binding handles it).
const urdfDeepLink = computed<string | null>(() => {
  if (!props.urdfPayloadUrl) return null;
  const encoded = encodeURIComponent(props.urdfPayloadUrl);
  return `/shapes/render?renderer=urdf&urdfUrl=${encoded}`;
});

const warnings = computed(() => props.response?.warnings ?? []);
const unsupported = computed(() => props.response?.unsupportedConstructs ?? []);
const stats = computed(() => props.response?.ikSolverStats ?? null);

const warningsHeaders = [
  { title: "Line", key: "line", sortable: true },
  { title: "Severity", key: "severity", sortable: true },
  { title: "Message", key: "message", sortable: false },
];
const unsupportedHeaders = [
  { title: "Construct", key: "construct", sortable: true },
  { title: "Line", key: "line", sortable: true },
  { title: "Reason", key: "reason", sortable: false },
];

function severityColor(severity: string): string {
  switch (severity) {
    case "ERROR":
      return "error";
    case "WARN":
      return "warning";
    case "INFO":
      return "info";
    default:
      return "default";
  }
}
</script>

<template>
  <v-card variant="outlined" class="pa-2">
    <!-- Status chip + headline -->
    <v-card-title class="d-flex align-center ga-2 flex-wrap">
      <v-chip
        v-if="isSuccess"
        color="success"
        variant="tonal"
        prepend-icon="mdi-check-circle"
        size="small"
      >
        Interpreter resolved offline replay
      </v-chip>
      <v-chip
        v-else-if="error"
        :color="error.status === 502 ? 'warning' : 'error'"
        variant="tonal"
        :prepend-icon="error.status === 502 ? 'mdi-power-plug-off' : 'mdi-alert-circle'"
        size="small"
      >
        HTTP {{ error.status }}
      </v-chip>
      <div v-if="isSuccess" class="text-body-2 text-medium-emphasis">
        Trajectory persisted as TimeseriesReference
      </div>
    </v-card-title>

    <!-- Error: friendly message + operator hint -->
    <v-card-text v-if="error" data-test="krl-error-message">
      <v-alert
        :type="error.status === 502 ? 'warning' : 'error'"
        variant="tonal"
        class="mb-2"
      >
        {{ error.message }}
      </v-alert>
      <div
        v-if="error.detail"
        class="text-caption text-medium-emphasis font-monospace"
        style="word-break: break-word"
      >
        {{ error.detail }}
      </div>
    </v-card-text>

    <!-- Success: trajectory IDs + deep-link + tables -->
    <v-card-text v-if="isSuccess && response">
      <v-row dense>
        <v-col cols="12" sm="6">
          <div class="text-caption text-medium-emphasis">
            Trajectory appId
          </div>
          <div class="font-monospace text-body-2">
            {{ response.trajectoryAppId }}
          </div>
        </v-col>
        <v-col cols="12" sm="6">
          <div class="text-caption text-medium-emphasis">
            Activity appId
          </div>
          <div class="font-monospace text-body-2">
            {{ response.activityAppId }}
          </div>
        </v-col>
        <v-col cols="12">
          <div class="text-caption text-medium-emphasis">
            Interpreter version
          </div>
          <div class="text-body-2">
            {{ response.interpreterVersion ?? "(unknown)" }}
          </div>
        </v-col>
      </v-row>

      <v-row class="mt-2" dense>
        <v-col cols="12" class="d-flex ga-2 flex-wrap">
          <v-btn
            v-if="urdfDeepLink"
            :href="urdfDeepLink"
            color="primary"
            variant="flat"
            prepend-icon="mdi-cube-outline"
            data-test="krl-open-urdf-view"
          >
            Run preview
            <v-icon end>mdi-arrow-top-right</v-icon>
          </v-btn>
          <v-btn
            v-if="dataObjectPath"
            :to="dataObjectPath"
            variant="outlined"
            prepend-icon="mdi-database"
          >
            Back to DataObject
          </v-btn>
        </v-col>
      </v-row>

      <!-- IK stats -->
      <v-row v-if="stats" class="mt-3" dense>
        <v-col cols="12">
          <div class="text-subtitle-2">IK convergence</div>
        </v-col>
        <v-col cols="6" sm="3">
          <div class="text-caption text-medium-emphasis">Mean cycle</div>
          <div class="text-body-2">
            {{ stats.meanCycleMs?.toFixed(2) ?? "—" }} ms
          </div>
        </v-col>
        <v-col cols="6" sm="3">
          <div class="text-caption text-medium-emphasis">p99 cycle</div>
          <div class="text-body-2">
            {{ stats.p99CycleMs?.toFixed(2) ?? "—" }} ms
          </div>
        </v-col>
        <v-col cols="6" sm="3">
          <div class="text-caption text-medium-emphasis">
            Max residual (pos)
          </div>
          <div class="text-body-2">
            {{ stats.maxResidualMeters?.toExponential(2) ?? "—" }} m
          </div>
        </v-col>
        <v-col cols="6" sm="3">
          <div class="text-caption text-medium-emphasis">
            Failed / total poses
          </div>
          <div class="text-body-2">
            {{ stats.failedPoses ?? "—" }} / {{ stats.totalPoses ?? "—" }}
          </div>
        </v-col>
      </v-row>

      <!-- Warnings -->
      <v-row v-if="warnings.length > 0" class="mt-3">
        <v-col cols="12">
          <div class="text-subtitle-2 mb-1">
            Warnings ({{ warnings.length }})
          </div>
          <v-data-table
            :items="warnings"
            :headers="warningsHeaders"
            density="compact"
            items-per-page="10"
            data-test="krl-warnings-table"
          >
            <template #[`item.severity`]="{ value }">
              <v-chip :color="severityColor(String(value))" size="x-small">
                {{ value }}
              </v-chip>
            </template>
            <template #[`item.line`]="{ value }">
              <span class="font-monospace">{{ value ?? "—" }}</span>
            </template>
          </v-data-table>
        </v-col>
      </v-row>

      <!-- Unsupported constructs -->
      <v-row v-if="unsupported.length > 0" class="mt-3">
        <v-col cols="12">
          <div class="text-subtitle-2 mb-1">
            Unsupported constructs ({{ unsupported.length }})
          </div>
          <v-data-table
            :items="unsupported"
            :headers="unsupportedHeaders"
            density="compact"
            items-per-page="10"
            data-test="krl-unsupported-table"
          >
            <template #[`item.construct`]="{ value }">
              <v-chip color="warning" size="x-small" variant="tonal">
                {{ value }}
              </v-chip>
            </template>
            <template #[`item.line`]="{ value }">
              <span class="font-monospace">{{ value ?? "—" }}</span>
            </template>
          </v-data-table>
        </v-col>
      </v-row>
    </v-card-text>
  </v-card>
</template>

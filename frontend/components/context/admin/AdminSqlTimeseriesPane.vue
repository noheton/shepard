<script setup lang="ts">
import { AdminFragments } from "./adminMenuItems";
import type { SqlTimeseriesConfigPatch } from "~/composables/context/admin/useSqlTimeseriesConfig";
import { useSqlTimeseriesConfig } from "~/composables/context/admin/useSqlTimeseriesConfig";

const { config, isLoading, isSaving, error, refresh, patch } =
  useSqlTimeseriesConfig();

// ─── Edit dialog state ────────────────────────────────────────────────────────
const dialogOpen = ref(false);
const editMaxRows = ref<string>("");
const editMaxDuration = ref<string>("");
const saveError = ref<string | null>(null);

// Inline validation — maxRows must be a positive integer or blank (blank = revert to default)
const maxRowsError = computed(() => {
  const val = editMaxRows.value.trim();
  if (val === "") return null; // blank = null = revert to default
  const n = Number(val);
  if (!Number.isInteger(n) || n <= 0) {
    return "Must be a positive integer, or leave blank to use the deploy-time default.";
  }
  return null;
});

// Inline validation — maxDuration must be an ISO-8601 duration or blank
// e.g. PT60S, PT2M, PT1H30M  — Java Duration.parse format
const ISO_DURATION_RE = /^P(?:\d+Y)?(?:\d+M)?(?:\d+W)?(?:\d+D)?(?:T(?:\d+H)?(?:\d+M)?(?:\d+(?:\.\d+)?S)?)?$/;
const maxDurationError = computed(() => {
  const val = editMaxDuration.value.trim();
  if (val === "") return null; // blank = null = revert to default
  if (!ISO_DURATION_RE.test(val)) {
    return "Must be a valid ISO-8601 duration (e.g. PT60S, PT2M30S, PT1H), or leave blank to use the deploy-time default.";
  }
  return null;
});

const canSave = computed(
  () => maxRowsError.value === null && maxDurationError.value === null,
);

function openEdit() {
  if (!config.value) return;
  editMaxRows.value = String(config.value.maxRows);
  editMaxDuration.value = config.value.maxDuration;
  saveError.value = null;
  dialogOpen.value = true;
}

function cancelEdit() {
  dialogOpen.value = false;
  saveError.value = null;
}

async function save() {
  if (!canSave.value) return;
  saveError.value = null;

  const updates: SqlTimeseriesConfigPatch = {};

  const rawRows = editMaxRows.value.trim();
  if (rawRows === "") {
    updates.maxRows = null; // revert to deploy-time default
  } else {
    updates.maxRows = Number(rawRows);
  }

  const rawDur = editMaxDuration.value.trim();
  if (rawDur === "") {
    updates.maxDuration = null; // revert to deploy-time default
  } else {
    updates.maxDuration = rawDur;
  }

  const result = await patch(updates);
  if (result) {
    dialogOpen.value = false;
  } else {
    saveError.value = error.value ?? "Failed to save. Please try again.";
  }
}
</script>

<template>
  <div :id="AdminFragments.SQL_TIMESERIES" class="d-flex flex-column ga-4">
    <!-- Header row -->
    <div class="d-flex align-center justify-space-between flex-wrap ga-2">
      <div class="d-flex align-center ga-3">
        <h4 class="text-h4">SQL Timeseries</h4>
        <v-btn
          icon="mdi-refresh"
          variant="text"
          size="small"
          :loading="isLoading"
          @click="refresh"
        />
      </div>
      <v-btn
        variant="tonal"
        color="primary"
        prepend-icon="mdi-pencil-outline"
        :disabled="isLoading || !config"
        @click="openEdit"
      >
        Edit
      </v-btn>
    </div>

    <p class="text-body-2 text-medium-emphasis">
      Runtime caps for the <code>POST /v2/sql/timeseries</code> bulk-read endpoint.
      Changing these values takes effect immediately — no restart required.
      Set a field to blank to revert to the deploy-time default in
      <code>application.properties</code>.
    </p>

    <v-alert
      v-if="error && !dialogOpen"
      type="error"
      variant="tonal"
      closable
      @click:close="error = null"
    >
      {{ error }}
    </v-alert>

    <v-progress-linear v-if="isLoading && !config" indeterminate />

    <template v-if="config">
      <v-card variant="outlined">
        <v-card-title class="d-flex align-center ga-2 pt-4 pb-2">
          <v-icon color="primary">mdi-database-search-outline</v-icon>
          Query caps (runtime-mutable)
        </v-card-title>
        <v-card-text>
          <v-row dense>
            <v-col cols="12" sm="6">
              <div class="text-caption text-medium-emphasis">Max rows</div>
              <div class="text-h6">{{ config.maxRows.toLocaleString() }}</div>
              <div class="text-caption text-medium-emphasis">
                hard row cap per query; truncated responses include
                <code>x-shepard-truncated: true</code> trailer
              </div>
            </v-col>
            <v-col cols="12" sm="6">
              <div class="text-caption text-medium-emphasis">Max duration</div>
              <div class="text-h6">{{ config.maxDuration }}</div>
              <div class="text-caption text-medium-emphasis">
                ISO-8601 PostgreSQL statement timeout; queries exceeding this
                return HTTP 504
              </div>
            </v-col>
          </v-row>
        </v-card-text>
      </v-card>

      <div class="text-caption text-medium-emphasis">
        These caps guard the <code>POST /v2/sql/timeseries</code> endpoint
        (P10a–P10c). The endpoint must be separately enabled via the feature
        toggle <em>sql-timeseries</em> — it is enabled by default on P10c+
        instances. Changes are captured in the provenance audit trail.
      </div>
    </template>

    <!-- Edit dialog ─────────────────────────────────────────────────────────── -->
    <v-dialog v-model="dialogOpen" max-width="540">
      <v-card>
        <v-card-title class="text-h6 pa-4 d-flex align-center ga-2">
          <v-icon icon="mdi-database-search-outline" />
          Edit SQL Timeseries Caps
        </v-card-title>

        <v-card-text class="pa-4">
          <v-alert v-if="saveError" type="error" class="mb-4">
            {{ saveError }}
          </v-alert>

          <v-text-field
            v-model="editMaxRows"
            label="Max rows"
            :error-messages="maxRowsError ?? undefined"
            placeholder="1000000"
            hint="Positive integer. Leave blank to revert to the deploy-time default."
            persistent-hint
            variant="outlined"
            density="comfortable"
            class="mb-2"
            inputmode="numeric"
          />

          <v-text-field
            v-model="editMaxDuration"
            label="Max duration (ISO-8601)"
            :error-messages="maxDurationError ?? undefined"
            placeholder="PT60S"
            hint="e.g. PT60S, PT2M30S, PT1H. Leave blank to revert to the deploy-time default."
            persistent-hint
            variant="outlined"
            density="comfortable"
          />
        </v-card-text>

        <v-card-actions class="pa-4 pt-0">
          <v-spacer />
          <v-btn variant="text" :disabled="isSaving" @click="cancelEdit">
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            variant="tonal"
            :loading="isSaving"
            :disabled="!canSave"
            @click="save"
          >
            Save
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

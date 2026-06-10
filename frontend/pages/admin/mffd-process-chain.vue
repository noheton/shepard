<script setup lang="ts">
/**
 * /admin/mffd-process-chain — MFFD-MAPPING-REST-1 upload surface.
 *
 * Placeholder-grade admin page that lets an instance admin POST a
 * mapping YAML to {@code /v2/admin/mffd/process-chain-mapping}. Renders
 * the response counters + unresolved checklist so the admin can iterate
 * on the YAML offline + re-upload until everything resolves.
 *
 * Design: aidocs/integrations/118-mffd-process-chain-mapping.md
 * Backlog: MFFD-AF-TRACK-MAPPING-1, MFFD-MAPPING-REST-1.
 *
 * Admin gate mirrors `/admin` — non-admins see `UnauthorizedView`
 * after auth resolves; they are never silently navigated away.
 */

import { useStaleRoleSession } from "~/composables/context/useStaleRoleSession";

useHead({
  title: "MFFD process-chain mapping | shepard",
});

const exampleYamlHref = "/mffd-process-chain-mapping.example.yaml";

const { data, status } = useAuth();

const isInstanceAdmin = computed(() =>
  hasInstanceAdminRole(data.value?.accessToken),
);

const showUnauthorized = computed(
  () =>
    status.value !== "loading" &&
    data.value !== undefined &&
    !isInstanceAdmin.value,
);

const { reason: staleRoleReason } = useStaleRoleSession();

// ── form state ────────────────────────────────────────────────────────────
interface UnresolvedRow {
  line: number;
  side: string;
  reason: string;
}
interface MappingResult {
  schemaVersion: number;
  entries: number;
  matched: number;
  unmatched: number;
  edgesCreated: number;
  unresolved?: UnresolvedRow[];
  warnings?: string[];
}

const yamlText = ref<string>("");
const submitting = ref<boolean>(false);
const result = ref<MappingResult | null>(null);
const error = ref<string | null>(null);

async function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;
  yamlText.value = await file.text();
}

async function submit() {
  if (!yamlText.value.trim()) {
    error.value = "Paste a YAML payload or upload a file first.";
    return;
  }
  submitting.value = true;
  error.value = null;
  result.value = null;
  try {
    const response = await $fetch<MappingResult>(
      "/v2/admin/mffd/process-chain-mapping",
      {
        method: "POST",
        headers: { "Content-Type": "application/yaml" },
        body: yamlText.value,
      },
    );
    result.value = response;
  } catch (e: unknown) {
    const err = e as { data?: { detail?: string }; message?: string };
    error.value =
      err.data?.detail ?? err.message ?? "Request failed — see browser console.";
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <UnauthorizedView
    v-if="showUnauthorized"
    title="MFFD process-chain mapping is restricted"
    message="This section is only available to instance administrators."
    required-role="instance-admin"
    :stale-session-reason="staleRoleReason ?? undefined"
  />
  <v-container v-else class="py-6">
    <div class="d-flex align-start justify-space-between flex-wrap ga-3 mb-4">
      <div class="d-flex flex-column ga-1">
        <h4 class="text-h4">MFFD process-chain mapping</h4>
        <p class="text-body-1 text-medium-emphasis mb-0">
          Apply a YAML mapping file to materialise cross-process Predecessor
          edges — tapelaying → bridgewelding → NDT thermography → cleats — and
          surface the full MFFD digital thread in the provenance graph.
        </p>
      </div>
      <v-btn
        variant="outlined"
        size="small"
        prepend-icon="mdi-download-outline"
        :href="exampleYamlHref"
        download="mffd-process-chain-mapping.example.yaml"
      >
        Download example YAML
      </v-btn>
    </div>

    <v-alert
      type="info"
      variant="tonal"
      class="mb-6"
      icon="mdi-information-outline"
    >
      <div class="text-body-2">
        <p class="mb-2">
          This page uploads a YAML mapping file to
          <code>POST /v2/admin/mffd/process-chain-mapping</code>. The loader
          matches source and target DataObjects via their
          <code>urn:shepard:mffd:*</code> SemanticAnnotation predicates and
          MERGEs <code>has_successor</code> edges with the entry's
          <code>transitionKind</code>.
        </p>
        <p class="mb-0">
          Re-uploading the same YAML is safe — the merge is idempotent.
          Download the example above to see the schema, or read
          <a
            href="https://github.com/noheton/shepard/blob/main/aidocs/integrations/118-mffd-process-chain-mapping.md"
            target="_blank"
            rel="noopener"
          >aidocs/integrations/118</a>
          for the full selector → predicate mapping convention.
        </p>
      </div>
    </v-alert>

    <v-card class="mb-6">
      <v-card-title>Upload mapping YAML</v-card-title>
      <v-card-text>
        <v-file-input
          label="Upload YAML file"
          accept=".yaml,.yml,text/yaml,application/yaml"
          density="comfortable"
          prepend-icon="mdi-file-upload-outline"
          @change="onFileChange"
        />
        <v-textarea
          v-model="yamlText"
          label="Or paste YAML here"
          rows="12"
          variant="outlined"
          placeholder="schemaVersion: 1&#10;mappings:&#10;  - source: { process: afp-layup, ply_number: 5, track_number: 244 }&#10;    target: { process: bridge-welding, part_name: AF_3 }&#10;    transitionKind: normal"
          spellcheck="false"
        />
        <v-alert
          v-if="error"
          type="error"
          variant="tonal"
          class="mt-3"
          icon="mdi-alert-circle-outline"
        >
          {{ error }}
        </v-alert>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn
          color="primary"
          variant="elevated"
          :loading="submitting"
          :disabled="!yamlText.trim() || submitting"
          prepend-icon="mdi-upload"
          @click="submit"
        >
          Apply mapping
        </v-btn>
      </v-card-actions>
    </v-card>

    <v-card v-if="result" class="mb-6">
      <v-card-title>Result</v-card-title>
      <v-card-text>
        <v-row dense>
          <v-col cols="6" sm="3">
            <div class="text-caption text-medium-emphasis">Entries</div>
            <div class="text-h6">{{ result.entries }}</div>
          </v-col>
          <v-col cols="6" sm="3">
            <div class="text-caption text-medium-emphasis">Matched</div>
            <div class="text-h6">{{ result.matched }}</div>
          </v-col>
          <v-col cols="6" sm="3">
            <div class="text-caption text-medium-emphasis">Unmatched</div>
            <div class="text-h6">{{ result.unmatched }}</div>
          </v-col>
          <v-col cols="6" sm="3">
            <div class="text-caption text-medium-emphasis">Edges created</div>
            <div class="text-h6">{{ result.edgesCreated }}</div>
          </v-col>
        </v-row>

        <v-divider class="my-4" />

        <div v-if="result.warnings && result.warnings.length > 0">
          <div class="text-subtitle-2 mb-2">Warnings</div>
          <v-list density="compact">
            <v-list-item
              v-for="(warning, idx) in result.warnings"
              :key="`warning-${idx}`"
            >
              <template #prepend>
                <v-icon size="small" color="warning">
                  mdi-alert-outline
                </v-icon>
              </template>
              <v-list-item-title class="text-body-2">
                {{ warning }}
              </v-list-item-title>
            </v-list-item>
          </v-list>
        </div>

        <div
          v-if="result.unresolved && result.unresolved.length > 0"
          class="mt-3"
        >
          <div class="text-subtitle-2 mb-2">Unresolved checklist</div>
          <v-list density="compact">
            <v-list-item
              v-for="(row, idx) in result.unresolved"
              :key="`unresolved-${idx}`"
            >
              <template #prepend>
                <v-icon size="small" color="error">
                  mdi-circle-outline
                </v-icon>
              </template>
              <v-list-item-title class="text-body-2">
                line {{ row.line }} [{{ row.side }}]: {{ row.reason }}
              </v-list-item-title>
            </v-list-item>
          </v-list>
        </div>

        <v-alert
          v-if="(!result.unresolved || result.unresolved.length === 0) &&
                (!result.warnings || result.warnings.length === 0)"
          type="success"
          variant="tonal"
          class="mt-3"
          icon="mdi-check-circle-outline"
        >
          All entries resolved cleanly.
        </v-alert>
      </v-card-text>
    </v-card>
  </v-container>
</template>

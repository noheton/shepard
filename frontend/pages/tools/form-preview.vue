<script setup lang="ts">
/**
 * /tools/form-preview — PLACEHOLDER-form-preview slice 1.
 *
 * Real form descriptor viewer for {@code GET /v2/templates/{templateAppId}/form}
 * (doc 125 §5.1): pick a data-kind template by name or raw appId, see the
 * compiled form descriptor (groups, fields with DASH editor hints, server-computed
 * submit block).
 *
 * The real "Record a…" in-context entry point arrives with FORM-UX-ACTIONBUTTON
 * (SHAPES-APPLICABLE-FORMS), where this page is pre-populated from context.
 * Until then, the TemplateAutocomplete picker provides the zero-typing path.
 *
 * FORM-UX-ACTIONBUTTON — the "Record a …" entries in ActionMenuButton route
 * here with `?template=<templateAppId>&focusAppId=<entityAppId>` until the
 * full form pane ships. Prefill from the query so the in-context entry is
 * zero-typing (tools-in-context-first rule); the focus context is carried
 * for the future edit-form prefill (FORM-DESCRIPTOR-1 residue).
 */

import { useRoute } from "vue-router";
import { fetchTemplateForm, templateExcelExportPath } from "~/composables/useTemplateForm";
import type { FormDescriptor } from "~/composables/useTemplateForm";
import { groupDescriptorFields } from "~/utils/formPreview";
import type { GroupWithFields } from "~/utils/formPreview";

useHead({ title: "Form preview | shepard" });

// ---------------------------------------------------------------------------
// Route-query prefill (FORM-UX-ACTIONBUTTON in-context entry)
// ---------------------------------------------------------------------------
const route = useRoute();
const templateAppId = ref<string>(
  typeof route.query.template === "string" ? route.query.template : "",
);
const rawAppId = ref<string>("");

// Sync the TemplateAutocomplete selection with the raw field and vice versa.
// The autocomplete emits the appId; the raw field is a power-user override.
watch(rawAppId, (v) => {
  if (v.trim()) templateAppId.value = v.trim();
});

const focusAppId = computed(() =>
  typeof route.query.focusAppId === "string" ? route.query.focusAppId : null,
);

// ---------------------------------------------------------------------------
// Form descriptor fetch
// ---------------------------------------------------------------------------
const descriptor = ref<FormDescriptor | null>(null);
const loading = ref(false);
const fetchError = ref<string | null>(null);
const groups = computed<GroupWithFields[]>(() =>
  descriptor.value ? groupDescriptorFields(descriptor.value) : [],
);

async function loadDescriptor(appId: string) {
  const trimmed = appId.trim();
  if (!trimmed) {
    descriptor.value = null;
    fetchError.value = null;
    return;
  }
  loading.value = true;
  fetchError.value = null;
  try {
    descriptor.value = await fetchTemplateForm(trimmed);
  } catch (e: unknown) {
    descriptor.value = null;
    fetchError.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
}

watch(templateAppId, (v) => void loadDescriptor(v), { immediate: true });

// ---------------------------------------------------------------------------
// BTKVS-C1-EXCEL-EXPORT — shape-driven Excel download (doc 125 §6/D5)
// ---------------------------------------------------------------------------
const dataObjectAppId = ref<string>("");
const exporting = ref(false);
const exportError = ref<string | null>(null);

async function downloadExcel() {
  const tmpl = templateAppId.value.trim();
  const dataObject = dataObjectAppId.value.trim();
  if (!tmpl || !dataObject) return;
  exporting.value = true;
  exportError.value = null;
  try {
    const { data: auth } = useAuth();
    const config = useRuntimeConfig().public;
    const explicit = config.backendV2ApiUrl as string | undefined;
    const v2Base =
      explicit && explicit.length > 0
        ? explicit
        : (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
    const headers: Record<string, string> = {
      Accept: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    };
    if (auth.value?.accessToken) {
      headers["Authorization"] = `Bearer ${auth.value.accessToken}`;
    }
    const res = await fetch(v2Base + templateExcelExportPath(tmpl, dataObject), { headers });
    if (!res.ok) {
      exportError.value = `${res.status} ${res.statusText}`;
      return;
    }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `docket-${dataObject}.xlsx`;
    a.click();
    URL.revokeObjectURL(url);
  } catch (e: unknown) {
    exportError.value = e instanceof Error ? e.message : String(e);
  } finally {
    exporting.value = false;
  }
}
</script>

<template>
  <!-- UI-1920-FORM-PREVIEW-WIDTH: single-column prose + single-field form.
       Cap to a readable width, centred. No-op at <=1280. -->
  <v-container class="py-6" style="max-width: 1200px">
    <!-- Page header -->
    <div class="mb-6">
      <div class="d-flex align-center gap-3 mb-2">
        <v-icon icon="mdi-form-select" size="32" color="primary" />
        <h1 class="text-h4 font-weight-bold">Form preview</h1>
      </div>
      <p class="text-body-2 text-medium-emphasis">
        A form is the write-direction projection of a data-kind template's SHACL shape: the same
        shapeGraph the instantiation endpoint validates is compiled into a renderable descriptor.
        Submit-leg 422s carry <code>violations[]</code> keyed by field path (doc
        <a
          href="https://github.com/nucli-de/shepard/blob/main/aidocs/integrations/125-btkvs-shacl-form-templates.md"
          target="_blank"
          rel="noopener"
        >aidocs/integrations/125 §5.1</a>).
      </p>
    </div>

    <!-- Template picker card -->
    <v-card class="mb-6">
      <v-card-title>Template</v-card-title>
      <v-card-text>
        <TemplateAutocomplete
          v-model:app-id="templateAppId"
          kind="*"
          label="Template"
          data-testid="form-preview-template-autocomplete"
          class="mb-4"
        />
        <v-text-field
          v-model="rawAppId"
          label="Raw appId override (power-user fallback)"
          density="compact"
          variant="plain"
          prepend-inner-icon="mdi-identifier"
          placeholder="019e7243-f995-7914-be80-…"
          spellcheck="false"
          hint="Paste a template appId directly — overrides the picker above."
          persistent-hint
        />
        <v-chip
          v-if="focusAppId"
          size="small"
          variant="tonal"
          color="primary"
          prepend-icon="mdi-target"
          data-testid="form-preview-focus-chip"
          class="mt-2"
        >
          Focus: {{ focusAppId }}
        </v-chip>
      </v-card-text>
    </v-card>

    <!-- Loading indicator -->
    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <!-- Fetch error -->
    <v-alert
      v-if="fetchError"
      type="error"
      variant="tonal"
      class="mb-4"
      :text="`Failed to load form descriptor: ${fetchError}`"
    />

    <!-- Form descriptor: groups + fields -->
    <template v-if="descriptor">
      <v-card class="mb-4">
        <v-card-title>
          {{ descriptor.title }}
          <v-chip size="x-small" variant="tonal" class="ml-2">{{ descriptor.templateKind }}</v-chip>
        </v-card-title>
        <v-card-subtitle v-if="descriptor.shapeIri" class="text-caption font-italic">
          {{ descriptor.shapeIri }}
        </v-card-subtitle>
      </v-card>

      <v-expansion-panels multiple class="mb-6">
        <v-expansion-panel
          v-for="group in groups"
          :key="group.id"
          :value="group.id"
        >
          <v-expansion-panel-title>
            {{ group.label ?? group.id }}
            <v-chip size="x-small" variant="tonal" class="ml-2">
              {{ group.fields.length }} field{{ group.fields.length !== 1 ? "s" : "" }}
            </v-chip>
          </v-expansion-panel-title>
          <v-expansion-panel-text>
            <v-list density="compact">
              <v-list-item
                v-for="field in group.fields"
                :key="field.path"
                :title="field.label"
                :subtitle="field.description ?? field.path"
              >
                <template #append>
                  <div class="d-flex gap-1 flex-wrap justify-end">
                    <v-chip
                      size="x-small"
                      variant="outlined"
                      color="secondary"
                    >
                      {{ field.editor.replace(/^sh:|^dash:/, "") }}
                    </v-chip>
                    <v-chip
                      v-if="field.required"
                      size="x-small"
                      color="error"
                      variant="tonal"
                    >
                      required
                    </v-chip>
                  </div>
                </template>
              </v-list-item>
              <v-list-item v-if="group.fields.length === 0">
                <v-list-item-title class="text-medium-emphasis text-caption">
                  No fields in this group.
                </v-list-item-title>
              </v-list-item>
            </v-list>
          </v-expansion-panel-text>
        </v-expansion-panel>
      </v-expansion-panels>

      <!-- Submit contract -->
      <v-card class="mb-6">
        <v-card-title>Submit contract</v-card-title>
        <v-card-text>
          <v-chip
            size="small"
            variant="tonal"
            color="primary"
            class="mr-2 font-weight-bold"
          >
            {{ descriptor.submit.method }}
          </v-chip>
          <code class="text-caption">{{ descriptor.submit.href }}</code>
          <p
            v-if="descriptor.submit.violationContract"
            class="text-caption text-medium-emphasis mt-2"
          >
            Violation contract: <code>{{ descriptor.submit.violationContract }}</code>
          </p>
        </v-card-text>
      </v-card>
    </template>

    <!-- Excel export card (BTKVS-C1, kept as-is) -->
    <v-card class="mb-6">
      <v-card-title>Excel export (shape-driven)</v-card-title>
      <v-card-text>
        <p class="text-caption text-medium-emphasis mb-4">
          The same <code>urn:btkvs:cell-mapping</code> annotations that drive the form drive the
          workbook: the focused DataObject's attribute values land in the mapped cells
          (<code>GET /v2/templates/{appId}/export</code>, doc 125 §6).
        </p>
        <v-text-field
          v-model="dataObjectAppId"
          label="DataObject appId (the docket instance to export)"
          density="comfortable"
          variant="outlined"
          prepend-inner-icon="mdi-file-table-outline"
          placeholder="019e7243-f995-7914-be80-…"
          spellcheck="false"
        />
        <v-btn
          color="primary"
          prepend-icon="mdi-microsoft-excel"
          :disabled="!templateAppId.trim() || !dataObjectAppId.trim()"
          :loading="exporting"
          @click="downloadExcel"
        >
          Download Excel
        </v-btn>
        <div v-if="exportError" class="text-caption text-error mt-2">
          Export failed: {{ exportError }}
        </div>
      </v-card-text>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
/**
 * /tools/form-preview — BTKVS-B2 form-descriptor placeholder stub.
 *
 * Minimal surface for {@code GET /v2/templates/{templateAppId}/form} (doc 125
 * §5.1): enter a data-kind template appId, see the compiled form descriptor
 * (groups, fields with DASH editor hints, server-computed submit block).
 *
 * The real surface is FORM-UX-ACTIONBUTTON ("Record a…" groups on every
 * detail page via /v2/shapes/applicable) — gated on SHAPES-APPLICABLE-FORMS
 * per aidocs/16. This placeholder keeps the UI-stub rule satisfied until
 * that lands; the in-context entry per the tools-in-context-first rule
 * arrives with the ActionMenuButton.
 */

import { templateExcelExportPath, templateFormPath } from "~/composables/useTemplateForm";

useHead({ title: "Form preview | shepard" });

const templateAppId = ref<string>("");
const endpoint = computed(() =>
  templateAppId.value.trim() ? templateFormPath(templateAppId.value.trim()) : null,
);

// BTKVS-C1-EXCEL-EXPORT — shape-driven Excel download (doc 125 §6/D5): the
// same urn:btkvs:cell-mapping annotations that drive the form drive the
// generated workbook server-side.
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
  <v-container class="py-6">
    <PlaceholderPageHeader
      title="Form preview (shape → form descriptor)"
      subtitle="A form is the write-direction projection of a data-kind template's SHACL shape: the same shapeGraph the instantiation endpoint validates is compiled into a renderable descriptor. Submit-leg 422s carry violations[] keyed by field path."
      design-doc-href="https://github.com/nucli-de/shepard/blob/main/aidocs/integrations/125-btkvs-shacl-form-templates.md"
      design-doc-label="aidocs/integrations/125 §5.1"
    />

    <PlaceholderImplStatus
      backend="shipped"
      backlog-row="BTKVS-B2 / FORM-UX-ACTIONBUTTON"
      design-doc="aidocs/integrations/125"
      :endpoint="endpoint"
      notes="Backend descriptor compiler shipped (BTKVS-B2). Real form rendering arrives with FORM-UX-ACTIONBUTTON ('Record a…' on detail pages), gated on SHAPES-APPLICABLE-FORMS."
    />

    <v-card class="mb-6">
      <v-card-title>Template</v-card-title>
      <v-card-text>
        <v-text-field
          v-model="templateAppId"
          label="Data-kind template appId (DATAOBJECT_RECIPE / COLLECTION_RECIPE / STRUCTURED_RECIPE)"
          density="comfortable"
          variant="outlined"
          prepend-inner-icon="mdi-form-select"
          placeholder="019e7243-f995-7914-be80-…"
          spellcheck="false"
        />
      </v-card-text>
    </v-card>

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

    <PlaceholderRestDump
      :endpoint="endpoint"
      hint="Enter a template appId to fetch its compiled form descriptor."
    />
  </v-container>
</template>

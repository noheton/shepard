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

import { templateFormPath } from "~/composables/useTemplateForm";

useHead({ title: "Form preview | shepard" });

const templateAppId = ref<string>("");
const endpoint = computed(() =>
  templateAppId.value.trim() ? templateFormPath(templateAppId.value.trim()) : null,
);
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

    <PlaceholderRestDump
      :endpoint="endpoint"
      hint="Enter a template appId to fetch its compiled form descriptor."
    />
  </v-container>
</template>

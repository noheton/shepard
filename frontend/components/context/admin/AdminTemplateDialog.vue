<script setup lang="ts">
import {
  ShepardTemplateApi,
  type ShepardTemplateIO,
  type CreateShepardTemplateIO,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

const props = defineProps<{
  modelValue: boolean;
  /** When set, we are editing an existing template (PATCH). Omit for create (POST). */
  template?: ShepardTemplateIO | null;
  /**
   * The full list of templates (used to scope the parent picker to same-kind,
   * non-retired templates and to exclude self + descendants — cycle prevention
   * in the UI). Design: aidocs/integrations/123.
   */
  allTemplates?: ShepardTemplateIO[];
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "saved"): void;
}>();

const TEMPLATE_KINDS = [
  { title: "DataObject Recipe", value: "DATAOBJECT_RECIPE" },
  { title: "Collection Recipe", value: "COLLECTION_RECIPE" },
  { title: "Experiment Recipe", value: "EXPERIMENT_RECIPE" },
];

const isSaving = ref(false);
const saveError = ref<string | null>(null);

// Form fields
const name = ref("");
const templateKind = ref("DATAOBJECT_RECIPE");
const description = ref<string>("");
const tags = ref<string[]>([]);
const body = ref("{}");
// TEMPLATE-ICONS-2-FE — admin-settable MDI name. Empty string clears.
const iconKey = ref<string>("");
// TPL-INHERIT — appId of the parent template this template extends (null = root).
const parentTemplateAppId = ref<string | null>(null);
// The flattened (inherited+own) body, fetched when a parent is selected.
const inheritedBody = ref<string | null>(null);
const inheritedLoading = ref(false);

const isEdit = computed(() => !!props.template);
const dialogTitle = computed(() =>
  isEdit.value ? "Edit Template (creates new version)" : "New Template",
);

/**
 * Compute the appIds that must NOT be selectable as a parent: self and every
 * descendant of self (selecting one would create a cycle). Walks child→parent
 * edges over allTemplates. Design: aidocs/integrations/123 §4.
 */
const forbiddenParentAppIds = computed<Set<string>>(() => {
  const forbidden = new Set<string>();
  const selfAppId = props.template?.appId;
  if (!selfAppId) return forbidden;
  forbidden.add(selfAppId);
  const all = props.allTemplates ?? [];
  // Repeatedly add any template whose parent is already forbidden (its descendants).
  let changed = true;
  while (changed) {
    changed = false;
    for (const t of all) {
      if (
        t.parentTemplateAppId &&
        forbidden.has(t.parentTemplateAppId) &&
        !forbidden.has(t.appId)
      ) {
        forbidden.add(t.appId);
        changed = true;
      }
    }
  }
  return forbidden;
});

/** Candidate parents: same-kind, non-retired, not self/descendant. */
const parentCandidates = computed(() => {
  const all = props.allTemplates ?? [];
  return all
    .filter(
      (t) =>
        t.templateKind === templateKind.value &&
        !t.retired &&
        !forbiddenParentAppIds.value.has(t.appId),
    )
    .map((t) => ({
      title: `${t.name} (v${t.version})`,
      value: t.appId,
    }));
});

/** Pretty-printed inherited field set for the read-only preview. */
const inheritedPretty = computed(() => {
  if (!inheritedBody.value) return "";
  try {
    return JSON.stringify(JSON.parse(inheritedBody.value), null, 2);
  } catch {
    return inheritedBody.value;
  }
});

// Reset and populate fields whenever dialog opens or template prop changes
watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      saveError.value = null;
      inheritedBody.value = null;
      if (props.template) {
        name.value = props.template.name;
        templateKind.value = props.template.templateKind;
        description.value = props.template.description ?? "";
        tags.value = props.template.tags ? [...props.template.tags] : [];
        body.value = props.template.body;
        iconKey.value = props.template.iconKey ?? "";
        parentTemplateAppId.value = props.template.parentTemplateAppId ?? null;
        if (parentTemplateAppId.value) void refreshInherited();
      } else {
        name.value = "";
        templateKind.value = "DATAOBJECT_RECIPE";
        description.value = "";
        tags.value = [];
        body.value = "{}";
        iconKey.value = "";
        parentTemplateAppId.value = null;
      }
    }
  },
);

// When the parent changes, refresh the inherited-fields preview.
watch(parentTemplateAppId, (v) => {
  if (v) void refreshInherited();
  else inheritedBody.value = null;
});

/**
 * Fetch the flattened (inherited) body of the SELECTED PARENT so the editor can
 * show the admin what fields the child will inherit. Visible in both basic and
 * advanced mode (advanced is a strict superset). Design: aidocs/integrations/123.
 */
async function refreshInherited() {
  if (!parentTemplateAppId.value) {
    inheritedBody.value = null;
    return;
  }
  inheritedLoading.value = true;
  try {
    const api = useV2ShepardApi(ShepardTemplateApi).value;
    const flattenedParent = await api.getTemplate({
      appId: parentTemplateAppId.value,
      flatten: true,
    });
    inheritedBody.value = flattenedParent.body;
  } catch (error) {
    inheritedBody.value = null;
    handleError(error, "fetching inherited template fields");
  } finally {
    inheritedLoading.value = false;
  }
}

function close() {
  emit("update:modelValue", false);
}

async function save() {
  saveError.value = null;
  isSaving.value = true;
  try {
    const api = useV2ShepardApi(ShepardTemplateApi).value;
    const payload: CreateShepardTemplateIO = {
      name: name.value.trim(),
      templateKind: templateKind.value,
      body: body.value.trim(),
      description: description.value.trim() || null,
      tags: tags.value.length > 0 ? [...tags.value] : null,
      // TEMPLATE-ICONS-2-FE — empty string clears (server resets to null).
      iconKey: iconKey.value.trim(),
      // TPL-INHERIT — empty string clears the parent (template becomes a root).
      parentTemplateAppId: parentTemplateAppId.value ?? "",
    };

    if (isEdit.value && props.template) {
      await api.patchTemplate({
        appId: props.template.appId,
        patchShepardTemplateIO: payload,
      });
    } else {
      await api.createTemplate({ createShepardTemplateIO: payload });
    }

    emit("saved");
    close();
  } catch (error: unknown) {
    const msg = (error as { message?: string })?.message ?? "Unknown error";
    saveError.value = `Failed to save template: ${msg}`;
    handleError(error, "saving template");
  } finally {
    isSaving.value = false;
  }
}
</script>

<template>
  <v-dialog
    :model-value="modelValue"
    max-width="700"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <v-card>
      <v-card-title class="text-h6 pa-4">{{ dialogTitle }}</v-card-title>

      <v-divider />

      <v-card-text class="pa-4">
        <v-alert
          v-if="saveError"
          type="error"
          closable
          class="mb-4"
          @click:close="saveError = null"
        >
          {{ saveError }}
        </v-alert>

        <v-row dense>
          <v-col cols="12" sm="7">
            <v-text-field
              v-model="name"
              label="Name"
              required
              :rules="[(v) => !!v || 'Name is required']"
              variant="outlined"
              density="compact"
            />
          </v-col>

          <v-col cols="12" sm="5">
            <v-select
              v-model="templateKind"
              label="Kind"
              :items="TEMPLATE_KINDS"
              item-title="title"
              item-value="value"
              required
              :disabled="isEdit"
              :hint="isEdit ? 'Kind cannot be changed (copy-on-write)' : ''"
              persistent-hint
              variant="outlined"
              density="compact"
            />
          </v-col>

          <!-- TPL-INHERIT — parent-template picker (appId-keyed, cycle-safe). -->
          <v-col cols="12">
            <v-autocomplete
              v-model="parentTemplateAppId"
              label="Extends (parent template, optional)"
              :items="parentCandidates"
              item-title="title"
              item-value="value"
              clearable
              variant="outlined"
              density="compact"
              prepend-inner-icon="mdi-file-tree"
              :hint="
                parentTemplateAppId
                  ? 'This template inherits the parent\'s fields; your own fields override on collision.'
                  : 'Leave empty for a root template. Only same-kind, non-cyclic templates are listed.'
              "
              persistent-hint
              data-test="template-parent-picker"
            />
          </v-col>

          <!-- TPL-INHERIT — inherited fields, read-only, distinct from own fields. -->
          <v-col v-if="parentTemplateAppId" cols="12">
            <v-card variant="tonal" color="primary" class="pa-3">
              <div class="d-flex align-center mb-1">
                <v-icon icon="mdi-file-tree" size="small" class="mr-2" />
                <span class="text-caption font-weight-medium">
                  Inherited fields (read-only — from parent, overridable by your Body)
                </span>
                <v-progress-circular
                  v-if="inheritedLoading"
                  indeterminate
                  size="16"
                  width="2"
                  class="ml-2"
                />
              </div>
              <v-textarea
                :model-value="inheritedPretty"
                readonly
                rows="5"
                auto-grow
                variant="outlined"
                density="compact"
                font-family="monospace"
                hide-details
                data-test="template-inherited-preview"
              />
            </v-card>
          </v-col>

          <v-col cols="12">
            <v-textarea
              v-model="description"
              label="Description (optional)"
              rows="2"
              auto-grow
              variant="outlined"
              density="compact"
            />
          </v-col>

          <v-col cols="12" sm="6">
            <v-text-field
              v-model="iconKey"
              label="Icon (MDI name, optional)"
              variant="outlined"
              density="compact"
              prepend-inner-icon="mdi-shape-outline"
              :hint="
                iconKey.trim()
                  ? `Preview shown to the right. Leave empty for per-kind default.`
                  : `e.g. mdi-layers. Leave empty for per-kind default. Browse names at materialdesignicons.com.`
              "
              persistent-hint
              clearable
              data-test="template-icon-key-field"
            />
          </v-col>

          <v-col cols="12" sm="6" class="d-flex align-center justify-center">
            <v-card variant="outlined" class="pa-3 d-flex flex-column align-center" min-width="80">
              <span class="text-caption text-medium-emphasis">Preview</span>
              <v-icon
                :icon="iconKey.trim() || 'mdi-circle-medium'"
                size="x-large"
                class="mt-1"
                data-test="template-icon-preview"
              />
              <span class="text-caption text-medium-emphasis mt-1">
                {{ iconKey.trim() || "(default)" }}
              </span>
            </v-card>
          </v-col>

          <v-col cols="12">
            <v-combobox
              v-model="tags"
              label="Tags (optional)"
              multiple
              chips
              closable-chips
              variant="outlined"
              density="compact"
              hint="Type and press Enter to add a tag"
              persistent-hint
            />
          </v-col>

          <v-col cols="12">
            <v-textarea
              v-model="body"
              label="Body (JSON DSL — your own fields; override inherited keys here)"
              required
              :rules="[(v) => !!v || 'Body is required']"
              rows="8"
              variant="outlined"
              density="compact"
              font-family="monospace"
              hint="JSON DSL per aidocs/54 §7. Fields here override the inherited fields above."
              persistent-hint
            />
          </v-col>
        </v-row>
      </v-card-text>

      <v-divider />

      <v-card-actions class="pa-4">
        <v-spacer />
        <v-btn variant="text" :disabled="isSaving" @click="close">Cancel</v-btn>
        <v-btn
          color="primary"
          variant="tonal"
          :loading="isSaving"
          :disabled="!name.trim() || !body.trim()"
          @click="save"
        >
          {{ isEdit ? "Save (new version)" : "Create" }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<style scoped lang="scss"></style>

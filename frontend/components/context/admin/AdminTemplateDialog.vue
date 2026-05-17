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

const isEdit = computed(() => !!props.template);
const dialogTitle = computed(() =>
  isEdit.value ? "Edit Template (creates new version)" : "New Template",
);

// Reset and populate fields whenever dialog opens or template prop changes
watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      saveError.value = null;
      if (props.template) {
        name.value = props.template.name;
        templateKind.value = props.template.templateKind;
        description.value = props.template.description ?? "";
        tags.value = props.template.tags ? [...props.template.tags] : [];
        body.value = props.template.body;
      } else {
        name.value = "";
        templateKind.value = "DATAOBJECT_RECIPE";
        description.value = "";
        tags.value = [];
        body.value = "{}";
      }
    }
  },
);

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
              label="Body (JSON DSL)"
              required
              :rules="[(v) => !!v || 'Body is required']"
              rows="8"
              variant="outlined"
              density="compact"
              font-family="monospace"
              hint="JSON DSL per aidocs/54 §7"
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

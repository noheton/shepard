<script setup lang="ts">
import { StructuredDataContainerApi } from "@dlr-shepard/backend-client";
import JsonEditor from "~/components/common/editor/JsonEditor.vue";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

interface StructuredDataViewerDialogProps {
  structuredDataPayload: string;
  isEditable?: boolean;
  structuredDataContainerId?: number;
  structuredDataName?: string;
}

const props = withDefaults(defineProps<StructuredDataViewerDialogProps>(), {
  isEditable: false,
});

const emit = defineEmits<{
  saved: [];
}>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const prettyFormattedPayload = computed(() => {
  try {
    return JSON.stringify(JSON.parse(props.structuredDataPayload), null, 2);
  } catch {
    return props.structuredDataPayload;
  }
});

const editedJson = ref<string>(prettyFormattedPayload.value);
const isSaving = ref(false);
const saveError = ref<string | null>(null);

watch(
  () => props.structuredDataPayload,
  val => {
    try {
      editedJson.value = JSON.stringify(JSON.parse(val), null, 2);
    } catch {
      editedJson.value = val;
    }
  },
);

async function save() {
  if (!props.structuredDataContainerId) return;
  saveError.value = null;
  isSaving.value = true;
  try {
    let payload: string;
    try {
      payload = JSON.stringify(JSON.parse(editedJson.value));
    } catch {
      saveError.value = "Invalid JSON — fix syntax errors before saving.";
      isSaving.value = false;
      return;
    }
    await useShepardApi(StructuredDataContainerApi).value.createStructuredData({
      structuredDataContainerId: props.structuredDataContainerId,
      structuredDataPayload: {
        structuredData: { name: props.structuredDataName ?? "default" },
        payload,
      },
    });
    emit("saved");
    showDialog.value = false;
  } catch (err) {
    handleError(err, "createStructuredData");
    saveError.value = "Save failed — see console for details.";
  } finally {
    isSaving.value = false;
  }
}
</script>

<template>
  <InformationDialog
    v-model:show-dialog="showDialog"
    :max-width="1000"
    :title="isEditable ? 'Edit Structured Data Payload' : 'Structured Data Payload'"
  >
    <template #text>
      <JsonEditor
        v-if="isEditable"
        v-model:json="editedJson"
        style="min-height: 320px"
      />
      <RichTextEditor
        v-else
        :model-value="prettyFormattedPayload"
        :is-editable="false"
        code-type="json"
      />
      <v-alert
        v-if="saveError"
        class="mt-2"
        density="compact"
        type="error"
        :text="saveError"
      />
    </template>
    <template v-if="isEditable" #actions>
      <v-spacer />
      <v-btn variant="text" @click="showDialog = false">Cancel</v-btn>
      <v-btn
        color="primary"
        variant="flat"
        :loading="isSaving"
        @click="save"
      >
        Save as new version
      </v-btn>
    </template>
  </InformationDialog>
</template>

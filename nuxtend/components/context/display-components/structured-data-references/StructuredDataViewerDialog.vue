<script setup lang="ts">
import InformationDialog from "~/components/common/dialog/InformationDialog.vue";

interface StructuredDataViewerDialogProps {
  structuredDataPayload: string;
}
const props = defineProps<StructuredDataViewerDialogProps>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const prettyFormattedPayload = computed(() =>
  JSON.stringify(JSON.parse(props.structuredDataPayload), null, 2),
);
</script>

<template>
  <InformationDialog
    v-model:show-dialog="showDialog"
    :max-width="1000"
    title="Structured Data Payload"
  >
    <template #text>
      <RichTextEditor
        :model-value="prettyFormattedPayload"
        :is-editable="false"
        code-type="json"
      />
    </template>
  </InformationDialog>
</template>

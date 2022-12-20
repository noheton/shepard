<script setup lang="ts">
const props = defineProps({
  modalId: {
    type: String,
    default: "deleteConfirmationModal",
  },
  modalName: {
    type: String,
    default: "deleteConfirmationModal",
  },
  modalText: {
    type: String,
    default: "Do you really want to delete?",
  },
});
</script>

<template>
  <b-modal
    :id="props.modalId"
    ref="modal"
    size="s"
    :title="props.modalName"
    @ok="$emit('confirmation')"
  >
    {{ props.modalText }}
  </b-modal>
</template>

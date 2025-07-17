<script setup lang="ts">
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const emit = defineEmits(["confirmed"]);

const confirm = () => {
  showDialog.value = false;
  emit("confirmed");
};
</script>

<template>
  <v-dialog
    v-model="showDialog"
    max-width="475"
    persistent
    scrollable
    @keydown.esc="showDialog = false"
    @keydown.enter="confirm"
  >
    <v-card class="pa-0 bg-canvas">
      <v-card-text>
        <div class="text-h4 text-textbody1">
          Are you sure you want change the owner of this group?
        </div>
        <div class="text-body-1 text-textbody1 mt-6">
          A group always has one owner. You will loose your owner status if you
          change the owner.
        </div>
      </v-card-text>
      <template #actions>
        <v-btn variant="flat" color="treeview" @click="showDialog = false">
          Cancel
        </v-btn>

        <v-btn variant="flat" color="error" @click="confirm">Proceed</v-btn>
      </template>
    </v-card>
  </v-dialog>
</template>

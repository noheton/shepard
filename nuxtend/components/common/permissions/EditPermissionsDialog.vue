<script setup lang="ts">
interface EditPermissionsDialogProps {
  collectionId: number;
}

const _props = defineProps<EditPermissionsDialogProps>();

const isValid = ref(true);
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const saveChanges = () => {
  showDialog.value = false;
};
</script>

<template>
  <Dialog
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    title="Edit Permissions"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form>
      <v-form ref="form" v-model="isValid" validate-on="invalid-input eager">
        <v-row class="pt-8">
          <v-col>
            Edit permissions of collection {{ collectionId }} here...
          </v-col>
        </v-row>
      </v-form>
    </template>
  </Dialog>
</template>

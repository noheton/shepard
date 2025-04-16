<script lang="ts" setup>
import type { ShepardObjectAccessor } from "~/composables/shepardObjectAccessor";

const showDialog = ref(false);
const emit = defineEmits(["update-success"]);

defineProps<{ shepardObjectAccessor: ShepardObjectAccessor }>();
</script>

<template>
  <v-btn
    class="mx-2"
    color="treeview"
    icon
    rounded="lg"
    size="small"
    variant="flat"
    @click="showDialog = true"
  >
    <v-icon>mdi-account-cog-outline</v-icon>
    <EditPermissionsDialog
      v-if="showDialog"
      v-model:show-dialog="showDialog"
      :shepard-object-accessor="shepardObjectAccessor"
      @update-success="emit('update-success')"
    />
  </v-btn>
</template>

<style lang="scss" scoped></style>

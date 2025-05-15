<script lang="ts" setup>
import {
  type Collection,
  CollectionApi,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

interface CollectionDeleteDialogProps {
  collection: Collection;
}

const props = defineProps<CollectionDeleteDialogProps>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const router = useRouter();

async function deleteCollection() {
  try {
    await useShepardApi(CollectionApi).value.deleteCollection({
      collectionId: props.collection.id,
    });

    showDialog.value = false;
    emitSuccess(`Successfully deleted collection "${props.collection.name}"`);
    await router.push(collectionsPath);
  } catch (error) {
    handleError(error as ResponseError, "deleteCollection");
  }
}
</script>

<template>
  <ConfirmSafeDeleteDialog
    v-model:show-dialog="showDialog"
    :target-name="props.collection.name"
    entity-type="collection"
    @confirmed="deleteCollection"
  />
</template>

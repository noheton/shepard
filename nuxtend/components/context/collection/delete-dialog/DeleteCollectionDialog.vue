<script setup lang="ts">
import {
  type Collection,
  CollectionApi,
  type ResponseError,
} from "@dlr-shepard/backend-client";

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
    await createApiInstance(CollectionApi).deleteCollection({
      collectionId: props.collection.id,
    });

    showDialog.value = false;
    emitSuccess(`Successfully deleted collection "${props.collection.name}"`);
    router.push(collectionsPath);
  } catch (error) {
    handleError(error as ResponseError, "deleteCollection");
  }
}
</script>

<template>
  <ConfirmSafeDeleteDialog
    v-model:show-dialog="showDialog"
    title="Are you sure you want to delete this collection?"
    prompt-text="Deleting this collection is not reversible. To reassure that you do this intentionally, please type in the collection name:"
    :target-name="props.collection.name"
    @confirmed="deleteCollection"
  />
</template>

<script lang="ts" setup>
import RichTextEditor from "~/components/common/editor/RichTextEditor.vue";
import type { Editor } from "@tiptap/vue-3";
import type { ShepardFile } from "@dlr-shepard/backend-client";
import { CollectionAccessor } from "~/composables/context/CollectionAccessor";
import { valsToNeo4jImg } from "~/composables/labJournalHelper";
import { handleError } from "~/utils/errorBus";

const journalContent = defineModel<string>("journalContent", {
  required: true,
  default: "",
});

const props = defineProps<{
  collectionId: number;
  dataObjectId: number;
  isEditing: boolean;
  isExpanded?: boolean;
}>();

const collectionAccessor = new CollectionAccessor(props.collectionId);
collectionAccessor.fetchData();

function handleUploadedImages(files: ShepardFile[], filecontainerId: number) {
  for (const file of files) {
    handleUploadedImage(file, filecontainerId);
  }
}

async function handleUploadedImage(file: ShepardFile, filecontainerId: number) {
  // todo: only allow images
  if (!collectionAccessor.collection.value?.defaultFileContainerId) {
    try {
      await collectionAccessor.fetchData();
    } catch (error) {
      handleError(error, "while uploading image");
    }
  }
  editor.value!.commands.insertContent(
    valsToNeo4jImg(filecontainerId, file.oid!, file.filename!),
  );
}

const editor = shallowRef<Editor>();
const showUploadFileDialog = ref(false);

const emit = defineEmits<{
  (e: "editor-created", value: Editor): void;
}>();

function handleEditorCreated(ed: Editor) {
  editor.value = ed;
  emit("editor-created", ed);
}
</script>

<template>
  <DataObjectFileUploadDialog
    v-if="showUploadFileDialog"
    v-model:show-dialog="showUploadFileDialog"
    :accept="'image/*'"
    :collection-id="collectionId"
    :create-reference="false"
    :dataobject-id="dataObjectId"
    @files-uploaded="handleUploadedImages"
  />
  <div class="mx-4">
    <RichTextEditor
      v-model="journalContent"
      :autofocus="true"
      :can-add-image="true"
      :initial-content="journalContent"
      :is-editable="isEditing"
      :is-preview-collapsed="!isExpanded"
      class="pr-2 pl-4"
      @add-image="showUploadFileDialog = true"
      @editor-created="handleEditorCreated"
    />
  </div>
</template>

<style lang="scss" scoped></style>

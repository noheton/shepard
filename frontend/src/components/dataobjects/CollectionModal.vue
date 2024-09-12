<script setup lang="ts">
import TextEditor from "@/components/generic/TextEditor.vue";
import CollectionService from "@/services/collectionService";
import { handleError } from "@/utils/error-handling";
import { permissionOptions as pOptions } from "@/utils/helpers";
import {
  PermissionType,
  ResponseError,
  type Collection,
} from "@dlr-shepard/backend-client";
import { ref, type PropType } from "vue";
import { useRouter } from "vue2-helpers/vue-router";

const props = defineProps({
  modalId: {
    type: String,
    default: "collectionModal",
  },
  modalName: {
    type: String,
    default: "collectionModal",
  },
  currentCollection: {
    type: Object as PropType<Collection | undefined>,
    default: undefined,
  },
});
const permissionOptions = pOptions;

const emit = defineEmits(["collection-changed"]);

const router = useRouter();
const name = ref<string>("");
const description = ref<string>("");
const possibleAttributes = ref<{ key: string; value: string }[]>([]);
const validationError = ref(false);

const newPermissionType = ref<PermissionType>(PermissionType.Private);

function prepare() {
  name.value = props.currentCollection?.name || "";
  description.value = props.currentCollection?.description || "";
  possibleAttributes.value = [];
  validationError.value = false;

  if (props.currentCollection?.attributes) {
    Object.entries(props.currentCollection?.attributes).forEach(
      ([key, value]) => {
        possibleAttributes.value.push({ key: key, value: value });
      },
    );
  }
  if (possibleAttributes.value.length == 0) {
    possibleAttributes.value.push({
      key: "",
      value: "",
    });
  }
}

function handleOk() {
  const newCollection: Collection = {
    id: props.currentCollection?.id,
    name: name.value,
    description: description.value,
  };

  const attributes: { [key: string]: string } = {};
  possibleAttributes.value.forEach(attr => {
    if (attr.key != "") {
      attributes[attr.key] = attr.value;
    }
  });
  newCollection.attributes = attributes;

  if (props.currentCollection) {
    updateCollection(newCollection);
  } else {
    createCollection(newCollection);
  }
}

function createCollection(collection: Collection) {
  CollectionService.createCollection({
    collection: collection,
  })
    .then(async response => {
      if (response.id) {
        const perms = await CollectionService.getCollectionPermissions({
          collectionId: response.id,
        });
        perms.permissionType = newPermissionType.value;
        await CollectionService.editCollectionPermissions({
          collectionId: response.id,
          permissions: perms,
        });
        router.push({
          name: "Collection",
          params: {
            collectionId: String(response.id),
          },
        });
      }
    })
    .catch(e => {
      handleError(e as ResponseError, "creating collection");
    });
}

function updateCollection(collection: Collection) {
  if (!collection.id) return;
  CollectionService.updateCollection({
    collectionId: collection.id,
    collection: collection,
  })
    .then(() => {
      emit("collection-changed");
    })
    .catch(e => {
      handleError(e as ResponseError, "updating collection");
    });
}
</script>

<template>
  <b-modal
    :id="modalId"
    ref="modal"
    size="lg"
    :title="modalName"
    lazy
    @show="prepare()"
    @ok="handleOk()"
  >
    <b-form-group>
      <b-container>
        <b-row class="mb-3">
          <b-col cols="2"> Name </b-col>
          <b-col cols="8">
            <b-form-input v-model="name" required placeholder="Name">
            </b-form-input>
          </b-col>
        </b-row>

        <b-row class="mb-3">
          <b-col cols="2"> Description </b-col>
          <b-col cols="8">
            <TextEditor v-model="description"></TextEditor>
          </b-col>
        </b-row>

        <b-row v-if="currentCollection == undefined" class="mb-3">
          <b-col cols="2"> Permission </b-col>
          <b-col cols="8">
            <b-form-select
              v-model="newPermissionType"
              class="mb-3"
              :options="permissionOptions"
            ></b-form-select>
          </b-col>
        </b-row>

        <p>Attributes</p>
        <div class="mt-3">
          <b-form-group
            v-for="(attribute, i) in possibleAttributes"
            :key="i"
            class="m-0"
          >
            <b-row class="mb-1">
              <b-col cols="2">{{ i }}</b-col>
              <b-col cols="3">
                <b-form-input
                  v-model="attribute.key"
                  placeholder="key"
                ></b-form-input>
              </b-col>

              <b-col cols="5">
                <b-form-input
                  v-model="attribute.value"
                  placeholder="value"
                ></b-form-input>
              </b-col>

              <b-col cols="1">
                <b-button
                  v-show="i == possibleAttributes.length - 1"
                  variant="success"
                  @click="
                    possibleAttributes.push({
                      key: '',
                      value: '',
                    })
                  "
                >
                  <CreateIcon />
                </b-button>
              </b-col>
              <b-col cols="1">
                <b-button
                  v-show="i || (!i && possibleAttributes.length > 1)"
                  variant="danger"
                  @click="possibleAttributes.splice(i, 1)"
                >
                  <RemoveIcon />
                </b-button>
              </b-col>
            </b-row>
          </b-form-group>
        </div>
      </b-container>
    </b-form-group>
  </b-modal>
</template>

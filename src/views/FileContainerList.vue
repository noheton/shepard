<script setup lang="ts">
import FilterListLine, {
  type FilterChangedData,
} from "@/components/generic/FilterListLine.vue";
import GenericCreateModal from "@/components/generic/GenericCreateModal.vue";
import GenericEntityList from "@/components/generic/GenericEntityList.vue";
import FileService from "@/services/fileService";
import { handleError } from "@/utils/error-handling";
import { getTotalRows } from "@/utils/helpers";
import type {
  FileContainer,
  GetAllFileContainersOrderByEnum,
  PermissionsPermissionTypeEnum,
  ResponseError,
} from "@dlr-shepard/shepard-client";
import { useTitle } from "@vueuse/core";
import { BButton, BButtonGroup, BPagination } from "bootstrap-vue";
import { computed, onMounted, ref, type ComputedRef } from "vue";
import { useRouter } from "vue2-helpers/vue-router";

const router = useRouter();
const containers = ref<FileContainer[] | undefined>();
const perPage = ref(10);
const currentPage = ref(1);
const orderBy = ref("createdAt");
const descending = ref(false);

onMounted(() => {
  retrieveContainers();
  useTitle("File Containers | shepard");
});

const totalRows: ComputedRef<number> = computed(() => {
  if (containers.value)
    return getTotalRows(
      containers.value.length,
      perPage.value,
      currentPage.value,
    );
  else return 0;
});

function filterChanged(options: FilterChangedData) {
  currentPage.value = options.currentPage;
  perPage.value = options.currentSize;
  descending.value = options.descending;
  orderBy.value = options.orderBy;
  retrieveContainers();
}

function retrieveContainers(page?: number) {
  const nextPage = page || currentPage.value;
  const nextOrderBy =
    orderBy.value as keyof typeof GetAllFileContainersOrderByEnum as GetAllFileContainersOrderByEnum;
  FileService.getAllFileContainers({
    size: perPage.value,
    page: nextPage - 1,
    orderBy: nextOrderBy,
    orderDesc: descending.value,
  })
    .then(response => {
      containers.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching file containers");
    });
}

function createContainer(options: {
  name: string;
  perms: PermissionsPermissionTypeEnum;
}) {
  FileService.createFileContainer({
    fileContainer: { name: options.name } as FileContainer,
  })
    .then(async response => {
      if (response.id) {
        const perms = await FileService.getFilePermissions({
          fileContainerId: response.id,
        });
        perms.permissionType = options.perms;
        await FileService.editFilePermissions({
          fileContainerId: response.id,
          permissions: perms,
        });
        router.push({
          name: "Files",
          params: {
            fileId: String(response.id),
          },
        });
      }
    })
    .catch(e => {
      handleError(e as ResponseError, "creating file container");
    });
}
</script>

<template>
  <div class="file-container-list">
    <div class="component">
      <b-button-group class="float-right">
        <b-button
          v-b-modal.create-file-container-modal
          v-b-tooltip.hover
          title="Create File Container"
          variant="primary"
        >
          <CreateIcon />
        </b-button>
      </b-button-group>
      <h4>File Containers</h4>
      <br />

      <FilterListLine
        :max-objects="totalRows"
        :default-page="currentPage"
        :default-size="perPage"
        :default-descending="descending"
        :default-order-by="orderBy"
        @filter-changed="filterChanged($event)"
      />
      <GenericEntityList :entities="containers" />
      <GenericCreateModal
        modal-id="create-file-container-modal"
        modal-name="Create File Container"
        @create="createContainer($event)"
      />
      <b-pagination
        v-model="currentPage"
        :total-rows="totalRows"
        :per-page="perPage"
        align="center"
        size="sm"
        @change="retrieveContainers($event)"
      ></b-pagination>
    </div>
  </div>
</template>

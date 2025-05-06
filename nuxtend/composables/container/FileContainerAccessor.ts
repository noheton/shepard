import { handleContainerUpdate } from "#imports";
import {
  FileContainerApi,
  type FileContainer,
  type Permissions,
  type ResponseError,
  type ShepardFile,
} from "@dlr-shepard/backend-client";
import { ContainerAccessor } from "../shepardObjectAccessor";

export class FileContainerAccessor extends ContainerAccessor {
  api = createApiInstance(FileContainerApi);
  fileContainer = ref<FileContainer>();
  files = ref<ShepardFile[]>([]);
  loading = ref<boolean>(true);

  async delete() {
    try {
      await this.api.deleteFileContainer({
        fileContainerId: this.id,
      });
      emitSuccess(
        `Successfully deleted container "${this.fileContainer.value?.name}"`,
      );
      await useRouter().push(containersPath);
    } catch (e) {
      handleError(e as ResponseError, "deleting file container");
      throw e;
    }
  }

  async fetchRoles() {
    try {
      this.roles.value = await this.api.getFileRoles({
        fileContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching roles");
      throw e;
    }
  }

  async fetchData() {
    try {
      this.fileContainer.value = await this.api.getFileContainer({
        fileContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching file container");
      throw e;
    }
  }

  async fetchFiles() {
    this.loading.value = true;
    try {
      this.files.value = await this.api.getAllFiles({
        fileContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching container files");
      throw e;
    } finally {
      this.loading.value = false;
    }
  }

  async downloadFile(file: ShepardFile) {
    if (file.oid && file.filename) {
      this.api
        .getFile({
          fileContainerId: this.id,
          oid: file.oid,
        })
        .then(response => {
          downloadFile(response, file.filename ?? "file");
        })
        .catch(e => {
          handleError(e as ResponseError, "downloading file");
        });
    }
  }

  async uploadFile(file: File) {
    return this.api
      .createFile({ fileContainerId: this.id, file })
      .then(() => {
        return Promise.resolve();
      })
      .finally(() => {
        this.fetchFiles();
      });
  }

  async deleteFile(file: ShepardFile) {
    if (file.oid) {
      try {
        await this.api.deleteFile({
          fileContainerId: this.id,
          oid: file.oid,
        });
        emitSuccess(
          `Successfully deleted file "${file.filename}" from container`,
        );
        this.fetchFiles();
      } catch (e) {
        handleError(e as ResponseError, "deleting file from container");
      }
    }
  }

  async fetchPermissions() {
    try {
      this.permissions.value = await this.api.getFilePermissions({
        fileContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching permissions");
      throw e;
    }
  }

  async updatePermissions(updatedPermissions: Permissions) {
    try {
      await this.api.editFilePermissions({
        fileContainerId: this.id,
        permissions: updatedPermissions,
      });
      emitSuccess(
        `Successfully updated permissions for file container ID: ${this.id}`,
      );
      handleContainerUpdate();
    } catch (e) {
      handleError(e as ResponseError, "updating permissions");
      throw e;
    }
  }
}

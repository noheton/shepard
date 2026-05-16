import { handleContainerUpdate } from "#imports";
import {
  FileContainerApi,
  type FileContainer,
  type Permissions,
  type ResponseError,
  type ShepardFile,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { ContainerAccessor } from "../shepardObjectAccessor";

// Sentinel: backend returned 503 — no S3 adapter active; caller falls back to legacy upload.
class PresignedUnavailable extends Error {
  constructor() {
    super("presigned-unavailable");
  }
}

// Same derivation as usePublishEntity.ts / useV2ShepardApi.ts.
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export class FileContainerAccessor extends ContainerAccessor {
  api = useShepardApi(FileContainerApi);
  fileContainer = ref<FileContainer>();
  files = ref<ShepardFile[]>([]);
  loading = ref<boolean>(true);

  async delete() {
    try {
      await this.api.value.deleteFileContainer({
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
      this.roles.value = await this.api.value.getFileRoles({
        fileContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching roles");
      throw e;
    }
  }

  async fetchData() {
    try {
      this.fileContainer.value = await this.api.value.getFileContainer({
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
      this.files.value = await this.api.value.getAllFiles({
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
      this.api.value
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

  async uploadFile(file: File): Promise<ShepardFile> {
    const appId = this.fileContainer.value?.appId;
    if (appId) {
      try {
        const result = await this.uploadFilePresigned(appId, file);
        this.fetchFiles();
        return result;
      } catch (e) {
        if (!(e instanceof PresignedUnavailable)) {
          this.fetchFiles();
          throw e;
        }
        // 503 — no S3 adapter active, fall through to legacy upload
      }
    }
    return this.api.value
      .createFile({ fileContainerId: this.id, file })
      .then(shepardFile => Promise.resolve(shepardFile))
      .finally(() => this.fetchFiles());
  }

  // FS1f — three-step presigned upload: get URL → PUT to S3 → commit.
  // Throws PresignedUnavailable if the backend returns 503 (GridFS active).
  private async uploadFilePresigned(
    containerAppId: string,
    file: File,
  ): Promise<ShepardFile> {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) throw new Error("Not authenticated");

    const base = v2BaseUrl();
    const authJson = {
      Authorization: `Bearer ${accessToken}`,
      Accept: "application/json",
      "Content-Type": "application/json",
    };

    // Step 1 — obtain presigned PUT URL.
    const urlResp = await fetch(
      `${base}/v2/file-containers/${encodeURIComponent(containerAppId)}/upload-url`,
      {
        method: "POST",
        headers: authJson,
        body: JSON.stringify({ fileName: file.name }),
      },
    );
    if (urlResp.status === 503) throw new PresignedUnavailable();
    if (!urlResp.ok)
      throw new Error(`upload-url failed (HTTP ${urlResp.status})`);
    const { uploadUrl, oid } = (await urlResp.json()) as {
      uploadUrl: string;
      oid: string;
    };

    // Step 2 — PUT bytes directly to S3 (no auth headers; signature in URL).
    const putResp = await fetch(uploadUrl, {
      method: "PUT",
      body: file,
      headers: { "Content-Type": file.type || "application/octet-stream" },
    });
    if (!putResp.ok)
      throw new Error(`S3 PUT failed (HTTP ${putResp.status})`);

    // Step 3 — register the file in shepard.
    const commitResp = await fetch(
      `${base}/v2/file-containers/${encodeURIComponent(containerAppId)}/upload-url/commit`,
      {
        method: "POST",
        headers: authJson,
        body: JSON.stringify({
          oid,
          fileName: file.name,
          contentType: file.type || null,
          fileSize: file.size,
        }),
      },
    );
    if (!commitResp.ok)
      throw new Error(`commit failed (HTTP ${commitResp.status})`);
    return commitResp.json() as Promise<ShepardFile>;
  }

  async deleteFile(file: ShepardFile) {
    if (file.oid) {
      try {
        await this.api.value.deleteFile({
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
      this.permissions.value = await this.api.value.getFilePermissions({
        fileContainerId: this.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching permissions");
      throw e;
    }
  }

  async updatePermissions(updatedPermissions: Permissions) {
    try {
      await this.api.value.editFilePermissions({
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

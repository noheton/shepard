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
import { safeDeleteContainer } from "./safeDeleteContainer";
import type { XhrUploadOptions } from "./xhrUpload";
import { xhrUploadMultipart, xhrUploadPresignedPut } from "./xhrUpload";

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
    // DI1 — see TimeseriesContainerAccessor.delete for rationale.
    try {
      const result = await safeDeleteContainer("file", this.id, { force: true });
      if (!result.ok) {
        handleError(
          new Error(
            `Server reported ${result.conflict.referenceCount} active references; delete blocked.`,
          ),
          "deleting file container",
        );
        return;
      }
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

  /**
   * Upload a file with optional progress + cancel.
   *
   * Task #135 — when `options.onProgress` or `options.signal` is provided, the
   * upload bypasses the generated `createFile` client (which uses `fetch`,
   * and `fetch` cannot report request-body progress) and instead uses
   * `XMLHttpRequest` directly.  Wire shape stays identical:
   *  - Legacy:    POST <basePath>/fileContainers/{id}/payload — multipart with `file` field.
   *  - Presigned: PUT  <signed-url> — raw bytes (signature in URL).
   *
   * When no `options` are passed, the behaviour falls back to the generated
   * client so existing callers stay unchanged.
   */
  async uploadFile(
    file: File,
    options?: XhrUploadOptions,
  ): Promise<ShepardFile> {
    const appId = this.fileContainer.value?.appId;
    if (appId) {
      try {
        const result = await this.uploadFilePresigned(appId, file, options);
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
    if (options) {
      try {
        const result = await this.uploadFileLegacyXhr(file, options);
        return result;
      } finally {
        this.fetchFiles();
      }
    }
    return this.api.value
      .createFile({ fileContainerId: this.id, file })
      .then(shepardFile => Promise.resolve(shepardFile))
      .finally(() => this.fetchFiles());
  }

  // Legacy multipart upload via XHR — mirrors `FileContainerApi.createFileRaw`
  // exactly (same path under `basePath`, same `file` form field, same Bearer
  // header).  Browser sets `Content-Type: multipart/form-data; boundary=…`
  // automatically when given a `FormData` body — don't set it manually.
  private async uploadFileLegacyXhr(
    file: File,
    options: XhrUploadOptions,
  ): Promise<ShepardFile> {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) throw new Error("Not authenticated");
    const base = (useRuntimeConfig().public.backendApiUrl as string).replace(
      /\/$/,
      "",
    );
    const url = `${base}/fileContainers/${encodeURIComponent(String(this.id))}/payload`;
    return xhrUploadMultipart<ShepardFile>({
      url,
      fieldName: "file",
      file,
      authorization: `Bearer ${accessToken}`,
      options,
    });
  }

  // FS1f — three-step presigned upload: get URL → PUT to S3 → commit.
  // Throws PresignedUnavailable if the backend returns 503 (GridFS active).
  private async uploadFilePresigned(
    containerAppId: string,
    file: File,
    options?: XhrUploadOptions,
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
        signal: options?.signal,
      },
    );
    if (urlResp.status === 503) throw new PresignedUnavailable();
    if (!urlResp.ok)
      throw new Error(`upload-url failed (HTTP ${urlResp.status})`);
    const { uploadUrl, oid } = (await urlResp.json()) as {
      uploadUrl: string;
      oid: string;
    };

    // Step 2 — PUT bytes directly to S3.  This is the byte-bearing leg —
    // route it through XHR so we can observe upload progress + cancel.
    await xhrUploadPresignedPut({
      url: uploadUrl,
      file,
      contentType: file.type || "application/octet-stream",
      options,
    });

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
        signal: options?.signal,
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

<script setup lang="ts">
/**
 * DMP-DOWNLOAD-NAV-01 — "Download DMP" button on Collection detail.
 *
 * Calls `GET /v2/collections/{collectionAppId}/dmp-snippet` (FAIR7),
 * receives the Markdown payload, and triggers a browser download via
 * `URL.createObjectURL`. The endpoint defaults to `text/markdown` when
 * the `Accept` header doesn't ask for JSON — see
 * `DmpSnippetV2Rest.java`.
 *
 * Placement: sits next to the CiteThisCard on the Collection landing
 * page. The two cards address two different funder-facing artefacts:
 *  - Cite this card → copy-paste citation for papers (APA / BibTeX / RIS / CSL).
 *  - Download DMP   → Markdown DMP block for DFG / EU Horizon Europe
 *                     data-management-plan forms.
 *
 * Auth: uses the same Bearer-token pattern as the other /v2 callers on
 * this page (see `downloadRepExport`, `useStructuredDataContainerLinkedDataObjects`).
 *
 * Closes the FAIR7 UI gap — the backend has been shipped since
 * 2026-05-26 (`DmpSnippetV2Rest` + `DmpSnippetService`), but the matrix
 * row sat without a frontend until this PR.
 */
import { dmpSnippetUrl, dmpFilenameFor } from "./dmpDownloadHelpers";

interface Props {
  /** Collection appId — UUID v7 native identifier. */
  collectionAppId: string;
  /** Collection display name; sanitised for the suggested filename. */
  collectionName?: string | null;
}
const props = defineProps<Props>();

const isDownloading = ref(false);

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = (config as { backendV2ApiUrl?: string }).backendV2ApiUrl;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function downloadDmp() {
  if (isDownloading.value) return;
  isDownloading.value = true;
  try {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      handleError(new Error("Not authenticated"), "downloading DMP snippet");
      return;
    }
    const url = dmpSnippetUrl(v2BaseUrl(), props.collectionAppId);
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "text/markdown",
      },
    });
    if (!response.ok) {
      handleError(
        new Error(`HTTP ${response.status}`),
        "downloading DMP snippet",
      );
      return;
    }
    const blob = await response.blob();
    const blobUrl = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = blobUrl;
    a.download = dmpFilenameFor(props.collectionName, props.collectionAppId);
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(blobUrl);
  } catch (e) {
    handleError(e, "downloading DMP snippet");
  } finally {
    isDownloading.value = false;
  }
}
</script>

<template>
  <v-btn
    color="primary"
    variant="tonal"
    prepend-icon="mdi-file-document-arrow-right-outline"
    :loading="isDownloading"
    data-test="download-dmp-button"
    @click="downloadDmp"
  >
    Download DMP
  </v-btn>
</template>

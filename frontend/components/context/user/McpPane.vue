<script setup lang="ts">
/**
 * MCP endpoint pane.
 *
 * Shows the user the SSE URL for shepard's native MCP server so they can
 * paste it into Claude Desktop / claude.ai / any MCP-aware agent. The URL
 * is derived from the current origin so it stays correct regardless of
 * which hostname this shepard instance is being served on.
 *
 * Phase 1 of the native MCP server (aidocs/88) is OIDC-only — the user's
 * agent needs to authenticate with a bearer token. The pane explains that
 * inline and points at /me#api-keys for the manual-token route.
 */

import { mcpSseUrl, mcpStreamableUrl } from "~/utils/mcpEndpoints";

const origin = ref<string>("");
const sseUrl = computed(() => mcpSseUrl(origin.value));
const streamableUrl = computed(() => mcpStreamableUrl(origin.value));

onMounted(() => {
  origin.value = window.location.origin;
});

const copiedKey = ref<string | null>(null);
const copyTimer = ref<ReturnType<typeof setTimeout> | null>(null);

async function copy(key: string, value: string) {
  try {
    await navigator.clipboard.writeText(value);
    copiedKey.value = key;
    if (copyTimer.value) clearTimeout(copyTimer.value);
    copyTimer.value = setTimeout(() => {
      copiedKey.value = null;
    }, 1500);
  } catch {
    /* clipboard unavailable; the URL is still visible for manual copy */
  }
}
</script>

<template>
  <div class="pa-4">
    <h2 class="text-h5 mb-2">Model Context Protocol</h2>
    <p class="text-body-2 text-medium-emphasis mb-4">
      Connect AI agents (Claude Desktop, claude.ai, IDE assistants) to this
      shepard so they can browse your collections, navigate provenance, and
      pull timeseries samples on your behalf.
    </p>

    <v-card class="mb-4" variant="outlined">
      <v-card-title class="text-subtitle-1">
        <v-icon icon="mdi-broadcast" class="mr-2" />
        SSE endpoint
      </v-card-title>
      <v-card-subtitle class="pb-3">
        Use this with Claude Desktop and any other client that speaks the
        MCP SSE transport.
      </v-card-subtitle>
      <v-card-text>
        <v-text-field
          :model-value="sseUrl"
          readonly
          variant="outlined"
          density="compact"
          hide-details
          aria-label="MCP SSE endpoint URL"
        >
          <template #append-inner>
            <v-btn
              size="small"
              variant="text"
              :icon="copiedKey === 'sse' ? 'mdi-check' : 'mdi-content-copy'"
              :aria-label="copiedKey === 'sse' ? 'Copied' : 'Copy SSE URL'"
              @click="copy('sse', sseUrl)"
            />
          </template>
        </v-text-field>
      </v-card-text>
    </v-card>

    <v-card class="mb-4" variant="outlined">
      <v-card-title class="text-subtitle-1">
        <v-icon icon="mdi-swap-horizontal" class="mr-2" />
        Streamable HTTP endpoint
      </v-card-title>
      <v-card-subtitle class="pb-3">
        Newer MCP clients that speak the Streamable HTTP transport (single
        POST, optional SSE response) use this URL.
      </v-card-subtitle>
      <v-card-text>
        <v-text-field
          :model-value="streamableUrl"
          readonly
          variant="outlined"
          density="compact"
          hide-details
          aria-label="MCP Streamable HTTP endpoint URL"
        >
          <template #append-inner>
            <v-btn
              size="small"
              variant="text"
              :icon="copiedKey === 'http' ? 'mdi-check' : 'mdi-content-copy'"
              :aria-label="copiedKey === 'http' ? 'Copied' : 'Copy HTTP URL'"
              @click="copy('http', streamableUrl)"
            />
          </template>
        </v-text-field>
      </v-card-text>
    </v-card>

    <v-alert type="info" variant="tonal" density="compact" class="mb-4">
      Authentication accepts either an OIDC bearer token OR a shepard
      API key (both sent as <code>Authorization: Bearer …</code>).
      For agents like Claude that ask for a single "API key" or
      "Authorization Token", use a shepard API key minted from the
      <strong>Api Keys</strong> tab — it's a static long-lived JWS
      ready to paste.
    </v-alert>

    <v-card class="mb-4" variant="outlined" color="primary">
      <v-card-title class="text-subtitle-1">
        <v-icon icon="mdi-creation" class="mr-2" />
        Connect to Claude (claude.ai / API connector)
      </v-card-title>
      <v-card-text>
        <ol class="ml-4 mb-3" style="line-height: 1.7;">
          <li>Mint a shepard API key under the <strong>Api Keys</strong> tab.</li>
          <li>
            In Claude's custom-connector setup, paste the
            <strong>Streamable HTTP URL</strong> above as the server URL.
          </li>
          <li>
            Set the <strong>Authorization Token</strong> field to your API key
            (no "Bearer " prefix — Claude adds it automatically).
          </li>
        </ol>
        <p class="text-caption text-medium-emphasis mb-0">
          For the Anthropic API <code>mcp_servers</code> body, the same key
          goes in the <code>authorization_token</code> field of the server
          definition. Both SSE and Streamable HTTP transports work; Claude
          picks the right one based on the URL.
        </p>
      </v-card-text>
    </v-card>

    <h3 class="text-subtitle-1 mt-6 mb-2">Available tools</h3>
    <v-list density="compact" class="rounded-lg" border>
      <v-list-item>
        <template #prepend>
          <v-icon icon="mdi-folder-multiple-outline" />
        </template>
        <v-list-item-title>list_collections</v-list-item-title>
        <v-list-item-subtitle>
          Discover Collections the caller can read.
        </v-list-item-subtitle>
      </v-list-item>
      <v-list-item>
        <template #prepend>
          <v-icon icon="mdi-file-tree-outline" />
        </template>
        <v-list-item-title>list_data_objects</v-list-item-title>
        <v-list-item-subtitle>
          Enumerate DataObjects in a Collection with per-payload counts.
        </v-list-item-subtitle>
      </v-list-item>
      <v-list-item>
        <template #prepend>
          <v-icon icon="mdi-card-bulleted-outline" />
        </template>
        <v-list-item-title>get_data_object</v-list-item-title>
        <v-list-item-subtitle>
          Full DataObject record plus typed container breakdown and lineage
          summaries.
        </v-list-item-subtitle>
      </v-list-item>
      <v-list-item>
        <template #prepend>
          <v-icon icon="mdi-chart-line" />
        </template>
        <v-list-item-title>list_channels</v-list-item-title>
        <v-list-item-subtitle>
          Channel 5-tuple discovery for a TimeseriesContainer.
        </v-list-item-subtitle>
      </v-list-item>
      <v-list-item>
        <template #prepend>
          <v-icon icon="mdi-chart-timeline-variant" />
        </template>
        <v-list-item-title>get_channel_data</v-list-item-title>
        <v-list-item-subtitle>
          Raw timestamp / value samples with LTTB downsampling.
        </v-list-item-subtitle>
      </v-list-item>
    </v-list>

    <p class="text-caption text-medium-emphasis mt-4">
      More tools (files, structured data, annotations, lineage chains) are
      being added in subsequent phases.
    </p>
  </div>
</template>

<script lang="ts" setup>
/**
 * II2 (ui-scrutinizer-2026-05-30) — "Cite this DataObject" card.
 *
 * Sister to `CiteThisCard.vue` (RDM-001, Collection variant). The
 * scrutinizer flagged DataObjects as citable but lacking the affordance.
 * DataObjects carry the same `createdBy` / `createdAt` wire-shape fields
 * as Collections, and post-LIC1 may also carry `license`.
 *
 * Wraps `CiteThisCardCommon` with a DataObject-specific input mapping.
 * Author source = `dataObject.createdBy` (single-element array). When
 * `createdByOrcid` (FAIR2) is present, we prefer the ORCID-suffixed
 * author rendering — researchers can attribute themselves precisely.
 *
 * DOI: when published, the DataObject carries a DOI surfaced via
 * `PublicationStatusBadge` (which fetches a separate endpoint). We do
 * not block the citation card on that fetch — citations remain valid
 * without a DOI, and the card simply renders the canonical-URL form. A
 * follow-up could thread the DOI in as an optional prop once the
 * PublicationStatusBadge composable is shared.
 */
import type { DataObject } from "@dlr-shepard/backend-client";
import type { CitationInput } from "~/utils/citation";

const { dataObject } = defineProps<{
  dataObject: DataObject;
}>();

const accessedDate = computed(() => new Date().toISOString().slice(0, 10));

const canonicalUrl = computed(() => {
  if (typeof window === "undefined") {
    return `/dataobjects/${dataObject.id}`;
  }
  return `${window.location.origin}${window.location.pathname}`;
});

const year = computed(() => {
  const ts = dataObject.createdAt;
  if (!ts) return new Date().getFullYear();
  if (ts instanceof Date) return ts.getFullYear();
  return new Date(ts).getFullYear();
});

/**
 * Authors — same shape as the Collection variant. Single-element array
 * with the creator's username. When `createdByOrcid` is available
 * (FAIR2), we append it in parentheses so downstream citation formats
 * preserve the identifier link.
 */
const authors = computed<string[]>(() => {
  const cb = dataObject.createdBy?.trim();
  if (!cb) return [];
  const orcid = (dataObject as unknown as { createdByOrcid?: string | null })
    .createdByOrcid;
  return orcid ? [`${cb} (ORCID: ${orcid})`] : [cb];
});

const dataObjectLicense = computed<string | null>(() => {
  if (!dataObject) return null;
  const raw = (dataObject as unknown as { license?: string | null }).license;
  return raw ?? null;
});

const citationInput = computed<CitationInput>(() => ({
  authors: authors.value,
  year: year.value,
  title: dataObject.name,
  repository: "Shepard Research Data Platform",
  url: canonicalUrl.value,
  license: dataObjectLicense.value,
  accessedDate: accessedDate.value,
}));
</script>

<template>
  <CiteThisCardCommon
    :input="citationInput"
    label="Cite this DataObject"
    testid-prefix="cite-this-do"
  />
</template>

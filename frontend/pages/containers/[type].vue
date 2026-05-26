<script setup lang="ts">
/**
 * UI-DEEP-LINK-TYPED-LISTS — /containers/[type] redirect shim
 *
 * Redirects typed deep-link URLs (e.g. /containers/timeseries) to the
 * query-param-filtered list page (/containers?selectedFilter=TIMESERIES).
 *
 * Only the four types present in ContainerFilterTypes are valid selectedFilter
 * values (FILE, TIMESERIES, STRUCTUREDDATA, SPATIALDATA).
 *
 * hdf5 is a plugin-specific container type with its own route tree at
 * /containers/hdf/[containerId]. It is NOT a valid selectedFilter and
 * therefore falls through to the unrecognised-type fallback (/containers).
 *
 * "files" is included as an alias for "file" — users arriving from a
 * file container detail page will see "files" in their recent address bar
 * and may try to navigate back via that path.
 */
definePageMeta({ layout: false });

const TYPE_MAP: Record<string, string> = {
  file: "FILE",
  files: "FILE",
  timeseries: "TIMESERIES",
  structureddata: "STRUCTUREDDATA",
  spatialdata: "SPATIALDATA",
};

const route = useRoute();
const type = route.params.type?.toString().toLowerCase() ?? "";
const selectedFilter = TYPE_MAP[type];

if (selectedFilter) {
  await navigateTo(`/containers?selectedFilter=${selectedFilter}`, {
    replace: true,
  });
} else {
  await navigateTo("/containers", { replace: true });
}
</script>

<template />

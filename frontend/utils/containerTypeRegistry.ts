/**
 * UI21 — Shared per-container-kind icon + colour + label registry.
 *
 * Single source of truth for the per-kind visual treatment of container
 * types across the UI. Previously the mapping was duplicated in:
 *   - CollectionContainersPanel.vue (containerIcon / containerLabel)
 *   - WatchedContainersPanel.vue    (containerKindIcons)
 *   - ContainerListPage.vue         (hard-coded empty-state mdi-database)
 *
 * Keyed by `string` rather than the generated `ContainerType` enum so
 * future plugin-contributed kinds (HDF5Container, VideoContainer, etc.)
 * can be added without a regen of the backend client. Unknown kinds
 * fall back to a neutral database icon.
 */

export interface ContainerTypeDescriptor {
  /** Canonical type key — matches `BasicContainer.type`. */
  readonly key: string;
  /** Human label shown in tables, chips, list rows. */
  readonly label: string;
  /** Material Design Icons name (`mdi-…`). */
  readonly icon: string;
  /** Vuetify colour token (or hex) used for icon tint + accent chips. */
  readonly color: string;
  /** URL path segment under `/containers/` for the detail page. */
  readonly urlSegment: string;
  /** True when the kind has a CC1b linked-data-objects endpoint
   *  (used for orphan / referenced-by checks on list pages). */
  readonly supportsReferenceCheck: boolean;
}

const FALLBACK: ContainerTypeDescriptor = {
  key: "UNKNOWN",
  label: "Container",
  icon: "mdi-database-outline",
  color: "textbody2",
  urlSegment: "basic/",
  supportsReferenceCheck: false,
};

/**
 * Registry. Entries are intentionally ordered: real kinds first
 * (matching the generated ContainerType enum), then forward-compat
 * plugin kinds, then BASIC as a structural fallback.
 */
export const CONTAINER_TYPE_REGISTRY: Readonly<
  Record<string, ContainerTypeDescriptor>
> = Object.freeze({
  TIMESERIES: {
    key: "TIMESERIES",
    label: "Timeseries",
    icon: "mdi-chart-line",
    color: "primary",
    urlSegment: "timeseries/",
    supportsReferenceCheck: true,
  },
  FILE: {
    key: "FILE",
    label: "File",
    icon: "mdi-folder-outline",
    color: "info",
    urlSegment: "files/",
    supportsReferenceCheck: true,
  },
  STRUCTUREDDATA: {
    key: "STRUCTUREDDATA",
    label: "Structured Data",
    icon: "mdi-table-large",
    color: "secondary",
    urlSegment: "structureddata/",
    supportsReferenceCheck: true,
  },
  SPATIALDATA: {
    key: "SPATIALDATA",
    label: "Spatial Data",
    icon: "mdi-map-outline",
    color: "success",
    urlSegment: "spatialdata/",
    supportsReferenceCheck: false,
  },
  // Forward-compat plugin kinds — landing pages may not yet exist.
  HDF5: {
    key: "HDF5",
    label: "HDF5",
    icon: "mdi-grid",
    color: "warning",
    urlSegment: "hdf/",
    supportsReferenceCheck: false,
  },
  VIDEO: {
    key: "VIDEO",
    label: "Video",
    icon: "mdi-video-outline",
    color: "error",
    urlSegment: "video/",
    supportsReferenceCheck: false,
  },
  BASIC: {
    key: "BASIC",
    label: "Container",
    icon: "mdi-database-outline",
    color: "textbody2",
    urlSegment: "basic/",
    supportsReferenceCheck: false,
  },
});

export function describeContainerType(
  type: string | null | undefined,
): ContainerTypeDescriptor {
  if (!type) return FALLBACK;
  return CONTAINER_TYPE_REGISTRY[type.toUpperCase()] ?? FALLBACK;
}

/** Convenience accessors — keep call sites short. */
export function iconForContainerType(type: string | null | undefined): string {
  return describeContainerType(type).icon;
}
export function colorForContainerType(type: string | null | undefined): string {
  return describeContainerType(type).color;
}
export function labelForContainerType(type: string | null | undefined): string {
  return describeContainerType(type).label;
}
export function urlSegmentForContainerType(
  type: string | null | undefined,
): string {
  return describeContainerType(type).urlSegment;
}
export function supportsReferenceCheck(
  type: string | null | undefined,
): boolean {
  return describeContainerType(type).supportsReferenceCheck;
}

/** All known descriptors, useful for type-filter dropdowns. */
export function allContainerTypeDescriptors(): ContainerTypeDescriptor[] {
  return Object.values(CONTAINER_TYPE_REGISTRY);
}

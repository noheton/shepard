// LIC1 (FAIR-1): SPDX license list shown in the create/edit dialogs as
// v-autocomplete suggestions. The Collection / DataObject `license` field is
// free-text on the wire (see backend/AbstractDataObjectIO.java) so users can
// type anything, but pre-populated suggestions point them at the most common
// open licenses + a "PROPRIETARY" catch-all.
//
// Source: a hand-curated subset of the SPDX License List
// (https://spdx.org/licenses/) ordered roughly by frequency-in-the-wild for
// scientific datasets and software. Adding to this list is safe; removing
// existing entries is not (operators may already have data licensed against
// them).

export interface SpdxLicense {
  /** SPDX short identifier, e.g. "CC-BY-4.0". Stored verbatim on the wire. */
  id: string;
  /** Human-readable expanded title. */
  title: string;
  /** Rough category, for grouping in the dropdown. */
  category: "creative-commons" | "permissive" | "copyleft" | "data" | "proprietary";
}

/**
 * The recommended license vocabulary shown in the autocomplete. The field is
 * free-text so users can override with any SPDX expression (e.g. a custom
 * conjunction like "Apache-2.0 OR MIT"); these are just the common ones.
 */
export const SPDX_LICENSES: SpdxLicense[] = [
  // Creative Commons — by far the most common for research data
  { id: "CC-BY-4.0", title: "Creative Commons Attribution 4.0", category: "creative-commons" },
  { id: "CC-BY-SA-4.0", title: "Creative Commons Attribution-ShareAlike 4.0", category: "creative-commons" },
  { id: "CC-BY-NC-4.0", title: "Creative Commons Attribution-NonCommercial 4.0", category: "creative-commons" },
  { id: "CC-BY-ND-4.0", title: "Creative Commons Attribution-NoDerivatives 4.0", category: "creative-commons" },
  { id: "CC-BY-NC-SA-4.0", title: "Creative Commons Attribution-NonCommercial-ShareAlike 4.0", category: "creative-commons" },
  { id: "CC-BY-NC-ND-4.0", title: "Creative Commons Attribution-NonCommercial-NoDerivatives 4.0", category: "creative-commons" },
  { id: "CC0-1.0", title: "Creative Commons Zero (Public Domain)", category: "creative-commons" },

  // Permissive software licenses
  { id: "MIT", title: "MIT License", category: "permissive" },
  { id: "Apache-2.0", title: "Apache License 2.0", category: "permissive" },
  { id: "BSD-2-Clause", title: "BSD 2-Clause \"Simplified\" License", category: "permissive" },
  { id: "BSD-3-Clause", title: "BSD 3-Clause \"New\" or \"Revised\" License", category: "permissive" },
  { id: "ISC", title: "ISC License", category: "permissive" },
  { id: "Unlicense", title: "The Unlicense (Public Domain Dedication)", category: "permissive" },
  { id: "Zlib", title: "zlib License", category: "permissive" },

  // Copyleft (weak and strong)
  { id: "MPL-2.0", title: "Mozilla Public License 2.0", category: "copyleft" },
  { id: "LGPL-3.0-only", title: "GNU Lesser General Public License 3.0", category: "copyleft" },
  { id: "LGPL-2.1-or-later", title: "GNU Lesser General Public License 2.1 or later", category: "copyleft" },
  { id: "GPL-3.0-only", title: "GNU General Public License 3.0", category: "copyleft" },
  { id: "GPL-2.0-or-later", title: "GNU General Public License 2.0 or later", category: "copyleft" },
  { id: "AGPL-3.0-only", title: "GNU Affero General Public License 3.0", category: "copyleft" },
  { id: "EUPL-1.2", title: "European Union Public License 1.2", category: "copyleft" },

  // Data-specific
  { id: "ODbL-1.0", title: "Open Data Commons Open Database License 1.0", category: "data" },
  { id: "ODC-By-1.0", title: "Open Data Commons Attribution License 1.0", category: "data" },
  { id: "PDDL-1.0", title: "Open Data Commons Public Domain Dedication & License 1.0", category: "data" },
  { id: "DL-DE-BY-2.0", title: "Data licence Germany - attribution - Version 2.0", category: "data" },
  { id: "DL-DE-ZERO-2.0", title: "Data licence Germany - Zero - Version 2.0", category: "data" },

  // In-house / non-SPDX
  { id: "PROPRIETARY", title: "Proprietary / All rights reserved (in-house terms)", category: "proprietary" },
];

// ── accessRights enum ────────────────────────────────────────────────────
//
// Mirrors the backend's @Schema enumeration on AbstractDataObjectIO.accessRights.
// Values are stored as-is on the wire (free-text String, server permissive); the
// frontend enforces the controlled vocabulary via the v-select.

export type AccessRights = "OPEN" | "RESTRICTED" | "CLOSED" | "EMBARGOED";

export interface AccessRightsOption {
  value: AccessRights;
  /** Vuetify color name used by the badge + select chip. */
  color: string;
  /** Short label shown in the v-select and the badge. */
  label: string;
  /** Long form for the autocomplete hint. */
  description: string;
}

export const ACCESS_RIGHTS_OPTIONS: AccessRightsOption[] = [
  {
    value: "OPEN",
    color: "success",
    label: "Open",
    description: "Public, no access restrictions. Anyone can read.",
  },
  {
    value: "RESTRICTED",
    color: "warning",
    label: "Restricted",
    description: "Access conditional — requires authentication or approval.",
  },
  {
    value: "CLOSED",
    color: "error",
    label: "Closed",
    description: "Closed access. Metadata-only externally; full data internal.",
  },
  {
    value: "EMBARGOED",
    color: "info",
    label: "Embargoed",
    description: "Restricted now, to become open at a future date (set in metadata).",
  },
];

/**
 * Lookup helper: given an accessRights string (typed via API or v-select),
 * return its display option, or `undefined` when unset / unknown.
 */
export function getAccessRightsOption(
  value: string | null | undefined,
): AccessRightsOption | undefined {
  if (!value) return undefined;
  return ACCESS_RIGHTS_OPTIONS.find(o => o.value === value);
}

/**
 * Lookup helper: given an SPDX id, return the canonical entry, or `undefined`
 * when not in the curated list. (A consumer-supplied custom license like
 * "MyTeam-License-1.0" returns `undefined`; the UI should display the raw
 * string in that case.)
 */
export function getSpdxLicense(id: string | null | undefined): SpdxLicense | undefined {
  if (!id) return undefined;
  return SPDX_LICENSES.find(l => l.id === id);
}

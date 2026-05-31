/**
 * UI-SHAPES-RENDER-PICKERS-001 — pure helpers for TemplateAutocomplete.
 * Extracted from the Vue component so a Vitest unit covers them without
 * mounting Vuetify.
 */

export interface TemplateListItem {
  appId: string;
  name?: string;
  templateKind?: string;
  description?: string;
}

export interface TemplateOption {
  title: string;
  value: string;
  subtitle?: string;
}

/** Build the v-autocomplete option object for one template. */
export function formatOption(
  t: TemplateListItem,
  kindFilter: string,
): TemplateOption {
  const shortId = t.appId.slice(0, 8);
  const kindBadge =
    kindFilter === "*" && t.templateKind ? ` [${t.templateKind}]` : "";
  return {
    title: `${t.name ?? "(unnamed)"} — ${shortId}…${kindBadge}`,
    value: t.appId,
    subtitle: t.description ?? undefined,
  };
}

/** Build the GET /v2/templates URL. `kind="*"` lists all. */
export function templatesUrl(v2Base: string, kind: string): string {
  if (!kind || kind === "*") return `${v2Base}/v2/templates`;
  return `${v2Base}/v2/templates?kind=${encodeURIComponent(kind)}`;
}

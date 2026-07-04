/**
 * BTKVS-B2 — composable for the form-descriptor endpoint
 * ({@code GET /v2/templates/{templateAppId}/form}, doc 125 §5.1).
 *
 * A form is the write-direction projection of a data-kind template's SHACL
 * shape: the descriptor carries groups + fields (with DASH editor hints) +
 * a server-computed submit block. violations[].path on the submit leg's 422
 * equals fields[].path, so mapping an error to a field is a dictionary
 * lookup (doc 125 §5.2).
 *
 * Per the frontend-v2-only rule this addresses templates by appId and
 * targets /v2/ exclusively.
 */

export interface FormDescriptorField {
  path: string;
  attributeKey?: string | null;
  label: string;
  description?: string | null;
  group?: string | null;
  order?: number | null;
  datatype?: string | null;
  required?: boolean | null;
  pattern?: string | null;
  editor: string;
  singleLine?: boolean | null;
  placeholder?: string | null;
  defaultValue?: string | null;
  options?: string[] | null;
  visibleWhen?: string | null;
  cellMapping?: { sheet?: string | null; cell: string } | null;
}

export interface FormDescriptor {
  templateAppId: string;
  templateKind: string;
  title: string;
  shapeIri?: string | null;
  groups: { id: string; label?: string | null; order?: number | null }[];
  fields: FormDescriptorField[];
  submit: { method: string; href: string; violationContract?: string };
}

/**
 * The form-descriptor endpoint path for a template appId. Centralised so the
 * /v2/ prefix lives in exactly one place.
 */
export function templateFormPath(templateAppId: string): string {
  return `/v2/templates/${encodeURIComponent(templateAppId)}/form`;
}

/**
 * BTKVS-C1-EXCEL-EXPORT — the shape-driven Excel export path (doc 125 §6/D5):
 * the same cell-mapping annotations that drive the form drive the workbook.
 * Sibling of the form descriptor on the generic template surface.
 */
export function templateExcelExportPath(templateAppId: string, dataObjectAppId: string): string {
  return (
    `/v2/templates/${encodeURIComponent(templateAppId)}/export` +
    `?dataObjectAppId=${encodeURIComponent(dataObjectAppId)}`
  );
}

/**
 * Map a 422 problem-JSON's violations[] to {fields[].path → message} — the
 * doc 125 §5.2 dictionary lookup. Pure + side-effect-free for unit tests.
 */
export function violationsByPath(
  problem: { violations?: { path?: string; message?: string; constraint?: string }[] } | null | undefined,
): Record<string, string> {
  const out: Record<string, string> = {};
  for (const v of problem?.violations ?? []) {
    if (v.path) {
      out[v.path] = v.message || v.constraint || "Invalid value";
    }
  }
  return out;
}

/** GET the compiled descriptor. Throws on non-2xx. */
export async function fetchTemplateForm(templateAppId: string): Promise<FormDescriptor> {
  return await $fetch<FormDescriptor>(templateFormPath(templateAppId));
}

/**
 * TOOLS-CONTEXT-* — pure helpers for the in-context tool entry points.
 *
 * Per the "Always: tool entry points are in-context first" rule
 * (CLAUDE.md, codified 2026-05-30), the canonical entry shape for any
 * entity-scoped tool is an action button on the entity's detail page,
 * pre-populated with the entity's appId. This module owns:
 *
 *  - The action-item inventory per entity scope (collection / data-object).
 *    Used both by the in-context popover menus and by component tests.
 *  - Sensible default prefill queries (SPARQL today; SHACL/vocab routes
 *    just carry `focusAppId` + `scope` query params and the destination
 *    page handles display).
 *
 * Kept as a plain module so the inventory is testable without mounting
 * the Vue component (mirrors `toolsLanding.ts` / `sectionLanding.ts`).
 */

export type ToolsContextScope = "collection" | "data-object";

export interface ContextToolItem {
  /** Stable id for tests + analytics. */
  id: string;
  /** Title shown in the v-menu item. */
  title: string;
  /** Short hint shown as subtitle. */
  subtitle: string;
  /** MDI icon. */
  icon: string;
  /** Destination path (router push target). */
  path: string;
  /**
   * Query-param builder. Receives the entity's appId AND any optional
   * extra context (e.g. the attached template's appId) and returns the
   * query object for `router.push({ path, query })`. Kept as a function
   * so callers can extend with extra params (e.g. snapshot pre-pick).
   */
  buildQuery: (appId: string, ctx?: ContextToolBuildContext) => Record<string, string>;
  /**
   * TOOLS-CONTEXT-DO-TEMPLATE-DETECT-1 — gate predicate.
   *
   * When set, the menu only renders this item when the predicate returns
   * true for the current entity context. Used to hide DO-SHACL / DO-RENDER
   * when no `:ShepardTemplate` is attached (per the brief — the buttons
   * should be conditional, not always-visible).
   *
   * When omitted, the item always renders.
   */
  enabledWhen?: (ctx: ContextToolBuildContext) => boolean;
}

/**
 * TOOLS-CONTEXT-DO-TEMPLATE-DETECT-1 — extra entity context passed to
 * the build/gate functions. The `attachedTemplateAppId` is sourced from
 * `DataObjectIO.attachedTemplateAppId` (server-stamped at create time).
 */
export interface ContextToolBuildContext {
  attachedTemplateAppId?: string | null;
  /**
   * UX612-M1 — `templateKind` of the attached template (resolved by the
   * detail page via GET /v2/templates/{appId}). `POST /v2/shapes/render`
   * accepts only VIEW_RECIPE templates (422 otherwise), so the DO-RENDER
   * gate checks the kind, not mere template presence.
   */
  attachedTemplateKind?: string | null;
}

/**
 * Build a sensible default SPARQL query that lists the first 50
 * annotation triples on the entity. Used as the `?query=` prefill on
 * `/semantic/sparql`. The destination page already renders the query
 * in an editable textarea — users see the default and can refine.
 *
 * The pattern matches the n10s-managed internal graph where every
 * annotation is reachable via the entity's appId (mapped to a
 * `urn:shepard:<scope>:<appId>` IRI on the subject side).
 */
export function buildScopedSparqlQuery(
  scope: ToolsContextScope,
  appId: string,
): string {
  const subject =
    scope === "collection"
      ? `<urn:shepard:collection:${appId}>`
      : `<urn:shepard:data-object:${appId}>`;
  return [
    `# Annotations on this ${scope === "collection" ? "Collection" : "DataObject"}`,
    `# Pre-populated from the in-context tool button (TOOLS-CONTEXT-*).`,
    `# Edit freely and re-run.`,
    `SELECT ?predicate ?object WHERE {`,
    `  ${subject} ?predicate ?object .`,
    `}`,
    `LIMIT 50`,
  ].join("\n");
}

/**
 * Action inventory for the Collection-detail Tools menu.
 *
 * Order is display order. Each item routes to a global Tool page with
 * `?focusAppId=<collAppId>&scope=collection` and any tool-specific
 * prefill (e.g. SPARQL gets a `?query=`).
 */
export const COLLECTION_CONTEXT_TOOLS: ContextToolItem[] = [
  {
    id: "coll-sparql",
    title: "Query annotations (SPARQL)",
    subtitle: "Pre-filled query of this Collection's annotation triples.",
    icon: "mdi-code-braces",
    path: "/semantic/sparql",
    buildQuery: (appId) => ({
      repoAppId: "internal",
      focusAppId: appId,
      scope: "collection",
      query: buildScopedSparqlQuery("collection", appId),
    }),
  },
  {
    id: "coll-vocab",
    title: "Terms used here",
    subtitle: "Vocabulary browser filtered to this Collection.",
    icon: "mdi-bookshelf",
    path: "/semantic/vocabularies",
    buildQuery: (appId) => ({
      usedBy: appId,
      scope: "collection",
    }),
  },
  {
    // V2CONV-B6-POLISH Item 4 — in-context "create template" affordance.
    // Routes to the admin templates page with a prefill hint so the dialog
    // opens pre-populated with this Collection's scope.
    id: "coll-create-template",
    title: "Create template for this Collection",
    subtitle: "Open the template editor pre-scoped to this Collection kind.",
    icon: "mdi-file-code-outline",
    path: "/admin/templates",
    buildQuery: (appId) => ({
      newTemplate: "1",
      targetEntityAppId: appId,
      scope: "collection",
    }),
  },
];

/**
 * Action inventory for the DataObject-detail Tools menu.
 *
 * SHACL + Render slots route to `/shapes/validate` and `/shapes/render`
 * with `?focusAppId=<doAppId>` (and `focusShepardId` for the render
 * endpoint's existing param name). The destination pages already accept
 * a template appId as a separate field — users pick which template to
 * apply if the DataObject lacks a `:CREATED_FROM_TEMPLATE` edge that
 * the frontend can observe (filed as TOOLS-CONTEXT-DO-TEMPLATE-DETECT-1).
 */
export const DATA_OBJECT_CONTEXT_TOOLS: ContextToolItem[] = [
  {
    id: "do-sparql",
    title: "Query annotations (SPARQL)",
    subtitle: "Pre-filled query of this DataObject's annotation triples.",
    icon: "mdi-code-braces",
    path: "/semantic/sparql",
    buildQuery: (appId) => ({
      repoAppId: "internal",
      focusAppId: appId,
      scope: "data-object",
      query: buildScopedSparqlQuery("data-object", appId),
    }),
  },
  {
    id: "do-vocab",
    title: "Terms used here",
    subtitle: "Vocabulary browser filtered to this DataObject.",
    icon: "mdi-bookshelf",
    path: "/semantic/vocabularies",
    buildQuery: (appId) => ({
      usedBy: appId,
      scope: "data-object",
    }),
  },
  {
    id: "do-shacl",
    title: "Validate against shape",
    subtitle: "Open SHACL playground pre-filled with this DataObject.",
    icon: "mdi-check-decagram-outline",
    path: "/shapes/validate",
    buildQuery: (appId, ctx) => {
      const out: Record<string, string> = {
        focusAppId: appId,
        scope: "data-object",
      };
      if (ctx?.attachedTemplateAppId) out.templateAppId = ctx.attachedTemplateAppId;
      return out;
    },
    // TOOLS-CONTEXT-DO-TEMPLATE-DETECT-1 — only render when a template is attached.
    enabledWhen: (ctx) => Boolean(ctx.attachedTemplateAppId),
  },
  // FORM-UX-ACTIONBUTTON — the former `do-render` ("Render view") entry is
  // ABSORBED by `ActionMenuButton.vue` ("View as …" group, fed by
  // GET /v2/shapes/applicable). The VIEW_RECIPE gate the UX612-M1 fix
  // expressed client-side here is now owned server-side by the
  // applicable-discovery endpoint; keeping both buttons would duplicate
  // the affordance.
  {
    // V2CONV-B6-POLISH Item 4 — in-context "create template" affordance.
    // Routes to the admin templates page with a prefill hint so the dialog
    // opens pre-populated with this DataObject's kind/scope as the target class.
    id: "do-create-template",
    title: "Create template for this DataObject",
    subtitle: "Open the template editor pre-scoped to this DataObject kind.",
    icon: "mdi-file-code-outline",
    path: "/admin/templates",
    buildQuery: (appId) => ({
      newTemplate: "1",
      targetEntityAppId: appId,
      scope: "data-object",
    }),
  },
];

/**
 * Lookup helper for tests + telemetry.
 */
export function getContextTools(
  scope: ToolsContextScope,
): ContextToolItem[] {
  return scope === "collection"
    ? COLLECTION_CONTEXT_TOOLS
    : DATA_OBJECT_CONTEXT_TOOLS;
}

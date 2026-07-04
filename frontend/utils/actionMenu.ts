/**
 * FORM-UX-ACTIONBUTTON — pure helpers for the unified `ActionMenuButton`
 * (doc 125 §5.3 / D4: ONE button carrying "View as…" (mode=VIEW) and
 * "Record a…" (mode=FORM) groups, fed by GET /v2/shapes/applicable).
 *
 * Kept as a plain module so grouping / visibility / routing logic is
 * testable without mounting the Vue component (mirrors `toolsContext.ts`).
 */

import type { ApplicableShapeItem } from "~/composables/useApplicableShapes";

export interface ActionMenuGroups {
  /** mode=VIEW entries — "View as …". */
  views: ApplicableShapeItem[];
  /** mode=FORM entries — "Record a …". */
  forms: ApplicableShapeItem[];
}

/**
 * Split the discovery items into the two display groups. Unknown modes are
 * dropped (forward-compat: a future mode renders nothing rather than a
 * broken entry).
 */
export function groupApplicableItems(
  items: readonly ApplicableShapeItem[] | null | undefined,
): ActionMenuGroups {
  const views: ApplicableShapeItem[] = [];
  const forms: ApplicableShapeItem[] = [];
  for (const item of items ?? []) {
    if (item.mode === "VIEW") views.push(item);
    else if (item.mode === "FORM") forms.push(item);
  }
  return { views, forms };
}

/**
 * The button is hidden entirely when nothing is applicable — an empty
 * menu is dead UI (per the FORM-UX-ACTIONBUTTON brief).
 */
export function actionMenuVisible(
  items: readonly ApplicableShapeItem[] | null | undefined,
): boolean {
  const { views, forms } = groupApplicableItems(items);
  return views.length > 0 || forms.length > 0;
}

export interface ActionMenuTarget {
  path: string;
  query: Record<string, string>;
}

/**
 * Router target per entry:
 *
 * - VIEW → the existing render flow (`/shapes/render`, the UX612-M1-fixed
 *   path that the standalone "Render view" tools entry used): prefilled
 *   `templateAppId` + `focusShepardId` (the render endpoint's existing
 *   param name).
 * - FORM → the form surface. `/tools/form-preview` is the placeholder pane
 *   until the full form pane ships; it receives the template AND the focus
 *   context (`?template=…&focusAppId=…`).
 */
export function buildActionTarget(
  item: ApplicableShapeItem,
  focusAppId: string,
): ActionMenuTarget {
  if (item.mode === "VIEW") {
    return {
      path: "/shapes/render",
      query: {
        templateAppId: item.templateAppId,
        focusShepardId: focusAppId,
        scope: "data-object",
      },
    };
  }
  return {
    path: "/tools/form-preview",
    query: {
      template: item.templateAppId,
      focusAppId,
    },
  };
}

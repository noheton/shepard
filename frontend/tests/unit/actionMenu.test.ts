/**
 * FORM-UX-ACTIONBUTTON — unit tests for the unified ActionMenuButton's
 * pure logic (`utils/actionMenu.ts`): menu population per mode,
 * empty→hidden, and click-routing targets.
 *
 * Pure-helper-pattern tests; no component mount (mirrors
 * toolsContext.test.ts — the Vuetify/Nuxt mount chain is covered by
 * Playwright per the repo convention).
 */
import { describe, it, expect } from "vitest";

import {
  actionMenuVisible,
  buildActionTarget,
  groupApplicableItems,
} from "../../utils/actionMenu";
import type { ApplicableShapeItem } from "../../composables/useApplicableShapes";

const FOCUS = "0197b6a2-7b4c-7000-8a3b-1234567890ab";

const viewItem: ApplicableShapeItem = {
  mode: "VIEW",
  templateAppId: "tmpl-view-1",
  title: "3D trace — gimbal path",
  shapeIri: "urn:shepard:shape:trace3d",
  renderHref: "/v2/shapes/render",
  formHref: null,
};

const formItem: ApplicableShapeItem = {
  mode: "FORM",
  templateAppId: "tmpl-form-1",
  title: "Record a Pyrolysis step",
  renderHref: null,
  formHref: "/v2/templates/tmpl-form-1/form",
};

// ── groupApplicableItems — menu population per mode ────────────────────────

describe("groupApplicableItems", () => {
  it("splits VIEW entries into the 'View as …' group", () => {
    const g = groupApplicableItems([viewItem]);
    expect(g.views).toHaveLength(1);
    expect(g.forms).toHaveLength(0);
    expect(g.views[0]?.title).toBe("3D trace — gimbal path");
  });

  it("splits FORM entries into the 'Record a …' group", () => {
    const g = groupApplicableItems([formItem]);
    expect(g.views).toHaveLength(0);
    expect(g.forms).toHaveLength(1);
    expect(g.forms[0]?.title).toBe("Record a Pyrolysis step");
  });

  it("populates both groups from a mixed response", () => {
    const g = groupApplicableItems([viewItem, formItem]);
    expect(g.views.map(i => i.templateAppId)).toEqual(["tmpl-view-1"]);
    expect(g.forms.map(i => i.templateAppId)).toEqual(["tmpl-form-1"]);
  });

  it("drops unknown modes (forward-compat) and tolerates null/undefined", () => {
    const weird = { ...viewItem, mode: "WIZARD" };
    expect(groupApplicableItems([weird]).views).toHaveLength(0);
    expect(groupApplicableItems([weird]).forms).toHaveLength(0);
    expect(groupApplicableItems(null).views).toHaveLength(0);
    expect(groupApplicableItems(undefined).forms).toHaveLength(0);
  });
});

// ── actionMenuVisible — empty → hidden ─────────────────────────────────────

describe("actionMenuVisible", () => {
  it("is hidden when nothing is applicable (empty list)", () => {
    expect(actionMenuVisible([])).toBe(false);
  });

  it("is hidden when only unknown modes arrive", () => {
    expect(actionMenuVisible([{ ...viewItem, mode: "WIZARD" }])).toBe(false);
  });

  it("is visible with at least one VIEW entry", () => {
    expect(actionMenuVisible([viewItem])).toBe(true);
  });

  it("is visible with at least one FORM entry", () => {
    expect(actionMenuVisible([formItem])).toBe(true);
  });
});

// ── buildActionTarget — click routing ──────────────────────────────────────

describe("buildActionTarget", () => {
  it("routes VIEW entries to the existing render flow with the prefill params", () => {
    const t = buildActionTarget(viewItem, FOCUS);
    expect(t.path).toBe("/shapes/render");
    // The /shapes/render page reads `focusShepardId` (its existing param
    // name — same contract the absorbed do-render tools entry used).
    expect(t.query.focusShepardId).toBe(FOCUS);
    expect(t.query.templateAppId).toBe("tmpl-view-1");
  });

  it("routes FORM entries to the form-preview placeholder with template + focus context", () => {
    const t = buildActionTarget(formItem, FOCUS);
    expect(t.path).toBe("/tools/form-preview");
    expect(t.query.template).toBe("tmpl-form-1");
    expect(t.query.focusAppId).toBe(FOCUS);
  });
});

/**
 * COLL-SCENE-2-UI — contract tests for the
 * `CollectionSceneGraphHeader.vue` band rendered at the top of the
 * Collection detail page.
 *
 * Pure-logic / contract tests (matches the
 * `EntityNotFound.test.ts` + `NotFoundPanel.test.ts` style — Vuetify
 * isn't mounted; Playwright covers the visual rendering). The component
 * has four branches; each branch is asserted via a resolver function that
 * mirrors the v-if/v-else-if/v-else cascade in the SFC template.
 *
 * Branches:
 *   1. linked + scene resolves cleanly → render viewer
 *   2. linked but dangling (404/403)   → render EntityNotFound
 *   3. unlinked + writer               → render link-CTA
 *   4. unlinked + reader               → render nothing
 */
import { describe, it, expect } from "vitest";

type Branch = "viewer" | "not-found" | "link-cta" | "nothing";

function resolveBranch(
  sceneGraphAppId: string | null,
  notFound: boolean,
  canLink: boolean,
): Branch {
  if (sceneGraphAppId && !notFound) return "viewer";
  if (sceneGraphAppId && notFound) return "not-found";
  if (canLink) return "link-cta";
  return "nothing";
}

describe("CollectionSceneGraphHeader — render branch resolver", () => {
  it("renders the viewer when a scene is linked and resolves cleanly", () => {
    expect(resolveBranch("scene-app-id", false, false)).toBe("viewer");
    expect(resolveBranch("scene-app-id", false, true)).toBe("viewer");
  });

  it("renders EntityNotFound when the link dangles", () => {
    // canLink doesn't matter — EntityNotFound takes priority over the CTA so
    // the writer sees the empty-state and can decide to relink via Replace.
    expect(resolveBranch("scene-app-id", true, false)).toBe("not-found");
    expect(resolveBranch("scene-app-id", true, true)).toBe("not-found");
  });

  it("renders the link-CTA when unlinked AND writer", () => {
    expect(resolveBranch(null, false, true)).toBe("link-cta");
  });

  it("renders nothing when unlinked AND not writer", () => {
    expect(resolveBranch(null, false, false)).toBe("nothing");
    expect(resolveBranch(null, true, false)).toBe("nothing");
  });
});

describe("CollectionSceneGraphHeader — col-mount predicate", () => {
  // Parent page-level guard mirrors the v-if on the v-col wrapper:
  // we mount the header only when collectionAppId is set AND
  // (link exists OR caller can edit). This prevents readers from seeing
  // an empty band on collections without a hero scene.
  function shouldMount(
    collectionAppId: string | null,
    sceneGraphAppId: string | null,
    canEdit: boolean,
  ): boolean {
    if (!collectionAppId) return false;
    return !!(sceneGraphAppId || canEdit);
  }

  it("mounts when a scene is linked (regardless of edit permission)", () => {
    expect(shouldMount("c", "s", false)).toBe(true);
    expect(shouldMount("c", "s", true)).toBe(true);
  });

  it("mounts when the user can edit even without a link (CTA path)", () => {
    expect(shouldMount("c", null, true)).toBe(true);
  });

  it("does not mount for readers on a collection with no link", () => {
    expect(shouldMount("c", null, false)).toBe(false);
  });

  it("does not mount when collectionAppId is null (defensive — race on mount)", () => {
    expect(shouldMount(null, "s", true)).toBe(false);
  });
});

describe("CollectionSceneGraphHeader — picker option mapping", () => {
  // The picker maps `SceneListItem` rows to v-select { value, title }
  // shape. The title falls back to a truncated appId when the scene has
  // no name set.
  type SceneListItemLike = { appId: string; name?: string | null };

  function toOption(it: SceneListItemLike): { value: string; title: string } {
    return {
      value: it.appId,
      title: it.name ? `${it.name} (${it.appId.slice(0, 8)}…)` : it.appId,
    };
  }

  it("uses the scene name with a truncated appId when available", () => {
    const opt = toOption({
      appId: "019e6ffc-89a4-76b5-8dbb-15888646a904",
      name: "MFFD robot cell",
    });
    expect(opt.value).toBe("019e6ffc-89a4-76b5-8dbb-15888646a904");
    expect(opt.title).toBe("MFFD robot cell (019e6ffc…)");
  });

  it("falls back to the raw appId when name is missing", () => {
    const opt = toOption({
      appId: "019e6ffc-89a4-76b5-8dbb-15888646a904",
    });
    expect(opt.title).toBe("019e6ffc-89a4-76b5-8dbb-15888646a904");
  });

  it("falls back to the raw appId when name is null", () => {
    const opt = toOption({
      appId: "019e6ffc-89a4-76b5-8dbb-15888646a904",
      name: null,
    });
    expect(opt.title).toBe("019e6ffc-89a4-76b5-8dbb-15888646a904");
  });
});

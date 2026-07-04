/**
 * PROJ-PANEL-1 / PROJ-NAV-1 — unit coverage for the Project composables.
 *
 * Exercises:
 *   useProject               — happy path, 404 → isMissing, fetch error
 *   useProjectSubCollections — happy path with programme strip + children, 404 → isMissing
 *   useProjectList           — happy path returns the appId array
 *
 * Uses vi.stubGlobal('fetch', ...) to intercept the raw fetch calls the
 * composables make against /v2/projects/*.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

import {
  useProject,
  useProjectSubCollections,
  useProjectList,
} from "~/composables/context/useProject";

const PROJECT_APP_ID = "018f9c5a-7e26-7000-a000-000000000001";

function mockFetchOk(body: unknown) {
  return vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    json: () => Promise.resolve(body),
  });
}

function mockFetch404() {
  return vi.fn().mockResolvedValue({
    ok: false,
    status: 404,
    json: () => Promise.resolve({}),
    text: () => Promise.resolve(""),
  });
}

const flush = () => new Promise<void>((r) => setTimeout(r, 0));

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe("useProject", () => {
  it("returns the Project envelope on 200", async () => {
    const body = {
      appId: PROJECT_APP_ID,
      name: "MFFD Upper Shell",
      programmes: ["Clean Aviation JU"],
      subCollectionCount: 5,
      aggregateDoCount: 8251,
      isProject: true,
    };
    vi.stubGlobal("fetch", mockFetchOk(body));

    const { project, isLoading, isMissing, error } = useProject(PROJECT_APP_ID);
    await flush();

    expect(project.value).not.toBeNull();
    expect(project.value?.appId).toBe(PROJECT_APP_ID);
    expect(project.value?.name).toBe("MFFD Upper Shell");
    expect(project.value?.programmes).toEqual(["Clean Aviation JU"]);
    expect(isLoading.value).toBe(false);
    expect(isMissing.value).toBe(false);
    expect(error.value).toBeNull();
  });

  it("flags isMissing when backend returns 404", async () => {
    vi.stubGlobal("fetch", mockFetch404());

    const { project, isLoading, isMissing, error } = useProject(PROJECT_APP_ID);
    await flush();

    expect(project.value).toBeNull();
    expect(isMissing.value).toBe(true);
    expect(isLoading.value).toBe(false);
    // 404 is not an error path — UI uses isMissing to hide affordances quietly.
    expect(error.value).toBeNull();
  });

  it("flags isMissing immediately when appId is blank", async () => {
    const { project, isMissing } = useProject("");
    await flush();
    expect(project.value).toBeNull();
    expect(isMissing.value).toBe(true);
  });
});

describe("useProjectSubCollections", () => {
  it("returns the sub-Collections envelope on 200", async () => {
    const body = {
      projectAppId: PROJECT_APP_ID,
      programmes: ["Clean Aviation JU", "DLR Project Line 4"],
      subCollections: [
        {
          appId: "018f9c5a-7e26-7000-a000-000000000010",
          name: "mffd-afp-tapelaying",
          doCount: 8251,
          alsoMemberOf: [],
        },
      ],
    };
    vi.stubGlobal("fetch", mockFetchOk(body));

    const { subCollections, isMissing } = useProjectSubCollections(PROJECT_APP_ID);
    await flush();

    expect(subCollections.value).not.toBeNull();
    expect(subCollections.value?.programmes).toHaveLength(2);
    expect(subCollections.value?.subCollections).toHaveLength(1);
    expect(subCollections.value?.subCollections[0]?.name).toBe("mffd-afp-tapelaying");
    expect(isMissing.value).toBe(false);
  });

  it("flags isMissing on 404", async () => {
    vi.stubGlobal("fetch", mockFetch404());

    const { subCollections, isMissing } = useProjectSubCollections(PROJECT_APP_ID);
    await flush();

    expect(subCollections.value).toBeNull();
    expect(isMissing.value).toBe(true);
  });
});

describe("useProjectList", () => {
  it("returns the appId array on 200", async () => {
    vi.stubGlobal("fetch", mockFetchOk([PROJECT_APP_ID, "018f9c5a-7e26-7000-a000-000000000003"]));

    const { projectAppIds, isLoading, error } = useProjectList();
    await flush();

    expect(projectAppIds.value).toHaveLength(2);
    expect(projectAppIds.value[0]).toBe(PROJECT_APP_ID);
    expect(isLoading.value).toBe(false);
    expect(error.value).toBeNull();
  });

  it("unwraps the PagedResponseIO {items,...} envelope", async () => {
    vi.stubGlobal(
      "fetch",
      mockFetchOk({
        items: [PROJECT_APP_ID, "018f9c5a-7e26-7000-a000-000000000003"],
        total: 2,
        page: 0,
        pageSize: 50,
      }),
    );

    const { projectAppIds, error } = useProjectList();
    await flush();

    expect(projectAppIds.value).toHaveLength(2);
    expect(projectAppIds.value[0]).toBe(PROJECT_APP_ID);
    expect(error.value).toBeNull();
  });

  it("records the error on non-ok response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: () => Promise.resolve({}),
      text: () => Promise.resolve(""),
    }));

    const { projectAppIds, error } = useProjectList();
    await flush();

    expect(projectAppIds.value).toEqual([]);
    expect(error.value).toBe("Failed to load Projects");
  });
});

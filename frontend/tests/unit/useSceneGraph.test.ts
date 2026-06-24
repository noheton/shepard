/**
 * SCENEGRAPH-REST-1-UI — unit tests for the useSceneGraph composable's
 * pure helpers.
 *
 * Status-to-message mapping, URDF filename sanitisation, parent-index
 * building, and descendant counting. The wire I/O is integration-tested
 * via the Playwright 4K spec; this file exercises the parts that don't
 * need a Vuetify / fetch harness.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  countDescendants,
  indexFramesByParent,
  sceneGraphErrorMessageForStatus,
  urdfDownloadFilename,
  useSceneGraph,
  type FrameIO,
  type SceneListPage,
} from "../../composables/useSceneGraph";

// ── Helpers ──────────────────────────────────────────────────────────────────

function mkFrame(
  appId: string,
  parent: string | null = null,
  overrides: Partial<FrameIO> = {},
): FrameIO {
  return {
    appId,
    name: appId,
    parentFrameAppId: parent,
    x: 0,
    y: 0,
    z: 0,
    rx: 0,
    ry: 0,
    rz: 0,
    kind: "FRAME",
    ...overrides,
  };
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe("useSceneGraph — sceneGraphErrorMessageForStatus", () => {
  it("returns the auth-expired message for 401", () => {
    expect(sceneGraphErrorMessageForStatus(401)).toMatch(/sign in expired/i);
  });

  it("returns the write-access message for 403", () => {
    expect(sceneGraphErrorMessageForStatus(403)).toMatch(/write access/i);
  });

  it("returns the not-found message for 404", () => {
    expect(sceneGraphErrorMessageForStatus(404)).toMatch(/not found/i);
  });

  it("returns the conflict message for 409", () => {
    expect(sceneGraphErrorMessageForStatus(409)).toMatch(/conflict/i);
  });

  it("falls back to detail on 400 + uses generic on unknown status", () => {
    expect(sceneGraphErrorMessageForStatus(400, "missing field")).toMatch(
      /missing field/i,
    );
    expect(sceneGraphErrorMessageForStatus(418)).toMatch(/HTTP 418/);
  });
});

describe("useSceneGraph — urdfDownloadFilename", () => {
  it("sanitises whitespace and slashes into underscores", () => {
    expect(urdfDownloadFilename("KR210 / cell", "appid-x")).toBe(
      "KR210_cell.urdf",
    );
  });

  it("falls back to scene_<short-appId> when name is empty", () => {
    expect(urdfDownloadFilename("", "019e7243-f995-7914")).toBe(
      "scene_019e7243.urdf",
    );
  });

  it("falls back to scene_<short-appId> when name is whitespace only", () => {
    expect(urdfDownloadFilename("   ", "abcd1234efgh")).toBe(
      "scene_abcd1234.urdf",
    );
  });

  it("collapses multiple underscores and strips leading/trailing", () => {
    expect(urdfDownloadFilename("__ a / b __", "x")).toBe("a_b.urdf");
  });

  it("removes filesystem-hostile characters from the name", () => {
    expect(urdfDownloadFilename('scene<>:"|?*name', "x")).toBe(
      "scene_name.urdf",
    );
  });
});

describe("useSceneGraph — indexFramesByParent", () => {
  it("returns empty map for null / empty input", () => {
    expect(indexFramesByParent(null).size).toBe(0);
    expect(indexFramesByParent(undefined).size).toBe(0);
    expect(indexFramesByParent([]).size).toBe(0);
  });

  it("groups null-parented frames under the empty-string key", () => {
    const map = indexFramesByParent([mkFrame("a"), mkFrame("b")]);
    expect(map.get("")?.map((f) => f.appId)).toEqual(["a", "b"]);
  });

  it("groups child frames under their parent's appId", () => {
    const map = indexFramesByParent([
      mkFrame("root"),
      mkFrame("c1", "root"),
      mkFrame("c2", "root"),
      mkFrame("gc1", "c1"),
    ]);
    expect(map.get("root")?.map((f) => f.appId)).toEqual(["c1", "c2"]);
    expect(map.get("c1")?.map((f) => f.appId)).toEqual(["gc1"]);
  });

  it("does not lose orphans (parent pointing at missing frame is still indexed)", () => {
    const map = indexFramesByParent([mkFrame("o", "ghost-parent")]);
    expect(map.get("ghost-parent")?.map((f) => f.appId)).toEqual(["o"]);
  });
});

// ── list() — SCENEGRAPH-LIST-1 wire path ─────────────────────────────────────

interface FakeAuthData {
  accessToken: string;
}

function mockAuth(accessToken: string | null): void {
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    refresh: vi.fn().mockResolvedValue(undefined),
    data: ref<FakeAuthData | null>(
      accessToken === null ? null : { accessToken },
    ),
    signIn: vi.fn().mockResolvedValue(undefined),
  });
}

function mockRuntimeConfig(backendApiUrl: string): void {
  (globalThis as unknown as { useRuntimeConfig: () => unknown }).useRuntimeConfig =
    () => ({ public: { backendApiUrl } });
}

describe("useSceneGraph — list (SCENEGRAPH-LIST-1)", () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    mockAuth("test-token");
    mockRuntimeConfig("http://localhost:8080/shepard/api");
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it("calls GET /v2/scene-graphs with page + size query params and Bearer auth", async () => {
    const samplePage: SceneListPage = {
      items: [
        {
          appId: "0197b6a2-aaaa-7000-8000-000000000001",
          name: "kr210",
          frameCount: 12,
          jointCount: 6,
          createdAt: 1717000000000,
          updatedAt: 1717100000000,
        },
      ],
      total: 1,
      page: 0,
      size: 25,
    };
    const fetchSpy = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(samplePage), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    globalThis.fetch = fetchSpy as unknown as typeof fetch;

    const { list } = useSceneGraph();
    const result = await list({ page: 0, size: 25 });

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const [url, init] = fetchSpy.mock.calls[0]!;
    expect(url).toBe("http://localhost:8080/v2/scene-graphs?page=0&size=25");
    expect((init as RequestInit).method).toBe("GET");
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer test-token");
    expect(headers.Accept).toBe("application/json");

    expect(result?.items).toHaveLength(1);
    expect(result?.items[0]!.appId).toBe(
      "0197b6a2-aaaa-7000-8000-000000000001",
    );
    expect(result?.total).toBe(1);
  });

  it("omits the query string when no page/size provided", async () => {
    const fetchSpy = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ items: [], total: 0, page: 0, size: 50 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    globalThis.fetch = fetchSpy as unknown as typeof fetch;

    const { list } = useSceneGraph();
    await list();

    const [url] = fetchSpy.mock.calls[0]!;
    expect(url).toBe("http://localhost:8080/v2/scene-graphs");
  });

  it("returns null and sets error on a 401 (no auth token)", async () => {
    mockAuth(null);
    const { list, error } = useSceneGraph();
    const result = await list({ page: 0, size: 25 });

    expect(result).toBeNull();
    expect(error.value?.status).toBe(401);
    expect(error.value?.message).toMatch(/sign in expired/i);
  });

  it("returns null and surfaces a 500 error message", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ detail: "neo4j unreachable" }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      }),
    ) as unknown as typeof fetch;

    const { list, error } = useSceneGraph();
    const result = await list({ page: 0, size: 25 });

    expect(result).toBeNull();
    expect(error.value?.status).toBe(500);
    expect(error.value?.detail).toContain("neo4j unreachable");
  });

  it("returns null and sets a network error when fetch rejects", async () => {
    globalThis.fetch = vi
      .fn()
      .mockRejectedValue(new Error("ECONNREFUSED")) as unknown as typeof fetch;

    const { list, error } = useSceneGraph();
    const result = await list({ page: 0, size: 25 });

    expect(result).toBeNull();
    expect(error.value?.status).toBe(0);
    expect(error.value?.message).toMatch(/network error/i);
    expect(error.value?.detail).toBe("ECONNREFUSED");
  });

  it("toggles loading.value across the request lifecycle", async () => {
    let resolveResponse!: (r: Response) => void;
    const pending = new Promise<Response>((resolve) => {
      resolveResponse = resolve;
    });
    globalThis.fetch = vi
      .fn()
      .mockReturnValue(pending) as unknown as typeof fetch;

    const { list, loading } = useSceneGraph();
    const inFlight = list({ page: 0, size: 25 });

    expect(loading.value).toBe(true);

    resolveResponse(
      new Response(JSON.stringify({ items: [], total: 0, page: 0, size: 25 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    await inFlight;
    expect(loading.value).toBe(false);
  });
});

describe("useSceneGraph — countDescendants", () => {
  it("returns 0 for a frame with no children", () => {
    const idx = indexFramesByParent([mkFrame("a")]);
    expect(countDescendants("a", idx)).toBe(0);
  });

  it("counts only descendants (not the frame itself)", () => {
    const idx = indexFramesByParent([
      mkFrame("root"),
      mkFrame("c1", "root"),
      mkFrame("c2", "root"),
      mkFrame("gc1", "c1"),
      mkFrame("gc2", "c1"),
    ]);
    expect(countDescendants("root", idx)).toBe(4);
    expect(countDescendants("c1", idx)).toBe(2);
    expect(countDescendants("c2", idx)).toBe(0);
  });

  it("handles unknown frame ids by returning 0", () => {
    const idx = indexFramesByParent([mkFrame("a")]);
    expect(countDescendants("ghost", idx)).toBe(0);
  });
});

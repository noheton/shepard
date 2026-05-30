/**
 * SCENEGRAPH-CREATE-FROM-URDF-2-FE — unit tests for the
 * useScenegraphFromUrdf composable + the pure post-create decision
 * helper.
 *
 * Mirrors the fetch-mocked pattern in useSceneGraph.test.ts. Component
 * mounting is intentionally skipped — the codebase has no Vuetify mount
 * fixture and the routing decision lives in `decideAfterCreate`, which
 * is tested as a pure function.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  decideAfterCreate,
  defaultSceneNameFor,
  useScenegraphFromUrdf,
  type CreateFromUrdfResult,
} from "../../composables/useScenegraphFromUrdf";
import type { SceneGraphIO } from "../../composables/useSceneGraph";

// ── Test plumbing — mirror useSceneGraph.test.ts ─────────────────────────────

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

// ── defaultSceneNameFor ──────────────────────────────────────────────────────

describe("defaultSceneNameFor", () => {
  it("strips .urdf extension", () => {
    expect(defaultSceneNameFor("kr210_r2700.urdf")).toBe("kr210_r2700");
    expect(defaultSceneNameFor("KR210.URDF")).toBe("KR210");
  });

  it("leaves names without the extension alone", () => {
    expect(defaultSceneNameFor("kr210")).toBe("kr210");
  });

  it("falls back to 'Scene' on empty / whitespace input", () => {
    expect(defaultSceneNameFor("")).toBe("Scene");
    expect(defaultSceneNameFor("   ")).toBe("Scene");
    expect(defaultSceneNameFor(null)).toBe("Scene");
    expect(defaultSceneNameFor(undefined)).toBe("Scene");
  });

  it("falls back to 'Scene' when the name was just an extension", () => {
    expect(defaultSceneNameFor(".urdf")).toBe("Scene");
  });
});

// ── decideAfterCreate (pure route decision) ──────────────────────────────────

describe("decideAfterCreate", () => {
  const sceneAppId = "019e79be-b880-7438-82df-4163625862b7";
  const sample: SceneGraphIO = { appId: sceneAppId, name: "kr210" };

  it("routes to the new scene on 201 success", () => {
    const result: CreateFromUrdfResult = { ok: true, scene: sample };
    expect(decideAfterCreate(result)).toEqual({
      kind: "navigate",
      path: `/scene-graphs/${sceneAppId}`,
    });
  });

  it("routes to the existing scene on 409 idempotency", () => {
    const result: CreateFromUrdfResult = {
      ok: false,
      status: 409,
      detail: "scene already exists for this FileReference",
      existingSceneAppId: sceneAppId,
    };
    expect(decideAfterCreate(result)).toEqual({
      kind: "navigate",
      path: `/scene-graphs/${sceneAppId}`,
    });
  });

  it("URL-encodes the appId in the navigate path", () => {
    const result: CreateFromUrdfResult = {
      ok: true,
      scene: { appId: "bad/id" },
    };
    expect(decideAfterCreate(result)).toEqual({
      kind: "navigate",
      path: "/scene-graphs/bad%2Fid",
    });
  });

  it("surfaces the parser error on 400", () => {
    const result: CreateFromUrdfResult = {
      ok: false,
      status: 400,
      detail: "missing <robot> root",
    };
    const decision = decideAfterCreate(result);
    expect(decision.kind).toBe("error");
    expect((decision as { message: string }).message).toMatch(/<robot> root/);
  });

  it("falls back to a friendly 400 message when detail is empty", () => {
    const decision = decideAfterCreate({ ok: false, status: 400, detail: "" });
    expect(decision.kind).toBe("error");
    expect((decision as { message: string }).message).toMatch(/URDF/i);
  });

  it("returns a permission decision on 403", () => {
    const decision = decideAfterCreate({ ok: false, status: 403, detail: "" });
    expect(decision.kind).toBe("permission");
    expect((decision as { message: string }).message).toMatch(/Write permission/i);
  });

  it("returns a retry decision on network error (status 0)", () => {
    const decision = decideAfterCreate({
      ok: false,
      status: 0,
      detail: "fetch failed",
    });
    expect(decision.kind).toBe("retry");
    expect((decision as { message: string }).message).toContain("fetch failed");
  });

  it("returns a generic error on unexpected status codes", () => {
    const decision = decideAfterCreate({ ok: false, status: 418, detail: "" });
    expect(decision.kind).toBe("error");
    expect((decision as { message: string }).message).toMatch(/HTTP 418/);
  });
});

// ── createFromUrdf (wire I/O, mocked fetch) ──────────────────────────────────

describe("useScenegraphFromUrdf — createFromUrdf", () => {
  const originalFetch = globalThis.fetch;
  const fileRefAppId = "0197b6a2-aaaa-7000-8000-000000000099";

  beforeEach(() => {
    mockAuth("test-token");
    mockRuntimeConfig("http://localhost:8080/shepard/api");
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it("POSTs to /v2/scene-graphs/from-urdf/{appId} with name + description", async () => {
    const scene: SceneGraphIO = { appId: "scene-1", name: "kr210" };
    const fetchSpy = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(scene), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      }),
    );
    globalThis.fetch = fetchSpy as unknown as typeof fetch;

    const { createFromUrdf } = useScenegraphFromUrdf();
    const result = await createFromUrdf({
      fileReferenceAppId: fileRefAppId,
      name: "kr210",
      description: "Loaded from URDF",
    });

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const [url, init] = fetchSpy.mock.calls[0]!;
    expect(url).toBe(
      `http://localhost:8080/v2/scene-graphs/from-urdf/${fileRefAppId}`,
    );
    expect((init as RequestInit).method).toBe("POST");
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer test-token");
    expect(headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      name: "kr210",
      description: "Loaded from URDF",
    });
    expect(result.ok).toBe(true);
    if (result.ok) expect(result.scene.appId).toBe("scene-1");
  });

  it("returns existingSceneAppId on 409", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          detail: "scene already exists for this FileReference",
          existingSceneAppId: "existing-scene-7",
        }),
        { status: 409, headers: { "Content-Type": "application/json" } },
      ),
    ) as unknown as typeof fetch;

    const { createFromUrdf } = useScenegraphFromUrdf();
    const result = await createFromUrdf({ fileReferenceAppId: fileRefAppId });

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.status).toBe(409);
      expect(result.existingSceneAppId).toBe("existing-scene-7");
    }
  });

  it("returns detail on 400", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ detail: "missing <robot> root" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      }),
    ) as unknown as typeof fetch;

    const { createFromUrdf } = useScenegraphFromUrdf();
    const result = await createFromUrdf({ fileReferenceAppId: fileRefAppId });

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.status).toBe(400);
      expect(result.detail).toContain("<robot> root");
    }
  });

  it("returns status 403 when caller lacks write permission", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ detail: "no write" }), { status: 403 }),
    ) as unknown as typeof fetch;

    const { createFromUrdf } = useScenegraphFromUrdf();
    const result = await createFromUrdf({ fileReferenceAppId: fileRefAppId });

    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.status).toBe(403);
  });

  it("returns status 401 when no auth token is set", async () => {
    mockAuth(null);
    const { createFromUrdf } = useScenegraphFromUrdf();
    const result = await createFromUrdf({ fileReferenceAppId: fileRefAppId });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.status).toBe(401);
  });

  it("returns status 0 on network error", async () => {
    globalThis.fetch = vi
      .fn()
      .mockRejectedValue(new Error("ECONNREFUSED")) as unknown as typeof fetch;

    const { createFromUrdf } = useScenegraphFromUrdf();
    const result = await createFromUrdf({ fileReferenceAppId: fileRefAppId });

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.status).toBe(0);
      expect(result.detail).toContain("ECONNREFUSED");
    }
  });
});

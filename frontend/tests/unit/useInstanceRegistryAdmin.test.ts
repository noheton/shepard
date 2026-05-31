import { describe, it, expect, vi, beforeEach } from "vitest";
import { useInstanceRegistryAdmin } from "~/composables/context/admin/useInstanceRegistryAdmin";
import type { RegisteredInstance } from "~/composables/context/admin/useInstanceRegistryAdmin";

const ACCESS_TOKEN = "test-admin-token";

beforeEach(() => {
  vi.clearAllMocks();
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
  vi.stubGlobal("fetch", vi.fn());
});

function mockFetchSequence(...bodies: unknown[]) {
  const fn = vi.fn();
  for (const body of bodies) {
    fn.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve(body),
    });
  }
  vi.stubGlobal("fetch", fn);
  return fn;
}

function mockFetchError(status: number, bodyText = "error") {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status,
      text: () => Promise.resolve(bodyText),
    }),
  );
}

const ROW: RegisteredInstance = {
  instanceId: "dlr-augsburg",
  displayName: "DLR BT, Augsburg",
  baseUrl: "https://shepard-api.intra.dlr.de",
  dlrInstitute: "BT",
};

describe("useInstanceRegistryAdmin — refresh()", () => {
  it("populates instances from { instances: [...] } envelope", async () => {
    // Auto-refresh on construction + explicit refresh() = 2 fetches.
    mockFetchSequence({ instances: [ROW] }, { instances: [ROW] });
    const { instances, refresh } = useInstanceRegistryAdmin();
    await refresh();
    expect(instances.value).toEqual([ROW]);
  });

  it("treats an empty list as the default-empty registry, not an error", async () => {
    // The composable auto-refreshes at construction; provide two responses
    // so the explicit refresh() call has its own mocked response.
    mockFetchSequence({ instances: [] }, { instances: [] });
    const { instances, error, refresh } = useInstanceRegistryAdmin();
    await refresh();
    expect(instances.value).toEqual([]);
    expect(error.value).toBeNull();
  });

  it("sets error on HTTP failure", async () => {
    mockFetchError(500);
    const { instances, error, refresh } = useInstanceRegistryAdmin();
    await refresh();
    expect(error.value).toBe("Failed to load instance registry");
    expect(instances.value).toEqual([]);
  });
});

describe("useInstanceRegistryAdmin — addInstance()", () => {
  it("sends PATCH with the full list (existing + new row) per RFC 7396 array semantics", async () => {
    const fetchFn = mockFetchSequence(
      { instances: [ROW] }, // initial refresh
      { instances: [ROW, { instanceId: "dlr-bs", displayName: "DLR Braunschweig" }] }, // PATCH response
    );
    const { instances, addInstance } = useInstanceRegistryAdmin();
    await Promise.resolve(); // let refresh() resolve
    await Promise.resolve();
    expect(instances.value).toHaveLength(1);

    const ok = await addInstance({
      instanceId: "dlr-bs",
      displayName: "DLR Braunschweig",
    });
    expect(ok).not.toBeNull();
    expect(instances.value).toHaveLength(2);

    const lastCall = fetchFn.mock.calls.at(-1) as [string, RequestInit];
    expect(lastCall[0]).toContain("/v2/admin/instances");
    expect(lastCall[1].method).toBe("PATCH");
    const body = JSON.parse(lastCall[1].body as string);
    expect(body.instances).toHaveLength(2);
    expect(body.instances[1].instanceId).toBe("dlr-bs");
  });

  it("dedupes by instanceId — re-adding replaces rather than duplicating", async () => {
    const fetchFn = mockFetchSequence(
      { instances: [ROW] },
      { instances: [{ ...ROW, displayName: "DLR (renamed)" }] },
    );
    const { instances, addInstance } = useInstanceRegistryAdmin();
    await Promise.resolve();
    await Promise.resolve();

    await addInstance({ ...ROW, displayName: "DLR (renamed)" });

    const lastCall = fetchFn.mock.calls.at(-1) as [string, RequestInit];
    const body = JSON.parse(lastCall[1].body as string);
    expect(body.instances).toHaveLength(1);
    expect(body.instances[0].displayName).toBe("DLR (renamed)");
    expect(instances.value).toHaveLength(1);
  });

  it("returns null and surfaces detail on PATCH 400", async () => {
    const fetchFn = vi
      .fn()
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ instances: [] }) })
      .mockResolvedValueOnce({
        ok: false,
        status: 400,
        text: () => Promise.resolve(JSON.stringify({ detail: "instanceId required" })),
      });
    vi.stubGlobal("fetch", fetchFn);
    const { error, addInstance } = useInstanceRegistryAdmin();
    await Promise.resolve();
    await Promise.resolve();

    const result = await addInstance({ instanceId: "x" });
    expect(result).toBeNull();
    expect(error.value).toBe("instanceId required");
  });
});

describe("useInstanceRegistryAdmin — deleteInstance()", () => {
  it("sends PATCH with the row removed", async () => {
    const fetchFn = mockFetchSequence(
      { instances: [ROW, { instanceId: "dlr-bs", displayName: "DLR Braunschweig" }] },
      { instances: [{ instanceId: "dlr-bs", displayName: "DLR Braunschweig" }] },
    );
    const { instances, deleteInstance } = useInstanceRegistryAdmin();
    await Promise.resolve();
    await Promise.resolve();
    expect(instances.value).toHaveLength(2);

    await deleteInstance("dlr-augsburg");

    const lastCall = fetchFn.mock.calls.at(-1) as [string, RequestInit];
    const body = JSON.parse(lastCall[1].body as string);
    expect(body.instances).toHaveLength(1);
    expect(body.instances[0].instanceId).toBe("dlr-bs");
    expect(instances.value).toHaveLength(1);
  });

  it("deleting an unknown instanceId is a no-op (still PATCHes with full list)", async () => {
    const fetchFn = mockFetchSequence(
      { instances: [ROW] },
      { instances: [ROW] },
    );
    const { instances, deleteInstance } = useInstanceRegistryAdmin();
    await Promise.resolve();
    await Promise.resolve();

    await deleteInstance("does-not-exist");

    const lastCall = fetchFn.mock.calls.at(-1) as [string, RequestInit];
    const body = JSON.parse(lastCall[1].body as string);
    expect(body.instances).toEqual([ROW]);
    expect(instances.value).toEqual([ROW]);
  });
});

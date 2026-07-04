import { describe, it, expect, vi, beforeEach } from "vitest";
import type { TestNotificationRequest } from "~/composables/context/admin/useNotificationsAdmin";
import {
  buildTestRequest,
  canSendTest,
  targetUsernameError,
  useNotificationsAdmin,
} from "~/composables/context/admin/useNotificationsAdmin";

const ACCESS_TOKEN = "test-admin-token";

beforeEach(() => {
  vi.clearAllMocks();
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
  vi.stubGlobal("fetch", vi.fn());
});

function mockFetchOk(body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(body),
    }),
  );
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

const defaultReq: TestNotificationRequest = {
  audience: "INSTANCE_ADMIN",
  category: "INFO",
  title: "Test notification",
  body: "Hello.",
};

describe("useNotificationsAdmin — sendTest()", () => {
  it("starts with isSending=false and no result", () => {
    const { isSending, lastResult, error } = useNotificationsAdmin();
    expect(isSending.value).toBe(false);
    expect(lastResult.value).toBeNull();
    expect(error.value).toBeNull();
  });

  it("POSTs to /v2/admin/notifications/test with the request body", async () => {
    mockFetchOk({ appId: "abc", title: "Test notification" });
    const { sendTest } = useNotificationsAdmin();
    await sendTest(defaultReq);

    const [url, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(url).toContain("/v2/admin/notifications/test");
    expect(opts.method).toBe("POST");
    expect((opts.headers as Record<string, string>)["Authorization"]).toBe(
      `Bearer ${ACCESS_TOKEN}`,
    );
    expect(JSON.parse(opts.body as string)).toEqual(defaultReq);
  });

  it("populates lastResult on success", async () => {
    const resp = { appId: "abc", title: "ok" };
    mockFetchOk(resp);
    const { sendTest, lastResult } = useNotificationsAdmin();
    const result = await sendTest(defaultReq);
    expect(result).toEqual(resp);
    expect(lastResult.value).toEqual(resp);
  });

  it("extracts {detail} message from a 400 response", async () => {
    mockFetchError(
      400,
      JSON.stringify({ detail: "targetUsername required when audience=USER." }),
    );
    const { sendTest, error } = useNotificationsAdmin();
    const result = await sendTest({ ...defaultReq, audience: "USER" });
    expect(result).toBeNull();
    expect(error.value).toBe("targetUsername required when audience=USER.");
  });

  it("returns null and surfaces a generic error on network failure", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new Error("Network down")),
    );
    const { sendTest, error } = useNotificationsAdmin();
    const result = await sendTest(defaultReq);
    expect(result).toBeNull();
    expect(error.value).toBe("Failed to send test notification");
  });

  it("forwards targetUsername + actionUrl when present", async () => {
    mockFetchOk({});
    const { sendTest } = useNotificationsAdmin();
    await sendTest({
      ...defaultReq,
      audience: "USER",
      targetUsername: "kreb_fl",
      actionUrl: "https://shepard.example.org/admin",
    });
    const [, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    const body = JSON.parse(opts.body as string);
    expect(body.targetUsername).toBe("kreb_fl");
    expect(body.actionUrl).toBe("https://shepard.example.org/admin");
  });

  it("flips isSending to true while in flight then back to false", async () => {
    mockFetchOk({});
    const { isSending, sendTest } = useNotificationsAdmin();
    expect(isSending.value).toBe(false);
    const p = sendTest(defaultReq);
    expect(isSending.value).toBe(true);
    await p;
    expect(isSending.value).toBe(false);
  });
});

describe("targetUsernameError — form-validation helper", () => {
  it("returns null when audience is INSTANCE_ADMIN regardless of username", () => {
    expect(targetUsernameError("INSTANCE_ADMIN", "")).toBeNull();
    expect(targetUsernameError("INSTANCE_ADMIN", "ignored")).toBeNull();
  });

  it("returns null when audience is ALL regardless of username", () => {
    expect(targetUsernameError("ALL", "")).toBeNull();
  });

  it("returns an error when audience is USER and username is blank", () => {
    expect(targetUsernameError("USER", "")).toMatch(/Required/);
    expect(targetUsernameError("USER", "   ")).toMatch(/Required/);
  });

  it("returns null when audience is USER and username is non-blank", () => {
    expect(targetUsernameError("USER", "kreb_fl")).toBeNull();
  });
});

describe("canSendTest — submit-button gating", () => {
  it("blocks empty title", () => {
    expect(canSendTest("", "INSTANCE_ADMIN", "")).toBe(false);
    expect(canSendTest("   ", "INSTANCE_ADMIN", "")).toBe(false);
  });

  it("allows non-empty title to INSTANCE_ADMIN/ALL with no username", () => {
    expect(canSendTest("hi", "INSTANCE_ADMIN", "")).toBe(true);
    expect(canSendTest("hi", "ALL", "")).toBe(true);
  });

  it("blocks USER audience with blank username even when title set", () => {
    expect(canSendTest("hi", "USER", "")).toBe(false);
  });

  it("allows USER audience when both title and username set", () => {
    expect(canSendTest("hi", "USER", "kreb_fl")).toBe(true);
  });
});

describe("buildTestRequest — payload shape", () => {
  const base = {
    audience: "INSTANCE_ADMIN" as const,
    targetUsername: "",
    category: "INFO" as const,
    title: "  hello  ",
    body: "  world  ",
    actionUrl: "",
  };

  it("trims title and body, omits targetUsername and actionUrl", () => {
    const req = buildTestRequest(base);
    expect(req).toEqual({
      audience: "INSTANCE_ADMIN",
      category: "INFO",
      title: "hello",
      body: "world",
    });
    expect("targetUsername" in req).toBe(false);
    expect("actionUrl" in req).toBe(false);
  });

  it("includes targetUsername when audience is USER", () => {
    const req = buildTestRequest({
      ...base,
      audience: "USER",
      targetUsername: " kreb_fl ",
    });
    expect(req.targetUsername).toBe("kreb_fl");
  });

  it("includes actionUrl when non-blank", () => {
    const req = buildTestRequest({
      ...base,
      actionUrl: " https://x ",
    });
    expect(req.actionUrl).toBe("https://x");
  });
});

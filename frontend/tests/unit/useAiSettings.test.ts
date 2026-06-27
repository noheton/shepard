/**
 * PLACEHOLDER-ai-settings (2026-06-26) — proves `useAiSettings()` reads
 * `ai.baseUrl` and `ai.model` from the preferences bag and writes them back
 * via `patchPreferences`. Also verifies optimistic revert on error and that
 * clearing a field sends `null` (RFC 7396 remove).
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const flush = () => new Promise<void>((r) => setTimeout(r, 0));

const mockGetPreferences = vi.fn().mockResolvedValue({
  "ai.baseUrl": "https://api.example.com/v1",
  "ai.model": "gpt-4o",
  "ui.advancedMode": "false",
});
const mockPatchPreferences = vi.fn().mockResolvedValue({});

vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: () => ({
    value: {
      getPreferences: mockGetPreferences,
      patchPreferences: mockPatchPreferences,
    },
  }),
}));

// useAiSettings holds module-level singletons; re-import fresh each suite.
let useAiSettings: typeof import("~/composables/context/useAiSettings").useAiSettings;

describe("useAiSettings — PLACEHOLDER-ai-settings", () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    vi.resetModules();
    const mod = await import("~/composables/context/useAiSettings");
    useAiSettings = mod.useAiSettings;
  });

  it("loads baseUrl and model from preferences on first call", async () => {
    const { baseUrl, model } = useAiSettings();
    await flush();
    expect(mockGetPreferences).toHaveBeenCalledOnce();
    expect(baseUrl.value).toBe("https://api.example.com/v1");
    expect(model.value).toBe("gpt-4o");
  });

  it("does not re-fetch on second call (singleton guard)", async () => {
    useAiSettings();
    useAiSettings();
    await flush();
    expect(mockGetPreferences).toHaveBeenCalledOnce();
  });

  it("save() patches preferences with trimmed values", async () => {
    const { save } = useAiSettings();
    await flush();
    await save("  https://ollama.local/v1  ", "llama3");
    expect(mockPatchPreferences).toHaveBeenCalledWith({
      "ai.baseUrl": "https://ollama.local/v1",
      "ai.model": "llama3",
    });
  });

  it("save() sends null for empty fields (RFC 7396 remove)", async () => {
    const { save } = useAiSettings();
    await flush();
    await save("", "");
    expect(mockPatchPreferences).toHaveBeenCalledWith({
      "ai.baseUrl": null,
      "ai.model": null,
    });
  });

  it("save() reverts optimistic update on error", async () => {
    mockPatchPreferences.mockRejectedValueOnce(new Error("network error"));
    const { baseUrl, model, save } = useAiSettings();
    await flush();
    const originalBase = baseUrl.value;
    const originalModel = model.value;
    await save("https://bad.example.com", "bad-model");
    expect(baseUrl.value).toBe(originalBase);
    expect(model.value).toBe(originalModel);
  });

  it("falls back silently when getPreferences throws", async () => {
    mockGetPreferences.mockRejectedValueOnce(new Error("500 Internal Server Error"));
    const { baseUrl, model } = useAiSettings();
    await flush();
    expect(baseUrl.value).toBe("");
    expect(model.value).toBe("");
  });
});

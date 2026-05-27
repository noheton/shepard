import { describe, it, expect } from "vitest";
import { symbolicNameToSearchToken } from "../../utils/symbolicNameToSearchToken";

describe("symbolicNameToSearchToken", () => {
  it("compaction_force → force", () => {
    expect(symbolicNameToSearchToken("compaction_force")).toBe("force");
  });

  it("tcp_temperature → temperature", () => {
    expect(symbolicNameToSearchToken("tcp_temperature")).toBe("temperature");
  });

  it("turbopump_vibration_rms → vibration", () => {
    expect(symbolicNameToSearchToken("turbopump_vibration_rms")).toBe("vibration");
  });

  it("pressure → pressure (single token)", () => {
    expect(symbolicNameToSearchToken("pressure")).toBe("pressure");
  });

  it("X → x (single uppercase letter lowercased)", () => {
    expect(symbolicNameToSearchToken("X")).toBe("x");
  });

  it("camelCase: compactionForce → force", () => {
    expect(symbolicNameToSearchToken("compactionForce")).toBe("force");
  });

  it("empty string → empty string", () => {
    expect(symbolicNameToSearchToken("")).toBe("");
  });
});

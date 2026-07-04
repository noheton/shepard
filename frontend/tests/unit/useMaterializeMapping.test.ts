import { describe, it, expect } from "vitest";
import {
  buildMaterializeBody,
  materializePath,
} from "~/composables/useMaterializeMapping";

describe("buildMaterializeBody", () => {
  it("keeps non-blank appId bindings", () => {
    const out = buildMaterializeBody({
      srcFileAppId: "ref-1",
      urdfFileAppId: "ref-2",
    });
    expect(out.inputReferenceAppIds).toEqual({
      srcFileAppId: "ref-1",
      urdfFileAppId: "ref-2",
    });
  });

  it("drops blank / whitespace-only bindings (half-filled picker rows)", () => {
    const out = buildMaterializeBody({
      srcFileAppId: "ref-1",
      empty: "",
      spaces: "   ",
    });
    expect(out.inputReferenceAppIds).toEqual({ srcFileAppId: "ref-1" });
  });

  it("trims surrounding whitespace from appIds", () => {
    const out = buildMaterializeBody({ a: "  ref-1  " });
    expect(out.inputReferenceAppIds).toEqual({ a: "ref-1" });
  });

  it("returns an empty binding map for empty / nullish input", () => {
    expect(buildMaterializeBody({}).inputReferenceAppIds).toEqual({});
    // @ts-expect-error — exercise the nullish guard
    expect(buildMaterializeBody(undefined).inputReferenceAppIds).toEqual({});
  });
});

describe("materializePath", () => {
  it("builds the /v2/ materialize path", () => {
    expect(materializePath("tmpl-abc")).toBe(
      "/v2/mappings/tmpl-abc/materialize",
    );
  });

  it("url-encodes the template appId", () => {
    expect(materializePath("a/b c")).toBe(
      "/v2/mappings/a%2Fb%20c/materialize",
    );
  });
});

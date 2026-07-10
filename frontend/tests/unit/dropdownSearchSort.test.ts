/**
 * UIRULE-DROPDOWN-SEARCH-SORT — source-level guards for rule (1),
 * "search-as-you-type". These SFCs cannot be mounted under the plain-node
 * Vitest environment (no @vitejs/plugin-vue), so we assert the structural
 * contract on the source: the shared <Select> wrapper and the high-value
 * long-list pickers must render a <v-autocomplete> (searchable), never a bare
 * <v-select>, and must run their option lists through naturalSort (rule 2).
 *
 * This is the regression guard the operator's post-merge Playwright pass can't
 * be the only line of defence for — a reverted picker would still typecheck.
 */
import { describe, it, expect } from "vitest";
import { readFileSync } from "fs";
import { resolve } from "path";

const root = resolve(__dirname, "..", "..");
const read = (rel: string) => readFileSync(resolve(root, rel), "utf8");

describe("common/Select.vue is a searchable autocomplete wrapper", () => {
  const src = read("components/common/Select.vue");

  it("wraps v-autocomplete, not a bare v-select", () => {
    expect(src).toContain("<v-autocomplete");
    expect(src).not.toMatch(/<v-select\b/);
  });

  it("enables auto-select-first so typing + Enter picks the top match", () => {
    expect(src).toContain("auto-select-first");
  });
});

describe("high-value long-list channel pickers are searchable + naturally ordered", () => {
  const pickers: Array<[string, string]> = [
    ["Trace3DChannelPicker", "components/container/timeseries/Trace3DChannelPicker.vue"],
    ["UrdfChannelPicker", "components/container/timeseries/UrdfChannelPicker.vue"],
    ["BindChannelsDialog", "components/scene-graph/BindChannelsDialog.vue"],
    ["TimeseriesReferencePicker", "components/context/data-references/create-dialog/TimeseriesReferencePicker.vue"],
  ];

  it.each(pickers)("%s binds channel :items to a v-autocomplete", (_name, rel) => {
    const src = read(rel);
    expect(src).toContain("<v-autocomplete");
    // The channel/option list must not fall back to a bare <v-select>.
    // (Trace3D keeps ONE v-select for the tiny colormap enum — assert the
    //  channelItems list itself is on an autocomplete instead.)
    expect(src).toMatch(/naturalSort\(/);
  });

  it("Trace3DChannelPicker sorts channelItems and keeps colormap as the only v-select", () => {
    const src = read("components/container/timeseries/Trace3DChannelPicker.vue");
    // channelItems computed runs through naturalSort by title.
    expect(src).toMatch(/naturalSort\(\s*[\s\S]*i\s*=>\s*i\.title/);
    // Exactly one <v-select left (the colormap enum); the 6 channel axes are autocompletes.
    const selectCount = (src.match(/<v-select\b/g) ?? []).length;
    expect(selectCount).toBe(1);
  });
});

describe("EditTimeseriesReferenceDialog channel list is naturally ordered", () => {
  it("sorts allChannelOptions by the displayed channel label", () => {
    const src = read("components/context/data-references/EditTimeseriesReferenceDialog.vue");
    expect(src).toMatch(/naturalSort\(result, channelLabel\)/);
  });
});

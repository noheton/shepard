import { describe, it, expect } from "vitest";
import {
  buildSectionLandingCards,
  type SectionLandingCard,
} from "../../components/layout/sectionLanding";

const sampleCards: SectionLandingCard[] = [
  {
    fragment: "profile",
    icon: "mdi-account-outline",
    title: "Profile",
    description: "Your account info.",
  },
  {
    fragment: "api-keys",
    icon: "mdi-key-outline",
    title: "API Keys",
    description: "Personal access tokens.",
  },
];

describe("SectionIndexLanding helpers", () => {
  it("attaches a `to` shape with leading `#` to each card", () => {
    const out = buildSectionLandingCards(sampleCards);
    expect(out).toHaveLength(2);
    const first = out[0]!;
    const second = out[1]!;
    expect(first.to).toEqual({ hash: "#profile" });
    expect(second.to).toEqual({ hash: "#api-keys" });
  });

  it("preserves the original card fields (title, description, icon, fragment)", () => {
    const first = buildSectionLandingCards(sampleCards)[0]!;
    expect(first.fragment).toBe("profile");
    expect(first.icon).toBe("mdi-account-outline");
    expect(first.title).toBe("Profile");
    expect(first.description).toBe("Your account info.");
  });

  it("handles an empty section gracefully", () => {
    expect(buildSectionLandingCards([])).toEqual([]);
  });

  it("preserves an optional badge field when present", () => {
    const cardWithBadge: SectionLandingCard = {
      ...sampleCards[0]!,
      badge: "new",
    };
    const out = buildSectionLandingCards([cardWithBadge]);
    expect(out[0]!.badge).toBe("new");
  });

  it("does not invent a badge field when absent", () => {
    const first = buildSectionLandingCards(sampleCards)[0]!;
    expect(first.badge).toBeUndefined();
  });

  it("uses the same-path hash format MenuList.vue uses (does not invent full paths)", () => {
    // MenuList.vue uses `:to="{ hash: '#${item.fragment}' }"` — same-path nav.
    // Card clicks must follow the same shape so the active-button class logic
    // (which compares against route.hash.slice(1)) keeps working.
    const out = buildSectionLandingCards(sampleCards);
    for (const card of out) {
      expect(card.to.hash.startsWith("#")).toBe(true);
      expect(card.to).not.toHaveProperty("path");
      expect(card.to).not.toHaveProperty("name");
    }
  });
});

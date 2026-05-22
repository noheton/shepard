import { describe, it, expect, vi, beforeEach } from "vitest";
import { useAnimate, fadeUp, popIn, playStagger } from "~/composables/useAnimate";

/**
 * Tests for the WAAPI wrapper. JSDOM is not part of the vitest setup, so we
 * stub `Element` with bare objects carrying the only WAAPI methods our wrapper
 * touches: `animate`, `getAnimations`. This keeps the suite fast and free of
 * DOM-environment dependencies while still asserting the exact arguments
 * we pass down to `Element.animate(...)`.
 */

interface FakeAnimation {
  finished: Promise<unknown>;
  cancel: ReturnType<typeof vi.fn>;
}

interface FakeElement {
  animate: (keyframes: unknown, options: KeyframeAnimationOptions) => FakeAnimation;
  getAnimations: () => FakeAnimation[];
}

function makeElement(): { el: FakeElement; mockAnimate: ReturnType<typeof vi.fn> } {
  const mockAnimate = vi.fn(
    (_keyframes: unknown, _options: KeyframeAnimationOptions): FakeAnimation => ({
      finished: Promise.resolve(undefined),
      cancel: vi.fn(),
    }),
  );
  const el: FakeElement = {
    animate: mockAnimate,
    getAnimations: () => [],
  };
  return { el, mockAnimate };
}

function setReducedMotion(reduced: boolean) {
  // matchMedia is consulted once on useAnimate() construction.
  const win = globalThis as unknown as { window?: { matchMedia: unknown }; matchMedia?: unknown };
  const matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: reduced && query.includes("reduce"),
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }));
  win.window = { matchMedia };
  win.matchMedia = matchMedia;
}

beforeEach(() => {
  setReducedMotion(false);
});

describe("useAnimate", () => {
  it("calls element.animate with the supplied keyframes and merged options", async () => {
    const { el, mockAnimate } = makeElement();
    const { play } = useAnimate();
    await play(el as unknown as Element, fadeUp, { duration: 500, delay: 100 });
    expect(mockAnimate).toHaveBeenCalledTimes(1);
    const [keyframes, options] = mockAnimate.mock.calls[0];
    expect(keyframes).toEqual(fadeUp);
    expect(options.duration).toBe(500);
    expect(options.delay).toBe(100);
    expect(options.fill).toBe("both");
    expect(options.easing).toContain("cubic-bezier");
  });

  it("treats a number as the duration shorthand", async () => {
    const { el, mockAnimate } = makeElement();
    const { play } = useAnimate();
    await play(el as unknown as Element, popIn, 320);
    expect(mockAnimate.mock.calls[0][1].duration).toBe(320);
  });

  it("is a no-op when the element is null/undefined", async () => {
    const { mockAnimate } = makeElement();
    const { play } = useAnimate();
    await play(null, fadeUp);
    await play(undefined, fadeUp);
    expect(mockAnimate).not.toHaveBeenCalled();
  });

  it("returns null and never calls .animate when prefers-reduced-motion matches", async () => {
    setReducedMotion(true);
    const { el, mockAnimate } = makeElement();
    const { play, prefersReducedMotion } = useAnimate();
    expect(prefersReducedMotion.value).toBe(true);
    const result = await play(el as unknown as Element, fadeUp);
    expect(result).toBeNull();
    expect(mockAnimate).not.toHaveBeenCalled();
  });

  it("falls back gracefully when an element lacks .animate (older browsers)", async () => {
    const el = {} as unknown as Element;
    const { play } = useAnimate();
    const result = await play(el, fadeUp);
    expect(result).toBeNull();
  });
});

describe("playStagger", () => {
  it("animates each element with the configured stagger delay", async () => {
    const calls: KeyframeAnimationOptions[] = [];
    const els: FakeElement[] = [];
    for (let i = 0; i < 3; i++) {
      els.push({
        animate: (_kf: unknown, opts: KeyframeAnimationOptions) => {
          calls.push(opts);
          return { finished: Promise.resolve(undefined), cancel: vi.fn() };
        },
        getAnimations: () => [],
      });
    }
    await playStagger(els as unknown as Element[], fadeUp, {
      stagger: 100,
      delay: 50,
      duration: 400,
    });
    expect(calls).toHaveLength(3);
    expect(calls[0].delay).toBe(50);
    expect(calls[1].delay).toBe(150);
    expect(calls[2].delay).toBe(250);
    expect(calls.every((c) => c.duration === 400)).toBe(true);
  });

  it("skips entirely when reducedMotion=true", async () => {
    const { el, mockAnimate } = makeElement();
    const out = await playStagger([el as unknown as Element], fadeUp, { reducedMotion: true });
    expect(out).toEqual([]);
    expect(mockAnimate).not.toHaveBeenCalled();
  });

  it("ignores elements without .animate without throwing", async () => {
    const { el } = makeElement();
    const badEl = {} as unknown as Element;
    const out = await playStagger([el as unknown as Element, badEl, null], fadeUp);
    expect(out).toHaveLength(1);
  });
});

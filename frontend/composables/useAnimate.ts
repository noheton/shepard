/**
 * useAnimate — thin Vue 3 wrapper over the Web Animations API.
 *
 * Returns a `play(el, keyframes, options)` function and a reactive
 * `prefersReducedMotion` flag. When the user has opted into reduced motion
 * (system setting), `play()` is a no-op that resolves immediately so the
 * final visual state is correct without any animation.
 *
 * Reference: https://developer.mozilla.org/en-US/docs/Web/API/Web_Animations_API
 */

export type AnimateKeyframes = Keyframe[] | PropertyIndexedKeyframes;
export type AnimateOptions = number | (KeyframeAnimationOptions & { reducedMotion?: "skip" | "instant" });

const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";

export interface UseAnimateApi {
  /** Resolves once the animation finishes, or immediately when reduced-motion is on. */
  play: (el: Element | undefined | null, keyframes: AnimateKeyframes, options?: AnimateOptions) => Promise<Animation | null>;
  /** True when the OS / browser is set to prefer reduced motion. */
  prefersReducedMotion: Ref<boolean>;
  /** Cancel + remove all in-flight animations on the given element. */
  cancelAll: (el: Element | undefined | null) => void;
}

export function useAnimate(): UseAnimateApi {
  const prefersReducedMotion = ref<boolean>(false);

  if (typeof window !== "undefined" && "matchMedia" in window) {
    const mq = window.matchMedia(REDUCED_MOTION_QUERY);
    prefersReducedMotion.value = mq.matches;
    const onChange = (e: MediaQueryListEvent) => {
      prefersReducedMotion.value = e.matches;
    };
    mq.addEventListener?.("change", onChange);
    if (typeof onScopeDispose === "function") {
      onScopeDispose(() => mq.removeEventListener?.("change", onChange));
    }
  }

  function play(
    el: Element | undefined | null,
    keyframes: AnimateKeyframes,
    options?: AnimateOptions,
  ): Promise<Animation | null> {
    if (!el || typeof (el as HTMLElement).animate !== "function") {
      return Promise.resolve(null);
    }

    if (prefersReducedMotion.value) {
      return Promise.resolve(null);
    }

    const opts: KeyframeAnimationOptions =
      typeof options === "number" ? { duration: options } : { ...options };
    if (!opts.easing) opts.easing = "cubic-bezier(0.16, 1, 0.3, 1)";
    if (opts.fill == null) opts.fill = "both";

    const anim = (el as HTMLElement).animate(keyframes, opts);
    return anim.finished.then(() => anim).catch(() => anim);
  }

  function cancelAll(el: Element | undefined | null) {
    if (!el || typeof (el as HTMLElement).getAnimations !== "function") return;
    for (const a of (el as HTMLElement).getAnimations()) a.cancel();
  }

  return { play, prefersReducedMotion, cancelAll };
}

/* ---------- ready-to-use keyframe presets ---------- */

export const fadeUp: AnimateKeyframes = [
  { opacity: 0, transform: "translateY(16px)" },
  { opacity: 1, transform: "translateY(0)" },
];

export const fadeIn: AnimateKeyframes = [{ opacity: 0 }, { opacity: 1 }];

export const popIn: AnimateKeyframes = [
  { opacity: 0, transform: "scale(0.94)" },
  { opacity: 1, transform: "scale(1)" },
];

export const slideInFromLeft: AnimateKeyframes = [
  { opacity: 0, transform: "translateX(-24px)" },
  { opacity: 1, transform: "translateX(0)" },
];

/**
 * Apply the same keyframes to a list of elements with a stagger between each.
 *
 * Useful for cards-row or avatar-strip entrance.
 */
export function playStagger(
  els: Array<Element | undefined | null>,
  keyframes: AnimateKeyframes,
  options: KeyframeAnimationOptions & { stagger?: number; reducedMotion?: boolean } = {},
): Promise<Animation[]> {
  const stagger = options.stagger ?? 80;
  const { reducedMotion, ...kfOpts } = options;
  if (reducedMotion) return Promise.resolve([]);
  const tasks = els
    .filter((el): el is Element => !!el && typeof (el as HTMLElement).animate === "function")
    .map((el, i) => {
      const opts: KeyframeAnimationOptions = {
        duration: 480,
        easing: "cubic-bezier(0.16, 1, 0.3, 1)",
        fill: "both",
        ...kfOpts,
        delay: (kfOpts.delay ?? 0) + i * stagger,
      };
      const anim = (el as HTMLElement).animate(keyframes, opts);
      return anim.finished.then(() => anim);
    });
  return Promise.all(tasks);
}

/*
 * Shepard Pages WAAPI flair — lightweight Web Animations API enhancement.
 *
 * Goals:
 *   - Richer hero entrance (fade + lift + subtle scale) on the splash hero
 *   - Stagger any `.card-grid` or `.feature-row` children into view as they scroll in
 *   - Headline-number ticker on elements marked with `data-counter="42"`
 *   - Pulse highlight on link-targeted headings (clicking a TOC anchor)
 *
 * No build step. No deps. Plain JS, ES2018-compatible.
 * Respects `prefers-reduced-motion` — if matched, all calls are no-ops
 * and the static CSS final state is shown immediately.
 *
 * Reference: https://developer.mozilla.org/en-US/docs/Web/API/Web_Animations_API
 */
(function () {
  "use strict";

  const reduced = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const supportsAnimate = "animate" in document.createElement("div");
  if (!supportsAnimate) return;

  const EASE = "cubic-bezier(0.16, 1, 0.3, 1)";

  function play(el, keyframes, opts) {
    if (!el || reduced) return null;
    const options = Object.assign({ duration: 480, easing: EASE, fill: "both" }, opts || {});
    return el.animate(keyframes, options);
  }

  function fadeUp(el, opts) {
    return play(
      el,
      [
        { opacity: 0, transform: "translateY(18px)" },
        { opacity: 1, transform: "translateY(0)" },
      ],
      opts,
    );
  }

  function popIn(el, opts) {
    return play(
      el,
      [
        { opacity: 0, transform: "scale(0.96)" },
        { opacity: 1, transform: "scale(1)" },
      ],
      opts,
    );
  }

  function pulse(el, opts) {
    return play(
      el,
      [
        { backgroundColor: "rgba(36, 96, 169, 0.0)" },
        { backgroundColor: "rgba(36, 96, 169, 0.18)" },
        { backgroundColor: "rgba(36, 96, 169, 0.0)" },
      ],
      Object.assign({ duration: 1100, easing: "ease-out" }, opts || {}),
    );
  }

  /* ---------- hero entrance: rich stagger over the CSS baseline ---------- */
  function animateHero() {
    const hero = document.querySelector(".hero.splash");
    if (!hero || reduced) return;

    const eyebrow = hero.querySelector(".eyebrow");
    const h1 = hero.querySelector("h1");
    const lede = hero.querySelector(".lede");
    const cta = hero.querySelector(".hero-cta");

    fadeUp(eyebrow, { duration: 520, delay: 80 });
    fadeUp(h1, { duration: 700, delay: 140 });
    fadeUp(lede, { duration: 620, delay: 280 });
    popIn(cta, { duration: 560, delay: 440 });

    // very subtle "breathing" of the background image — opacity-only on the overlay
    const overlay = hero.querySelector(".overlay");
    if (overlay) {
      play(
        overlay,
        [{ opacity: 0.55 }, { opacity: 0.42 }, { opacity: 0.55 }],
        { duration: 9000, iterations: Infinity, easing: "ease-in-out" },
      );
    }
  }

  /* ---------- stagger any flagged section into view on scroll ---------- */
  function setupScrollStagger() {
    if (reduced || !("IntersectionObserver" in window)) return;
    const candidates = document.querySelectorAll("[data-anim='stagger']");
    if (!candidates.length) return;

    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          const parent = entry.target;
          const children = Array.from(parent.children);
          children.forEach((child, i) => {
            fadeUp(child, { duration: 520, delay: i * 100 });
          });
          io.unobserve(parent);
        });
      },
      { threshold: 0.18 },
    );

    candidates.forEach((el) => io.observe(el));
  }

  /* ---------- count-up tickers for headline numbers ---------- */
  function setupCounters() {
    if (reduced || !("IntersectionObserver" in window)) return;
    const els = document.querySelectorAll("[data-counter]");
    if (!els.length) return;

    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          const el = entry.target;
          const target = Number(el.getAttribute("data-counter")) || 0;
          const duration = Number(el.getAttribute("data-counter-duration")) || 1400;
          const decimals = Number(el.getAttribute("data-counter-decimals")) || 0;
          const start = performance.now();
          function tick(now) {
            const t = Math.min(1, (now - start) / duration);
            const eased = 1 - Math.pow(1 - t, 3);
            const value = target * eased;
            el.textContent = value.toFixed(decimals);
            if (t < 1) requestAnimationFrame(tick);
            else el.textContent = target.toFixed(decimals);
          }
          requestAnimationFrame(tick);
          io.unobserve(el);
        });
      },
      { threshold: 0.4 },
    );

    els.forEach((el) => io.observe(el));
  }

  /* ---------- anchor-link target pulse ---------- */
  function setupAnchorPulse() {
    function highlightFromHash() {
      const hash = window.location.hash;
      if (!hash || hash.length < 2) return;
      const id = decodeURIComponent(hash.slice(1));
      const target = document.getElementById(id);
      if (target) pulse(target);
    }
    window.addEventListener("hashchange", highlightFromHash);
    // also on initial load if the URL already carries a hash
    if (window.location.hash) setTimeout(highlightFromHash, 50);
  }

  function init() {
    animateHero();
    setupScrollStagger();
    setupCounters();
    setupAnchorPulse();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();

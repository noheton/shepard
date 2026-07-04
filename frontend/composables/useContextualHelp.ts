/**
 * Contextual help resolution — maps the current route to the most relevant
 * in-app help page (`/help?page=<key>`). Powers the header "?" affordance so a
 * researcher gets the doc for the screen they are on, not just the help index.
 *
 * `helpPageForPath` is a pure function (route path → doc page key) so it can be
 * unit-tested without a router. Returns `null` when no button should show
 * (e.g. on the /help page itself).
 */

interface HelpRule {
  test: RegExp;
  page: string;
}

// Ordered most-specific-first; first match wins.
const RULES: HelpRule[] = [
  { test: /^\/collections\/[^/]+\/dataobjects(\/|$)/, page: "reference/data-objects" },
  { test: /^\/collections(\/|$)/, page: "reference/collections" },
  { test: /^\/containers\/timeseries(\/|$)/, page: "reference/timeseries-reference" },
  { test: /^\/containers\/file(\/|$)/, page: "reference/file-reference" },
  { test: /^\/containers\/video(\/|$)/, page: "reference/video-stream-references" },
  { test: /^\/containers\/hdf(\/|$)/, page: "reference/containers" },
  { test: /^\/containers(\/|$)/, page: "reference/containers" },
  { test: /^\/projects(\/|$)/, page: "reference/projects" },
  { test: /^\/shapes(\/|$)/, page: "reference/view-recipes" },
  { test: /^\/tools(\/|$)/, page: "reference/view-recipes" },
  { test: /^\/semantic\/sparql(\/|$)/, page: "reference/semantic-repositories" },
  { test: /^\/semantic\/vocabularies(\/|$)/, page: "reference/semantic-annotations" },
  { test: /^\/semantic(\/|$)/, page: "reference/semantic-annotations" },
  { test: /^\/admin(\/|$)/, page: "admin" },
  { test: /^\/me(\/|$)/, page: "reference/user-profile" },
  { test: /^\/$/, page: "getting-started" },
];

/**
 * Resolve a route path to a help page key, or `null` to hide the affordance.
 * Unknown paths fall back to the help index ("index").
 */
export function helpPageForPath(path: string): string | null {
  // Strip query/hash and trailing slash (except root).
  const clean = (path.split(/[?#]/)[0] ?? path).replace(/(.)\/+$/, "$1");
  if (clean === "/help" || clean.startsWith("/help/")) return null;
  for (const rule of RULES) {
    if (rule.test.test(clean)) return rule.page;
  }
  return "index";
}

/** Reactive contextual help page key for the current route. */
export function useContextualHelp() {
  const route = useRoute();
  const helpPage = computed(() => helpPageForPath(route.path));
  return { helpPage };
}

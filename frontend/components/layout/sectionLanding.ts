/**
 * SectionIndexLanding helpers — pure functions, easy to unit-test without DOM.
 *
 * Used by SectionIndexLanding.vue (the friendly landing for /me, /admin,
 * /about, /configuration when no URL fragment is set).
 */

/**
 * One entry in a section landing grid. `fragment` is the URL hash that the
 * sub-pane reacts to (see useRouteFragment + the various FragmentEntries
 * enums under components/context/*\/...MenuItems.ts). `description` is a
 * one-liner shown under the card title.
 */
export interface SectionLandingCard {
  fragment: string;
  icon: string;
  title: string;
  description: string;
  badge?: string;
}

export interface RoutedSectionLandingCard extends SectionLandingCard {
  /** Vue Router target — same-path hash navigation, matches MenuList.vue. */
  to: { hash: string };
}

/**
 * Add the Vue Router `to` shape (`{ hash: '#<fragment>' }`) to each card so
 * the template can bind `<v-card :to="card.to">` directly.
 *
 * Pure function — exported so tests can verify the hash transform without
 * mounting the component.
 */
export function buildSectionLandingCards(
  cards: SectionLandingCard[],
): RoutedSectionLandingCard[] {
  return cards.map(card => ({
    ...card,
    to: { hash: `#${card.fragment}` },
  }));
}

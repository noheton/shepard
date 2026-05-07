package de.dlr.shepard.common.subscription.services;

import de.dlr.shepard.common.subscription.entities.Subscription;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Centralises the URL-pattern match used to decide whether a {@link Subscription} fires.
 *
 * <p>Subscriptions store their {@code subscribedURL} as a Java regular expression and a single
 * {@link java.util.regex.Pattern}{@code .matches()} call decides whether a given request URL
 * triggers the subscription. This helper lifts that one-line algorithm out of
 * {@code SubscriptionFilter} so other call sites — notably the RO-Crate export walker — can
 * reuse the exact same matcher without duplicating the regex semantics.
 *
 * <p>Behaviour is intentionally identical to the runtime filter: {@code Pattern.matches(...)}
 * over the full URL. A subscription whose pattern fails to compile is treated as non-matching
 * (instead of crashing the caller), matching the defensive posture the export needs.
 */
public final class SubscriptionMatcher {

  private SubscriptionMatcher() {}

  /**
   * @return {@code true} iff {@code subscription.getSubscribedURL()} compiles and the resulting
   *     pattern matches {@code url} in full (anchored, the same shape
   *     {@code SubscriptionFilter} uses).
   */
  public static boolean matches(Subscription subscription, String url) {
    if (subscription == null || url == null) return false;
    String regex = subscription.getSubscribedURL();
    if (regex == null || regex.isEmpty()) return false;
    try {
      return Pattern.compile(regex).matcher(url).matches();
    } catch (PatternSyntaxException e) {
      return false;
    }
  }

  /**
   * Returns those {@code subscriptions} whose URL pattern matches {@code url}, preserving input
   * iteration order. Never {@code null}.
   */
  public static List<Subscription> matchAll(Collection<Subscription> subscriptions, String url) {
    if (subscriptions == null || subscriptions.isEmpty() || url == null) return List.of();
    List<Subscription> hits = new ArrayList<>(subscriptions.size());
    for (Subscription sub : subscriptions) {
      if (matches(sub, url)) hits.add(sub);
    }
    return hits;
  }
}

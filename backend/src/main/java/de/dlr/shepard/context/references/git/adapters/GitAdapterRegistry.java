package de.dlr.shepard.context.references.git.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Optional;

/**
 * Dispatches per-host calls to the matching {@link GitAdapter}. v1 (G1b)
 * ships GitLab only; G1d adds GitHub + Gitea on the same interface.
 *
 * <p>For unknown hosts the registry returns {@link Optional#empty()};
 * the REST layer maps this to a 501 with RFC 7807 type
 * {@code git.adapter.unsupported-host}.
 */
@ApplicationScoped
public class GitAdapterRegistry {

  @Inject
  Instance<GitAdapter> adapters;

  /** Constructor for CDI. */
  public GitAdapterRegistry() {}

  /** Visible for testing. */
  GitAdapterRegistry(Instance<GitAdapter> adapters) {
    this.adapters = adapters;
  }

  /**
   * @param host hostname (no scheme, no path).
   * @return the first matching adapter, or empty when none claims the host.
   */
  public Optional<GitAdapter> findByHost(String host) {
    if (host == null || host.isBlank()) return Optional.empty();
    if (adapters == null) return Optional.empty();
    for (GitAdapter a : adapters) {
      if (a.supports(host)) return Optional.of(a);
    }
    return Optional.empty();
  }
}

package de.dlr.shepard.context.references.git.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Dispatches per-host calls to the matching {@link GitAdapter}. v1 (G1b)
 * shipped GitLab only; G1d adds GitHub + Gitea on the same interface.
 *
 * <p>For unknown hosts the registry returns {@link Optional#empty()};
 * the REST layer maps this to a 501 with RFC 7807 type
 * {@code git.adapter.unsupported-host}.
 *
 * <p>Adapters are consulted in {@link GitAdapter#priority()} ascending
 * order (lower = more specific = checked first) so a host like
 * {@code github.dlr.de} that is listed in
 * {@code shepard.git.adapter.github.hosts} routes to the GitHub
 * adapter before the GitLab substring matcher could ever (in principle)
 * claim it via a future config tweak.
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
    List<GitAdapter> sorted = new ArrayList<>();
    for (GitAdapter a : adapters) sorted.add(a);
    sorted.sort(Comparator.comparingInt(GitAdapter::priority));
    for (GitAdapter a : sorted) {
      if (a.supports(host)) return Optional.of(a);
    }
    return Optional.empty();
  }
}

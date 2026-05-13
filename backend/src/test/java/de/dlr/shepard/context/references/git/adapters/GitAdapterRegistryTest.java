package de.dlr.shepard.context.references.git.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.Instance;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class GitAdapterRegistryTest {

  /** Tiny CDI {@link Instance} stub that surfaces the supplied adapters. */
  private static Instance<GitAdapter> stub(GitAdapter... adapters) {
    @SuppressWarnings("unchecked")
    Instance<GitAdapter> i = (Instance<GitAdapter>) mock(Instance.class);
    when(i.iterator()).thenAnswer(inv -> Arrays.asList(adapters).iterator());
    return i;
  }

  @Test
  void findByHost_returnsFirstSupportingAdapter() {
    GitAdapter gitlab = mock(GitAdapter.class);
    when(gitlab.supports("gitlab.com")).thenReturn(true);
    GitAdapter github = mock(GitAdapter.class);
    when(github.supports("gitlab.com")).thenReturn(false);

    var reg = new GitAdapterRegistry(stub(github, gitlab));
    assertEquals(gitlab, reg.findByHost("gitlab.com").orElseThrow());
  }

  @Test
  void findByHost_returnsEmptyForUnsupportedHost() {
    GitAdapter gitlab = mock(GitAdapter.class);
    when(gitlab.supports("github.com")).thenReturn(false);
    var reg = new GitAdapterRegistry(stub(gitlab));
    assertTrue(reg.findByHost("github.com").isEmpty());
  }

  @Test
  void findByHost_blankHost_isEmpty() {
    var reg = new GitAdapterRegistry(stub());
    assertTrue(reg.findByHost("").isEmpty());
    assertTrue(reg.findByHost(null).isEmpty());
  }

  @Test
  void findByHost_emptyAdapterList_isEmpty() {
    @SuppressWarnings("unchecked")
    Instance<GitAdapter> i = (Instance<GitAdapter>) mock(Instance.class);
    when(i.iterator()).thenAnswer(inv -> Collections.<GitAdapter>emptyIterator());
    var reg = new GitAdapterRegistry(i);
    assertTrue(reg.findByHost("gitlab.com").isEmpty());
  }

  @Test
  void findByHost_prefersLowerPriorityWhenMultipleAdaptersClaimHost() {
    // Both adapters claim the host; the one with the lower priority wins
    // (more-specific → checked first). G1d: GitHub allowlist (priority 100)
    // beats GitLab fallback (priority 1000) for "*github*" hosts that an
    // operator added to the GitHub allowlist.
    GitAdapter specific = mock(GitAdapter.class);
    when(specific.supports("gitlab-github.example.com")).thenReturn(true);
    when(specific.priority()).thenReturn(100);
    GitAdapter fallback = mock(GitAdapter.class);
    when(fallback.supports("gitlab-github.example.com")).thenReturn(true);
    when(fallback.priority()).thenReturn(1000);

    var reg = new GitAdapterRegistry(stub(fallback, specific));
    assertEquals(specific, reg.findByHost("gitlab-github.example.com").orElseThrow());
  }

  @Test
  void findByHost_priorityOrderIsIndependentOfRegistrationOrder() {
    GitAdapter low = mock(GitAdapter.class);
    when(low.supports("h")).thenReturn(true);
    when(low.priority()).thenReturn(50);
    GitAdapter mid = mock(GitAdapter.class);
    when(mid.supports("h")).thenReturn(true);
    when(mid.priority()).thenReturn(500);

    var regA = new GitAdapterRegistry(stub(low, mid));
    var regB = new GitAdapterRegistry(stub(mid, low));
    assertEquals(low, regA.findByHost("h").orElseThrow());
    assertEquals(low, regB.findByHost("h").orElseThrow());
  }
}

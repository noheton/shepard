package de.dlr.shepard.context.references.git.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.Instance;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
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
}

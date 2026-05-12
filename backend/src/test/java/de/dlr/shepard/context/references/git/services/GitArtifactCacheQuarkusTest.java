package de.dlr.shepard.context.references.git.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.references.git.adapters.FileResolution;
import de.dlr.shepard.context.references.git.adapters.GitAdapter;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Quarkus-level test for the {@code @CacheResult(cacheName="git-artifacts")}
 * key composition. Verifies:
 * <ul>
 *   <li>Same {@code (userSub, repoUrl, ref, path)} → cached, second call
 *       does not re-invoke the adapter.</li>
 *   <li>Different {@code userSub} for the same tuple → cache miss (per-user
 *       partitioning that the design demands).</li>
 *   <li>Different {@code path} → cache miss.</li>
 * </ul>
 */
@QuarkusTest
class GitArtifactCacheQuarkusTest {

  @Inject
  GitArtifactCache cache;

  private GitAdapter newAdapter(byte[] content) {
    GitAdapter a = mock(GitAdapter.class);
    when(a.getFile(anyString(), anyString(), anyString(), anyString()))
      .thenReturn(new FileResolution("sha", content, "text/markdown", (long) content.length));
    return a;
  }

  @Test
  void sameKey_secondCallIsCacheHit() {
    GitAdapter a = newAdapter("hello".getBytes());
    FileResolution first = cache.getFile("alice", "https://gitlab.com/foo/bar", "main", "README.md", a, "PAT");
    FileResolution second = cache.getFile("alice", "https://gitlab.com/foo/bar", "main", "README.md", a, "PAT");
    // Same instance because the second call short-circuited via cache.
    assertSame(first, second);
    verify(a, times(1)).getFile(any(), any(), any(), any());
  }

  @Test
  void differentUser_isCacheMiss() {
    GitAdapter a = newAdapter("hello".getBytes());
    cache.getFile("alice", "https://gitlab.com/foo/bar", "main", "README.md", a, "PAT-A");
    cache.getFile("bob",   "https://gitlab.com/foo/bar", "main", "README.md", a, "PAT-B");
    verify(a, times(2)).getFile(any(), any(), any(), any());
  }

  @Test
  void differentPath_isCacheMiss() {
    GitAdapter a = newAdapter("x".getBytes());
    cache.getFile("alice", "https://gitlab.com/foo/bar", "main", "a.md", a, "PAT");
    cache.getFile("alice", "https://gitlab.com/foo/bar", "main", "b.md", a, "PAT");
    verify(a, times(2)).getFile(any(), any(), any(), any());
  }

  @Test
  void patNotPartOfKey_changingPatDoesNotInvalidate() {
    // The PAT changes between calls (user rotated), but the cache key is
    // (user, repoUrl, ref, path) — so the second call still hits the cache.
    // This is the spec's stated behaviour; PAT rotation is handled by the
    // PT5M TTL ageing out the old entry.
    GitAdapter a = newAdapter("h".getBytes());
    FileResolution first = cache.getFile("alice", "https://gitlab.com/foo/bar", "main", "p.md", a, "OLD");
    FileResolution second = cache.getFile("alice", "https://gitlab.com/foo/bar", "main", "p.md", a, "NEW");
    assertSame(first, second);
    verify(a, atLeastOnce()).getFile(any(), any(), any(), any());
  }
}

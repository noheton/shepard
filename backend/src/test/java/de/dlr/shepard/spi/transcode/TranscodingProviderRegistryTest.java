package de.dlr.shepard.spi.transcode;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageLocator;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * VID-FFMPEG-TRANSCODE-2026-06-29 — unit tests for
 * {@link TranscodingProviderRegistry}. Mirrors the shape of
 * {@code AnalyticsRegistryTest}: a hand-rolled {@code Instance<T>} shim, no
 * Quarkus container, no real ffmpeg.
 */
class TranscodingProviderRegistryTest {

  // ── fixtures ─────────────────────────────────────────────────────────────

  static class FakeProvider implements TranscodingProvider {
    private final String id;

    FakeProvider(String id) { this.id = id; }

    @Override
    public String id() { return id; }

    @Override
    public TranscodeResult transcode(TranscodeRequest request, FileStorage storage) throws StorageException {
      return TranscodeResult.ok(new StorageLocator("fake", "proxy-locator"));
    }
  }

  /** Hand-rolled Instance shim — same pattern as AnalyticsRegistryTest. */
  static class FakeInstance<T> implements Instance<T> {
    final List<T> items;

    FakeInstance(List<T> items) { this.items = items; }

    @Override public Iterator<T> iterator() { return items.iterator(); }
    @Override public T get() { return items.get(0); }
    @Override public Instance<T> select(Annotation... q) { return this; }
    @Override @SuppressWarnings("unchecked")
    public <U extends T> Instance<U> select(Class<U> s, Annotation... q) { return (Instance<U>) this; }
    @Override @SuppressWarnings("unchecked")
    public <U extends T> Instance<U> select(TypeLiteral<U> s, Annotation... q) { return (Instance<U>) this; }
    @Override public boolean isUnsatisfied() { return items.isEmpty(); }
    @Override public boolean isAmbiguous() { return false; }
    @Override public boolean isResolvable() { return items.size() == 1; }
    @Override public void destroy(T instance) { }
    @Override public Handle<T> getHandle() { throw new UnsupportedOperationException(); }
    @Override public Iterable<? extends Handle<T>> handles() { return java.util.Collections.emptyList(); }
  }

  // ── tests ────────────────────────────────────────────────────────────────

  @Test
  void resolve_discovers_and_indexes_by_id() {
    var a = new FakeProvider("ffmpeg-local");
    var b = new FakeProvider("ffmpeg-remote");
    var registry = new TranscodingProviderRegistry(new FakeInstance<>(List.of(a, b)), "ffmpeg-local");

    assertThat(registry.all()).hasSize(2);
    assertThat(registry.get("ffmpeg-local")).containsSame(a);
    assertThat(registry.get("ffmpeg-remote")).containsSame(b);
  }

  @Test
  void active_returns_the_configured_provider() {
    var local = new FakeProvider("ffmpeg-local");
    var remote = new FakeProvider("ffmpeg-remote");
    var registry = new TranscodingProviderRegistry(new FakeInstance<>(List.of(local, remote)), "ffmpeg-remote");

    assertThat(registry.active()).containsSame(remote);
    assertThat(registry.activeId()).isEqualTo("ffmpeg-remote");
  }

  @Test
  void active_returns_empty_when_configured_slot_is_unfilled() {
    var local = new FakeProvider("ffmpeg-local");
    var registry = new TranscodingProviderRegistry(new FakeInstance<>(List.of(local)), "nvenc-local");

    assertThat(registry.active()).isEmpty();
  }

  @Test
  void get_with_null_or_blank_falls_back_to_default_provider() {
    var local = new FakeProvider("ffmpeg-local");
    var registry = new TranscodingProviderRegistry(new FakeInstance<>(List.of(local)), "ffmpeg-local");

    assertThat(registry.get(null)).containsSame(local);
    assertThat(registry.get("")).containsSame(local);
    assertThat(registry.get("   ")).containsSame(local);
  }

  @Test
  void get_with_unknown_id_returns_empty() {
    var registry = new TranscodingProviderRegistry(
      new FakeInstance<>(List.of(new FakeProvider("ffmpeg-local"))),
      "ffmpeg-local"
    );
    assertThat(registry.get("vp9-local")).isEmpty();
  }

  @Test
  void duplicate_ids_keep_first_and_continue() {
    var first = new FakeProvider("dup");
    var second = new FakeProvider("dup");
    var registry = new TranscodingProviderRegistry(new FakeInstance<>(List.of(first, second)), "dup");

    assertThat(registry.all()).hasSize(1);
    assertThat(registry.get("dup")).containsSame(first);
  }

  @Test
  void blank_id_provider_is_skipped() {
    var bad = new FakeProvider("");
    var good = new FakeProvider("ffmpeg-local");
    var registry = new TranscodingProviderRegistry(new FakeInstance<>(List.of(bad, good)), "ffmpeg-local");

    assertThat(registry.all()).containsOnlyKeys("ffmpeg-local");
  }

  @Test
  void no_providers_resolves_to_empty_active() {
    var registry = new TranscodingProviderRegistry(new FakeInstance<>(List.of()), "ffmpeg-local");
    assertThat(registry.active()).isEmpty();
    assertThat(registry.all()).isEmpty();
  }

  @Test
  void transcode_request_defaults_factory_uses_recommended_knobs() {
    var loc = new StorageLocator("gridfs", "container:oid");
    var req = TranscodeRequest.defaults(loc, "videos", "clip_proxy.mp4");

    assertThat(req.videoBitrateKbps()).isEqualTo(TranscodeRequest.DEFAULT_BITRATE_KBPS);
    assertThat(req.maxHeight()).isEqualTo(TranscodeRequest.DEFAULT_MAX_HEIGHT);
    assertThat(req.timeoutSeconds()).isEqualTo(TranscodeRequest.DEFAULT_TIMEOUT_SECONDS);
  }

  @Test
  void transcode_result_helpers_distinguish_success_and_failure() {
    var ok = TranscodeResult.ok(new StorageLocator("gridfs", "abc:def"));
    var failed = TranscodeResult.failed("ffmpeg exit 1: ...");

    assertThat(ok.isSuccess()).isTrue();
    assertThat(ok.locator()).isNotNull();
    assertThat(ok.errorMessage()).isNull();
    assertThat(failed.isSuccess()).isFalse();
    assertThat(failed.locator()).isNull();
    assertThat(failed.errorMessage()).contains("exit 1");
  }
}

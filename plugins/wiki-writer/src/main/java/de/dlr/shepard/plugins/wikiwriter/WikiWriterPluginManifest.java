package de.dlr.shepard.plugins.wikiwriter;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import de.dlr.shepard.spi.ai.AiCapability;
import de.dlr.shepard.spi.ai.RequiresAiCapability;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * WW1 — Wiki-writer plugin manifest, discovered by
 * {@code de.dlr.shepard.plugin.PluginRegistry} at startup via the
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * file shipped alongside this class.
 *
 * <p>This plugin summarises a DataObject (and its Collection siblings)
 * using the {@link de.dlr.shepard.spi.ai.LlmProvider} SPI (TEXT
 * capability) and writes the result as a
 * {@link de.dlr.shepard.context.labJournal.entities.LabJournalEntry}
 * on the target DataObject.
 *
 * <p>{@code hardDep = false} — the plugin still activates when
 * {@code shepard-plugin-ai} is absent; the wiki-write endpoint returns
 * 503 at runtime when no provider is available.
 *
 * <p>No new Neo4j entities — {@link WikiWriterPayloadKind} declares
 * an empty {@code entityPackages()} list.
 */
@RequiresAiCapability(capability = AiCapability.TEXT, hardDep = false)
public final class WikiWriterPluginManifest implements PluginManifest {

  private static final String ID = "wiki-writer";

  private static final String VERSION = "0.1.0-SNAPSHOT";

  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";

  private static final String TITLE = "Wiki Writer";

  private static final String DESCRIPTION =
    "Wiki-writer plugin (WW1 v0). Uses the LlmProvider TEXT capability to summarise " +
    "a DataObject and its Collection siblings, writing the result as a " +
    "LabJournalEntry on the target DataObject. " +
    "Requires shepard-plugin-ai for TEXT capability; returns 503 when absent. " +
    "v0: local journal entry only — no external wiki integration.";

  private static final URI REPOSITORY = URI.create("https://github.com/noheton/shepard");

  private static final String LICENCE = "Apache-2.0";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String version() {
    return VERSION;
  }

  @Override
  public String shepardCompatibility() {
    return SHEPARD_COMPATIBILITY;
  }

  @Override
  public String title() {
    return TITLE;
  }

  @Override
  public String description() {
    return DESCRIPTION;
  }

  @Override
  public Optional<URI> repositoryUrl() {
    return Optional.of(REPOSITORY);
  }

  @Override
  public String licence() {
    return LICENCE;
  }

  @Override
  public void onRegister(PluginContext ctx) {
    Log.infof(
      "WW1: wiki-writer plugin v%s active via PluginManifest SPI (id=%s, compat=%s). " +
      "TEXT capability availability is checked per-request via Instance<LlmProvider>.",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("WW1: wiki-writer plugin onUnregister invoked");
  }
}

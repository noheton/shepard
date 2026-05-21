package de.dlr.shepard.plugins.wikiwriter;

import de.dlr.shepard.spi.payload.PayloadKind;
import java.util.List;

/**
 * WW1 — PayloadKind SPI implementation for the wiki-writer plugin.
 *
 * <p>This plugin introduces no new Neo4j-OGM entities, so
 * {@link #entityPackages()} returns an empty list. The SPI entry is
 * still declared (via {@code META-INF/services}) so that the backend's
 * {@code NeoConnector} ServiceLoader scan can discover and register it
 * without error.
 *
 * <p>The plugin ID {@code "wiki-writer"} matches
 * {@link WikiWriterPluginManifest#id()}.
 */
public final class WikiWriterPayloadKind implements PayloadKind {

  @Override
  public String name() {
    return "wiki-writer";
  }

  /**
   * No new Neo4j-OGM entity packages — the plugin writes to the existing
   * {@code LabJournalEntry} entity, which is already registered by the backend.
   */
  @Override
  public List<String> entityPackages() {
    return List.of();
  }
}

package de.dlr.shepard.v2.admin.plugins.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * PM1b — wrapper shape for {@code GET /v2/admin/plugins}.
 *
 * <p>Wraps the array under a {@code plugins} key so the response is
 * forward-compatible with adding sibling fields (count, pagination
 * cursor, server-side filter echoes, …) without breaking clients —
 * matching the {@code OntologyBundleListIO} idiom from N1c2.
 */
@Schema(name = "PluginList", description = "Envelope for GET /v2/admin/plugins.")
public record PluginListIO(
  @Schema(required = true, description = "Discovered plugins in registry insertion order.") List<PluginEntryIO> plugins
) {}

package de.dlr.shepard.plugins.aas.admin.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * AAS1-reg Commit 3 — response body for
 * {@code POST /v2/admin/aas/registrations/sync}.
 *
 * @param synced count of shells successfully registered in this invocation
 */
@Schema(name = "AasSyncResultIO", description = "Response body for POST /v2/admin/aas/registrations/sync — count of shells registered.")
public record AasSyncResultIO(int synced) {}

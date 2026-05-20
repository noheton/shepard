package de.dlr.shepard.plugins.aas.admin.io;

/**
 * AAS1-reg Commit 3 — response body for
 * {@code POST /v2/admin/aas/registrations/sync}.
 *
 * @param synced count of shells successfully registered in this invocation
 */
public record AasSyncResultIO(int synced) {}

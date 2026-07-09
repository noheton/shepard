package de.dlr.shepard.v2.admin.instance.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * FE-PROV-INSTANCE-REGISTRY — response shape for
 * {@code GET/PATCH /v2/admin/instances}.
 *
 * <p>Wraps a list of registered peer Shepard instances. An empty
 * {@link #instances()} list means no peer instances have been registered yet
 * (the default posture — operator opt-in).
 *
 * <p>RFC 7396 PATCH semantics: {@code instances} is an <em>atomic</em>
 * array field — sending {@code {"instances": []}} replaces the list with an
 * empty list; omitting {@code instances} entirely leaves the current list
 * unchanged. No element-level merge is performed; the entire list is
 * replaced atomically.
 */
@Schema(description = "Registry of peer Shepard instances for cross-instance provenance attribution.")
public record InstanceRegistryIO(
  List<RegisteredInstanceIO> instances
) {}

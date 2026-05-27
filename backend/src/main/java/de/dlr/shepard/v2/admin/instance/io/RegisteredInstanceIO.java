package de.dlr.shepard.v2.admin.instance.io;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * FE-PROV-INSTANCE-REGISTRY — a single registered peer Shepard instance.
 *
 * <p>Value type stored inside {@link InstanceRegistryIO#instances()}.
 * Also used as the element type when deserialising
 * {@link de.dlr.shepard.v2.admin.instance.entities.InstanceRegistry#getInstancesJson()}.
 *
 * <p>All fields are optional ({@code null} = not configured):
 * <ul>
 *   <li>{@link #instanceId} — stable short identifier, e.g. {@code "dlr-augsburg"}.
 *       Used as the key in the frontend's {@code registryMap}.</li>
 *   <li>{@link #displayName} — human-readable label, e.g. {@code "DLR BT, Augsburg"}.
 *       Shown in badge hover tooltips.</li>
 *   <li>{@link #baseUrl} — base URL of the remote instance, e.g.
 *       {@code "https://shepard-api.intra.dlr.de"}. Used by the frontend to
 *       construct deep-links to foreign DataObjects.</li>
 *   <li>{@link #dlrInstitute} — optional DLR institute code, e.g. {@code "BT"}.
 *       Not validated; purely informational.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegisteredInstanceIO(
  String instanceId,
  String displayName,
  String baseUrl,
  String dlrInstitute
) {}

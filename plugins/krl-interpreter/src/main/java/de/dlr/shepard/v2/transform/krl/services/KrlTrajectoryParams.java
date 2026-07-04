package de.dlr.shepard.v2.transform.krl.services;

import java.util.List;

/**
 * V2CONV-B5 — resolved input parameters for one KRL trajectory materialization.
 *
 * <p>Built by the {@link KrlTrajectoryTransformExecutor} from the MAPPING_RECIPE
 * template body + the materialize call's role-keyed input reference appIds, then
 * handed to {@link KrlTrajectoryService#interpret}. All identifiers are
 * {@code appId} strings (v2-only on the wire); the service resolves them to
 * substrate keys internally.
 *
 * @param templateAppId            appId of the driving MAPPING_RECIPE template
 *                                 (echoed onto the provenance Activity)
 * @param srcFileAppId             appId of the KRL .src/.krl FileReference (required)
 * @param urdfFileAppId            appId of the URDF FileReference (required)
 * @param targetDataObjectAppId    appId of the DataObject the derived
 *                                 TimeseriesReference attaches under (required)
 * @param timeseriesContainerAppId appId of the TimeseriesContainer the trajectory
 *                                 data points are written into (required)
 * @param datFileAppIds            optional companion .dat FileReference appIds
 */
public record KrlTrajectoryParams(
  String templateAppId,
  String srcFileAppId,
  String urdfFileAppId,
  String targetDataObjectAppId,
  String timeseriesContainerAppId,
  List<String> datFileAppIds
) {}

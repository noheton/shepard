package de.dlr.shepard.v2.transform.urscript.services;

/**
 * URSCRIPT-TRAJECTORY-1 — resolved input parameters for one URScript trajectory
 * materialization.
 *
 * <p>Built by the {@link de.dlr.shepard.v2.transform.urscript.UrScriptTrajectoryTransformExecutor}
 * from the MAPPING_RECIPE template body + the materialize call's role-keyed input
 * reference appIds, then handed to
 * {@link UrScriptTrajectoryService#interpret}. All identifiers are
 * {@code appId} strings (v2-only on the wire); the service resolves them to
 * substrate keys internally.
 *
 * <p>No companion {@code .dat} appIds — URScript programs carry all state in the
 * single {@code .urscript}/{@code .script} source file.
 *
 * @param templateAppId            appId of the driving MAPPING_RECIPE template
 *                                 (echoed onto the provenance Activity)
 * @param urscriptFileAppId        appId of the URScript .urscript/.script FileReference (required)
 * @param urdfFileAppId            appId of the URDF FileReference (required)
 * @param targetDataObjectAppId    appId of the DataObject the derived
 *                                 TimeseriesReference attaches under (required)
 * @param timeseriesContainerAppId appId of the TimeseriesContainer the trajectory
 *                                 data points are written into (required)
 */
public record UrScriptTrajectoryParams(
  String templateAppId,
  String urscriptFileAppId,
  String urdfFileAppId,
  String targetDataObjectAppId,
  String timeseriesContainerAppId
) {}

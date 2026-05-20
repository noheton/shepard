package de.dlr.shepard.plugins.video;

import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.spi.payload.PayloadKind;
import de.dlr.shepard.v2.video.model.VideoAnnotation;
import java.util.List;

/**
 * VID1b — registers the video plugin's Neo4j-OGM entity packages with
 * {@link de.dlr.shepard.common.neo4j.NeoConnector}.
 *
 * <p>Discovered via {@code META-INF/services/de.dlr.shepard.spi.payload.PayloadKind}
 * before CDI is up, mirroring the AAS plugin pattern.
 *
 * <p>The two packages registered are:
 * <ul>
 *   <li>{@code de.dlr.shepard.context.references.videostreamreference.model} —
 *       contains {@link VideoStreamReference} ({@code @NodeEntity})</li>
 *   <li>{@code de.dlr.shepard.v2.video.model} —
 *       contains {@link VideoAnnotation} ({@code @NodeEntity})</li>
 * </ul>
 */
public final class VideoPayloadKind implements PayloadKind {

  @Override
  public String name() {
    return "video";
  }

  @Override
  public List<String> entityPackages() {
    return List.of(
      VideoStreamReference.class.getPackageName(),
      VideoAnnotation.class.getPackageName()
    );
  }
}

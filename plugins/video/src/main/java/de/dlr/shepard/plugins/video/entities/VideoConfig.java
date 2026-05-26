package de.dlr.shepard.plugins.video.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * VID1c — runtime-mutable video-plugin config singleton.
 *
 * <p>Single-instance Neo4j node — mirrors the A3b / UH1a / N1c2 / AAS1c shapes
 * per the {@code CLAUDE.md} "Always: surface operator knobs in the admin config"
 * rule. One {@code :VideoConfig} node is seeded on first startup from the
 * {@code shepard.plugins.video.*} install-time defaults; subsequent runtime
 * PATCHes against {@code /v2/admin/video/config} mutate this node in place.
 *
 * <p>Field set — the runtime-mutable subset of video-plugin knobs:
 *
 * <ul>
 *   <li>{@link #ffprobeEnabled} — gates the {@code VideoProbeService.probe()}
 *       call on every upload. When {@code false}, metadata fields
 *       (duration, codec, resolution, frame rate) are left null but the
 *       upload succeeds. Default {@code true}.</li>
 *   <li>{@link #maxFileSizeMb} — optional upload-size cap in MiB. When
 *       {@code null} (default), uploads are unlimited (subject to the
 *       storage backend's own limits). When set, uploads exceeding the cap
 *       receive HTTP 413.</li>
 * </ul>
 *
 * <p><b>Precedence.</b> Runtime field values win for both;
 * deploy-time {@code shepard.plugins.video.*} properties are install
 * defaults that seed the singleton on first start. See the
 * {@code CLAUDE.md} admin-config rule.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class VideoConfig implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on save by
   * {@code GenericDAO.createOrUpdate}.
   */
  @Property("appId")
  private String appId;

  /**
   * Whether {@code ffprobe} metadata extraction runs on every upload.
   * When {@code false}, {@code VideoProbeService.probe()} is skipped
   * and all metadata fields ({@code durationSeconds}, {@code width},
   * {@code height}, {@code frameRate}, {@code videoCodec},
   * {@code audioCodec}, {@code wallClockTimestamp}) remain null.
   * The upload itself still succeeds.
   *
   * <p>Default: {@code true}.
   */
  @Property("ffprobeEnabled")
  private boolean ffprobeEnabled = true;

  /**
   * Maximum upload size in mebibytes (MiB). {@code null} means
   * no cap — uploads are bounded only by the storage backend's
   * own limits. When set to a positive value, uploads whose
   * {@code Content-Length} (or on-disk size) exceeds
   * {@code maxFileSizeMb × 1,048,576} bytes are rejected with
   * HTTP 413 before the bytes reach storage.
   *
   * <p>Default: {@code null} (unlimited).
   */
  @Property("maxFileSizeMb")
  private Long maxFileSizeMb;

  /** For testing purposes only. */
  public VideoConfig(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof VideoConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}

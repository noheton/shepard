package de.dlr.shepard.data.spatialdata.repositories;

import java.util.Map;

/**
 * Value object for one row in {@code shepard_spatial.profile}.
 *
 * <p>Maps to the v6 green-field hypertable defined in
 * {@code V2.0.0__green_field_schema.sql} (SPATIAL-V6-001).
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code profileKind} — one of: {@code point}, {@code line}, {@code polygon},
 *       {@code tin}, {@code multipoint}, {@code tube_centerline}
 *   <li>{@code profileWkt} — EWKT for the profile geometry; {@code null} only when
 *       {@code profileKind} is {@code 'point'} (degenerate single-point stream)
 *   <li>{@code anchorX/Y/Z} — POINTZ coordinates for the tool head/TCP/origin position
 * </ul>
 */
public record ProfileRow(
    long containerId,
    long time,
    String profileKind,
    double anchorX,
    double anchorY,
    double anchorZ,
    /** EWKT for profile geometry; null only when profileKind = 'point'. */
    String profileWkt,
    Map<String, Object> measurements,
    Map<String, Object> metadata,
    Map<String, Object> orientation
) {}

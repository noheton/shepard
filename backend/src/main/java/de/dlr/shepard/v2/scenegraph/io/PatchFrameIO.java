package de.dlr.shepard.v2.scenegraph.io;

/**
 * SCENEGRAPH-REST-1 — RFC 7396 merge-patch body for
 * {@code PATCH /v2/scene-graphs/{appId}/frames/{frameAppId}}.
 *
 * <p>All fields are nullable — only non-null fields are applied to
 * the existing {@link de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame}.
 * Translation and rotation are {@link Double} (boxed) so that a
 * missing field in JSON deserialises to {@code null} rather than
 * {@code 0.0}, preserving the existing value.
 */
public record PatchFrameIO(
  String name,
  String parentFrameAppId,
  Double x,
  Double y,
  Double z,
  Double rx,
  Double ry,
  Double rz
) {}

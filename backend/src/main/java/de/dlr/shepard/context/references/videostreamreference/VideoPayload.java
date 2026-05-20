package de.dlr.shepard.context.references.videostreamreference;

/**
 * VID1b — marker interface for Neo4j reference nodes that represent a video
 * payload kind.
 *
 * <p>Used by {@link de.dlr.shepard.context.collection.io.DataObjectIO} to
 * count video references without coupling the backend core to the plugin's
 * concrete {@code VideoStreamReference} class (which lives in
 * {@code shepard-plugin-video} once VID1b extraction is complete).
 *
 * <p>Any {@code @NodeEntity} that represents a video payload should implement
 * this interface.
 */
public interface VideoPayload {}

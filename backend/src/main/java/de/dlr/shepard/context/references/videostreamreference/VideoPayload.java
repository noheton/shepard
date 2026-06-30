package de.dlr.shepard.context.references.videostreamreference;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * VID1b — marker annotation for Neo4j reference nodes that represent a video
 * payload kind.
 *
 * <p>Used by {@link de.dlr.shepard.context.collection.io.DataObjectIO} to
 * count video references without coupling the backend core to the plugin's
 * concrete {@code VideoStreamReference} class (which lives in
 * {@code shepard-plugin-video} once VID1b extraction is complete).
 *
 * <p>Any {@code @NodeEntity} that represents a video payload should carry
 * this annotation. The backend detects video references via
 * {@code ref.getClass().isAnnotationPresent(VideoPayload.class)}.
 *
 * <p><b>Design note</b>: this is intentionally an annotation, not an
 * interface. {@code VideoStreamReference} lives in the video plugin, which
 * declares a {@code provided}-scope dependency on the backend. Quarkus's
 * {@code ClassTransformingBuildStep} augments the plugin via
 * {@code quarkus.index-dependency} using an augmentation classloader that
 * excludes {@code provided}-scope dependencies. If {@code VideoPayload} were
 * an interface, ASM's {@code ClassWriter.getCommonSuperClass} would try to
 * load it from that classloader and throw
 * {@code NoClassDefFoundError: VideoPayload}. An annotation type is stored as
 * a descriptor string in the class file's attributes table — it is never
 * loaded during frame computation, so the CRIT is avoided.
 * See CRIT-QUARKUS-CLASSTRANSFORM-VIDEOPAYLOAD-2026-06-30 in
 * {@code aidocs/16-dispatcher-backlog.md}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface VideoPayload {}

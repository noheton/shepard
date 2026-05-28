package de.dlr.shepard.plugin.fileformat.robotics;

/**
 * Canonical predicate IRIs emitted by the RoboDK {@code .rdk} tier-1
 * parser. Follow the {@code urn:shepard:<domain>:<role>} namespace
 * convention; this module owns {@code urn:shepard:rdk:*}.
 *
 * <p>Why this namespace (vs. {@code chameo:*} / {@code m4i:*} listed in
 * aidocs/integrations/110 §4.3): RDK-PARSE-1 in
 * {@code aidocs/16-dispatcher-backlog.md} pins the contract to
 * {@code shepard:rdk:*}, and URDF-WEBVIEW-1 already consumes
 * {@code urn:shepard:rdk:*} as the joint-mapping signal. The §4.3 table
 * is currently {@code stage: concept}; it is updated in this same PR
 * to reflect the chosen namespace.
 *
 * <p>Reference: aidocs/integrations/110 §4.3.
 */
public final class RdkAnnotations {

    private RdkAnnotations() {
        // utility class — no instances
    }

    /** Prefix shared by every predicate this parser emits. */
    public static final String RDK_PREDICATE_PREFIX = "urn:shepard:rdk:";

    /** RoboDK application version (e.g. {@code "5.5.3"}). Single-valued. */
    public static final String APP_VERSION = RDK_PREDICATE_PREFIX + "appVersion";

    /** Build platform string (e.g. {@code "WIN64"}). Single-valued. */
    public static final String PLATFORM = RDK_PREDICATE_PREFIX + "platform";

    /**
     * Robot-program source directory found via the
     * {@code VCP_SOURCE_DIRECTORY} marker (e.g.
     * {@code "D:/MFFD/RoboDK/Ply 1-15"}). Single-valued.
     */
    public static final String PROGRAM_SOURCE = RDK_PREDICATE_PREFIX + "programSource";

    /**
     * CAD model reference path (extension {@code .dae}). Multi-valued —
     * the writer is called once per distinct value.
     */
    public static final String CAD_REF = RDK_PREDICATE_PREFIX + "cadRef";

    /**
     * STEP geometry reference path (extension {@code .stp} /
     * {@code .step}). Multi-valued.
     */
    public static final String STEP_REF = RDK_PREDICATE_PREFIX + "stepRef";

    /**
     * RoboDK API endpoint (IPv4 or {@code IPv4:port} literal, e.g.
     * {@code "127.0.0.1"}). Single-valued; duplicate appearances
     * across the file are de-duplicated.
     */
    public static final String API_ENDPOINT = RDK_PREDICATE_PREFIX + "apiEndpoint";

    /**
     * Robot-driver identity (string ending in {@code "Driver"} and not
     * starting with {@code "VCP_"}; e.g. {@code "R20_MFZDriver"}).
     * Single-valued.
     */
    public static final String ROBOT_CONTROLLER = RDK_PREDICATE_PREFIX + "robotController";

    /**
     * Paired Spatial Analyzer {@code .xit} / {@code .xit64} file in the
     * same {@code FileContainer}. Value is the FileReference appId of
     * the sibling; single-valued. Emitted only when a sibling with the
     * same basename and an {@code .xit} or {@code .xit64} extension is
     * present.
     */
    public static final String COMPANION_SPATIAL_ANALYZER =
            RDK_PREDICATE_PREFIX + "companionSpatialAnalyzer";
}

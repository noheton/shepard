package de.dlr.shepard.plugin.fileformat.cad;

/**
 * Semantic annotation predicate IRIs emitted by the CAD file parser.
 *
 * <p>Namespace split:
 * <ul>
 *   <li>{@code urn:shepard:cad:*} — format-agnostic CAD metadata applicable to
 *       any CAD exchange format (STEP, 3DXML, JT, OBJ, …).</li>
 *   <li>{@code urn:shepard:mffd:cad:*} — MFFD / composite-manufacturing–specific
 *       metadata extracted from AP242 DATA sections (ply stacks, layup parameters,
 *       material definitions from the MFFD upper-shell campaign).</li>
 * </ul>
 */
public final class CadAnnotations {

  private CadAnnotations() {}

  // ── Generic CAD metadata ─────────────────────────────────────────────────

  /** Exchange format family: {@code step}, {@code 3dxml}, {@code jt}, {@code obj}. */
  public static final String FORMAT = "urn:shepard:cad:format";

  /** STEP schema identifier string from FILE_SCHEMA entity, e.g. {@code AP242_MANAGED_MODEL_BASED_3D_ENGINEERING_MIM_LF}. */
  public static final String STEP_SCHEMA = "urn:shepard:cad:step_schema";

  /** Product name extracted from PRODUCT entity (STEP) or {@code <Reference>} name (3DXML). */
  public static final String PRODUCT_NAME = "urn:shepard:cad:product_name";

  /** Authoring organisation from FILE_NAME entity (STEP) or {@code <AuthorAndDateCreated>} (3DXML). */
  public static final String ORGANISATION = "urn:shepard:cad:organisation";

  /** Originating author / owner from FILE_NAME entity. */
  public static final String AUTHOR = "urn:shepard:cad:author";

  /** Authoring application name, e.g. {@code CATIA V5} or {@code SolidWorks 2023}. */
  public static final String APPLICATION = "urn:shepard:cad:application";

  /** ISO 8601 creation timestamp from FILE_NAME / authoring metadata. */
  public static final String CREATED_AT = "urn:shepard:cad:created_at";

  /** STEP protocol description string from FILE_DESCRIPTION entity. */
  public static final String DESCRIPTION = "urn:shepard:cad:description";

  /** Coordinate unit detected (e.g. {@code mm}, {@code m}, {@code inch}). */
  public static final String UNIT = "urn:shepard:cad:unit";

  /** Vertex count for mesh formats (OBJ). */
  public static final String VERTEX_COUNT = "urn:shepard:cad:vertex_count";

  /** Face count for mesh formats (OBJ). */
  public static final String FACE_COUNT = "urn:shepard:cad:face_count";

  /** OBJ material library filename ({@code .mtl}). */
  public static final String MTL_LIBRARY = "urn:shepard:cad:mtl_library";

  // ── MFFD / AP242 composite-manufacturing metadata ────────────────────────

  /** Number of plies in the composite layup (AP242 {@code COMPOSITE_TEXT} or heuristic count). */
  public static final String PLY_COUNT = "urn:shepard:mffd:cad:ply_count";

  /** Dominant fibre material string (e.g. {@code CF/LMPAEK}). */
  public static final String MATERIAL = "urn:shepard:mffd:cad:material";

  /**
   * Comma-separated fibre angles extracted from AP242 entities
   * (e.g. {@code 0,45,-45,90}).
   */
  public static final String FIBRE_ANGLES = "urn:shepard:mffd:cad:fibre_angles";

  /** Detected CATIA product reference (3DXML product instance count, integer string). */
  public static final String CATIA_INSTANCE_COUNT = "urn:shepard:mffd:cad:catia_instance_count";
}

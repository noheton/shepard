package de.dlr.shepard.v2.shapes.seeder;

import de.dlr.shepard.spi.payload.PayloadKind;
import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import java.util.List;

/**
 * V2CONV-B7 proof-of-concept — a minimal in-tree {@link PayloadKind} whose
 * {@link #shapeDescriptor()} returns a real {@link ShapeSpec}. Exercises the
 * {@link KindShapeSeeder} pipeline end-to-end so the seeder is testable
 * without pulling in a plugin module.
 *
 * <p>This is NOT a production payload kind. It carries no entity packages
 * ({@link #entityPackages()} returns an empty list) and registers no Neo4j-OGM
 * model. Its sole purpose is to demonstrate — and test — that a {@link PayloadKind}
 * can declare its SHACL data-shape via {@link #shapeDescriptor()} and have
 * {@link KindShapeSeeder} compile + persist it at startup.
 *
 * <p>The shape declares two optional annotation predicates that a URDF
 * FileReference DataObject is expected to carry:
 * <ul>
 *   <li>{@code urn:shepard:urdf:package-path} — the mesh-resolution root path
 *       (used by {@code UrdfResolver} to resolve relative mesh URLs)</li>
 *   <li>{@code urn:shepard:urdf:role} — a controlled string identifying the
 *       URDF artefact's role (e.g. {@code "urdf"}, {@code "mesh-collection"})</li>
 * </ul>
 *
 * <p>Registered via
 * {@code META-INF/services/de.dlr.shepard.spi.payload.PayloadKind}.
 */
public final class UrdfKindShapeExample implements PayloadKind {

  static final String SHAPE_IRI = "urn:shepard:shape:urdf-data-object";
  static final String PRED_PACKAGE_PATH = "urn:shepard:urdf:package-path";
  static final String PRED_ROLE = "urn:shepard:urdf:role";
  static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";

  @Override
  public String name() {
    return "urdf-example";
  }

  @Override
  public List<String> entityPackages() {
    // Proof-of-concept: no Neo4j-OGM entities to register.
    return List.of();
  }

  /**
   * V2CONV-B7 — declares the SHACL NodeShape for URDF DataObjects. Open shape
   * (not {@code sh:closed}); both properties are optional ({@code sh:minCount 0}).
   */
  @Override
  public ShapeSpec shapeDescriptor() {
    return new ShapeSpec(
      SHAPE_IRI,
      null, // sh:targetClass: no explicit class target — shape is referenced via sh:node
      false, // open shape
      List.of(
        new PropertyShapeSpec(PRED_PACKAGE_PATH, XSD_STRING, 0, 1, null, null),
        new PropertyShapeSpec(PRED_ROLE, XSD_STRING, 0, 1, null, null)
      )
    );
  }
}

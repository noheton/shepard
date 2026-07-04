package de.dlr.shepard.v2.shapes.builder;

/**
 * One {@code sh:PropertyGroup} declaration — a form section (doc 125 §4.2
 * Layer 1: {@code sh:group} → {@code sh:PropertyGroup} with its own
 * {@code rdfs:label} + {@code sh:order}).
 *
 * <p>Property shapes reference the group via
 * {@link FormHintSpec#group()} = {@code iri}.
 *
 * @param iri   the group's IRI, e.g. {@code urn:btkvs:group:identity};
 *              must be non-blank when the group is declared
 * @param label human-readable section label ({@code rdfs:label}); nullable
 * @param order section order ({@code sh:order}); nullable
 */
public record GroupSpec(String iri, String label, Double order) {}

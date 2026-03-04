package de.dlr.shepard.common.neo4j.entities;

import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import java.util.List;

public interface Annotatable {
  List<SemanticAnnotation> getAnnotations();
  void addAnnotation(SemanticAnnotation annotation);
}

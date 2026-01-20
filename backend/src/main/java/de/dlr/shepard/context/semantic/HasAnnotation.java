package de.dlr.shepard.context.semantic;

import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import java.util.List;

public interface HasAnnotation {
  List<SemanticAnnotation> getAnnotations();
}

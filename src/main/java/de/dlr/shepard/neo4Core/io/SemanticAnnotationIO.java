package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.SemanticAnnotation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "SemanticAnnotation")
public class SemanticAnnotationIO extends AbstractEntityIO {

	@NotEmpty
	private String propertyIRI;

	@NotEmpty
	private String valueIRI;

	@NotNull
	private long propertyRepositoryId;

	@NotNull
	private long valueRepositoryId;

	public SemanticAnnotationIO(SemanticAnnotation ref) {
		super(ref);
		this.propertyIRI = ref.getPropertyIRI();
		this.valueIRI = ref.getValueIRI();
		this.propertyRepositoryId = ref.getPropertyRepository() != null ? ref.getPropertyRepository().getId() : -1;
		this.valueRepositoryId = ref.getValueRepository() != null ? ref.getValueRepository().getId() : -1;
	}

}

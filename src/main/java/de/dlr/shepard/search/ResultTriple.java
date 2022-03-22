package de.dlr.shepard.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResultTriple {

	private Long collectionId;
	private Long dataObjectId;
	private Long referenceId;

}

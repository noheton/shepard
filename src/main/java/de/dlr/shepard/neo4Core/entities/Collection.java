package de.dlr.shepard.neo4Core.entities;

import java.util.ArrayList;
import java.util.List;

import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import de.dlr.shepard.util.Constants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NodeEntity
@Getter
@Setter
@NoArgsConstructor
public class Collection extends AbstractDataObject {

	@Relationship(type = Constants.HAS_DATAOBJECT)
	private List<DataObject> dataObjects = new ArrayList<DataObject>();

	/**
	 * For testing purposes only
	 * 
	 * @param id identifies the entity
	 */
	public Collection(long id) {
		super(id);
	}

	/**
	 * Add one related DataObject
	 * 
	 * @param dataObject the dataObject to add
	 */
	public void addDataObject(DataObject dataObject) {
		dataObjects.add(dataObject);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + HasId.hashcodeHelper(dataObjects);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof Collection))
			return false;
		Collection other = (Collection) obj;
		return HasId.equalsHelper(dataObjects, other.dataObjects);
	}

}

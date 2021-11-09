package de.dlr.shepard.neo4Core.entities;

import java.util.ArrayList;
import java.util.List;

import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.HasId;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NodeEntity
@Data
@ToString(callSuper = true)
@NoArgsConstructor
public class DataObject extends AbstractDataObject {

	@Relationship(type = Constants.HAS_DATAOBJECT, direction = Relationship.INCOMING)
	private Collection collection;

	@Relationship(type = Constants.HAS_REFERENCE)
	private List<BasicReference> references = new ArrayList<BasicReference>();

	@Relationship(type = Constants.HAS_SUCCESSOR)
	private List<DataObject> successors = new ArrayList<DataObject>();

	@Relationship(type = Constants.HAS_SUCCESSOR, direction = Relationship.INCOMING)
	private List<DataObject> predecessors = new ArrayList<DataObject>();

	@Relationship(type = Constants.HAS_CHILD)
	private List<DataObject> children = new ArrayList<DataObject>();

	@Relationship(type = Constants.HAS_CHILD, direction = Relationship.INCOMING)
	private DataObject parent;

	@ToString.Exclude
	@Relationship(type = Constants.POINTS_TO, direction = Relationship.INCOMING)
	private List<DataObjectReference> incoming = new ArrayList<DataObjectReference>();

	/**
	 * For testing purposes only
	 *
	 * @param id identifies the entity
	 */
	public DataObject(long id) {
		super(id);
	}

	public void addReference(BasicReference reference) {
		references.add(reference);
	}

	public void addSuccessor(DataObject successor) {
		successors.add(successor);
	}

	public void addPredecessor(DataObject predecessor) {
		predecessors.add(predecessor);
	}

	public void addChild(DataObject child) {
		children.add(child);
	}

	public void addIncoming(DataObjectReference dataObjectReference) {
		incoming.add(dataObjectReference);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + HasId.hashcodeHelper(collection);
		result = prime * result + HasId.hashcodeHelper(references);
		result = prime * result + HasId.hashcodeHelper(successors);
		result = prime * result + HasId.hashcodeHelper(predecessors);
		result = prime * result + HasId.hashcodeHelper(children);
		result = prime * result + HasId.hashcodeHelper(parent);
		result = prime * result + HasId.hashcodeHelper(incoming);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof DataObject))
			return false;
		DataObject other = (DataObject) obj;
		return HasId.equalsHelper(collection, other.collection) && HasId.equalsHelper(references, other.references)
				&& HasId.equalsHelper(predecessors, other.predecessors)
				&& HasId.equalsHelper(successors, other.successors) && HasId.equalsHelper(parent, other.parent)
				&& HasId.equalsHelper(children, other.children) && HasId.equalsHelper(incoming, other.incoming);
	}
}

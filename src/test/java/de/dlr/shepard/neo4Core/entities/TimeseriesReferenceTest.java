package de.dlr.shepard.neo4Core.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.influxDB.Timeseries;
import nl.jqno.equalsverifier.EqualsVerifier;

public class TimeseriesReferenceTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(TimeseriesReference.class)
				.withPrefabValues(DataObject.class, new DataObject(1L), new DataObject(2L))
				.withPrefabValues(User.class, new User("bob"), new User("claus"))
				.withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
				.verify();
	}

	@Test
	public void addTimeseriesTest() {
		var ref = new TimeseriesReference(1L);
		var ts = new Timeseries("meas", "dev", "loc", "symname", "field");
		ref.addTimeseries(ts);

		assertEquals(List.of(ts), ref.getTimeseries());
	}

}

package de.dlr.shepard.influxDB;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;

public class TimeseriesServiceTest extends BaseTestCase {

	@Mock
	private InfluxConnector connector;

	@InjectMocks
	private TimeseriesService service;

	@Captor
	private ArgumentCaptor<String> databaseName;

	private Timeseries ts = new Timeseries("meas", "dev", "loc", "symName", "field");
	private TimeseriesPayload payload = new TimeseriesPayload(ts, List.of(new InfluxPoint(123L, "value")));
	private AggregateFunction function = AggregateFunction.MEAN;
	private Long groupBy = 10L;

	@Test
	public void createTimeseriesTest() {
		when(connector.saveTimeseries("database", payload)).thenReturn("");

		var actual = service.createTimeseries("database", payload);
		assertEquals("", actual);
	}

	@Test
	public void createDatabaseTest() {
		var actual = service.createDatabase();
		verify(connector).createDatabase(databaseName.capture());
		assertEquals(databaseName.getValue(), actual);
	}

	@Test
	public void getTimeseriesTest() {
		when(connector.getTimeseries(1, 2, "db", ts, function, groupBy)).thenReturn(payload);
		var actual = service.getTimeseries(1, 2, "db", ts, function, groupBy);
		assertEquals(payload, actual);
	}

	@Test
	public void getTimeseriesListTest_noFilter() {
		when(connector.getTimeseries(1, 2, "db", ts, function, groupBy)).thenReturn(payload);
		var actual = service.getTimeseriesList(1, 2, "db", List.of(ts), function, groupBy, Collections.emptySet(),
				Collections.emptySet(), Collections.emptySet());
		assertEquals(List.of(payload), actual);
	}

	@Test
	public void getTimeseriesListTest_allFilters() {
		when(connector.getTimeseries(1, 2, "db", ts, function, groupBy)).thenReturn(payload);
		var actual = service.getTimeseriesList(1, 2, "db", List.of(ts), function, groupBy, Set.of("dev"), Set.of("loc"),
				Set.of("symName"));
		assertEquals(List.of(payload), actual);
	}

	@Test
	public void getTimeseriesListTest_filterLoc() {
		when(connector.getTimeseries(1, 2, "db", ts, function, groupBy)).thenReturn(payload);
		var actual = service.getTimeseriesList(1, 2, "db", List.of(ts), function, groupBy, Collections.emptySet(),
				Set.of("loc"), Collections.emptySet());
		assertEquals(List.of(payload), actual);
	}

	@Test
	public void getTimeseriesListTest_filterDev() {
		when(connector.getTimeseries(1, 2, "db", ts, function, groupBy)).thenReturn(payload);
		var actual = service.getTimeseriesList(1, 2, "db", List.of(ts), function, groupBy, Set.of("dev"),
				Collections.emptySet(), Collections.emptySet());
		assertEquals(List.of(payload), actual);
	}

	@Test
	public void getTimeseriesListTest_filterName() {
		when(connector.getTimeseries(1, 2, "db", ts, function, groupBy)).thenReturn(payload);
		var actual = service.getTimeseriesList(1, 2, "db", List.of(ts), function, groupBy, Collections.emptySet(),
				Collections.emptySet(), Set.of("symName"));
		assertEquals(List.of(payload), actual);
	}

	@Test
	public void getTimeseriesListTest_nonMatchingLoc() {
		when(connector.getTimeseries(1, 2, "db", ts, function, groupBy)).thenReturn(payload);
		var actual = service.getTimeseriesList(1, 2, "db", List.of(ts), function, groupBy, Collections.emptySet(),
				Set.of("wrong"), Collections.emptySet());
		assertEquals(Collections.emptyList(), actual);
	}

	@Test
	public void getTimeseriesListTest_nonMatchingDev() {
		when(connector.getTimeseries(1, 2, "db", ts, function, groupBy)).thenReturn(payload);
		var actual = service.getTimeseriesList(1, 2, "db", List.of(ts), function, groupBy, Set.of("wrong"),
				Collections.emptySet(), Collections.emptySet());
		assertEquals(Collections.emptyList(), actual);
	}

	@Test
	public void getTimeseriesListTest_nonMatchingName() {
		when(connector.getTimeseries(1, 2, "db", ts, function, groupBy)).thenReturn(payload);
		var actual = service.getTimeseriesList(1, 2, "db", List.of(ts), function, groupBy, Collections.emptySet(),
				Collections.emptySet(), Set.of("wrong"));
		assertEquals(Collections.emptyList(), actual);
	}

	@Test
	public void deleteDatabaseTest() {
		service.deleteDatabase("database");
		verify(connector).deleteDatabase("database");
	}

}

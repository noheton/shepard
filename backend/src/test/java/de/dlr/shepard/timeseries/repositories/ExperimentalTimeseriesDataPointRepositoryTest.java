package de.dlr.shepard.timeseries.repositories;

import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPointEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ExperimentalTimeseriesDataPointRepositoryTest {

  private int timeseriesId = 1;

  @Inject
  ExperimentalTimeseriesDataPointRepositoryPlain repository;

  @BeforeAll
  public void init() {
    repository.deleteByTimeseries(timeseriesId);
  }

  @Disabled
  @Test
  public void insert_addDoublePayload_success() {
    var dataPoints = new ArrayList<ExperimentalTimeseriesDataPointEntity>();

    repository.insert(timeseriesId, dataPoints);
    var actual = repository.getByTimeseries(timeseriesId);

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(dataPoints, actual);
  }
}

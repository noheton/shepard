package de.dlr.shepard.common.neo4j.entities;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;

public enum ContainerType {
  FILE {
    public String getTypeAlias() {
      return Constants.FILECONTAINER_IN_QUERY;
    }

    public String getTypeName() {
      return FileContainer.class.getSimpleName();
    }
  },
  TIMESERIES {
    public String getTypeAlias() {
      return Constants.TIMESERIESCONTAINER_IN_QUERY;
    }

    public String getTypeName() {
      return TimeseriesContainer.class.getSimpleName();
    }
  },
  STRUCTUREDDATA {
    public String getTypeAlias() {
      return Constants.STRUCTUREDDATACONTAINER_IN_QUERY;
    }

    public String getTypeName() {
      return StructuredDataContainer.class.getSimpleName();
    }
  },
  BASIC {
    public String getTypeAlias() {
      return Constants.BASICCONTAINER_IN_QUERY;
    }

    public String getTypeName() {
      return BasicContainer.class.getSimpleName();
    }
  };

  public abstract String getTypeAlias();

  public abstract String getTypeName();
}

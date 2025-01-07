package de.dlr.shepard.common.mongoDB;

import java.io.InputStream;
import lombok.Value;

@Value
public class NamedInputStream {

  private String oid;

  private InputStream inputStream;

  private String name;

  private Long size;
}

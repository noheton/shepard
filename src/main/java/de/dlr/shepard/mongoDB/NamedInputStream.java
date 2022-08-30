package de.dlr.shepard.mongoDB;

import java.io.InputStream;

import lombok.Value;

@Value
public class NamedInputStream {

	private InputStream inputStream;

	private String name;

	private Long size;

}

package de.dlr.shepard.mongoDB;

import java.io.InputStream;

import lombok.Value;

@Value
public class NamedInputStream {

	public InputStream inputStream;

	public String name;

}

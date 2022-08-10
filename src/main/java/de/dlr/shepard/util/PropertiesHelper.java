package de.dlr.shepard.util;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PropertiesHelper {
	private final String PROPERTIES = "db.properties";

	private Properties propertiesFile = new Properties();
	private Map<String, String> environment = System.getenv();

	private void init() {
		log.info("Reading properties file at {}", PROPERTIES);
		try (var resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(PROPERTIES);) {
			propertiesFile.load(resourceAsStream);
		} catch (IOException e) {
			log.error("IOException while reading properties file");
		}
	}

	public String getProperty(String name) {
		// try environment first
		if (environment.containsKey(name))
			return environment.get(name);

		// fallback to properties file
		if (propertiesFile.isEmpty())
			init();

		// load property
		String property = propertiesFile.getProperty(name);
		if (property == null || property.isBlank()) {
			return "";
		}
		return property;
	}

}

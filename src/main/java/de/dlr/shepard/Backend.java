package de.dlr.shepard;

import java.util.Map;

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

public class Backend extends ResourceConfig {

	public Backend() {
		Map<String, Object> properties = Map.of("jersey.config.server.wadl.disableWadl", Boolean.TRUE);
		setProperties(properties);

		packages("de.dlr.shepard.endpoints");
		packages("de.dlr.shepard.filters");
		packages("de.dlr.shepard.exceptions");

		register(MultiPartFeature.class);
		register(RolesAllowedDynamicFeature.class);
	}

}

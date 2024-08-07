// Todo: Backend must be replaced by a quarkus equivalent
// package de.dlr.shepard;

// import org.glassfish.jersey.media.multipart.MultiPartFeature;
// import org.glassfish.jersey.server.ResourceConfig;
// import org.glassfish.jersey.server.ServerProperties;
// import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

// public class Backend extends ResourceConfig {

//   public Backend() {
//     property(ServerProperties.WADL_FEATURE_DISABLE, true);

//     packages("de.dlr.shepard.endpoints");
//     packages("de.dlr.shepard.filters");
//     packages("de.dlr.shepard.exceptions");

//     register(MultiPartFeature.class);
//     register(RolesAllowedDynamicFeature.class);
//   }
// }

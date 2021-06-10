package de.dlr.shepard.filters;

import java.io.IOException;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.apache.http.HttpStatus;

import de.dlr.shepard.exceptions.ApiError;
import de.dlr.shepard.exceptions.InvalidPathException;
import de.dlr.shepard.neo4Core.services.UrlPathChecker;
import lombok.extern.log4j.Log4j2;

@Provider
@Log4j2
public class UrlPathCheckerFilter implements ContainerRequestFilter {

	@Override
	public void filter(ContainerRequestContext requestContext) throws IOException {
		var urlPathChecker = getUrlPathChecker();

		try {
			urlPathChecker.checkPathSegments(requestContext.getUriInfo().getPathSegments());
		} catch (InvalidPathException e) {
			log.warn("Cought invalid path exception: {}", e.getMessage());
			var status = HttpStatus.SC_NOT_FOUND;
			requestContext.abortWith(Response.status(status)
					.entity(new ApiError(status, e.getClass().toString(), e.getMessage())).build());
		}

	}

	protected UrlPathChecker getUrlPathChecker() {
		return new UrlPathChecker();
	}

}

package de.dlr.shepard.util;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;

public class PKIHelperTest extends BaseTestCase {

	@Mock
	private File keysDir;

	@InjectMocks
	private PKIHelper pkiHelper;

	@Test
	public void testInit() {
		// TODO
	}

}

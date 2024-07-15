package de.dlr.shepard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockitoAnnotations;

public abstract class BaseTestCase {

	private AutoCloseable mocks;

	@BeforeEach
	public void setUp() {
		mocks = MockitoAnnotations.openMocks(this);

	}

	@AfterEach
	public void tearDown() throws Exception {
		mocks.close();
	}
}

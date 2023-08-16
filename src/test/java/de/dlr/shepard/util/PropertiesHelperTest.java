package de.dlr.shepard.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;

public class PropertiesHelperTest extends BaseTestCase {

	@Mock
	private Properties propertiesFile;

	@Mock
	private Map<String, String> environment;

	@InjectMocks
	private PropertiesHelper helper;

	@Test
	public void testGetPropertySuccessful_alreadyInitialized() throws IOException {
		doNothing().when(propertiesFile).load(any(BufferedInputStream.class));
		when(propertiesFile.getProperty("my.key")).thenReturn("MyValue");
		when(propertiesFile.isEmpty()).thenReturn(true);

		String result = helper.getProperty("my.key");
		assertEquals("MyValue", result);
	}

	@Test
	public void testGetEnvironment() throws IOException {
		when(environment.containsKey("MY_KEY")).thenReturn(true);
		when(environment.get("MY_KEY")).thenReturn("myValue");
		String resultString = helper.getProperty("my.key");

		assertEquals("myValue", resultString);
		verify(propertiesFile, never()).getProperty(any());
		verify(propertiesFile, never()).load(any(BufferedInputStream.class));
	}

	@Test
	public void testGetPropertySuccessful() throws IOException {
		when(propertiesFile.getProperty("my.key")).thenReturn("MyValue");
		when(propertiesFile.isEmpty()).thenReturn(false);

		String result = helper.getProperty("my.key");
		assertEquals("MyValue", result);
		verify(environment, never()).get(any());
		verify(propertiesFile, never()).load(any(BufferedInputStream.class));
	}

	@Test
	public void testGetProperty_withWrongProperty() throws IOException {
		when(propertiesFile.isEmpty()).thenReturn(false);

		String result = helper.getProperty("my.key");
		assertEquals("", result);
	}

	@Test
	public void testGetProperty_withEmptyProperty() throws IOException {
		when(propertiesFile.isEmpty()).thenReturn(false);
		when(propertiesFile.getProperty("my.key")).thenReturn("");

		String result = helper.getProperty("my.key");
		assertEquals("", result);
	}

	@Test
	public void testGetProperty_expectIOException() throws IOException {
		doThrow(IOException.class).when(propertiesFile).load(any(BufferedInputStream.class));
		when(propertiesFile.isEmpty()).thenReturn(true);

		String result = helper.getProperty("my.key");
		assertEquals("", result);
	}

}

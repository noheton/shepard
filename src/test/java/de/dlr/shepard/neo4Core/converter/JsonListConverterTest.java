package de.dlr.shepard.neo4Core.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Spy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.dlr.shepard.BaseTestCase;

public class JsonListConverterTest extends BaseTestCase {

	private class IntegerConverter extends JsonListConverter<Integer> {

		@Override
		Class<Integer> getEntityType() {
			return Integer.class;
		}

	}

	@Spy
	private ObjectMapper mapper;

	@InjectMocks
	private IntegerConverter converter = new IntegerConverter();

	@Test
	public void toGraphPropertyTest() {
		List<Integer> list = List.of(1, 2, 3);
		var actual = converter.toGraphProperty(list);
		var expected = List.of("1", "2", "3");

		assertEquals(expected, actual);
	}

	@Test
	public void toGraphPropertyTest_Exception() throws JsonProcessingException {
		List<Integer> list = List.of(1, 2, 3);

		when(mapper.writeValueAsString(Integer.valueOf(2))).thenThrow(new JsonProcessingException("fake exception") {
			private static final long serialVersionUID = 1L;
		});
		var actual = converter.toGraphProperty(list);
		var expected = List.of("1", "3");

		assertEquals(expected, actual);
	}

	@Test
	public void toEntityAttribute() {
		List<String> list = List.of("1", "2", "3");
		var actual = converter.toEntityAttribute(list);
		var expected = List.of(1, 2, 3);

		assertEquals(expected, actual);
	}

	@Test
	public void toEntityAttribute_Exception() throws JsonMappingException, JsonProcessingException {
		List<String> list = List.of("1", "ads", "3");

		when(mapper.readValue("3", Integer.class)).thenThrow(new JsonProcessingException("fake exception") {
			private static final long serialVersionUID = 1L;
		});

		var actual = converter.toEntityAttribute(list);
		var expected = List.of(1);

		assertEquals(expected, actual);
	}

}

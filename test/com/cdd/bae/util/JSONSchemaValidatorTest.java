/*
	BioAssay Express (BAE)

	Copyright 2016-2023 Collaborative Drug Discovery, Inc.

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/

package com.cdd.bae.util;

import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.json.*;
import org.junit.jupiter.api.*;

public class JSONSchemaValidatorTest
{
	private String configSchema = "/com/cdd/bae/config/ConfigurationSchema.json";
	
	private TestResourceFile validConfig = new TestResourceFile("/testData/config/validConfig.json");
	private TestResourceFile invalidConfig = new TestResourceFile("/testData/config/invalidConfig.json");
	private TestResourceFile testSchema = new TestResourceFile("/testData/config/schema.json");

	@Test
	public void testFromResource() throws IOException, JSONSchemaValidatorException
	{
		JSONSchemaValidator validator = JSONSchemaValidator.fromResource(configSchema);
		JSONObject json = new JSONObject(validConfig.getContent());
		List<String> errors = validator.validate(json);
		assertThat(errors, empty());
		
		// exceptions
		JSONSchemaValidatorException e = assertThrows(JSONSchemaValidatorException.class,
				() -> JSONSchemaValidator.fromResource("/invalid/resource.json"));
		assertThat(e.getMessage(), is("Error loading configuration schema"));
		assertThat(e.getDetails(), hasItem("Cannot read configuration schema definition file"));

		e = assertThrows(JSONSchemaValidatorException.class,
				() -> JSONSchemaValidator.fromResource("/testData/config/test.txt"));
		assertThat(e.getMessage(), is("Error loading configuration schema"));
		assertThat(e.getDetails(), hasItem("Cannot parse JSON from configuration schema"));
	}

	@Test
	public void testJsonSchemaValidatorErrors() throws IOException
	{
		JSONObject schema = new JSONObject(testSchema.getContent());
		JSONSchemaValidator validator = new JSONSchemaValidator(schema);

		JSONObject json = new JSONObject(invalidConfig.getContent());
		List<String> errors = validator.validate(json);
		// Extract the levels from the errors
		Set<String> levels = new HashSet<>();
		for (String error : errors)
			levels.add(error.split(" ")[0]);

		assertThat("Missing required property", levels, hasItem("/schema"));
		assertThat("Missing required entity", levels, hasItem("/database/requiredMissing"));
		assertThat("Missing required entity but have default", levels, not(hasItem("/database/requiredDefault")));
		assertThat("Missing array", levels, hasItem("/database/arrayMissing"));
		assertThat("Missing required array", levels, hasItem("/database/requiredArrayMissing"));
		assertThat("Incorrect pattern", levels, hasItem("/database/incorrectPattern"));
		assertThat("Invalid integer", levels, hasItem("/database/invalidInteger"));
		assertThat("Invalid number", levels, hasItem("/database/invalidNumber"));
		assertThat("Invalid boolean", levels, hasItem("/pages/randomAssay"));

		assertEquals(8, errors.size(), "Expected number of errors from invalidConfig");
	}

	@Test
	public void testExceptionMissingRootType() throws IOException
	{
		Map<String, Object> m = new HashMap<>();
		m.put("type", "incorrectRootType");
		JSONSchemaValidator validator = new JSONSchemaValidator(new JSONObject(m));
		JSONObject json = new JSONObject();
		Exception e = assertThrows(IllegalStateException.class, 
				() -> validator.validate(json));
		assertThat(e.getMessage(), startsWith("Root type of schema"));
		
		JSONArray jarr = new JSONArray();
		e = assertThrows(IllegalStateException.class, 
				() -> validator.validate(jarr));
		assertThat(e.getMessage(), startsWith("Root type of schema"));
	}
		
	@Test
	public void testExceptionUnknownChildType() throws IOException
	{
		Map<String, JSONObject> properties = new HashMap<>();
		properties.put("value", makeNumberConstraint());
		properties.get("value").put("type", "new type");
		JSONSchemaValidator validator = new JSONSchemaValidator(makeObjectSchema(properties));

		Map<String, Object> entries = new HashMap<>();
		entries.put("value", 10);

		Exception e = assertThrows(IllegalStateException.class, 
				() -> validator.validate(new JSONObject(entries)));
		assertThat(e.getMessage(), containsString("new type"));
	}
	
	@Test
	public void testExceptionMissingFormat() throws IOException
	{
		Map<String, JSONObject> properties = new HashMap<>();
		properties.put("value", makeStringConstraint());
		properties.get("value").put("format", "unknown format");
		JSONSchemaValidator validator = new JSONSchemaValidator(makeObjectSchema(properties));

		Map<String, Object> entries = new HashMap<>();
		entries.put("value", "abc");

		Exception e = assertThrows(IllegalStateException.class, 
				() -> validator.validate(new JSONObject(entries)));
		assertThat(e.getMessage(), containsString("unknown format"));
	}

	@Test
	public void testIntegerField()
	{
		Map<String, JSONObject> properties = new HashMap<>();
		properties.put("value", makeIntConstraint(-1, 10));
		JSONSchemaValidator validator = new JSONSchemaValidator(makeObjectSchema(properties));

		// valid
		Map<String, Object> entries = new HashMap<>();
		entries.put("value", 10);
		assertThat(validator.validate(new JSONObject(entries)), empty());

		entries.put("value", -1);
		assertThat(validator.validate(new JSONObject(entries)), empty());

		entries.put("value", 1);
		assertThat(validator.validate(new JSONObject(entries)), empty());

		// invalid
		entries.put("value", 1.234);
		assertThat(validator.validate(new JSONObject(entries)), hasSize(1));

		entries.put("value", "abc");
		assertThat(validator.validate(new JSONObject(entries)), hasSize(1));

		entries.put("value", -2);
		assertThat(validator.validate(new JSONObject(entries)), hasSize(1));

		entries.put("value", 11);
		assertThat(validator.validate(new JSONObject(entries)), hasSize(1));
	}

	@Test
	public void testNumberField()
	{
		Map<String, JSONObject> properties = new HashMap<>();
		properties.put("value", makeNumberConstraint());
		JSONSchemaValidator validator = new JSONSchemaValidator(makeObjectSchema(properties));

		// valid
		Map<String, Object> entries = new HashMap<>();
		entries.put("value", 10);
		assertThat(validator.validate(new JSONObject(entries)), empty());

		entries.put("value", -1);
		assertThat(validator.validate(new JSONObject(entries)), empty());

		entries.put("value", 1.234);
		assertThat(validator.validate(new JSONObject(entries)), empty());

		entries.put("value", -1.234);
		assertThat(validator.validate(new JSONObject(entries)), empty());

		// invalid
		entries.put("value", "abc");
		assertThat(validator.validate(new JSONObject(entries)), hasSize(1));
	}

	@Test
	public void testBooleanField()
	{
		Map<String, JSONObject> properties = new HashMap<>();
		properties.put("value", makeBooleanConstraint());
		JSONSchemaValidator validator = new JSONSchemaValidator(makeObjectSchema(properties));

		// valid
		Map<String, Object> entries = new HashMap<>();
		entries.put("value", true);
		assertThat(validator.validate(new JSONObject(entries)), empty());

		entries.put("value", false);
		assertThat(validator.validate(new JSONObject(entries)), empty());

		// invalid
		entries.put("value", 1.234);
		assertThat(validator.validate(new JSONObject(entries)), hasSize(1));

		entries.put("value", "abc");
		assertThat(validator.validate(new JSONObject(entries)), hasSize(1));
	}

	@Test
	public void testStringField()
	{
		Map<String, JSONObject> properties = new HashMap<>();
		properties.put("value", makeStringConstraint());
		JSONSchemaValidator validator = new JSONSchemaValidator(makeObjectSchema(properties));

		// valid
		Map<String, Object> entries = new HashMap<>();
		entries.put("value", "10");
		assertThat(validator.validate(new JSONObject(entries)), empty());

		// invalid
		entries.put("value", 10);
		assertThat(validator.validate(new JSONObject(entries)), hasSize(1));

		// test required
		JSONObject constraint = makeStringConstraint();
		constraint.put("required", true);
		properties.put("value", constraint);
		validator = new JSONSchemaValidator(makeObjectSchema(properties));

		entries.put("value", "10");
		assertThat(validator.validate(new JSONObject(entries)), empty());
		entries.put("value", null);
		assertThat(validator.validate(new JSONObject(entries)), hasSize(1));
		entries.remove("value");
		assertThat(validator.validate(new JSONObject(entries)), hasSize(1));

		// test required and default
		constraint = makeStringConstraint();
		constraint.put("required", true);
		constraint.put("default", "abc");
		properties.put("value", constraint);
		validator = new JSONSchemaValidator(makeObjectSchema(properties));

		entries.put("value", "10");
		assertThat(validator.validate(new JSONObject(entries)), empty());
		entries.put("value", null);
		assertThat(validator.validate(new JSONObject(entries)), empty());
		entries.remove("value");
		assertThat(validator.validate(new JSONObject(entries)), empty());
	}
	
	@Test
	public void testDateField()
	{
		Map<String, JSONObject> properties = new HashMap<>();
		properties.put("value", makeStringConstraint("format", "date"));
		JSONSchemaValidator validator = new JSONSchemaValidator(makeObjectSchema(properties));

		// valid
		Map<String, Object> entries = new HashMap<>();
		entries.put("value", "2019-12-24");
		assertThat(validator.validate(new JSONObject(entries)), empty());

		// invalid
		entries.put("value", "not a date");
		assertThat(validator.validate(new JSONObject(entries)), hasSize(1));

		entries.put("value", "24-12-2019");
		assertThat(validator.validate(new JSONObject(entries)), hasSize(1));
	}

	@Test
	public void testStringURIField()
	{
		Map<String, JSONObject> properties = new HashMap<>();
		properties.put("value", makeStringConstraint("format", "uri"));
		JSONSchemaValidator validator = new JSONSchemaValidator(makeObjectSchema(properties));

		// valid
		Map<String, Object> entries = new HashMap<>();
		entries.put("value", "https://sandbox.orcid.org/oauth/authorize");
		assertThat(validator.validate(new JSONObject(entries)), empty());

		entries.put("value", "http://sandbox.orcid.org/oauth/authorize");
		assertThat(validator.validate(new JSONObject(entries)), empty());

		entries.put("value", "http://localhost:8080/");
		assertThat(validator.validate(new JSONObject(entries)), empty());

		entries.put("value", "https://alpha.bioassayexpress.com/");
		assertThat(validator.validate(new JSONObject(entries)), empty());

		// invalid
		entries.put("value", "not a uri");
		assertThat(validator.validate(new JSONObject(entries)), hasSize(1));

		entries.put("value", "file://alpha/bioassayexpress.com");
		assertThat(validator.validate(new JSONObject(entries)), hasSize(1));
	}

	@Test
	public void testStringURLField()
	{
		Map<String, JSONObject> properties = new HashMap<>();
		properties.put("value", makeStringConstraint("format", "url"));
		JSONSchemaValidator validator = new JSONSchemaValidator(makeObjectSchema(properties));

		// valid
		Map<String, Object> entries = new HashMap<>();
		entries.put("value", "http://localhost:8080/");
		assertThat(validator.validate(new JSONObject(entries)), empty());

		entries.put("value", "https://alpha.bioassayexpress.com/");
		assertThat(validator.validate(new JSONObject(entries)), empty());

		entries.put("value", "file://alpha/bioassayexpress.com");
		assertThat(validator.validate(new JSONObject(entries)), empty());

		entries.put("value", "ftp://alpha.bioassayexpress.com/a/b.txt");
		assertThat(validator.validate(new JSONObject(entries)), empty());
	}

	@Test
	public void testStringPatternField()
	{
		Map<String, JSONObject> properties = new HashMap<>();
		properties.put("value", makeStringConstraint("pattern", "^\\d{4}-\\d{2}-\\d{2}$"));
		JSONSchemaValidator validator = new JSONSchemaValidator(makeObjectSchema(properties));

		// valid
		Map<String, Object> entries = new HashMap<>();
		entries.put("value", "2019-12-24");
		assertThat(validator.validate(new JSONObject(entries)), empty());

		// invalid
		entries.put("value", "2019/12/24");
		assertThat(validator.validate(new JSONObject(entries)), hasSize(1));
	}

	@Test
	public void testNullField()
	{
		Map<String, JSONObject> properties = new HashMap<>();
		properties.put("value", makeArrayConstraint(makeNullConstraint()));
		JSONSchemaValidator validator = new JSONSchemaValidator(makeObjectSchema(properties));

		// valid
		JSONObject json = new JSONObject("{\"value\": [null]}");
		assertThat(validator.validate(json), empty());

		// invalid
		json = new JSONObject("{\"value\": [123]}");
		assertThat(validator.validate(json), hasSize(1));
	}

	@Test
	public void testEnum() throws JSONException, IOException
	{
		Map<String, JSONObject> properties = new HashMap<>();
		properties.put("name", makeEnumConstraint("red", "amber", "green"));
		JSONSchemaValidator validator = new JSONSchemaValidator(makeObjectSchema(properties));

		Map<String, String> entries = new HashMap<>();
		entries.put("name", "red");
		assertThat(validator.validate(new JSONObject(entries)), empty());

		entries.put("name", "purple");
		assertThat(validator.validate(new JSONObject(entries)), hasSize(1));
	}

	@Test
	public void testArray()
	{
		Map<String, JSONObject> properties = new HashMap<>();
		properties.put("value", makeArrayConstraint(makeNumberConstraint()));
		JSONSchemaValidator validator = new JSONSchemaValidator(makeObjectSchema(properties));

		// valid
		JSONObject json = new JSONObject("{\"value\": [1,2]}");
		assertThat(validator.validate(json), empty());

		json = new JSONObject("{\"value\": []}");
		assertThat(validator.validate(json), empty());

		// invalid
		json = new JSONObject("{\"value\": [true,false,3]}");
		assertThat(validator.validate(json), hasSize(2));

		// required
		properties.put("value", makeArrayConstraint(makeNumberConstraint(), "required"));
		validator = new JSONSchemaValidator(makeObjectSchema(properties));
		json = new JSONObject("{\"value\": [1,2]}");
		assertThat(validator.validate(json), empty());
		// invalid
		json = new JSONObject("{\"value\": []}");
		assertThat(validator.validate(json), hasSize(1));
		json = new JSONObject("{\"value\": null}");
		assertThat(validator.validate(json), hasSize(1));

		// required and default
		properties.put("value", makeArrayConstraint(makeNumberConstraint(), "required", "default", "[4,5,6]"));
		validator = new JSONSchemaValidator(makeObjectSchema(properties));
		json = new JSONObject("{\"value\": [1,2]}");
		assertThat(validator.validate(json), empty());
		// invalid
		json = new JSONObject("{\"value\": []}");
		assertThat(validator.validate(json), empty());
		json = new JSONObject("{\"value\": null}");
		assertThat(validator.validate(json), empty());
	}

	@Test
	public void testObject()
	{
		Map<String, JSONObject> properties = new HashMap<>();
		properties.put("number", makeNumberConstraint());
		properties.put("string", makeStringConstraint());
		JSONObject schema = makeObjectSchema(properties);
		JSONSchemaValidator validator = new JSONSchemaValidator(schema);

		Map<String, Object> entries = new HashMap<>();
		assertThat(validator.validate(new JSONObject(entries)), empty());

		entries.put("number", 123);
		assertThat(validator.validate(new JSONObject(entries)), empty());
		entries.put("string", "abc");
		assertThat(validator.validate(new JSONObject(entries)), empty());

		// make an entry required
		entries.clear();
		schema.put("required", makeJSONArray("number", "string"));
		validator = new JSONSchemaValidator(schema);

		assertThat(validator.validate(new JSONObject(entries)), hasSize(2));
		entries.put("number", 123);
		assertThat(validator.validate(new JSONObject(entries)), hasSize(1));
		entries.put("string", "abc");
		assertThat(validator.validate(new JSONObject(entries)), empty());
	}

	@Test
	public void testOneOf()
	{
		JSONArray constraints = new JSONArray();
		constraints.put(makeBooleanConstraint());
		constraints.put(makeIntConstraint(0, 100));
		JSONObject mixedConstraint = new JSONObject();
		mixedConstraint.put("oneOf", constraints);
		Map<String, JSONObject> properties = new HashMap<>();
		properties.put("value", mixedConstraint);
		JSONSchemaValidator validator = new JSONSchemaValidator(makeObjectSchema(properties));

		Map<String, Object> entries = new HashMap<>();
		entries.put("value", 50);
		assertThat(validator.validate(new JSONObject(entries)), empty());
		entries.put("value", true);
		assertThat(validator.validate(new JSONObject(entries)), empty());

		// invalid
		entries.put("value", "2019/12/24");
		assertThat(validator.validate(new JSONObject(entries)), hasSize(3));
	}

	@Test
	public void testDefinitions() throws JSONException, IOException
	{
		Map<String, JSONObject> defProperties = new HashMap<>();
		defProperties.put("street_address", makeStringConstraint());
		defProperties.put("city", makeStringConstraint());
		defProperties.put("state", makeStringConstraint());
		JSONObject definitions = new JSONObject();
		definitions.put("address", makeObjectSchema(defProperties));
		
		Map<String, JSONObject> properties = new HashMap<>();
		properties.put("billing_address", makeDefinitionReference("#/definitions/address"));
		properties.put("shipping_address", makeDefinitionReference("#/definitions/address"));
		JSONObject schema = makeObjectSchema(properties);
		schema.put("definitions", definitions);
		
		JSONSchemaValidator validator = new JSONSchemaValidator(schema);
		
		JSONObject json = new JSONObject();
		json.put("shipping_address", makeAddress("street1", "city1", "DC"));
		json.put("billing_address", makeAddress("street2", "city2", "CA"));

		assertThat(validator.validate(json), empty());
	}

	// --- private ---------------
	
	private JSONObject makeAddress(String street, String city, String state)
	{
		JSONObject result = new JSONObject();
		result.put("street_address", street);
		result.put("city", city);
		result.put("state", state);
		return result;
	}

	private JSONObject makeObjectSchema(Map<String, JSONObject> properties)
	{
		JSONObject schema = new JSONObject();
		schema.put("type", "object");
		schema.put("properties", new JSONObject(properties));
		return schema;
	}
	
	private JSONObject makeDefinitionReference(String path)
	{
		JSONObject result = new JSONObject();
		result.put("$ref", path);
		return result;
	}

	private JSONObject makeEnumConstraint(String... values)
	{
		JSONObject result = new JSONObject();
		result.put("type", "string");
		result.put("enum", new JSONArray(values));
		return result;
	}

	private JSONArray makeJSONArray(Object... values)
	{
		return new JSONArray(values);
	}

	private JSONObject makeIntConstraint(int minValue, int maxValue)
	{
		JSONObject result = new JSONObject();
		result.put("type", "integer");
		result.put("minimum", minValue);
		result.put("maximum", maxValue);
		return result;
	}

	private JSONObject makeNumberConstraint()
	{
		JSONObject result = new JSONObject();
		result.put("type", "number");
		return result;
	}

	private JSONObject makeBooleanConstraint()
	{
		JSONObject result = new JSONObject();
		result.put("type", "boolean");
		return result;
	}

	private JSONObject makeNullConstraint()
	{
		JSONObject result = new JSONObject();
		result.put("type", "null");
		return result;
	}

	private JSONObject makeStringConstraint(String... modifier)
	{
		JSONObject result = new JSONObject();
		result.put("type", "string");
		return addModifiers(result, modifier);
	}

	private JSONObject makeArrayConstraint(JSONObject itemConstraint, String... modifier)
	{
		JSONObject result = new JSONObject();
		result.put("type", "array");
		result.put("items", itemConstraint);
		return addModifiers(result, modifier);
	}

	private JSONObject addModifiers(JSONObject result, String[] modifier)
	{
		List<String> modList = new ArrayList<>(Arrays.asList(modifier));
		while (!modList.isEmpty())
		{
			String key = modList.remove(0);
			if (key.equals("required"))
				result.put(key, true);
			else if (key.equals("default"))
				result.put(key, new JSONArray(modList.remove(0)));
			else
				result.put(key, modList.remove(0));
		}
		return result;

	}
}

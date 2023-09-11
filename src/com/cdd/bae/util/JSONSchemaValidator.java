/*
 * This is not a complete implementation of a JSON Schema validator. If more functionality is required, consider using an existing implementation.
 * See http://json-schema.org/ for the full specification. 
 */

package com.cdd.bae.util;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import org.slf4j.*;

import com.cdd.bao.template.*;

import org.json.*;

public class JSONSchemaValidator
{
	private static final String MINIMUM = "minimum";
	private static final String MAXIMUM = "maximum";
	private static final Logger logger = LoggerFactory.getLogger(JSONSchemaValidator.class);
	private static final String JSON_REQUIRED = "required"; 
	private static final String JSON_PROPERTIES = "properties";

	protected JSONObject schema;
	protected Map<String, JSONObject> definitions = new HashMap<>();

	// ------------ public methods ------------

	public JSONSchemaValidator(JSONObject schema)
	{
		this.schema = schema;
		logger.trace("Parsing definitions");
		JSONObject defs = schema.optJSONObject("definitions");
		if (defs != null)
		{
			Iterator<?> keys = defs.keys();
			while (keys.hasNext())
			{
				String key = (String) keys.next();
				logger.trace("  definition: {}", key);
				definitions.put("#/definitions/" + key, defs.getJSONObject(key));
			}
		}
	}
	
	public static JSONSchemaValidator fromResource(String schemaDefinition) throws JSONSchemaValidatorException
	{
		JSONSchemaValidator validator;
		ResourceFile schemaFile = new ResourceFile(schemaDefinition);
		try
		{
			validator = new JSONSchemaValidator(new JSONObject(schemaFile.getContent()));
		}
		catch (IOException ex)
		{
			throw new JSONSchemaValidatorException("Error loading configuration schema",
					Arrays.asList("Cannot read configuration schema definition file", ex.getMessage()), ex);
		}
		catch (JSONException ex)
		{
			throw new JSONSchemaValidatorException("Error loading configuration schema",
					Arrays.asList("Cannot parse JSON from configuration schema", ex.getMessage()), ex);
		}
		return validator;
	}

	// validate JSON data structure against the schema
	public List<String> validate(JSONObject json)
	{
		if (!this.schema.opt("type").equals("object"))
			throw new IllegalStateException("Root type of schema must be 'object' or 'array'");
		return validateObject(this.schema, json, "");
	}
	
	public List<String> validate(JSONArray json)
	{
		if (!this.schema.opt("type").equals("array"))
			throw new IllegalStateException("Root type of schema must be 'object' or 'array'");
		return validateArray(this.schema, json, "");
	}

	// ------------ private methods ------------

	private List<String> validateObject(JSONObject schema, JSONObject json, String level)
	{
		if (logger.isTraceEnabled())
		{
			logger.trace("validateObject: {}", json);
			logger.trace("        schema: {}", schema.toString());
		}
		List<String> errors = new ArrayList<>();

		// check required properties
		Set<String> required = jsonArrayToString(schema.optJSONArray(JSON_REQUIRED));
		for (String property : required)
		{
			if (!json.has(property)) errors.add(level + "/" + property + " is required");
		}

		// validate the individual properties
		JSONObject properties = schema.getJSONObject(JSON_PROPERTIES);
		Iterator<?> keys = properties.keys();
		HashSet<String> definedKeys = new HashSet<>();
		while (keys.hasNext())
		{
			String key = (String) keys.next();
			definedKeys.add(key);
			JSONObject propertySchema = properties.getJSONObject(key);
			if (propertySchema.has("$ref"))
				propertySchema = definitions.get(propertySchema.getString("$ref"));
			if (propertySchema.has("oneOf"))
				errors.addAll(validateItem(propertySchema, json.opt(key), level + "/" + key));
			else
				switch (propertySchema.getString("type")) 
				{
					case "object":
						// we already checked for requirement, so skip this if child is missing
						if (json.has(key)) errors.addAll(this.validateObject(propertySchema, json.getJSONObject(key), level + "/" + key));
						break;
					case "array":
						errors.addAll(this.validateArray(propertySchema, json.optJSONArray(key), level + "/" + key));
						break;
					case "string":
					case "integer":
					case "number":
					case "boolean":
						errors.addAll(this.validateEntities(propertySchema, json.opt(key), level + "/" + key));
						break;
					default:
						throw new IllegalStateException("JSON type " + propertySchema.getString("type") + " not supported");
				}
		}

		// handle the additionalProperties constraints for keys that are not in
		JSONObject schemaAdditional = schema.optJSONObject("additionalProperties");
		if (schemaAdditional != null)
		{
			keys = json.keys();
			while (keys.hasNext())
			{
				String key = (String) keys.next();
				if (definedKeys.contains(key)) continue;
				errors.addAll(this.validateEntities(schemaAdditional, json.opt(key), level + "/" + key));
			}
		}

		// check if keys that point to directories or filenames are valid directories or files
		return errors;
	}

	private List<String> validateEntities(JSONObject schema, Object obj, String level)
	{
		List<String> errors = new ArrayList<>();
		if (schema.getString("type").contentEquals("null"))
		{
			errors.addAll(this.validateNull(obj, level));
			return errors;
		}
		if (obj == null || obj == JSONObject.NULL)
		{
			if (schema.optBoolean(JSON_REQUIRED) && schema.optString("default", null) == null)
				errors.add(level + " is required and must not be null");
			return errors;
		}
		switch (schema.getString("type")) 
		{
			case "string":
				errors.addAll(this.validateString(schema, obj, level));
				break;
			case "integer":
				errors.addAll(this.validateInteger(schema, obj, level));
				break;
			case "number":
				errors.addAll(this.validateNumber(obj, level));
				break;
			case "boolean":
				errors.addAll(this.validateBoolean(obj, level));
				break;
			default:
				throw new IllegalStateException("JSON type " + schema.getString("type") + " not supported");
		}
		return errors;
	}

	private List<String> validateArray(JSONObject schema, JSONArray array, String level)
	{
		logger.trace("validateArray: {} {}", level, array);
		List<String> errors = new ArrayList<>();
		if (array == null || array.length() == 0)
		{
			if (schema.optBoolean(JSON_REQUIRED) && schema.optString("default", null) == null)
				errors.add(level + " is required and must not be null");
			return errors;
		}
		JSONObject itemSchema = schema.getJSONObject("items");
		if (itemSchema.has("$ref"))
			itemSchema = definitions.get(itemSchema.getString("$ref"));
		for (int i = 0; i < array.length(); i++)
			errors.addAll(validateItem(itemSchema, array.get(i), level + "/[" + i + "]"));
		return errors;
	}

	private List<String> validateOneOf(JSONObject schema, Object json, String level)
	{
		List<String> errors = new ArrayList<>();
		JSONArray candidates = schema.getJSONArray("oneOf");
		boolean matches = false;
		for (int i = 0; i < candidates.length(); i++)
		{
			JSONObject candSchema = candidates.getJSONObject(i);
			if (candSchema.has("$ref")) candSchema = definitions.get(candSchema.getString("$ref"));
			List<String> result = validateItem(candSchema, json, level + "   ");
			if (result.isEmpty())
			{
				matches = true;
				break;
			}
			errors.addAll(result);
		}
		if (matches)
			errors.clear();
		else
			errors.add(0, level + " JSON is not valid against any schema from 'oneOf'");
		return errors;
	}

	private List<String> validateItem(JSONObject schema, Object json, String iLevel)
	{
		if (schema.has("oneOf"))
			return validateOneOf(schema, json, iLevel);
		else if (schema.getString("type").equals("object"))
			if (json instanceof JSONObject)
				return this.validateObject(schema, (JSONObject)json, iLevel);
			else
				return Arrays.asList(iLevel + "JSONObject expected");
		else if (schema.getString("type").equals("array"))
			if (json instanceof JSONArray)
				return this.validateArray(schema, (JSONArray)json, iLevel);
			else
				return Arrays.asList(iLevel + "JSONArray expected");
		return this.validateEntities(schema, json, iLevel);
	}

	private List<String> validateString(JSONObject schema, Object value, String level)
	{
		logger.trace("validateString: {} {}", level, value);
		List<String> errors = new ArrayList<>();
		if (!(value instanceof String))
		{
			errors.add(level + " value not a string");
			return errors;
		}

		// we have string now check the format using either pattern or format (if present)
		String pattern = schema.optString("pattern");
		String format = schema.optString("format");
		if (pattern.length() == 0 && format.length() > 0)
		{
			switch (format) 
			{
				case "hostname":
					pattern = "(\\w+\\.)*\\w+";
					break;
				case "uri":
					pattern = "https?://(\\w+\\.)*\\w+(:\\d+)*/(\\S)*";
					break;
				case "url":
					pattern = "(http|https|ftp|file):///?(\\w+\\.)*\\w+(:\\d+)*/(\\S)*";
					break;
				case "date":
					pattern = "(\\d{4})-(\\d{2})-(\\d{2})";
					break;
				default:
					throw new IllegalStateException("Format " + format + " not supported (found in " + level + ")");
			}
		}
		String strval = value.toString();
		if (format.equals("uri")) strval = ModelSchema.expandPrefix(strval);
		if (pattern.length() > 0 && !Pattern.matches(pattern, strval))
		{
			String errmsg = level + " value has incorrect pattern (value=" + value;
			if (format.length() > 0) errmsg += ", format=" + format;
			errmsg += ", pattern=" + pattern + ")";
			errors.add(errmsg);
		}

		if (schema.has("enum"))
		{
			JSONArray arr = schema.getJSONArray("enum");
			List<String> allowedValues = new ArrayList<>();
			for (int i = 0; i < arr.length(); i++) allowedValues.add(arr.getString(i));
			
			if (!allowedValues.contains(strval))
			{
				StringJoiner sj = new StringJoiner(", ", "[", "]");
				for (String allowedValue : allowedValues) sj.add(allowedValue);
				String errmsg = level + " value must be one of " + sj.toString();
				errors.add(errmsg);
			}
		}
		return errors;
	}

	private List<String> validateInteger(JSONObject schema, Object value, String level)
	{
		logger.trace("validateInteger: {} {}", level, value);
		String sValue = value.toString();
		List<String> errors = new ArrayList<>();
		if (!Pattern.matches("[-+]?\\d+", sValue))
		{
			errors.add(level + " value must be an integer; found: " + sValue);
			return errors;
		}
		
		// handle constraints
		int intValue = Integer.parseInt(sValue);
		if (schema.has(MINIMUM) && intValue < schema.getInt(MINIMUM))
			errors.add(level + " integer value must be equal or larger than " + schema.getInt(MINIMUM) + "; found: " + sValue);
		if (schema.has(MAXIMUM) && intValue > schema.getInt(MAXIMUM))
			errors.add(level + " integer value must be equal or smaller than " + schema.getInt(MAXIMUM) + "; found: " + sValue);
			
		return errors;
	}

	private List<String> validateNumber(Object value, String level)
	{
		logger.trace("validateNumber: {} {}", level, value);
		List<String> errors = new ArrayList<>();
		if (!Pattern.matches("-{0,1}\\d+(\\.\\d+){0,1}", value.toString()))
			errors.add(level + " value must be a number; found: " + value.toString());
		return errors;
	}

	private List<String> validateBoolean(Object value, String level)
	{
		logger.trace("validateBoolean: {} {}", level, value);
		List<String> errors = new ArrayList<>();
		String s = value.toString();
		if (!(s.equals("true") || s.equals("false")))
			errors.add(level + " value must be a boolean; found: " + value.toString());
		return errors;
	}

	private List<String> validateNull(Object value, String level)
	{
		logger.trace("validateNull: {} {}", level, value);
		List<String> errors = new ArrayList<>();
		String s = value.toString();
		if (!(s.equals("null")))
			errors.add(level + " value must be null; found: " + value.toString());
		return errors;
	}

	// convert a JSONArray of strings to array of strings
	private Set<String> jsonArrayToString(JSONArray jsonArray)
	{
		Set<String> strings = new HashSet<>();
		if (jsonArray != null)
			for (int i = 0; i < jsonArray.length(); i++) strings.add(jsonArray.getString(i));
		return strings;
	}
}

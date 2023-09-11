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

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import org.json.*;
import org.junit.jupiter.api.*;

public class FileLoaderJsonTest extends TestBaseClass
{
	private static final String GHIJKL = "ghijkl";
	private static final String ABCDEF = "abcdef";
	private static final String PROP2 = "prop2";
	private static final String PROP1 = "prop1";
	private static final String SCHEMA_JSON_OBJECT = "/testData/config/fileLoader/schemaObject.json";
	private static final String SCHEMA_JSON_ARRAY = "/testData/config/fileLoader/schemaArray.json";

	public TestResourceFile valid = new TestResourceFile("/testData/config/fileLoader/validObject.json");
	public TestResourceFile validArray = new TestResourceFile("/testData/config/fileLoader/validArray.json");
	public TestResourceFile invalid = new TestResourceFile("/testData/config/fileLoader/invalidObject.json");
	
	@Test
	public void testFileLoaderJsonObjectValid() throws IOException, JSONSchemaValidatorException
	{
		File file = valid.getAsFile(folder, "validObject.json");
		FileLoaderJSONObject loader = new FileLoaderJSONObject(file, SCHEMA_JSON_OBJECT);
		JSONObject json = loader.load();
		assertEquals(ABCDEF, json.getString(PROP1));
		assertEquals(1234, json.getInt(PROP2));

		json = loader.reload();
		assertEquals(ABCDEF, json.getString(PROP1));
		assertEquals(1234, json.getInt(PROP2));

		assertFalse(loader.hasChanged());
		// fake file has changed
		loader.getWatcher().lastModified.put(file, loader.getWatcher().lastModified.get(file) - 10);
		assertTrue(loader.hasChanged(), "File was modified");
		loader.load();
		assertFalse(loader.hasChanged(), "Loading resets the watcher");
		
		// Change the file
		File newFile = createFile("testfile");
		loader.setFile(file);
		assertFalse(loader.hasChanged(), "We assign the same file again");
		loader.setFile(newFile);
		assertTrue(loader.hasChanged(), "New file (not validated)");
	}

	@Test
	public void testFileLoaderJsonObjectInvalid() throws IOException, JSONSchemaValidatorException
	{
		File file = invalid.getAsFile(folder, "invalidObject.json");
		FileLoaderJSONObject loader = new FileLoaderJSONObject(file, SCHEMA_JSON_OBJECT);
		loader.retryDelay = 1;

		try
		{
			loader.load();
			assertTrue(false, "Exception expected");
		}
		catch (JSONSchemaValidatorException e)
		{
			assertEquals(1, e.getDetails().size());
		}

		JSONObject json = loader.reload();
		assertNull(json, "reload fails twice and return null");
	}

	@Test
	public void testFileLoaderJsonArray() throws IOException, JSONSchemaValidatorException
	{
		File file = validArray.getAsFile(folder, "validArray.json");
		FileLoaderJSONArray loader = new FileLoaderJSONArray(file, SCHEMA_JSON_ARRAY);
		JSONArray json = loader.load();
		assertEquals(ABCDEF, json.getJSONObject(0).getString(PROP1));
		assertEquals(1234, json.getJSONObject(0).getInt(PROP2));
		assertEquals(GHIJKL, json.getJSONObject(1).getString(PROP1));
		assertEquals(5678, json.getJSONObject(1).getInt(PROP2));
	}
}

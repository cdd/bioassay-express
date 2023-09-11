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

package com.cdd.bae.config;

import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.json.*;
import org.junit.jupiter.api.*;

public class EntryFormsTest extends TestBaseClass
{
	private final TestUtilities.MuteLogger muteLogger = new TestUtilities.MuteLogger(EntryForms.class);

	private TestResourceFile exampleFile = new TestResourceFile("/testData/config/forms/forms_cat.json");
	private TestResourceFile[] exampleFiles = 
	{
		new TestResourceFile("/testData/config/forms/forms_cat.json"),
		new TestResourceFile("/testData/config/forms/forms_mix_table_tableRow.json"),
	};

	@Test
	public void testLoadForms() throws IOException, ConfigurationException
	{
		String dirName = folder.toAbsolutePath().toString();
		String[] requiredFiles = new String[]{};

		// works without any files
		EntryForms forms = new EntryForms(dirName, requiredFiles);
		assertEquals(0, forms.getEntries().length);
		assertFalse(forms.hasChanged());

		// add the example file
		File newFile = exampleFile.getAsFile(folder, "example.json");
		requiredFiles = new String[]{"example.json"};
		forms = new EntryForms(dirName, requiredFiles);
		assertEquals(1, forms.getEntries().length);
		assertFalse(forms.hasChanged());

		// add an additional file to the directory, as long it is not included in requiredFiles, it is not used
		createFile("unused.json");
		forms = new EntryForms(dirName, requiredFiles);
		assertEquals(1, forms.getEntries().length);
		assertFalse(forms.hasChanged());

		// now delete the example file
		assertTrue(newFile.delete(), "Could not delete file");
		muteLogger.mute();
		final EntryForms excForms = forms;
		Assertions.assertThrows(ConfigurationException.class, () -> excForms.load());
		muteLogger.restore();
	}
	
	@Test
	public void testSchemaDefinition() throws IOException, JSONSchemaValidatorException
	{
		
		JSONSchemaValidator validator = JSONSchemaValidator.fromResource(EntryForms.SCHEMA_DEFINITION);
		for (TestResourceFile example : exampleFiles)
		{
			JSONArray json = new JSONArray(example.getContent());
			List<String> errors = validator.validate(json);
			assertThat(errors, empty());
		}
		
		
	}
}

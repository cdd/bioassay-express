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

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.json.*;
import org.junit.jupiter.api.*;

public class TransliterationTest extends TestBaseClass
{
	private static final String EXAMPLE_JSON = "example.json";

	private final TestUtilities.MuteLogger muteLogger = new TestUtilities.MuteLogger(Transliteration.class);

	public TestResourceFile exampleFile = new TestResourceFile("/testData/config/transliteration/transliterateCAT.json");

	@Test
	public void testLoadTransliterate() throws IOException, ConfigurationException
	{
		String dirName = folder.toAbsolutePath().toString();
		String[] requiredFiles = new String[]{};

		// works without any files
		Transliteration translit = new Transliteration(dirName, requiredFiles);
		assertEquals(0, translit.boilers.size());
		assertFalse(translit.hasChanged());

		// add the example file
		File newFile = exampleFile.getAsFile(folder, EXAMPLE_JSON);
		requiredFiles = new String[]{EXAMPLE_JSON};
		translit = new Transliteration(dirName, requiredFiles);
		assertEquals(1, translit.boilers.size());
		assertFalse(translit.hasChanged());

		// add an additional file to the directory, as long it is not included in requiredFiles, it is not used
		exampleFile.getAsFile(folder, "unused.json");
		translit = new Transliteration(dirName, requiredFiles);
		assertEquals(1, translit.boilers.size());
		assertFalse(translit.hasChanged());

		// now delete the example file
		assertTrue(newFile.delete(), "Could not delete file");
		Transliteration invalidTranslit = translit;
		muteLogger.mute();
		Assertions.assertThrows(ConfigurationException.class, () -> invalidTranslit.load());
		muteLogger.restore();
	}

	@Test
	public void testBoilerplate() throws IOException, ConfigurationException
	{
		exampleFile.getAsFile(folder, EXAMPLE_JSON);
		String dirName = folder.toAbsolutePath().toString();
		String[] requiredFiles = new String[]{EXAMPLE_JSON};

		Transliteration translit = new Transliteration(dirName, requiredFiles);
		assertEquals(1, translit.boilers.size());
		String schemaURI = translit.boilers.keySet().iterator().next();

		Transliteration.Boilerplate bp = translit.getBoilerplate(schemaURI);
		assertNotNull(bp);

		bp = translit.getBoilerplate("unknown schemaURI");
		assertNull(bp);

		String[] previews = translit.getPreviews(schemaURI);
		assertEquals(0, previews.length);

		previews = translit.getPreviews("unknown schemaURI");
		assertEquals(0, previews.length);
	}
	
	@Test
	public void testSchemaValidation() throws JSONSchemaValidatorException, IOException, ConfigurationException
	{
		List<String> noErrors = new ArrayList<String>();
		
		JSONSchemaValidator validator = JSONSchemaValidator.fromResource(Transliteration.schemaDefinition);
		
		File resource = new TestResourceFile("/testData/config/transliteration/testTerm.json").getAsFile(folder, "testTerm.json");
		List<String> result = validator.validate(Transliteration.loadTemplateFile(resource));
		assertEquals(noErrors, result);

		resource = new TestResourceFile("/testData/config/transliteration/testTerms.json").getAsFile(folder, "testTerms.json");
		result = validator.validate(Transliteration.loadTemplateFile(resource));
		assertEquals(noErrors, result);
		
		resource = new TestResourceFile("/testData/config/transliteration/testIfAny.json").getAsFile(folder, "testIfAny.json");
		result = validator.validate(Transliteration.loadTemplateFile(resource));
		assertEquals(noErrors, result);
		
		resource = new TestResourceFile("/testData/config/transliteration/testIfBranch.json").getAsFile(folder, "testIfBranch.json");
		result = validator.validate(Transliteration.loadTemplateFile(resource));
		assertEquals(noErrors, result);
	}
	
	@Test
	public void testBuildingBlocks() throws JSONSchemaValidatorException, IOException, ConfigurationException
	{
		List<String> noErrors = new ArrayList<String>();
		
		JSONSchemaValidator validator = JSONSchemaValidator.fromResource(Transliteration.schemaDefinition);
		
		File templateFile = new TestResourceFile("/testData/config/transliteration/testBuildingBlock.json").getAsFile(folder, "template.json");
		new TestResourceFile("/testData/config/transliteration/bblock1.json").getAsFile(folder, "bblock1.json");
		new TestResourceFile("/testData/config/transliteration/bblock2.json").getAsFile(folder, "bblock2.json");
		
		JSONArray bblockTemplate = Transliteration.loadTemplateFile(templateFile);
		List<String> result = validator.validate(bblockTemplate);
		assertEquals(noErrors, result);
		
		File resource = new TestResourceFile("/testData/config/transliteration/testTerms.json").getAsFile(folder, "testTerms.json");
		JSONArray fullTemplate = Transliteration.loadTemplateFile(resource);
		assertEquals(bblockTemplate.toString(), fullTemplate.toString());
	}
}

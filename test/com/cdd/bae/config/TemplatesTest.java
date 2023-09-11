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

import org.junit.jupiter.api.*;

public class TemplatesTest extends TestBaseClass
{
	public TestResourceFile template = new TestResourceFile("/testData/config/schema_cat.json");

	@Test
	public void testLoadTemplates() throws IOException, ConfigurationException
	{
		String templateDir = folder.toAbsolutePath().toString();
		String[] templateFiles = new String[]{};
		Assertions.assertThrows(ConfigurationException.class, () -> new Templates(templateDir, null, templateFiles));

		// Default file exists
		createJSONfile(Templates.DEFAULT_TEMPLATE);
		Templates templates = new Templates(templateDir, templateFiles, null);
		assertFalse(templates.hasChanged());
		assertEquals(1, templates.getSchemaList().length);

		// Add a new template to the directory
		File newFile = createJSONfile("new.json");
		assertTrue(templates.hasChanged());
		templates.load();
		assertFalse(templates.hasChanged());
		assertTrue(newFile.delete(), "Could not delete file");
		assertTrue(templates.hasChanged());
	}

	@Test
	public void testLoadTemplatesExceptions() throws IOException, ConfigurationException
	{
		String templateDir = folder.toAbsolutePath().toString();

		// No files exist, we get an exception
		Assertions.assertThrows(ConfigurationException.class, () -> new Templates(templateDir, null, null));

		// Default file exists
		createJSONfile(Templates.DEFAULT_TEMPLATE);
		Templates templates = new Templates(templateDir, null, null);
		templates.load();
		assertEquals(1, templates.getSchemaList().length);

		// Now create a broken JSON file that is loaded as it is found in the folder
		createFile("broken.json");
		Assertions.assertThrows(ConfigurationException.class, () -> templates.load());
	}

	@Test
	public void testMultipleTemplates() throws ConfigurationException, IOException
	{
		String templateDir = folder.toAbsolutePath().toString();
		createJSONfile(Templates.DEFAULT_TEMPLATE);
		createJSONfile("templateA.json");
		createJSONfile("templateB.json");
		String[] templateFiles = new String[]{Templates.DEFAULT_TEMPLATE, "templateA.json", "templateB.json"};
		Templates templates = new Templates(templateDir, templateFiles, null);
		templates.load();
		assertEquals(3, templates.getSchemaList().length);
	}

	private File createJSONfile(String filename) throws IOException
	{
		File f = createFile(filename);
		try (FileWriter out = new FileWriter(f))
		{
			out.write(template.getContent());
		}
		return f;
	}
}

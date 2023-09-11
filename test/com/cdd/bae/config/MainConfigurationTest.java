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
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import org.junit.jupiter.api.*;

public class MainConfigurationTest extends TestBaseClass
{
	static final String INCORRECT_BASE_DIRECTORY = "/incorrectBase";
	static final int NUMBER_MISSING_FILES = 7; // If configuration requirements change, this number may change

	public TestResourceFile res = new TestResourceFile("/testData/config/validConfig.json");

	@Test
	public void testMainConfiguration() throws IOException, JSONSchemaValidatorException
	{
		File file = res.getAsFile(createFile("config.json"));
		String productionConfiguration = TestConfiguration.getProductionConfiguration();

		if ((new File(productionConfiguration)).isDirectory())
		{
			InitParams configuration = new InitParams(file.getAbsolutePath(), productionConfiguration);

			assertEquals("bae", configuration.database.name);
			assertEquals(productionConfiguration + "/assays", configuration.modulePubChem.directory);
			assertEquals(7, configuration.moduleVault.vaultIDList.length);
			assertEquals(1, configuration.moduleVault.unitsMap.size());
			assertEquals(0, configuration.moduleVault.operatorMap.size());
			assertEquals(productionConfiguration + "/authentication.json", configuration.authenticationFile);
			assertEquals(productionConfiguration + "/vocab.dump.gz", configuration.schemaVocabFile);
			assertEquals(productionConfiguration + "/template", configuration.template.directory);
			assertEquals(0, configuration.template.files.length);
			assertEquals(false, configuration.pageToggle.randomAssay);
			assertEquals(true, configuration.pageToggle.schemaReport);
		}

		Assertions.assertThrows(JSONSchemaValidatorException.class, () -> new InitParams(file.getAbsolutePath(), INCORRECT_BASE_DIRECTORY));
		try
		{
			new InitParams(file.getAbsolutePath(), INCORRECT_BASE_DIRECTORY);
		}
		catch (JSONSchemaValidatorException e)
		{
			assertThat(e.getMessage(), startsWith("Configuration invalid:"));
			assertEquals(NUMBER_MISSING_FILES, e.getDetails().size());
			assertThat(e.getDetails().get(0), containsString(INCORRECT_BASE_DIRECTORY));
		}
	}

	@Test
	public void testConstructureExceptions() throws IOException, JSONSchemaValidatorException
	{
		File file = res.getAsFile(createFile("config.json"));

		Assertions.assertThrows(JSONSchemaValidatorException.class, () -> new InitParams(file.getPath()));
		try
		{
			new InitParams(file.getPath());
		}
		catch (JSONSchemaValidatorException ex)
		{
			assertEquals(NUMBER_MISSING_FILES, ex.getDetails().size());
			assertThat(ex.getDetails().get(0), containsString(file.getParent()));
		}

		Assertions.assertThrows(JSONSchemaValidatorException.class, () -> new InitParams(file.getPath(), INCORRECT_BASE_DIRECTORY));
		try
		{
			new InitParams(file.getPath(), INCORRECT_BASE_DIRECTORY);
		}
		catch (JSONSchemaValidatorException ex)
		{
			assertEquals(NUMBER_MISSING_FILES, ex.getDetails().size());
			assertThat(ex.getDetails().get(0), containsString(INCORRECT_BASE_DIRECTORY));
		}
	}

	@Test
	public void testToString() throws IOException, JSONSchemaValidatorException
	{
		String productionConfiguration = TestConfiguration.getProductionConfiguration();
		if ((new File(productionConfiguration)).isDirectory())
		{
			File file = res.getAsFile(folder, "config.json");
			InitParams configuration = new InitParams(file.getAbsolutePath(), productionConfiguration);
			String s = configuration.toString();
			assertThat(s, containsString("Database"));
			assertThat(s, containsString("Custom Web Directory"));
		}
	}

	@Test
	public void testEquals() throws IOException, JSONSchemaValidatorException
	{
		String productionConfiguration = TestConfiguration.getProductionConfiguration();
		// Skip the test if production configuration is not available
		if (!(new File(productionConfiguration)).isDirectory()) return;

		File file = res.getAsFile(folder, "config.json");
		InitParams configuration1 = new InitParams(file.getAbsolutePath(), productionConfiguration);
		InitParams configuration2 = new InitParams(file.getAbsolutePath(), productionConfiguration);
		assertTrue(configuration1.equals(configuration2));
		assertTrue(configuration2.equals(configuration1));

		configuration1.authenticationFile = null;
		assertFalse(configuration1.equals(configuration2));
		assertFalse(configuration2.equals(configuration1));
		configuration2.authenticationFile = null;
		assertTrue(configuration1.equals(configuration2));
		assertTrue(configuration2.equals(configuration1));

		configuration1.template.files = new String[] {"A", "B"};
		assertFalse(configuration1.equals(configuration2));
		assertFalse(configuration2.equals(configuration1));
		configuration2.authenticationFile = null;
		assertFalse(configuration1.equals(configuration2));
		assertFalse(configuration2.equals(configuration1));
		configuration2.template.files = new String[] {"A", "B"};
		assertTrue(configuration1.equals(configuration2));
		assertTrue(configuration2.equals(configuration1));

		configuration1.modulePubChem = new InitParams.ModulePubChem();
		assertFalse(configuration1.equals(configuration2));
		assertFalse(configuration2.equals(configuration1));
		configuration2.modulePubChem = new InitParams.ModulePubChem();
		assertTrue(configuration1.equals(configuration2));
		assertTrue(configuration2.equals(configuration1));
		configuration1.modulePubChem = null;
		assertFalse(configuration1.equals(configuration2));
		assertFalse(configuration2.equals(configuration1));
		configuration2.modulePubChem = null;
		assertTrue(configuration1.equals(configuration2));
		assertTrue(configuration2.equals(configuration1));

		configuration1.moduleVault = new InitParams.ModuleVault();
		assertFalse(configuration1.equals(configuration2));
		assertFalse(configuration2.equals(configuration1));
		configuration2.moduleVault = new InitParams.ModuleVault();
		assertTrue(configuration1.equals(configuration2));
		assertTrue(configuration2.equals(configuration1));
		configuration1.moduleVault = null;
		assertFalse(configuration1.equals(configuration2));
		assertFalse(configuration2.equals(configuration1));
		configuration2.moduleVault = null;
		assertTrue(configuration1.equals(configuration2));
		assertTrue(configuration2.equals(configuration1));
	}

	@Test
	public void testHasChangedReload() throws IOException, JSONSchemaValidatorException
	{
		// Skip the test if production configuration is not available
		String productionConfiguration = TestConfiguration.getProductionConfiguration();
		if (!(new File(productionConfiguration)).isDirectory()) return;

		File file = res.getAsFile(folder, "config.json");
		InitParams configuration = new InitParams(file.getAbsolutePath(), productionConfiguration);
		assertFalse(configuration.hasChanged(), "After initialisation, file has not changed");

		// Modify an entry in configuration so that we can validate the reloading
		String oldvalue = configuration.authenticationFile;
		configuration.authenticationFile = "abcdef";
		assertNotEquals(oldvalue, configuration.authenticationFile);
		configuration.reload();
		assertEquals(oldvalue, configuration.authenticationFile, "Old value is restored after reload");
	}
	
	@Test
	// load a configuration file that has all the sections, and insist that they're present
	public void testComprehensiveConfig() throws IOException
	{
		try
		{
			TestResourceFile resFile = new TestResourceFile("/testData/config/comprehensiveConfig.json");
			File file = resFile.getAsFile(createFile("config.json"));
			String productionConfiguration = TestConfiguration.getProductionConfiguration();
			InitParams cfg = new InitParams(file.getAbsolutePath(), productionConfiguration);
			
			assertNotNull(cfg.baseURL);
			assertNotNull(cfg.database);
			assertNotNull(cfg.modulePubChem);
			assertNotNull(cfg.moduleVault);
			assertNotNull(cfg.authenticationFile);
			assertNotNull(cfg.identifierFile);
			assertNotNull(cfg.schemaVocabFile);
			assertNotNull(cfg.schemaVocabUpdate);
			assertNotNull(cfg.template.directory);
			assertNotNull(cfg.template.files);
			assertNotNull(cfg.translit.directory);
			assertNotNull(cfg.translit.files);
			assertNotNull(cfg.forms.directory);
			assertNotNull(cfg.forms.files);
			assertNotNull(cfg.axiomsDir);
			assertNotNull(cfg.nlpDir);
			assertNotNull(cfg.customWebDir);
			assertNotNull(cfg.pageToggle);
			assertNotNull(cfg.uiMessage);
			assertNotNull(cfg.provisional);
			assertNotNull(cfg.bridges);
			
			// dig into the object members
			assertNotNull(cfg.database.name);
			assertNotNull(cfg.modulePubChem.directory);
			assertNotNull(cfg.moduleVault.vaultIDList);
			assertNotNull(cfg.moduleVault.apiKey);
			assertThat(cfg.moduleVault.propertyMap.size(), greaterThan(0));
			assertThat(cfg.moduleVault.unitsMap.size(), greaterThan(0));
			assertThat(cfg.moduleVault.operatorMap.size(), greaterThan(0));
			assertNotNull(cfg.uiMessage.message);
			assertNotNull(cfg.uiMessage.style);
			assertNotNull(cfg.provisional.baseURI);
			assertThat(cfg.provisional.abbreviation, is("user:"));
			assertNotNull(cfg.provisional.directory);
			for (InitParams.OntoloBridge bridge : cfg.bridges)
			{
				assertNotNull(bridge.name);
				assertNotNull(bridge.description);
				assertNotNull(bridge.baseURL);
			}
		}
		catch (JSONSchemaValidatorException ex)
		{
			throw new IOException(ex.getMessage() + ":" + String.join(",", ex.getDetails()), ex);
		}
	}
}

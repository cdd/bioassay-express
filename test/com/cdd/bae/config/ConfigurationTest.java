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
import com.cdd.bao.template.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import org.apache.commons.lang3.*;
import org.junit.jupiter.api.*;

public class ConfigurationTest extends TestBaseClass
{
	static final String INCORRECT_BASE_DIRECTORY = "/incorrectBase";
	static final boolean NO_LOAD_NLPMODELS = false;
	private final TestUtilities.MuteLogger muteLogger = new TestUtilities.MuteLogger(Configuration.class);

	public TestResourceFile res = new TestResourceFile("/testData/config/validConfig.json");

	@AfterAll
	public static void restore() throws IOException, ConfigurationException
	{
		//TestConfiguration.restoreSchemaVocab();
	}

	@Test
	public void testConfiguration() throws IOException, ConfigurationException
	{
		// require a production configuration to test
		String productionConfiguration = TestConfiguration.getProductionConfiguration();
		if (!(new File(productionConfiguration)).isDirectory()) return;
		File file = res.getAsFile(folder, "config.json");
		Configuration configuration;
		try
		{
			configuration = new Configuration(file.getAbsolutePath(), productionConfiguration);
		}
		catch (ConfigurationException e)
		{
			System.out.println(e.getMessage());
			for (String d: e.getDetails()) System.out.println(d);
			throw e;
		}
		assertFalse(configuration.hasChanged(), "Configuration not changed");

		assertEquals(file.getAbsolutePath(), configuration.getConfigFN());
		assertNotNull(configuration.getParams());
		assertNotNull(configuration.getModulePubChem());
		assertNotNull(configuration.getModuleVault());
		assertNotNull(configuration.getCustomWebDir());
		assertNotNull(configuration.getPageToggle());
		assertNotNull(configuration.getAuthentication());
		assertNotNull(configuration.getIdentifier());
		/* !! deprecated
		Schema[] allSchemata = configuration.getAllSchemata();
		Schema schemaCAT = configuration.getSchemaCAT();
		assertTrue(allSchemata.length > 0);
		assertEquals(allSchemata[0], schemaCAT);
		assertEquals(schemaCAT, configuration.getSchema(schemaCAT.getSchemaPrefix()));

		SchemaVocabFile schemaVocabFile = configuration.getSchemaVocabFile();
		assertNotNull(schemaVocabFile);
		assertEquals(schemaVocabFile.getSchemaVocab(), configuration.getSchemaVocab());
		*/

		NLPModels nlpModels = configuration.getNLPModels();
		assertEquals(nlpModels.sentenceModel, configuration.getSentenceModel());
		assertEquals(nlpModels.tokenModel, configuration.getTokenModel());
		assertEquals(nlpModels.posModel, configuration.getPosModel());
		assertEquals(nlpModels.chunkModel, configuration.getChunkModel());
		assertEquals(nlpModels.parserModel, configuration.getParserModel());

		Configuration newConfiguration = new Configuration(file.getAbsolutePath(), productionConfiguration, configuration);
		assertNotNull(newConfiguration.getNLPModels(), "NLP models reused");
		newConfiguration = new Configuration(file.getAbsolutePath(), productionConfiguration, configuration, NO_LOAD_NLPMODELS);
		assertNull(newConfiguration.getNLPModels(), "NLP models not loaded");
	}

	@Test
	public void testExceptions()
	{
		muteLogger.mute();
		
		ConfigurationException e = assertThrows(ConfigurationException.class, () -> new Configuration(INCORRECT_BASE_DIRECTORY));
		assertThat(e.getMessage(), containsString("load configuration"));
		assertEquals(1, e.getDetails().size());
		assertThat(StringUtils.join(e.getDetails(), "\n"), containsString(INCORRECT_BASE_DIRECTORY));
		
		muteLogger.restore();
	}

}

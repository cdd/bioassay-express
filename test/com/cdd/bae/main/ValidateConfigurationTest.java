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

package com.cdd.bae.main;

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;

import org.junit.jupiter.api.*;

/*
	Test for ValidateConfiguration.
*/

public class ValidateConfigurationTest
{
	private static final String HELP_FRAGMENT = "Command line options";

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
	}

	@Test
	public void testPrintHelp()
	{
		// there are several ways of getting help
		assertThat(TestCommandLine.captureOutput(() -> ValidateConfiguration.main(new String[0])), containsString(HELP_FRAGMENT));
		assertThat(TestCommandLine.captureOutput(() -> ValidateConfiguration.main(new String[]{"-h"})), containsString(HELP_FRAGMENT));
		assertThat(TestCommandLine.captureOutput(() -> ValidateConfiguration.main(new String[]{"--help"})), containsString(HELP_FRAGMENT));
	}

	@Test
	public void testValidation()
	{
		final String configFile = TestConfiguration.getProductionConfiguration() + "/config.json";
		if (!new File(configFile).isFile()) return;
		
		String result = TestCommandLine.captureOutput(() -> ValidateConfiguration.main(new String[] {configFile}));
		assertThat(result, containsString("Configuration is valid"));
	}
}

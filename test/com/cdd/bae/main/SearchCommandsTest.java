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
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;

import java.io.*;

import org.junit.jupiter.api.*;

/*
	Test for SearchCommands.
*/

public class SearchCommandsTest
{
	private static final String UNABLE_TO_PARSE = "Unable to parse";
	private static final String BLOCKS = "Blocks:";
	private static final String KEYWORD = "keyword";
	private SearchCommands searchCommands;

	@BeforeEach
	public void initialize() throws ConfigurationException, IOException
	{
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);

		searchCommands = new SearchCommands();
	}

	@Test
	public void testPrintHelp()
	{
		String output = TestCommandLine.captureOutput(() -> searchCommands.printHelp());
		assertThat(output, containsString("Options"));

		// help is printed for empty arguments
		output = executeCommand();
		assertThat(output, containsString("Options"));
	}

	@Test
	public void testKeywordSearch()
	{
		String output = executeCommand(KEYWORD);
		assertThat(output, containsString("Keyword is required"));

		output = executeCommand(KEYWORD, "kw1");
		assertThat(output, containsString("Keyword:"));
		assertThat(output, containsString(BLOCKS));
		assertThat(output, containsString("Formulated clauses:"));

		output = executeCommand(KEYWORD, "kw1 > ");
		assertThat(output, containsString(UNABLE_TO_PARSE));

		output = executeCommand(KEYWORD, "NOT kw1");
		assertThat(output, containsString(UNABLE_TO_PARSE));

		// two or more keywords throw an exception
		output = executeCommand(KEYWORD, "kw1", "kw2");
		assertThat(output, startsWith("Exception:"));
		assertThat(output, containsString("Unexpected parameter: kw2"));

		// unknown flags throw an exception
		output = executeCommand(KEYWORD, "-unknown");
		assertThat(output, startsWith("Exception:"));
		assertThat(output, containsString("Unexpected parameter: -unknown"));
	}

	@Test
	public void testUnknown()
	{
		String output = executeCommand("unknown");
		assertThat(output, containsString("Unknown command"));
	}

	// ------------ private methods ------------

	private String executeCommand(String... commands)
	{
		return TestCommandLine.captureOutput(() ->
			{
				try
				{
					searchCommands.execute(commands);
				}
				catch (IOException e)
				{
					Util.writeln("Exception: " + e.getMessage());
				}
			});
	}
}

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

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import org.junit.jupiter.api.*;

/*
	Test for Main command line interface.
*/

public class MainTest
{
	private final TestUtilities.MuteLogger muteLogger = new TestUtilities.MuteLogger(Main.class);

	private static final String UNIVERSAL_OPTIONS = "Universal options";
	private static final String SUBCOMMAND_OPTIONS = "Options";
	private static final String UNKNOWN_COMMAND = "Unknown command";

	@BeforeEach
	public void initialize() throws ConfigurationException, IOException
	{
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.makeBootstrapped();
	}

	@Test
	public void testPrintHelp()
	{
		// there are several ways of getting help
		assertThat(TestCommandLine.captureOutput(() -> Main.main(new String[0])), containsString(UNIVERSAL_OPTIONS));
		assertThat(TestCommandLine.captureOutput(() -> Main.main(new String[]{"-h"})), containsString(UNIVERSAL_OPTIONS));
		assertThat(TestCommandLine.captureOutput(() -> Main.main(new String[]{"--help"})), containsString(UNIVERSAL_OPTIONS));
		assertThat(TestCommandLine.captureOutput(() -> Main.main(new String[]{"help"})), containsString(UNIVERSAL_OPTIONS));

		// we can also access help information for all commands
		for (String[] commandInformation : Main.COMMANDS)
		{
			String command = commandInformation[0];
			if (command.equals("geneid") || command.equals("protein"))
			{
				muteLogger.mute();
				assertThat(TestCommandLine.captureOutput(() -> Main.main(new String[]{"help", command})), containsString(UNKNOWN_COMMAND));
				muteLogger.mute();
			}
			else
				assertThat(TestCommandLine.captureOutput(() -> Main.main(new String[]{"help", command})), containsString(SUBCOMMAND_OPTIONS));
		}

		// and finally get subcommand help if we give no options (this is tested for individual commands)
	}

	@Test
	public void testObtainBase()
	{
		for (String[] commandInformation : Main.COMMANDS)
		{
			String command = commandInformation[0];
			Main.ExecuteBase base = Main.obtainBase(command);
			if (command.equals("geneid") || command.equals("protein"))
			{
				muteLogger.mute();
				assertNull(base);
				muteLogger.restore();
			}
			else
				assertNotNull(base);
		}

		assertNull(Main.obtainBase(UNKNOWN_COMMAND), "returns null for unknown command");
	}
}

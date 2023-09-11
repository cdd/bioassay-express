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
	Test for UserCommands.
*/

public class UserCommandsTest
{
	private static final String RENAME = "rename";
	private static final String CHANGE_APPLIED = "Change applied";
	private static final String UNKNOWN = "unknown";
	private static final String STATUS = "status";
	private UserCommands userCommands;
	private DataUser dataUser;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		userCommands = new UserCommands();
		
		dataUser = new DataUser(mongo.getDataStore());
		dataUser.submitSession(DataStoreSupport.makeUserSession("alf", "Alf"));
		dataUser.submitSession(DataStoreSupport.makeUserSession("worf", "Worf"));
		dataUser.submitSession(DataStoreSupport.makeUserSession("noname", null));
		dataUser.changeStatus("alf", DataUser.STATUS_DEFAULT);
		dataUser.changeStatus("worf", DataUser.STATUS_DEFAULT);
		dataUser.changeStatus("noname", DataUser.STATUS_DEFAULT);
	}

	@Test
	public void testPrintHelp()
	{
		String output = TestCommandLine.captureOutput(() -> userCommands.printHelp());
		assertThat(output, containsString("Options"));

		// help is printed for empty arguments
		output = executeCommand();
		assertThat(output, containsString("Options"));
	}

	@Test
	public void testList()
	{
		String output = executeCommand("list");
		assertThat(output, containsString("<default> 'Worf'"));
		assertThat(output, containsString("<default> 'Alf'"));
		assertThat(output, containsString("[noname]: <default> 'null'"));
	}

	@Test
	public void testStatus()
	{
		// require two options
		String output = executeCommand(STATUS);
		assertThat(output, containsString("Parameters are {curatorID} {new status}"));

		// status must be a valid name
		output = executeCommand(STATUS, "a", "b");
		assertThat(output, containsString("Parameter status 'b' invalid"));

		// user must exist
		output = executeCommand(STATUS, UNKNOWN, DataUser.STATUS_DEFAULT);
		assertThat(output, containsString("User not present"));

		// status not changed
		assertThat(dataUser.getUser("worf").status, is(DataUser.STATUS_DEFAULT));
		output = executeCommand(STATUS, "worf", DataUser.STATUS_DEFAULT);
		assertThat(output, containsString("Status unchanged."));
		assertThat(dataUser.getUser("worf").status, is(DataUser.STATUS_DEFAULT));

		// success message if status was changed
		output = executeCommand(STATUS, "worf", DataUser.STATUS_BLOCKED);
		assertThat(output, containsString(CHANGE_APPLIED));
		assertThat(dataUser.getUser("worf").status, is(DataUser.STATUS_BLOCKED));

		executeCommand(STATUS, "worf", DataUser.STATUS_CURATOR);
		assertThat(dataUser.getUser("worf").status, is(DataUser.STATUS_CURATOR));

		executeCommand(STATUS, "worf", DataUser.STATUS_ADMIN);
		assertThat(dataUser.getUser("worf").status, is(DataUser.STATUS_ADMIN));
	}

	@Test
	public void testRename()
	{
		// require two options
		String output = executeCommand(RENAME);
		assertThat(output, containsString("Parameters are {curatorID} {new name}"));

		// user must exist
		output = executeCommand(RENAME, UNKNOWN, "b");
		assertThat(output, containsString("User not present"));

		// name hasn't changed
		output = executeCommand(RENAME, "worf", "Worf");
		assertThat(output, containsString("Name unchanged"));

		// but apply if user name is null
		output = executeCommand(RENAME, "noname", "No Name");
		assertThat(output, containsString(CHANGE_APPLIED));
		assertThat(dataUser.getUser("noname").name, is("No Name"));

		// and of course if the name changes
		output = executeCommand(RENAME, "worf", "Worf Klingon");
		assertThat(output, containsString(CHANGE_APPLIED));
		assertThat(dataUser.getUser("worf").name, is("Worf Klingon"));
	}
	
	@Test
	public void testUnknown()
	{
		assertThat(executeCommand(UNKNOWN), containsString("Unknown command"));
	}

	// ------------ private methods ------------

	private String executeCommand(String... commands)
	{
		return TestCommandLine.captureOutput(() -> userCommands.execute(commands));
	}
}

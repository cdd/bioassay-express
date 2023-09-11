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

import java.io.*;

import org.junit.jupiter.api.*;

/*
	Test for HoldingCommands.
*/

public class HoldingCommandsTest
{
	private DataHolding dataHolding;
	private DataAssay dataAssay;
	private HoldingCommands holdingCommands;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		holdingCommands = new HoldingCommands();
		holdingCommands.delayEmptyHoldingBay = 0;

		DataUser dataUser = new DataUser(mongo.getDataStore());
		dataUser.submitSession(DataStoreSupport.makeUserSession("curator", "Alf"));
		dataUser.submitSession(DataStoreSupport.makeUserSession("admin", "Worf"));

		dataHolding = new DataHolding(mongo.getDataStore());
		dataHolding.depositHolding(DataStoreSupport.makeHolding(2268L, 2, "curator"));
		dataHolding.depositHolding(DataStoreSupport.makeHolding(2269L, 3, "admin"));

		dataAssay = new DataAssay(mongo.getDataStore());
	}

	@Test
	public void testPrintHelp()
	{
		String output = TestCommandLine.captureOutput(() -> holdingCommands.printHelp());
		assertThat(output, containsString("Options"));

		// help is printed for empty arguments
		output = executeCommand();
		assertThat(output, containsString("Options"));
	}

	@Test
	public void testListEntries()
	{
		String output = executeCommand("list");
		assertThat(output, containsString("Holding Bay entries..."));
		assertThat(output, containsString("Assay#2268"));
		assertThat(output, containsString("Alf"));
		assertThat(output, containsString("Assay#2269"));
		assertThat(output, containsString("Worf"));
		assertThat(output, containsString("Done"));
	}

	@Test
	public void testListCurators()
	{
		String output = executeCommand("curators");
		assertThat(output, containsString("Holding Bay entries by curator"));
		assertThat(output, containsString("admin ('Worf', 'Worf@b.com'):\n    10000001"));
		assertThat(output, containsString("curator ('Alf', 'Alf@b.com'):\n    10000000"));
		assertThat(output, containsString("Done"));
	}

	@Test
	public void testApproveEntries()
	{
		int nassay = dataAssay.countAssays();
		int nholding = dataHolding.countTotal();

		String output = executeCommand("approve");
		assertThat(output, containsString("Approving entries in Holding Bay: \nDone"));

		output = executeCommand("approve", "10000001", "4567", "3");
		assertThat(output, containsString("Approving entries in Holding Bay: 10000001,4567,3"));
		assertThat(output, containsString("Applying holdingID #10000001"));
		assertThat(output, containsString("holdingID #4567 not found."));
		assertThat(output, containsString("Done"));
		assertThat(output, containsString("# approved: 2"));

		assertThat(dataHolding.countTotal(), is(nholding - 2));
		assertThat(dataAssay.countAssays(), is(nassay + 2));
	}

	@Test
	public void testDeleteEntries()
	{
		int nholding = dataHolding.countTotal();
		
		String output = executeCommand("delete");
		assertThat(output, containsString("Deleting entries in Holding Bay: \nDone"));

		output = executeCommand("delete", "10000001", "4567");
		assertThat(output, containsString("Deleting entries in Holding Bay: 10000001,4567"));
		assertThat(output, containsString("Deleting holdingID #10000001"));
		assertThat(output, containsString("holdingID #4567 not found"));
		assertThat(output, containsString("Done"));

		assertThat(dataHolding.countTotal(), is(nholding - 1));
	}

	@Test
	public void testDeleteAllEntries()
	{
		String output = executeCommand("empty");
		assertThat(output, containsString("Deleting all entries"));
		assertThat(output, containsString("About to delete 6 entries."));
		assertThat(output, containsString("Deleting holdingID #1"));
		assertThat(output, containsString("Deleting holdingID #2"));
		assertThat(output, containsString("Deleting holdingID #3"));
		assertThat(output, containsString("Deleting holdingID #16"));
		assertThat(output, containsString("Deleting holdingID #10000000"));
		assertThat(output, containsString("Deleting holdingID #10000001"));
		assertThat(output, containsString("Done"));

		assertThat(dataHolding.countTotal(), is(0));
	}

	// ------------ private methods ------------

	private String executeCommand(String... commands)
	{
		return TestCommandLine.captureOutput(() -> holdingCommands.execute(commands));
	}
}

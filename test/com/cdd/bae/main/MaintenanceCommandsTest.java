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
	Test for MaintenanceCommands.
*/

public class MaintenanceCommandsTest
{
	private static final String REMEASUREPUBCHEM = "remeasurepubchem";
	private MaintenanceCommands maintenanceCommands;
	private DataAssay dataAssay;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
		dataAssay = new DataAssay(mongo.getDataStore());
		
		maintenanceCommands = new MaintenanceCommands();
	}

	@Test
	public void testPrintHelp()
	{
		String output = TestCommandLine.captureOutput(() -> maintenanceCommands.printHelp());
		assertThat(output, containsString("Options"));

		// help is printed for empty arguments
		output = executeCommand();
		assertThat(output, containsString("Options"));
	}

	@Test
	public void testRebuildModels() throws ConfigurationException, IOException
	{
		FauxMongo mongoModel = FauxMongo.getInstance("/testData/db/model");
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongoModel.getDataStore());
		DataModel dataModel = new DataModel(mongoModel.getDataStore());
		DataNLP dataNLP = new DataNLP(mongoModel.getDataStore());
		DataStoreSupport.addFingerprints(dataNLP);

		assertThat(dataModel.countModelCorr(), is(5));
		assertThat(dataModel.countModelNLP(), is(3));
		assertThat(dataNLP.countFingerprints(), is(6));

		String output = executeCommand("rebuildmodels");
		assertThat(output, containsString("Model data deleted"));

		assertThat(dataModel.countModelCorr(), is(0));
		assertThat(dataModel.countModelNLP(), is(0));
		assertThat(dataNLP.countFingerprints(), is(0));

		mongoModel.restoreContent();
	}

	@Test
	public void testRefreshModels() throws ConfigurationException, IOException
	{
		FauxMongo mongoModel = FauxMongo.getInstance("/testData/db/model");
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongoModel.getDataStore());
		DataModel dataModel = new DataModel(mongoModel.getDataStore());
		
		assertThat(dataModel.countModelCorr(), is(5));
		assertThat(dataModel.countModelNLP(), is(3));

		String output = executeCommand("refreshmodels");
		assertThat(output, containsString("Suggestion models deleted"));

		assertThat(dataModel.countModelCorr(), is(0));
		assertThat(dataModel.countModelNLP(), is(0));
		
		mongoModel.restoreContent();
	}

	@Test
	public void testMissingSchema()
	{
		String output = executeCommand("missingschema");
		assertThat(output, containsString("Looking for missing schemaURI..."));
		assertThat(output, containsString("Done: 0 assays changed"));
	}

	@Test
	public void testConformSchema()
	{
		String output = executeCommand("conformschema");
		assertThat(output, containsString("Conforming assays to schema..."));
		assertThat(output, containsString("Done: 0 assays changed"));
	}

	@Test
	public void testBumpLoadedAssays()
	{
		Common.makeBootstrapped();
		String output = executeCommand("bumploadedassays");
		assertThat(output, containsString("Bumped"));
	}

	@Test
	public void testUpdateStructureHash() throws ConfigurationException, IOException
	{
		FauxMongo mongoCompound = FauxMongo.getInstance("/testData/db/compound");
		
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongoCompound.getDataStore());
		DataCompound dataCompound = new DataCompound(mongoCompound.getDataStore());
		
		assertThat(dataCompound.getCompound(4).hashECFP6, is(0));
		assertThat(dataCompound.getCompound(5).hashECFP6, is(0));
		
		String output = executeCommand("structurehash");
		assertThat(output, containsString("Fetched: 2 (first ID=4)"));
		assertThat(output, containsString("Finished. Total updated: 2"));
		
		assertThat(dataCompound.getCompound(4).hashECFP6, not(nullValue()));
		assertThat(dataCompound.getCompound(5).hashECFP6, not(nullValue()));
	}

	@Test
	public void testRemeasurePubChem()
	{
		String output = executeCommand(REMEASUREPUBCHEM);
		assertThat(output, containsString("Provide a list of PubChem AID numbers"));

		assertThat(dataAssay.getAssay(2).measureChecked, is(true));

		output = executeCommand(REMEASUREPUBCHEM, "all");
		assertThat(output, containsString("Marking AID 1020"));
		assertThat(output, containsString("Done"));
		assertThat(dataAssay.getAssay(2).measureChecked, is(false));

		dataAssay.submitPubChemAIDMeasured(2, true);
		output = executeCommand(REMEASUREPUBCHEM, "1020");
		assertThat(output, containsString("Marking AID 1020"));
		assertThat(output, containsString("Done"));
		assertThat(dataAssay.getAssay(2).measureChecked, is(false));
	}

	@Test
	public void testThresholdPubChem()
	{
		String output = executeCommand("thresholdpubchem");
		assertThat(output, containsString("Done. Calculated: 0"));
	}

	// ------------ private methods ------------

	private String executeCommand(String... commands)
	{
		return TestCommandLine.captureOutput(() -> maintenanceCommands.execute(commands));
	}
}

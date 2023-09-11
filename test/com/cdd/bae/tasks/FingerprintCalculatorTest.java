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

package com.cdd.bae.tasks;

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import org.junit.jupiter.api.*;

/*
	Test for com.cdd.bae.tasks.FingerprintCalculator
*/

public class FingerprintCalculatorTest
{
	static private final int NR_BLOCKS = 69; // number of blocks found in assayDescription
	
	private Configuration configuration;
	private DataStore.Assay assay1;
	private DataStore.Assay assay2;
	private DataStore.Assay assay3;
	private DataStore.Assay assay4;
	// Database API
	private DataStore store;
	private DataNLP dataNLP;
	private DataAssay dataAssay;

	public TestResourceFile assayDescription = new TestResourceFile("/testData/nlp/AssayDescription.txt");

	@BeforeEach
	public void initialize() throws ConfigurationException, IOException
	{
		// test configuration - note that TestConfiguration requires a working configuration
		configuration = TestConfiguration.getConfiguration();
		Common.setConfiguration(configuration);

		assay1 = new DataStore.Assay();
		assay1.text = assayDescription.getContent();
		assay1.assayID = 100;

		assay2 = new DataStore.Assay();
		assay2.text = assayDescription.getContent();
		assay2.fplist = new int[]{};
		assay2.assayID = 200;

		assay3 = new DataStore.Assay();
		assay3.text = "";
		assay3.assayID = 300;

		assay4 = new DataStore.Assay();
		assay4.text = "Text available";
		assay4.fplist = new int[]{1, 2, 3};
		assay4.assayID = 400;

		// mock the database
		store = mock(DataStore.class);
		dataNLP = mock(DataNLP.class);
		when(store.nlp()).thenReturn(dataNLP);
		doNothing().when(dataNLP).addNLPFingerprint(anyString(), anyInt());
		dataAssay = mock(DataAssay.class);
		when(store.assay()).thenReturn(dataAssay);
		doNothing().when(dataAssay).submitAssayFingerprints(anyLong(), any(int[].class));
		doReturn(assay1).when(dataAssay).getAssay(100);
		doReturn(assay2).when(dataAssay).getAssay(200);
		doReturn(assay3).when(dataAssay).getAssay(300);
		doReturn(assay4).when(dataAssay).getAssay(400);
	}

	@Test
	public void testDoTask()
	{
		// we require a configuration to work
		if (Common.getConfiguration() == null) return;

		// mock the database side
		FingerprintCalculator fpCalculator = new FingerprintCalculator();
		fpCalculator.store = store;
		fpCalculator.logger = TestUtilities.mockLogger();

		// prepare the return values
		doReturn(new long[]{100, 200, 300}).when(dataAssay).fetchAssayIDWithoutFP();
		Map<String, Integer> blockToFP = new HashMap<>();
		doReturn(blockToFP).when(dataNLP).fetchFingerprints();

		boolean workDone = fpCalculator.doTask();
		assertFalse(workDone, "We processed assays");
		assertEquals(NR_BLOCKS, blockToFP.size(), "Number of new text blocks found");

		// the next list only has an empty assay.text
		doReturn(new long[]{300, 400}).when(dataAssay).fetchAssayIDWithoutFP();
		workDone = fpCalculator.doTask();
		assertTrue(workDone, "Only empty assays or with available fingerprints, therefore no");

		// task can be stopped
		doReturn(new long[]{100, 200, 300}).when(dataAssay).fetchAssayIDWithoutFP();
		fpCalculator.stopped = true;
		workDone = fpCalculator.doTask();
		assertTrue(workDone, "task was stopped, nothing calculated");
	}

	@Test
	public void testRecalculate()
	{
		// we require a configuration to work
		if (Common.getConfiguration() == null) return;

		// mock the database side
		FingerprintCalculator fpCalculator = new FingerprintCalculator();
		fpCalculator.store = store;

		// and test the recalculate method
		Map<String, Integer> blockToFP = new HashMap<>();
		fpCalculator.recalculate(assay1, blockToFP);
		int nIndices = blockToFP.size();
		int[] expectedFP = IntStream.rangeClosed(1, nIndices).toArray();

		// NLP fingerprint lookup table is updated, a new fingerprint is stored
		verify(dataNLP, times(nIndices)).addNLPFingerprint(anyString(), anyInt());
		verify(dataAssay, times(1)).submitAssayFingerprints(assay1.assayID, expectedFP);
		verify(dataAssay).submitAssayFingerprints(assay1.assayID, expectedFP);

		// Now we just do lookups in blockToFP, so only the number of submitAssayFingerprints
		// increases. To verify this, we modify the lookup table
		blockToFP.replaceAll((k, v) -> v + 1000);
		expectedFP = IntStream.rangeClosed(1001, nIndices + 1000).toArray();

		fpCalculator.recalculate(assay1, blockToFP);
		// NLP fingerprint lookup table is not updated, a new fingerprint is stored
		verify(dataNLP, times(nIndices)).addNLPFingerprint(anyString(), anyInt());
		verify(dataAssay, times(2)).submitAssayFingerprints(anyLong(), any(int[].class));
		verify(dataAssay).submitAssayFingerprints(assay1.assayID, expectedFP);
	}

}

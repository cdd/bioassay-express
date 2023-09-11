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

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.*;

/*
	Test for com.cdd.bae.tasks.CorrelationBuilder
 */

public class CorrelationBuilderTest
{
	public TestResourceFile assayDescription = new TestResourceFile("/testData/nlp/AssayDescription.txt");

	@BeforeEach
	public void initialize() throws ConfigurationException, IOException
	{
		// test configuration - note that TestConfiguration requires a working configuration
		Configuration configuration = TestConfiguration.getConfiguration();
		Common.setConfiguration(configuration);
		
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Common.setDataStore(mongo.getDataStore());
	}

	@Test
	public void testCreateAllModels()
	{
		// we require a configuration to work
		if (Common.getConfiguration() == null) return;
		
		DataModel dataModel = Common.getDataStore().model();
		DataAnnot dataAnnot = Common.getDataStore().annot();
		long currentWatermark = dataModel.getWatermarkCorr();
		
		int nTargets = dataModel.allTargetsCorr().size();
		int nAnnotFP = dataAnnot.fetchAnnotationFP().length;

		CorrelationBuilder builder = new CorrelationBuilder();
		builder.logger = TestUtilities.mockLogger();

		// Call with a different watermark - nothing happens
		builder.createAllModels(currentWatermark - 1);
		assertThat(dataModel.allTargetsCorr().size(), is(nTargets));
		// this depends on the test database to have annotations without fingerprint
		// should this not be the case, then we need to add a new annotation to make 
		// sure that we always call updateAnnotationFP
		assertThat(dataAnnot.fetchAnnotationFP().length, greaterThan(nAnnotFP));
		nAnnotFP = dataAnnot.fetchAnnotationFP().length;

		// Call with the watermark that is expected - models updated
		builder.createAllModels(currentWatermark);
		assertThat(dataModel.allTargetsCorr().size(), greaterThan(nTargets));
		assertThat(dataAnnot.fetchAnnotationFP().length, is(nAnnotFP));
	}

	@Test
	public void testBuildModel()
	{
		DataStore.Model model;

		int[][] fplist = new int[][]{new int[]{1, 2, 3, 5, 6}, new int[]{1, 4}, new int[]{1, 4}, new int[]{1, 4, 5, 6}, new int[]{1, 2, 4, 5, 6}};
		boolean[] active = new boolean[]{false, true, true, true, false};

		// get the cases out of the way where we have nothing to do
		assertNull(CorrelationBuilder.buildModel(new int[][]{}, new boolean[]{}), "No fingerprints");
		assertNull(CorrelationBuilder.buildModel(fplist, new boolean[]{false, false, false, false, false}), "No actives");
		assertNull(CorrelationBuilder.buildModel(fplist, new boolean[]{true, true, true, true, true}), "All actives");

		model = CorrelationBuilder.buildModel(fplist, active);
		assertTrue(Arrays.equals(model.fplist, new int[]{2, 3, 4, 5, 6}), "Feature 1 occurs everywhere and is ignored");
		assertEquals(model.contribs[0], Math.log(1 / 2.2), 0.0001);
		assertEquals(model.contribs[1], Math.log(1 / 1.6), 0.0001);
		assertEquals(model.contribs[2], Math.log(4 / 3.4), 0.0001);
		assertEquals(model.contribs[3], Math.log(2 / 2.8), 0.0001);
		assertEquals(model.contribs[4], Math.log(2 / 2.8), 0.0001);
		assertEquals(-1.6151441, model.calibLow, 0.0001);
		assertEquals(-0.1941643, model.calibHigh, 0.0001);
	}

}

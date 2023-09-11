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
import java.util.stream.*;

import org.junit.jupiter.api.*;

/*
	Test for com.cdd.bae.tasks.ModelBuilder
 */

public class ModelBuilderTest
{
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
		DataModel dataModel = Common.getDataStore().model();
		DataAnnot dataAnnot = Common.getDataStore().annot();
		long currentWatermark = dataModel.getWatermarkNLP();
		
		int nTargets = dataModel.allTargetsNLP().size();
		int nAnnotFP = dataAnnot.fetchAnnotationFP().length;

		ModelBuilder builder = new ModelBuilder();
		builder.logger = TestUtilities.mockLogger();

		// Call with a different watermark - nothing happens
		builder.createAllModels(currentWatermark - 1);
		assertThat(dataModel.allTargetsNLP().size(), is(nTargets));
		// this depends on the test database to have annotations without fingerprint
		// should this not be the case, then we need to add a new annotation to make 
		// sure that we always call updateAnnotationFP
		assertThat(dataAnnot.fetchAnnotationFP().length, greaterThan(nAnnotFP));
		nAnnotFP = dataAnnot.fetchAnnotationFP().length;

		// Call with the watermark that is expected - models updated
		builder.createAllModels(currentWatermark);
		assertThat(dataModel.allTargetsNLP().size(), greaterThan(nTargets));
		assertThat(dataAnnot.fetchAnnotationFP().length, is(nAnnotFP));
		
	}
	
	@Test
	public void testBuildModel()
	{
		DataStore.Model model;
		ModelBuilder modelBuilder = new ModelBuilder();

		int[][] fplist = new int[][]{
				new int[]{1, 2, 3, 5, 6},
				new int[]{1, 4},
				new int[]{1, 4},
				new int[]{1, 4, 5, 6},
				new int[]{1, 2, 4, 5, 6}
		};
		boolean[] active = new boolean[]{false, true, true, true, false};

		// get the cases out of the way where we have nothing to do
		assertNull(modelBuilder.buildModel(new int[][]{}, new boolean[]{}), "No fingerprints");
		assertNull(modelBuilder.buildModel(fplist, new boolean[]{false, false, false, false, false}), "No actives");
		assertNull(modelBuilder.buildModel(fplist, new boolean[]{true, true, true, true, true}), "All actives");

		model = modelBuilder.buildModel(fplist, active);
		assertTrue(Arrays.equals(model.fplist, new int[]{2, 3, 4, 5, 6}), "Feature 1 occurs everywhere and is ignored");
		assertEquals(model.contribs[0], Math.log(1 / 2.2), 0.0001);
		assertEquals(model.contribs[1], Math.log(1 / 1.6), 0.0001);
		assertEquals(model.contribs[2], Math.log(4 / 3.4), 0.0001);
		assertEquals(model.contribs[3], Math.log(2 / 2.8), 0.0001);
		assertEquals(model.contribs[4], Math.log(2 / 2.8), 0.0001);
		assertEquals(-1.6151441, model.calibLow, 0.0001);
		assertEquals(-0.1941643, model.calibHigh, 0.0001);
	}

	@Test
	public void testPruneFingerprints()
	{
		final int maxSize = 10;
		Map<Integer, Integer> count = new HashMap<>();
		List<int[]> list = new ArrayList<>();

		prepareTestDataPruneFingerprints(maxSize, count, list);
		ModelBuilder.pruneFingerprints(count, list, 10);
		assertPrunedFingerprints(count, list, 10, 1, 10);

		prepareTestDataPruneFingerprints(maxSize, count, list);
		ModelBuilder.pruneFingerprints(count, list, 9);
		assertPrunedFingerprints(count, list, 9, 1, 9);

		prepareTestDataPruneFingerprints(maxSize, count, list);
		ModelBuilder.pruneFingerprints(count, list, 8);
		assertPrunedFingerprints(count, list, 8, 2, 9);

		prepareTestDataPruneFingerprints(maxSize, count, list);
		ModelBuilder.pruneFingerprints(count, list, 4);
		assertPrunedFingerprints(count, list, 4, 4, 7);

		prepareTestDataPruneFingerprints(maxSize, count, list);
		ModelBuilder.pruneFingerprints(count, list, 2);
		assertPrunedFingerprints(count, list, 2, 5, 6);
	}

	@Test
	public void testTargetInPriorityOrder()
	{
		Set<Integer> requiredTargets = new HashSet<>(Arrays.asList(101, 102, 104, 103));
		SortedMap<Long, List<Integer>> modelsByWatermark = new TreeMap<>();

		// no existing models
		assertEqualOrder(new int[]{101, 102, 103, 104}, ModelBuilder.targetInPriorityOrder(requiredTargets, modelsByWatermark));

		// existing models but no overlap with required targets
		modelsByWatermark.put((long)1, Arrays.asList(14, 15));
		assertEqualOrder(new int[]{101, 102, 103, 104, 14, 15}, ModelBuilder.targetInPriorityOrder(requiredTargets, modelsByWatermark));

		// existing models have overlap with required targets
		modelsByWatermark.put((long)1, Arrays.asList(14, 15, 101, 102));
		assertEqualOrder(new int[]{103, 104, 14, 15, 101, 102}, ModelBuilder.targetInPriorityOrder(requiredTargets, modelsByWatermark));

		// existing models with different watermark
		modelsByWatermark.put((long)2, Arrays.asList(1, 2, 3));
		assertEqualOrder(new int[]{103, 104, 14, 15, 101, 102, 1, 2, 3}, ModelBuilder.targetInPriorityOrder(requiredTargets, modelsByWatermark));
		
		// required targets is empty
		requiredTargets.clear();
		assertEqualOrder(new int[]{14, 15, 101, 102, 1, 2, 3}, ModelBuilder.targetInPriorityOrder(requiredTargets, modelsByWatermark));
	}

	private void prepareTestDataPruneFingerprints(int maxSize, Map<Integer, Integer> count, List<int[]> list)
	{
		count.clear();
		list.clear();
		for (int i = 1; i <= maxSize; i++)
		{
			count.put(i, i);
			list.add(new int[maxSize - i + 1]);
		}
		for (int i = 1; i <= maxSize; i++)
			for (int j = 1; j <= i; ++j)
				list.get(j - 1)[i - j] = i;
	}

	private void assertPrunedFingerprints(Map<Integer, Integer> count, List<int[]> list, int maxSize, int minValue, int maxValue)
	{
		assertEquals(count.size(), maxSize);
		assertEquals((int)Collections.min(count.values()), minValue);
		assertEquals((int)Collections.max(count.values()), maxValue);
		for (int[] fp : list)
		{
			if (fp.length == 0) continue;
			List<Integer> fpList = Arrays.stream(fp).boxed().collect(Collectors.toList());
			assertTrue(minValue <= (int)Collections.min(fpList));
			assertTrue(maxValue >= (int)Collections.max(fpList));
		}
	}

	private void assertEqualOrder(int[] expected, List<Integer> observed)
	{
		assertEquals(expected.length, observed.size());
		for (int i = 0; i < expected.length; i++)
			assertEquals(expected[i], observed.get(i).intValue());
	}
}

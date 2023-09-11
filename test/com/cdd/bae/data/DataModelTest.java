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

package com.cdd.bae.data;

import com.cdd.bae.data.DataObject.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import org.junit.jupiter.api.*;

/*
	Data model test.
*/

public class DataModelTest
{
	DataModel dataModel;

	@BeforeEach
	public void initialize()
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/model");
		dataModel = new DataModel(mongo.getDataStore());
	}

	@Test
	public void testWatermark() throws IOException
	{
		assertEquals(10000000, dataModel.getWatermarkNLP());
		assertEquals(10000000, dataModel.nextWatermarkNLP());
		assertEquals(10000001, dataModel.getWatermarkNLP());

		assertEquals(10000000, dataModel.getWatermarkCorr());
		assertEquals(10000000, dataModel.nextWatermarkCorr());
		assertEquals(10000001, dataModel.getWatermarkCorr());

		assertEquals(1, dataModel.getModelNLPWatermark(101));
		assertEquals(2, dataModel.getModelNLPWatermark(102));
		assertEquals(2, dataModel.getModelNLPWatermark(103));
		assertEquals(0, dataModel.getModelNLPWatermark(123123123));

		assertEquals(11, dataModel.getModelCorrWatermark(101));
		assertEquals(22, dataModel.getModelCorrWatermark(102));
		assertEquals(22, dataModel.getModelCorrWatermark(103));
		assertEquals(0, dataModel.getModelCorrWatermark(123123123));

		assertGrouping("{1=[101], 2=[102, 103]}", dataModel.groupNLPByWatermarks());
		assertGrouping("{11=[101], 22=[102, 103, 104], 10000000=[105]}", dataModel.groupCorrByWatermarks());
	}

	@Test
	public void testGetModel() throws IOException
	{
		Model model = dataModel.getModelNLP(101);
		assertEquals(101, model.target);
		assertEquals(1, model.watermark);

		model = dataModel.getModelCorr(102);
		assertEquals(102, model.target);
		assertEquals(22, model.watermark);

		// If model doesn't exist, it will be temporarily blocked
		assertThat(dataModel.hasModelNLP(1111), is(false));
		model = dataModel.getModelNLP(1111);
		assertThat(model, is(nullValue()));
		// has model indicates true
		assertThat(dataModel.hasModelNLP(1111), is(true));
		// but we will not be able to get a model
		assertThat(dataModel.getModelNLP(1111), is(nullValue()));

		// Will return model when found in cache and having the same watermark
		assertThat(dataModel.getModelCorrWatermark(105), is(10000000L));
		model = dataModel.getModelCorr(105);
		assertThat(model.watermark, is(10000000L));
		// removing this line will show that the cache isn't used in coverage
		model = dataModel.getModelCorr(105);
		assertThat(model.watermark, is(10000000L));
	}

	@Test
	public void testBlankModel() throws IOException
	{
		Model model = dataModel.getModelNLP(101);
		assertEquals(1, model.watermark);
		assertEquals(6.0, model.calibHigh);

		dataModel.blankModelNLP(101, 200);
		model = dataModel.getModelNLP(101);
		assertEquals(200, model.watermark);
		assertThat(Float.isNaN(model.calibHigh), is(true));

		model = dataModel.getModelCorr(101);
		assertEquals(11, model.watermark);
		assertEquals(5.0, model.calibLow);

		dataModel.blankModelCorr(101, 2200);
		model = dataModel.getModelCorr(101);
		assertEquals(2200, model.watermark);
		assertThat(Float.isNaN(model.calibLow), is(true));
	}

	@Test
	public void testCounts()
	{
		assertThat(dataModel.countModelNLP(), is(3));
		assertThat(dataModel.countModelCorr(), is(5));
	}

	@Test
	public void testAllTargets()
	{
		assertEquals(Stream.of(101, 102, 103).collect(Collectors.toSet()), dataModel.allTargetsNLP());
		assertEquals(Stream.of(101, 102, 103, 104, 105).collect(Collectors.toSet()), dataModel.allTargetsCorr());
	}

	@Test
	public void testHasModel()
	{
		assertThat(dataModel.hasModelNLP(101), is(true));
		assertThat(dataModel.hasModelNLP(104), is(false));

		assertThat(dataModel.hasModelCorr(101), is(true));
		assertThat(dataModel.hasModelCorr(105), is(true));
		assertThat(dataModel.hasModelCorr(106), is(false));
	}

	@Test
	public void testDeleteModel() throws IOException
	{
		dataModel.allTargetsNLP();

		assertThat(dataModel.hasModelNLP(101), is(true));
		dataModel.deleteModelNLP(101);
		assertThat(dataModel.hasModelNLP(101), is(false));

		assertThat(dataModel.hasModelCorr(102), is(true));
		dataModel.deleteModelCorr(102);
		assertThat(dataModel.hasModelCorr(102), is(false));

		assertEquals(Stream.of(102, 103).collect(Collectors.toSet()), dataModel.allTargetsNLP());
		assertEquals(Stream.of(101, 103, 104, 105).collect(Collectors.toSet()), dataModel.allTargetsCorr());
		assertEquals(Stream.of(102, 103).collect(Collectors.toSet()), dataModel.allTargetsNLP());
		assertEquals(Stream.of(101, 103, 104, 105).collect(Collectors.toSet()), dataModel.allTargetsCorr());
	}

	@Test
	public void testDeleteAllModels() throws IOException
	{
		assertThat(dataModel.countModelNLP(), is(3));
		assertThat(dataModel.countModelCorr(), is(5));

		dataModel.deleteAllModels();

		assertThat(dataModel.countModelNLP(), is(0));
		assertThat(dataModel.countModelCorr(), is(0));
	}

	@Test
	public void testSubmitModel() throws IOException
	{
		assertThat(dataModel.countModelNLP(), is(3));
		assertThat(dataModel.countModelCorr(), is(5));
		assertThat(dataModel.hasModelNLP(110), is(false));
		assertThat(dataModel.hasModelCorr(111), is(false));

		Model model = dataModel.getModelNLP(101);
		model.target = 110;
		dataModel.submitModelNLP(model);
		assertThat(dataModel.hasModelNLP(110), is(true));
		assertEquals(Stream.of(101, 102, 103, 110).collect(Collectors.toSet()), dataModel.allTargetsNLP());

		model = dataModel.getModelCorr(101);
		model.target = 111;
		dataModel.submitModelCorr(model);
		assertThat(dataModel.hasModelCorr(111), is(true));
		assertEquals(Stream.of(101, 102, 103, 104, 105, 111).collect(Collectors.toSet()), dataModel.allTargetsCorr());

		assertThat(dataModel.countModelNLP(), is(4));
		assertThat(dataModel.countModelCorr(), is(6));
	}

	// ------------ private methods ------------

	private static void assertGrouping(String expected, SortedMap<Long, List<Integer>> grouped)
	{
		for (List<Integer> group : grouped.values())
		{
			Collections.sort(group);
		}
		assertThat(grouped.toString(), is(expected));
	}

}

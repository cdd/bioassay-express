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

import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.hamcrest.collection.*;
import org.junit.jupiter.api.*;

/*
	Test for com.cdd.bae.data.DataNLP
*/

public class DataNLPTest
{
	DataNLP dataNLP;

	@BeforeEach
	public void initialize()
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		dataNLP = new DataNLP(mongo.getDataStore());
	}

	@Test
	public void testNLPmethods() throws IOException
	{
		assertThat(dataNLP.countFingerprints(), is(0));
		assertEquals(10000000, dataNLP.getWatermark());

		DataStoreSupport.addFingerprints(dataNLP);

		assertThat(dataNLP.countFingerprints(), is(6));
		assertEquals(10000006, dataNLP.getWatermark());

		dataNLP.deleteAllFingerprints();
		assertThat(dataNLP.countFingerprints(), is(0));
		assertEquals(10000007, dataNLP.getWatermark());
	}

	@Test
	public void testWatermark() throws IOException
	{
		assertEquals(10000000, dataNLP.getWatermark());
		assertEquals(10000000, dataNLP.nextWatermark());
		assertEquals(10000001, dataNLP.getWatermark());
	}

	@Test
	public void testFetchFingerprints() throws IOException
	{
		assertThat(dataNLP.countFingerprints(), is(0));
		assertThat(dataNLP.cacheMapping, is(nullValue()));
		assertThat(dataNLP.cacheWatermark, is(-1L));

		Map<String, Integer> result = dataNLP.fetchFingerprints();
		assertThat(result, equalTo(Collections.emptyMap()));
		assertThat(dataNLP.cacheMapping, equalTo(Collections.emptyMap()));
		assertThat(dataNLP.cacheWatermark, is(dataNLP.getWatermark()));

		DataStoreSupport.addFingerprints(dataNLP);

		result = dataNLP.fetchFingerprints();
		assertThat(result, aMapWithSize(6));
		assertThat(dataNLP.cacheMapping, aMapWithSize(6));
		long watermark = dataNLP.getWatermark();
		assertThat(dataNLP.cacheWatermark, is(watermark));

		dataNLP.addNLPFingerprint("new string", 7);

		result = dataNLP.fetchFingerprints();
		assertThat(result, aMapWithSize(7));
		assertThat(dataNLP.cacheMapping, aMapWithSize(7));
		assertThat(dataNLP.cacheWatermark, is(dataNLP.getWatermark()));
		assertThat(result, IsMapContaining.hasKey("new string"));

		result = dataNLP.fetchFingerprints();
		assertThat(result, aMapWithSize(7));
		assertThat(dataNLP.cacheMapping, aMapWithSize(7));
		assertThat(dataNLP.cacheWatermark, is(dataNLP.getWatermark()));
	}
}

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

import com.cdd.bae.config.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.util.*;
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.junit.jupiter.api.*;


/*
	Test for com.cdd.bae.data.DataHolding
*/

public class DataHoldingTest
{
	DataStore store;
	DataHolding dataHolding;
	DataAssay dataAssay;

	@BeforeEach
	public void initialize() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);

		dataAssay = new DataAssay(mongo.getDataStore());
		dataHolding = new DataHolding(mongo.getDataStore());
		dataHolding.depositHolding(DataStoreSupport.makeHolding(2268L, 2));
		dataHolding.depositHolding(DataStoreSupport.makeHolding(2269L, 3));
	}

	@Test
	public void testFetchHoldings()
	{
		long[] result = dataHolding.fetchHoldings();
		assertThat(result.length, is(6));
		assertThat(result, is(new long[]{1L, 2L, 3L, 16L, 10000000L, 10000001L}));
	}

	@Test
	public void testFetchForAssayID()
	{
		List<Long> result = toList(dataHolding.fetchForAssayID(2268));
		assertThat(result, hasSize(1));
		assertThat(result, containsInAnyOrder(10000000L));
	}

	@Test
	public void testGetHolding()
	{
		Holding holding = dataHolding.getHolding(10000000L);
		assertThat(holding.holdingID, is(10000000L));
		assertThat(holding.assayID, is(2268L));
	}

	@Test
	public void testGetHoldingAssayID()
	{
		long assayID = dataHolding.getHoldingAssayID(10000001L);
		assertThat(assayID, is(2269L));
	}

	@Test
	public void testCountTotal()
	{
		assertThat(dataHolding.countTotal(), is(6));
	}

	@Test
	public void testConvertToAssay()
	{
		Holding holding = dataHolding.getHolding(10000000L);
		Assay assay = DataHolding.createAssayFromHolding(holding);
		assertThat(assay.assayID, is(2268L));
		assertThat(toList(assay.textLabels), hasSize(2));
		assertThat(toList(assay.annotations), hasSize(2));
	}

	@Test
	public void testDepositHolding()
	{
		assertThat(dataHolding.countTotal(), is(6));
		Holding holding = DataStoreSupport.makeHolding(1234, 2);
		dataHolding.depositHolding(holding);
		assertThat(dataHolding.countTotal(), is(7));

		long[] result = dataHolding.fetchHoldings();
		assertThat(result.length, is(7));
		assertThat(result, is(new long[]{1L, 2L, 3L, 16L, 10000000L, 10000001L, 10000002L}));
	}

	@Test
	public void testDeleteHolding()
	{
		assertThat(dataHolding.countTotal(), is(6));
		dataHolding.deleteHolding(10000000L);
		assertThat(dataHolding.countTotal(), is(5));
	}
	
	@Test
	public void testFetchHoldingsByCurator()
	{
		assertThat(dataHolding.fetchHoldingsByCurator("fnord"), is(new long[]{2, 1}));
		assertThat(dataHolding.fetchHoldingsByCurator("dronf"), is(new long[]{3}));
	}
	
	@Test
	public void testFetchHoldingsByAnnotation()
	{
		Set<String> provURIs = new HashSet<>();
		Map<String, Set<Long>> expected = new HashMap<>();
		for (long holdingID : dataHolding.fetchHoldings())
		{
			DataObject.Holding holding = dataHolding.getHolding(holdingID);
			for (DataObject.Annotation annot : holding.annotsAdded)
			{
				provURIs.add(annot.valueURI);
				Set<Long> assayIDs = expected.computeIfAbsent(annot.valueURI, k -> new HashSet<>());
				assayIDs.add(holdingID);
				expected.put(annot.valueURI, assayIDs);
			}
			for (DataObject.Annotation annot : holding.annotsRemoved)
			{
				provURIs.add(annot.valueURI);
				Set<Long> assayIDs = expected.computeIfAbsent(annot.valueURI, k -> new HashSet<>());
				assayIDs.add(holdingID);
				expected.put(annot.valueURI, assayIDs);
			}
		}

		for (String provURI : provURIs)
		{
			long[] assayIDs = dataHolding.fetchHoldingsByAnnotation(provURI);
			assertArrays(assayIDs, Util.primLong(expected.get(provURI)), provURI);
		}
	}


	// ------------ private methods ------------

	private List<Long> toList(long[] array)
	{
		return Arrays.asList(ArrayUtils.toObject(array));
	}

	private <T> List<T> toList(T[] array)
	{
		return Arrays.asList(array);
	}

	private void assertArrays(long[] actual, long[] expected, String reason)
	{
		Arrays.sort(actual);
		Arrays.sort(expected);
		assertThat(reason, actual, is(expected));
	}
}

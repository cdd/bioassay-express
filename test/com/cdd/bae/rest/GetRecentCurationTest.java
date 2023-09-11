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

package com.cdd.bae.rest;

import com.cdd.bae.config.*;
import com.cdd.bae.config.Identifier.*;
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;
import java.util.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for GetAssay REST API.
*/

public class GetRecentCurationTest extends EndpointEmulator
{
	@BeforeEach
	public void prepare() throws ConfigurationException
	{
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Common.setDataStore(mongo.getDataStore());

		GetRecentCuration getRecentCuration = new GetRecentCuration();
		getRecentCuration.logger = TestUtilities.mockLogger();
		restService = getRecentCuration;
	}
	
	@Test
	public void testStaticGetRecentCuration()
	{
		int[] assayIDsAll = {104, 102, 2, 103, 105};
		for (int maxNum = 0; maxNum <= assayIDsAll.length; maxNum++)
		{
			JSONObject json = GetRecentCuration.getRecentCuration(maxNum, null);
			assertThat(json.has("all"), is(true));
			assertThat(json.has("curator"), is(false));
			assertThat(json.has("holding"), is(false));
			assertAssayList(json.getJSONArray("all"), Arrays.copyOf(assayIDsAll, maxNum));

			json = GetRecentCuration.getRecentCuration(maxNum, "fnord");
			assertThat(json.has("all"), is(true));
			assertThat(json.has("curator"), is(maxNum > 0));
			assertThat(json.has("holding"), is(maxNum > 0));
			assertAssayList(json.getJSONArray("all"), Arrays.copyOf(assayIDsAll, maxNum));
		}

		JSONObject json = GetRecentCuration.getRecentCuration(5, "fnord");
		assertThat(json.has("all"), is(true));
		assertThat(json.has("curator"), is(true));
		assertThat(json.has("holding"), is(true));
		assertAssayList(json.getJSONArray("all"), assayIDsAll);
		assertAssayList(json.getJSONArray("curator"), new int[]{2, 102});
		assertAssayList(json.getJSONArray("holding"), new int[]{2, 2});

		json = GetRecentCuration.getRecentCuration(1, "fnord");
		assertThat(json.has("all"), is(true));
		assertThat(json.has("curator"), is(true));
		assertThat(json.has("holding"), is(true));
		assertAssayList(json.getJSONArray("all"), Arrays.copyOf(assayIDsAll, 1));
		assertAssayList(json.getJSONArray("curator"), new int[]{2});
		assertAssayList(json.getJSONArray("holding"), new int[]{2});
		JSONObject holding = json.getJSONArray("holding").getJSONObject(0);
		assertThat(holding.getString("shortText"), startsWith("Counter Screen for Glucose-6-Phosphate"));

		json = GetRecentCuration.getRecentCuration(5, "dronf");
		assertThat(json.has("all"), is(true));
		assertThat(json.has("curator"), is(true));
		assertThat(json.has("holding"), is(true));
		assertAssayList(json.getJSONArray("all"), assayIDsAll);
		assertAssayList(json.getJSONArray("curator"), new int[]{102});
		holding = json.getJSONArray("holding").getJSONObject(0);
		assertThat(holding.has("assayID"), is(false));
		assertThat(holding.getString("shortText"), is("Assay title in holding bay. Text in holding bay"));
	}

	@Test
	public void testRESTGetRecentCuration() throws IOException
	{
		int[] assayIDsAll = {104, 102, 2, 103, 105, 106};

		Map<String, String> params = new HashMap<>();

		JSONObject json = doGet().getContentAsJSON();
		assertThat(json.has("all"), is(true));
		assertThat(json.has("curator"), is(false));
		assertAssayList(json.getJSONArray("all"), assayIDsAll);

		json = doPost(params).getContentAsJSON();
		assertThat(json.has("all"), is(true));
		assertThat(json.has("curator"), is(false));
		assertAssayList(json.getJSONArray("all"), assayIDsAll);

		params.put("maxNum", "3");
		json = doPost(params).getContentAsJSON();
		assertThat(json.has("all"), is(true));
		assertThat(json.has("curator"), is(false));
		assertAssayList(json.getJSONArray("all"), Arrays.copyOf(assayIDsAll, 3));

		params.put("curatorID", "fnord");
		json = doPost(params).getContentAsJSON();
		assertThat(json.has("all"), is(true));
		assertThat(json.has("curator"), is(true));
		assertAssayList(json.getJSONArray("all"), Arrays.copyOf(assayIDsAll, 3));
		assertAssayList(json.getJSONArray("curator"), new int[]{2, 102});

		params.put("maxNum", "1");
		json = doPost(params).getContentAsJSON();
		assertThat(json.has("all"), is(true));
		assertThat(json.has("curator"), is(true));
		assertAssayList(json.getJSONArray("all"), Arrays.copyOf(assayIDsAll, 1));
		assertAssayList(json.getJSONArray("curator"), new int[]{2});
	}
	
	@Test
	public void testGetSummaryOrder()
	{
		assertThat(GetRecentCuration.getSummaryOrder(null), arrayContaining(Identifier.defaultSummary));

		DataObject.Assay assay = new DataObject.Assay();
		assay.uniqueID = null;
		assertThat(GetRecentCuration.getSummaryOrder(assay), arrayContaining(Identifier.defaultSummary));
		
		assay.uniqueID = "abc";
		assertThat(GetRecentCuration.getSummaryOrder(assay), arrayContaining(Identifier.defaultSummary));

		assay.uniqueID = "newPfx:12345";
		assertThat(GetRecentCuration.getSummaryOrder(assay), arrayContaining(Identifier.defaultSummary));

		for (Source source : Common.getIdentifier().getSources())
		{
			assay.uniqueID = source.prefix + "12345";
			assertThat(GetRecentCuration.getSummaryOrder(assay), arrayContaining(source.summary));
		}
	}

	@Test
	public void testCombineFragments()
	{
		List<String> fragments = new ArrayList<>();
		assertThat(GetRecentCuration.combineFragments(fragments), is(""));
		fragments.add("abc");
		assertThat(GetRecentCuration.combineFragments(fragments), is("abc"));
		fragments.add("");
		assertThat(GetRecentCuration.combineFragments(fragments), is("abc"));
		fragments.add("abc def");
		assertThat(GetRecentCuration.combineFragments(fragments), is("abc def"));
		fragments.add("xyz");
		assertThat(GetRecentCuration.combineFragments(fragments), is("abc def. xyz"));
		fragments.add(null);
		assertThat(GetRecentCuration.combineFragments(fragments), is("abc def. xyz"));
		fragments.add("xyz");
		assertThat(GetRecentCuration.combineFragments(fragments), is("abc def. xyz"));
		fragments.add("rst");
		assertThat(GetRecentCuration.combineFragments(fragments), is("abc def. xyz. rst"));
		fragments.add("          ");
		assertThat(GetRecentCuration.combineFragments(fragments), is("abc def. xyz. rst"));
	}

	// ------------ private methods ------------

	private void assertAssayList(JSONArray arr, int[] expected)
	{
		assertThat(arr.length(), is(expected.length));
		for (int i = 0; i < expected.length; i++)
			assertThat(arr.getJSONObject(i).getInt("assayID"), is(expected[i]));
	}
}

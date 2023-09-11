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
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import javax.servlet.http.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for ListCompounds REST API.
*/

public class ListCompoundsTest
{
	private ListCompounds listCompounds;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/compound");
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		listCompounds = new ListCompounds();
		listCompounds.logger = TestUtilities.mockLogger();
	}

	@Test
	public void testCorrectCall() throws IOException
	{
		// we can get empty results if nothing is requested
		JSONObject json = postRequest("{}");
		assertEquals(0, json.length());

		json = postRequest("{\"assayIDList\": [1]}");
		assertResponse1(json, 7);

		json = postRequest("{\"assayIDList\": [1], \"justIdentifiers\": true}");
		assertResponse2(json, 7);

		json = postRequest("{\"compoundIDList\": [1, 2, 4, 6]}");
		assertResponse3(json);
	}

	@Test
	public void testSimilarity() throws IOException
	{
		DataStore store = Common.getDataStore();
		String similarTo = store.compound().getCompound(1).molfile;

		String jsonRequest = "{\"compoundIDList\": [1, 2, 4, 6], \"similarTo\": \"" + similarTo.replace("\n", "\\n") + "\"}";
		JSONObject json = postRequest(jsonRequest);
		assertResponse3(json);
		assertEquals(1, json.getJSONArray("similarity").getDouble(0), 0.01);
		assertEquals(0.0403, json.getJSONArray("similarity").getDouble(1), 0.01);
		assertEquals(0.1454, json.getJSONArray("similarity").getDouble(2), 0.01);
		assertEquals(0, json.getJSONArray("similarity").getDouble(3), 0.01);
		double[] similarity = new double[]{1, 0.04032257944345474, 0.145454540848732, 0};
		for (int i = 0; i < similarity.length; ++i)
			assertEquals(json.getJSONArray("similarity").getDouble(i), similarity[i], 0.01);

		jsonRequest = "{\"assayIDList\": [1], \"similarTo\": \"" + similarTo.replace("\n", "\\n") + "\"}";
		json = postRequest(jsonRequest);
		assertResponse1(json, 7);
		similarity = new double[]{1, 0.04032258, 0, 0.14545454, 0.20168068, 0, 0};
		for (int i = 0; i < similarity.length; ++i)
			assertEquals(json.getJSONArray("similarity").getDouble(i), similarity[i], 0.01);
	}

	@Test
	public void testRequireMol() throws IOException
	{
		String jsonRequest = "{\"assayIDList\": [1], \"requireMolecule\": true}";
		JSONObject json = postRequest(jsonRequest);
		assertResponse1(json, 4); // three molecules are not defined

		jsonRequest = "{\"assayIDList\": [1], \"requireMolecule\": false}";
		json = postRequest(jsonRequest);
		assertResponse1(json, 7);

		jsonRequest = "{\"assayIDList\": [1], \"requireMolecule\": true, \"justIdentifiers\": true}";
		json = postRequest(jsonRequest);
		assertResponse2(json, 4); // three molecules are not defined

		jsonRequest = "{\"assayIDList\": [1], \"requireMolecule\": false, \"justIdentifiers\": true}";
		json = postRequest(jsonRequest);
		assertResponse2(json, 7);
	}

		@Test
		public void testHashWhitelist() throws IOException
		{
			String jsonRequest = "{\"assayIDList\": [1]}";
			JSONObject json = postRequest(jsonRequest);
			assertResponse1(json, 7);

			jsonRequest = "{\"assayIDList\": [1], \"hashECFP6List\": [2117857030, -435309131, 12345]}";
			json = postRequest(jsonRequest);
			assertResponse1(json, 2); // only two match

			jsonRequest = "{\"assayIDList\": [1], \"hashECFP6List\": [2117857030, -435309131, 12345]}, \"justIdentifiers\": true}";
			json = postRequest(jsonRequest);
			assertResponse2(json, 2); // only two match
		}

	// ------------ private methods ------------

	private JSONObject postRequest(String json) throws IOException
	{
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest(json);
		MockRESTUtilities.MockJSONResponse response = new MockRESTUtilities.MockJSONResponse();
		listCompounds.doPost(request, response.getResponse());
		return response.getContentAsJSON();
	}

	// results for assayID search
	private void assertResponse1(JSONObject json, int nCompounds)
	{
		String[] fields = new String[]{"compoundIDList", "hashECFP6List", "assays", "columns", "measureIndex", "measureCompound", "measureValue", "measureRelation"};
		for (String field : fields)
			assertTrue(json.has(field), "Field " + field);
		assertEquals(nCompounds, json.getJSONArray("compoundIDList").length());
	}

	// results for assayID search (justIdentfiiers)
	private void assertResponse2(JSONObject json, int nCompounds)
	{
		String[] fields = new String[]{"compoundIDList", "hashECFP6List"};
		for (String field : fields)
			assertTrue(json.has(field), "Field " + field);
		assertEquals(nCompounds, json.getJSONArray("compoundIDList").length());
	}

	// results for compoundID search
	private void assertResponse3(JSONObject json)
	{
		String[] fields = new String[]{"molfileList", "compoundIDList", "hashECFP6List", "pubchemCIDList", "pubchemSIDList", "vaultIDList", "vaultMIDList"};
		for (String field : fields)
			assertTrue(json.has(field), "Field " + field);
		JSONArray jsonArray = json.getJSONArray("compoundIDList");
		assertEquals(4, jsonArray.length());
		jsonArray = json.getJSONArray("molfileList");
		assertThat(jsonArray.getString(0), containsString("4237472"));
		assertThat(jsonArray.getString(1), containsString("4237475"));
		assertThat(jsonArray.getString(2), containsString("4237480"));
		assertThat(jsonArray.isNull(3), is(true));
	}
}

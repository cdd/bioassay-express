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
import java.util.*;

import javax.servlet.http.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for GetHoldingBay REST API.
*/

public class GetHoldingBayTest
{
	private GetHoldingBay getHoldingBay;

	@BeforeEach
	public void initialize() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		getHoldingBay = new GetHoldingBay();
		getHoldingBay.logger = TestUtilities.mockLogger();
		DataHolding dataHolding = new DataHolding(mongo.getDataStore());
		mongo.restoreContent();

		dataHolding.depositHolding(DataStoreSupport.makeHolding(2268L, 2));
		dataHolding.depositHolding(DataStoreSupport.makeHolding(2268L, 3));
		dataHolding.depositHolding(DataStoreSupport.makeHolding(2270L, 4));
	}

	// ------------ mockito-based tests ------------

	@Test
	public void testGetList() throws IOException
	{
		JSONObject json = getRequest();
		assertEquals(7, json.getJSONArray("holdingID").length());
	}

	@Test
	public void testSpecificAssays() throws IOException
	{
		JSONObject json = getRequest(10000000, 10000001);

		assertThat(json.has("list"), is(true));
		assertThat(json.getJSONArray("list").length(), is(2));

		json = getRequest(1, 2);
		assertThat(json.toString(), containsString("currentUniqueID"));
	}

	@Test
	public void testManufactureList() throws IOException
	{
		HttpServletRequest request = MockRESTUtilities.mockedPOSTRequest(getParameters(null, null));
		String result = GetHoldingBay.manufactureList(request);
		assertThat(result, containsString("uniqueID:1234"));
		assertThat(result, containsString("currentUniqueID"));

		request = MockRESTUtilities.mockedPOSTRequest(getParameters(null, "10000000"));
		result = GetHoldingBay.manufactureList(request);
		assertThat(result, containsString("2268"));

		request = MockRESTUtilities.mockedPOSTRequest(getParameters("2268", null));
		result = GetHoldingBay.manufactureList(request);
		assertThat(result, containsString("\"holdingID\":10000000"));
		assertThat(result, containsString("\"holdingID\":10000001"));
		assertThat(result, containsString("\"assayID\":2268"));
		assertThat(result, containsString("\"uniqueID\":\"uniqueID:1234\""));
		assertThat(result, containsString("deleteFlag"));
	}
	
	@Test
	public void testGetAssayID()
	{
		DataObject.Holding holding = new DataObject.Holding();
		assertThat(holding.assayID, is(0L));
		
		// assayID and uniqueID unknown
		assertThat(GetHoldingBay.getAssayID(holding), is(0L));
		assertThat(holding.assayID, is(0L));
		
		// assayID unknown, uniqueID known (assay was registered in the meantime)
		holding.uniqueID = "pubchemAID:1020";
		assertThat(GetHoldingBay.getAssayID(holding), is(2L));
		assertThat(holding.assayID, is(2L));
		
		// assayID and uniqueID known
		assertThat(GetHoldingBay.getAssayID(holding), is(2L));
		assertThat(holding.assayID, is(2L));
		
		// assayID unknown, uniqueID known (assay is not yet registered)
		holding.assayID = 0;
		holding.uniqueID = "pubchemAID:98765";
		assertThat(GetHoldingBay.getAssayID(holding), is(0L));
		assertThat(holding.assayID, is(0L));
	}

	// ------------ private methods ------------

	private static Map<String, String> getParameters(String assayID, String holdingID)
	{
		Map<String, String> parameters = new HashMap<>();
		if (assayID != null) parameters.put("assayID", assayID);
		if (holdingID != null) parameters.put("holdingID", holdingID);
		return parameters;
	}

	private JSONObject getRequest() throws IOException
	{
		JSONObject json = new JSONObject();
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest(json.toString());
		MockRESTUtilities.MockJSONResponse mockResponse = new MockRESTUtilities.MockJSONResponse();
		getHoldingBay.doGet(request, mockResponse.getResponse());
		return mockResponse.getContentAsJSON();
	}

	private JSONObject getRequest(long holdingID1, long holdingID2) throws IOException
	{
		JSONArray jsonArray = new JSONArray();
		jsonArray.put(holdingID1);
		jsonArray.put(holdingID2);
		JSONObject json = new JSONObject();
		json.put("holdingIDList", jsonArray);
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest(json.toString());
		MockRESTUtilities.MockJSONResponse mockResponse = new MockRESTUtilities.MockJSONResponse();
		getHoldingBay.doPost(request, mockResponse.getResponse());
		return mockResponse.getContentAsJSON();
	}
}

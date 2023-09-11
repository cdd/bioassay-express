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
	Test for GetAssay REST API.
*/

public class GetAssayTest
{
	private static final String COUNT_COMPOUNDS = "countCompounds";
	private GetAssay getAssay;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		getAssay = new GetAssay();
		getAssay.logger = TestUtilities.mockLogger();
	}

	// ------------ mockito-based tests ------------

	@Test
	public void testCallWithAssayID() throws IOException
	{
		JSONObject response = postRequest(2, false);
		assertResponse(response, 2);
		assertThat(response.has(COUNT_COMPOUNDS), is(false));

		response = postRequest(2, true);
		assertResponse(response, 2);
		assertThat(response.has(COUNT_COMPOUNDS), is(true));
	}

	@Test
	public void testCallWithUniqueID() throws IOException
	{
		JSONObject response = postRequest("pubchemAID:1020", false);
		assertResponse(response, 2);
		assertThat(response.has(COUNT_COMPOUNDS), is(false));

		response = postRequest("pubchemAID:1020", true);
		assertResponse(response, 2);
		assertThat(response.has(COUNT_COMPOUNDS), is(true));
	}

	@Test
	public void testExceptions() throws IOException
	{
		JSONObject response = postRequest(12345678, false);
		TestUtilities.assertErrorResponse(response, RESTException.HTTPStatus.BAD_REQUEST);
		assertThat(response.getString("userMessage"), containsString("Unable to find assayID"));

		response = postRequest(0, false);
		TestUtilities.assertErrorResponse(response, RESTException.HTTPStatus.BAD_REQUEST);
		assertThat(response.getString("userMessage"), containsString("Bad parameters"));
	}
	

	@Test
	public void testSchemaValidation()
	{
		// parameter list ok
		List<String> result = getAssay.schemaValidation(new JSONObject("{\"assayID\": 12345678}"));
		assertTrue(result.isEmpty());

		// assayID should be >= 1
		result = getAssay.schemaValidation(new JSONObject("{\"assayID\": 0}"));
		assertEquals(1, result.size());

		// assayID parameter should be integer
		result = getAssay.schemaValidation(new JSONObject("{\"assayID\": \"abc\"}"));
		assertEquals(1, result.size());

		// no required parameters
		result = getAssay.schemaValidation(new JSONObject("{\"countCompounds\": true}"));
		assertEquals(0, result.size());

		// parameter list ok
		List<String> result1 = getAssay.schemaValidation(new JSONObject("{\"uniqueID\": \"pubchemAID:1117308\"}"));
		assertEquals(0, result1.size());

		// uniqueID parameter should be a string
		result1 = getAssay.schemaValidation(new JSONObject("{\"uniqueID\": 1}"));
		assertEquals(1, result1.size());
	}

	// ------------ faux-mongo tests ------------

	@Test
	// fetch a relatively detailed assay from the basic database population file, then check it quite thoroughly
	public void testGetExisting() throws RESTException
	{
		
		JSONObject request = new JSONObject();
		request.put("uniqueID", "pubchemAID:1020");
		JSONObject result = getAssay.processRequest(request, TestUtilities.mockSession());
		
		String text = result.getString("text");
		assertTrue(text.startsWith("Counter Screen"));

		assertEquals("http://www.bioassayontology.org/bas#", result.getString("schemaURI"));
		
		assertThat(result.getLong("curationTime"), greaterThan(0L));
		assertThat(result.optString("curatorID"), not(emptyOrNullString()));
		
		JSONArray history = result.getJSONArray("history");
		assertThat(history.length(), greaterThan(0));

		JSONArray holding = result.getJSONArray("holdingIDList");		
		assertEquals(2, holding.length());
		
		JSONArray annots = result.getJSONArray("annotations");
		assertThat(annots.length(), greaterThan(0));

		boolean gotProtein = false;
		for (JSONObject annot : annots.toObjectArray())
		{
			assertThat(annot.optString("propURI"), not(emptyOrNullString()));
			assertThat(annot.optString("propLabel"), not(emptyOrNullString()));
			assertThat(annot.optString("valueLabel"), not(emptyOrNullString()));

			if (!annot.optString("valueURI").equals("http://www.bioassayontology.org/protein#149631")) continue;
			
			assertEquals("http://www.bioassayontology.org/bao#BAX_0000012", annot.optString("propURI"));
			assertEquals("protein identity", annot.optString("propLabel"));
			assertEquals("glucose-6-phosphate dehydrogenase [Leuconostoc mesenteroides]", annot.optString("valueLabel"));
			assertThat(annot.optString("valueDescr"), not(emptyOrNullString()));
			assertEquals(0, annot.getJSONArray("groupNest").length());
			assertEquals(0, annot.getJSONArray("groupLabel").length());
			assertThat(annot.getJSONArray("valueHier").length(), greaterThan(0));
			assertThat(annot.getJSONArray("labelHier").length(), greaterThan(0));
			
			gotProtein = true;
		}
		assertTrue(gotProtein, "Protein annotation missing");
	}

	// ------------ private methods ------------

	private void assertResponse(JSONObject json, long assayID)
	{
		assertTrue(json.has("assayID"));
		assertEquals(assayID, json.getLong("assayID"));
		assertTrue(json.has("uniqueID"));
		assertTrue(json.has("annotations"));
		assertTrue(json.has("curatorID"));
	}

	private JSONObject postRequest(String uniqueID, boolean countCompounds) throws IOException
	{
		JSONObject json = new JSONObject();
		json.put("uniqueID", uniqueID);
		json.put(COUNT_COMPOUNDS, countCompounds);
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest(json.toString());
		MockRESTUtilities.MockJSONResponse mockResponse = new MockRESTUtilities.MockJSONResponse();
		getAssay.doPost(request, mockResponse.getResponse());
		return mockResponse.getContentAsJSON();
	}

	private JSONObject postRequest(long assayID, boolean countCompounds) throws IOException
	{
		JSONObject json = new JSONObject();
		json.put("assayID", assayID);
		json.put(COUNT_COMPOUNDS, countCompounds);
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest(json.toString());
		MockRESTUtilities.MockJSONResponse mockResponse = new MockRESTUtilities.MockJSONResponse();
		getAssay.doPost(request, mockResponse.getResponse());
		return mockResponse.getContentAsJSON();
	}
}

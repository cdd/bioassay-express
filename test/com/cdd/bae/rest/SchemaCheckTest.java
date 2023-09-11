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
import com.cdd.bae.rest.MockRESTUtilities.*;
import com.cdd.bae.util.*;
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import javax.servlet.http.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for SchemaCheck REST API.
*/

public class SchemaCheckTest
{
	private SchemaCheck schemaCheck;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		schemaCheck = new SchemaCheck();
		schemaCheck.logger = TestUtilities.mockLogger();
	}
	
	@Test
	public void testRESTcall() throws IOException
	{
		MockJSONResponse response = doServletCall("{\"assayIDList\":[101, 104]}");
		
		JSONObject json = response.getContentAsJSON();
		JSONArray jsonOutOfSchema = json.getJSONArray("outOfSchema");
		JSONArray jsonMissingMandatory = json.getJSONArray("missingMandatory");
		JSONArray jsonAxiomViolation = json.getJSONArray("axiomViolation");
		JSONArray jsonNumberMisformat = json.getJSONArray("numberMisformat");
		
		assertNotEquals(0, jsonOutOfSchema.length() + jsonMissingMandatory.length());
		assertEquals(0, jsonAxiomViolation.length());
		assertEquals(0, jsonNumberMisformat.length());
	}

	@Test
	public void testMisformat() throws RESTException
	{
		JSONObject request = new JSONObject();
		request.put("assayIDList", new long[]{105, 106}); // 105=bad, 106=good
		JSONObject result = schemaCheck.processRequest(request, TestUtilities.mockSessionCurator());
		
		JSONObject[] misformat = result.getJSONArray("numberMisformat").toObjectArray();
		assertEquals(2, misformat.length);
		
		for (JSONObject json : misformat)
		{
			assertEquals(105, json.getInt("assayID"));
			assertEquals("http://www.bioassayontology.org/bao#BAO_0002916", json.getString("propURI"));
			
			String label = json.getString("valueLabel");
			assertTrue(label.equals("10,01") || label.equals("1,000"));
		}
	}

	// ------------ private methods ------------

	private MockJSONResponse doServletCall(String json) throws IOException
	{
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest(json);
		MockJSONResponse response = new MockRESTUtilities.MockJSONResponse();
		schemaCheck.doPost(request, response.getResponse());
		return response;
	}
}

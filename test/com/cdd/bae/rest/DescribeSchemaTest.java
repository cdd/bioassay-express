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

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import javax.servlet.http.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for DescribeSchema REST API.
*/

public class DescribeSchemaTest
{
	private static final String SCHEMA_URI = "schemaURI";
	private static final String SCHEMA_CAT = "http://www.bioassayontology.org/bas#";
	private static final int DEFAULT_N_ASSIGNMENTS = 28;
	private DescribeSchema describeSchema;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		describeSchema = new DescribeSchema();
	}

	// ------------ mockito-based tests ------------

	@Test
	public void testLocator() throws IOException
	{
		HttpServletRequest request = MockRESTUtilities.mockedPOSTRequest(getParameters(SCHEMA_CAT, "1"));
		MockRESTUtilities.MockJSONResponse mockResponse = new MockRESTUtilities.MockJSONResponse();
		HttpServletResponse response = mockResponse.getResponse();
		describeSchema.doPost(request, response);

		JSONObject json = mockResponse.getContentAsJSON();
		assertEquals(1, json.length());

		// not reliable
		//JSONArray schema = json.getJSONArray("values");
		//assertEquals(13, schema.length(), "Check if schema has changed");
	}

	@Test
	public void testNoLocator() throws IOException
	{
		HttpServletRequest request = MockRESTUtilities.mockedPOSTRequest(getParameters(SCHEMA_CAT, null));
		MockRESTUtilities.MockJSONResponse mockResponse = new MockRESTUtilities.MockJSONResponse();
		HttpServletResponse response = mockResponse.getResponse();
		describeSchema.doPost(request, response);

		JSONObject json = mockResponse.getContentAsJSON();
		assertEquals(6, json.length());
		assertEquals(SCHEMA_CAT, json.getString(SCHEMA_URI));
		assertEquals(DEFAULT_N_ASSIGNMENTS, json.getJSONArray("assignments").length());
	}

	@Test
	public void testNoParameter() throws IOException
	{
		HttpServletRequest request = MockRESTUtilities.mockedPOSTRequest(getParameters(null, null));
		MockRESTUtilities.MockJSONResponse mockResponse = new MockRESTUtilities.MockJSONResponse();
		HttpServletResponse response = mockResponse.getResponse();
		describeSchema.doPost(request, response);

		JSONObject json = mockResponse.getContentAsJSON();
		assertEquals(6, json.length());
		assertEquals(SCHEMA_CAT, json.getString(SCHEMA_URI));
		assertEquals(DEFAULT_N_ASSIGNMENTS, json.getJSONArray("assignments").length());
	}

	// ------------ private methods ------------

	private static Map<String, String> getParameters(String schemaURI, String locator)
	{
		Map<String, String> parameters = new HashMap<>();
		if (schemaURI != null) parameters.put(SCHEMA_URI, schemaURI);
		if (locator != null) parameters.put("locator", locator);
		return parameters;
	}
}

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
import com.cdd.bae.rest.RESTException.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;

import javax.servlet.http.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for TallyStats REST API.
*/

public class TallyStatsTest
{
	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
	}

	@Test
	public void testTallyStats() throws IOException, ConfigurationException
	{

		TallyStats tallyStats = new TallyStats();
		tallyStats.logger = TestUtilities.mockLogger();

		// we get information using GET request without parameter
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest("{\"tokens\": [\"numAssays\"]}");
		MockRESTUtilities.MockJSONResponse mockResponse = new MockRESTUtilities.MockJSONResponse();
		tallyStats.doPost(request, mockResponse.getResponse());
		JSONObject json = mockResponse.getContentAsJSON();
		assertResponse(json, 1);

		request = MockRESTUtilities.mockedJSONRequest("{\"tokens\": [\"numAssays\", \"compoundsWithStructures\", \"assaysWithMeasurements\"]}");
		mockResponse = new MockRESTUtilities.MockJSONResponse();
		tallyStats.doPost(request, mockResponse.getResponse());
		assertResponse(mockResponse.getContentAsJSON(), 3);

		request = MockRESTUtilities.mockedJSONRequest("{\"tokens\": [\"typo\", \"compoundsWithStructures\", \"assaysWithMeasurements\"]}");
		mockResponse = new MockRESTUtilities.MockJSONResponse();
		HttpServletResponse response = mockResponse.getResponse();
		tallyStats.doPost(request, response);
		TestUtilities.assertErrorResponse(mockResponse.getContentAsJSON(), HTTPStatus.BAD_REQUEST);
		verify(response).setStatus(HTTPStatus.BAD_REQUEST.code());
	}

	// ------------ private methods ------------

	private void assertResponse(JSONObject json, int expectedLength)
	{
		assertEquals(expectedLength, json.length());
		assertEquals(7, json.getInt("numAssays"));
		if (json.has("assaysWithMeasurements")) assertEquals(0, json.getInt("assaysWithMeasurements"));
		if (json.has("compoundsWithStructures")) assertEquals(0, json.getInt("compoundsWithStructures"));

	}
}

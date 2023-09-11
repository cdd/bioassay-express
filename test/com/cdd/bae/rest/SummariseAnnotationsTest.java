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

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for SummariseAnnotations REST API.
*/

public class SummariseAnnotationsTest extends EndpointEmulator
{
	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		// required mongo database calls use JavaScript - therefore need to mock
		Common.setDataStore(MockDataStore.mockedDataStore()); 

		restService = new SummariseAnnotations();
		restService.logger = TestUtilities.mockLogger();
	}

	@Test
	public void testCorrectCall() throws IOException
	{
		MockRESTUtilities.MockJSONResponse mockResponse = doGet();
		JSONObject json = mockResponse.getContentAsJSON();

		assertResponse(json);
	}

	// ------------ private methods ------------

	private void assertResponse(JSONObject json)
	{
		assertTrue(json.has("propCounts"));
		assertTrue(json.has("annotCounts"));
		assertEquals(2, json.getJSONObject("propCounts").length());
		assertTrue(json.getJSONObject("propCounts").has("url1"));
		assertTrue(json.getJSONObject("propCounts").has("url2"));
		assertEquals(2, json.getJSONObject("annotCounts").length());
		assertTrue(json.getJSONObject("annotCounts").has("url1"));
		assertTrue(json.getJSONObject("annotCounts").has("url2"));
	}
}

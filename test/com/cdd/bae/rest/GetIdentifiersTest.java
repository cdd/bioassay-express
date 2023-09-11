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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;

import javax.servlet.http.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for GetIdentifiers REST API.
*/

public class GetIdentifiersTest
{
	@Test
	public void testCorrectCall() throws IOException, ConfigurationException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		JSONObject json = getRequest();
		assertResponse(json);
	}

	// ------------ private methods ------------

	private void assertResponse(JSONObject json)
	{
		assertThat(json.has("pubchemAID:"), is(true));
		assertThat(json.getJSONArray("pubchemAID:").length(), is(7));
	}

	private JSONObject getRequest() throws IOException
	{
		JSONObject json = new JSONObject();
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest(json.toString());
		MockRESTUtilities.MockJSONResponse mockResponse = new MockRESTUtilities.MockJSONResponse();
		new GetIdentifiers().doGet(request, mockResponse.getResponse());
		verify(mockResponse.getResponse()).setHeader(eq("Cache-Control"), anyString());
		return mockResponse.getContentAsJSON();
	}
}

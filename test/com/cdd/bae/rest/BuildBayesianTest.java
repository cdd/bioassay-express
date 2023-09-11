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

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import javax.servlet.http.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for BuildBayesian REST API.
*/

public class BuildBayesianTest
{
	private static final String FAIL_MSG = "failMsg";
	private static final String ECFP6 = "ECFP6";
	private static final String MODEL = "model";
	private static final String JSON_PAYLOAD = "{\"assayIDList\": [1]}";
	private BuildBayesian buildBayesian;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/compound");
		
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		buildBayesian = new BuildBayesian();
	}

	// ------------ mockito-based tests ------------

	@Test
	public void testBuildModel() throws IOException
	{
		JSONObject results = restCall(JSON_PAYLOAD);
		assertTrue(results.has(MODEL));
		assertThat(results.getString(MODEL), containsString(ECFP6));
		assertTrue(results.has(FAIL_MSG));
		assertThat(results.getString(FAIL_MSG), containsString("not suitable"));
	}

	// ------------ private methods ------------
	private JSONObject restCall(String payload) throws IOException
	{
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest(payload);
		MockRESTUtilities.MockJSONResponse response = new MockRESTUtilities.MockJSONResponse();
		buildBayesian.doPost(request, response.getResponse());
		return response.getContentAsJSON();
	}
}

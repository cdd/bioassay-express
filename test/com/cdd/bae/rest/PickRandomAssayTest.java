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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.*;

import javax.servlet.http.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for PickRandomAssay REST API.
*/

public class PickRandomAssayTest
{
	private PickRandomAssay pickRandomAssay;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		pickRandomAssay = new PickRandomAssay();
		pickRandomAssay.logger = TestUtilities.mockLogger();
	}

	@Test
	public void testPickRandomAssay() throws IOException
	{
		Templates templates = mock(Templates.class);
		when(templates.getSchema(anyString())).thenReturn(Common.getSchemaCAT());

		// we get information using GET request without parameter
		HttpServletRequest request = MockRESTUtilities.mockedPOSTRequest(getParameters());
		MockRESTUtilities.MockJSONResponse mockResponse = new MockRESTUtilities.MockJSONResponse();
		HttpServletResponse response = mockResponse.getResponse();
		pickRandomAssay.doPost(request, response);

		JSONArray json = mockResponse.getContentAsJSONArray();
		assertEquals(6, json.length()); // adjust number if CAT schema changes
	}

	// ------------ private methods ------------

	private static Map<String, String> getParameters()
	{
		Map<String, String> parameters = new HashMap<>();
		parameters.put("numAssays", "10");
		parameters.put("curated", "true");
		return parameters;
	}
}

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
import com.cdd.bae.rest.RESTException.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for ListCuratedAssays REST API.
*/
public class ListQueryAssaysTest extends EndpointEmulator
{
	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		setRestService(new ListQueryAssays());
	}

	@Test
	public void testCorrectCall() throws IOException
	{
		Map<String, String> parameter = new HashMap<>();
		
		// query with results
		parameter.put("query", "(bao:BAO_0002867=@bao:BAO_0000516)");
		assertResult(doPost(parameter).getContentAsJSON(), 1);

		// and without
		parameter.put("query", "(bao:BAO_0002867=@bao:BAO_0000000)");
		assertResult(doPost(parameter).getContentAsJSON(), 0);

		// positve + negative query: should get nothing
		parameter.put("query", "(bao:BAO_0002867=@bao:BAO_0000516);(bao:NOTHING=@bao:BAO_0000041)");
		assertResult(doPost(parameter).getContentAsJSON(), 0);

		// and with an incorrect query
		parameter.put("query", "()");
		MockJSONResponse response = doPost(parameter);
		verify(response.getResponse()).setStatus(HTTPStatus.BAD_REQUEST.code());
		TestUtilities.assertErrorResponse(response.getContentAsJSON(), RESTException.HTTPStatus.BAD_REQUEST);
	}

	// ------------ private methods ------------

	private void assertResult(JSONObject json, long nAssays)
	{
		assertEquals(nAssays, json.getJSONArray("assayIDList").length());
	}
}

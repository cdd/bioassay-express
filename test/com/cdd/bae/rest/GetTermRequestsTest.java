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
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;
import java.util.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for GetTermRequests REST API.
*/

public class GetTermRequestsTest extends EndpointEmulator
{
	private Provisional provisional;
	
	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		provisional = DataStoreSupport.makeProvisional();
		DataProvisional dataProvisional = new DataProvisional(mongo.getDataStore());
		dataProvisional.updateProvisional(provisional);

		restService = new GetTermRequests();
		restService.logger = TestUtilities.mockLogger();
	}
	
	@Test
	public void testAssayUsageCounts()
	{
		String provURI = provisional.uri;

		DataStore store = Common.getDataStore();
		Provisional[] provisionals = store.provisional().fetchAllTerms();
		
		Map<String, Integer> usageCount = GetTermRequests.usageAssays(provisionals, store);
		assertThat(usageCount.keySet(), hasSize(1));
		assertThat(usageCount, hasKey(provURI));
		assertThat(usageCount.get(provURI), is(0));
		
		usageCount = GetTermRequests.usageHoldings(provisionals, store);
		assertThat(usageCount.keySet(), hasSize(1));

		// additional tests in DeleteProvisionalTermTest
	}

	@Test
	public void testGetAll() throws IOException
	{
		Map<String, String> parameters = new HashMap<>();
		JSONObject json = doCall(parameters).getContentAsJSON();
		assertThat(json.getJSONArray("list").length(), is(1));

		parameters.put("status", "submitted");
		json = doCall(parameters).getContentAsJSON();
		assertThat(json.getJSONArray("list").length(), is(1));

		parameters.put("status", "accepted");
		json = doCall(parameters).getContentAsJSON();
		assertThat(json.getJSONArray("list").length(), is(0));
	}

	// ------------ private methods ------------

}

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

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for ListCuratedAssays REST API.
*/

public class ListCuratedAssaysTest extends EndpointEmulator
{
	private static final String ANOMALY = "anomaly";

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		setRestService(new ListCuratedAssays());
	}

	// ------------ faux-mongo tests ------------

	@Test
	// fetch from the preloaded list of assays, and insist that certain entries are present/absent
	public void testListVariants() throws IOException
	{
		Map<String, String> parameter = new HashMap<>();

		// default: anything with curated flag set true
		JSONObject json = doPost(parameter).getContentAsJSON();
		assertAssaysListed(json, new long[]{2, 102, 103, 104}, new long[]{101});

		// curated flag set to true, and has at least one semantic annotation
		parameter.put(ANOMALY, "nonblanks");
		json = doPost(parameter).getContentAsJSON();
		assertAssaysListed(json, new long[]{2, 104}, new long[]{101, 102, 103});

		// anything curated for which the schema isn't completely filled out
		parameter.put(ANOMALY, "blanks");
		json = doPost(parameter).getContentAsJSON();
		assertAssaysListed(json, new long[]{2, 102, 103, 104}, new long[]{101});

		// anything that has an "out of schema" annotation
		parameter.put(ANOMALY, "outschema");
		json = doPost(parameter).getContentAsJSON();
		assertAssaysListed(json, new long[]{104}, new long[]{2, 101, 102, 103});
	}

	@Test
	public void testAbsenceTerms() throws IOException
	{
		Map<String, String> parameter = new HashMap<>();
		parameter.put(ANOMALY, "absenceterms");
		JSONObject json = doPost(parameter).getContentAsJSON();
		assertAssaysListed(json, new long[]{2}, new long[]{101, 102, 103, 104});
	}

	@Test
	public void testCutoffTime() throws Exception
	{
		JSONObject request = new JSONObject();
		request.put("cutoffTime", 1444345153000L);
		JSONObject result = new ListCuratedAssays().processRequest(request, TestUtilities.mockSessionCurator());
		long[] resultList = result.getJSONArray("assayIDList").toLongArray();

		Arrays.sort(resultList);
		assertArrayEquals(new long[]{2, 102}, resultList);
	}

	// ------------ private methods ------------

	private void assertAssaysListed(JSONObject result, long[] whitelist, long[] blacklist)
	{
		long[] assayIDList = result.getJSONArray("assayIDList").toLongArray();
		Set<Long> obtained = new HashSet<>();
		for (long assayID : assayIDList) obtained.add(assayID);

		String obt = " (obtained=" + obtained.toString() + ")";
		for (long assayID : whitelist) assertTrue(obtained.contains(assayID), "expected assayID " + assayID + " but not returned" + obt);
		for (long assayID : blacklist) assertFalse(obtained.contains(assayID), "expected not assayID " + assayID + " but was returned" + obt);
	}
}

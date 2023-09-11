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
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for TallyCompletion REST API.
*/

public class TallyCompletionTest
{
	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
	}

	// ------------ faux-mongo tests ------------

	@Test
	public void testTallyCompletion() throws Exception
	{
		JSONObject request = new JSONObject();
		request.put("assayIDList", new long[]{2, 101, 102, 103, 104, 105, 106});
		// NOTE: the basic content doesn't put the history functionality through its paces too aggressively, but the underlying
		//		 HistoricalAssays class has its own relatively thorough test
		
		TallyCompletion tally = new TallyCompletion();
		JSONObject result = tally.processRequest(request, TestUtilities.mockSession());		

		JSONObject days = result.getJSONObject("days");
		assertEquals(4, days.length());
		
		verifySlice(days.getJSONObject("0"), new int[]{101}, new float[]{1.5f});
		verifySlice(days.getJSONObject("1444262400000"), new int[]{2, 102}, new float[]{20, 0.5f});
		verifySlice(days.getJSONObject("1454284800000"), new int[]{2}, new float[]{20});
		verifySlice(days.getJSONObject("1464307200000"), new int[]{2, 102, 103, 104, 105, 106}, new float[]{20, 0.5f, 0, 1, 2, 2});
		JSONObject slice1 = days.getJSONObject("0"), slice2 = days.getJSONObject("1444262400000");
		
		JSONArray assignments = result.getJSONArray("assignments");
		assertThat(assignments.length(), greaterThan(10));
	}
		
	// ------------ private methods ------------

	private void verifySlice(JSONObject obj, int[] keys, float[] values)
	{
		assertEquals(obj.length(), keys.length);
		for (int n = 0; n < keys.length; n++)
		{
			float value = (float)obj.getDouble(String.valueOf(keys[n]));
			assertEquals(values[n], value);
		}
	}
}

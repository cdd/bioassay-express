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

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import javax.servlet.http.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for FindIdentifier REST API.
*/

public class FindIdentifierTest
{
	private FindIdentifier findIdentifier;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		findIdentifier = new FindIdentifier();
		findIdentifier.logger = TestUtilities.mockLogger();
	}

	@Test
	public void testNonPermissive() throws IOException
	{
		JSONObject json = postRequest("");
		assertMatch(json, new long[0], new String[0]);

		json = postRequest("1020");
		assertMatch(json, new long[]{2L}, new String[]{"pubchemAID:1020"});

		json = postRequest("102");
		assertMatch(json, new long[]{102L}, new String[]{"pubchemAID:102"});
	}

	@Test
	public void testPermissive() throws IOException
	{
		JSONObject json = postRequest("", true, true);
		assertMatch(json, new long[0], new String[0]);

		json = postRequest("1020", true, true);
		assertMatch(json, new long[]{2L}, new String[]{"pubchemAID:1020"});

		json = postRequest("102", true, true);
		assertMatch(json, new long[]{2, 102}, new String[]{"pubchemAID:1020", "pubchemAID:102"});
	}

	@Test
	public void testExactUniqueIDList()
	{
		Identifier.Source[] srclist = Common.getIdentifier().getSources();
		List<String> prefixes = Arrays.stream(srclist).map(src -> src.prefix).sorted().collect(Collectors.toList());

		String[] uniqueIDs = FindIdentifier.exactUniqueIDList("123");
		Arrays.sort(uniqueIDs);
		assertEquals(srclist.length, uniqueIDs.length);
		for (int i = 0; i < srclist.length; i++)
		{
			assertThat(uniqueIDs[i], startsWith(prefixes.get(i)));
			assertThat(uniqueIDs[i], endsWith("123"));
		}
	}

	@Test
	public void testCreateIDVariants()
	{
		String[] expected = {"1234", "AID1234", "AID 1234", "AID-1234"};

		for (String id : expected)
		{
			Set<String> result = FindIdentifier.createIDVariants(id, "AID");
			assertThat("Failed for id=" + id, result, hasItems(expected));
			assertEquals(4, result.size(), "Failed for id=" + id);
		}

		// strips whitespace
		Set<String> result = FindIdentifier.createIDVariants(" AID-1234  ", "AID");
		assertThat(result, hasItems(expected));
		assertEquals(4, result.size());

		// tolerant to additional whitespace
		result = FindIdentifier.createIDVariants("AID  1234", "AID");
		assertThat(result, hasItems(expected));
		assertEquals(4, result.size());
		
		// tolerant to additional whitespace
		result = FindIdentifier.createIDVariants("AID -1234", "AID");
		assertThat(result, hasItems(expected));
		assertEquals(4, result.size());
		
		// identifier with different shortname
		result = FindIdentifier.createIDVariants("AID1234", "AZ");
		assertThat(result, hasItems("AID1234", "AZAID1234", "AZ AID1234", "AZ-AID1234"));
		assertEquals(4, result.size());
	}

	// ------------ private methods ------------

	private JSONObject postRequest(String id) throws IOException
	{
		return postRequest(id, false, true);
	}

	private JSONObject postRequest(String id, boolean permissive, boolean partialMatching) throws IOException
	{
		JSONObject json = new JSONObject();
		json.put("id", id);
		json.put("permissive", permissive);
		json.put("partialMatching", partialMatching);
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest(json.toString());
		MockRESTUtilities.MockJSONResponse mockResponse = new MockRESTUtilities.MockJSONResponse();
		findIdentifier.doPost(request, mockResponse.getResponse());
		return mockResponse.getContentAsJSON();
	}

	private void assertMatch(JSONObject json, long[] assayID, String[] uniqueID)
	{
		assertThat(assayID.length, is(uniqueID.length));
		assertThat(json.has("matches"), is(true));
		JSONArray matches = json.getJSONArray("matches");
		assertThat(matches.length(), is(assayID.length));
		List<Long> matchedAssayID = new ArrayList<>();
		List<String> matchedUniqueID = new ArrayList<>();
		for (int i = 0; i < matches.length(); ++i)
		{
			JSONObject match = matches.getJSONObject(i);
			matchedAssayID.add(match.getLong("assayID"));
			matchedUniqueID.add(match.getString("uniqueID"));
		}
		assertThat(matchedAssayID, is(Arrays.stream(assayID).boxed().collect(Collectors.toList())));
		assertThat(matchedUniqueID, is(Arrays.asList(uniqueID)));
	}
}

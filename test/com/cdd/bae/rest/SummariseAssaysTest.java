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

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import javax.servlet.http.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for SummariseAssays REST API.
*/

public class SummariseAssaysTest
{
	private SummariseAssays summariseAssays;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		summariseAssays = new SummariseAssays();
	}

	// ------------ mockito-based tests ------------

	@Test
	public void testCorrectCall() throws IOException
	{
		// we get information using GET request without parameter
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest("{\"uniqueIDList\": [\"pubchemAID:1020\", \"incorrect\"]}");
		MockRESTUtilities.MockJSONResponse mockResponse = new MockRESTUtilities.MockJSONResponse();
		summariseAssays.doPost(request, mockResponse.getResponse());

		JSONArray json = mockResponse.getContentAsJSONArray();
		assertEquals(2, json.length());
		assertItem(json.getJSONObject(0));
		assertEquals(0, json.getJSONObject(1).length(), "Empty object for incorrect uniqueID");

		request = MockRESTUtilities.mockedJSONRequest("{\"assayIDList\": [2, 101, 102]}");
		mockResponse = new MockRESTUtilities.MockJSONResponse();
		summariseAssays.doPost(request, mockResponse.getResponse());

		json = mockResponse.getContentAsJSONArray();
		assertEquals(3, json.length());
		assertItem(json.getJSONObject(0));
	}

	@Test
	public void testTruncateText()
	{
		assertThat(SummariseAssays.truncateText(""), equalTo(""));
		assertThat(SummariseAssays.truncateText(null), equalTo(""));

		String s = "1234567890 1234567890 1234567890 1234567890";
		String text = s;
		assertThat(SummariseAssays.truncateText(text), equalTo(text));

		// multiple new lines are removed
		text = s + "\n\nline1";
		assertThat(SummariseAssays.truncateText(text), equalTo(text.replace("\n\n", " ")));

		// increasing the length
		text = s + "\n" + s + "\n" + s;
		assertThat(SummariseAssays.truncateText(text), equalTo(text.replace("\n", " ")));

		// and more; only three lines used
		String text2long = s + "\n" + s + "\n" + s + "\n" + s;
		assertThat(SummariseAssays.truncateText(text2long), equalTo(text.replace("\n", " ")));

		// lines that are over 80 characters
		text = s + " " + s;
		assertThat(SummariseAssays.truncateText(text), equalTo(text.substring(0, 76) + "..."));
		text = s + " " + s + " " + s;
		assertThat(SummariseAssays.truncateText(text), equalTo(text.substring(0, 98) + "..."));
		// line without whitespace
		text = text.replace(" ", "-");
		assertThat(SummariseAssays.truncateText(text), equalTo(text.substring(0, 80) + "..."));
	}

	// ------------ faux-mongo tests ------------

	// TODO

	// ------------ private methods ------------

	private void assertItem(JSONObject json)
	{
		assertTrue(json.has("assayID"));
		assertTrue(json.has("uniqueID"));
		assertTrue(json.has("shortText"));
		assertTrue(json.has("schemaURI"));
		assertTrue(json.has("annotations"));
	}
}

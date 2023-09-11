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
import com.cdd.bae.rest.MockRESTUtilities.*;
import com.cdd.bae.rest.RESTException.*;
import com.cdd.bae.util.*;
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.stream.*;

import javax.servlet.http.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for Search REST API.
*/

public class SearchTest
{
	private static final String STATUS = "status";
	private static final String PROP_URI = "http://www.bioassayontology.org/bao#BAO_0095010";
	private static final String VALUE_URI_1 = "http://www.bioassayontology.org/bao#BAO_0000574";
	private static final String VALUE_URI_2 = "http://www.bioassayontology.org/bao#BAO_0000573";
	private static final String VALUE_URI_3 = "http://www.bioassayontology.org/bao#BAO_0002198";
	private static final String VALUE_URI_4 = "http://www.bioassayontology.org/bao#BAO_0002429";
	private static final String VALUE_URI_5 = "http://www.bioassayontology.org/bao#BAO_0002155";
	private static final String VALUE_URI_6 = "http://www.bioassayontology.org/bao#BAO_0000545";
	private Search searchService;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		searchService = new Search();
		searchService.logger = TestUtilities.mockLogger();
	}

	@Test
	public void testSearchCall() throws IOException
	{
		// this is not testing the code entirely; due to the mocking, we get no hits
		String search = "[{\"propURI\": \"" + PROP_URI + "\", \"valueURI\": \"" + PROP_URI + "\"}]";
		MockJSONResponse response = doServletCall("{\"assayIDList\": [1004,1005], \"search\": " + search + "}");
		JSONObject json = response.getContentAsJSON();
		assertEquals(1, json.length());
		assertEquals(0, json.getJSONArray("results").length());
	}
	
	@Test
	public void testCompareProperty()
	{
		DataStore.Assay assay = new DataStore.Assay();
		assay.schemaURI = "http://www.bioassayontology.org/bas#";
		assay.annotations = new Annotation[]{new DataStore.Annotation(PROP_URI, VALUE_URI_1)};

		assertEquals(1.0, searchService.compareProperty(new DataStore.Annotation(PROP_URI, VALUE_URI_1), assay), 0.001);
		assertEquals(1.0 / 3, searchService.compareProperty(new DataStore.Annotation(PROP_URI, VALUE_URI_2), assay), 0.001);
		assertEquals(1.0 / 4, searchService.compareProperty(new DataStore.Annotation(PROP_URI, VALUE_URI_3), assay), 0.001);
		assertEquals(1.0 / 5, searchService.compareProperty(new DataStore.Annotation(PROP_URI, VALUE_URI_4), assay), 0.001);
		assertEquals(0, searchService.compareProperty(new DataStore.Annotation(PROP_URI, VALUE_URI_5), assay), 0.001);
		assertEquals(0, searchService.compareProperty(new DataStore.Annotation(PROP_URI, VALUE_URI_6), assay), 0.001);

	}

	@Test
	public void testExceptions() throws IOException
	{
		// only can search chunks of 100 assays
		String s = IntStream.range(0, 101).mapToObj(Integer::toString).collect(Collectors.joining(","));
		MockJSONResponse response = doServletCall("{\"assayIDList\": [" + s + "]}");
		JSONObject json = response.getContentAsJSON();
		assertEquals(HTTPStatus.BAD_REQUEST.code(), json.getInt(STATUS));

		// require a search list
		s = IntStream.range(0, 10).mapToObj(Integer::toString).collect(Collectors.joining(","));
		response = doServletCall("{\"assayIDList\": [" + s + "]}");
		json = response.getContentAsJSON();
		assertEquals(HTTPStatus.BAD_REQUEST.code(), json.getInt(STATUS));

		s = IntStream.range(0, 10).mapToObj(Integer::toString).collect(Collectors.joining(","));
		response = doServletCall("{\"assayIDList\": [" + s + "],  \"search\": []}");
		json = response.getContentAsJSON();
		assertEquals(HTTPStatus.BAD_REQUEST.code(), json.getInt(STATUS));
	}

	// ------------ private methods ------------

	private MockJSONResponse doServletCall(String json) throws IOException
	{
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest(json);
		MockJSONResponse response = new MockRESTUtilities.MockJSONResponse();
		searchService.doPost(request, response.getResponse());
		return response;
	}
}

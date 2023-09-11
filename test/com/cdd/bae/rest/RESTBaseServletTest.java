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
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.*;

import javax.servlet.http.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test the basic functionality of RESTBaseServlet.

	The tests use the GetAssay servlet, however focus on the functionality of the RESTBaseServlet class.
*/

public class RESTBaseServletTest extends EndpointEmulator
{
	private GetAssay getAssay;
	private DataAssay dataAssay;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		getAssay = new GetAssay();
		setRestService(getAssay);
		dataAssay = mongo.getDataStore().assay();
	}

	@Test
	public void testCorrectCall() throws IOException
	{
		// we can get information using JSON content of the request
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest("{assayID: 2, countCompounds: false}");
		MockRESTUtilities.MockJSONResponse response = new MockRESTUtilities.MockJSONResponse();
		getAssay.doPost(request, response.getResponse());
		JSONObject json = response.getContentAsJSON();
		assertResponse(json, 2);
		assertFalse(json.has("countCompounds"));

		// and override using POST parameters
		request = MockRESTUtilities.mockedJSONRequest("{assayID: 200}", getParameters(2, false));
		response = new MockRESTUtilities.MockJSONResponse();
		getAssay.doPost(request, response.getResponse());
		assertResponse(response.getContentAsJSON(), 2);

		// or simply as GET parameters
		request = MockRESTUtilities.mockedGETRequest(getParameters(2, false));
		response = new MockRESTUtilities.MockJSONResponse();
		getAssay.doGet(request, response.getResponse());
		assertResponse(response.getContentAsJSON(), 2);

		// or POST parameters
		request = MockRESTUtilities.mockedPOSTRequest(getParameters(2, false));
		response = new MockRESTUtilities.MockJSONResponse();
		getAssay.doPost(request, response.getResponse());
		assertResponse(response.getContentAsJSON(), 2);
	}

	@Test
	public void testExceptions() throws IOException
	{
		MockRESTUtilities.MockJSONResponse mockResponse;
		HttpServletRequest request;
		HttpServletResponse response;

		// incorrect JSON
		mockResponse = new MockRESTUtilities.MockJSONResponse();
		request = MockRESTUtilities.mockedJSONRequest("{assayID: 2");
		response = mockResponse.getResponse();
		getAssay.doPost(request, response);
		TestUtilities.assertErrorResponse(mockResponse.getContentAsJSON(), RESTException.HTTPStatus.BAD_REQUEST);
		verify(response).setStatus(RESTException.HTTPStatus.BAD_REQUEST.code());

		// send feedback about missing required parameters
		mockResponse = new MockRESTUtilities.MockJSONResponse();
		request = MockRESTUtilities.mockedJSONRequest("{}");
		response = mockResponse.getResponse();
		getAssay.doPost(request, response);
		TestUtilities.assertErrorResponse(mockResponse.getContentAsJSON(), RESTException.HTTPStatus.BAD_REQUEST);
		verify(response).setStatus(RESTException.HTTPStatus.BAD_REQUEST.code());

		// errors that happened in the actual servlet implementation (processRequest) are sent back
		mockResponse = new MockRESTUtilities.MockJSONResponse();
		request = MockRESTUtilities.mockedJSONRequest("{assayID: \"abcde\"}");
		response = mockResponse.getResponse();
		getAssay.doPost(request, response);
		TestUtilities.assertErrorResponse(mockResponse.getContentAsJSON(), RESTException.HTTPStatus.BAD_REQUEST);
		verify(response).setStatus(RESTException.HTTPStatus.BAD_REQUEST.code());
	}

	@Test
	public void testUnexpectedExceptions() throws IOException, RESTException
	{
		// unexpected errors are reported as internal server error
		MockRESTUtilities.MockJSONResponse mockResponse = new MockRESTUtilities.MockJSONResponse();
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest("{assayID: 118}");
		HttpServletResponse response = mockResponse.getResponse();

		getAssay = spy(new GetAssay());
		getAssay.logger = TestUtilities.mockLogger();
		doThrow(new RuntimeException("Unexpected exception")).when(getAssay).processRequest(any(), any());

		getAssay.doPost(request, response);
		TestUtilities.assertErrorResponse(mockResponse.getContentAsJSON(), RESTException.HTTPStatus.INTERNAL_SERVER_ERROR);
		verify(response).setStatus(RESTException.HTTPStatus.INTERNAL_SERVER_ERROR.code());
	}

	@Test
	public void testETagSupport() throws IOException
	{
		long watermark = dataAssay.getWatermark();

		GetLiteralValues getLiteralValues = new GetLiteralValues();
		MockJSONRequest requestWrapper = new MockJSONRequest("{\"schemaURI\":\"http://www.bioassayontology.org/bas#\",\"locator\":\"0\"}");
		MockServletResponse responseWrapper = new MockServletResponse();

		HttpServletRequest request = requestWrapper.getJSONRequest();
		HttpServletResponse response = responseWrapper.getResponse();
		getLiteralValues.doPost(request, response);
		assertThat(responseWrapper.getHeader("ETag"), is("assay-" + watermark));
		assertThat(response.getStatus(), is(RESTException.HTTPStatus.OK.code()));


		requestWrapper.setHeader("If-None-Match", "assay-" + watermark);
		request = requestWrapper.getJSONRequest();
		getLiteralValues.doPost(request, response);
		assertThat(response.getStatus(), is(RESTException.HTTPStatus.NOT_MODIFIED.code()));

		request = requestWrapper.getJSONRequest();
		responseWrapper.reset();
//		response = responseWrapper.getResponse();
		dataAssay.nextWatermark();
		getLiteralValues.doPost(request, response);
		assertThat(responseWrapper.getHeader("ETag"), is("assay-" + dataAssay.getWatermark()));
		assertThat(response.getStatus(), is(RESTException.HTTPStatus.OK.code()));
	}

	// ------------ private methods ------------

	private void assertResponse(JSONObject json, long assayID)
	{
		assertTrue(json.has("assayID"));
		assertEquals(assayID, json.getLong("assayID"));
		assertTrue(json.has("uniqueID"));
		assertTrue(json.has("annotations"));
		assertTrue(json.has("curatorID"));
	}

	private static Map<String, String> getParameters(int assayID, boolean countCompounds)
	{
		Map<String, String> parameters = new HashMap<>();
		parameters.put("assayID", String.valueOf(assayID));
		parameters.put("countCompounds", countCompounds ? "true" : "false");
		return parameters;
	}
}

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

import com.cdd.bae.rest.MockRESTUtilities.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import javax.servlet.http.*;

import org.json.*;

public class EndpointEmulator
{
	protected RESTBaseServlet restService;
	
	protected void setRestService(RESTBaseServlet restService)
	{
		this.restService = restService;
		this.restService.logger = TestUtilities.mockLogger();
	}
	
	protected MockRESTUtilities.MockJSONResponse doGet() throws IOException
	{
		HttpServletRequest request = new MockJSONRequest().getJSONRequest();
		return iDoGet(request);
	}

	protected MockRESTUtilities.MockJSONResponse doPost(JSONObject payload) throws IOException
	{
		return doPost(payload.toString());
	}

	protected MockRESTUtilities.MockJSONResponse doPost(String payload) throws IOException
	{
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest(payload);
		return iDoCall(request);
	}

	protected MockRESTUtilities.MockJSONResponse doPost(Map<String, String> parameters) throws IOException
	{
		HttpServletRequest request = MockRESTUtilities.mockedPOSTRequest(parameters);
		return iDoCall(request);
	}
	
	protected JSONObject doPostJSON(JSONObject payload) throws IOException
	{
		return doPost(payload.toString()).getContentAsJSON();
	}
	protected JSONObject doPostJSON(Map<String, String> parameters) throws IOException
	{
		return doPost(parameters).getContentAsJSON();
	}


	protected void assertStatus(JSONObject json, boolean success, RESTBaseServlet.Status status)
	{
		assertEquals(success, json.getBoolean("success"));
		assertEquals(status.toString(), json.getString("status"));
	}

	protected void assertStatus(JSONObject json, boolean success, int status)
	{
		assertEquals(success, json.getBoolean("success"));
		assertEquals(status, json.getInt("status"));
	}

	protected MockRESTUtilities.MockJSONResponse doCall(Map<String, String> parameters) throws IOException
	{
		return doPost(parameters);
	}
	protected MockRESTUtilities.MockJSONResponse doCall(String payload) throws IOException
	{
		return doPost(payload);
	}
	
	// ------------ private methods ------------
	
	private MockRESTUtilities.MockJSONResponse iDoCall(HttpServletRequest request) throws IOException
	{
		MockRESTUtilities.MockJSONResponse response = new MockRESTUtilities.MockJSONResponse();
		restService.doPost(request, response.getResponse());
		return response;
	}
	
	private MockRESTUtilities.MockJSONResponse iDoGet(HttpServletRequest request) throws IOException
	{
		MockRESTUtilities.MockJSONResponse response = new MockRESTUtilities.MockJSONResponse();
		restService.doGet(request, response.getResponse());
		return response;
	}
}

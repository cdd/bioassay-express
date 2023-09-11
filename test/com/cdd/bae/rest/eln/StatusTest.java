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

package com.cdd.bae.rest.eln;

import com.cdd.bae.rest.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import javax.servlet.http.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for Status service
*/

public class StatusTest
{
	@Test
	public void testDefault() throws IOException
	{
		Status status = new Status();
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest("{}");
		MockRESTUtilities.MockJSONResponse response = new MockRESTUtilities.MockJSONResponse();
		status.doPost(request, response.getResponse());
		JSONObject json = response.getContentAsJSON();
		assertEquals(1, json.length());
		assertEquals("OK", json.getString("status"));
	}
}

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
import com.cdd.bao.template.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;
import java.util.*;

import javax.servlet.http.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for GetLiteralValues REST API.
*/

public class GetLiteralValuesTest
{
	private GetLiteralValues getLiteralValues;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		getLiteralValues = new GetLiteralValues();
		getLiteralValues.logger = TestUtilities.mockLogger();
	}

	@Test
	public void testLocator() throws IOException
	{
		// we get information using GET request without parameter
		HttpServletRequest request = MockRESTUtilities.mockedPOSTRequest(getParameters("0"));
		MockRESTUtilities.MockJSONResponse mockResponse = new MockRESTUtilities.MockJSONResponse();
		HttpServletResponse response = mockResponse.getResponse();
		getLiteralValues.doPost(request, response);

		JSONObject json = mockResponse.getContentAsJSON();
		assertThat(json.length(), is(1));
		assertThat(json.has("Counter Screen for Glucose-6-Phosphate Dehydrogenase-based Primary Assay"), is(true));
	}
	
	@Test
	public void testNumbers() throws IOException, RESTException
	{
		Schema schema = Common.getSchemaCAT();
		
		String propURI = ModelSchema.expandPrefix("bao:BAO_0002916"); // threshold
		Schema.Assignment assn = schema.findAssignmentByProperty(propURI)[0];

		int prevCount = Common.getDataStore().assay().countAssays();
		DataObject.Assay assay = new DataObject.Assay();
		assay.uniqueID = "pubchemAID:10001";
		assay.schemaURI = schema.getSchemaPrefix();
		assay.isCurated = true;
		DataObject.TextLabel labelValid = new DataObject.TextLabel(assn.propURI, "20.0", assn.groupNest());
		DataObject.TextLabel labelInvalid = new DataObject.TextLabel(assn.propURI, "20,0", assn.groupNest());
		assay.textLabels = new DataObject.TextLabel[]{labelValid, labelInvalid};
		Common.getDataStore().assay().submitAssay(assay);
		assertThat(assay.assayID, greaterThan(0L));
		assertThat(Common.getDataStore().assay().countAssays(), is(prevCount + 1));
		
		JSONObject request = new JSONObject();
		request.put("schemaURI", schema.getSchemaPrefix());
		request.put("locator", schema.locatorID(assn));
		JSONObject response = getLiteralValues.processRequest(request, TestUtilities.mockSessionCurator());
		Set<String> literals = response.keySet();
		
		assertThat(literals, hasItem(labelValid.text));
		assertThat(literals, not(hasItem(labelInvalid.text)));
	}
	

	@Test
	public void testServletHeadersReturned() throws IOException
	{
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest("{\"schemaURI\":\"http://www.bioassayontology.org/bas#\",\"locator\":\"0\"}");
		MockServletResponse responseWrapper = new MockServletResponse();
		HttpServletResponse response = responseWrapper.getResponse();

		getLiteralValues.doPost(request, response);

		assertThat(responseWrapper.getHeader("ETag"), is("assay-100000"));
	}

	// ------------ private methods ------------

	private static Map<String, String> getParameters(String locator)
	{
		Map<String, String> parameters = new HashMap<>();
		parameters.put("schemaURI", "http://www.bioassayontology.org/bas#"); // get the test schema
		parameters.put("locator", locator);
		return parameters;
	}
}

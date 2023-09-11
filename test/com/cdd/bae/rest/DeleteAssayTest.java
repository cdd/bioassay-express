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

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.MatcherAssert.assertThat; 
import static org.hamcrest.Matchers.*;

import java.io.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for DeleteAssay REST API.
*/

public class DeleteAssayTest
{
	private static final String HOLDING_ID = "holdingID";
	private static final String STATUS = "status";
	private static final String SUCCESS = "success";
	private static final String ASSAY_ID = "assayID";
	private DataStore store;
	private DeleteAssay deleteAssay;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		store = mongo.getDataStore();

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		deleteAssay = new DeleteAssay();
	}

	@Test
	public void testPermissions()
	{
		assertThat(deleteAssay.requireSession(), is(true));
		assertThat(deleteAssay.hasPermission(TestUtilities.mockNoSession()), is(false));
		assertThat(deleteAssay.hasPermission(TestUtilities.mockSessionBlocked()), is(false));
		assertThat(deleteAssay.hasPermission(TestUtilities.mockSession()), is(true));
		assertThat(deleteAssay.hasPermission(TestUtilities.mockSessionCurator()), is(true));
		assertThat(deleteAssay.hasPermission(TestUtilities.mockSessionAdmin()), is(true));
	}

	@Test
	public void testCorrectCall() throws IOException, RESTException
	{
		JSONObject request = new JSONObject();
		request.put(ASSAY_ID, 2);
		JSONObject result = deleteAssay.processRequest(request, TestUtilities.mockSessionCurator());
		assertThat(result.getBoolean(SUCCESS), is(true));
		assertThat(result.getString(STATUS), is("holding"));
		
		// make sure the assay has been created
		long holdingID = result.getLong(HOLDING_ID);
		assertThat(holdingID, greaterThan(0L));
		assertHolding(2, holdingID);
	}

	@Test
	public void testExceptions() throws RESTException
	{
		
		JSONObject request = new JSONObject();
		request.put(ASSAY_ID, 0);
		assertThrows(RESTException.class, () -> deleteAssay.processRequest(request, TestUtilities.mockSessionCurator()));

		request.put(ASSAY_ID, 12345678);
		JSONObject result = deleteAssay.processRequest(request, TestUtilities.mockSessionCurator());
		assertThat(result.getBoolean(SUCCESS), is(false));
		assertThat(result.getString(STATUS), is("nonexistent"));
	}

	@Test
	public void testAddThenDelete() throws RESTException, IOException 
	{
		// add an assay directly
		DataObject.Assay assay = new DataObject.Assay();
		assay.uniqueID = "test:123";
		assay.text = "test";
		assay.schemaURI = Common.getSchemaCAT().getSchemaPrefix();
		assay.annotations = new DataObject.Annotation[0];
		assay.textLabels = new DataObject.TextLabel[0];
		assay.curatorID = "test";
		store.assay().submitAssay(assay);
		assertThat(assay.assayID, greaterThan(0L));
		
		// now delete it using the API, with curator-level permission (which makes a holding bay request)
		JSONObject request = new JSONObject();
		request.put(ASSAY_ID, assay.assayID);
		JSONObject result = deleteAssay.processRequest(request, TestUtilities.mockSessionCurator());
		assertTrue(result.getBoolean(SUCCESS));
		assertEquals("holding", result.getString(STATUS));
		
		// make sure the holding bay item has been created
		long holdingID = result.getLong(HOLDING_ID);
		assertThat(holdingID, greaterThan(0L));
		assertHolding(assay.assayID, holdingID);
	}

	// ------------ private methods ------------
	
	private void assertHolding(long assayID, long holdingID)
	{
		DataObject.Holding holding = store.holding().getHolding(holdingID);
		assertNotNull(holding);
		assertTrue(holding.deleteFlag);
		assertEquals(assayID, holding.assayID);
	}
}

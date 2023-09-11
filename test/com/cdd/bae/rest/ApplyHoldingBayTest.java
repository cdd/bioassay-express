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
import com.cdd.bae.rest.RESTBaseServlet.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.json.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

/*
	Test for GetHoldingBay REST API.
*/

public class ApplyHoldingBayTest extends EndpointEmulator
{
	private ApplyHoldingBay applyHoldingBay;
	private DataAssay dataAssay;

	private DataHolding dataHolding;
	private DataMeasure dataMeasure;
	private FauxMongo mongo;

	@BeforeEach
	public void initialize() throws ConfigurationException, IOException
	{
		mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
		
		dataHolding = new DataHolding(mongo.getDataStore());
		dataMeasure = new DataMeasure(mongo.getDataStore());
		dataAssay = new DataAssay(mongo.getDataStore());


		applyHoldingBay = new ApplyHoldingBay();
		setRestService(applyHoldingBay);
		restService = Mockito.spy(restService);
		doReturn(TestUtilities.mockSessionCurator()).when(restService).getSession(any());
	}

	// ------------ mockito-based tests ------------

	@Test
	public void testCallPermission() throws IOException
	{
		assertThat(applyHoldingBay.requireSession(), is(true));

		Map<String, String> parameters = new HashMap<>();

		// not logged in
		doReturn(TestUtilities.mockNoSession()).when(restService).getSession(any());
		assertThat(doPostJSON(parameters).getBoolean("success"), is(false));

		// blocked user
		doReturn(TestUtilities.mockSessionBlocked()).when(restService).getSession(any());
		assertThat(doPostJSON(parameters).getBoolean("success"), is(false));

		// default logged in user
		doReturn(TestUtilities.mockSession()).when(restService).getSession(any());
		assertThat(doPostJSON(parameters).getBoolean("success"), is(false));

		// curator
		doReturn(TestUtilities.mockSessionCurator()).when(restService).getSession(any());
		assertThat(doPostJSON(parameters).getBoolean("success"), is(true));

		// administrator
		doReturn(TestUtilities.mockSessionCurator()).when(restService).getSession(any());
		assertThat(doPostJSON(parameters).getBoolean("success"), is(true));
	}

	@Test
	public void testEmptyCall() throws IOException
	{
		JSONObject json = new JSONObject();
		json.put("applyList", new JSONArray());
		json.put("deleteList", new JSONArray());
		
		JSONObject result = doPostJSON(json);
		assertResult(result, true, 0, 0);
	}

	@Test
	public void testDeleteHoldingEntries() throws IOException
	{
		int nholding = dataHolding.countTotal();
		List<Long> holdingIDs = Arrays.asList(ArrayUtils.toObject(dataHolding.fetchHoldings()));
		
		long holdingID = holdingIDs.get(0);
		
		JSONObject json = new JSONObject();
		JSONArray arr = new JSONArray().put(holdingID);
		json.put("deleteList", arr);
		
		// delete existing holding ID
		JSONObject result = doPostJSON(json);
		assertResult(result, true, 0, 0);
		assertThat(dataHolding.countTotal(), is(nholding - 1));
		
		// call with non-existing holding ID
		result = doPostJSON(json);
		assertResult(result, true, 0, 0);
		assertThat(dataHolding.countTotal(), is(nholding - 1));
		
		// call with full list of holding IDs
		holdingIDs.stream().forEach(id -> arr.put(id));
		result = doPostJSON(json);
		assertResult(result, true, 0, 0);
		assertThat(dataHolding.countTotal(), is(0));
	}
	
	@Test
	public void testApplyHoldingEntries_delete() throws IOException
	{
		// Prepare
		int nholding = dataHolding.countTotal();
		
		// pick a holding that deletes an assay
		long holdingID = 16L;
		Holding holding = dataHolding.getHolding(holdingID);
		assertThat(holding.deleteFlag, is(true));
		
		// add measurement for assay
		Measurement m = new Measurement();
		m.assayID = holding.assayID;
		m.compoundID = new long[0];
		dataMeasure.updateMeasurement(m);
		assertThat(dataMeasure.getMeasurements(holding.assayID).length, is(1));
		
		// Case: applying the holding for an existing assay
		JSONObject json = new JSONObject();
		JSONArray arr = new JSONArray().put(holdingID);
		json.put("applyList", arr);

		JSONObject result = doPostJSON(json);
		assertResult(result, true, 1, 1);
		assertThat(result.getJSONArray("holdingIDList").getLong(0), is(holdingID));
		assertThat(result.getJSONArray("assayIDList").getLong(0), is(holding.assayID));

		assertThat(dataHolding.countTotal(), is(nholding - 1));
		assertThat(dataMeasure.getMeasurements(holding.assayID).length, is(0));

		// let's apply it a second time - nothing changes
		int dbHash = mongo.getDatabaseHash();
		result = doPostJSON(json);
		assertResult(result, true, 0, 0);
		assertThat(mongo.getDatabaseHash(), is(dbHash));
		
		// Case: assay deleted before applying the holding
		mongo.restoreContent();
		dataAssay.deleteAssay(holding.assayID);

		result = doPostJSON(json);
		assertResult(result, true, 0, 0);
		assertThat(dataHolding.countTotal(), is(nholding - 1));
		
		// Case: holding has delete flag but no assayID
		mongo.restoreContent();
		holding.assayID = 0;
		dataHolding.depositHolding(holding);
		nholding = dataHolding.countTotal();
		arr = new JSONArray().put(holding.holdingID);
		json.put("applyList", arr);
		
		result = doPostJSON(json);
		assertResult(result, true, 0, 0);
		assertThat(dataHolding.countTotal(), is(nholding - 1));
	}
	
	@Test
	public void testApplyHoldingEntries_change() throws IOException
	{
		// Prepare
		int nholding = dataHolding.countTotal();

		JSONObject json = new JSONObject();
		JSONArray arr = new JSONArray().put(1L).put(2L);
		json.put("applyList", arr);

		JSONObject result = doPostJSON(json);
		assertResult(result, true, 2, 2);
		assertThat(result.getJSONArray("holdingIDList").getLong(0), is(1L));
		assertThat(result.getJSONArray("holdingIDList").getLong(1), is(2L));
		assertThat(result.getJSONArray("assayIDList").getLong(0), is(2L));
		assertThat(result.getJSONArray("assayIDList").getLong(1), is(2L));
		assertThat(dataHolding.countTotal(), is(nholding - 2));

		// Case: holding bay entries were created before assay was registered
		// associate based on uniqueID
		mongo.restoreContent();
		Holding holding = dataHolding.getHolding(2L);
		Assay assay = dataAssay.getAssay(holding.assayID);
		int nannotations = assay.annotations.length;
		holding.assayID = 0;
		holding.uniqueID = assay.uniqueID;
		dataHolding.depositHolding(holding);
		
		arr = new JSONArray().put(holding.holdingID);
		json.put("applyList", arr);
		
		result = doPostJSON(json);
		assertResult(result, true, 1, 1);
		assertThat(result.getJSONArray("holdingIDList").getLong(0), is(holding.holdingID));
		assertThat(result.getJSONArray("assayIDList").getLong(0), is(assay.assayID));
		
		Assay assayAfter = dataAssay.getAssay(assay.assayID);
		assertThat(assayAfter.annotations.length, is(nannotations + 1));
		
		// Case: holding bay entry for a new assay
		mongo.restoreContent();
		int nassays = dataAssay.countAssays();
		holding = dataHolding.getHolding(2L);
		holding.assayID = 0;
		dataHolding.depositHolding(holding);
		
		arr = new JSONArray().put(holding.holdingID);
		json.put("applyList", arr);

		result = doPostJSON(json);
		assertResult(result, true, 1, 1);
		assertThat(result.getJSONArray("holdingIDList").getLong(0), is(holding.holdingID));
		assertThat(result.getJSONArray("assayIDList").getLong(0), is(100000L));
		assertThat(dataAssay.countAssays(), is(nassays + 1));
	}

	// ------------ private methods ------------

	private static void assertResult(JSONObject result, boolean success, int nholding, int nassay)
	{
		assertThat(result.getBoolean("success"), is(success));
		assertThat(result.getString("status"), is(Status.HOLDING.toString()));
		assertThat("number holdings", result.getJSONArray("holdingIDList").length(), is(nholding));
		assertThat("number assays", result.getJSONArray("assayIDList").length(), is(nassay));
	}
}

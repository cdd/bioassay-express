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
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.MatcherAssert.assertThat; 
import static org.hamcrest.Matchers.*;

import java.io.*;

import org.apache.commons.lang3.*;
import org.json.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

/*
	Test for SubmitAssay REST API.
*/

public class SubmitAssayTest extends EndpointEmulator
{
	private static DataStore store;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		store = mongo.getDataStore();
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(store);

		SubmitAssay submitAssay = new SubmitAssay();
		setRestService(submitAssay);
		restService = Mockito.spy(restService); 
		when(restService.hasPermission(any())).thenReturn(true);
	}

	@Test
	public void testCallDefaultPermission() throws IOException
	{
		// mock permission to modify
		doReturn(TestUtilities.mockSession()).when(restService).getSession(any());
		
		// we can get information using JSON content of the request
		MockRESTUtilities.MockJSONResponse response = doCall("{assayID: 118, added: [], removed: []}");
		JSONObject json = response.getContentAsJSON();
		assertStatus(json, true, RESTBaseServlet.Status.HOLDING);
		assertEquals(10000000L, json.getLong("holdingID"));
	}

	@Test
	public void testCallCuratorPermission() throws IOException
	{
		// mock permission to modify
		doReturn(TestUtilities.mockSessionCurator()).when(restService).getSession(any());

		// create a new assay with a given uniqueID (two assays already associated with this pubchemAID)
		MockRESTUtilities.MockJSONResponse response = doCall("{\"uniqueID\": \"pubchemAID:1020\", added: [], removed: []}");
		JSONObject json = response.getContentAsJSON();
		assertStatus(json, true, RESTBaseServlet.Status.APPLIED);
		assertEquals(2, json.getLong("assayID"));

		// modify existing assay
		response = doCall("{assayID: 2, added: [], removed: []}");
		json = response.getContentAsJSON();
		assertStatus(json, true, RESTBaseServlet.Status.APPLIED);
		assertEquals(2, json.getLong("assayID"));
	}

	@Test
	public void testInsufficientPermission() throws IOException
	{
		when(restService.hasPermission(any())).thenReturn(false);
		
		MockRESTUtilities.MockJSONResponse response = doCall("{\"assayID\": 2}");
		JSONObject json = response.getContentAsJSON();
		assertStatus(json, false, RESTBaseServlet.Status.NOLOGIN);
	}
	
	@Test
	public void testGetAnnotationsLabels()
	{
		DataStore.Holding holding = new DataStore.Holding();
		JSONArray json = new JSONArray();
		json.put(new JSONObject().put("propURI", "URI-1").put("valueURI", "value"));
		json.put(new JSONObject().put("propURI", "URI-2").put("valueLabel", "label"));
		json.put(new JSONObject().put("propURI", "URI-3").put("valueURI", ""));
		json.put(new JSONObject().put("propURI", "URI-4").put("valueLabel", ""));
		json.put(new JSONObject().put("propURI", "URI-5").put("valueURI", JSONObject.NULL));
		json.put(new JSONObject().put("propURI", "URI-6").put("valueLabel", JSONObject.NULL));
		json.put(new JSONObject().put("propURI", "URI-7"));
		SubmitAssay.addAnnotationsLabels(holding, true, json);
		assertEquals(1, holding.annotsAdded.length);
		assertEquals(1, holding.labelsAdded.length);
		DataStore.Annotation annot = holding.annotsAdded[0];
		assertEquals("URI-1", annot.propURI);
		assertEquals("value", annot.valueURI);
		TextLabel label = holding.labelsAdded[0];
		assertEquals("URI-2", label.propURI);
		assertEquals("label", label.text);
	}

	@Test
	public void testVariousNewPermissions() throws RESTException
	{
		SubmitAssay service = new SubmitAssay();
		
		// a blocked user can't be allowed to do anything
		Assay assay = dummyAssay();
		Session session = TestUtilities.mockSessionBlocked();
		assertFalse(service.hasPermission(session));
		
		// a default user can add to the holding bay (implicitly)
		session = TestUtilities.mockSession();
		assertTrue(service.hasPermission(session));
		JSONObject request = formulateRequest(assay);
		assertTrue(service.hasPermission(session));
		JSONObject result = service.processRequest(request, session);
		assertSubmittedHolding(result);
		
		// default users are NOT allowed to update content directly (it gets diverted to the holding bay)
		request = formulateRequest(assay);
		request.put("holdingBay", false);
		result = service.processRequest(request, session);
		assertSubmittedHolding(result);
		
		// curators get to add content to the holding bay, if they ask for it explicitly
		session = TestUtilities.mockSessionCurator();
		assertTrue(service.hasPermission(session));
		request = formulateRequest(assay);
		request.put("holdingBay", true);
		result = service.processRequest(request, session);
		assertSubmittedHolding(result);
		
		// curators can add content directly (the default)
		request = formulateRequest(assay);
		result = service.processRequest(request, session);
		assertSubmittedDirectly(result);
		
		// same deal for admins
		session = TestUtilities.mockSessionAdmin();
		assertTrue(service.hasPermission(session));
		request = formulateRequest(assay);
		result = service.processRequest(request, session);
		assertSubmittedDirectly(result);
	}
	
	@Test
	public void testVariousModPermissions() throws RESTException
	{
		// submit an assay directly, so it can be used to pile up changes
		Assay baseAssay = dummyAssay();
		JSONObject request = formulateRequest(baseAssay);
		JSONObject result = restService.processRequest(request, TestUtilities.mockSessionCurator());
		long assayID = assertSubmittedDirectly(result);
		
		// submit a modified version as default user: goes to the holding bay
		Assay assay = dummyAssay();
		Annotation annot1 = new Annotation(ModelSchema.expandPrefix("bao:BAO_0002854"), ModelSchema.expandPrefix("bao:fnord1"));
		assay.assayID = assayID;
		assay.annotations = new Annotation[]{annot1};
		assay.textLabels = new TextLabel[0];
		request = formulateRequest(assay);
		result = restService.processRequest(request, TestUtilities.mockSession());
		long holdingID = assertSubmittedHolding(result);
		Holding holding = store.holding().getHolding(holdingID);
		assertNotNull(holding);
		assertEquals(assayID, holding.assayID);
		assertEquals(1, holding.annotsAdded.length);
		assertEquals(annot1.toString(), holding.annotsAdded[0].toString());

		// submit another modified version as curator: goes directly into database
		assay = dummyAssay();
		Annotation annot2 = new Annotation(ModelSchema.expandPrefix("bao:BAO_0002854"), ModelSchema.expandPrefix("bao:fnord2"));
		assay.assayID = assayID;
		assay.annotations = ArrayUtils.add(assay.annotations, annot2);
		request = formulateRequest(assay);
		result = restService.processRequest(request, TestUtilities.mockSessionCurator());
		long updatedID = assertSubmittedDirectly(result);
		assertEquals(assayID, updatedID);
		assay = store.assay().getAssay(assayID);
		assertNotNull(assay);
		assertEquals(2, assay.annotations.length);
		assertTrue(assay.annotations[0].toString().equals(annot2.toString()) ||
				   assay.annotations[1].toString().equals(annot2.toString()));
		assertEquals(2, assay.history.length);
	}

	// ------------ private methods ------------
	
	private static int idWatermark = 100;
	private DataObject.Assay dummyAssay()
	{
		Assay assay = new DataObject.Assay();
		assay.uniqueID = "foo:" + (++idWatermark);
		assay.text = "bar";
		assay.schemaURI = Common.getSchemaCAT().getSchemaPrefix();
		Annotation annot = new Annotation(ModelSchema.expandPrefix("bao:BAO_0002854"), ModelSchema.expandPrefix("bao:BAO_0000010"));
		assay.annotations = new Annotation[]{annot};
		TextLabel label = new TextLabel(ModelSchema.expandPrefix("bao:BAO_0002853"), "foo bar");
		assay.textLabels = new DataObject.TextLabel[]{label};
		return assay;
	}
	
	// builds a submission request, on the assumption that the annotations are going in the "added" section
	private JSONObject formulateRequest(Assay assay)
	{
		JSONObject json = new JSONObject();
		if (assay.assayID > 0) json.put("assayID", assay.assayID);
		if (Util.notBlank(assay.uniqueID)) json.put("uniqueID", assay.uniqueID);
		json.put("schemaURI", assay.schemaURI);
		if (Util.notBlank(assay.text)) json.put("text", assay.text);

		JSONArray jsonAdded = new JSONArray();
		if (assay.annotations != null) for (Annotation annot : assay.annotations)
		{
			JSONObject jsonAnnot = new JSONObject();
			jsonAnnot.put("propURI", annot.propURI);
			jsonAnnot.put("groupNest", annot.groupNest);
			jsonAnnot.put("valueURI", annot.valueURI);
			jsonAdded.put(jsonAnnot);
		}
		if (assay.textLabels != null) for (TextLabel label : assay.textLabels)
		{
			JSONObject jsonLabel = new JSONObject();
			jsonLabel.put("propURI", label.propURI);
			jsonLabel.put("groupNest", label.groupNest);
			jsonLabel.put("valueLabel", label.text);
			jsonAdded.put(jsonLabel);
		}
		json.put("added", jsonAdded);
		json.put("removed", new JSONArray());
		
		return json;
	}
	
	// insists that an assay was applied to the database, and returns the assayID
	private long assertSubmittedDirectly(JSONObject result)
	{
		assertTrue(result.getBoolean("success"));
		assertFalse(result.has("holdingID"));
		long assayID = result.getLong("assayID");
		assertThat(assayID, greaterThan(0L));
		assertNotNull(store.assay().getAssay(assayID));
		return assayID;
	}
	
	// insists that an assay was applied to the holding back, and returns the holdingID
	private long assertSubmittedHolding(JSONObject result)
	{
		assertTrue(result.getBoolean("success"));
		assertFalse(result.has("assayID"));
		long holdingID = result.getLong("holdingID");
		assertThat(holdingID, greaterThan(0L));
		assertNotNull(store.holding().getHolding(holdingID));
		return holdingID;
	}
}

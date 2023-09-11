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
import com.cdd.bae.rest.RESTBaseServlet.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.*;

import org.apache.http.*;
import org.json.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

/*
	Test for RequestProvisional REST API.
*/

public class RequestProvisionalTest extends EndpointEmulator
{
	private static final String PROVISIONAL_BASE_URI = "http://www.bioassayontology.org/bao_provisional#";
	private InitParams.Provisional provisional;
	private FauxMongo mongo;
	private DataStore store;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		TestUtilities.ensureOntologies();
		mongo = FauxMongo.getInstance("/testData/db/basic");
		store = mongo.getDataStore();

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(store);
		Common.setProvCache(ProvisionalCache.loaded());

		provisional = mock(InitParams.Provisional.class);
		provisional.baseURI = PROVISIONAL_BASE_URI;
		TestConfiguration.setProvisionals(provisional);

		restService = new RequestProvisional();
		restService.logger = TestUtilities.mockLogger();
		restService = Mockito.spy(restService);
	}
	
	// ------------ mockito-based tests ------------

	@Test
	public void testPermissionOk() throws IOException
	{
		doReturn(TestUtilities.mockSessionCurator()).when(restService).getSession(any());

		MockRESTUtilities.MockJSONResponse response = doPost(preparePayload());
		JSONObject json = response.getContentAsJSON();
		assertStatus(json, true, HttpStatus.SC_OK);
	}

	@Test
	public void testPermissionNotOk() throws IOException
	{
		doReturn(TestUtilities.mockSession()).when(restService).getSession(any());

		MockRESTUtilities.MockJSONResponse response = doPost(preparePayload());
		JSONObject json = response.getContentAsJSON();
		assertStatus(json, false, Status.NOLOGIN);
	}

	@Test
	public void testNewProvisionalId() throws IOException
	{
		// # terms before provisional term is added
		int nterms0 = Common.getProvCache().numTerms();
		
		doReturn(TestUtilities.mockSessionCurator()).when(restService).getSession(any());

		MockRESTUtilities.MockJSONResponse response = doPost(preparePayload());

		// # terms after provisional term is added
		int nterms1 = Common.getProvCache().numTerms();
		assertEquals(nterms0 + 1, nterms1);

		JSONObject json = response.getContentAsJSON();
		assertStatus(json, true, HttpStatus.SC_OK);
		assertEquals(1000, json.getLong("provisionalID"));
	}
	
	@Test
	public void testNextProvisionalURI()
	{
		String nextURI = RequestProvisional.nextProvisionalURI();
		assertThat(nextURI, is(PROVISIONAL_BASE_URI + "0001000"));

		nextURI = RequestProvisional.nextProvisionalURI();
		assertThat(nextURI, is(PROVISIONAL_BASE_URI + "0001001"));
	}

	@Test
	public void testParentNotFound() throws IOException
	{
		doReturn(TestUtilities.mockSessionCurator()).when(restService).getSession(any());

		JSONObject payload = preparePayload();
		payload.put("parentURI", "unrecognized_parent_uri"); // if it is found, then rename it here 

		MockRESTUtilities.MockJSONResponse response = doPost(payload);
		assertFalse(response.getContentAsJSON().getBoolean("success"));
	}

	@Test
	public void testNullBaseURL() throws IOException
	{
		doReturn(TestUtilities.mockSessionCurator()).when(restService).getSession(any());

		provisional.baseURI = null;

		MockRESTUtilities.MockJSONResponse response = doPost(preparePayload());
		verify(response.getResponse()).setStatus(RESTException.HTTPStatus.BAD_REQUEST.code());
	}
	
	// ------------ faux-mongo tests ------------

	@Test
	public void testAddBranch() throws RESTException, ConfigurationException, IOException
	{
		final String ROOT_URI = ModelSchema.expandPrefix("bao:BAO_0000008"); // add to [bioassay type]
		final String LABEL1 = "thing1", LABEL2 = "thing2", DESCR = "Description", EXPLAN = "Explanation";
		Session session = TestUtilities.mockSessionCurator();
		
		// add first term, verify that it's in place
		JSONObject request1 = new JSONObject();
		request1.put("parentURI", ROOT_URI);
		request1.put("label", LABEL1);
		request1.put("description", DESCR);
		request1.put("explanation", EXPLAN);
		JSONObject result1 = new RequestProvisional().processRequest(request1, session);
		assertTrue(result1.getBoolean("success"));
		long provisionalID1 = result1.getLong("provisionalID");
		assertThat(provisionalID1, greaterThan(0L));
		String provURI1 = result1.getString("uri");
		assertTrue(Util.notBlank(provURI1));

		// make sure it's in the database
		DataObject.Provisional prov = store.provisional().getProvisional(provisionalID1);
		assertNotNull(prov);
		assertEquals(ROOT_URI, prov.parentURI);
		assertEquals(LABEL1, prov.label);
		
		// try adding the first term again, insist that it fails (because it's a literal duplicate)
		JSONObject result1Fail = new RequestProvisional().processRequest(request1, session);
		assertFalse(result1Fail.getBoolean("success"));
		
		// add the second term as a child of the first one
		JSONObject request2 = new JSONObject();
		request2.put("parentURI", provURI1);
		request2.put("label", LABEL2);
		request2.put("description", DESCR);
		request2.put("explanation", EXPLAN);
		JSONObject result2 = new RequestProvisional().processRequest(request2, session);
		assertTrue(result2.getBoolean("success"));
		long provisionalID2 = result2.getLong("provisionalID");
		assertThat(provisionalID2, greaterThan(0L));
		String provURI2 = result2.getString("uri");
		assertTrue(Util.notBlank(provURI2));
		
		// make sure database specifics match
		prov = store.provisional().getProvisional(provisionalID2);
		assertNotNull(prov);
		assertEquals(provURI1, prov.parentURI);
		assertEquals(LABEL2, prov.label);		
		
		// modify the first term
		final String FLUFF = "z";
		JSONObject request3 = new JSONObject();
		request3.put("provisionalID", provisionalID1);
		request3.put("label", LABEL1 + FLUFF);
		request3.put("description", DESCR + FLUFF);
		request3.put("explanation", EXPLAN + FLUFF);
		JSONObject result3 = new RequestProvisional().processRequest(request3, session);
		assertTrue(result3.getBoolean("success"));
		
		// make sure database has been modified accordingly
		prov = store.provisional().getProvisional(provisionalID1);
		assertEquals(LABEL1 + FLUFF, prov.label);
		assertEquals(DESCR + FLUFF, prov.description);
		assertEquals(EXPLAN + FLUFF, prov.explanation);

		// TODO: try it again with a different user/low status user and require failure
		// TODO: make sure the actual schema hierarchy got updated as well
		
		// expensive but necessary because scrubbing the DB puts the tree out of sync
		mongo.restoreContent();
		//TestConfiguration.restoreSchemaVocab();
	}
	
	@Test
	public void testModify() throws RESTException, ConfigurationException, IOException
	{
		Session session = TestUtilities.mockSessionCurator();
		
		int dbSize = store.provisional().countProvisionals();
		
		// decide where to stick it
		Schema schema = Common.getSchemaCAT();
		Schema.Assignment assn = null;
		for (Schema.Assignment look : schema.getRoot().flattenedAssignments())
			if (look.suggestions == Schema.Suggestions.FULL) {assn = look; break;}
		SchemaTree tree = Common.obtainTree(schema, assn);
		int treeSize = tree.getTree().size();
		SchemaTree.Node parent = tree.getFlat()[treeSize - 1];
		
		// add the new term
		final String LABEL1 = "foo", DESCR1 = "Ring-a-ring o' roses", EXPLAN1 = "A tishoo a tishoo";
		final String LABEL2 = "bar", DESCR2 = "A pocket full of posies", EXPLAN2 = "We all fall down";
		final DataObject.ProvisionalRole ROLE1 = DataObject.ProvisionalRole.PRIVATE, ROLE2 = DataObject.ProvisionalRole.PUBLIC;
		JSONObject request = new JSONObject();
		request.put("parentURI", parent.uri);
		request.put("label", LABEL1);
		request.put("description", DESCR1);
		request.put("explanation", EXPLAN1);
		request.put("role", ROLE1.toString().toLowerCase());
		JSONObject result = new RequestProvisional().processRequest(request, session);
		assertTrue(result.getBoolean("success"));
		long provisionalID = result.getLong("provisionalID");
		assertThat(provisionalID, greaterThan(0L));
		String provURI = result.getString("uri");
		assertTrue(Util.notBlank(provURI));
		
		// enforce internal datastructure consistency
		assertEquals(dbSize + 1, store.provisional().countProvisionals());
		tree = Common.obtainTree(schema, assn);
		assertEquals(treeSize + 1, tree.getTree().size());
		assertEquals(treeSize + 1, tree.getFlat().length);
		assertEquals(treeSize + 1, tree.getList().length);
		SchemaTree.Node node = tree.getNode(provURI);
		assertNotNull(node);
		assertEquals(LABEL1, node.label);
		assertEquals(DESCR1, node.descr);
		
		// request a change
		request = new JSONObject();
		request.put("provisionalID", provisionalID);
		request.put("label", LABEL2);
		request.put("description", DESCR2);
		request.put("explanation", EXPLAN2);		
		request.put("role", ROLE2.toString().toLowerCase());
		result = new RequestProvisional().processRequest(request, session);
		assertTrue(result.getBoolean("success"));

		// make sure DB reflects changes correctly
		assertEquals(dbSize + 1, store.provisional().countProvisionals());		
		DataObject.Provisional prov = store.provisional().getProvisional(provisionalID);
		assertNotNull(prov);
		assertEquals(prov.uri, provURI);
		assertEquals(prov.label, LABEL2);
		assertEquals(prov.description, DESCR2);
		assertEquals(prov.explanation, EXPLAN2);
		assertEquals(prov.role, ROLE2);

		// check the tree
		tree = Common.obtainTree(schema, assn);
		assertEquals(treeSize + 1, tree.getTree().size());
		assertEquals(treeSize + 1, tree.getFlat().length);
		assertEquals(treeSize + 1, tree.getList().length);
		node = tree.getNode(provURI);
		assertNotNull(node);
		assertEquals(node.label, LABEL2);
		assertEquals(node.descr, DESCR2);

		// expensive but necessary because scrubbing the DB puts the tree out of sync
		mongo.restoreContent();
		//TestConfiguration.restoreSchemaVocab();
	}

	// ------------ private methods ------------

	private JSONObject preparePayload()
	{
		JSONObject payload = new JSONObject();
		payload.put("parentURI", "http://www.bioassayontology.org/bao#BAO_0000551"); // from CAT: organism.organism
		payload.put("label", "E. plasma hyper");
		payload.put("description", "E. plasma culture with reproductive rate of 30ms");
		payload.put("explanation", "Hypergrowth E. plasma culture");
		payload.put("proposerID", "Default"); // should come from session
		return payload;
	}
}

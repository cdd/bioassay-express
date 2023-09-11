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
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for DeleteProvisionalTerm REST API.
*/

public class DeleteProvisionalTermTest extends EndpointEmulator
{
	private static DataStore store;
	private static FauxMongo mongo;
	private static boolean restoreSchemaVocab;

	@BeforeAll
	public static void setUp()
	{
		mongo = FauxMongo.getInstance("/testData/db/basic");
		store = mongo.getDataStore();
	}

	@BeforeEach
	public void prepare() throws ConfigurationException
	{
		restoreSchemaVocab = false;
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(store);

		setRestService(new DeleteProvisionalTerm());
	}
	
	@AfterEach
	public void restore() throws IOException, ConfigurationException
	{
		// expensive but necessary because scrubbing the DB puts the tree out of sync
		mongo.restoreContent();
		//if (restoreSchemaVocab) TestConfiguration.restoreSchemaVocab();
	}

	@Test
	public void testHasPermission()
	{
		assertThat(restService.hasPermission(TestUtilities.mockNoSession()), is(false));
		assertThat(restService.hasPermission(TestUtilities.mockSessionBlocked()), is(false));
		assertThat(restService.hasPermission(TestUtilities.mockSession()), is(false));
		assertThat(restService.hasPermission(TestUtilities.mockSessionCurator()), is(true));
		assertThat(restService.hasPermission(TestUtilities.mockSessionAdmin()), is(true));
	}

	@Test
	public void testAddDelete() throws Exception
	{
		restoreSchemaVocab = true;
		
		final String ROOT_URI = ModelSchema.expandPrefix("bao:BAO_0000008"); // add to [bioassay type]
		final String LABEL1 = "thing1", LABEL2 = "thing2";
		Session sessionPleb = TestUtilities.mockSession();
		Session sessionCuratorA = TestUtilities.mockSessionCurator();
		Session sessionCuratorB = TestUtilities.mockSessionCurator();
		sessionCuratorB.curatorID += "_other";
		Session sessionAdmin = TestUtilities.mockSessionTrusted();

		// add two terms (directly) that are dependent on each other, and both attributed to curatorA

		DataObject.Provisional prov1 = new DataObject.Provisional();
		prov1.parentURI = ROOT_URI;
		prov1.label = LABEL1;
		prov1.uri = RequestProvisional.nextProvisionalURI();
		prov1.proposerID = sessionCuratorA.curatorID;
		store.provisional().updateProvisional(prov1);
		assertThat(prov1.provisionalID, greaterThan(0L));

		DataObject.Provisional prov2 = new DataObject.Provisional();
		prov2.parentURI = prov1.uri;
		prov2.label = LABEL2;
		prov2.uri = RequestProvisional.nextProvisionalURI();
		prov2.proposerID = sessionCuratorA.curatorID;
		store.provisional().updateProvisional(prov2);
		assertThat(prov2.provisionalID, greaterThan(0L));
		
		Provisional[] provisionals = store.provisional().fetchAllTerms();
		Map<String, Integer> usageCount = GetTermRequests.usageAssays(provisionals, store);
		assertUsageCount(usageCount, prov1, 0, prov2, 0);
		usageCount = GetTermRequests.usageHoldings(store.provisional().fetchAllTerms(), store);
		assertUsageCount(usageCount, prov1, 0, prov2, 0);

		// try deleting with insufficient permissions
		assertFalse(new DeleteProvisionalTerm().hasPermission(sessionPleb));

		// try deleting with the wrong user
		JSONObject request = new JSONObject();
		request.put("provisionalIDList", new long[]{prov2.provisionalID});
		JSONObject result = new DeleteProvisionalTerm().processRequest(request, sessionCuratorB);
		assertEquals(0, result.getJSONArray("deleted").length());
		assertEquals(1, result.getJSONArray("notDeleted").length());
		assertReason(result, prov2.provisionalID, "insufficient permission");

		// try deleting #1 which is forbidden because not a leaf node
		request = new JSONObject();
		request.put("provisionalIDList", new long[]{prov1.provisionalID});
		result = new DeleteProvisionalTerm().processRequest(request, sessionCuratorA);
		assertEquals(0, result.getJSONArray("deleted").length());
		assertEquals(1, result.getJSONArray("notDeleted").length());
		assertReason(result, prov1.provisionalID, "not leaf node");

		// try deleting #2 which is allowed because it's a leaf node
		request = new JSONObject();
		request.put("provisionalIDList", new long[]{prov2.provisionalID});
		result = new DeleteProvisionalTerm().processRequest(request, sessionCuratorA);
		assertTrue(Arrays.equals(new long[]{prov2.provisionalID}, result.getJSONArray("deleted").toLongArray()));
		assertEquals(0, result.getJSONArray("notDeleted").length());
		assertNull(store.provisional().getProvisional(prov2.provisionalID)); // make sure it's gone

		usageCount = GetTermRequests.usageAssays(store.provisional().fetchAllTerms(), store);
		assertUsageCount(usageCount, prov1, 0);
		usageCount = GetTermRequests.usageHoldings(store.provisional().fetchAllTerms(), store);
		assertUsageCount(usageCount, prov1, 0);

		// now delete #1 (as admin)
		request = new JSONObject();
		request.put("provisionalIDList", new long[]{prov1.provisionalID});
		result = new DeleteProvisionalTerm().processRequest(request, sessionAdmin);
		assertTrue(Arrays.equals(new long[]{prov1.provisionalID}, result.getJSONArray("deleted").toLongArray()));
		assertEquals(0, result.getJSONArray("notDeleted").length());
		assertNull(store.provisional().getProvisional(prov1.provisionalID)); // make sure it's gone

		usageCount = GetTermRequests.usageAssays(store.provisional().fetchAllTerms(), store);
		assertThat(usageCount.keySet(), hasSize(0));

		// expensive but necessary because scrubbing the DB puts the tree out of sync
		restoreSchemaVocab = true;
	}

/* deprecated
	@Test
	public void testTreeSync() throws RESTException, IOException
	{
		restoreSchemaVocab = true;

		int dbSize = store.provisional().countProvisionals();
		// decide where to stick it
		Schema schema = Common.getSchemaCAT();
		Schema.Assignment assn = getSuitableAssignment(schema);
		SchemaTree tree = Common.obtainTree(schema, assn);
		int treeSize = tree.getTree().size();
		SchemaTree.Node parent = tree.getFlat()[treeSize - 1];

		assertTreeStructure(tree, treeSize);

		// add the new term
		JSONObject result = requestProvisional(parent);
		long provisionalID = result.getLong("provisionalID");
		String provURI = result.getString("uri");

		// enforce internal datastructure consistency
		assertEquals(dbSize + 1, store.provisional().countProvisionals());
		assertThat(store.provisional().getProvisional(provisionalID), not(nullValue()));
		tree = Common.obtainTree(schema, assn);
		assertTreeStructure(tree, treeSize + 1);
		assertThat(tree.getNode(provURI), not(nullValue()));

		// request a deletion
		deleteProvisional(provisionalID);

		// make sure DB reflects changes correctly
		assertEquals(dbSize, store.provisional().countProvisionals());
		assertThat(store.provisional().getProvisional(provisionalID), is(nullValue()));
		tree = Common.obtainTree(schema, assn);
		assertTreeStructure(tree, treeSize);
		assertThat(tree.getNode(provURI), is(nullValue()));

		// add tree of provisionals and remove it
		result = requestProvisional(parent);
		provisionalID = result.getLong("provisionalID");
		provURI = result.getString("uri");
		tree = Common.obtainTree(schema, assn);
		assertTreeStructure(tree, treeSize + 1);

		result = requestProvisional(tree.getNode(provURI));
		long provisionalID2 = result.getLong("provisionalID");
		tree = Common.obtainTree(schema, assn);
		assertTreeStructure(tree, treeSize + 2);

		deleteProvisional(provisionalID, provisionalID2);
		tree = Common.obtainTree(schema, assn);
		assertTreeStructure(tree, treeSize);
	}*/

	@Test
	public void testIsUsedByAssays_BlockAssay() throws RESTException
	{
		DataObject.Provisional prov = createProvisionalTerm("label1");
		assertThat(DeleteProvisionalTerm.isUsedByAssays(store, prov), is(false));
		
		useProvInAssay(prov);
		assertThat(DeleteProvisionalTerm.isUsedByAssays(store, prov), is(true));

		Map<String, Integer> usageCount = GetTermRequests.usageAssays(store.provisional().fetchAllTerms(), store);
		assertThat(usageCount.keySet(), hasSize(1));
		assertThat(usageCount.get(prov.uri), is(1));

		// try to delete the provisional term, and fail
		Session session = TestUtilities.mockSessionCurator();
		JSONObject request = new JSONObject();
		request.put("provisionalIDList", new long[]{prov.provisionalID});
		JSONObject result = new DeleteProvisionalTerm().processRequest(request, session);
		assertEquals(0, result.getJSONArray("deleted").length());
		assertEquals(1, result.getJSONArray("notDeleted").length());
		assertReason(result, prov.provisionalID, "term is used");
	}

	@Test
	public void testIsUsedByHolding_BlockHolding() throws RESTException
	{
		DataObject.Provisional prov1 = createProvisionalTerm("label1");
		DataObject.Provisional prov2 = createProvisionalTerm("label2");
		assertThat(DeleteProvisionalTerm.isUsedByHolding(store, prov1), is(false));
		assertThat(DeleteProvisionalTerm.isUsedByHolding(store, prov2), is(false));
		
		Map<String, Integer> usageCount = GetTermRequests.usageAssays(store.provisional().fetchAllTerms(), store);
		assertUsageCount(usageCount, prov1, 0, prov2, 0);
		usageCount = GetTermRequests.usageHoldings(store.provisional().fetchAllTerms(), store);
		assertUsageCount(usageCount, prov1, 0, prov2, 0);

		useProvInHolding(prov1, prov2);
		assertThat(DeleteProvisionalTerm.isUsedByHolding(store, prov1), is(true));
		assertThat(DeleteProvisionalTerm.isUsedByHolding(store, prov2), is(true));

		usageCount = GetTermRequests.usageAssays(store.provisional().fetchAllTerms(), store);
		assertUsageCount(usageCount, prov1, 0, prov2, 0);
		usageCount = GetTermRequests.usageHoldings(store.provisional().fetchAllTerms(), store);
		assertUsageCount(usageCount, prov1, 1, prov2, 1);

		// try to delete the provisional term, and fail
		Session session = TestUtilities.mockSessionCurator();
		JSONObject request = new JSONObject();
		request.put("provisionalIDList", new long[]{prov1.provisionalID});
		JSONObject result = new DeleteProvisionalTerm().processRequest(request, session);
		assertEquals(0, result.getJSONArray("deleted").length());
		assertEquals(1, result.getJSONArray("notDeleted").length());
		assertReason(result, prov1.provisionalID, "term is used in holding bay");
	}
	
//	private void memoryUsage()
//	{
//		long usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
//		long freeMemory = Runtime.getRuntime().freeMemory() / (1024 * 1024);
//		System.out.println("Memory usage: " + usedMemory + " Free memory: " + freeMemory);
//	}

	// ------------ private methods ------------
	
	private void assertUsageCount(Map<String, Integer> usageCount, Provisional prov1, int count1, Provisional prov2, int count2)
	{
		assertThat(usageCount.keySet(), hasSize(2));
		assertThat(usageCount.get(prov1.uri), is(count1));
		assertThat(usageCount.get(prov2.uri), is(count2));
	}

	private void assertUsageCount(Map<String, Integer> usageCount, Provisional prov1, int count1)
	{
		assertThat(usageCount.keySet(), hasSize(1));
		assertThat(usageCount.get(prov1.uri), is(count1));
	}

	private DataObject.Provisional createProvisionalTerm(String label)
	{
		restoreSchemaVocab = true;

		final String ROOT_URI = ModelSchema.expandPrefix("bao:BAO_0000008"); // add to [bioassay type]
		final String LABEL1 = label;
		Session session = TestUtilities.mockSessionCurator();
		DataObject.Provisional prov = new DataObject.Provisional();
		prov.parentURI = ROOT_URI;
		prov.label = LABEL1;
		prov.uri = RequestProvisional.nextProvisionalURI();
		prov.proposerID = session.curatorID;
		store.provisional().updateProvisional(prov);
		assertThat(prov.provisionalID, greaterThan(0L));
		return prov;
	}

	private void useProvInAssay(DataObject.Provisional prov)
	{
		// add an assay that uses the new term
		DataObject.Assay assay = new DataObject.Assay();
		assay.uniqueID = "test:123";
		assay.text = "test";
		assay.schemaURI = Common.getSchemaCAT().getSchemaPrefix();
		DataObject.Annotation annot = new DataObject.Annotation(ModelSchema.expandPrefix("bao:nothing"), prov.uri);
		assay.annotations = new DataObject.Annotation[]{annot};
		assay.textLabels = new DataObject.TextLabel[0];
		assay.curatorID = "test";
		store.assay().submitAssay(assay);
		assertThat(assay.assayID, greaterThan(0L));
	}

	private void useProvInHolding(DataObject.Provisional prov1, DataObject.Provisional prov2)
	{
		// add an assay that uses the new term
		DataObject.Holding holding = new DataObject.Holding();
		holding.schemaURI = Common.getSchemaCAT().getSchemaPrefix();
		DataObject.Annotation annot = new DataObject.Annotation(ModelSchema.expandPrefix("bao:nothing"), prov1.uri);
		holding.annotsAdded = new DataObject.Annotation[]{annot};
		annot = new DataObject.Annotation(ModelSchema.expandPrefix("bao:nothing"), prov2.uri);
		holding.annotsRemoved = new DataObject.Annotation[]{annot};
		holding.curatorID = "test";
		store.holding().depositHolding(holding);
		assertThat(holding.holdingID, greaterThan(0L));
	}

	private void assertReason(JSONObject response, long provisionalID, String expected)
	{
		JSONArray notDeleted = response.getJSONArray("notDeleted");
		for (int n = 0; n < notDeleted.length(); n++)
		{
			JSONObject reason = notDeleted.getJSONObject(n);
			long provID = reason.getLong("ID");
			if (provID != provisionalID) continue;
			assertThat(reason.getString("reason"), is(expected));
			return;
		}
		assertFalse(true, "Provisional " + provisionalID + " not deleted");
	}

	private Schema.Assignment getSuitableAssignment(Schema schema)
	{
		Schema.Assignment assn = null;
		for (Schema.Assignment look : schema.getRoot().flattenedAssignments())
			if (look.suggestions == Schema.Suggestions.FULL) {assn = look; break;}
		return assn;
	}

	private JSONObject requestProvisional(SchemaTree.Node parent) throws RESTException
	{
		Session session = TestUtilities.mockSessionCurator();

		JSONObject request = new JSONObject();
		request.put("parentURI", parent.uri);
		request.put("label", "Baa baa");
		request.put("description", "Black sheep");
		request.put("explanation", "Have you any wool");
		request.put("role", DataObject.ProvisionalRole.PRIVATE.toString().toLowerCase());
		JSONObject result = new RequestProvisional().processRequest(request, session);

		assertTrue(result.getBoolean("success"));
		long provisionalID = result.getLong("provisionalID");
		assertThat(provisionalID, greaterThan(0L));
		assertThat(result.getString("uri"), not(blankString()));
		assertThat(store.provisional().getProvisional(provisionalID), not(nullValue()));
		return result;
	}

	private JSONObject deleteProvisional(long... provisionalIDs) throws RESTException
	{
		JSONObject request = new JSONObject();
		request.put("provisionalIDList", provisionalIDs);
		JSONObject result = new DeleteProvisionalTerm().processRequest(request, TestUtilities.mockSessionCurator());
		for (long provisionalID : provisionalIDs)
			assertThat(store.provisional().getProvisional(provisionalID), is(nullValue()));
		assertTrue(result.getBoolean("success"));
		assertThat(ArrayUtils.toObject(result.getJSONArray("deleted").toLongArray()),
				arrayContainingInAnyOrder(ArrayUtils.toObject(provisionalIDs)));
		return result;
	}

	private void assertTreeStructure(SchemaTree tree, int expectedSize)
	{
		assertThat(tree.getTree().size(), is(expectedSize));
		assertThat(tree.getFlat().length, is(expectedSize));
		assertThat(tree.getList().length, is(expectedSize));

		// assert that the child count is correct
		for (SchemaTree.Node node : tree.getFlat())
			assertThat(node.childCount, is(countNodes(node) - 1));
	}

	// return the number of nodes in branch starting at parent (incl. parent)
	private static int countNodes(SchemaTree.Node parent)
	{
		int count = 1;
		for (SchemaTree.Node child : parent.children) count += countNodes(child);
		return count;
	}
}

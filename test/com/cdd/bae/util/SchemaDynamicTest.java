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

package com.cdd.bae.util;

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bao.template.*;
import com.cdd.bao.template.Schema.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.junit.jupiter.api.*;

public class SchemaDynamicTest extends TestBaseClass
{
	static final String[] BRANCH_TEMPLATES = 
	{
		"control_details.json",
		"screened_entity_details.json",
		"assay_components.json",
		"solvent_concentration.json"
	};
	static final String SCHEMA_BRANCH1 = "http://www.bioassayontology.org/bas/ControlDetails#";
	static final String SCHEMA_BRANCH2 = "http://www.bioassayontology.org/bas/ScreenedEntityDetails#";
	static final String SCHEMA_BRANCH3 = "http://www.bioassayontology.org/bas/AssayComponents#";
	static final String SCHEMA_BRANCH4 = "http://www.bioassayontology.org/bas/SolventConcentration#";
	
	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		Configuration configuration = TestConfiguration.getConfiguration(false);
		
		TestUtilities.ensureOntologies();
		Common.setConfiguration(configuration);
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Common.setDataStore(mongo.getDataStore());
		Common.setProvCache(ProvisionalCache.loaded());
	}
	
	@AfterEach
	public void tearDown() throws ConfigurationException
	{
		//TestConfiguration.restoreTemplate();
	}

	@Test
	public void testGrafting()
	{
		assertTrue(Common.getBranchSchemata().length >= 4, "Branch templates not found in schema"); // branch templates go in a separate bucket
		Schema branchSchema1 = Common.getSchema(SCHEMA_BRANCH1), branchSchema2 = Common.getSchema(SCHEMA_BRANCH2);
		assertNotNull(branchSchema1);
		assertNotNull(branchSchema2);
		
		Schema baselineSchema = Common.getSchemaCAT();
		
		DataObject.SchemaBranch branch1 = new DataObject.SchemaBranch(SCHEMA_BRANCH1, null);
		DataObject.SchemaBranch branch2 = new DataObject.SchemaBranch(SCHEMA_BRANCH2, null);
		
		SchemaDynamic graft = new SchemaDynamic(baselineSchema, new DataObject.SchemaBranch[]{branch1, branch2}, null);
		Schema graftedSchema = graft.getResult();
		
		assertTrue(graft.isComposite());

		// each of the branches has one group at its top level, so the grafted version must have 2 more
		assertEquals(baselineSchema.getRoot().subGroups.size() + 2, graftedSchema.getRoot().subGroups.size());
		
		Schema.Group branchGroup = branchSchema1.getRoot().subGroups.get(0);
		Schema.Assignment branchAssn = branchGroup.assignments.get(0);
		String[] branchNest = ArrayUtils.insert(0, branchGroup.groupNest(), branchGroup.groupURI);
		SchemaDynamic.SubTemplate subt = graft.relativeAssignment(branchAssn.propURI, branchNest);
		assertNotNull(subt);
		assertTrue(branchSchema1.getSchemaPrefix().equals(subt.schema.getSchemaPrefix())); // must belong to the original schema
	}
	
	@Test
	public void testSubGrafting()
	{
		Schema branchSchema3 = Common.getSchema(SCHEMA_BRANCH3), branchSchema4 = Common.getSchema(SCHEMA_BRANCH4);
		assertNotNull(branchSchema3, "Branch not found, update your schema file");
		assertNotNull(branchSchema4, "Branch not found, update your schema file");
		
		Schema baselineSchema = Common.getSchemaCAT();
		
		Schema.Group group3 = branchSchema3.getRoot().subGroups.get(0);
		Schema.Group group4 = branchSchema4.getRoot().subGroups.get(0);
		String[] groupNest3 = new String[]{group3.groupURI};
		String[] groupNest4 = ArrayUtils.insert(0, groupNest3, group4.groupURI);
		
		// branch4 is inserted as a child of branch3
		DataObject.SchemaBranch branch3 = new DataObject.SchemaBranch(SCHEMA_BRANCH3, null);
		DataObject.SchemaBranch branch4 = new DataObject.SchemaBranch(SCHEMA_BRANCH4, groupNest3);
		
		SchemaDynamic graft = new SchemaDynamic(baselineSchema, new DataObject.SchemaBranch[]{branch3, branch4}, null);
		Schema graftedSchema = graft.getResult();
		
		// adding a child and a child, so group count is 1 higher
		assertEquals(baselineSchema.getRoot().subGroups.size() + 1, graftedSchema.getRoot().subGroups.size());
		
		// make sure both got grafted at the right position
		assertTrue(graftedSchema.findGroupByNest(groupNest3).groupURI.equals(group3.groupURI));
		assertTrue(graftedSchema.findGroupByNest(groupNest4).groupURI.equals(group4.groupURI));
	}
	
	@Test
	public void testCloning()
	{
		final String URI_PROP1 = "http://something/prop1";
		final String URI_PROP2 = "http://something/prop2";
		final String URI_GROUP = "http://something/group";
		
		Schema schema = new Schema();
		Schema.Group root = schema.getRoot();
		root.assignments.add(new Assignment(root, "assn1", URI_PROP1));
		Schema.Group group = new Group(root, "group", URI_GROUP);
		root.subGroups.add(group);
		group.assignments.add(new Assignment(group, "assn2", URI_PROP2));
		
		// sanity check: baseline
		Assignment[] thenList = root.flattenedAssignments();
		assertEquals(2, thenList.length, "Initial list of assignments wrong");
		assertEquals(URI_PROP1, thenList[0].propURI);
		assertEquals(URI_PROP2, thenList[1].propURI);
		assertArrayEquals(new String[0], thenList[0].groupNest());
		assertArrayEquals(new String[]{URI_GROUP}, thenList[1].groupNest());
		
		// apply the duplication and make sure it worked correctly
		DataObject.SchemaDuplication dupl = new DataObject.SchemaDuplication(2, new String[]{URI_GROUP});
		schema = new SchemaDynamic(schema, null, new DataObject.SchemaDuplication[]{dupl}).getResult();
		
		Assignment[] nowList = schema.getRoot().flattenedAssignments();
		assertEquals(3, nowList.length, "Cloning failed to achieve correct count");
		assertEquals(URI_PROP1, nowList[0].propURI);
		assertEquals(URI_PROP2, nowList[1].propURI);
		assertEquals(URI_PROP2, nowList[2].propURI);
		assertArrayEquals(new String[0], nowList[0].groupNest());
		assertArrayEquals(new String[]{URI_GROUP + "@1"}, nowList[1].groupNest());
		assertArrayEquals(new String[]{URI_GROUP + "@2"}, nowList[2].groupNest());
	}
	
	@Test
	public void testSubDuplicate()
	{
		// create a composite template that has {assay components} -> {solvent} x 2
	
		Schema schemaCommon = Common.getSchemaCAT();
		Schema schemaComponents = Common.getSchema(SCHEMA_BRANCH3);
		Schema schemaSolvent = Common.getSchema(SCHEMA_BRANCH4);
		assertNotNull(schemaCommon);
		assertNotNull(schemaComponents);
		assertNotNull(schemaSolvent);
		
		final String GROUPNEST1 = "http://www.bioassayontology.org/bao#BAX_0000034";
		final String GROUPNEST2 = "http://www.bioassayontology.org/bao#BAX_0000036";

		DataObject.SchemaBranch branch1 = new DataObject.SchemaBranch(schemaComponents.getSchemaPrefix(), null);
		DataObject.SchemaBranch branch2 = new DataObject.SchemaBranch(schemaSolvent.getSchemaPrefix(), new String[]{GROUPNEST1});
		DataObject.SchemaDuplication dupl = new DataObject.SchemaDuplication(2, new String[]{GROUPNEST2, GROUPNEST1});
		SchemaDynamic graft = new SchemaDynamic(schemaCommon, 
											    new DataObject.SchemaBranch[]{branch1, branch2}, 
											    new DataObject.SchemaDuplication[]{dupl});
		Schema schema = graft.getResult();

		// make sure the expected groupNest entities are encountered
		Set<String> expected = new HashSet<>();
		expected.add("http://www.bioassayontology.org/bao#BAX_0000034");
		expected.add("http://www.bioassayontology.org/bao#BAX_0000036@1::http://www.bioassayontology.org/bao#BAX_0000034");
		expected.add("http://www.bioassayontology.org/bao#BAX_0000036@2::http://www.bioassayontology.org/bao#BAX_0000034");
		for (Schema.Assignment assn : schema.getRoot().flattenedAssignments())
		{
			String key = String.join("::", assn.groupNest());
			expected.remove(key);
		}
		assertTrue(expected.isEmpty(), "Missing groupNest entries: " + expected.toString());	
	
		final String LOOK_PROPURI = "http://www.bioassayontology.org/bao#BAX_0000062";
		final String[] LOOK_GROUPNEST1 = {"http://www.bioassayontology.org/bao#BAX_0000036@1", "http://www.bioassayontology.org/bao#BAX_0000034"};
		final String[] LOOK_GROUPNEST2 = {"http://www.bioassayontology.org/bao#BAX_0000036@2", "http://www.bioassayontology.org/bao#BAX_0000034"};

		// make sure the first instance is traced back to its original branch (this is the one that doesn't get cloned explicitly)
		SchemaDynamic.SubTemplate subt1 = graft.relativeAssignment(LOOK_PROPURI, LOOK_GROUPNEST1);
		assertNotNull(subt1, "Failed to find expected duplicate-schema @1.");
		assertEquals(subt1.schema.getSchemaPrefix(), schemaSolvent.getSchemaPrefix());
		assertArrayEquals(subt1.groupNest, new String[]{GROUPNEST2});
		
		// make sure the second instance is traced back to its original branch (this one does get cloned explicitly)
		SchemaDynamic.SubTemplate subt2 = graft.relativeAssignment(LOOK_PROPURI, LOOK_GROUPNEST2);
		assertNotNull(subt2, "Failed to find expected duplicate-schema @2.");
		assertEquals(subt2.schema.getSchemaPrefix(), schemaSolvent.getSchemaPrefix());
		assertArrayEquals(subt2.groupNest, new String[]{GROUPNEST2});
	}	
}



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
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.*;

/*
	Test for com.cdd.bae.util.CompositeTree - use with a fixed snapshot version of the common assay template
 */

public class CompositeTreeTest2
{
	private static FauxOntologyTree onto;

	@BeforeAll
	public static void init() throws ConfigurationException, IOException
	{
		TestUtilities.ensureOntologies();
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
		Common.setProvCache(ProvisionalCache.loaded());
	}

	@Test
	public void testHierarchies()
	{
		checkoutAssignment("assay title", "http://www.bioassayontology.org/bao#BAO_0002853", null, 0);
		checkoutAssignment("bioassay type", "http://www.bioassayontology.org/bao#BAO_0002854", null, 7);
		checkoutAssignment("bioassay", "http://www.bioassayontology.org/bao#BAO_0002855", null, 376);
		checkoutAssignment("assay format", "http://www.bioassayontology.org/bao#BAO_0000205", null, 24);
		checkoutAssignment("assay design method", "http://www.bioassayontology.org/bao#BAO_0095009", null, 121);
		checkoutAssignment("assay supporting method", "http://www.bioassayontology.org/bao#BAO_0095010", null, 186);
		checkoutAssignment("assay cell line", "http://www.bioassayontology.org/bao#BAO_0002800", null, 41623); //41624
		checkoutAssignment("organism", "http://www.bioassayontology.org/bao#BAO_0002921", null, 1724);
		checkoutAssignment("biological process", "http://www.bioassayontology.org/bao#BAO_0002009", null, 38240);
		checkoutAssignment("target", "http://www.bioassayontology.org/bao#BAO_0000211", null, 218437);
		checkoutAssignment("applies to disease", "http://www.bioassayontology.org/bao#BAO_0002848", null, 4124);
		checkoutAssignment("assay mode of action", "http://www.bioassayontology.org/bao#BAO_0000196", null, 43);
		checkoutAssignment("result", "http://www.bioassayontology.org/bao#BAO_0000208", null, 188);
		checkoutAssignment("screening campaign stage", "http://www.bioassayontology.org/bao#BAO_0000210", null, 25);
		checkoutAssignment("assay footprint", "http://www.bioassayontology.org/bao#BAO_0002867", null, 37);
		checkoutAssignment("assay kit", "http://www.bioassayontology.org/bao#BAO_0002663", null, 134);
		checkoutAssignment("physical detection method", "http://www.bioassayontology.org/bao#BAO_0000207", null, 71);
		checkoutAssignment("detection instrument", "http://www.bioassayontology.org/bao#BAO_0002865", null, 146);
		checkoutAssignment("perturbagen type", "http://www.bioassayontology.org/bao#BAO_0000185", null, 22);
		checkoutAssignment("protein identity", "http://www.bioassayontology.org/bao#BAX_0000012", null, 9127);
		checkoutAssignment("gene identity", "http://www.bioassayontology.org/bao#BAX_0000011", null, 28931);
		checkoutAssignment("GO terms", "http://www.bioassayontology.org/bao#BAO_0003107", null, 42115);
		checkoutAssignment("assay sources", "http://www.bioassayontology.org/bao#BAO_0002852", null, 106);
		checkoutAssignment("related assays", "http://www.bioassayontology.org/bao#BAO_0000539", null, 0);
		checkoutAssignment("field", "http://www.bioassayontology.org/bao#BAX_0000015", "http://www.bioassayontology.org/bao#BAX_0000017", 0);
		checkoutAssignment("units", "http://www.bioassayontology.org/bao#BAO_0002874", "http://www.bioassayontology.org/bao#BAX_0000017", 321);
		checkoutAssignment("operator", "http://www.bioassayontology.org/bao#BAX_0000016", "http://www.bioassayontology.org/bao#BAX_0000017", 6);
		checkoutAssignment("threshold", "http://www.bioassayontology.org/bao#BAO_0002916", "http://www.bioassayontology.org/bao#BAX_0000017", 0);
	}

	// ------------ private methods ------------

	private void checkoutAssignment(String title, String propURI, String groupURI, int numNodes)
	{
		var schema = Common.getSchemaCAT();
		assertNotNull(schema);
		var assnList = groupURI == null ? schema.findAssignmentByProperty(propURI) : schema.findAssignmentByProperty(propURI, new String[]{groupURI});
		assertEquals(1, assnList.length);
		var assn = assnList[0];
		
		var tree = Common.obtainTree(schema, assn);
		assertNotNull(tree);
		
		if (tree.getFlat().length == numNodes) return;
		
		dumpAssn(assn);
		dumpTree(tree);
		throw new AssertionError("Schema nodes mismatch");
	}

	private void dumpAssn(Schema.Assignment assn)
	{
		Util.writeln("ASSIGNMENT: #values=" + assn.values.size() + " propURI=" + assn.propURI + " name=" + assn.name);
		for (var value : assn.values) Util.writeln("  <" + value.uri + "> spec=" + value.spec);
	}
	private void dumpTree(SchemaTree tree)
	{
		var flat = tree.getFlat();
		Util.writeln("TREE: #nodes=" + flat.length);
		for (int n = 0; n < flat.length; n++) 
		{
			Util.writeln("* ".repeat(flat[n].depth) + "<" + flat[n].uri + "> " + flat[n].label + " #child=" + flat[n].childCount);
			if (n >= 100) break; // that's enough
		}
	}
}

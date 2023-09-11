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

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.*;

/*
	Test for com.cdd.bae.util.CompositeTree - trivial use of a contrived faux tree
 */

public class CompositeTreeTest1
{
	private static FauxOntologyTree onto;

	@BeforeAll
	public static void init() throws IOException
	{
		onto = new FauxOntologyTree();
		Common.setProvCache(new ProvisionalCache());
	}

	@Test
	public void testSimpleRoot() throws IOException
	{
		var assn = new Schema.Assignment(null, "testname", "testuri");
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:01"), null, Schema.Specify.ITEM));
		
		var tree = new CompositeTree(onto, assn).compose();
		assertNotNull(tree);
		
		var flat = tree.getFlat();
		assertEquals(17, flat.length);
		assertEquals("http://www.bioassayontology.org/bat#01", flat[0].uri);
		assertEquals("root A", flat[0].label);

		int[] depths = new int[]{0, 1, 1, 2, 1, 1, 2, 2, 1, 1, 2, 3, 1, 2, 3, 4, 3};
		int[] childs = new int[]{16, 0, 1, 0, 0, 2, 0, 0, 0, 2, 1, 0, 4, 3, 1, 0, 0};

		for (int n = 0; n < 17; n++)
		{
			assertEquals(depths[n], flat[n].depth);
			assertEquals(childs[n], flat[n].childCount);
		}
	}
	
	@Test
	public void testMultiRoot() throws IOException
	{
		var assn = new Schema.Assignment(null, "testname", "testuri");
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:01"), null, Schema.Specify.ITEM));
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:18"), null, Schema.Specify.ITEM));
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:22"), null, Schema.Specify.ITEM));
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:23"), null, Schema.Specify.ITEM));

		var tree = new CompositeTree(onto, assn).compose();
		assertNotNull(tree);
	
		var flat = tree.getFlat();
		assertEquals(23, flat.length);
		assertEquals("http://www.bioassayontology.org/bat#01", flat[0].uri);
		assertEquals("root A", flat[0].label);
	}
	
	@Test
	public void testOneBranch() throws IOException
	{
		var assn = new Schema.Assignment(null, "testname", "testuri");
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:10"), null, Schema.Specify.ITEM));

		var tree = new CompositeTree(onto, assn).compose();
		assertNotNull(tree);
	
		var flat = tree.getFlat();
		assertEquals(3, flat.length);
		assertEquals("http://www.bioassayontology.org/bat#10", flat[0].uri);
		assertEquals("http://www.bioassayontology.org/bat#11", flat[1].uri);
		assertEquals("http://www.bioassayontology.org/bat#12", flat[2].uri);
	}
	
	@Test
	public void testSubBranches() throws IOException
	{
		var assn = new Schema.Assignment(null, "testname", "testuri");
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:02"), null, Schema.Specify.ITEM));
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:13"), null, Schema.Specify.ITEM));

		var tree = new CompositeTree(onto, assn).compose();
		assertNotNull(tree);
	
		var flat = tree.getFlat();
		assertEquals(7, flat.length);
		assertEquals("http://www.bioassayontology.org/bat#01", flat[0].uri);
		assertEquals("http://www.bioassayontology.org/bat#02", flat[1].uri);
		assertEquals("http://www.bioassayontology.org/bat#13", flat[2].uri);
	}
	
	@Test
	public void testDisjointBranches() throws IOException
	{
		var assn = new Schema.Assignment(null, "testname", "testuri");
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:06"), null, Schema.Specify.ITEM));
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:15"), null, Schema.Specify.ITEM));

		var tree = new CompositeTree(onto, assn).compose();
		assertNotNull(tree);

		var flat = tree.getFlat();
		assertEquals(8, flat.length);
		assertEquals("http://www.bioassayontology.org/bat#01", flat[0].uri);
		assertEquals("http://www.bioassayontology.org/bat#06", flat[1].uri);
		assertEquals("http://www.bioassayontology.org/bat#13", flat[4].uri);
	}
	
	@Test
	public void testExclude() throws IOException
	{
		var assn = new Schema.Assignment(null, "testname", "testuri");
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:01"), null, Schema.Specify.ITEM));
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:03"), null, Schema.Specify.EXCLUDE));
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:11"), null, Schema.Specify.EXCLUDEBRANCH));

		var tree = new CompositeTree(onto, assn).compose();
		assertNotNull(tree);

		var flat = tree.getFlat();
		assertEquals(13, flat.length);
		assertEquals("http://www.bioassayontology.org/bat#01", flat[0].uri);
		assertEquals("http://www.bioassayontology.org/bat#02", flat[1].uri);
		assertEquals("http://www.bioassayontology.org/bat#05", flat[2].uri);
		assertEquals("http://www.bioassayontology.org/bat#06", flat[3].uri);
		assertEquals("http://www.bioassayontology.org/bat#09", flat[6].uri);
		assertEquals("http://www.bioassayontology.org/bat#10", flat[7].uri);
		assertEquals("http://www.bioassayontology.org/bat#13", flat[8].uri);
	}
	
	@Test
	public void testReparent() throws IOException
	{
		var assn = new Schema.Assignment(null, "testname", "testuri");
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:01"), null, Schema.Specify.ITEM));
		var vrep = new Schema.Value(ModelSchema.expandPrefix("bat:18"), null, Schema.Specify.ITEM);
		vrep.parentURI = ModelSchema.expandPrefix("bat:01");
		assn.values.add(vrep);

		var tree = new CompositeTree(onto, assn).compose();
		assertNotNull(tree);

		var flat = tree.getFlat();
		assertEquals(21, flat.length);
		assertEquals("http://www.bioassayontology.org/bat#01", flat[0].uri);
		assertEquals("http://www.bioassayontology.org/bat#18", flat[17].uri);
		assertEquals(1, flat[17].depth);
	}
	
	@Test
	public void testRename() throws IOException
	{
		var assn = new Schema.Assignment(null, "testname", "testuri");
		var value = new Schema.Value(ModelSchema.expandPrefix("bat:01"), "new name", Schema.Specify.ITEM);
		value.descr = "new description";
		value.altLabels = new String[]{"syn1", "syn2"};
		value.externalURLs = new String[]{"url1", "url2"};
		assn.values.add(value);
		
		var tree = new CompositeTree(onto, assn).compose();
		assertNotNull(tree);
		
		var flat = tree.getFlat();
		assertEquals(17, flat.length);
		assertEquals("http://www.bioassayontology.org/bat#01", flat[0].uri);
		assertEquals("new description", flat[0].descr);
		assertArrayEquals(new String[]{"syn1", "syn2"}, flat[0].altLabels);
		assertArrayEquals(new String[]{"url1", "url2"}, flat[0].externalURLs);
		
		// rename an existing item in the tree
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:02"), "renamed", Schema.Specify.ITEM));
		tree = new CompositeTree(onto, assn).compose();
		assertNotNull(tree);
		
		flat = tree.getFlat();
		assertEquals("renamed", flat[1].label);
	}	

	@Test
	public void testContainer() throws IOException
	{
		var assn = new Schema.Assignment(null, "testname", "testuri");
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:18"), null, Schema.Specify.CONTAINER));

		var tree = new CompositeTree(onto, assn).compose();
		assertNotNull(tree);
		
		var flat = tree.getFlat();
		assertEquals(4, flat.length);
		assertFalse(flat[0].inSchema);
		assertTrue(flat[1].inSchema);
		
		// when mentioned explicitly, switch it
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:18"), null, Schema.Specify.ITEM));
		tree = new CompositeTree(onto, assn).compose();
		assertNotNull(tree);
		
		flat = tree.getFlat();
		assertEquals(4, flat.length);
		assertTrue(flat[0].inSchema);
		assertTrue(flat[1].inSchema);		
	}

	@Test
	public void testAddItems() throws IOException
	{
		var assn = new Schema.Assignment(null, "testname", "testuri");
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:18"), null, Schema.Specify.CONTAINER));
		var value = new Schema.Value(ModelSchema.expandPrefix("bat:XYZ"), "new thing", Schema.Specify.ITEM);
		value.parentURI = ModelSchema.expandPrefix("bat:19");
		assn.values.add(value);
		
		var tree = new CompositeTree(onto, assn).compose();
		assertNotNull(tree);
		
		var flat = tree.getFlat();
		assertEquals(5, flat.length);
		assertEquals("http://www.bioassayontology.org/bat#XYZ", flat[3].uri);
		assertEquals(2, flat[3].depth);
	}

	@Test
	public void testPostReparent() throws IOException
	{
		var assn = new Schema.Assignment(null, "testname", "testuri");
		assn.values.add(new Schema.Value(ModelSchema.expandPrefix("bat:01"), null, Schema.Specify.ITEM));
		var value = new Schema.Value(ModelSchema.expandPrefix("bat:03"), null, Schema.Specify.ITEM);
		value.parentURI = ModelSchema.expandPrefix("bat:02");
		assn.values.add(value);

		var tree = new CompositeTree(onto, assn).compose();
		assertNotNull(tree);

		var flat = tree.getFlat();
		assertEquals(17, flat.length);
		assertEquals("http://www.bioassayontology.org/bat#01", flat[0].uri);
		assertEquals("http://www.bioassayontology.org/bat#02", flat[1].uri);
		assertEquals("http://www.bioassayontology.org/bat#03", flat[2].uri);
		assertEquals("http://www.bioassayontology.org/bat#04", flat[3].uri);
		assertEquals(0, flat[0].depth);
		assertEquals(1, flat[1].depth);
		assertEquals(2, flat[2].depth);
		assertEquals(3, flat[3].depth);
	}
	
	@Test
	public void testCustomTerms() throws IOException
	{
		String uri1 = ModelSchema.expandPrefix("bat:NEW01"), uri2 = ModelSchema.expandPrefix("bat:NEW02");
	
		var assn = new Schema.Assignment(null, "testname", "testuri");
		assn.values.add(new Schema.Value(uri1, "new one", Schema.Specify.ITEM));

		var tree = new CompositeTree(onto, assn).compose();
		assertNotNull(tree);
		var flat = tree.getFlat();
		assertEquals(1, flat.length);
		assertEquals(uri1, flat[0].uri);
		assertEquals(0, flat[0].depth);
		
		var value = new Schema.Value(uri2, "new two", Schema.Specify.ITEM);
		value.parentURI = uri1;
		assn.values.add(value);
		
		tree = new CompositeTree(onto, assn).compose();
		assertNotNull(tree);
		flat = tree.getFlat();
		assertEquals(2, flat.length);
		assertEquals(uri1, flat[0].uri);
		assertEquals(uri2, flat[1].uri);
		assertEquals(0, flat[0].depth);
		assertEquals(1, flat[1].depth);
	}	

	// ------------ private methods ------------

	private void dumpAssn(Schema.Assignment assn)
	{
		Util.writeln("ASSIGNMENT: #values=" + assn.values.size());
		for (var value : assn.values) Util.writeln("  <" + value.uri + "> spec=" + value.spec);
	}
	private void dumpTree(SchemaTree tree)
	{
		var flat = tree.getFlat();
		Util.writeln("TREE: #nodes=" + flat.length);
		for (int n = 0; n < flat.length; n++) Util.writeln("* ".repeat(flat[n].depth) + "<" + flat[n].uri + "> " + flat[n].label + " inSchema:" + flat[n].inSchema);
	}
}

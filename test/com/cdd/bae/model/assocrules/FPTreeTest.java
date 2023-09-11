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

package com.cdd.bae.model.assocrules;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.*;

/*
	Test for AssociationRulesLearner
*/

public class FPTreeTest 
{

	@Test
	public void testBuildTree() 
	{
		FPTree tree = new FPTree();
		tree.add("1/2/3/4".split("/"));
		tree.add("1/2/4".split("/"));
		tree.add("1/2".split("/"));
		tree.add("2/3/4".split("/"));
		tree.add("2/3".split("/"));
		tree.add("3/4".split("/"));
		tree.add("2/4".split("/"));
		tree.add("5".split("/"));
		
		String s = tree.toString();
		assertThat(s, containsString("1 : 1 subtrees"));
		assertThat(s, containsString("2 : 2 subtrees"));
		assertThat(s, containsString("3 : 3 subtrees"));
		assertThat(s, containsString("4 : 5 subtrees"));
		assertThat(s, containsString("1 [3]"));
		assertThat(s, containsString("3 [2]"));
		
		assertEquals(3, tree.lookup("1".split("/")));
		assertEquals(3, tree.lookup("1/2".split("/")));
		assertEquals(1, tree.lookup("1/2/3".split("/")));
		assertEquals(1, tree.lookup("1/2/3/4".split("/")));

		// getKeys
		assertEquals(new HashSet<String>(Arrays.asList("1,2,3,4,5".split(","))), tree.getKeys());

		assertEquals(1, tree.findTerminus("1".split("/")).size());
		assertEquals(2, tree.findTerminus("2".split("/")).size());
		assertEquals(3, tree.findTerminus("3".split("/")).size());
		assertEquals(5, tree.findTerminus("4".split("/")).size());
		assertEquals(1, tree.findTerminus("1/2".split("/")).size());
		assertEquals(2, tree.findTerminus("2/3".split("/")).size());
		assertEquals(4, tree.findTerminus("2/4".split("/")).size());
		assertEquals(3, tree.findTerminus("3/4".split("/")).size());
		assertEquals(2, tree.findTerminus("1/2/4".split("/")).size());
		assertEquals(2, tree.findTerminus("2/3/4".split("/")).size());
		assertEquals(1, tree.findTerminus("1/2/3/4".split("/")).size());
		assertEquals(0, tree.findTerminus("1/2/3/4/5".split("/")).size());
		assertEquals(1, tree.findTerminus("5".split("/")).size());
		
		assertEquals(3, tree.getCount("1".split("/")));
		assertEquals(6, tree.getCount("2".split("/")));
		assertEquals(4, tree.getCount("3".split("/")));
		assertEquals(5, tree.getCount("4".split("/")));
		assertEquals(1, tree.getCount("5".split("/")));
		assertEquals(3, tree.getCount("1/2".split("/")));
		assertEquals(3, tree.getCount("2/3".split("/")));
		assertEquals(4, tree.getCount("2/4".split("/")));
		assertEquals(3, tree.getCount("3/4".split("/")));
		assertEquals(2, tree.getCount("1/2/4".split("/")));
		assertEquals(2, tree.getCount("2/3/4".split("/")));
		assertEquals(1, tree.getCount("1/2/3/4".split("/")));
		assertEquals(0, tree.getCount("1/2/3/4/5".split("/")));
		
		assertEquals(toSet("2,3,4"), tree.getChildren("1".split("/")));
		assertEquals(toSet("3,4"), tree.getChildren("2".split("/")));
		assertEquals(toSet("4"), tree.getChildren("3".split("/")));
		assertEquals(toSet(""), tree.getChildren("4".split("/")));
		assertEquals(toSet(""), tree.getChildren("5".split("/")));
		assertEquals(toSet("3,4"), tree.getChildren("1/2".split("/")));
		assertEquals(toSet("4"), tree.getChildren("2/3".split("/")));
		assertEquals(toSet(""), tree.getChildren("2/4".split("/")));
		assertEquals(toSet(""), tree.getChildren("3/4".split("/")));
		assertEquals(toSet(""), tree.getChildren("1/2/4".split("/")));
		assertEquals(toSet(""), tree.getChildren("2/3/4".split("/")));
		assertEquals(toSet(""), tree.getChildren("1/2/3/4".split("/")));
		assertEquals(toSet(""), tree.getChildren("1/2/3/4/5".split("/")));
		
		assertChildrenSupport(tree, "2=3,3=1,4=2", "1");
		assertChildrenSupport(tree, "3=3,4=4", "2");
		assertChildrenSupport(tree, "4=3", "3");
		assertChildrenSupport(tree, "", "4");
		assertChildrenSupport(tree, "3=1,4=2", "1/2");
		assertChildrenSupport(tree, "4=1", "1/3");
		assertChildrenSupport(tree, "", "1/4");
		assertChildrenSupport(tree, "4=2", "2/3");
		assertChildrenSupport(tree, "", "2/4");
		assertChildrenSupport(tree, "4=1", "1/2/3");
	}
	
	private void assertChildrenSupport(FPTree tree, String expected, String prefix)
	{
		Map<String, Integer> actual = tree.getChildrenSupport(prefix.split("/"));
		if (expected.equals(""))
			assertEquals(0, actual.size());
		else
		{
			assertEquals(expected.split(",").length, actual.size());
			for (String a : expected.split(","))
			{
				String[] pair = a.split("=");
				assertEquals(Integer.valueOf(pair[1]), actual.get(pair[0]));
			}
		}
	}
	
	private Set<String> toSet(String string)
	{
		if (string.equals("")) return new HashSet<>();
		return new HashSet<>(Arrays.asList(string.split(",")));
	}

}

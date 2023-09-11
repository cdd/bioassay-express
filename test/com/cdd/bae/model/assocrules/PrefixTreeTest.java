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

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.*;

/*
	Test for AssociationRulesLearner
*/

public class PrefixTreeTest
{

	@Test
	public void testBuildTree()
	{
		PrefixTree<String, String> tree = new PrefixTree<>();
		tree.add("a".split("/"), "a");
		tree.add("a/b".split("/"), "ab");
		tree.add("c".split("/"), "c");
		tree.add("a/c".split("/"), "ac");
		tree.add("a/b/c/d".split("/"), "abcd");
		tree.add("a/c/e".split("/"), "ace");

		assertFindAll(tree, "a", "a");
		assertFindAll(tree, "a,b", "a,ab");
		assertFindAll(tree, "a,b,c", "a,ab,c,ac");
		assertFindAll(tree, "a,b,c,d", "a,ab,c,ac,abcd");

		tree.add("a".split("/"), "a2");
		assertFindAll(tree, "a", "a,a2");

		String s = tree.toString();
		assertTrue(s.contains("d [abcd]"));
		assertTrue(s.contains("a [a, a2]"));

		// edge cases
		assertFindAll(tree, "unknown", "");
		assertFindAll(tree, "", "");

		// getKeys and getValues
		assertEquals(new HashSet<String>(Arrays.asList("a,b,c,d,e".split(","))), tree.getKeys());
		assertEquals(new HashSet<String>(Arrays.asList("a,a2,ab,ac,ace,c,abcd".split(","))), tree.getValues());

		// lookup
		assertLookup(tree, "a", "a,a2");
		assertLookup(tree, "a/b", "ab");
		assertLookup(tree, "a/b/c", "");
		assertLookup(tree, "a/b/c/d", "abcd");
		assertLookup(tree, "a/b/c/d/e", "");
		assertLookup(tree, "c", "c");
		assertLookup(tree, "unknown", "");
	}

	@Test
	public void testEmptyTree()
	{
		PrefixTree<String, String> tree = new PrefixTree<>();
		Set<String> result = tree.findAll(toSet(new String[]{"a"}));
		assertEquals(0, result.size());
	}

	private void assertFindAll(PrefixTree<String, String> tree, String keys, String expected)
	{
		Set<String> sKeys = toSet(keys.split(","));
		Set<String> sExpected = toSet(expected.split(","));
		assertEquals(sExpected, tree.findAll(sKeys));
	}

	private void assertLookup(PrefixTree<String, String> tree, String prefix, String expected)
	{
		Set<String> sExpected = toSet(expected.split(","));
		assertEquals(sExpected, tree.lookup(prefix.split("/")));
	}

	private Set<String> toSet(String[] strings)
	{
		if (strings.length == 1 && strings[0].equals("")) return new HashSet<>();
		return new HashSet<>(Arrays.asList(strings));
	}
}

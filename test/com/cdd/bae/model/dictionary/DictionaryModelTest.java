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

package com.cdd.bae.model.dictionary;

import com.cdd.bao.template.SchemaTree.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.*;

/*
	Test for DictionaryModel
*/

public class DictionaryModelTest
{

	@Test
	public void testOldMappingBehaviour()
	{
		Map<String, Node> map = getDictionary("abc");
		DictionaryModel dm = new DictionaryModel("propURI", map);
		List<DictionaryModel.Hit<Node>> result = dm.parseText(DictionaryModels.standardizeText("Abc"));
		assertEquals(1, result.size());
		assertEquals(0, result.get(0).begin);
		assertEquals(3, result.get(0).end);
	}

	@Test
	public void testParseText()
	{
		// independent of capitalisation
		assertFinds("text Abc text", "abc", 5);

		// matches at end of text
		assertFinds("text Abc", "abc", 5);

		// short dictionary words have a space appended, need to make sure that they match
		// in a variety of cases
		assertFinds("text short text", "short ", 5);
		assertFinds("text short. text", "short ", 5);
		assertFinds("text short, text", "short ", 5);
		assertNotFinds("text short.text", "short ", 5);
		assertNotFinds("text short,text", "short ", 5);
		assertFinds("text (short). text", "short ", 6);
		assertFinds("text short-text", "short ", 5);
		// we also match this one at the end of text
		assertFinds("text short", "short ", 5);

		// dictionary words with space in between
		assertFinds("text spa ced text", "spa ced", 5);
		assertFinds("text spa-ced text", "spa ced", 5);
		assertFinds("text spa   ced text", "spa ced", 5);
		assertFinds("text spa\nced text", "spa ced", 5);
		assertNotFinds("text spa.\nced text", "spa ced", 5);

		// additional cases
		assertFinds("cho-1", "cho ", 0);
		assertFinds("both", " both ", 0);
	}

	@Test
	public void testRemoveOverlappingHits()
	{

		Map<String, Node> map = new HashMap<>();
		map.put("dummy", getNode("unused"));
		DictionaryModel dm = new DictionaryModel("propURI", map);
		List<DictionaryModel.Hit<Node>> hits = new ArrayList<>();
		hits.add(dm.new Hit<Node>(10, 20, getNode("h2")));
		hits.add(dm.new Hit<Node>(2, 4, getNode("h1")));
		hits.add(dm.new Hit<Node>(3, 4, getNode("h1.1")));
		hits.add(dm.new Hit<Node>(12, 18, getNode("h2.1")));
		hits.add(dm.new Hit<Node>(10, 18, getNode("h2.2")));
		hits.add(dm.new Hit<Node>(15, 20, getNode("h2.3")));
		hits.add(dm.new Hit<Node>(21, 30, getNode("h3")));
		hits.add(dm.new Hit<Node>(25, 35, getNode("h4")));
		hits.add(dm.new Hit<Node>(26, 29, getNode("h3.1")));
		hits.add(dm.new Hit<Node>(26, 30, getNode("h3.2")));

		hits = DictionaryModel.removeOverlappingHits(hits);

		assertEquals(4, hits.size());
		assertEquals("h1", hits.get(0).value.label);
		assertEquals("h2", hits.get(1).value.label);
		assertEquals("h3", hits.get(2).value.label);
		assertEquals("h4", hits.get(3).value.label);
	}
	
	@Test
	public void testHandlingOfWhitespace()
	{
		assertFinds("a b text abc text", "abc", 9);
		assertFinds("a   b text abc text", "abc", 11);
		assertFinds("a   b  text abc text", "abc", 12);
		assertFinds("text spa   ced text", "spa ced", 5);
		assertFinds("text spa    ced text", "spa ced", 5);
		assertFinds("text spa     ced text", "spa ced", 5);
		assertFinds("text  spa      ced text", "spa ced", 6);
		assertFinds("spa      ced text", "spa ced", 0);
		assertFinds("spa      ced", "spa ced", 0);
		assertFinds("text spa  \nced text", "spa ced", 5);
		assertFinds("text spa \nced text", "spa ced", 5);
		assertFinds("text spa\tced text", "spa ced", 5);
	}

	private Node getNode(String label)
	{
		Node node = new Node();
		node.label = label;
		node.uri = label;
		return node;
	}

	private Map<String, Node> getDictionary(String... words)
	{
		Map<String, Node> map = new HashMap<>();
		for (String word : words)
			map.put(word, getNode(word));
		return map;
	}

	private List<Map<String, SortedSet<Integer>>> getParseResult(String text)
	{
		DictionaryModel dm = new DictionaryModel("propURI",
				getDictionary("abc", "spa ced", "short ", "cho ", " both "));
		Map<String, SortedSet<Integer>> groupedBegin = new HashMap<>();
		Map<String, SortedSet<Integer>> groupedEnd = new HashMap<>();
		for (DictionaryModel.Hit<Node> hit : dm.parseText(text))
		{
			String key = hit.value.label;
			groupedBegin.computeIfAbsent(key, k -> new TreeSet<>()).add(hit.begin);
			groupedEnd.computeIfAbsent(key, k -> new TreeSet<>()).add(hit.end);
		}
		List<Map<String, SortedSet<Integer>>> result = new ArrayList<>();
		result.add(groupedBegin);
		result.add(groupedEnd);
		return result;
	}

	private void assertFinds(String text, String expected, int position)
	{
		List<Map<String, SortedSet<Integer>>> result = getParseResult(text);
		Map<String, SortedSet<Integer>> begin = result.get(0);
		try
		{
			assertTrue(begin.containsKey(expected));
			SortedSet<Integer> positions = begin.get(expected);
			assertEquals(1, positions.size());
			assertEquals(Integer.valueOf(position), positions.first());
		}
		catch (AssertionError e)
		{
			System.out.println("Expected to find '" + expected + "'");
			System.out.println("Failed to find in |" + text + "|");
			System.out.println(result);
			throw e;
		}
	}

	private void assertNotFinds(String text, String expected, int position)
	{
		List<Map<String, SortedSet<Integer>>> result = getParseResult(text);
		Map<String, SortedSet<Integer>> begin = result.get(0);
		try
		{
			assertFalse(begin.containsKey(expected));
		}
		catch (AssertionError e)
		{
			System.out.println("Expected not to find '" + expected + "'");
			System.out.println("Failed to find in " + text);
			System.out.println(result);
			throw e;
		}
	}
}

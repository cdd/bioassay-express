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

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.model.dictionary.AnnotationSearchModel.*;
import com.cdd.bae.tasks.*;
import com.cdd.bae.util.*;
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import org.junit.jupiter.api.*;

/*
	Test for AnnotationSearchModel
*/

public class AnnotationSearchModelTest
{

	@BeforeEach
	public void setup() throws ConfigurationException, IOException
	{
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
	}

	@Test
	public void testSearch1() throws IOException
	{
		AnnotationSearchModel search = new AnnotationSearchModel();

		List<?> result = search.search("ce", "unknown URI");
		assertTrue(result.isEmpty());

		result = search.search("", PubChemAssays.CELLLINE_URI);
		assertFalse(result.isEmpty());
		assertTrue(result.size() > 10);
		int fullSize = result.size();
		result = search.search("c", PubChemAssays.CELLLINE_URI);
		assertTrue(result.size() < fullSize);

		result = search.search(" ", PubChemAssays.CELLLINE_URI);
		assertFalse(result.isEmpty());
		assertTrue(result.size() > 10);

		result = search.search("ce", PubChemAssays.CELLLINE_URI);
		assertFalse(result.isEmpty());
		assertTrue(result.size() > 10);

		result = search.search("cho", PubChemAssays.CELLLINE_URI);
		assertFalse(result.isEmpty());
		assertTrue(result.size() > 10);

		// equivalent searches
		result = search.search("cho-k1", PubChemAssays.CELLLINE_URI);
		int resultSize = result.size();
		assertFalse(result.isEmpty());
		assertTrue(resultSize < 10);

		result = search.search("chok1", PubChemAssays.CELLLINE_URI);
		assertEquals(result.size(), resultSize, "dash ignored");

		result = search.search("cho k1", PubChemAssays.CELLLINE_URI);
		assertEquals(result.size(), resultSize, "spaces ignored");

		result = search.search("chok1234", PubChemAssays.CELLLINE_URI);
		assertTrue(result.isEmpty());

		// equivalent searches
		result = search.search("111-", "http://www.bioassayontology.org/bao#BAO_0002009");
		resultSize = result.size();
		assertFalse(result.isEmpty(), "can search only numbers");

		result = search.search("1,1.1-", "http://www.bioassayontology.org/bao#BAO_0002009");
		assertFalse(result.isEmpty(), "and ignores commas and dots");
		assertEquals(result.size(), resultSize, "dash ignored");
	}

	@Test
	public void testSearch2() throws IOException
	{
		AnnotationSearchModel search = new AnnotationSearchModel();

		List<?> result = search.search("ce", PubChemAssays.CELLLINE_URI);
		assertTrue(result.size() > 10);

		result = search.search("cho", PubChemAssays.CELLLINE_URI);
		assertTrue(result.size() > 10);

		result = search.search("choline receptor m", PubChemAssays.CELLLINE_URI);
		assertTrue(result.size() < 10);
	}
	
	@Test
	public void testSearch3() throws IOException
	{
		AnnotationSearchModel search = new AnnotationSearchModel();
		List<?> result = search.search("canc", PubChemAssays.DISEASE_URI);
		int size = result.size();
		
		result = search.search("cancer", PubChemAssays.DISEASE_URI);
		assertTrue(size > result.size());
		
		
		// check that the order of words doesn't matter
		result = search.search("bladder cancer", PubChemAssays.DISEASE_URI);
		size = result.size();
		
		result = search.search("cancer bladder", PubChemAssays.DISEASE_URI);
		assertEquals(size, result.size(), "order of words doesn't matter");
		
		result = search.search("BLADDER CANCER", PubChemAssays.DISEASE_URI);
		assertEquals(size, result.size(), "capitalisation doesn't matter");

		result = search.search("", PubChemAssays.DISEASE_URI);
		result = search.search("c", PubChemAssays.DISEASE_URI);
	}
	
	@Test
	public void testNoDuplicates() throws IOException
	{
		AnnotationSearchModel search = new AnnotationSearchModel();
		List<AnnotationSearchModel.Hit> result = search.search("full", PubChemAssays.MODE_OF_ACTION_URI);
		Set<String> uniqueURI = result.stream().map(h -> h.node.uri).collect(Collectors.toSet());
		assertEquals(uniqueURI.size(), result.size());
	}
	
	@Test
	public void testSearchIgnoreListHandling() throws IOException
	{
		AnnotationSearchModel search = new AnnotationSearchModel();
		List<AnnotationSearchModel.Hit> result = search.search("functional", PubChemAssays.ASSAYTYPE_URI);
		int numExact = 0;
		for (AnnotationSearchModel.Hit hit : result) if (hit.node.label.equals("functional")) numExact++;
		assertEquals(1, numExact);
	}
	
	@Test
	public void testHitOrder() throws IOException
	{
		AnnotationSearchModel search = new AnnotationSearchModel();
	
		List<AnnotationSearchModel.Hit> result = search.search("leukemia", PubChemAssays.DISEASE_URI);
		assertFalse(result.isEmpty());
		assertEquals("leukemia", result.get(0).node.label);
		
		String label0 = result.get(0).node.label;
		String label2 = result.get(2).node.label;
		assertTrue(label0.compareTo(label2) > 0, "label2 comes alphabetically before label0");
	}
	
	@Test
	public void testWhitespace() throws IOException
	{
		AnnotationSearchModel search = new AnnotationSearchModel();
		List<AnnotationSearchModel.Hit> result = search.search("", PubChemAssays.DISEASE_URI);
		Hit previous = result.remove(0);
		while (!result.isEmpty())
		{
			Hit current = result.remove(0);
			assertTrue(previous.node.label.trim().compareToIgnoreCase(current.node.label.trim()) < 0);
		}
	}
}

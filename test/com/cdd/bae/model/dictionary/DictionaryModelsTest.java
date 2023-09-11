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
import com.cdd.bae.tasks.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.*;

import objectexplorer.*;

/*
	Test for DictionaryModels
*/

public class DictionaryModelsTest
{
	@BeforeEach
	public void setup() throws ConfigurationException, IOException
	{
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
	}

	@Test
	public void testBuildModels() throws IOException
	{
		DictionaryModels builder = new DictionaryModels();
		// System.out.println(ObjectGraphMeasurer.measure(builder));
		builder.buildModels();
		// System.out.println(ObjectGraphMeasurer.measure(builder));
		// Footprint{Objects=1672, References=3239, Primitives=[char x 7912, int x 1146, float x 11]}
		// Footprint{Objects=526835, References=1918134, Primitives=[boolean x 130299, char x 3189421, int x 4636982, float x 12]}

		TestResourceFile assayDescription = new TestResourceFile("/testData/nlp/AssayDescriptionLong.txt");
		String text = assayDescription.getContent();
		builder.predict(text);
	}

	@Test
	public void testIgnoreList() throws IOException
	{
		DictionaryModels builder = new DictionaryModels();
		assertNotEquals(0, builder.ignoreList.size());
		assertTrue(builder.ignoreList.contains("absence"));
		assertTrue(builder.ignoreList.contains("fetal cell line"));
	}

	@Test
	public void testStandardizeText()
	{
		// characters that are ignored irrespective of where they occur
		assertEquals("homo sapiens", DictionaryModels.standardizeText("Homo-sapiens"));
		assertEquals("homo sapiens", DictionaryModels.standardizeText("[Homo sapiens]"));
		assertEquals("homo sapiens", DictionaryModels.standardizeText("(Homo sapiens)"));
		assertEquals("a b c", DictionaryModels.standardizeText("[a[b]c]"));
		assertEquals("a b c", DictionaryModels.standardizeText("(a(b)c)"));

		// characters that are ignored at the end of a word but not inside
		assertEquals("a.b a,b a;b a:b", DictionaryModels.standardizeText("a.b. a,b, a;b; a:b:"));

		// insert a space at the beginning of a new line
		assertEquals("a\n b\n c", DictionaryModels.standardizeText("a\nb\nc"));

		// don't have more than one consecutive space
		assertEquals("a b c\n d", DictionaryModels.standardizeText("[a] b    c\n d"));
	}

	@Test
	public void testExpandWord() throws IOException
	{
		DictionaryModels builder = new DictionaryModels();

		Set<String> result = builder.expandWord("behavior");
		assertEquals(2, result.size());
		assertThat(result, hasItem("behaviour"));

		result = builder.expandWord("cho cell");
		assertEquals(3, result.size());
		assertThat(result, hasItem("cho"));
		assertThat(result, hasItem("cho cell"));
		assertThat(result, hasItem("cho cell line"));

		result = builder.expandWord("cho cell line");
		assertEquals(3, result.size());
		assertThat(result, hasItem("cho"));
		assertThat(result, hasItem("cho cell"));
		assertThat(result, hasItem("cho cell line"));

		result = builder.expandWord("bg-1 cell line");
		assertEquals(5, result.size());
		assertThat(result, hasItem("bg-1"));
		assertThat(result, not(hasItem("bg1")));
		assertThat(result, hasItem("bg-1 cell"));
		assertThat(result, hasItem("bg1 cell"));

		result = builder.expandWord("csm14.1");
		assertEquals(2, result.size());
		assertThat(result, hasItem("csm14.1"));
		assertThat(result, hasItem("csm 14.1"));
	}

	@Test
	public void testExpandLabels() throws IOException
	{
		DictionaryModels builder = new DictionaryModels();
		assertExpandLabels(builder, "ABC", "abc");
		assertExpandLabels(builder, "ABC cell", "abc cell:abc cell line");
		assertExpandLabels(builder, "ABC cell line", "abc cell:abc cell line");
		assertExpandLabels(builder, "ABCD cell", "abcd:abcd cell:abcd cell line");
		assertExpandLabels(builder, "ABCDE cell line", "abcde:abcde cell:abcde cell line");
		assertExpandLabels(builder, "ABC cell:DEF Cell:cell:absence", "abc cell:abc cell line:def cell:def cell line:cell");
		
		assertExpandLabels(builder, "BG-1 cell line", "bg 1:bg1 cell:bg 1 cell:bg1 cell line:bg 1 cell line");
		assertExpandLabels(builder, "CSM14.1", "csm14.1:csm 14.1");

		assertExpandLabels(builder, "absence", "");
		
		// some cell line entries have a trailing space
		assertExpandLabels(builder, "immortal cell line cell", "immortal cell line:immortal cell line cell:immortal cell");
	}

	@Test
	public void testPredict() throws IOException
	{
		// this test depends on the underlying ontology, should this change adapt the outcome as necessary
		DictionaryModels models = new DictionaryModels();
		List<ScoredHit> result = models.predict("cho cell").get(PubChemAssays.CELLLINE_URI);
		assertEquals(1, result.size());
		assertThat(result.get(0).toString(), containsString("CHO cell"));

		result = models.predict("cho cells and hek293 cells").get(PubChemAssays.CELLLINE_URI);
		assertEquals(2, result.size());
		assertThat(result.get(0).toString(), containsString("CHO cell"));
		assertThat(result.get(1).toString(), containsString("HEK-293"));

		result = models.predict("cho cells  or detailed").get(PubChemAssays.CELLLINE_URI);
		assertEquals(1, result.size());
		assertThat(result.get(0).toString(), containsString("CHO cell"));

		result = models.predict("cho cells and Or De-cells").get(PubChemAssays.CELLLINE_URI);
		assertEquals(2, result.size());
		assertThat(result.get(0).toString(), containsString("Or De cell"));
		assertThat(result.get(1).toString(), containsString("CHO cell"));

		result = models.predict("rats or mice").get(PubChemAssays.ORGANISM_URI);
		assertEquals(1, result.size());
		assertThat(result.get(0).toString(), containsString("Rattus"));
		
		result = models.predict("Just the number 293 in text").get(PubChemAssays.CELLLINE_URI);
		assertEquals(0, result.size());
	}
	
	@Test
	public void testCellWhitelist() throws IOException
	{
		// test for cell names that we expect to occur without 'cell' or 'cell line'
		DictionaryModels models = new DictionaryModels();
		String[] allowedShortCellnames = {"COS-1", "H4 neuroglioma", "HEK-293",
				"HeLa", "Hep2", "K562", "MCF-7", "MEF", "CHO", "Vero",
				"U-2 OS", "Khos", "cyst", "ovum"};

		for (String cellname : allowedShortCellnames)
		{
			String s = "in " + cellname + " at";
			List<ScoredHit> result = models.predict(s).get(PubChemAssays.CELLLINE_URI);
			assertThat(cellname, result.size(), is(1));
		}
	}
	
	@Test
	public void testSpecialCases() throws IOException
	{
		DictionaryModels models = new DictionaryModels();
		List<ScoredHit> result = models.predict("page 4 of 5").get(PubChemAssays.ASSAYFOOTPRINT_URI);
		assertThat(result, empty());

		result = models.predict("the page assay").get(PubChemAssays.ASSAYFOOTPRINT_URI);
		assertThat(result, hasSize(1));

		result = models.predict("we used sds-page.").get(PubChemAssays.ASSAYFOOTPRINT_URI);
		assertThat(result, hasSize(1));

		result = models.predict("we used native page.").get(PubChemAssays.ASSAYFOOTPRINT_URI);
		assertThat(result, hasSize(1));
	}

	private void assertExpandLabels(DictionaryModels builder, String input, String expected)
	{
		Set<String> labels = new TreeSet<>();
		labels.addAll(Arrays.asList(input.split(":")));
		Set<String> expectedLabels = new TreeSet<>();
		if (!expected.equals("")) expectedLabels.addAll(Arrays.asList(expected.split(":")));
		assertEquals(expectedLabels, builder.expandLabels(labels));
	}
}

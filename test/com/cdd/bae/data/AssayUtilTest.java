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

package com.cdd.bae.data;

import com.cdd.bae.config.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.template.SchemaVocab.*;
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Assay utilities test.
*/

public class AssayUtilTest extends TestBaseClass
{
	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		TestUtilities.ensureOntologies();
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
		Common.setProvCache(ProvisionalCache.loaded());
	}

	@Test
	public void testGroupNestScore()
	{
		final String[][] TESTGROUPS = 
		{
			null, null, {String.valueOf(0)},
			{"A"}, null, {String.valueOf(0)},
			{"A"}, {"A"}, {String.valueOf(2)},
			{"A", "B"}, {"A", "B"}, {String.valueOf(6)},
			{"A"}, {"A", "B"}, {String.valueOf(3)},
			{"B"}, {"A", "B"}, {String.valueOf(2)},
			{"A", "B"}, {"A"}, {String.valueOf(3)},
			{"A", "B"}, {"B"}, {String.valueOf(2)},
			{"A", "B"}, {"A", "C"}, {String.valueOf(4)},
			{"A", "C"}, {"B", "C"}, {String.valueOf(2)},
			{"A", "B", "D"}, {"A", "C", "D"}, {String.valueOf(8)},
			{"A", "C", "D"}, {"B", "C", "D"}, {String.valueOf(6)},
			{"A", "B", "D"}, {"A", "C", "E"}, {String.valueOf(6)},
			{"A", "C", "D"}, {"B", "C", "E"}, {String.valueOf(4)},
		};
		for (int n = 0; n < TESTGROUPS.length; n += 3)
		{
			String[] nest1 = TESTGROUPS[n], nest2 = TESTGROUPS[n + 1];
			int wantScore = TESTGROUPS[n + 2] == null ? -1 : Integer.parseInt(TESTGROUPS[n + 2][0]);
			int gotScore = AssayUtil.groupNestCompatibilityScore(nest1, nest2);
			String msg = String.format("Nest [%s] vs [%s]", Util.arrayStr(nest1), Util.arrayStr(nest2));
			assertEquals(wantScore, gotScore, msg);
		}
	}

	@Test
	public void testConformGroupNest()
	{
		Schema schema = mock(Schema.class);
		String[] groupNest = "A:B:C".split(":");

		// no group nests
		mockAssignments(schema, null);
		String[] result = AssayUtil.conformGroupNest("propURI", groupNest, schema);
		assertArrayEquals("A:B:C".split(":"), result);

		// one group nest
		mockAssignments(schema, new String[][]{"X:Y".split(":")});
		result = AssayUtil.conformGroupNest("propURI", groupNest, schema);
		assertArrayEquals("X:Y".split(":"), result);

		// two or more group nests - use score to prioritize
		mockAssignments(schema, new String[][]{"A:B:D".split(":"), "A:B".split(":")});
		result = AssayUtil.conformGroupNest("propURI", groupNest, schema);
		assertArrayEquals("A:B:D".split(":"), result);

		mockAssignments(schema, new String[][]{"A:B:D".split(":"), "A:B:C".split(":")});
		result = AssayUtil.conformGroupNest("propURI", groupNest, schema);
		assertArrayEquals("A:B:C".split(":"), result);
	}

	/*@Test
	public void testConformToRemapWithNoRemappings() throws IOException
	{
		final String noRemappingPath = "/testData/data/no-remappings.ttl";
		TestResourceFile resource = new TestResourceFile(noRemappingPath);

		int lastSlashIdx = noRemappingPath.lastIndexOf('/');
		final String noRemappingBaseName = noRemappingPath.substring(lastSlashIdx + 1);
		File noRemappings = resource.getAsFile(folder, noRemappingBaseName);

		Vocabulary vocab = new Vocabulary();
		vocab.loadExplicit(new String[]{noRemappings.getCanonicalPath()});

		Assay assay = getTestAssay();
		Schema schema = mock(Schema.class);
		Map<String, StoredRemapTo> remappings = getRemappings(vocab);
		assertEquals(0, remappings.size(), "There should not be any remappings.");

		// conformToRemap should be a no-op
		JSONObject beforeConformToRemap = AssayJSON.serialiseAssay(assay);
		AssayUtil.conformToRemap(assay, schema);
		JSONObject afterConformToRemap = AssayJSON.serialiseAssay(assay);
		assertTrue(beforeConformToRemap.toString().equals(afterConformToRemap.toString()),
				"There should be no changes to the assay.");
	}*/

/* deprecated
	@Test
	public void testConformToRemapWithOneRemapping() throws IOException
	{
		final String oneRemappingPath = "/testData/data/one-remapping.ttl";
		TestResourceFile resource = new TestResourceFile(oneRemappingPath);

		int lastSlashIdx = oneRemappingPath.lastIndexOf('/');
		final String oneRemappingBaseName = oneRemappingPath.substring(lastSlashIdx + 1);
		File oneRemapping = resource.getAsFile(folder, oneRemappingBaseName);

		Vocabulary vocab = new Vocabulary();
		vocab.loadExplicit(new String[]{oneRemapping.getCanonicalPath()});

		Assay assay = getTestAssay();
		Schema schema = mock(Schema.class);
		Map<String, StoredRemapTo> remappings = getRemappings(vocab);
		assertEquals(1, remappings.size(), "There should be a single remapping.");

		// bao:BAO_0000205 remapTo bao:BAO_0002009
		int count_BAO_0002009 = 0;
		AssayUtil.conformToRemap(assay, schema);
		for (DataObject.Annotation annot : assay.annotations)
			if (annot.propURI != null && annot.propURI.endsWith("BAO_0002009")) count_BAO_0002009++;
		assertEquals(4, count_BAO_0002009, "After remapping, there should be 4 annotations with propURI bao:BAO_0002009.");
	}

	@Test
	public void testConformToRemapWithTwoLinkChain() throws IOException
	{
		final String twoLinkRemappingPath = "/testData/data/two-link-remapping.ttl";
		TestResourceFile resource = new TestResourceFile(twoLinkRemappingPath);

		int lastSlashIdx = twoLinkRemappingPath.lastIndexOf('/');
		final String twoLinkRemappingBaseName = twoLinkRemappingPath.substring(lastSlashIdx + 1);
		File twoLinkRemapping = resource.getAsFile(folder, twoLinkRemappingBaseName);

		Vocabulary vocab = new Vocabulary();
		vocab.loadExplicit(new String[]{twoLinkRemapping.getCanonicalPath()});

		Assay assay = getTestAssay();
		Schema schema = mock(Schema.class);
		Map<String, StoredRemapTo> remappings = getRemappings(vocab);
		assertEquals(2, remappings.size(), "There should be two-link remapping chain.");

		// bao:BAO_0000205 remapTo bao:BAO_0002009
		// bao:BAO_0002009 remapTo bao:BAO_0095009
		int count_BAO_0095009 = 0;
		AssayUtil.conformToRemap(assay, schema);
		for (DataObject.Annotation annot : assay.annotations)
			if (annot.propURI != null && annot.propURI.endsWith("BAO_0095009")) count_BAO_0095009++;
		assertEquals(6, count_BAO_0095009, "After remapping, there should be 6 annotations with propURI bao:BAO_0095009.");
	}

	@Test // (expected = RuntimeException.class)
	public void testConformToRemapWithCycle() throws IOException
	{
		final String twoLinkRemappingPath = "/testData/data/two-link-remapping.ttl";
		TestResourceFile resource = new TestResourceFile(twoLinkRemappingPath);

		int lastSlashIdx = twoLinkRemappingPath.lastIndexOf('/');
		final String twoLinkRemappingBaseName = twoLinkRemappingPath.substring(lastSlashIdx + 1);
		File twoLinkRemapping = resource.getAsFile(folder, twoLinkRemappingBaseName);

		Vocabulary vocab = new Vocabulary();
		vocab.loadExplicit(new String[]{twoLinkRemapping.getCanonicalPath()});

		Assay assay = getTestAssay();
		Schema schema = mock(Schema.class);
		Map<String, StoredRemapTo> remappings = getRemappings(vocab);
		assertEquals(2, remappings.size(), "There should be two-link remapping chain.");
		
		// tack on additional remapping to make a cycle
		Map<String, StoredRemapTo> remappingsWithCycle = new HashMap<>(remappings);
		StoredRemapTo srt = new StoredRemapTo();
		srt.fromURI = "http://www.bioassayontology.org/bao#BAO_0095009";
		srt.toURI = "http://www.bioassayontology.org/bao#BAO_0000205";
		remappingsWithCycle.put(srt.fromURI, srt);

		// bao:BAO_0000205 remapTo bao:BAO_0002009
		// bao:BAO_0002009 remapTo bao:BAO_0095009
		// bao:BAO_0095009 remapTo bao:BAO_0000205
		Assertions.assertThrows(RuntimeException.class, () -> AssayUtil.conformToRemap(assay, schema));
	}

	@Test // (expected = RuntimeException.class)
	public void testConformToRemapWithOneLinkCycle() throws IOException
	{
		Assay assay = getTestAssay();
		Schema schema = mock(Schema.class);
		
		// set up remappings to contain a single cycle
		Map<String, StoredRemapTo> remappingsWithCycle = new HashMap<>();
		StoredRemapTo srt = new StoredRemapTo();
		srt.fromURI = "http://www.bioassayontology.org/bao#BAO_0000205";
		srt.toURI = "http://www.bioassayontology.org/bao#BAO_0000205";
		remappingsWithCycle.put(srt.fromURI, srt);

		// bao:BAO_0000205 remapTo bao:BAO_0000205
		Assertions.assertThrows(RuntimeException.class, () -> AssayUtil.conformToRemap(assay, schema));
	}

	@Test // (expected = RuntimeException.class)
	public void testConformToRemapWithTwoLinkCycle() throws IOException
	{
		Assay assay = getTestAssay();
		Schema schema = mock(Schema.class);

		// set up remappings to contain a single cycle
		Map<String, StoredRemapTo> remappingsWithCycle = new HashMap<>();
		StoredRemapTo srt1 = new StoredRemapTo();
		srt1.fromURI = "http://www.bioassayontology.org/bao#BAO_0000205";
		srt1.toURI = "http://www.bioassayontology.org/bao#BAO_0002009";
		StoredRemapTo srt2 = new StoredRemapTo();
		srt2.fromURI = "http://www.bioassayontology.org/bao#BAO_0002009";
		srt2.toURI = "http://www.bioassayontology.org/bao#BAO_0000205";
		remappingsWithCycle.put(srt1.fromURI, srt1);
		remappingsWithCycle.put(srt2.fromURI, srt2);

		// bao:BAO_0000205 remapTo bao:BAO_0002009
		// bao:BAO_0002009 remapTo bao:BAO_0000205
		Assertions.assertThrows(RuntimeException.class, () -> AssayUtil.conformToRemap(assay, schema));
	}*/
	
	@Test
	public void testNumberFormats()
	{
		String[] goodIntegers = {"1", "-1", "10000"};
		String[] goodNumbers = {"10", "1", "1.0", "1.01", "0.01", "1E6", "9.9e3", "-1", "-10", "-0.3E-5", "10.123e12", "234.45e78"};
		String[] badIntegers = {"+123", "1.0", "1.01", "0.01", "1E6", "9.9e3", "-0.3E-5"};
		String[] badNumbers = {"+10.123", "1,000", "1,000,000", "10,01", "-9,99", "-1,1e3"};
		
		for (String literal : goodIntegers)
			assertTrue(AssayUtil.validIntegerLiteral(literal), "Good integer failed: " + literal);
		for (String literal : goodNumbers)
			assertTrue(AssayUtil.validNumberLiteral(literal), "Good number failed: " + literal);
		for (String literal : badIntegers)
			assertFalse(AssayUtil.validIntegerLiteral(literal), "Bad integer failed: " + literal);
		for (String literal : badNumbers)
			assertFalse(AssayUtil.validNumberLiteral(literal), "Bad number failed: " + literal);
		
		String[] standardNumbers = {"10", "-10", "0.01", "-0.01", "1e6", "-1e6"};
		String[] nonStandardNumbers = {"abc", "010", "-010", ".01", "01.01", "-.01", "-01.01", "-01e6", "01e6"};
		for (String literal: standardNumbers)
			assertTrue(AssayUtil.standardNumberLiteral(literal), "Standard number failed: " + literal);
		for (String literal: nonStandardNumbers)
			assertFalse(AssayUtil.standardNumberLiteral(literal), "Non-standard number failed: " + literal);
	}

	// ------------ private methods ------------

	private void mockAssignments(Schema schema, String[][] groupNests)
	{

		Schema.Assignment[] assnList = new Schema.Assignment[groupNests != null ? groupNests.length : 0];
		if (groupNests != null) for (int i = 0; i < groupNests.length; i++)
		{
			Schema.Assignment assn = mock(Schema.Assignment.class);
			when(assn.groupNest()).thenReturn(groupNests[i]);
			assnList[i] = assn;
		}
		when(schema.findAssignmentByProperty(anyString())).thenReturn(assnList);
	}

	private Assay getTestAssay() throws IOException
	{
		TestResourceFile resource = new TestResourceFile("/testData/data/assay_AID1621.json");
		JSONObject json = new JSONObject(new JSONTokener(resource.getAsStream()));
		Assay testAssay = AssayJSON.deserialiseAssay(json);
		return testAssay;
	}
	
	private Map<String, StoredRemapTo> getRemappings(Vocabulary vocab)
	{
		Map<String, StoredRemapTo> retval = new HashMap<>();

		Map<String, String> remappings = vocab.getRemappings();
		for (Map.Entry<String, String> entry : remappings.entrySet())
		{
			StoredRemapTo srt = new StoredRemapTo();
			srt.fromURI = entry.getKey();
			srt.toURI = entry.getValue();
			retval.put(srt.fromURI, srt);
		}

		return retval;
	}
}

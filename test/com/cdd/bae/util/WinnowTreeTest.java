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

public class WinnowTreeTest
{
	private static final String LABEL = "label";
	private static final String LABEL4 = "label4";
	private static final String LABEL3 = "label3";
	private static final String LABEL2 = "label2";
	private static final String LABEL1 = "label1";
	private static final String ANNOTATION5 = "annotation5";
	private static final String ANNOTATION4 = "annotation4";
	private static final String ANNOTATION3 = "annotation3";
	private static final String ANNOTATION2 = "annotation2";
	private static final String ANNOTATION1 = "annotation1";
//	private static final String LITERAL_LIST = "literalList";
//	private static final String TREE_LIST = "treeList";
	private static final String PROP_URI = "http://www.bioassayontology.org/bao#BAO_0000019";

	private Configuration configuration;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
	}

	@Test
	public void testSelectReducedTree()
	{
		Map<Long, DataStore.Assay> data = createData();
		SchemaTree.Node[] flat = new SchemaTree.Node[]{createNode(ANNOTATION3, -1), createNode(ANNOTATION5, 0), createNode(ANNOTATION4, 0)};
		WinnowTree.NodeResult[] results = WinnowTree.selectReducedTree(flat, PROP_URI, null, data, data);
		assertEquals(2, results.length);
		assertEquals(ANNOTATION3, results[0].uri);
		assertEquals(0, results[0].totalCount);
		assertEquals(ANNOTATION4, results[1].uri);
		assertEquals(4, results[1].totalCount);
	}

	@Test
	public void testGetValueCounts()
	{
		Map<Long, DataStore.Assay> data = createData();
		Map<String, Integer> counts = WinnowTree.getValueCounts(PROP_URI, null, data, true);
		assertEquals(3, counts.size());
		assertEquals(Integer.valueOf(1), counts.get(ANNOTATION1));
		assertEquals(Integer.valueOf(2), counts.get(ANNOTATION2));
		assertEquals(Integer.valueOf(4), counts.get(ANNOTATION4));
	}

	@Test
	public void testReduceDataKeywordsTextLabel()
	{
		Map<Long, DataStore.Assay> data = createData();
		Schema.Assignment[] assn = createAssnList();
		WinnowTree.reduceDataKeywords(data, assn, new KeywordMatcher(""));
		assertEquals(0, data.size());

		data = createData();
		WinnowTree.reduceDataKeywords(data, assn, new KeywordMatcher(LABEL1));
		assertEquals(3, data.size());

		data = createData();
		WinnowTree.reduceDataKeywords(data, assn, new KeywordMatcher(LABEL3));
		assertEquals(1, data.size());

		data = createData();
		WinnowTree.reduceDataKeywords(data, assn, new KeywordMatcher(LABEL));
		assertEquals(7, data.size());

		// no assignment list
		data = createData();
		WinnowTree.reduceDataKeywords(data, null, new KeywordMatcher(LABEL1));
		assertEquals(3, data.size());
	}

	@Test
	public void testReduceDataKeywords_Annotations()
	{
		Map<Long, DataStore.Assay> data = createData();
		Schema.Assignment[] assn = createAssnList();
		WinnowTree.reduceDataKeywords(data, assn, new KeywordMatcher(""));
		assertEquals(0, data.size());

		data = createData();
		WinnowTree.reduceDataKeywords(data, assn, new KeywordMatcher(ANNOTATION1));
		assertEquals(0, data.size()); // annotation1 gives label null

		/* these aren't actually valid; the match is supposed to be performed on the label, which doesn't exist because
		   the annotations are arbitrary text... ANNOTATION{x} was known to be in the schema tree somewhere, and the search was for
		   its corresponding label, then the test would work
		   
		data = createData();
		WinnowTree.reduceDataKeywords(data, assn, new KeywordMatcher(ANNOTATION2), schvoc);
		assertEquals(2, data.size());

		data = createData();
		WinnowTree.reduceDataKeywords(data, assn, new KeywordMatcher(ANNOTATION4), schvoc);
		assertEquals(4, data.size());*/
	}

	@Test
	public void testReduceDataFullText()
	{
		Map<Long, DataStore.Assay> data = createData();
		WinnowTree.reduceDataFullText(data, new KeywordMatcher(""));
		assertEquals(0, data.size());

		data = createData();
		WinnowTree.reduceDataFullText(data, new KeywordMatcher(LABEL1));
		assertEquals(3, data.size());

		data = createData();
		WinnowTree.reduceDataFullText(data, new KeywordMatcher(LABEL3));
		assertEquals(1, data.size());

		data = createData();
		WinnowTree.reduceDataFullText(data, new KeywordMatcher(LABEL));
		assertEquals(8, data.size());
	}

	@Test
	public void testReduceDataIdentifier()
	{
		Map<Long, DataStore.Assay> data = createData();
		WinnowTree.reduceDataIdentifier(data, new String[]{}, new KeywordMatcher(""));
		assertEquals(0, data.size());

		data = createData();
		WinnowTree.reduceDataIdentifier(data, new String[]{}, new KeywordMatcher("assay2"));
		assertEquals(1, data.size());

		data = createData();
		WinnowTree.reduceDataIdentifier(data, new String[]{}, new KeywordMatcher("assay"));
		assertEquals(7, data.size());

		data = createData();
		WinnowTree.reduceDataIdentifier(data, new String[]{"pubchemAID:"}, new KeywordMatcher(""));
		assertEquals(7, data.size());

		data = createData();
		WinnowTree.reduceDataIdentifier(data, new String[]{"otherAID:"}, new KeywordMatcher(""));
		assertEquals(0, data.size());
	}

	@Test
	public void testSelectCommonLiterals()
	{
		Map<Long, DataStore.Assay> data = createData();
		WinnowTree.LiteralResult[] results = WinnowTree.selectCommonLiterals(PROP_URI, null, data);
		assertEquals(3, results.length);
		assertEquals(3, results[0].count);
		assertEquals(LABEL1, results[0].label);
		assertEquals(3, results[1].count);
		assertEquals(LABEL2, results[1].label);
		assertEquals(1, results[2].count);
		assertEquals(LABEL3, results[2].label);
	}

	@Test
	public void testSelectIdentifiers()
	{
		Map<Long, DataStore.Assay> data = new HashMap<>();
		data.put(0L, createAssay("pubchemAID:assay0", null, null, null));
		data.put(1L, createAssay("pubchemAID:assay1", null, null, null));
		data.put(2L, createAssay("other:assay2", null, null, null));

		WinnowTree.NodeResult[] results = WinnowTree.selectIdentifiers(data);
		// System.out.println(arr.toString(3));
		assertEquals(1, results.length);
		assertEquals(2, results[0].count);
	}
	
	@Test
	public void testSelectWithText()
	{
		Map<Long, DataStore.Assay> data = new HashMap<>();
		final String PROP = ModelSchema.expandPrefix("bao:BAO_0002854");
		final String LABEL = "something", URI = ModelSchema.expandPrefix("bao:BAO_0000123");
		data.put(0L, createPartialAssay("pubchemAID:assay0", PROP, LABEL, URI));
		data.put(1L, createPartialAssay("pubchemAID:assay1", PROP, LABEL, null));
		data.put(2L, createPartialAssay("pubchemAID:assay2", PROP, null, URI));
		data.put(3L, createPartialAssay("pubchemAID:assay3", PROP + "z", LABEL, URI));
		
		WinnowTree.Layer layer = new WinnowTree.Layer();
		layer.propURI = PROP;
		layer.valueURIList = new String[]{WinnowTree.SPECIAL_VALUE_WITHTEXT};

		Map<Long, DataStore.Assay> subData = new HashMap<>(data);
		WinnowTree winnow = new WinnowTree(Common.getSchemaCAT(), new WinnowTree.Layer[]{layer}, false);
		winnow.reduceDataMap(subData);
		assertSetEquals(subData.keySet(), new long[]{0L, 1L});
		
		subData = new HashMap<>(data);
		layer.valueURIList = new String[]{WinnowTree.SPECIAL_VALUE_WITHTEXT, URI};
		winnow = new WinnowTree(Common.getSchemaCAT(), new WinnowTree.Layer[]{layer}, false);
		winnow.reduceDataMap(subData);
		assertSetEquals(subData.keySet(), new long[]{0L, 1L, 2L});
	}

	// ------------ private methods ------------

	private DataStore.Assay createAssay(String uniqueID, String label, String propURI, String annotation)
	{
		DataStore.Assay assay = new DataStore.Assay();
		assay.uniqueID = uniqueID;
		DataObject.TextLabel textLabel = new DataObject.TextLabel();
		textLabel.text = label;
		textLabel.propURI = propURI;
		assay.textLabels = new DataStore.TextLabel[]{textLabel};
		assay.text = label;
		assay.annotations = new DataStore.Annotation[]{new DataStore.Annotation(propURI, annotation)};
		assay.isCurated = true;
		return assay;
	}
	
	private DataStore.Assay createPartialAssay(String uniqueID, String propURI, String label, String valueURI)
	{
		DataStore.Assay assay = new DataStore.Assay();
		assay.uniqueID = uniqueID;
		
		if (label != null)
			assay.textLabels = new DataObject.TextLabel[]{new DataObject.TextLabel(propURI, label)};
		else
			assay.textLabels = new DataObject.TextLabel[0];
		
		if (valueURI != null)
			assay.annotations = new DataStore.Annotation[]{new DataStore.Annotation(propURI, valueURI)};
		else
			assay.annotations = new DataStore.Annotation[0];
		
		assay.isCurated = true;
		return assay;
	}

	private Map<Long, DataStore.Assay> createData()
	{
		Map<Long, DataStore.Assay> data = new HashMap<>();
		data.put((long)0, createAssay("pubchemAID:assay0", LABEL1, PROP_URI, ANNOTATION1));
		data.put((long)1, createAssay("pubchemAID:assay1", LABEL1, PROP_URI, ANNOTATION2));
		data.put((long)2, createAssay("pubchemAID:assay2", LABEL2, PROP_URI, ANNOTATION2));
		data.put((long)3, createAssay("pubchemAID:assay3", LABEL2, PROP_URI, ANNOTATION4));
		data.put((long)4, createAssay("pubchemAID:assay4", LABEL1, PROP_URI, ANNOTATION4));
		data.put((long)5, createAssay("pubchemAID:assay5", LABEL3, PROP_URI, ANNOTATION4));
		data.put((long)6, createAssay("pubchemAID:assay6", LABEL2, PROP_URI, ANNOTATION4));
		data.put((long)7, createAssay("other:assay6", LABEL4, "Another propURI", null));
		return data;
	}

	private Schema.Assignment[] createAssnList()
	{
		Schema.Group parent = new Schema.Group(null, "parent");
		Schema.Assignment[] assnList = new Schema.Assignment[2];
		assnList[0] = new Schema.Assignment(parent, "name1", PROP_URI);
		assnList[1] = new Schema.Assignment(parent, "name2", "property2");
		return assnList;
	}

	private static SchemaTree.Node createNode(String uri, int parentIndex)
	{
		SchemaTree.Node node = new SchemaTree.Node();
		node.uri = uri;
		node.parentIndex = parentIndex;
		return node;
	}
	
	private void assertSetEquals(Set<Long> got, long[] want)
	{
		String msg = "Want [" + Util.arrayStr(want) + "], got " + got;
		assertTrue(got.size() == want.length, msg);
		for (long w : want) assertTrue(got.contains(w), msg);
	}
}

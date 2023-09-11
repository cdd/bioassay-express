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

package com.cdd.bae.rest;

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;

import org.json.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

/*
	Test for SelectionTree REST API.
*/

public class SelectionTreeTest
{
	private static final String ANNOTATION1 = "annotation1";
	private static final String LITERAL_LIST = "literalList";
	private static final String TREE_LIST = "treeList";
	private Configuration configuration;
	private SelectionTree selectionTree;
	private SchemaVocab oldSchemaVocab;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		/* !!
		oldSchemaVocab = configuration.getSchemaVocab();
		SchemaVocab schemaVocab = Mockito.spy(Common.getSchemaVocab());
		when(schemaVocab.getLabel(any())).then(new SchemaVocabGetLabel());
		TestConfiguration.setSchemaVocab(schemaVocab);*/

		selectionTree = new SelectionTree();
		selectionTree.logger = TestUtilities.mockLogger();
	}

	@AfterEach
	public void restore()
	{
		//TestConfiguration.setSchemaVocab(oldSchemaVocab);
	}

	@Test
	public void testPerformSelectionSpecialCases()
	{
		Templates templates = mock(Templates.class);
		when(templates.getSchema(anyString())).thenReturn(Common.getSchemaCAT());

		Schema schema = Common.getSchema(null);

		// test special case KEYWORD
		JSONArray select = new JSONArray();
		select.put(new JSONObject("{\"valueURIList\":[],\"keywordSelector\":\"\",\"propURI\":\"KEYWORD\"}"));
		JSONObject result = selectionTree.performSelection(schema, select, false);
		assertEquals(0, result.getJSONArray(TREE_LIST).getJSONArray(0).length());
		assertEquals(0, result.getJSONArray(LITERAL_LIST).getJSONArray(0).length());

		// test special case FULLTEXT
		select = new JSONArray();
		select.put(new JSONObject("{\"valueURIList\":[],\"keywordSelector\":\"assay\",\"propURI\":\"FULLTEXT\"}"));
		result = selectionTree.performSelection(schema, select, false);
		assertEquals(0, result.getJSONArray(TREE_LIST).getJSONArray(0).length());
		assertEquals(0, result.getJSONArray(LITERAL_LIST).getJSONArray(0).length());

		// test special case FULLTEXT
		select = new JSONArray();
		select.put(new JSONObject("{\"valueURIList\":[],\"keywordSelector\":\"\",\"propURI\":\"IDENTIFIER\"}"));
		result = selectionTree.performSelection(schema, select, false);
		assertEquals(1, result.getJSONArray(TREE_LIST).getJSONArray(0).length());
		assertEquals(0, result.getJSONArray(LITERAL_LIST).getJSONArray(0).length());

		// TODO: test the selection using withUncurated=true
	}

	// ------------ private methods ------------
	
	protected static class SchemaVocabGetLabel implements Answer<String>
	{
		@Override
		public String answer(InvocationOnMock invocation) throws Throwable
		{
			if (invocation.getArgument(0) == null) return null;
			String uri = (String)invocation.getArgument(0);
			if (uri.equals(ANNOTATION1)) return null;
			return uri;
		}
	}
}

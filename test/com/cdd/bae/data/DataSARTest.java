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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

/*
	Test for com.cdd.bae.data.DataSAR
	
	!!! DEPRECATE: the class being tested is flagged for deletion
*/

public class DataSARTest
{
	@Test
	public void testDummy()
	{
		// require a test method
		assertTrue(true);
	}
	/*private static final String CODE_1 = "code-1";
	private static final String CODE_2 = "code-2";
	DataStore store;
	DataSAR dataSAR;

	private MongoCollection<Document> mockCollection;

	@Before
	public void initialize()
	{
		store = mock(DataStore.class);
		store.db = mockMongoDB();

		dataSAR = new DataSAR(store);
	}

	@Test
	public void testListModels()
	{
		new MockCursorBuilder(mockCollection).withQuery().usingInto(makeDocument(1), makeDocument(2));
		Map<String, String> result = dataSAR.listModels();
		assertEquals(2, result.size());
		assertTrue(result.containsKey(CODE_1));
		assertTrue(result.containsKey(CODE_2));
	}

	@Test
	public void testGetModel()
	{
		new MockCursorBuilder(mockCollection).withQuery(new Document(FLD_SARMODEL_CODE, CODE_1)).usingInto(makeDocument(1));
		new MockCursorBuilder(mockCollection).withQuery(new Document(FLD_SARMODEL_CODE, CODE_2)).usingInto();
		SARModel model = dataSAR.getModel(CODE_1);
		assertEquals(CODE_1, model.code);
		assertNull("no model found", dataSAR.getModel(CODE_2));
	}

	@Test
	public void testSetModel()
	{
		new MockCursorBuilder(mockCollection).withQuery(new Document(FLD_SARMODEL_CODE, CODE_1)).usingInto(makeDocument(1));
		SARModel model = dataSAR.getModel(CODE_1);
		assertNotNull(model);

		dataSAR.setModel(model);
		verify(mockCollection, times(1)).replaceOne(any(), any(), any());
	}

	// ------------ private methods ------------

	@SuppressWarnings("unchecked")
	private MongoDatabase mockMongoDB()
	{
		MongoDatabase mongoDB = mock(MongoDatabase.class);
		mockCollection = mock(MongoCollection.class);
		when(mongoDB.getCollection(COLL_SARMODEL)).thenReturn(mockCollection);
		return mongoDB;
	}

	private Document makeDocument(int index)
	{
		return new Document("_id", new ObjectId("579397d20c2dd41b9a8a09eb"))
				.append(FLD_SARMODEL_WATERMARK, (long)1234)
				.append(FLD_SARMODEL_CODE, "code-" + index)
				.append(FLD_SARMODEL_DESCRIPTION, "description-" + index)
				.append(FLD_SARMODEL_ANNOTATIONS, makeAnnotationList(3))
				.append(FLD_SARMODEL_FPLIST, makeList(5, 1))
				.append(FLD_SARMODEL_CONTRIBS, makeList(5, 1.0))
				.append(FLD_SARMODEL_ASSAYCONTRIBS, makeList(5, 1.0))
				.append(FLD_SARMODEL_ASSAYACTIVES, makeList(5, 1))
				.append(FLD_SARMODEL_ASSAYCOUNTS, makeList(5, 1))
				.append(FLD_SARMODEL_CALIBRATION, makeList(2, 10.0))
				.append(FLD_SARMODEL_ROCAUC, 0.78)
				.append(FLD_SARMODEL_ROCX, makeList(4, 1.0))
				.append(FLD_SARMODEL_ROCY, makeList(4, 2.0))
				.append(FLD_SARMODEL_NUMASSAYS, 10)
				.append(FLD_SARMODEL_NUMCOMPOUNDS, 10)
				.append(FLD_SARMODEL_NUMACTIVES, 10)
				.append(FLD_SARMODEL_NUMINACTIVES, 10);
	}*/
}

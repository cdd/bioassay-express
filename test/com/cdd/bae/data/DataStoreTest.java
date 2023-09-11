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

import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

/*
	Test for com.cdd.bae.data.DataStore
*/

public class DataStoreTest
{
	@BeforeEach
	public void initialize()
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Common.setDataStore(mongo.getDataStore());
		Common.setDataStore(mongo.getDataStore());
	}

	@Test
	public void testAnnotation()
	{
		assertEquals("[null,null]", new DataStore.Annotation().toString());
		assertEquals("[propURI,valueURI]", new DataStore.Annotation("propURI", "valueURI").toString());
		assertEquals("[propURI,valueURI,a,b]", new DataStore.Annotation("propURI", "valueURI", new String[]{"a", "b"}).toString());

		DataStore.Annotation annot = new DataStore.Annotation();
		assertEqualAnnotations(annot, annot.clone());
		annot = new DataStore.Annotation("propURI", "valueURI");
		assertEqualAnnotations(annot, annot.clone());
		annot = new DataStore.Annotation("propURI", "valueURI", new String[]{"a", "b"});
		assertEqualAnnotations(annot, annot.clone());

		// test the AnnotationFP variant
		DataStore.AnnotationFP annotFP = new DataStore.AnnotationFP();
		assertEquals("[null,null]", annotFP.toString());
		assertEqualAnnotations(new DataStore.Annotation(), annotFP);

		annotFP = new DataStore.AnnotationFP("propURI", "valueURI", 123);
		assertEquals("[propURI,valueURI]", annotFP.toString());
		assertEqualAnnotations(new DataStore.Annotation("propURI", "valueURI"), annotFP);
	}

	@Test
	public void testTextLabel()
	{
		assertEquals("[null,\"null\"]", new DataStore.TextLabel().toString());
		assertEquals("[propURI,\"text\"]", new DataStore.TextLabel("propURI", "text").toString());
		assertEquals("[propURI,\"text\",a,b]", new DataStore.TextLabel("propURI", "text", new String[]{"a", "b"}).toString());

		DataStore.TextLabel label = new DataStore.TextLabel();
		assertEqualTextLabel(label, label.clone());
		label = new DataStore.TextLabel("propURI", "text");
		assertEqualTextLabel(label, label.clone());
		label = new DataStore.TextLabel("propURI", "text", new String[]{"a", "b"});
		assertEqualTextLabel(label, label.clone());

		label = new DataStore.TextLabel("propURI", "text", new String[]{"a", "b"});
		assertTrue(label.matchesProperty("propURI", new String[]{"a", "b"}));
		assertTrue(label.matchesProperty("propURI", new String[]{"a", "b", "c"}), "also matches larger groupnest");
		assertTrue(label.matchesProperty("propURI", new String[]{"a"}), "and shorter groupnest");

		// and now the cases where it is not matching
		assertFalse(label.matchesProperty("otherURI", new String[]{"a", "b"}));
		assertFalse(label.matchesProperty("propURI", new String[]{"a", "other"}));
		assertFalse(label.matchesProperty("propURI", new String[]{"other", "b"}));
	}

	@Test
	public void testSequenceMethods()
	{
		DataStore store = Common.getDataStore();
		long seqIDAssay = store.getSequence(DataStore.SEQ_ID_ASSAY);
		assertThat(store.getSequence(DataStore.SEQ_ID_ASSAY), is(seqIDAssay));
		
		// getNextSequence returns the previous value, but increments by one
		assertThat(store.getNextSequence(DataStore.SEQ_ID_ASSAY), is(seqIDAssay));
		assertThat(store.getSequence(DataStore.SEQ_ID_ASSAY), is(seqIDAssay + 1));
		
		// handles new sequences not well - sequences must be added in setupSequences
		String newSeqID = "new-sequence-id";
		assertThat(store.getSequence(newSeqID), is(0L));
		assertThrows(NullPointerException.class, () -> store.getNextSequence(newSeqID));
	}

	// ------------ private methods ------------

	private void assertEqualAnnotations(DataStore.Annotation annot1, DataStore.Annotation annot2)
	{
		assertEquals(annot1.propURI, annot2.propURI);
		assertEquals(annot1.valueURI, annot2.valueURI);
		assertArrayEquals(annot1.groupNest, annot2.groupNest);
	}

	private void assertEqualTextLabel(DataStore.TextLabel annot1, DataStore.TextLabel annot2)
	{
		assertEquals(annot1.propURI, annot2.propURI);
		assertEquals(annot1.text, annot2.text);
		assertArrayEquals(annot1.groupNest, annot2.groupNest);
	}
}

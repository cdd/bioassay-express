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
import com.cdd.bae.data.DataStore.*;
import com.cdd.bae.util.*;
import com.cdd.bae.util.diff.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.bson.*;
import org.junit.jupiter.api.*;

/*
	Test for com.cdd.bae.data.DataAssay
*/

public class DataAssayTest
{
	private static final String SCHEMA_URI = "http://www.bioassayontology.org/bas#";

	private DataStore store;

	DataAssay dataAssay;
	Notifier notifier;

	@BeforeEach
	public void initialize() throws ConfigurationException, IOException
	{
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);

		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		store = mongo.getDataStore();
		notifier = mock(Notifier.class);
		store.setNotifier(notifier);
		dataAssay = new DataAssay(store);
	}

	@Test
	public void testCountAssays()
	{
		assertThat(dataAssay.countAssays(), is(7));
	}

	@Test
	public void testGetWatermark() throws IOException
	{
		assertThat(dataAssay.getWatermark(), is(100000L));
		assertThat(dataAssay.getWatermark(), is(100000L));
		assertThat(dataAssay.getWatermark(), is(100000L));
		
		assertThat(dataAssay.nextWatermark(), is(100000L)); // returns the old watermark
		assertThat(dataAssay.getWatermark(), is(100001L));
	}

	@Test
	public void testGetAssayMethods()
	{
		Assay assay = dataAssay.getAssay(2);
		assertThat(assay.assayID, is(2L));
		assertThat(dataAssay.getAssay(2222), is(nullValue()));

		assay = dataAssay.getAssayFromUniqueID(assay.uniqueID);
		assertThat(assay.assayID, is(2L));
		assertThat(dataAssay.getAssayFromUniqueID("unknown"), is(nullValue()));

	}

	@Test
	public void testFetchAssayIDMethods()
	{
		assertArrays(dataAssay.fetchAllAssayID(), new long[]{2, 101, 102, 103, 104, 105, 106});
		assertArrays(dataAssay.fetchAllCuratedAssayID(), new long[]{2, 102, 103, 104, 105, 106});
		assertArrays(dataAssay.fetchAllNonCuratedAssayID(), new long[]{101});
	}

	@Test
	public void testFetchUniqueIDMethods()
	{
		assertArrays(dataAssay.fetchAllUniqueID(), new String[]{"pubchemAID:1020", "pubchemAID:101", "pubchemAID:102", "pubchemAID:103", "pubchemAID:104", "pubchemAID:105", "pubchemAID:106"});
		assertArrays(dataAssay.fetchAllCuratedUniqueID(), new String[]{"pubchemAID:1020", "pubchemAID:102", "pubchemAID:103", "pubchemAID:104", "pubchemAID:105", "pubchemAID:106"});
		assertArrays(dataAssay.fetchAllNonCuratedUniqueID(), new String[]{"pubchemAID:101"});
	}

	@Test
	public void testFetchRecentlyCurated()
	{
		assertThat(dataAssay.fetchRecentlyCurated(2), is(new long[]{104, 102}));
		assertThat(dataAssay.fetchRecentlyCurated(3), is(new long[]{104, 102, 2}));

		assertThat(dataAssay.fetchRecentlyCurated(0), is(new long[]{104, 102, 2, 103, 105, 106}));
	}

	@Test
	public void testFetchAssayIDWithoutFP()
	{
		assertArrays(dataAssay.fetchAssayIDWithoutFP(), new long[]{101, 102, 103, 104, 105, 106});
	}

	@Test
	public void testFetchAssayIDWithSchemaCurated()
	{
		assertArrays(dataAssay.fetchAssayIDWithSchemaCurated(SCHEMA_URI), new long[]{2, 102, 103, 104, 105, 106});
	}

	@Test
	public void testFetchAssayIDWithFPSchema()
	{
		assertThat(dataAssay.fetchAssayIDWithFPSchema(SCHEMA_URI), is(new long[]{2}));
	}

	@Test
	public void testFetchAssayIDWithAnnotationsSchema()
	{
		assertArrays(dataAssay.fetchAssayIDWithAnnotationsSchema(SCHEMA_URI), new long[]{2, 101, 104, 105, 106});
	}

	@Test
	public void testFetchAssayIDCuratedWithAnnotations()
	{
		assertArrays(dataAssay.fetchAssayIDCuratedWithAnnotations(), new long[]{2, 104, 105, 106});
	}

	@Test
	public void testFetchAssayIDWithAnnotations()
	{
		assertArrays(dataAssay.fetchAssayIDWithAnnotations(), new long[]{2, 101, 104, 105, 106});
	}
	
	@Test
	public void testFetchCuratedAssayIDWithAnnotation()
	{
		Set<String> valueURIs = new HashSet<>();
		Map<String, Set<Long>> expected = new HashMap<>();
		for (long assayID : dataAssay.fetchAllCuratedAssayID())
		{
			for (DataObject.Annotation annot : dataAssay.getAssay(assayID).annotations)
			{
				valueURIs.add(annot.valueURI);
				Set<Long> assayIDs = expected.computeIfAbsent(annot.valueURI, k -> new HashSet<>());
				assayIDs.add(assayID);
				expected.put(annot.valueURI, assayIDs);
			}
		}

		for (String valueURI : valueURIs)
		{
			long[] assayIDs = dataAssay.fetchCuratedAssayIDWithAnnotation(valueURI);
			assertArrays(assayIDs, Util.primLong(expected.get(valueURI)), valueURI);
		}
	}

	@Test
	public void testFetchAssayIDWithoutAnnotations()
	{
		assertArrays(dataAssay.fetchAssayIDWithoutAnnotations(), new long[]{102, 103});
	}

	@Test
	public void testFetchAssayIDCurated()
	{
		assertArrays(dataAssay.fetchAssayIDCurated(), new long[]{2, 102, 103, 104, 105, 106});
	}

	@Test
	public void testFetchCurationTimes()
	{
		long[] ids = dataAssay.fetchAllCuratedAssayID();
		long[] times = dataAssay.fetchCurationTimes(ids);

		assertThat(times, is(new long[]{1464345154000L, 1464345154002L, 1464345153998L, 1464345154003L, 1464345153970L, 1464345153960L}));
	}

	@Test
	public void testAssayIDFromUniqueID()
	{
		long[][] result = dataAssay.assayIDFromUniqueID(new String[]{"pubchemAID:1020", "pubchemAID:101"});
		assertThat(result.length, is(2));
		assertThat(result[0], is(new long[]{2L}));
		assertThat(result[1], is(new long[]{101L}));
	}

	@Test
	public void testAssayIDFromUniqueIDRegex()
	{
		long[] result = dataAssay.assayIDFromUniqueIDRegex("pubchemAID:\\d+");
		assertArrays(result, new long[]{2, 101, 102, 103, 104, 105, 106});

		result = dataAssay.assayIDFromUniqueIDRegex("unknown:\\d+");
		assertThat(result, is(new long[0]));
	}

	@Test
	public void testUniqueIDFromAssayID()
	{
		String[] result = dataAssay.uniqueIDFromAssayID(new long[]{2L, 101L});
		assertArrays(result, new String[]{"pubchemAID:1020", "pubchemAID:101"});
	}

	@Test
	public void testDeleteAssay() throws IOException
	{
		int prevCount = dataAssay.countAssays();
		boolean flag = dataAssay.deleteAssay(101);
		assertThat(flag, is(true));
		assertThat(dataAssay.countAssays(), is(prevCount - 1));
		assertArrays(dataAssay.fetchAllAssayID(), new long[]{2, 102, 103, 104, 105, 106});

		flag = dataAssay.deleteAssay(101);
		assertThat(flag, is(false));

		verify(store.notifier, times(1)).datastoreTextChanged();
		verify(store.notifier, times(1)).datastoreAnnotationsChanged();
		verify(store.notifier, times(1)).datastoreFingerprintsChanged();
	}

	@Test
	public void testSetAssay() throws IOException
	{
		// create information for a new assay
		Assay assay = dataAssay.getAssay(2);
		assay.assayID = 0;

		int prevCount = dataAssay.countAssays();
		dataAssay.setAssay(assay);
		assertThat(dataAssay.countAssays(), is(prevCount + 1));
		assertArrays(dataAssay.fetchAllAssayID(), new long[]{2, 101, 102, 103, 104, 105, 106, 100000L});

		verify(store.notifier, times(1)).datastoreTextChanged();
		verify(store.notifier, times(1)).datastoreAnnotationsChanged();

		// old assay is replaced if new assay has the same ID
		dataAssay.setAssay(assay);
		assertThat(dataAssay.countAssays(), is(prevCount + 1));
		assertArrays(dataAssay.fetchAllAssayID(), new long[]{2, 101, 102, 103, 104, 105, 106, 100000L});

		verify(store.notifier, times(2)).datastoreTextChanged();
		verify(store.notifier, times(2)).datastoreAnnotationsChanged();
	}

	@Test
	public void testSubmitAssay() throws IOException
	{
		// create a new assay
		Assay assay = dataAssay.getAssay(2);
		assay.assayID = 0;

		int prevCount = dataAssay.countAssays();
		dataAssay.submitAssay(assay);
		assertThat(dataAssay.countAssays(), is(prevCount + 1));
		assertArrays(dataAssay.fetchAllAssayID(), new long[]{2, 101, 102, 103, 104, 105, 106, 100000L});
		verify(store.notifier, times(1)).datastoreTextChanged();
		verify(store.notifier, times(1)).datastoreAnnotationsChanged();
		verify(store.notifier, times(1)).datastoreMeasurementsChanged();

		Assay newAssay = dataAssay.getAssay(100000L);
		assertThat(newAssay.assayID, is(100000L));
		assertThat(newAssay.uniqueID, is("pubchemAID:1020"));
		dataAssay.submitAssay(newAssay);
		assertThat(dataAssay.countAssays(), is(prevCount + 1));

		verify(store.notifier, times(2)).datastoreTextChanged();
		verify(store.notifier, times(2)).datastoreAnnotationsChanged();
		verify(store.notifier, times(2)).datastoreMeasurementsChanged();
	}

	@Test
	public void testSubmitAssayFingerprints() throws IOException
	{
		Assay assay = dataAssay.getAssay(2);
		assertThat(assay.fplist.length, is(399));

		dataAssay.submitAssayFingerprints(2, new int[]{1, 2, 3});

		assay = dataAssay.getAssay(2);
		assertArrays(assay.fplist, new int[]{1, 2, 3});

		verify(store.notifier, times(1)).datastoreFingerprintsChanged();
	}

	@Test
	public void testClearAssayFingerprints() throws IOException
	{
		Assay assay = dataAssay.getAssay(2);
		assertThat(assay.fplist.length, is(399));

		dataAssay.clearAssayFingerprints(2);
		assay = dataAssay.getAssay(2);
		assertArrays(assay.fplist, new int[0]);
		verify(store.notifier, times(1)).datastoreFingerprintsChanged();
	}

	@Test
	public void testSubmitAssayAnnotations() throws IOException
	{
		Assay assay = dataAssay.getAssay(2);
		assertThat(assay.annotations.length, is(20));

		dataAssay.submitAssayAnnotations(2, new Annotation[]{makeAnnotation()});
		assay = dataAssay.getAssay(2);
		assertThat(assay.annotations.length, is(1));
		verify(store.notifier, times(1)).datastoreAnnotationsChanged();

		dataAssay.submitAssayAnnotations(2, null);
		assay = dataAssay.getAssay(2);
		assertThat(assay.annotations.length, is(0));
		verify(store.notifier, times(2)).datastoreAnnotationsChanged();
	}

	@Test
	public void testSubmitAssayPubChemAnnotations() throws IOException
	{
		Assay assay = dataAssay.getAssay(2);
		assertThat(assay.annotations.length, is(20));

		dataAssay.submitAssayPubChemAnnotations(1020, new Annotation[]{makeAnnotation()});
		assay = dataAssay.getAssay(2);
		assertThat(assay.annotations.length, is(1));
		verify(store.notifier, times(1)).datastoreAnnotationsChanged();

		dataAssay.submitAssayPubChemAnnotations(1020, null);
		assay = dataAssay.getAssay(2);
		assertThat(assay.annotations.length, is(0));
		verify(store.notifier, times(2)).datastoreAnnotationsChanged();
	}

	@Test
	public void testReplaceAssayText() throws IOException
	{
		Assay assay = dataAssay.getAssay(2);
		assertThat(assay.text, containsString("Counter Screen"));
		assertThat(assay.annotations.length, is(20));

		dataAssay.replaceAssayText(1020, "abc");
		assay = dataAssay.getAssay(2);
		assertThat(assay.text, is("abc"));
		verify(store.notifier, times(1)).datastoreTextChanged();
	}

	@Test
	public void testSubmitAssaySchema() throws IOException
	{
		Assay assay = dataAssay.getAssay(2);
		assertThat(assay.schemaURI, is(SCHEMA_URI));

		dataAssay.submitAssaySchema(2, SCHEMA_URI); // need to use a known schema here as getAssay does validation
		assay = dataAssay.getAssay(2);
		assertThat(assay.schemaURI, is(SCHEMA_URI));
		verify(store.notifier, times(1)).datastoreAnnotationsChanged();
	}

	@Test
	public void testFetchAssayIDHaveMeasure()
	{
		assertArrays(dataAssay.fetchAssayIDHaveMeasure(), new long[]{2});
	}

	@Test
	public void testFetchAssayIDNeedMeasure()
	{
		assertArrays(dataAssay.fetchAssayIDNeedMeasure(), new long[]{101, 102, 103, 104, 105, 106});
	}

	@Test
	public void testSubmitPubChemAIDMeasured() throws IOException
	{
		Assay assay = dataAssay.getAssay(2);
		assertThat(assay.measureChecked, is(true));

		dataAssay.submitPubChemAIDMeasured(1020, false);
		assay = dataAssay.getAssay(2);
		assertThat(assay.measureChecked, is(false));
	}

	@Test
	public void testSubmitIsCurated() throws IOException
	{
		Assay assay = dataAssay.getAssay(2);
		assertThat(assay.isCurated, is(true));

		dataAssay.submitIsCurated(2, false);
		assay = dataAssay.getAssay(2);
		assertThat(assay.isCurated, is(false));
	}

	@Test
	public void testSubmitMeasureState() throws IOException
	{
		Assay assay = dataAssay.getAssay(2);
		assertThat(assay.measureState, is(nullValue()));

		dataAssay.submitMeasureState(2, "new");
		assay = dataAssay.getAssay(2);
		assertThat(assay.measureState, is("new"));
	}

	@Test
	public void testPatchHistory()
	{
		final String UNIQUEID1 = "pubchemAID:123", UNIQUEID2 = "pubchemAID:456";
		final String TEXT1 = "Oh freddled gruntbuggly,\n" +
				"Thy micturations are to me,\n" +
				"As plurdled gabbleblotchits,\n" +
				"On a lurgid bee.";
		final String TEXT2 = "That mordiously hath blurted out,\n" +
				"Its earted jurtles, grumbling\n" +
				"Or else I shall rend thee in the gobberwarts with my blurglecruncheon,\n" +
				"See if I don't!";

		Assay oldAssay = new Assay();
		oldAssay.uniqueID = UNIQUEID1;
		oldAssay.text = TEXT1;

		Assay newAssay = new Assay();
		newAssay.uniqueID = UNIQUEID2;
		newAssay.text = TEXT2;

		Document doc = dataAssay.submitAssayDoc(newAssay, oldAssay);
		Assay assay = DataAssay.assayFromDoc(doc);

		assertNotNull(assay);
		assertEquals(UNIQUEID2, assay.uniqueID);
		assertEquals(TEXT2, assay.text);
		assertEquals(1, assay.history.length);

		History h = assay.history[0];

		assertNotNull(h.uniqueIDPatch);
		LinkedList<DiffMatchPatch.Patch> patchID = DiffMatchPatch.patchFromText(h.uniqueIDPatch);
		String mappedID = DiffMatchPatch.patchApplyText(patchID, assay.uniqueID);
		assertEquals(oldAssay.uniqueID, mappedID);

		assertNotNull(h.textPatch);
		LinkedList<DiffMatchPatch.Patch> patchText = DiffMatchPatch.patchFromText(h.textPatch);
		String mappedText = DiffMatchPatch.patchApplyText(patchText, assay.text);
		assertEquals(oldAssay.text, mappedText);
	}
	
	@Test
	public void testFetchAssayIDRecentCuration()
	{
		List<DataAssay.UserCuration> result = dataAssay.fetchAssayIDRecentCuration("fnord");
		assertThat(result, hasSize(2));
		assertThat(result.get(0).assayID, is(2L));
		assertThat(result.get(1).assayID, is(102L));
		assertThat(result.get(0).curationTime, greaterThan(result.get(1).curationTime));

		result = dataAssay.fetchAssayIDRecentCuration("dronf");
		assertThat(result, hasSize(1));
		assertThat(result.get(0).assayID, is(102L));
	}

	// ------------ private methods ------------

	private <T> void assertArrays(T[] actual, T[] expected)
	{
		Arrays.sort(actual);
		Arrays.sort(expected);
		assertThat(actual, is(expected));
	}

	private void assertArrays(long[] actual, long[] expected)
	{
		Arrays.sort(actual);
		Arrays.sort(expected);
		assertThat(actual, is(expected));
	}

	private void assertArrays(long[] actual, long[] expected, String reason)
	{
		Arrays.sort(actual);
		Arrays.sort(expected);
		assertThat(reason, actual, is(expected));
	}

	private void assertArrays(int[] actual, int[] expected)
	{
		Arrays.sort(actual);
		Arrays.sort(expected);
		assertThat(actual, is(expected));
	}

	private Annotation makeAnnotation()
	{
		Annotation obj = new Annotation();
		obj.propURI = "propURI";
		obj.valueURI = "valueURI";
		obj.groupNest = new String[]{"a", "b"};
		return obj;
	}
}

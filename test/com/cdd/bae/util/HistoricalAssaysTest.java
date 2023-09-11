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
import com.cdd.bae.data.DataObject.*;
import com.cdd.bao.template.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.junit.jupiter.api.*;

/*
	Tests for HistoricalAssays
*/

public class HistoricalAssaysTest
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
	public void testAssayHistory()
	{
		Date[] dates = new Date[]
		{
			new GregorianCalendar(2017, 3, 1).getTime(),
			new GregorianCalendar(2017, 9, 1).getTime(),
			new GregorianCalendar(2018, 0, 1).getTime(),
			new GregorianCalendar(2018, 5, 1).getTime(),
			new Date()
		};
		int ndates = dates.length;
		
		Schema schema = Common.getSchemaCAT();
		Schema.Assignment[] assnList = schema.getRoot().flattenedAssignments();
		Annotation[][] stateAnnot = new Annotation[ndates][];
		TextLabel[][] stateLabel = new TextLabel[ndates][];
		
		Assay assay = new Assay();
		assay.history = new History[ndates];
		for (int n = 0; n < ndates; n++)
		{
			History h = new History();
			h.curationTime = dates[n];
			h.annotsAdded = new Annotation[]{new Annotation(assnList[n].propURI, "KEEP-" + n)};
			h.labelsAdded = new TextLabel[]{new TextLabel(assnList[n].propURI, "keep-" + n)};
			if (n < ndates - 1)
			{
				h.annotsAdded = ArrayUtils.add(h.annotsAdded, new Annotation(assnList[n].propURI, "DROP-" + n));
				h.labelsAdded = ArrayUtils.add(h.labelsAdded, new TextLabel(assnList[n].propURI, "drop-" + n));
			}
			h.annotsRemoved = n == 0 ? new Annotation[0] : new Annotation[]{new Annotation(assnList[n - 1].propURI, "DROP-" + (n - 1))};
			h.labelsRemoved = n == 0 ? new TextLabel[0] : new TextLabel[]{new TextLabel(assnList[n - 1].propURI, "drop-" + (n - 1))};
			
			stateAnnot[n] = applyAnnots(n == 0 ? new Annotation[0] : stateAnnot[n - 1], h.annotsAdded, h.annotsRemoved);
			stateLabel[n] = applyLabels(n == 0 ? new TextLabel[0] : stateLabel[n - 1], h.labelsAdded, h.labelsRemoved);
			assay.history[n] = h;
		}
		assay.curationTime = dates[ndates - 1];
		assay.annotations = stateAnnot[ndates - 1];
		assay.textLabels = stateLabel[ndates - 1];
		
		/*for (int n = 0; n < ndates; n++)
		{
			Util.writeln("HISTORY#" + n);
			Util.writeln("  added  : " + Util.arrayStr(assay.history[n].annotsAdded));
			Util.writeln("         : " + Util.arrayStr(assay.history[n].labelsAdded));
			Util.writeln("  removed: " + Util.arrayStr(assay.history[n].annotsRemoved));
			Util.writeln("         : " + Util.arrayStr(assay.history[n].labelsRemoved));
			Util.writeln("  date:" + assay.history[n].curationTime + "/wholeday=" + HistoricalAssays.wholeDay(assay.history[n].curationTime));
				
			Util.writeln("SLICE#" + n + "/sizes:" + stateAnnot[n].length + ":" + stateLabel[n].length);
			Util.writeln("  annots: " + Util.arrayStr(stateAnnot[n]));
			Util.writeln("  labels: " + Util.arrayStr(stateLabel[n]));
		}*/
		
		// sanity check on the answer key
		assertEquals(stateAnnot[0].length, 2);
		assertEquals(stateLabel[0].length, 2);
		assertEquals(stateAnnot[1].length, 3);
		assertEquals(stateLabel[1].length, 3);
		assertEquals(stateAnnot[2].length, 4);
		assertEquals(stateLabel[2].length, 4);
		assertEquals(stateAnnot[3].length, 5);
		assertEquals(stateLabel[3].length, 5);
		assertEquals(stateAnnot[4].length, 5);
		assertEquals(stateLabel[4].length, 5);
		
		for (int n = 0; n < ndates; n++)
		{
			long dayTick = HistoricalAssays.wholeDay(dates[n]);
		
			Assay capsule = HistoricalAssays.timeCapsule(assay, dayTick - 1); // go 1 millisecond into the previous day
			Annotation[] wantAnnots = n == 0 ? new Annotation[0] : stateAnnot[n - 1];
			TextLabel[] wantLabels = n == 0 ? new TextLabel[0] : stateLabel[n - 1];
			requireSameAnnotations(capsule, wantAnnots, wantLabels, "time " + n + "/pre");
		
			capsule = HistoricalAssays.timeCapsule(assay, dayTick); // exactly this moment
			requireSameAnnotations(capsule, stateAnnot[n], stateLabel[n], "time " + n + "/actual");		

			capsule = HistoricalAssays.timeCapsule(assay, dayTick + 1); // slightly past this moment
			requireSameAnnotations(capsule, stateAnnot[n], stateLabel[n], "time " + n + "/post");		
		}
	}

	// compose a new array with the additions/deletions applied to it
	private Annotation[] applyAnnots(Annotation[] initial, Annotation[] added, Annotation[] removed)
	{
		Annotation[] annots = ArrayUtils.addAll(initial, added);
		for (Annotation look : removed)
		{
			for (int n = annots.length - 1; n >= 0; n--) if (annots[n].propURI.equals(look.propURI) && annots[n].valueURI.equals(look.valueURI))
				annots = ArrayUtils.remove(annots, n);
		}
		return annots;
	}
	private TextLabel[] applyLabels(TextLabel[] initial, TextLabel[] added, TextLabel[] removed)
	{
		TextLabel[] labels = ArrayUtils.addAll(initial, added);
		for (TextLabel look : removed)
		{
			for (int n = labels.length - 1; n >= 0; n--) if (labels[n].propURI.equals(look.propURI) && labels[n].text.equals(look.text))
				labels = ArrayUtils.remove(labels, n);
		}
		return labels;
	}

	// throw assertion failure unless the assay has the indicated annotations
	private void requireSameAnnotations(Assay assay, Annotation[] wantAnnots, TextLabel[] wantLabels, String note)
	{
		Set<String> got = new TreeSet<>(), want = new TreeSet<>();
		for (Annotation annot : assay.annotations) got.add("A:" + annot.valueURI + ":" + annot.propURI);
		for (TextLabel label : assay.textLabels) got.add("T:" + label.text + ":" + label.propURI);
		for (Annotation annot : wantAnnots) want.add("A:" + annot.valueURI + ":" + annot.propURI);
		for (TextLabel label : wantLabels) want.add("T:" + label.text + ":" + label.propURI);
		assertTrue(Arrays.equals(got.toArray(new String[0]), want.toArray(new String[0])), "assay annotations differ:" + note);
	}
}




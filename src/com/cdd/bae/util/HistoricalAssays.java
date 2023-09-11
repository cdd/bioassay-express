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

import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.util.diff.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.apache.commons.lang3.*;

/*
	Creates a virtual series of snapshots in time for all of the assays: at each day interval, the assays are in a certain state as indicated by their history
	(i.e. nonexistent, partially rolled back or completely current). This facilitates retroactive analysis of progress.
	
	Note that this class is expensive, because it loads all assays into memory and keeps them there until it gets discarded.
*/

public class HistoricalAssays
{
	private String schemaURI = null; // default = all
	private long[] assayIDList = null; // can be pre-specified or obtained on demand
	private Assay[] assays = null;
	private long[] timeIntervals = null;
	
	// ------------ public methods ------------

	public HistoricalAssays()
	{
	}
	public HistoricalAssays(String schemaURI)
	{
		this.schemaURI = schemaURI;
	}
	public HistoricalAssays(long[] assayIDList)
	{
		this.assayIDList = assayIDList;
	}
	
	// gather the data and make it available
	public void setup()
	{
		// load assays and collet unique dates
		DataStore store = Common.getDataStore();
		List<Assay> assayList = new ArrayList<>();
		Set<Long> wholeDays = new TreeSet<>(); // days where something changed (= timestamp with time portion removed)
		
		if (assayIDList == null)
		{
			if (schemaURI == null)
				assayIDList = store.assay().fetchAssayIDCurated();
			else
				assayIDList = store.assay().fetchAssayIDWithSchemaCurated(schemaURI);
		}
			
		for (long assayID : assayIDList)
		{
			Assay assay = store.assay().getAssay(assayID);
			if (assay == null) continue;
			
			assayList.add(assay);
			
			wholeDays.add(wholeDay(assay.curationTime));
			if (assay.history != null) for (History h : assay.history) wholeDays.add(wholeDay(h.curationTime));
		}
		assays = assayList.toArray(new Assay[assayList.size()]);
		
		timeIntervals = ArrayUtils.toPrimitive(wholeDays.toArray(new Long[wholeDays.size()]));
	}
	
	// rolls an assay back to a certain time; may just return the current assay if nothing to do
	public static Assay timeCapsule(Assay assay, long day)
	{
		if (ArrayUtils.getLength(assay.history) == 0) return assay;
		
		Assay capsule = assay.clone();
		
		// special deal: going past the beginning of time
		if (day < wholeDay(capsule.history[0].curationTime))
		{
			capsule.uniqueID = null;
			capsule.text = "";
			capsule.annotations = new Annotation[0];
			capsule.textLabels = new TextLabel[0];
			return capsule;
		}
		
		for (int n = capsule.history.length - 1; n >= 0; n--)
		{
			History hist = capsule.history[n];
			long hday = wholeDay(hist.curationTime);
			if (day >= hday) break; // stop rolling back, because this day is valid
			rollbackHistory(capsule, hist);
		}
		
		return capsule;
	}
	
	// access to derived content
	public Assay[] getAssays() {return assays;}
	public long[] getTimeIntervals() {return timeIntervals;}
	
	// return a time value rounded down to the day
	public static long wholeDay(Date time)
	{
		if (time == null) return 0;
		final long MOD = 1000L * 60 * 60 * 24; // one day's worth of milliseconds
		return time.getTime() - time.getTime() % MOD;
	}

	// ------------ private methods ------------
		
	// reverse-applies the history to the assay datastructure (note that this will need to be called multiple times in order to guarantee correct rollback to an intermediate state)
	private static void rollbackHistory(Assay assay, History hist)
	{
		assay.curationTime = hist.curationTime;
		assay.curatorID = hist.curatorID;

		// the stuff that was "added" has to be removed
		for (Annotation annot : hist.annotsAdded)
		{
			int idx = findAnnotation(assay.annotations, annot);
			if (idx >= 0) assay.annotations = ArrayUtils.remove(assay.annotations, idx);
		}
		for (TextLabel label : hist.labelsAdded)
		{
			int idx = findTextLabel(assay.textLabels, label);
			if (idx >= 0) assay.textLabels = ArrayUtils.remove(assay.textLabels, idx);
		}
		
		// the stuff that was "removed" has to be added
		for (Annotation annot : hist.annotsRemoved)
		{
			int idx = findAnnotation(assay.annotations, annot);
			if (idx < 0) assay.annotations = ArrayUtils.add(assay.annotations, annot);
		}
		for (TextLabel label : hist.labelsRemoved)
		{
			int idx = findTextLabel(assay.textLabels, label);
			if (idx < 0) assay.textLabels = ArrayUtils.add(assay.textLabels, label);
		}
		
		if (Util.notBlank(hist.uniqueIDPatch))
		{
			LinkedList<DiffMatchPatch.Patch> patch = DiffMatchPatch.patchFromText(hist.uniqueIDPatch);
			String newID = DiffMatchPatch.patchApplyText(patch, assay.uniqueID);
			if (newID != null) assay.uniqueID = newID;
		}
		if (Util.notBlank(hist.textPatch))
		{
			LinkedList<DiffMatchPatch.Patch> patch = DiffMatchPatch.patchFromText(hist.textPatch);
			String newText = DiffMatchPatch.patchApplyText(patch, assay.text);
			if (newText != null) assay.text = newText;
		}
	}
	
	// convenience lookups
	private static int findAnnotation(Annotation[] annotList, Annotation annot)
	{
		if (annotList == null) return -1;
		for (int n = 0; n < annotList.length; n++) 
			if (Schema.samePropGroupNest(annot.propURI, annot.groupNest, annotList[n].propURI, annotList[n].groupNest) && annot.valueURI.equals(annotList[n].valueURI)) return n;
		return -1;
	}
	private static int findTextLabel(TextLabel[] labelList, TextLabel label)
	{
		if (labelList == null) return -1;
		for (int n = 0; n < labelList.length; n++) 
			if (Schema.samePropGroupNest(label.propURI, label.groupNest, labelList[n].propURI, labelList[n].groupNest) && label.text.equals(labelList[n].text)) return n;
		return -1;
	}
}



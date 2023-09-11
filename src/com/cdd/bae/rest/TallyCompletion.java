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

import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.json.*;

/*
	TallyCompletion: returns historical "completion" metrics for given assays
	
	Parameters:
		assayIDList: list of assay IDs to operate on

	Return:
		days: {tick: {assayID: fairscore}}
			(note: fair score = total fairness for the assay at that time, where max value = # assignments in its schema)
		assignments: array of...
			propURI, groupNest: assignment reference
			assayIDList: assay IDs that have something defined (subset of eponymous parameter)
			countDefined: # of these assays that either have non-absence URI or text label for literal type
			countAbsence: assays not in above list that use an absence term (typically "not applicable")
			countMismatch: assays not in above lists that have a text label when they shouldn't, or are out of schema
*/

public class TallyCompletion extends RESTBaseServlet 
{
	private static final long serialVersionUID = 1L;

	private static class AssnInfo
	{
		Schema.Assignment assn;
		Set<Long> assayIDList = new TreeSet<>();
		int countDefined = 0;
		int countAbsence = 0;
		int countMismatch = 0;
		AssnInfo(Schema.Assignment assn) {this.assn = assn;}
	}
	
	// ------------ public methods ------------
       
	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		DataStore store = Common.getDataStore();

		long[] assayIDList = input.getJSONArray("assayIDList").toLongArray();
		
		// gather the data
		
		Map<Long, Map<Long, Float>> days = new TreeMap<>(); // tick: {assayID: fairscore}
		Map<String, AssnInfo> assnInfo = new TreeMap<>(); // prop/group hash
		
		for (long assayID : assayIDList)
		{
			Assay assay = store.assay().getAssay(assayID);
			if (assay == null) continue;
			extractHistory(assay, days, assnInfo);
		}
		
		// write it back
		
		JSONObject result = new JSONObject();

		JSONObject jsonDays = new JSONObject();
		for (Map.Entry<Long, Map<Long, Float>> entry1 : days.entrySet())
		{
			long tick = entry1.getKey();
			Map<Long, Float> assayScores = entry1.getValue();
			
			JSONObject jsonScores = new JSONObject();
			for (Map.Entry<Long, Float> entry2 : assayScores.entrySet())
			{
				long assayID = entry2.getKey();
				float score = entry2.getValue();
				jsonScores.put(String.valueOf(assayID), score);
			}
			
			jsonDays.put(String.valueOf(tick), jsonScores);
		}
		
		JSONArray jsonAssignments = new JSONArray();
		for (AssnInfo ai : assnInfo.values())
		{
			JSONObject jsonAssn = new JSONObject();
			jsonAssn.put("propURI", ai.assn.propURI);
			jsonAssn.put("groupNest", ai.assn.groupNest());
			jsonAssn.put("assayIDList", ai.assayIDList);
			jsonAssn.put("countDefined", ai.countDefined);
			jsonAssn.put("countAbsence", ai.countAbsence);
			jsonAssn.put("countMismatch", ai.countMismatch);
			jsonAssignments.put(jsonAssn);
		}
		
		result.put("days", jsonDays);
		result.put("assignments", jsonAssignments);
		return result;
	}
	
	// ------------ private methods ------------

	private void extractHistory(Assay assay, Map<Long, Map<Long, Float>> days, Map<String, AssnInfo> assnInfo)
	{
		Schema schema = Common.getSchema(assay.schemaURI);
		if (schema == null) return;
		
		// one for each assignment/assay pair; higher takes precedence
		final int STATUS_MISMATCH = 1;
		final int STATUS_ABSENCE = 2;
		final int STATUS_DEFINED = 3;
		
		// look through annotations in the latest version and increment # assays for each unique instance
		Map<String, Integer> bestStatus = new HashMap<>(); // assn-to-highest status
		for (DataObject.Annotation annot : assay.annotations)
		{
			String key = Schema.keyPropGroup(annot.propURI, annot.groupNest);
			Schema.Assignment[] assnList = schema.findAssignmentByProperty(annot.propURI, annot.groupNest);
			if (assnList.length == 0) continue;

			AssnInfo ai = assnInfo.get(key);
			if (ai == null) assnInfo.put(key, ai = new AssnInfo(assnList[0]));
			ai.assayIDList.add(assay.assayID);
			
			int status = STATUS_DEFINED;
			SchemaTree tree = Common.obtainTree(schema, assnList[0]);
			SchemaTree.Node node = tree == null ? null : tree.getNode(annot.valueURI);
			if (annot.valueURI.equals(AssayUtil.URI_NOTAPPLICABLE)) {}
			else if (AssayUtil.ABSENCE_SET.contains(annot.valueURI)) status = STATUS_ABSENCE;
			else if (node != null)
			{
				for (; node != null; node = node.parent) if (node.uri.equals(AssayUtil.URI_ABSENCE)) status = STATUS_ABSENCE;
			}
			else status = STATUS_MISMATCH;
			bestStatus.put(key, Math.max(status, bestStatus.getOrDefault(key, 0)));
		}
		for (DataObject.TextLabel label : assay.textLabels)
		{
			String key = Schema.keyPropGroup(label.propURI, label.groupNest);
			Schema.Assignment[] assnList = schema.findAssignmentByProperty(label.propURI, label.groupNest);
			if (assnList.length == 0) continue;
			
			AssnInfo ai = assnInfo.get(key);
			if (ai == null) assnInfo.put(key, ai = new AssnInfo(assnList[0]));
			ai.assayIDList.add(assay.assayID);
			
			int status = STATUS_DEFINED;
			if (assnList[0].suggestions == Schema.Suggestions.FULL ||
				assnList[0].suggestions == Schema.Suggestions.DISABLED) status = STATUS_MISMATCH;
			bestStatus.put(key, Math.max(status, bestStatus.getOrDefault(key, 0)));
		}
		
		for (Map.Entry<String, Integer> entry : bestStatus.entrySet())
		{
			AssnInfo ai = assnInfo.get(entry.getKey());
			int status = entry.getValue();
			if (status == STATUS_DEFINED) ai.countDefined++;
			else if (status == STATUS_ABSENCE) ai.countAbsence++;
			else if (status == STATUS_MISMATCH) ai.countMismatch++;
		}

		// now add composite fairness scores for each day code
		Set<Long> wholeDays = new HashSet<>();
		
		wholeDays.add(HistoricalAssays.wholeDay(assay.curationTime));
		if (assay.history != null) for (History h : assay.history) wholeDays.add(HistoricalAssays.wholeDay(h.curationTime));
		
		for (long day : wholeDays)
		{
			Assay capsule = HistoricalAssays.timeCapsule(assay, day);
			
			// record keys for each absence term besides n/a, which tarnish the value of any other assertion
			Set<String> tarnished = new HashSet<>();
			for (DataObject.Annotation annot : assay.annotations) if (annot.valueURI != null)
			{
				if (annot.valueURI.equals(AssayUtil.URI_NOTAPPLICABLE)) continue;
				if (AssayUtil.ABSENCE_SET.contains(annot.valueURI)) tarnished.add(Schema.keyPropGroup(annot.propURI, annot.groupNest));
			}

			Map<String, Float> assnValues = new HashMap<>(); // {key:fairness<0..1>}
			for (DataObject.Annotation annot : assay.annotations)
			{
				Schema.Assignment[] assnList = schema.findAssignmentByProperty(annot.propURI, annot.groupNest);
				if (assnList.length == 0) continue;

				String key = Schema.keyPropGroup(annot.propURI, annot.groupNest);
				float value = 1; // always get a full point for a semantic annotation
							     // NOTE: could augment it to see if the annotation is still in the tree; 
							     // probably not really necessary though					     
				if (tarnished.contains(key)) value = 0.5f; // reduced to half if tarnished by absence
				assnValues.put(key, Math.max(assnValues.getOrDefault(key, 0.0f), value));
			}
			for (DataObject.TextLabel label : assay.textLabels)
			{
				Schema.Assignment[] assnList = schema.findAssignmentByProperty(label.propURI, label.groupNest);
				if (assnList.length == 0) continue;

				String key = Schema.keyPropGroup(label.propURI, label.groupNest);
				float value = assnList[0].suggestions == Schema.Suggestions.FULL || 
							  assnList[0].suggestions == Schema.Suggestions.DISABLED ? 0.5f : 1;
								// get 50% of value if it's supposed to be a semantic field, or 100% if it's a literal
								// NOTE: could augment it to do a validify check based on type
				assnValues.put(key, Math.max(assnValues.getOrDefault(key, 0.0f), value));
			}
			
			// add up the total FAIRness; note that this is not calibrated, i.e. maximum value = # assays in template
			float total = 0;
			for (float v : assnValues.values()) total += v;

			Map<Long, Float> tick = days.get(day); // {assayID: fairscore}
			if (tick == null) days.put(day, tick = new TreeMap<>());
			tick.put(assay.assayID, total);
		}
	}
}

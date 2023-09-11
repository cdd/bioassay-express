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

import java.util.*;

import org.apache.commons.lang3.*;
import org.json.*;;

/*
	Takes a boilerplate definition and applies it to assay details. Once it is complete, there should be some nice text.
*/

public class BoilerplateAnalysis
{
	private Transliteration.Boilerplate boiler;
	private Schema schema;
//	private DataObject.Assay assay;
	
	protected Set<Schema.Assignment> assignments = new HashSet<>();
	
	public static class AnalysisCombination
	{
		Set<DataObject.TextLabel> textLabels = new HashSet<>();
		Set<DataObject.Annotation> annotations = new HashSet<>();
		
		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;
			AnalysisCombination other = (AnalysisCombination) o;
			return Util.equals(textLabels, other.textLabels) && Util.equals(annotations, other.annotations);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(textLabels, annotations);
		}
		
		@Override
		public String toString()
		{
			return textLabels + " " + annotations;
		}

		public int size()
		{
			return textLabels.size() + annotations.size();
		}
	}

	// ------------ public methods ------------

	public BoilerplateAnalysis(Transliteration.Boilerplate boiler, Schema schema)
	{
		this.boiler = boiler;
		this.schema = schema;
	}
	
	// get list of all assignments found in definition
	public Set<Schema.Assignment> getAssignmentsInScript() throws DetailException
	{
		reset();
		if (boiler != null) processContent(boiler.content);
		return assignments;
	}
	
	public Set<AnalysisCombination> createCombinations(String... additionalValueURIs) throws DetailException
	{
		List<Schema.Assignment> assignments = new ArrayList<>(getAssignmentsInScript());
		Set<AnalysisCombination> combinations = new HashSet<>();
		
		int n = 2 + additionalValueURIs.length;
		int[] pick = new int[assignments.size()];
		Arrays.fill(pick, 0);
		
		while (true)
		{
			AnalysisCombination combination = new AnalysisCombination();
			for (int i = 0; i < pick.length; i++)
			{
				if (pick[i] == 0) continue;
				Schema.Assignment assn = assignments.get(i);
				if (pick[i] == 1)
				{
					String text = "val:" + assn.name.replace(' ', '_');
					DataObject.TextLabel label = new DataObject.TextLabel(assn.propURI, text, assn.groupNest());
					combination.textLabels.add(label);
				} 
				else
				{
					String valueURI = additionalValueURIs[pick[i] - 2];
					DataObject.Annotation annot = new DataObject.Annotation(assn.propURI, valueURI, assn.groupNest());
					combination.annotations.add(annot);
				}
			}
			combinations.add(combination);

			// increment the pick list
			boolean finished = false;
			for (int i = 0; i < pick.length; i++)
			{
				pick[i]++;
				if (pick[i] != n) break;
				pick[i] = 0;
				finished = (i + 1) == pick.length;
			}
			if (finished) break;
		}
		
		return combinations;
	}

	public SortedMap<String, List<AnalysisCombination>> groupedTransliterations() throws DetailException
	{
		SortedMap<String, List<AnalysisCombination>> grouped = new TreeMap<>((s1, s2) -> s2.length() - s1.length());
		for (AnalysisCombination combination : createCombinations())
		{
			DataObject.Assay assay = new DataObject.Assay();
			assay.textLabels = combination.textLabels.toArray(new DataObject.TextLabel[0]);
			assay.annotations = combination.annotations.toArray(new DataObject.Annotation[0]);
			BoilerplateScript script = new BoilerplateScript(boiler, Common.getSchemaCAT(), assay);
			script.inscribe();
			
			grouped.computeIfAbsent(script.getHTML(), key -> new ArrayList<>()).add(combination);
		}
		return grouped;
	}

	public void reset()
	{
		assignments.clear();
	}

	// ------------ private methods ------------

	// run down a list of content items and spool them all out
	protected void processContent(JSONArray content) throws DetailException
	{
		for (int n = 0; n < content.length(); n++)
		{
			Object obj = content.get(n);
			if (obj instanceof String) { /* nothing to do */ }
			else if (obj instanceof JSONObject) processObject((JSONObject)obj);
			else throw new DetailException("Boilerplate content must consist of strings or objects.");
		}
	}

	// given an embedded JSON object, presumes that it must contain a higher level instruction of some kind
	protected void processObject(JSONObject json) throws DetailException
	{
		if (json.has("term")) processTerm(json);
		else if (json.has("terms")) processMultipleTerm(json);
		else if (json.has("field")) processField(json);
		else if (json.has("ifany")) processIfAny(json);
		else if (json.has("ifvalue")) processIfValue(json, "ifvalue", false);
		else if (json.has("ifbranch")) processIfValue(json, "ifbranch", true);
		else throw new DetailException("Misunderstood boilerplate item: " + json);
	}

	// unpacks a "term" or "terms" and fills in everything according to display preferences
	protected void processTerm(JSONObject json) throws DetailException
	{
		extractAssignments(json.get("term"));
	}
	private void processMultipleTerm(JSONObject json) throws DetailException
	{
		JSONArray list = json.getJSONArray("terms");
		for (int n = 0; n < list.length(); n++)
		{
			extractAssignments(list.get(n));
		}
	}

	// add information from the assay object
	private void processField(JSONObject json) throws DetailException
	{
		/* nothing to do */
		String field = json.optString("field", "");
		if (field.equals("uniqueID")) { /* nothing to do */ }
		else if (field.equals("assayID")) { /* nothing to do */ }
		else throw new DetailException("Unknown field '" + field + "'");

	}

	// executes two possibilities: if an assignment has any terms, process the first block, otherwise process the second
	protected void processIfAny(JSONObject json) throws DetailException
	{
		parseCondition(json.get("ifany"));
		
		for (String fld : new String[]{"then", "else"})
		{
			if (!json.has(fld)) continue;
			Object obj = json.get(fld);
			if (obj instanceof JSONArray) processContent((JSONArray)obj);
			else if (obj instanceof JSONObject) processObject((JSONObject)obj);
			else if (obj instanceof String) { /* nothing to do */ }
			else throw new DetailException("Incompatible type " + obj.getClass());
		}
	}

	// executes two possibilities, depending on whether the term has a certain value; format is [valueURI, propURI, groupNest...]
	private void processIfValue(JSONObject json, String action, boolean branch) throws DetailException
	{
		JSONArray param = json.getJSONArray(action);
		JSONArray term = new JSONArray();
		for (int n = 1; n < param.length(); n++) term.put(param.getString(n));
		extractAssignments(term);


		for (String fld : new String[]{"then", "else"})
		{
			if (!json.has(fld)) continue;
			Object obj = json.get(fld);
			if (obj instanceof JSONArray) processContent((JSONArray)obj);
			else if (obj instanceof JSONObject) processObject((JSONObject)obj);
			else if (obj instanceof String) { /* nothing to do */ }
			else throw new DetailException("Incompatible type " + obj.getClass());
		}
	}

	protected void parseCondition(Object obj) throws DetailException
	{
		if (obj instanceof String)
		{
			extractAssignments(obj);
			return;
		}
		JSONArray arr = (JSONArray)obj;
		if (arr.get(0) instanceof String)
		{
			extractAssignments(arr);
			return;
		}
		
		for (int n = 0; n < arr.length(); n++)
		{
			extractAssignments(arr.get(n));
		}
	}

	// given a JSON spec (must be string or array), finds any matching assignments; if the group nest is underspecified, may end up
	// matching more than wanted
	private void extractAssignments(Object obj) throws DetailException
	{
		String propURI = null;
		String[] groupNest = null;

		if (obj instanceof String) propURI = ModelSchema.expandPrefix((String)obj);
		else if (obj instanceof JSONArray)
		{
			String[] bits = ((JSONArray)obj).toStringArray();
			if (bits.length > 0) propURI = ModelSchema.expandPrefix(bits[0]);
			if (bits.length > 1)
			{
				groupNest = ArrayUtils.remove(bits, 0);
				for (int n = 0; n < groupNest.length; n++) groupNest[n] = ModelSchema.expandPrefix(groupNest[n]);
			}
		}
		else throw new DetailException("Terms must be specified as a string or array");

		if (propURI == null) return;

		for (Schema.Assignment assn : schema.findAssignmentByProperty(propURI))
		{
			if (Schema.compatibleGroupNest(assn.groupNest(), groupNest)) assignments.add(assn);
		}
	}
}

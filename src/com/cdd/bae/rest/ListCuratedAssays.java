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
import java.util.stream.*;

import org.json.*;

/*
	ListCuratedAssays: returns unique ID numbers for all of the user-curated assays.

	Parameters:
		anomaly: (optional)	"nonblanks" = entries that are curated AND have at least one annotation
							"blanks" = only entries with a blank field
							"outschema" = entries with a value not belonging in the schema
							"absenceterms" = entries that have at least one absence term
		schemaURI: (optional) restrict to the given schema URI
		withAssayID: (default=true) return assay IDs
		withUniqueID: (default=true) return unique IDs
		withCurationTime: (default=true) return last curation time
		withFAIRness: (default=false) calculate and return FAIRness score
		cutoffTime: (optional) only include assays that first appeared after this time (millisecond since 1970)
*/

public class ListCuratedAssays extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;
	private static final String[] ABSENCE_TERMS = {"bat:Ambiguous", "bat:Dubious", "bat:Missing", "bat:NeedsChecking", "bat:RequiresTerm", "bat:Unknown"};
	private static final Set<String> absenceTerms = Arrays.asList(ABSENCE_TERMS).stream().map(ModelSchema::expandPrefix).collect(Collectors.toSet());

	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		DataStore store = Common.getDataStore();

		String anomaly = input.optString("anomaly");
		String schemaURI = input.optString("schemaURI", null);
		boolean withAssayID = input.optBoolean("withAssayID", true);
		boolean withUniqueID = input.optBoolean("withUniqueID", true);
		boolean withCurationTime = input.optBoolean("withCurationTime", true);
		boolean withFAIRness = input.optBoolean("withFAIRness", false);

		long[] assayIDList;

		if (Util.notBlank(schemaURI) && Util.isBlank(anomaly))
		{
			assayIDList = store.assay().fetchAssayIDWithSchemaCurated(schemaURI);
		}
		else
		{
			if (anomaly.equals("nonblanks")) assayIDList = store.assay().fetchAssayIDCuratedWithAnnotations();
			else if (anomaly.equals("blanks")) assayIDList = limitBlanks(store.assay().fetchAllCuratedAssayID(), store);
			else if (anomaly.equals("outschema")) assayIDList = limitOutSchema(store.assay().fetchAllCuratedAssayID(), store);
			else if (anomaly.equals("absenceterms")) assayIDList = limitAbsenceTerms(store.assay().fetchAllCuratedAssayID(), store);
			else assayIDList = store.assay().fetchAllCuratedAssayID();

			if (schemaURI != null) assayIDList = limitToSchema(assayIDList, schemaURI, store);
		}

		// note: could make the implementation more efficient if it ends up in a rate limiting step
		long cutoffTime = input.optLong("cutoffTime", Long.MIN_VALUE);
		if (cutoffTime != Long.MIN_VALUE) assayIDList = limitCutoffTime(assayIDList, input.getLong("cutoffTime"), store);

		JSONObject result = new JSONObject();
		if (withAssayID) result.put("assayIDList", assayIDList);
		if (withUniqueID) result.put("uniqueIDList", store.assay().uniqueIDFromAssayID(assayIDList));
		if (withCurationTime) result.put("curationTime", store.assay().fetchCurationTimes(assayIDList));
		if (withFAIRness) result.put("fairness", calculateFAIRness(assayIDList));
		return result;
	}

	// ------------ private methods ------------

	// goes through all the assays and limits to just those with missing assignments
	private long[] limitBlanks(long[] assayIDList, DataStore store)
	{
		return Arrays.stream(assayIDList).filter(assayID -> anyBlanks(store.assay().getAssay(assayID))).toArray();
	}

	// returns true if any of the schema assignments have no annotation
	private boolean anyBlanks(DataObject.Assay assay)
	{
		if (assay.schemaURI == null) return false;
		Schema schema = Common.getSchema(assay.schemaURI);
		if (schema == null) return false;
		if (assay.annotations == null) return true;

		// collect the propURI's of the schema assignments excluding the GO terms
		Set<String> residual = Arrays.stream(schema.getRoot().flattenedAssignments()).map(assn -> assn.propURI).collect(Collectors.toSet());
		residual.remove("http://www.bioassayontology.org/bao#BAX_0000009");

		// remove all annotations from assay
		for (DataObject.Annotation annot : assay.annotations) residual.remove(annot.propURI);
		return !residual.isEmpty();
	}

	// goes through all the assays and limits to just those with an out-of-schema annotation
	private long[] limitOutSchema(long[] assayIDList, DataStore store)
	{
		return Arrays.stream(assayIDList).filter(assayID -> outOfSchema(store.assay().getAssay(assayID))).toArray();
	}

	private long[] limitAbsenceTerms(long[] assayIDList, DataStore store)
	{
		return Arrays.stream(assayIDList).filter(assayID -> hasAbsenceTerms(store.assay().getAssay(assayID))).toArray();
	}

	// returns true if any of the annotations are absence terms
	private boolean hasAbsenceTerms(Assay assay)
	{
		if (assay.schemaURI == null) return false;
		Schema schema = Common.getSchema(assay.schemaURI);
		if (schema == null) return false;
		if (assay.annotations == null) return false;

		for (Annotation annotation : assay.annotations)
			if (absenceTerms.contains(annotation.valueURI)) return true;
		return false;
	}

	// returns true if any of the terms don't belong in their template
	private boolean outOfSchema(Assay assay)
	{
		if (assay.annotations == null || assay.schemaURI == null) return false;
		Schema schema = Common.getSchema(assay.schemaURI);
		if (schema == null) return false;

		for (DataObject.Annotation annot : assay.annotations)
		{
			if (AssayUtil.ABSENCE_SET.contains(annot.valueURI)) continue; // absence terms are never out of schema
			SchemaTree tree = Common.obtainTree(schema, annot.propURI, annot.groupNest);
			// invalid propURI or schema contains no values for propURI
			if (tree == null || tree.getTree().isEmpty()) continue;
			if (tree.getTree().get(annot.valueURI) == null) return true; // not present
		}

		return false;
	}

	// limit to just the assays with the given schema
	private long[] limitToSchema(long[] assayIDList, String schemaURI, DataStore store)
	{
		return Arrays.stream(assayIDList).filter(assayID ->
		{
			Assay assay = store.assay().getAssay(assayID);
			return assay != null && assay.schemaURI != null && assay.schemaURI.equals(schemaURI);
		}).toArray();
	}

	// return only the assays for which the earliest curation is later than the cutoff
	private long[] limitCutoffTime(long[] assayIDList, long cutoffTime, DataStore store)
	{
		return Arrays.stream(assayIDList).filter(assayID ->
		{
			Assay assay = store.assay().getAssay(assayID);
			if (assay == null || assay.history == null || assay.history.length == 0) return false;
			if (assay.history[0].curationTime == null) return false;
			return assay.history[0].curationTime.getTime() >= cutoffTime; // first curation event must be past cutoff
		}).toArray();
	}
	
	// calculate "FAIRness" score for each of the assays
	private double[] calculateFAIRness(long[] assayIDList)
	{
		double[] fairness = new double[assayIDList.length];
		DataStore store = Common.getDataStore();
		
		for (int n = 0; n < assayIDList.length; n++)
		{
			Assay assay = store.assay().getAssay(assayIDList[n]);
			fairness[n] = assayFAIRness(assay);
		}
		
		return fairness;
	}
	private double assayFAIRness(Assay assay)
	{
		Schema schema = Common.getSchema(assay.schemaURI);
		if (schema == null) return 0;
		
		Schema.Assignment[] assnList = schema.getRoot().flattenedAssignments();
		Map<String, Integer> assnKeyIndex = new HashMap<>();
		for (int n = 0; n < assnList.length; n++) assnKeyIndex.put(Schema.keyPropGroup(assnList[n].propURI, assnList[n].groupNest()), n);
		
		double[] best = new double[assnList.length];
		
		if (assay.annotations != null) for (DataObject.Annotation annot : assay.annotations)
		{
			SchemaDynamic graft = new SchemaDynamic(schema, assay.schemaBranches, assay.schemaDuplication);
			SchemaDynamic.SubTemplate subt = graft.relativeAssignment(annot.propURI, annot.groupNest);
			if (subt == null) continue;
			
			Integer idx = assnKeyIndex.get(Schema.keyPropGroup(annot.propURI, subt.groupNest));
			if (idx == null) continue; // note that branch templates aren't counted, but duplications are
			if (best[idx] == 1) continue; // already maxed out
			
			if (annot.valueURI.equals(AssayUtil.URI_NOTAPPLICABLE)) 
			{
				best[idx] = Math.max(best[idx], 0.5); 
				continue;
			}			
			
			Schema.Assignment assn = assnList[idx];
			SchemaTree tree = Common.obtainTree(schema, assn);
			SchemaTree.Node node = tree == null ? null : tree.getNode(annot.valueURI);
			if (node == null) continue;
			best[idx] = 1.0; // present in the tree: always full value for a URI
		}
		
		if (assay.textLabels != null) for (DataObject.TextLabel label : assay.textLabels)
		{
			SchemaDynamic graft = new SchemaDynamic(schema, assay.schemaBranches, assay.schemaDuplication);
			SchemaDynamic.SubTemplate subt = graft.relativeAssignment(label.propURI, label.groupNest);
			if (subt == null) continue;
			
			Integer idx = assnKeyIndex.get(Schema.keyPropGroup(label.propURI, subt.groupNest));
			if (idx == null) continue; // note that branch templates aren't counted, but duplications are
			if (best[idx] == 1) continue; // already maxed out
			
			Schema.Assignment assn = assnList[idx];
			if (assn.suggestions == Schema.Suggestions.FULL || assn.suggestions == Schema.Suggestions.DISABLED)
				best[idx] = Math.max(best[idx], 0.5); // half value for literal in a URI field
			else
				best[idx] = 1.0; // full value when literal is expected
		}
		
		double total = 0;
		for (int n = 0; n < best.length; n++) total += best[n];
		return total / assnList.length;
	}
}


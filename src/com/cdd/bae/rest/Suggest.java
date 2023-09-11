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
import com.cdd.bae.tasks.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.apache.commons.lang3.*;
import org.json.*;

/*
	Suggestions: send some text/previous assignments, and get a list of suggestions for new assignments. The request is on a timer, so the
	result may not include all of the requested assignments.
	
	Parameters:
		text: (optional) text of assay protocol
		schemaURI, schemaBranches, schemaDuplication
		assignments: array of...
			propURI: the property for which the predictions will be made
			groupNest: further identification
		accepted: (optional) current annotations, in the form of [{propURI: valueURI:, groupNest:},...]
		rejected: (optional) previously rejected assignments, in the form of [{propURI: valueURI:, groupNest:},...]
		allTerms: if true, returns unmodelled terms from schema branch (default = false)
		
	Result: array of...
		propURI
		groupNest
		suggestions: array of...
			propURI, propLabel
			valueURI, valueLabel
			groupNest
			nlp, corr
			combined
		axiomFiltered: true if axiom rules reduced the size of the list
*/

public class Suggest extends RESTBaseServlet 
{
	protected static final String VALUE_LABEL = "valueLabel";
	protected static final String PROP_LABEL = "propLabel";
	protected static final String GROUP_NEST = "groupNest";
	protected static final String VALUE_URI = "valueURI";
	protected static final String PROP_URI = "propURI";
	
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		SchemaDynamic graft = new SchemaDynamic(input.optString("schemaURI", null), 
												input.optJSONArray("schemaBranches"), input.optJSONArray("schemaDuplication"));
		Schema schema = graft.getResult();
													  
		String text = input.has("text") ? input.getString("text") : "";
		
		JSONObject[] assignments = input.getJSONArray("assignments").toObjectArray();
		
		boolean allTerms = input.optBoolean("allTerms", false);
		Set<String> seenValues = new HashSet<>();
		
		DataObject.Annotation[] accepted = null;
		JSONArray jsonAccepted = input.optJSONArray("accepted");
		if (jsonAccepted != null) 
		{
			List<DataObject.Annotation> list = new ArrayList<>();
			for (int n = 0; n < jsonAccepted.length(); n++)
			{
				JSONObject obj = jsonAccepted.getJSONObject(n);
				// UGH: the service needs content outside of current property
				//if (!propURI.equals(obj.getString("propURI"))) continue;
				String accPropURI = obj.getString(PROP_URI);
				if (accPropURI.equals(AssayUtil.URI_ASSAYTITLE))
				{
					String title = obj.optString(VALUE_LABEL);
					if (!text.contains(title)) text = title + "\n\n" + text;
					continue;
				}
				if (!obj.has(VALUE_URI) || obj.isNull(VALUE_URI)) continue;
				String accValueURI = obj.getString(VALUE_URI);
				String[] accGroupNest = obj.optJSONArrayEmpty(GROUP_NEST).toStringArray();
				for (int i = 0; i < accGroupNest.length; i++) accGroupNest[i] = Schema.removeSuffixGroupURI(accGroupNest[i]);
				
				// NOTE: this is a little bit weird - can't count the same prop/value pair if it occurs within another group
				// nest, because then it won't list the outcome at all; this is a general deficiency in the model building -
				// nesting isn't accounted for at all (but it should be conditionally only)
				//if (sameGroup(groupNest, accGroupNest)) continue;
				
				list.add(new DataObject.Annotation(accPropURI, accValueURI, accGroupNest));
				seenValues.add(accValueURI);
			}
			accepted = list.toArray(new DataObject.Annotation[list.size()]);
		}
		
		DataObject.Annotation[] rejected = null;
		JSONArray jsonRejected = input.optJSONArray("rejected");
		if (jsonRejected != null)
		{
			for (int n = 0; n < jsonRejected.length(); n++)
			{
				JSONObject obj = jsonRejected.getJSONObject(n);
				String rejPropURI = obj.getString(PROP_URI), rejValueURI = obj.getString(VALUE_URI);
				String[] rejGroupNest = obj.optJSONArrayEmpty(GROUP_NEST).toStringArray();
				for (int i = 0; i < rejGroupNest.length; i++) rejGroupNest[i] = Schema.removeSuffixGroupURI(rejGroupNest[i]);

				//if (!propURI.equals(accPropURI)) continue;
				rejected = ArrayUtils.add(rejected, new DataObject.Annotation(rejPropURI, rejValueURI, rejGroupNest));
			}
		}

		long startTime = System.currentTimeMillis();
		int numResults = 0;
		final long MAX_TIME_MILLI = 1000; // one second of computation is enough
		final int MAX_RESULTS = 20000; // this many terms starts to add up quite a bit

		// prepare the winnowing
		WinnowAxioms winnow = new WinnowAxioms(Common.getAxioms());
		List<WinnowAxioms.SubjectContent> subjects = new ArrayList<>();
		List<WinnowAxioms.KeywordContent> keywords = new ArrayList<>();
		for (JSONObject obj : jsonAccepted.toObjectArray())
		{
			String uri = obj.optString(VALUE_URI, null);
		
			if (Util.notBlank(uri))
			{
				String accValueURI = obj.getString(VALUE_URI), accPropURI = obj.getString(PROP_URI);
				String[] accGroupNest = obj.optJSONArrayEmpty(GROUP_NEST).toStringArray();
				SchemaDynamic.SubTemplate subt = graft.relativeAssignment(accPropURI, accGroupNest);
				if (subt == null) continue;
				SchemaTree tree = Common.obtainTree(subt.schema, accPropURI, subt.groupNest);
				if (tree == null) continue;
				subjects.add(new WinnowAxioms.SubjectContent(uri, tree));
			}
			else
			{
				String label = obj.getString(VALUE_LABEL), propURI = obj.getString(PROP_URI);
				keywords.add(new WinnowAxioms.KeywordContent(label, propURI));
			}
		}
		if (Util.notBlank(text)) keywords.add(new WinnowAxioms.KeywordContent(text, null));
		
		JSONArray results = new JSONArray();
		
		for (JSONObject jsonAssn : assignments)
		{
			String propURI = jsonAssn.getString(PROP_URI);
			String[] groupNest = jsonAssn.optJSONArrayEmpty(GROUP_NEST).toStringArray();

			JSONObject jsonResult = new JSONObject();
			jsonResult.put(PROP_URI, propURI);
			jsonResult.put(GROUP_NEST, groupNest);
			
			JSONArray suggestions = new JSONArray();
			
			SchemaDynamic.SubTemplate subt = graft.relativeAssignment(propURI, groupNest);
			Set<String> applicable = null;
			if (subt != null)
			{			
				SchemaTree impactTree = Common.obtainTree(subt.schema, propURI, subt.groupNest);
				if (impactTree != null) 
				{
					applicable = WinnowAxioms.filterURI(winnow.winnowBranch(subjects, keywords, impactTree));
					if (applicable == null)
					{
						// nothing got filtered out, so just add the whole tree: this has the effect of excluding
						// any models that are out-of-schema/cross-contamination
						applicable = new HashSet<>();
						for (SchemaTree.Node node : impactTree.getFlat()) applicable.add(node.uri);
					}
				}
			}
			
			ModelPredict.Prediction[] preds = null;
			boolean filtered = false;
			
			if (applicable != null && applicable.size() == 1)
			{
				// special deal: axiom winnowing reduced it to 1 value, meaning that we already have the answer - even in 
				// cases where there's no protocol text or semantic annotations
				preds = new ModelPredict.Prediction[]{new ModelPredict.Prediction()};
				preds[0].propURI = propURI;
				preds[0].groupNest = groupNest;
				preds[0].valueURI = applicable.iterator().next();
				preds[0].nlp = Double.NaN;
				preds[0].corr = Double.NaN;
				preds[0].combined = 0;
				filtered = true;
			}
			else
			{
				// usual case: use text + accepted terms to make predictions
				ModelPredict model = new ModelPredict(schema, text, new String[]{propURI}, accepted, rejected);
				model.setApplicable(applicable);
				model.calculate();
				preds = model.getPredictions();
				filtered = model.wasAnythingFiltered();
			}
			
			SchemaTree.Node[] nodes = null;
			if (allTerms)
			{
				SchemaTree tree = Common.obtainTree(schema, propURI, groupNest);
				if (tree != null) nodes = tree.getList();
			}
			
			int sz = preds.length + Util.length(nodes);
			if (numResults > 0 && numResults + sz > MAX_RESULTS) break; // would push us over the limit
			numResults += sz;
			
			preds = orderPredictions(preds);
			
			for (ModelPredict.Prediction p : preds)
			{
				String valueLabel = Common.getCustomName(schema, p.propURI, groupNest, p.valueURI);
				if (valueLabel == null) valueLabel = Common.getOntoValues().getLabel(p.valueURI);

				JSONObject jsonPred = new JSONObject();
				jsonPred.put(PROP_URI, ModelSchema.collapsePrefix(p.propURI));
				jsonPred.put(PROP_LABEL, Common.getOntoProps().getLabel(p.propURI));
				jsonPred.put(VALUE_URI, ModelSchema.collapsePrefix(p.valueURI));
				jsonPred.put(VALUE_LABEL, valueLabel);
				jsonPred.put(GROUP_NEST, groupNest);
			
				/*String propLabel = schvoc.getLabel(p.propURI), valueLabel = schvoc.getLabel(p.valueURI);
				if (propLabel != null) jsonPred.put(PROP_LABEL, propLabel);
				if (valueLabel != null) jsonPred.put(VALUE_LABEL, valueLabel);*/
				
				if (!Double.isNaN(p.nlp)) jsonPred.put("nlp", p.nlp);
				if (!Double.isNaN(p.corr)) jsonPred.put("corr", p.corr);
				jsonPred.put("combined", p.combined);
				suggestions.put(jsonPred);
				
				seenValues.add(p.valueURI);
			}
			
			// if requested, include all the branch terms that didn't show up in the model
			if (nodes != null) for (SchemaTree.Node node : nodes)
			{
				if (seenValues.contains(node.uri)) continue;

				String valueLabel = Common.getCustomName(schema, propURI, groupNest, node.uri);
				if (valueLabel == null) valueLabel = Common.getOntoValues().getLabel(node.uri);

				JSONObject jsonTerm = new JSONObject();
				jsonTerm.put(PROP_URI, propURI);
				jsonTerm.put(PROP_LABEL, Common.getOntoProps().getLabel(propURI));
				jsonTerm.put(VALUE_URI, node.uri);
				jsonTerm.put(VALUE_LABEL, valueLabel);
				jsonTerm.put(GROUP_NEST, groupNest);
				suggestions.put(jsonTerm);
			}
			
			jsonResult.put("suggestions", suggestions);
			jsonResult.put("axiomFiltered", filtered);

			results.put(jsonResult);

			long time = System.currentTimeMillis() - startTime;
			if (time > MAX_TIME_MILLI) break; // long enough
		}
		
		return new JSONObject().put(RETURN_JSONARRAY, results);
	}
	
	// ------------ private methods ------------

	protected static ModelPredict.Prediction[] orderPredictions(ModelPredict.Prediction[] preds)
	{
		// sort by highest prediction first
		List<ModelPredict.Prediction> list = new ArrayList<>(preds.length);
		for (ModelPredict.Prediction p : preds) list.add(p);
		list.sort((v1, v2) -> Double.compare(v2.combined, v1.combined));

		// allow one-of-each group in the top bracket, followed by the rest
		Set<String> groups = new HashSet<>();
		List<ModelPredict.Prediction> ret = new ArrayList<>();
		for (Iterator<ModelPredict.Prediction> iterator = list.iterator(); iterator.hasNext();)
		{
			ModelPredict.Prediction p = iterator.next();
			if (groups.contains(p.propURI)) continue;
			groups.add(p.propURI);
			ret.add(p);
			iterator.remove();
		}
		ret.addAll(list);
		
		return ret.toArray(new ModelPredict.Prediction[ret.size()]);
	}
	
	protected static boolean sameGroup(String[] list1, String[] list2)
	{
		int sz1 = Util.length(list1), sz2 = Util.length(list2);
		if (sz1 != sz2) return false;
		for (int n = 0; n < sz1; n++) if (!list1[n].equals(list2[n])) return false;
		return true;
	}
}

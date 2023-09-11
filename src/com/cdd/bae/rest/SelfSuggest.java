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
import static com.cdd.bae.data.DataObject.*;
import com.cdd.bae.tasks.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.apache.commons.lang3.*;
import org.json.*;

/*
	Suggestions, based on an existing assay: this is used to evaluate how well the prediction system
	can reproduce existing data. The execution time is relatively fast, due to caching of the worst
	rate limiting steps.
	
	NOTE: the predictions currently use the regular models, which means that the assay itself is in
	the model. It could be subsequently improved to use a cross validation system, but that would
	require more infrastructure.
	
	Parameters:
		assayIDList: the assasy to load up and operate on
		assignments: array of...
			propURI: the property for which the predictions will be made
			groupNest: further identification
		valueURIList: optional array of URIs which will limit the predictions that are made
		
	Result: array of...
		assayID
		uniqueID
		propURI
		groupNest
		suggestions: array of...
			propURI, propLabel
			valueURI, valueLabel
			groupNest
			nlp, corr
			combined
		valueURIList: array of URI strings, which this assay/assignment currently has (if any)
*/

public class SelfSuggest extends RESTBaseServlet 
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		long[] assayIDList = input.getJSONArray("assayID").toLongArray();
		JSONObject[] assignments = input.getJSONArray("assignments").toObjectArray();

		Set<String> values = null;	
		if (input.has("valueURIList"))
		{
			values = new HashSet<>();
			values.addAll(Arrays.asList(input.getJSONArray("valueURIList").toStringArray()));
		}

		JSONArray results = new JSONArray();
		for (long assayID : assayIDList) makeSuggestions(assayID, assignments, results, values);		
		return new JSONObject().put(RETURN_JSONARRAY, results);
	}
	
	// ------------ private methods ------------

	// make self-suggestions for one assay, for which there should be 1-or-more assignments; the result(s) are
	// appended to the results array, tagged with assay & assignment identifiers
	private void makeSuggestions(long assayID, JSONObject[] assignments, JSONArray results, Set<String> values)
	{
		Assay assay = Common.getDataStore().assay().getAssay(assayID);
		if (assay == null) return;
		
		SchemaDynamic graft = new SchemaDynamic(Common.getSchema(assay.schemaURI), assay.schemaBranches, assay.schemaDuplication);
		Schema schema = graft.getResult();
		
		// prepare the winnowing
		WinnowAxioms winnow = new WinnowAxioms(Common.getAxioms());
		List<WinnowAxioms.SubjectContent> subjects = new ArrayList<>();
		List<WinnowAxioms.KeywordContent> keywords = new ArrayList<>();
		for (Annotation annot : assay.annotations)
		{
			SchemaDynamic.SubTemplate subt = graft.relativeAssignment(annot.propURI, annot.groupNest);
			if (subt == null) continue;
			SchemaTree tree = Common.obtainTree(subt.schema, annot.propURI, subt.groupNest);
			if (tree == null) continue;
			subjects.add(new WinnowAxioms.SubjectContent(annot.valueURI, tree));
		}
		for (TextLabel label : assay.textLabels)
		{
			keywords.add(new WinnowAxioms.KeywordContent(label.text, label.propURI));
		}
		if (Util.notBlank(assay.text)) keywords.add(new WinnowAxioms.KeywordContent(assay.text, null));
		
		for (JSONObject jsonAssn : assignments)
		{
			String propURI = jsonAssn.getString(Suggest.PROP_URI);
			String[] groupNest = jsonAssn.optJSONArrayEmpty(Suggest.GROUP_NEST).toStringArray();

			JSONObject jsonResult = new JSONObject();
			jsonResult.put("assayID", assayID);
			jsonResult.put("uniqueID", assay.uniqueID);
			jsonResult.put(Suggest.PROP_URI, propURI);
			jsonResult.put(Suggest.GROUP_NEST, groupNest);
			
			JSONArray suggestions = new JSONArray();
			
			SchemaDynamic.SubTemplate subt = graft.relativeAssignment(propURI, groupNest);
			Set<String> winnowed = null;
			if (subt != null)
			{			
				SchemaTree impactTree = Common.obtainTree(subt.schema, propURI, subt.groupNest);
				if (impactTree != null) winnowed = WinnowAxioms.filterURI(winnow.winnowBranch(subjects, keywords, impactTree));
			}
			
			Set<String> applicable = winnowed;
			if (values != null)
			{
				if (applicable != null)
					applicable.retainAll(values);
				else
					applicable = values;
			}
			
			ModelPredict.Prediction[] preds = null;
						
			// usual case: use text placeholders + annotations to make predictions
			int[] fplist = assay.fplist == null ? new int[0] : assay.fplist;
			ModelPredict model = new ModelPredict(schema, fplist, new String[]{propURI}, assay.annotations, new Annotation[0]);
			model.setApplicable(applicable);
			model.setRepredictKnown(true);
			model.calculate();
			preds = model.getPredictions();
			
			// if winnowing happened, set all winnowed results to score zero
			if (winnowed != null)
			{
				for (ModelPredict.Prediction pred : preds) if (!winnowed.contains(pred.valueURI)) pred.combined = 0;
			}
															
			for (ModelPredict.Prediction p : preds)
			{
				String valueLabel = Common.getCustomName(schema, p.propURI, groupNest, p.valueURI);
				if (valueLabel == null) valueLabel = Common.getOntoValues().getLabel(p.valueURI);

				JSONObject jsonPred = new JSONObject();
				jsonPred.put(Suggest.PROP_URI, ModelSchema.collapsePrefix(p.propURI));
				jsonPred.put(Suggest.VALUE_URI, ModelSchema.collapsePrefix(p.valueURI));
				jsonPred.put(Suggest.GROUP_NEST, groupNest);
			
				if (!Double.isNaN(p.nlp)) jsonPred.put("nlp", p.nlp);
				if (!Double.isNaN(p.corr)) jsonPred.put("corr", p.corr);
				jsonPred.put("combined", p.combined);
				suggestions.put(jsonPred);
			}
			
			jsonResult.put("suggestions", suggestions);
			
			List<String> valueURIList = new ArrayList<>();
			for (Annotation annot : assay.annotations) 
				if (Schema.samePropGroupNest(propURI, groupNest, annot.propURI, annot.groupNest)) 
					valueURIList.add(ModelSchema.collapsePrefix(annot.valueURI));
			jsonResult.put("valueURIList", valueURIList);

			results.put(jsonResult);
		}
	}
}

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
import com.cdd.bae.model.*;
import com.cdd.bae.model.dictionary.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import org.json.*;

/*
	Text mining: given a text document, attempts to identify key fragments that imply the existence of some number of annotations.
	The resulting list of terms represents a conservative subset of all the annotations that are likely to apply, i.e. it can be
	assumed that false positives are quite rare, but false negatives are very common.
	
	Parameters:
		text: text of assay protocol
		schemaURI: the applicable schema (if none supplied, will suggest one)
		existing: (optional) current annotations, if any, which may influence the analysis
		
	Result:
		schemaURI, schemaBranches, schemaDuplication
		extractions: array of...
			propURI, propLabel
			valueURI, valueLabel
			groupNest
			score
			count
			begin[], end[]
*/

public class TextMine extends RESTBaseServlet 
{
	private static final String PROP_LABEL = "propLabel";
	private static final String GROUP_NEST = "groupNest";
	private static final String VALUE_URI = "valueURI";
	private static final String VALUE_LABEL = "valueLabel";
	private static final String PROP_URI = "propURI";
	private static final long serialVersionUID = 1L;

	private static final class Result
	{
		DataObject.Annotation annot;
		ScoredHit hit;
	}

	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		DataObject.Assay assay = new DataObject.Assay();
		assay.text = input.getString("text");

		Schema schema = SchemaDynamic.compositeSchema(input.optString("schemaURI", null), 
													 input.optJSONArray("schemaBranches"), input.optJSONArray("schemaDuplication"));

		JSONArray jsonExisting = input.optJSONArray("existing");
		List<DataObject.Annotation> listAnnots = new ArrayList<>();
		List<DataObject.TextLabel> listLabels = new ArrayList<>();
		if (jsonExisting != null) 
		{
			for (int n = 0; n < jsonExisting.length(); n++)
			{
				JSONObject obj = jsonExisting.getJSONObject(n);
				String propURI = obj.optString(PROP_URI), valueURI = obj.optString(VALUE_URI);
				String[] groupNest = obj.optJSONArrayEmpty(GROUP_NEST).toStringArray();
				if (Util.notBlank(valueURI))
					listAnnots.add(new DataObject.Annotation(propURI, valueURI, groupNest));
				else
					listLabels.add(new DataObject.TextLabel(propURI, obj.optString(VALUE_LABEL, ""), groupNest));
			}
		}
		assay.annotations = listAnnots.toArray(new DataObject.Annotation[listAnnots.size()]);
		assay.textLabels = listLabels.toArray(new DataObject.TextLabel[listLabels.size()]);
		
		Result[] extracted = null;
		try {extracted = extractContent(schema, assay);}
		catch (IOException ex) {throw new RESTException(ex, "Extraction failed", RESTException.HTTPStatus.INTERNAL_SERVER_ERROR);}
		
		JSONObject result = new JSONObject();
		
		result.put("schemaURI", assay.schemaURI);
		
		JSONArray extractedList = new JSONArray();
		for (Result res : extracted)
		{
			String valueLabel = Common.getCustomName(schema, res.annot.propURI, res.annot.groupNest, res.annot.valueURI);
			if (valueLabel == null) valueLabel = Common.getOntoValues().getLabel(res.annot.valueURI);

			JSONObject json = new JSONObject();
			json.put(PROP_URI, ModelSchema.collapsePrefix(res.annot.propURI));
			json.put(PROP_LABEL, Common.getOntoProps().getLabel(res.annot.propURI));
			json.put(VALUE_URI, ModelSchema.collapsePrefix(res.annot.valueURI));
			json.put(VALUE_LABEL, valueLabel);
			json.put(GROUP_NEST, res.annot.groupNest);
			// TODO: also figure out & return the groupLabel
			
			// information about the nature of the match(es)
			json.put("score", res.hit.score);
			json.put("count", res.hit.count);
			json.put("begin", res.hit.begin);
			json.put("end", res.hit.end);

			extractedList.put(json);
		}
		result.put("extractions", extractedList);
		
		return result;
	}
	
	// ------------ private methods ------------

	// for a partially filled out assay (.text is meaningful, .annotations may have preexisting information) and maybe a schema (which
	// may be null if unspecified) returns a list of extracted annotations that ought to be asserted; if the schema parameter is blank,
	// will modify the assay datastructure to return the best guess
	private Result[] extractContent(Schema schema, DataObject.Assay assay) throws IOException
	{
		if (schema == null)
		{
			schema = Common.getSchemaCAT();
			assay.schemaURI = schema.getSchemaPrefix();
			// TODO: make an educated guess rather than defaulting; need to do this after the prediction process, or integrate it
			// into the algorithm itself
		}
	
		List<Result> list = new ArrayList<>();

		/* this is a stub version that demo's well for assay AID 364
		if (assay.text.contains("cytotoxicity")) 
			list.add(new DataObject.Annotation(ModelSchema.expandPrefix("bao:BAO_0002854"), ModelSchema.expandPrefix("bao:BAO_0002993")));
		if (assay.text.contains("CC50")) 
			list.add(new DataObject.Annotation(ModelSchema.expandPrefix("bao:BAO_0000208"), ModelSchema.expandPrefix("bao:BAO_0000187")));
		if (assay.text.contains("1536-well")) 
			list.add(new DataObject.Annotation(ModelSchema.expandPrefix("bao:BAO_0002867"), ModelSchema.expandPrefix("bao:BAO_0000516")));*/

		DictionaryPredict dpred = new DictionaryPredict();
		Map<String, List<ScoredHit>> results = dpred.getPredictionHits(assay);
		
		// TODO: results array needs to be more specific about the assignments beyond just propURI, and may also need to the ability to guess
		// the schema from a list of candidates; but for a POC using the Common Assay Template, this isn't necessary just yet
		
		Set<String> already = new HashSet<>();
		for (DataObject.Annotation annot : assay.annotations) 
			already.add(Schema.keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueURI));
		
		for (String propURI : results.keySet())
		{		
			// TODO: should already have this... fishing it out of the matched assignment for now
			Schema.Assignment[] assnList = schema.findAssignmentByProperty(propURI);
			if (assnList.length == 0) continue; // NOTE: for now assuming size of 0 or 1
			String[] groupNest = assnList[0].groupNest();
			
			// include any of the predicted annotations that wasn't already in the annotation list		
			for (ScoredHit hit : results.get(propURI))
			{
				String valueURI = hit.hit.uri;
				if (already.contains(Schema.keyPropGroupValue(propURI, groupNest, valueURI))) continue;
				
				Result res = new Result();
				res.annot = new DataObject.Annotation(propURI, valueURI, groupNest);
				res.hit = hit;
				list.add(res);
			}
		}
		
		return list.toArray(new Result[list.size()]);
	}
}

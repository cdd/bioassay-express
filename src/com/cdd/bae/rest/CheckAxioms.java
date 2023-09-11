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
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.json.*;

/*
	CheckAxioms: looks for annotations that contradict each other due to triggering a contradictory axiom rule.
	
	Parameters:
		schemaURI
		schemaBranches
		schemaDuplication
		annotations
		
	Return:
		justifications: array of [reason] - triggered axioms, with explanation for 
		violations: array of [reason] - terms in the incoming annotations that are excluded by something else
		additional: array of [reason] - terms that were *created* by axioms i.e. not in original tree
		
			where [reason] is object of:
				valueURI
				propURI
				groupNest
				triggers: array of strings, the valueURIs for anything implicated in the violation rule
				valueLabel: when axioms implicate a literal term, this is defined & valueURI is null

*/

public class CheckAxioms extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;

	protected static AxiomVocab useAxioms = null; // needed for testing: supply custom axioms

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		String schemaURI = input.optString("schemaURI", null);
		JSONArray schemaBranches = input.optJSONArray("schemaBranches"), schemaDuplication = input.optJSONArray("schemaDuplication");
		JSONArray jsonAnnotations = input.getJSONArray("annotations");
		
		SchemaDynamic graft = new SchemaDynamic(schemaURI, schemaBranches, schemaDuplication);
		Schema schema = graft.getResult();
		
		List<DataObject.Annotation> annotList = new ArrayList<>();
		List<DataObject.TextLabel> labelList = new ArrayList<>();
		Set<String> gotAssn = new HashSet<>(); // for quick exclusion
		for (JSONObject json : jsonAnnotations.toObjectArray())
		{
			String valueURI = json.optString("valueURI", null);
			String propURI = json.getString("propURI");
			String[] groupNest = json.optJSONArrayEmpty("groupNest").toStringArray();
			if (valueURI != null)
				annotList.add(new DataObject.Annotation(propURI, valueURI, groupNest));
			else
				labelList.add(new DataObject.TextLabel(propURI, json.getString("valueLabel"), groupNest));
			gotAssn.add(Schema.keyPropGroup(propURI, groupNest));
		}
		DataObject.Annotation[] annotations = annotList.toArray(new DataObject.Annotation[annotList.size()]);
		DataObject.TextLabel[] textLabels = labelList.toArray(new DataObject.TextLabel[labelList.size()]);
		
		WinnowAxioms winnow = new WinnowAxioms(useAxioms != null ? useAxioms : Common.getAxioms());

		// package the results
		JSONObject result = new JSONObject();
		
		List<WinnowAxioms.SubjectContent> subjects = new ArrayList<>();
		List<WinnowAxioms.KeywordContent> keywords = new ArrayList<>();
		for (DataObject.Annotation annot : annotations)
		{
			SchemaDynamic.SubTemplate subt = graft.relativeAssignment(annot.propURI, annot.groupNest);
			if (subt == null) continue;
			SchemaTree tree = Common.obtainTree(subt.schema, annot.propURI, subt.groupNest);
			if (tree == null) continue;
			subjects.add(new WinnowAxioms.SubjectContent(annot.valueURI, tree));
		}
		for (DataObject.TextLabel label : textLabels)
		{
			keywords.add(new WinnowAxioms.KeywordContent(label.text, label.propURI));
		}
		
		// look for justifications of terms that are positively asserted, so that the caller knows why
		JSONArray jsonJustif = new JSONArray();
		Schema.Assignment[] allAssignments = schema.getRoot().flattenedAssignments();
		for (Schema.Assignment assn : allAssignments)
		{
			String[] groupNest = assn.groupNest();
			if (gotAssn.contains(Schema.keyPropGroup(assn.propURI, groupNest))) continue;
			SchemaDynamic.SubTemplate subt = graft.relativeAssignment(assn.propURI, groupNest);
			if (subt == null) continue;
			SchemaTree tree = Common.obtainTree(subt.schema, assn.propURI, subt.groupNest);
			if (tree == null) continue;
			Set<String> applicable = WinnowAxioms.filterURI(winnow.winnowBranch(subjects, keywords, tree));
			if (applicable == null || applicable.size() != 1) continue;
			
			String valueURI = applicable.toArray(new String[1])[0];
			DataObject.Annotation target = new DataObject.Annotation(assn.propURI, valueURI, subt.groupNest);
			String[] triggers = winnow.findJustificationTriggers(graft, annotations, target);
						
			JSONObject json = new JSONObject();
			json.put("propURI", assn.propURI);
			json.put("valueURI", valueURI);
			json.put("groupNest", groupNest);
			json.put("triggers", triggers);
			jsonJustif.put(json);	
		}
		result.put("justifications", jsonJustif);
		
		// look for terms that violate the axioms, and note the reasons
		JSONArray jsonViol = new JSONArray();
		DataObject.Annotation[] violations = winnow.violatingAxioms(graft, annotations);
		for (DataObject.Annotation annot : violations)
		{
			String[] triggers = winnow.findViolationTriggers(graft, annotations, annot);
		
			JSONObject json = new JSONObject();
			json.put("propURI", annot.propURI);
			json.put("valueURI", annot.valueURI);
			json.put("groupNest", annot.groupNest);
			json.put("triggers", triggers);
			jsonViol.put(json);
		}
		result.put("violations", jsonViol);
		
		// look over all of the assignments and see if there are any "extra" terms (annotations or labels) that are implied
		// by the axioms
		JSONArray jsonAddit = new JSONArray();
		for (Schema.Assignment assn : allAssignments)
		{
			Set<WinnowAxioms.Result> results = new HashSet<>();
		
			// do extra-vocabular URI terms, then literals
			SchemaDynamic.SubTemplate subt = graft.relativeAssignment(assn.propURI, assn.groupNest());
			SchemaTree tree = Common.obtainTree(subt.schema, assn.propURI, subt.groupNest);
			if (tree == null) tree = new SchemaTree(new SchemaTree.Node[0], assn);
			
			Set<WinnowAxioms.Result> applicable = winnow.winnowBranch(subjects, keywords, tree);
			if (applicable != null) results.addAll(applicable);
			Set<WinnowAxioms.Result> literals = winnow.impliedLiterals(subjects, keywords, assn);
			if (literals != null) results.addAll(literals);
						
			// package results
			for (WinnowAxioms.Result axresult : results)
			{
				if (axresult.uri != null && tree.getNode(axresult.uri) != null) continue;
				// ... otherwise: result is either a literal or something extra to the tree (such as not-applicable)
				
				String[] triggers = null;
				if (axresult.uri != null)
				{
					DataObject.Annotation target = new DataObject.Annotation(assn.propURI, axresult.uri, subt.groupNest);
					triggers = winnow.findJustificationTriggers(graft, annotations, target);
				}
				else triggers = winnow.findLiteralTriggers(graft, annotations, assn, axresult.literal);
				
				JSONObject json = new JSONObject();
				json.put("propURI", assn.propURI);
				json.put("groupNest", assn.groupNest());
				if (axresult.uri != null)
				{
					json.put("valueURI", axresult.uri);
					json.put("valueLabel", Common.getOntoValues().getLabel(axresult.uri));
				}
				else json.put("valueLabel", axresult.literal);
				if (triggers != null) json.put("triggers", triggers);
				jsonAddit.put(json);
			}
			
			
		}
		result.put("additional", jsonAddit);
		
		return result;			
	}	

	// ------------ private methods ------------

}

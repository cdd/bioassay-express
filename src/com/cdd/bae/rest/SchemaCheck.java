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

import java.util.*;

import org.json.*;

/*
	Takes a list of assay IDs and checks to see if any of their annotations refer to terms that are not in the appropriate
	schema. This can pull out cases that were never correct, or cases that have become invalid due to schema changes.
	
	It also validates each assay according to the current set of axiom rules, and reports any problems.
	
	Parameters:
		assayIDList: assays to examine
					 
	Results:
		outOfSchema: array [see Result class]
		missingMandatory: array [see Result class]
		axiomViolation: array [see Result class]
		numberMisformat: array [see Result class]
*/

public class SchemaCheck extends RESTBaseServlet 
{
	private static final long serialVersionUID = 1L;

	private static final class Result
	{
		long assayID;
		String uniqueID;
		String schemaURI;
		String propURI, propLabel;
		String[] groupNest;
		String valueURI, valueLabel;
		
		JSONObject toJSON()
		{
			JSONObject json = new JSONObject();
			json.put("assayID", assayID);
			json.put("uniqueID", uniqueID);
			json.put("schemaURI", schemaURI);
			json.put("propURI", propURI);
			json.put("propLabel", propLabel);
			json.put("valueURI", valueURI);
			json.put("valueLabel", valueLabel);
			json.put("groupNest", groupNest);
			return json;
		}
	}


	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		JSONArray assayIDList = input.optJSONArray("assayIDList");

		DataStore store = Common.getDataStore();

		JSONArray jsonSchema = new JSONArray();
		JSONArray jsonMandatory = new JSONArray();
		JSONArray jsonViolation = new JSONArray();
		JSONArray jsonNumber = new JSONArray();
		
		for (long assayID : assayIDList.toLongArray())
		{
			DataObject.Assay assay = store.assay().getAssay(assayID);
			if (assay == null) continue; // silent failure
			
			Schema schema = Common.getSchema(assay.schemaURI);
			if (schema == null) continue; // also silent
			SchemaDynamic graft = new SchemaDynamic(schema, assay.schemaBranches, assay.schemaDuplication);
			
			List<Result> outOfSchema = new ArrayList<>();
			List<Result> missingMandatory = new ArrayList<>();
			List<Result> axiomViolation = new ArrayList<>();
			List<Result> numberMisformat = new ArrayList<>();
			extractSchemaIssues(outOfSchema, missingMandatory, assay, graft);
			extractAxiomViolations(axiomViolation, assay, graft);
			extractNumberMisformat(numberMisformat, assay, graft);

			for (Result r : outOfSchema) jsonSchema.put(r.toJSON());
			for (Result r : missingMandatory) jsonMandatory.put(r.toJSON());
			for (Result r : axiomViolation) jsonViolation.put(r.toJSON());
			for (Result r : numberMisformat) jsonNumber.put(r.toJSON());
		}
		
		JSONObject results = new JSONObject();
		results.put("outOfSchema", jsonSchema);
		results.put("missingMandatory", jsonMandatory);
		results.put("axiomViolation", jsonViolation);
		results.put("numberMisformat", jsonNumber);
		return results;
	}

	// ------------ private methods ------------

	// find trouble related to schema inadequacies for one assay
	private void extractSchemaIssues(List<Result> outOfSchema, List<Result> missingMandatory, DataObject.Assay assay, SchemaDynamic graft)
	{
		Schema schema = graft.getResult(); // has branch/duplication entries included
		
		Set<String> annotKeys = new HashSet<>();

		// annotations are the main focus - look for both properties and values that don't fit
		passed: for (DataObject.Annotation annot : assay.annotations)
		{
			annotKeys.add(Schema.keyPropGroup(annot.propURI, annot.groupNest));
		
			SchemaDynamic.SubTemplate subt = graft.relativeAssignment(annot.propURI, annot.groupNest);
			String propLabel = null;
			if (subt != null)
			{
				Schema.Assignment[] assnList = subt.schema.findAssignmentByProperty(annot.propURI, subt.groupNest);
				if (assnList.length > 0) propLabel = assnList[0].name;
				for (Schema.Assignment assn : assnList)
				{
					SchemaTree tree = Common.obtainTree(schema, assn);
					if (tree != null && tree.getNode(annot.valueURI) != null) continue passed; // found something: so it's OK
				}
			}
			
			Result r = new Result();
			r.assayID = assay.assayID;
			r.uniqueID = assay.uniqueID;
			r.schemaURI = assay.schemaURI;
			r.propURI = annot.propURI;
			r.propLabel = propLabel != null ? propLabel : Common.getOntoProps().getLabel(annot.propURI);
			r.groupNest = annot.groupNest;
			r.valueURI = annot.valueURI;
			r.valueLabel = Common.getOntoValues().getLabel(annot.valueURI);
			outOfSchema.add(r);
		}
		
		// labels with mismatched properties also need to be identified
		for (DataObject.TextLabel label : assay.textLabels)
		{
			annotKeys.add(Schema.keyPropGroup(label.propURI, label.groupNest));
		
			Schema.Assignment[] assnList = schema.findAssignmentByProperty(label.propURI, label.groupNest);
			if (assnList.length > 0) continue;

			Result r = new Result();
			r.assayID = assay.assayID;
			r.uniqueID = assay.uniqueID;
			r.schemaURI = assay.schemaURI;
			r.propURI = label.propURI;
			r.propLabel = Common.getOntoValues().getLabel(label.propURI);
			r.groupNest = label.groupNest;
			r.valueURI = null;
			r.valueLabel = label.text;
			outOfSchema.add(r);
		}
		
		// list out mandatory assignments that have no annotations
		for (Schema.Assignment assn : schema.getRoot().flattenedAssignments())
		{
			// TODO: proceed only if the assignment is marked "mandatory" (future field)
			if (annotKeys.contains(Schema.keyPropGroup(assn.propURI, assn.groupNest()))) continue;
			
			Result r = new Result();
			r.assayID = assay.assayID;
			r.uniqueID = assay.uniqueID;
			r.schemaURI = assay.schemaURI;
			r.propURI = assn.propURI;
			r.propLabel = assn.name;
			r.groupNest = assn.groupNest();
			r.valueURI = null;
			r.valueLabel = null;
			missingMandatory.add(r);
		}		
	}

	// list out any annotations that are contradicted by axioms
	private void extractAxiomViolations(List<Result> axiomViolation, DataObject.Assay assay, SchemaDynamic graft)
	{
		Schema schema = graft.getResult();
		WinnowAxioms winnow = new WinnowAxioms(Common.getAxioms());
		for (DataObject.Annotation annot : winnow.violatingAxioms(graft, assay.annotations))
		{
			Schema.Assignment[] assnList = schema.findAssignmentByProperty(annot.propURI, annot.groupNest);
			String propLabel = assnList.length > 0 ? assnList[0].name : Common.getOntoProps().getLabel(annot.propURI);
			String valueLabel = null;
			SchemaDynamic.SubTemplate subt = graft.relativeAssignment(annot.propURI, annot.groupNest);
			if (subt != null)
			{
				SchemaTree tree = Common.obtainTree(subt.schema, annot.propURI, subt.groupNest);
				SchemaTree.Node node = tree == null ? null : tree.getNode(annot.valueURI);
				if (node != null) valueLabel = node.label;
			}
			if (valueLabel == null) valueLabel = Common.getOntoValues().getLabel(annot.valueURI);
		
			Result r = new Result();
			r.assayID = assay.assayID;
			r.uniqueID = assay.uniqueID;
			r.schemaURI = assay.schemaURI;
			r.propURI = annot.propURI;
			r.propLabel = propLabel;
			r.groupNest = annot.groupNest;
			r.valueURI = annot.valueURI;
			r.valueLabel = valueLabel;
			axiomViolation.add(r);
		}
	}

	// look for misformatted number literals
	private void extractNumberMisformat(List<Result> numberMisformat, DataObject.Assay assay, SchemaDynamic graft)
	{
		Schema schema = graft.getResult();
		for (DataObject.TextLabel label : assay.textLabels)
		{
			Schema.Assignment[] assnList = schema.findAssignmentByProperty(label.propURI, label.groupNest);
			if (assnList.length == 0) continue;
			
			boolean valid = true;
			if (assnList[0].suggestions == Schema.Suggestions.INTEGER) 
				valid = AssayUtil.validIntegerLiteral(label.text) && AssayUtil.standardNumberLiteral(label.text);
			else if (assnList[0].suggestions == Schema.Suggestions.NUMBER) 
				valid = AssayUtil.validNumberLiteral(label.text) && AssayUtil.standardNumberLiteral(label.text);
			if (valid) continue;
			
			Result r = new Result();
			r.assayID = assay.assayID;
			r.uniqueID = assay.uniqueID;
			r.schemaURI = assay.schemaURI;
			r.propURI = label.propURI;
			r.propLabel = assnList[0].name;
			r.groupNest = label.groupNest;
			r.valueLabel = label.text;
			numberMisformat.add(r);
		}	
	}
}

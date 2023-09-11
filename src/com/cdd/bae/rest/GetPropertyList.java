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

import com.cdd.bae.config.InitParams.*;
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.rest.RESTException.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.json.*;

/*
	GetPropertyList: fetches information about a particular assignment within a schema, with the intent to create a list to be searched, ordered and selected from.
	
	Parameters:
		schemaURI, schemaBranches, schemaDuplication
		locator: which part of the template
			or
		propURI, groupNest: identify the assignment
		annotations: (optional) current annotations, in the form of [{propURI: valueURI:, groupNest:},...]
			(the purpose of giving these is to trigger axiom logic)

*/

public class GetPropertyList extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		SchemaAssn par = getProperties(input);
		return new JSONObject().put("list", buildList(par.schema, par.assn, par.applicable));
	}

	public static final class SchemaAssn 
	{
		public Schema schema;
		public Schema.Assignment assn;
		public Set<String> applicable;
		
		public SchemaAssn(Schema schema, Schema.Assignment assn, Set<String> applicable) 
		{
			this.schema = schema; 
			this.assn = assn;
			this.applicable = applicable;
		}
	}
	protected static SchemaAssn getProperties(JSONObject input) throws RESTException
	{
		String locator = input.optString("locator");
		String propURI = input.optString("propURI");
		String[] groupNest = input.optJSONArrayEmpty("groupNest").toStringArray();

		if (Util.isBlank(locator) && Util.isBlank(propURI))
			throw new RESTException("You need to provide either locator or propURI", RESTException.HTTPStatus.BAD_REQUEST);

		List<DataObject.Annotation> annotList = new ArrayList<>();
		List<DataObject.TextLabel> labelList = new ArrayList<>();		
		for (JSONObject json : input.optJSONArrayEmpty("annotations").toObjectArray())
		{
			String uri = json.optString("valueURI", null);
			if (Util.notBlank(uri))
			{
				DataObject.Annotation annot = new DataObject.Annotation();
				annot.valueURI = uri;
				annot.propURI = json.getString("propURI");
				annot.groupNest = json.optJSONArrayEmpty("groupNest").toStringArray();
				annotList.add(annot);
			}
			else // is label
			{
				DataObject.TextLabel label = new DataObject.TextLabel();
				label.text = json.getString("valueLabel");
				label.propURI = json.getString("propURI");
				label.groupNest = json.optJSONArrayEmpty("groupNest").toStringArray();
				labelList.add(label);
			}
		}

		SchemaDynamic graft = new SchemaDynamic(input.optString("schemaURI", null), input.optJSONArray("schemaBranches"), input.optJSONArray("schemaDuplication"));
		
		// prepare the winnowing
		Set<String> applicable = null;
		if (annotList.size() > 0 || labelList.size() > 0)
		{
			WinnowAxioms winnow = new WinnowAxioms(Common.getAxioms());
			List<WinnowAxioms.SubjectContent> subjects = new ArrayList<>();
			List<WinnowAxioms.KeywordContent> keywords = new ArrayList<>();
			
			for (DataObject.Annotation annot : annotList)
			{
				WinnowAxioms.SubjectContent subj = WinnowAxioms.annotationToSubject(graft, annot);
				if (subj != null) subjects.add(subj);
			}
			for (DataObject.TextLabel label : labelList)
			{
				keywords.add(new WinnowAxioms.KeywordContent(label.text, label.propURI));
			}
			// (assay text not included in the request)

			SchemaDynamic.SubTemplate subt = graft.relativeAssignment(propURI, groupNest);
			if (subt != null)
			{			
				SchemaTree impactTree = Common.obtainTree(subt.schema, propURI, subt.groupNest);
				if (impactTree != null) applicable = WinnowAxioms.filterURI(winnow.winnowBranch(subjects, keywords, impactTree));
			}
		}		
		
		if (Util.notBlank(locator))
		{
			Schema schema = graft.getResult();
			Schema.Assignment assn = schema.obtainAssignment(locator);
			if (assn == null) throw new RESTException("Assignment not found; locator=" + locator, HTTPStatus.BAD_REQUEST);
			if (!graft.isComposite()) return new SchemaAssn(schema, assn, applicable);
			propURI = assn.propURI;
			groupNest = assn.groupNest();
			// (continue on to find the assignment in the relative template)
		}

		SchemaDynamic.SubTemplate subt = graft.relativeAssignment(propURI, groupNest);
		if (subt == null) throw new RESTException("Assignment not found; propURI=" + propURI + ", groupNest=" + Util.arrayStr(groupNest), HTTPStatus.BAD_REQUEST);

		Schema.Assignment[] assnList = subt.schema.findAssignmentByProperty(propURI, subt.groupNest);
		if (assnList.length == 0) throw new RESTException("Assignment not found; propURI=" + propURI + ", groupNest=" + Util.arrayStr(groupNest), HTTPStatus.BAD_REQUEST);
		
		return new SchemaAssn(subt.schema, assnList[0], applicable);
	}

	// creates a JSON object describing need-to-know information about a provisional term
	protected static JSONObject describeProvisional(DataObject.Provisional prov)
	{
		JSONObject json = new JSONObject();
		json.put("provisionalID", prov.provisionalID);
		json.put("proposerID", prov.proposerID);
		json.put("role", prov.role == null ? null : prov.role.toString());
		json.put("bridgeStatus", prov.bridgeStatus);
		return json;
	}

	// ------------ private methods ------------

	private static JSONArray buildList(Schema schema, Schema.Assignment assn, Set<String> applicable) throws RESTException
	{
		// list the values that have models associated with them
		DataStore store = Common.getDataStore();
		Set<String> hasModel = new HashSet<>();
		Set<Integer> modelTargets = store.model().allTargetsNLP();
		for (DataObject.AnnotationFP annot : store.annot().fetchAnnotationFP(assn.propURI))
		{
			if (modelTargets.contains(annot.fp)) hasModel.add(annot.valueURI);
		}

		var provCache = Common.getProvCache();
		Set<String> containers = AssayUtil.enumerateContainerTerms(assn);

		// emit in JSON-ready form
		JSONArray list = new JSONArray();

		SchemaTree tree = Common.obtainTree(schema, assn);
		if (tree == null)
		{
			Util.writeln("Tree not found: base schema=" + schema.getSchemaPrefix());
			Util.writeln("Requested assignment: propURI=" + assn.propURI + " groupNest=" + Util.arrayStr(assn.groupNest()));
			Util.writeln("Assignments:");
			for (Schema.Assignment look : schema.getRoot().flattenedAssignments())
				Util.writeln("    propURI=" + look.propURI + " groupNest=" + Util.arrayStr(look.groupNest()));
			String msg = "Tree not found, propURI=" + assn.propURI + " groupNest=" + Util.arrayStr(assn.groupNest());
			throw new RESTException(msg, HTTPStatus.BAD_REQUEST);
		}
		
		SchemaTree.Node[] values = tree.getList();
		for (SchemaTree.Node node : values)
		{
			// skip container nodes
			if (containers.contains(node.uri)) continue;
			
			var prov = provCache.getTerm(node.uri);

			JSONObject obj = new JSONObject();
			obj.put("uri", node.uri);
			obj.put("abbrev", ModelSchema.collapsePrefix(node.uri));
			obj.put("inSchema", node.inSchema);
			if (prov != null) obj.put("provisional", describeProvisional(prov));
			obj.put("inModel", hasModel.contains(node.uri));
			obj.put("schemaCount", node.schemaCount);
			obj.put("altLabels", node.altLabels);
			obj.put("externalURLs", node.externalURLs);

			String name = Common.getCustomName(schema, assn.propURI, assn.groupNest(), node.uri);
			if (name == null) name = Common.getOntoValues().getLabel(node.uri);
			obj.put("name", name);
			
			if (applicable != null) obj.put("axiomApplicable", applicable.contains(node.uri));
			
			list.put(obj);
		}

		return list;
	}

}

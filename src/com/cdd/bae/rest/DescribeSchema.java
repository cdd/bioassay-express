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
import com.cdd.bae.rest.RESTException.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.json.*;

/*
	DescribeSchema: loads up a template, and fills in information about it. Can be used to fetch an overview of the groups and the
	assignments contained within, or it can be used to drill down on a specific assignment.
	
	Parameters:
		schemaURI: (optional) default is to select the common assay template
		locator: (optional) if missing, provides information about the whole schema; if given, shows specific assignment information
		schemaBranches: (optional) list of branches to append to the schema, to form a composite
*/

public class DescribeSchema extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;

	private static final String LOCATOR = "locator";
	private static final String SCHEMA_URI = "schemaURI";
	private static final String BRANCHES = "schemaBranches";
	private static final String DUPLICATION = "schemaDuplication";
	private static final String DESCR = "descr";

	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		String schemaURI = input.optString(SCHEMA_URI, null);
		String locator = input.optString(LOCATOR, null);
		JSONArray jsonBranches = input.optJSONArray(BRANCHES);
		JSONArray jsonDuplication = input.optJSONArray(DUPLICATION);

		Schema schema = null;
		if (Util.notBlank(schemaURI))
		{
			schema = Common.getSchema(schemaURI);
			if (schema == null) throw new RESTException("Schema URI not found: [" + schemaURI + "]", HTTPStatus.INTERNAL_SERVER_ERROR);
		}
		else schema = Common.getSchemaCAT();

		JSONObject result = new JSONObject();
		if (Util.isBlank(locator))
		{
			DataObject.SchemaBranch[] branches = null;
			if (jsonBranches != null)
			{
				branches = new DataObject.SchemaBranch[jsonBranches.length()];
				for (int n = 0; n < branches.length; n++) branches[n] = AssayJSON.deserialiseBranch(jsonBranches.getJSONObject(n));
			}
			DataObject.SchemaDuplication[] duplication = null;
			if (jsonDuplication != null)
			{
				duplication = new DataObject.SchemaDuplication[jsonDuplication.length()];
				for (int n = 0; n < duplication.length; n++) duplication[n] = AssayJSON.deserialiseDuplication(jsonDuplication.getJSONObject(n));
			}

			fillSchema(schema, branches, duplication, result);
		}
		else fillAssignment(schema, locator, result);
		
		return result;
	}

	// used internally, but also made available in other places
	public static void fillSchema(Schema schema, DataObject.SchemaBranch[] branches, DataObject.SchemaDuplication[] duplication, JSONObject result)
	{
		if (branches != null || duplication != null)
		{
			SchemaDynamic graft = new SchemaDynamic(schema, branches, duplication);
			if (graft.isComposite())
			{
				schema = graft.getResult();
				// TODO: and information about graft-points...
			}
		}
	
		Schema.Group root = schema.getRoot();

		result.put("name", root.name);
		result.put(DESCR, root.descr);
		result.put(SCHEMA_URI, schema.getSchemaPrefix());
		result.put("canTransliterate", Common.getTransliteration().getBoilerplate(schema.getSchemaPrefix()) != null);

		JSONArray jsonAssnList = new JSONArray(), jsonGroupList = new JSONArray();

		List<Schema.Group> queue = new ArrayList<>();
		queue.add(root);

		while (!queue.isEmpty())
		{
			Schema.Group group = queue.remove(0);

			JSONObject jsonGroup = new JSONObject();
			jsonGroup.put("name", group.name);
			jsonGroup.put(DESCR, group.descr);
			jsonGroup.put(LOCATOR, schema.locatorID(group));
			jsonGroup.put("groupURI", group.groupURI);
			jsonGroup.put("groupNest", group.groupNest());
			jsonGroup.put("canDuplicate", group.canDuplicate);
			jsonGroupList.put(jsonGroup);

			for (int n = 0; n < group.assignments.size(); n++)
			{
				Schema.Assignment assn = group.assignments.get(n);

				JSONObject jsonAssn = new JSONObject();
				jsonAssn.put("name", assn.name);
				jsonAssn.put(DESCR, assn.descr);
				jsonAssn.put("propURI", assn.propURI);
				jsonAssn.put(LOCATOR, schema.locatorID(assn));
				jsonAssn.put("suggestions", assn.suggestions.toString().toLowerCase());
				jsonAssn.put("mandatory", assn.mandatory);

				jsonAssn.put("groupNest", assn.groupNest());
				jsonAssn.put("groupLabel", assn.groupLabel());

				/* this uses up hella bandwidth, and has only a minor use case
				SchemaTree tree = Common.obtainTree(assn);
				JSONArray treeValues = new JSONArray();
				for (SchemaTree.Node node : tree.getList()) treeValues.put(ModelSchema.collapsePrefix(node.uri));
				jsonAssn.put("treeValueAbbrevs", treeValues);*/

				jsonAssnList.put(jsonAssn);
			}

			for (int n = 0; n < group.subGroups.size(); n++) queue.add(n, group.subGroups.get(n));
		}

		result.put("assignments", jsonAssnList);
		result.put("groups", jsonGroupList);
	}

	// ------------ private methods ------------	

	private void fillAssignment(Schema schema, String locator, JSONObject result)
	{
		DataStore store = Common.getDataStore();
		Schema.Assignment assn = schema.obtainAssignment(locator);

		JSONArray jsonList = new JSONArray();

		Set<String> modelledValues = store.model().modelledValuesForProperty(assn.propURI);
		Set<String> gotURI = new HashSet<>();

		for (SchemaTree.Node node : Common.obtainTree(schema, assn).getList())
		{
			JSONObject jsonValue = new JSONObject();

			jsonValue.put("name", node.label);
			jsonValue.put("uri", node.uri);
			jsonValue.put("abbrev", ModelSchema.collapsePrefix(node.uri));
			jsonValue.put(DESCR, node.descr);
			jsonValue.put("inSchema", true);
			jsonValue.put("isExplicit", node.isExplicit);
			jsonValue.put("hasModel", modelledValues.contains(node.uri));

			jsonList.put(jsonValue);

			gotURI.add(node.uri);
		}

		// show anything else that has a model
		for (String uri : modelledValues) if (!gotURI.contains(uri))
		{
			JSONObject jsonValue = new JSONObject();

			var branch = Common.getOntoValues().getBranch(uri);
			jsonValue.put("name", branch == null ? null : branch.label);
			jsonValue.put("uri", uri);
			jsonValue.put("abbrev", ModelSchema.collapsePrefix(uri));
			jsonValue.put(DESCR, Common.getOntoValues().getDescr(uri));
			jsonValue.put("inSchema", false);
			jsonValue.put("isExplicit", false);
			jsonValue.put("hasModel", true);

			jsonList.put(jsonValue);
		}

		result.put("values", jsonList);
	}
}

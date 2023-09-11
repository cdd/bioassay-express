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

import java.util.*;

import org.json.*;

import com.cdd.bae.config.InitParams.*;
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

/*
	GetPropertyTree: fetches information about a particular assignment within a schema, in the form of a tree-ready datastructure.
	
	Parameters:
		schemaURI, schemaBranches, schemaDuplication
		locator: which part of the template
			or
		propURI, groupNest: identify the assignment
		annotations: (optional) current annotations, in the form of [{propURI: valueURI:, groupNest:},...]
			(the purpose of giving these is to trigger axiom logic)

*/

public class GetPropertyTree extends RESTBaseServlet 
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		GetPropertyList.SchemaAssn par = GetPropertyList.getProperties(input);
		return new JSONObject().put("tree", buildTree(par.schema, par.assn, par.applicable));
	}

	// ------------ private methods ------------
	
	// builds the tree, then serialises it to JSON
	private static JSONObject buildTree(Schema schema, Schema.Assignment assn, Set<String> applicable) throws RESTException
	{
		DataStore store = Common.getDataStore();
		
		// list the values that have models associated with them
		Set<String> hasModel = new HashSet<>();
		Set<Integer> modelTargets = store.model().allTargetsNLP();
		for (DataObject.AnnotationFP annot : store.annot().fetchAnnotationFP(assn.propURI))
		{
			if (modelTargets.contains(annot.fp)) hasModel.add(annot.valueURI);
		}
		
		var provCache = Common.getProvCache();
		
		// obtain a flattened version of the schema tree
		SchemaTree schtree = Common.obtainTree(schema, assn);
		if (schtree == null) 
		{
			String msg = "Assignment not present in tree: propURI=" + assn.propURI + ", groupNest=" + Util.arrayStr(assn.groupNest());
			throw new RESTException(msg, RESTException.HTTPStatus.BAD_REQUEST);
		}
		SchemaTree.Node[] flat = schtree.getFlat();
		JSONObject tree = new JSONObject();
		JSONArray listDepth = append(tree, "depth");
		JSONArray listParent = append(tree, "parent");
		JSONArray listName = append(tree, "name");
		JSONArray listAbbrev = append(tree, "abbrev");
		JSONArray listInSchema = append(tree, "inSchema");
		JSONArray listProvisional = append(tree, "provisional");
		JSONArray listSchemaCount = append(tree, "schemaCount");
		JSONArray listChildCount = append(tree, "childCount");
		JSONArray listInModel = append(tree, "inModel");
		JSONArray listAltLabels = append(tree, "altLabels");
		JSONArray listExternalURLs = append(tree, "externalURLs");
		JSONArray listAxiomApplic = applicable == null ? null : append(tree, "axiomApplicable");
		
		Set<String> containers = AssayUtil.enumerateContainerTerms(assn);
		JSONArray listContainers = new JSONArray();
		for (int n = 0; n < flat.length; n++)
		{
			SchemaTree.Node node = flat[n];
			
			var prov = provCache.getTerm(node.uri);
		
			listDepth.put(node.depth);
			listParent.put(node.parentIndex);
			listAbbrev.put(ModelSchema.collapsePrefix(node.uri));
			listInSchema.put(node.inSchema);
			listProvisional.put(prov == null ? null : GetPropertyList.describeProvisional(prov));
			listInModel.put(hasModel.contains(node.uri));
			listSchemaCount.put(node.schemaCount);
			listChildCount.put(node.childCount);
			listAltLabels.put(node.altLabels);
			listExternalURLs.put(node.externalURLs);
			
			String name = Common.getCustomName(schema, assn.propURI, assn.groupNest(), node.uri);
			if (name == null) name = Common.getOntoValues().getLabel(node.uri);
			listName.put(name);

			if (applicable != null) listAxiomApplic.put(applicable.contains(node.uri));

			// store index of container nodes here
			if (containers.contains(node.uri)) listContainers.put(n);
		}
		tree.put("containers", listContainers);

		// so the caller can expand out the abbreviations...
		tree.put("prefixMap", ModelSchema.getPrefixes());

		return tree;
	}
	
	// convenience
	private static JSONArray append(JSONObject parent, String name)
	{
		JSONArray array = new JSONArray();
		parent.put(name, array);
		return array;
	}
}

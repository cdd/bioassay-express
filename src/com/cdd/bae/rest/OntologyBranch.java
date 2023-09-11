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

import org.apache.commons.lang3.*;
import org.json.*;

import com.cdd.bae.config.InitParams.*;
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

/*
	OntologyBranch: fetches tree-based information about an ontology branch
	
	Parameters:
		type: either "property" or "value"
		branches: array of URIs (full or prefixed) to incorporate into the returned tree(s); blank = return the roots
		maxDepth: (default = unlimited) how deep each branch is allowed to go; 0=just the requested URIs; 1=immediate children; etc.
		maxCount: (default = reasonable) maximum number of appendings to each root before switching to placeholder mode
		withDescr: (default = false) whether to include descriptions (can burn up bandwidth)
		withOther: (default = false) whether to include alternate labels and external URLs when available
		priority: array of URIs (full or prefixed) to ensure inclusion of regardless of max depth/count, even if it makes the tree lopsided
		pruned: (default = false) stem nodes that are not descendents of priority branches have their children truncated
*/

public class OntologyBranch extends RESTBaseServlet 
{
	private static final long serialVersionUID = 1L;
	
	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		var type = input.optString("type", "");
		OntologyTree onto = null;
		if (type.equals("property")) onto = Common.getOntoProps();
		else if (type.equals("value")) onto = Common.getOntoValues();
		else throw RESTException.bad("Must provide valid 'type' parameter.");
		
		var branches = input.optJSONArrayEmpty("branches").toStringArray();
		var priority = input.optJSONArrayEmpty("priority").toStringArray();
		boolean pruned = input.optBoolean("pruned", false);
		
		/*Util.writeln("** OntologyBranch parameters:");
		Util.writeln("   type: " + type);
		Util.writeln("   branches: " + Util.arrayStr(branches));
		Util.writeln("   priority: " + Util.arrayStr(priority));*/
				
		Set<String> prioritySet = new HashSet<>();
		for (var uri : priority)
		{
			var sequence = findURISequence(onto, ModelSchema.expandPrefix(uri));
			if (sequence != null)
			{
				// find the root branch: if it intersects with one of the parameters, stop there; if it goes all the way to the actual root, then
				// add it implicitly, if not already present
				int rootidx = sequence.length - 1;
				for (; rootidx > 0; rootidx--) if (ArrayUtils.contains(branches, sequence[rootidx])) break;
				
				if (branches.length > 0 && !ArrayUtils.contains(branches, sequence[rootidx])) branches = ArrayUtils.add(branches, sequence[rootidx]);
				
				for (int n = rootidx; n < sequence.length; n++) prioritySet.add(sequence[n]);
			}
		}
		
		var jsonResults = new JSONArray();
		
		if (pruned && priority.length > 0 && prioritySet.isEmpty()) return new JSONObject().put("trees", jsonResults); // stop here
		
		if (branches.length == 0)
		{
			var roots = onto.getRoots();
			branches = new String[roots.length];
			for (int n = 0; n < roots.length; n++) branches[n] = roots[n].uri;
		}
		
		int maxDepth = input.optInt("maxDepth", Integer.MAX_VALUE);
		int maxCount = input.optInt("maxCount", 10000);
		boolean withDescr = input.optBoolean("withDescr", false);
		boolean withOther = input.optBoolean("withOther", false);
		
		for (String uri : branches)
		{
			uri = ModelSchema.expandPrefix(uri);
			
			if (pruned && !prioritySet.isEmpty() && !prioritySet.contains(uri)) continue; // if prioritising a subsequence, skip missing roots
			
			var assembler = new Assembler(onto, uri, maxDepth, maxCount, withDescr, withOther, prioritySet, pruned);
			jsonResults.put(assembler.json);
		}

		return new JSONObject().put("trees", jsonResults);	
	}

	// ------------ private methods ------------
	
	// internal functionality for building up a partial branch of a tree section, based on requested parameters
	private static final class Assembler
	{
		JSONObject json = new JSONObject();
		
		OntologyTree onto;
		int maxDepth, maxCount;
		boolean withDescr, withOther;
		Set<String> prioritySet;
		boolean pruned;
		
		int total = 0;
	
		Assembler(OntologyTree onto, String uri, int maxDepth, int maxCount, boolean withDescr, boolean withOther, Set<String> prioritySet, boolean pruned)
		{
			this.onto = onto;
			this.maxDepth = maxDepth;
			this.maxCount = maxCount;
			this.withDescr = withDescr;
			this.withOther = withOther;
			this.prioritySet = prioritySet;
			this.pruned = pruned;
			
			if (onto.getBranch(uri) == null) return; // failed root lookup results in an empty object
			
			buildBranch(uri, json, 0);
		}
		
		void buildBranch(String uri, JSONObject json, int depth)
		{
			var branch = onto.getBranch(uri);
		
			json.put("uri", ModelSchema.collapsePrefix(uri));
			json.put("label", branch.label);
			if (branch.descendents > 0) json.put("descendents", branch.descendents);
			
			if (withDescr)
			{
				String descr = onto.getDescr(uri);
				if (descr != null) json.put("descr", descr);
			}
			if (withOther)
			{
				String[] altLabels = onto.getAltLabels(uri), externalURLs = onto.getExternalURLs(uri);
				if (altLabels != null) json.put("altLabels", altLabels);
				if (externalURLs != null) json.put("externalURLs", externalURLs);
			}
			
			total++;
			int nchild = branch.children.size();
			
			boolean havePriority = false;
			if (!prioritySet.isEmpty()) for (var child : branch.children) if (prioritySet.contains(child.uri))
			{
				havePriority = true;
				break;
			}
			
			if (nchild == 0) {}
			else if (!havePriority && (depth >= maxDepth || total + nchild >= maxCount))
			{
				var placeholders = new String[nchild];
				for (int n = 0; n < nchild; n++) placeholders[n] = ModelSchema.collapsePrefix(branch.children.get(n).uri);
				json.put("placeholders", placeholders);
			}
			else
			{
				var jsonChildren = new JSONArray();
				for (var child : branch.children)
				{
					var jsonChild = new JSONObject();

					if (pruned && !prioritySet.contains(child.uri)) continue;
					
					buildBranch(child.uri, jsonChild, depth + 1);
					jsonChildren.put(jsonChild);
				}
				json.put("children", jsonChildren);
			}
		}
	}
	
	// looks for a given URI in the tree, and returns the lineage (root first, given URI last) or null if not present
	private String[] findURISequence(OntologyTree onto, String uri)
	{
		var branch = onto.getBranch(uri);
		if (branch == null) return null;
		
		List<String> sequence = new ArrayList<>();
		for (; branch != null; branch = branch.parent) sequence.add(0, branch.uri);
		return Util.primString(sequence);
	}
}

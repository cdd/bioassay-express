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

package com.cdd.bae.util;

import com.cdd.bae.data.*;
import com.cdd.bae.data.OntologyTree.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;

/*
	Given an ontology tree, and a collection of value instructions from a template assignment, assembles a new tree out of the pieces.
*/

public class CompositeTree
{
	private OntologyTree src;
	private Schema.Assignment assn;
	
	private static final class Node
	{
		Node parent;
		String uri, label, descr;
		String[] altLabels, externalURLs;
		List<Node> children = new ArrayList<>();
		boolean inSchema = true, isProvisional = false;
	}
	
	private static final class Tree
	{
		Node root;
		Map<String, Node> map;
		String sourceParentURI = null;
	}

	// ------------ public methods ------------

	public CompositeTree(OntologyTree src, Schema.Assignment assn)
	{
		this.src = src;
		this.assn = assn;
	}
	
	// creates an ontology tree made up of 
	public SchemaTree compose()
	{
		List<Tree> treeBranches = new ArrayList<>();
		var provCache = Common.getProvCache();
		
		// part 1: all affirmative inclusions cause the creation of a branch of nodes (overlap not considered)
		for (var v : assn.values) if (v.spec == Schema.Specify.ITEM || v.spec == Schema.Specify.WHOLEBRANCH || v.spec == Schema.Specify.CONTAINER)
		{
			var branch = src.getBranch(v.uri);
			if (branch == null) 
			{
				// consider this as a directive to add a new item)
				if (v.spec == Schema.Specify.ITEM)
				{
					if (Util.notBlank(v.parentURI))
					{
						for (var tree : treeBranches)
						{
							var parent = tree.map.get(provCache.remapMaybe(v.parentURI));
							if (parent != null)
							{
								var node = new Node();
								node.parent = parent;
								node.uri = provCache.remapMaybe(v.uri);
								node.label = v.name;
								node.descr = v.descr;
								node.altLabels = v.altLabels;
								node.externalURLs = v.externalURLs;
								parent.children.add(node);
								tree.map.put(node.uri, node);
							}
						}
					}
					else
					{
						// treat it as a root
						var node = new Node();
						node.uri = provCache.remapMaybe(v.uri);
						node.label = v.name;
						node.descr = v.descr;
						node.altLabels = v.altLabels;
						node.externalURLs = v.externalURLs;
						
						var tree = new Tree();
						tree.root = node;
						tree.map = new HashMap<>();
						tree.map.put(node.uri, node);
						treeBranches.add(tree);
					}
				}
				continue;
			}
			
			// see if it's already in one of the tree hierarchies
			boolean already = false;
			for (var tree : treeBranches)
			{
				var node = tree.map.get(provCache.remapMaybe(v.uri));
				if (node == null) continue;
				
				// replace details if applicable
				if (v.spec == Schema.Specify.ITEM)
				{
					if (Util.notBlank(v.name)) node.label = v.name;
					if (Util.notBlank(v.descr)) node.descr = v.descr;
					if (Util.length(v.altLabels) > 0) node.altLabels = v.altLabels;
					if (Util.length(v.externalURLs) > 0) node.externalURLs = v.externalURLs;
					node.inSchema = true;
				}
				
				// if a parent is specified, move it to the new position
				String parentURI = provCache.remapMaybe(v.parentURI);
				if (Util.notBlank(parentURI))
				{
					var parent = tree.map.get(provCache.remapMaybe(parentURI));
					if (parent != null)
					{
						node.parent.children.remove(node);
						parent.children.add(node);
						node.parent = parent;
					}
				}
				
				already = true;
			}
			if (already) continue;
			
			Map<String, Node> map = new HashMap<>();
			var node = fromBranch(null, branch, map);
			if (v.spec == Schema.Specify.ITEM)
			{
				if (Util.notBlank(v.name)) node.label = v.name;
				if (Util.notBlank(v.descr)) node.descr = v.descr;
				if (Util.length(v.altLabels) > 0) node.altLabels = v.altLabels;
				if (Util.length(v.externalURLs) > 0) node.externalURLs = v.externalURLs;
			}
			
			if (v.spec == Schema.Specify.CONTAINER) node.inSchema = false;
			
			var tree = new Tree();
			tree.root = node;
			tree.map = map;

			if (v.parentURI != null) tree.sourceParentURI = provCache.remapMaybe(v.parentURI);
			else if (branch.parent != null) tree.sourceParentURI = branch.parent.uri;
			
			treeBranches.add(tree);
		}
		
		// part 2: add provisional terms into each tree wherever they might belong
		for (var tree : treeBranches) augmentProvisional(tree, provCache);
				
		// part 3: apply deletions
		for (var v : assn.values) if (v.spec == Schema.Specify.EXCLUDE || v.spec == Schema.Specify.EXCLUDEBRANCH)
		{
			for (int n = 0; n < treeBranches.size(); n++)
			{
				var tree = treeBranches.get(n);
				if (tree.root.uri.equals(provCache.remapMaybe(v.uri)))
				{
					// special case: destroy it
					treeBranches.remove(n);
					n--;
					continue;
				}
				
				// normal case: snip out a section of it
				var look = tree.map.get(provCache.remapMaybe(v.uri));
				if (look != null)
				{
					if (look.parent != null) look.parent.children.remove(look);
					pruneMap(look, tree.map);
				}
			}
		}
		
		if (treeBranches.size() == 0) return new SchemaTree(new SchemaTree.Node[0], assn);
		
		// part 4: any branch that has a root parent existing in another branch gets grafted onto that
		outer: for (int i = 0; i < treeBranches.size(); i++)
		{
			var tree1 = treeBranches.get(i);
			if (tree1.sourceParentURI == null) continue;
			for (int j = 0; j < treeBranches.size(); j++)
			{
				var tree2 = treeBranches.get(j);
				var parent = tree2.map.get(tree1.sourceParentURI);
				if (parent != null)
				{
					graftTree(tree2, parent, tree1.root);
					treeBranches.remove(i);
					i--;
					continue outer;
				}
			}
		}
		
		// part 5: merge branches
		var tree = treeBranches.get(0);
		for (int n = 1; n < treeBranches.size(); n++) 
		{
			var other = treeBranches.get(n);
			
			// it may have become graftable since the previous section
			if (other.sourceParentURI != null)
			{
				var parent = tree.map.get(other.sourceParentURI);
				if (parent != null)
				{
					graftTree(tree, parent, other.root);
					continue;
				}
			}
		
			// otherwise, have to extend the trunks in some capacity
			tree = mergeTrees(tree, other);
		}
	
		// convert to the required format
		List<SchemaTree.Node> flat = new ArrayList<>();
		if (tree.root.uri == null)
		{
			for (var child : tree.root.children) appendSchemaTree(flat, -1, child);
		}
		else appendSchemaTree(flat, -1, tree.root);
		return new SchemaTree(flat.toArray(new SchemaTree.Node[flat.size()]), assn);
	}
	
	// ------------ private methods ------------

	private Node fromBranch(Node parent, OntologyTree.Branch branch, Map<String, Node> mapNode)
	{
		var node = makeNode(branch);
		node.parent = parent;
		for (var child : branch.children) node.children.add(fromBranch(node, child, mapNode));
		mapNode.put(node.uri, node);
		return node;
	}
	
	private Node makeNode(OntologyTree.Branch branch)
	{
		var node = new Node();
		node.uri = Common.getProvCache().remapMaybe(branch.uri);
		node.label = branch.label;
		node.descr = src.getDescr(branch.uri);
		node.altLabels = src.getAltLabels(branch.uri);
		node.externalURLs = src.getExternalURLs(branch.uri);
		return node;
	}

	private void pruneMap(Node node, Map<String, Node> mapNode)
	{
		mapNode.remove(node.uri);
		for (var child : node.children) pruneMap(child, mapNode);
	}
	private void augmentMap(Node node, Map<String, Node> mapNode)
	{
		mapNode.put(node.uri, node);
		for (var child : node.children) augmentMap(child, mapNode);
	}
	
	// given that {node} has a parent URI that exists in {tree} as {parent}, copy over any missing constituents
	private void graftTree(Tree tree, Node parent, Node node)
	{
		// if the node's URI isn't present, add its whole branch as a child, and call it done
		var look = tree.map.get(node.uri);
		if (look == null)
		{
			node.parent = parent;
			parent.children.add(node);
			augmentMap(node, tree.map);
			return;
		}
		
		look.inSchema = look.inSchema || node.inSchema;
		
		// scan through all the children recursively
		for (var child : node.children) graftTree(tree, look, child);
	}
	
	// given two trees that do not "contain" each other, make them into a single tree by following the trunk backward, even if it leads to them
	// sharing a blank placeholder (i.e. they're both roots)
	private Tree mergeTrees(Tree tree1, Tree tree2)
	{
		var provCache = Common.getProvCache();
		String[] trunk1 = new String[0], trunk2 = new String[0];
		for (var branch = src.getBranch(tree1.sourceParentURI); ; branch = branch.parent)
		{
			trunk1 = ArrayUtils.add(trunk1, branch != null ? provCache.remapMaybe(branch.uri) : null);
			if (branch == null) break;
		}
		for (var branch = src.getBranch(tree2.sourceParentURI); ; branch = branch.parent)
		{
			trunk2 = ArrayUtils.add(trunk2, branch != null ? provCache.remapMaybe(branch.uri) : null);
			if (branch == null) break;
		}
		
		//Util.writeln("  trunk1:"+trunk1);
		//Util.writeln("  trunk2:"+trunk2);

		int best1 = trunk1.length + trunk2.length, best2 = best1; // bignum
		for (int i = 0; i < trunk1.length; i++) for (int j = 0; j < trunk2.length; j++) 
			if (Util.equals(trunk1[i], trunk2[j]) && i + j < best1 + best2) {best1 = i; best2 = j;}

		//Util.writeln("   best1="+best1+" best2="+best2);
		
		// if they both lead back to nothing in common, trim the trunks
		if (trunk1[best1] == null && trunk2[best2] == null)
		{
			tree1.root.parent = null;
			tree1.sourceParentURI = null;
			tree2.root.parent = null;
			tree2.sourceParentURI = null;
			trunk1 = new String[]{null};
			trunk2 = new String[]{null};
			best1 = best2 = 0;
		}
		
		for (int n = 0; n <= best1; n++)
		{
			if (Util.equals(trunk1[n], tree1.root.uri)) break;
			
			String uri = trunk1[n];
			Node parent;
			if (uri == null) 
				parent = new Node();
			else
				parent = makeNode(src.getBranch(uri));
			parent.inSchema = false;
			parent.children.add(tree1.root);
			tree1.root.parent = parent;
			tree1.map.put(uri, parent);
			tree1.root = parent;
		}
		for (int n = 0; n <= best2; n++)
		{
			if (Util.equals(trunk2[n], tree2.root.uri)) break;
			
			String uri = trunk2[n];
			Node parent;
			if (uri == null) 
				parent = new Node();
			else
				parent = makeNode(src.getBranch(uri));
			parent.inSchema = false;
			parent.children.add(tree2.root);
			tree2.root.parent = parent;
			tree2.map.put(uri, parent);
			tree2.root = parent;
		}
		
		if (tree2.root.children.isEmpty())
		{
			tree1.root.children.add(tree2.root);
			tree2.root.parent = tree1.root;
		}
		else graftTree(tree1, tree1.root, tree2.root.children.get(0));
		
		return tree1;
	}
	
	// convert a local node into a SchemaTree instance
	private void appendSchemaTree(List<SchemaTree.Node> flat, int parentIdx, Node node)
	{
		var parent = parentIdx < 0 ? null : flat.get(parentIdx);
	
		var st = new SchemaTree.Node();
		st.uri = node.uri;
		st.label = node.label;
		st.descr = node.descr;
		st.altLabels = node.altLabels;
		st.externalURLs = node.externalURLs;
		st.parent = parent;
		st.parentIndex = parentIdx;
		st.inSchema = node.inSchema;
		// (do something with node.isProvisional?)
		
		if (parent != null)
		{
			for (var look = parent; look != null; look = look.parent)
			{
				look.childCount++;
				if (node.inSchema) look.schemaCount++;
			}
			parent.children.add(st);
			st.depth = parent.depth + 1;
		}
		
		int nodeIdx = flat.size();
		flat.add(st);
		for (var child : node.children) appendSchemaTree(flat, nodeIdx, child);
	}
	
	// top up the tree with any provisional terms that may apply
	private void augmentProvisional(Tree tree, ProvisionalCache provCache)
	{
		List<DataObject.Provisional> provTerms = new ArrayList<>();
		for (var term : provCache.getAllTerms()) provTerms.add(term);
		
		while (true)
		{
			boolean anything = false;
			
			for (var it = provTerms.iterator(); it.hasNext();)
			{
				var term = it.next();
				String parentURI = provCache.remapMaybe(term.parentURI);
				var parentNode = tree.map.get(parentURI);
				if (parentNode == null) continue;
				
				var node = new Node();
				node.parent = parentNode;
				node.uri = provCache.remapMaybe(term.uri);
				node.label = term.label;
				node.descr = term.description;
				node.isProvisional = true;
				parentNode.children.add(node);				
				
				it.remove();
				anything = true;
			}
			
			if (!anything) break;
		}
	}
}



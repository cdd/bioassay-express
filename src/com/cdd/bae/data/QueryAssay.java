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

package com.cdd.bae.data;

import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

/*
	Defines a set of constraints that can be used to pare down the assays contained in the database. The simplest invocation involves
	specifying property/value pairs which must be present, or a hierarchy thereof.
	
	Basic format of the query string is:
	
		(propURI1=valueURI1,valueURI2,...);(propURI2:valueURI3,valueURI4,...);...
		
	Any URI can be specified as full or abbreviated (e.g. bao:BAO_00012345). Reserved characters should be escaped out with \.

	Clauses can alternately be specified by referring to assays explicitly, by:
		[id1,id2,...] (where id is either an internal assay ID, or prefixed with 'AID' for a PubChem ID)
		
	Explicitly specified assays will be 
	
	Values may be prefixed with * (match value and all sub-branches) or @ (match just the value itself). The default is to match all
	sub-branches. Prefixing with ! is a negation, i.e. !* or ! removes the branch and all sub-branches, while !@ removes just that item.
	Negation is done after all the included terms are worked out. Referring to terms that are not in the schema is a nop rather than an
	error.
*/

public class QueryAssay
{
	public static class Fail extends IOException 
	{
		Fail(String msg) {super(msg);}
		Fail(Throwable ex) {super(ex);}
	}
	
	public static final int MATCH_BRANCH = 0;
	public static final int MATCH_EXACT = 1;
	public static final int MATCH_NOT_BRANCH = 2;
	public static final int MATCH_NOT_EXACT = 3;
	
	public static class Value
	{
		public String uri;
		public int match; // one of MATCH_*

		public Value(String uri) {this.uri = uri; match = MATCH_BRANCH;}
		public Value(String uri, int match) {this.uri = uri; this.match = match;}
	}
	
	// terms to match, of the from {propURI:{values...}}	
	private Map<String, List<Value>> matchTerms = new LinkedHashMap<>();

	// on a per-schema basis: the given terms, expanded out using the matching criteria
	private Map<String, Map<String, Set<String>>> expandedTerms = new HashMap<>();
	
	private Set<Long> literalAssayID = new HashSet<>();
	//private Set<Integer> literalPubChemAID = new HashSet<>();
	private Set<String> literalUniqueID = new HashSet<>();

	// ------------ public methods ------------

	public QueryAssay()
	{
	}
	
	// creates a new query by unpacking from the serialised line notation
	private QueryAssay(String qstr) throws Fail
	{
		try
		{
			String[] parts = divideParts(qstr, ';');
			for (String part : parts)
			{
				if (part.length() == 0) ;
				else if (part.startsWith("(") && part.endsWith(")")) extractTerms(part);
				else if (part.startsWith("[") && part.endsWith("]")) extractLiterals(part);
				else throw new Fail("Unexpected part '" + part + "'");
			}
		}
		catch (Fail ex) {throw ex;}
		catch (Exception ex) {throw new Fail(ex);}
	}
	
	// public entrypoint for turning a query string into a parsed instance
	public static QueryAssay parse(String qstr) throws Fail {return new QueryAssay(qstr);}
	
	// applies all the constraints in the query to the given assay, and returns true if they are matched
	public boolean matchesAssay(DataObject.Assay assay)
	{
		// check literals first
		if (literalAssayID.contains(assay.assayID)) return true;
		if (Util.notBlank(assay.uniqueID) && literalUniqueID.contains(assay.uniqueID)) return true;
	
		if (Util.length(assay.annotations) == 0) return false;
		if (matchTerms.size() == 0) return literalAssayID.isEmpty() && literalUniqueID.isEmpty(); // null query: matches anything with any annotations
	
		//Schema schema = Common.getSchemaCAT(); // TBD: use the actual schema...
		Schema schema = Common.getSchema(assay.schemaURI);
		
		Map<String, Set<String>> expmap = expandedTerms.get(schema.getSchemaPrefix());
		if (expmap == null) expandedTerms.put(schema.getSchemaPrefix(), expmap = createExpandedTerms(schema));
		
		// list the properties in the query that must match something, and eliminate them as they do
		Set<String> reqProp = new HashSet<>(expmap.keySet());
		for (DataObject.Annotation annot : assay.annotations) if (reqProp.contains(annot.propURI))
		{
			if (expmap.get(annot.propURI).contains(annot.valueURI))
			{
				reqProp.remove(annot.propURI);
				if (reqProp.isEmpty()) break;
			}
		}
		return reqProp.isEmpty();
	}
	
	@Override
	public String toString()
	{
		StringBuilder buff = new StringBuilder();
		
		boolean first = true;
		for (String propURI : matchTerms.keySet())
		{
			List<Value> vlist = matchTerms.get(propURI);
			if (vlist.isEmpty()) continue;
		
			if (!first) buff.append(';'); else first = false;
		
			buff.append("(" + escape(ModelSchema.collapsePrefix(propURI)) + "=");
			for (int n = 0; n < vlist.size(); n++)
			{
				if (n > 0) buff.append(',');
				Value value = vlist.get(n);
				if (value.match == MATCH_BRANCH) ;
				else if (value.match == MATCH_EXACT) buff.append("@");
				else if (value.match == MATCH_NOT_BRANCH) buff.append("!");
				else if (value.match == MATCH_NOT_EXACT) buff.append("!@");
				buff.append(escape(ModelSchema.collapsePrefix(value.uri)));
			}
			buff.append(")");
		}
		
		return buff.toString();
	}
	
	public void addTerm(String propURI, Value value)
	{
		List<Value> vlist = matchTerms.get(propURI);
		if (vlist == null) matchTerms.put(propURI, vlist = new ArrayList<>());
		vlist.add(value);
	}
	
	// ------------ private methods ------------

	private static final String RESERVED = "\\()=,;*@!";

	private String escape(String str)
	{
		char[] list = str.toCharArray();
		boolean any = false;
		for (int n = 0; n < list.length; n++) if (RESERVED.indexOf(list[n]) >= 0) {any = true; break;}
		if (!any) return str;
		StringBuilder buff = new StringBuilder();
		for (int n = 0; n < list.length; n++)
		{
			if (RESERVED.indexOf(list[n]) >= 0) buff.append('\\');
			buff.append(list[n]);
		}
		return buff.toString();
	}
	
	private String unescape(String str)
	{
		char[] list = str.toCharArray();
		boolean any = false;
		for (int n = 0; n < list.length; n++) if (list[n] == '\\') {any = true; break;}
		if (!any) return str;
		
		StringBuilder buff = new StringBuilder();
		for (int n = 0; n < list.length; n++) if (list[n] != '\\') buff.append(list[n]);
		return buff.toString();
	}
	
	// splits the overall query into parts, with a given separators; honours escape characters and bracket levels
	private String[] divideParts(String qstr, char sep) throws Fail
	{
		List<Integer> divs = new ArrayList<>();
		int brklevel = 0;
		for (int n = 0; n < qstr.length(); n++)
		{
			char ch = qstr.charAt(n);
			if (ch == '\\') ;
			else if (ch == '(') brklevel++;
			else if (ch == ')') 
			{
				brklevel--;
				if (brklevel < 0) throw new Fail("Unexpected closing bracket at column " + (n + 1));
			}
			else if (brklevel == 0 && ch == sep) divs.add(n);
		}
		if (brklevel != 0) throw new Fail("Missing a closing bracket.");

		if (divs.isEmpty()) return new String[]{qstr};
		String[] parts = new String[divs.size() + 1];
		for (int n = 0; n < parts.length; n++)
		{
			int i1 = n == 0 ? 0 : divs.get(n - 1) + 1;
			int i2 = n < divs.size() ? divs.get(n) : qstr.length();
			parts[n] = qstr.substring(i1, i2);
		}
		return parts;
	}
	
	// pulls out the property/values content
	private void extractTerms(String part) throws Fail
	{
		String[] propval = divideParts(part.substring(1, part.length() - 1), '=');
		if (propval.length != 2) throw new Fail("Part has to start with (propURI=values...): '" + part + "'");
		
		String propURI = ModelSchema.expandPrefix(unescape(propval[0]));
		
		for (String vraw : divideParts(propval[1], ','))
		{
			String vstr = unescape(vraw);
			int match = MATCH_BRANCH;
			if (vstr.startsWith("*")) {match = MATCH_BRANCH; vstr = vstr.substring(1);}
			else if (vstr.startsWith("@")) {match = MATCH_EXACT; vstr = vstr.substring(1);}
			else if (vstr.startsWith("!*")) {match = MATCH_NOT_BRANCH; vstr = vstr.substring(2);}
			else if (vstr.startsWith("!@")) {match = MATCH_NOT_EXACT; vstr = vstr.substring(2);}
			
			String valueURI = ModelSchema.expandPrefix(vstr);
			if (!valueURI.startsWith("http://") && !valueURI.startsWith("https://")) throw new Fail("Invalid URI: '" + valueURI + "'");
			
			addTerm(propURI, new Value(valueURI, match));
		}
	}
	
	// pulls out literals (ID numbers)
	private void extractLiterals(String part) throws Fail
	{
		for (String bit : part.substring(1, part.length() - 1).split(","))
		{
			try
			{
				//if (bit.startsWith("AID")) literalPubChemAID.add(Integer.parseInt(bit.substring(3)));
				if (bit.startsWith("UID/")) literalUniqueID.add(bit.substring(4));
				else literalAssayID.add(Long.parseLong(bit));
			}
			catch (NumberFormatException ex) {throw new Fail("Invalid literal: " + bit);}
		}
	}
	
	// applies each property in the query to the schema branch, and enumerates the actual URIs that match
	private Map<String, Set<String>> createExpandedTerms(Schema schema)
	{
		Map<String, Set<String>> expmap = new HashMap<>();
		
		for (String propURI : matchTerms.keySet())
		{
			Schema.Assignment[] assnList = schema.findAssignmentByProperty(propURI);
			
			// if the property doesn't match anything in the template, mark it as empty, which prevents it from
			// matching anything
			if (assnList.length == 0) 
			{
				expmap.put(propURI, new HashSet<>());
				continue;
			}
			
			for (Schema.Assignment assn : assnList)
			{
				SchemaTree tree = Common.obtainTree(schema, assn);
				if (tree == null) continue;
				Map<String, SchemaTree.Node> treeNodes = tree.getTree();
				
				Set<String> include = new HashSet<>();
				Set<String> exclude = new HashSet<>();
				for (Value value : matchTerms.get(propURI))
				{
					if (value.match == MATCH_BRANCH) appendBranch(include, value.uri, treeNodes);
					else if (value.match == MATCH_EXACT) include.add(value.uri);
					else if (value.match == MATCH_NOT_BRANCH) appendBranch(include, value.uri, treeNodes);
					else if (value.match == MATCH_NOT_EXACT) exclude.add(value.uri);
				}
				for (String uri : exclude) include.remove(uri);
				
				Set<String> terms = expmap.get(propURI);
				if (terms == null) expmap.put(propURI, terms = new HashSet<>());
				terms.addAll(include);
			}
		}
		
		return expmap;	
	}
	
	// adds item and all sub-branched items to a set
	private void appendBranch(Set<String> set, String uri, Map<String, SchemaTree.Node> treeNodes)
	{
		SchemaTree.Node node = treeNodes.get(uri);
		if (node == null) return;
		List<SchemaTree.Node> stack = new ArrayList<>();
		stack.add(node);
		while (!stack.isEmpty())
		{
			node = stack.remove(0);
			set.add(node.uri);
			stack.addAll(node.children);
		}	
	}
}

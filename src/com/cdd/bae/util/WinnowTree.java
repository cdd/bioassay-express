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

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.util.KeywordMatcher.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.apache.commons.lang3.*;

/*
	 Heavy lifting for the selection-tree functionality: takes a series of criteria layers and winnows down the qualifying assays, and
	 notes the values & counts at each layer.
*/

public class WinnowTree
{
	public static final String SPECIAL_PROP_FULLTEXT = "FULLTEXT"; // use keywords to search the unstructured full text
	public static final String SPECIAL_PROP_KEYWORD = "KEYWORD"; // use keywords to search all annotation labels
	public static final String SPECIAL_PROP_IDENTIFIER = "IDENTIFIER"; // limit by identifier types and/or keywords applied to their payloads
	public static final String SPECIAL_VALUE_EMPTY = "EMPTY"; // value that explicitly matches nothing
	public static final String SPECIAL_VALUE_WITHTEXT = "WITHTEXT"; // value that explicitly matches non-URI annotatins

	public static final class Layer
	{
		public String propURI;
		public String[] groupNest;
		public String[] valueURIList;
		public String keywordSelector;
	}
	
	private Schema schema;
	private Layer[] layers;
	private boolean withUncurated;
	
	public static final class NodeResult
	{
		public int depth, parent;
		public String uri, name;
		public int count; // number of assays still in the game which have this term
		public int childCount; // as above, plus the counts for all child nodes
		public int totalCount; // number of assays with this term, without accounting for cumulative elimination
		public int curatedCount; // as for totalCount, but only those which have the curated flag set
	}
	
	public static final class LiteralResult
	{
		public String label;
		public int count;
	}
	
	public static final class Result
	{
		public NodeResult[] nodes;
		public LiteralResult[] literals;

		public Result(NodeResult[] nodes, LiteralResult[] literals)
		{
			this.nodes = nodes;
			this.literals = literals;
		}
	}
	private List<Result> results = new ArrayList<>();
	private long[] matchedAssayID = new long[0];
	
	// ------------ public methods ------------

	public WinnowTree(Schema schema, Layer[] layers, boolean withUncurated)
	{
		this.schema = schema;
		this.layers = layers;
		this.withUncurated = withUncurated;
	}
		
	// does the hard work to generate the results
	public void perform()
	{
		DataStore store = Common.getDataStore();
		
		Map<Long, DataObject.Assay> data = new HashMap<>();
		long[] rawAssayIDList = withUncurated ? store.assay().fetchAssayIDWithAnnotations() : store.assay().fetchAssayIDCuratedWithAnnotations();
		for (long assayID : rawAssayIDList) data.put(assayID, store.assay().getAssay(assayID));
		
		reduceDataMap(data);
		
		Long[] assayBoxed = data.keySet().toArray(new Long[data.size()]);
		Arrays.sort(assayBoxed, (assayID1, assayID2) ->
		{
			DataObject.Assay assay1 = data.get(assayID1), assay2 = data.get(assayID2);
			Identifier.UID uid1 = Common.getIdentifier().parseKey(assay1.uniqueID), uid2 = Common.getIdentifier().parseKey(assay2.uniqueID);
			if (uid1 == null && uid2 == null) return assayID1.compareTo(assayID2);
			if (uid1 == null) return -1;
			if (uid2 == null) return 1;
			return uid1.compareTo(uid2);
		});
		matchedAssayID = ArrayUtils.toPrimitive(assayBoxed);
	}
	
	public Result[] getResults() {return results.toArray(new Result[results.size()]);}
	public long[] getMatched() {return matchedAssayID;}

	// ------------ private methods ------------
	
	// take the populated list of assays as a starting point, remove those which don't meet the criteria
	protected void reduceDataMap(Map<Long, DataObject.Assay> data)
	{	
		Map<Long, DataObject.Assay> allData = new HashMap<>(data);
		
		for (int n = 0; n < layers.length; n++)
		{
			String propURI = layers[n].propURI;
			String[] groupNest = layers[n].groupNest, valueURIList = layers[n].valueURIList;
			String keyword = layers[n].keywordSelector;
		
			Set<String> valueList = new HashSet<>();
			boolean withEmpty = false, withText = false;
			if (valueURIList != null) for (String uri : valueURIList)
			{
				if (uri.equals(SPECIAL_VALUE_EMPTY)) withEmpty = true;
				else if (uri.equals(SPECIAL_VALUE_WITHTEXT)) withText = true;
				else valueList.add(uri);
			}
			
			// part 0: special cases
			if (propURI.equals(SPECIAL_PROP_FULLTEXT))
			{
				reduceDataFullText(data, new KeywordMatcher(keyword));
				results.add(new Result(null, null));
				continue;
			}
			else if (propURI.equals(SPECIAL_PROP_KEYWORD))
			{
				reduceDataKeywords(data, null, new KeywordMatcher(keyword));
				results.add(new Result(null, null));
				continue;
			}
			else if (propURI.equals(SPECIAL_PROP_IDENTIFIER))
			{
				NodeResult[] nodes = selectIdentifiers(data);
				reduceDataIdentifier(data, valueList.toArray(new String[valueList.size()]), new KeywordMatcher(keyword));
				results.add(new Result(nodes, null));
				continue;
			}
						
			// part 1: obtain the tree for the property and mask out just the entries that contained a term value
			// that was indeed referenced in the total list of assays eligible thus far
			
			Schema.Assignment[] assn = schema.findAssignmentByProperty(propURI, groupNest);
			// note: goes down hard if 0 entries (which is for sure a bug); maybe report it more nicely
			SchemaTree tree = Common.obtainTree(schema, assn[0]);

			// apply keyword first; note that if there's a keyword but no value limitations, skip the next step
			if (Util.notBlank(keyword)) 
			{
				reduceDataKeywords(data, assn, new KeywordMatcher(keyword));
				if (valueList.isEmpty() && !withEmpty)
				{
					SchemaTree.Node[] flat = tree.getFlat();
					flat = appendAbsenceBranch(flat);
					NodeResult[] nodes = selectReducedTree(flat, propURI, groupNest, data, allData);
					LiteralResult[] literals = selectCommonLiterals(propURI, groupNest, data);
					results.add(new Result(nodes, literals));
					continue;
				}
			}

			// different pathway depending on whether the template is marked as being a literal-based type, or ontology-based (default)
			Schema.Suggestions suggest = assn[0].suggestions;
			
			if (suggest == Schema.Suggestions.STRING || suggest == Schema.Suggestions.INTEGER || suggest == Schema.Suggestions.NUMBER)
			{
				// !! consider putting this above the reduceDataKeywords(..) call? because the filter affects the filter... possibly weird
				
				LiteralResult[] literals = selectCommonLiterals(propURI, groupNest, data);
				results.add(new Result(null, literals));				
			}
			else
			{
				SchemaTree.Node[] flat = tree.getFlat();
				flat = appendAbsenceBranch(flat);
				NodeResult[] nodes = selectReducedTree(flat, propURI, groupNest, data, allData);
				results.add(new Result(nodes, null)); // note: could consider enumerating literals... but probably not
			}
			
			// part 2: anything in the data list must match one of the propURI/valueURIList cases, else it
			// gets the chop

			if (valueList.isEmpty() && !withEmpty && !withText) {data.clear(); continue;} // quick out
			skip: for (Iterator<DataObject.Assay> it = data.values().iterator(); it.hasNext();)
			{
				DataObject.Assay curAssay = it.next();

				DataObject.Annotation[] annotList = curAssay.annotations;
				boolean propHit = false;
				
				// see if any of the annotation terms match the value list (which isn't necessarily populated)
				for (DataObject.Annotation annot : annotList) if (annot.matchesProperty(propURI, groupNest))
				{
					propHit = true;
					if (valueList.contains(annot.valueURI)) continue skip; // keep this assay
				}
				
				// if looking for anything-with-text, match it here
				if (withText) for (DataObject.TextLabel lbl : curAssay.textLabels)
				{
					if (lbl.matchesProperty(propURI, groupNest)) continue skip; // keep this assay
				}
				
				if (!propHit)
				{
					boolean foundText = false;
					for (DataObject.TextLabel lbl : curAssay.textLabels)
					{
						// accept assay if literal matches even if no annotation does
						if (Schema.compatiblePropGroupNest(propURI, groupNest, lbl.propURI, lbl.groupNest))
						{
							foundText = true;
							break;
						}
					}
					if (!foundText && withEmpty) continue; // accept empty or literal match, exclusive
					if (foundText && withText) continue; // of each other
				}
				it.remove();
			}
		}
	}
	
	// create a mask for all of the eligible values in a flattened list, and creates something to return
	protected static NodeResult[] selectReducedTree(SchemaTree.Node[] flat, String propURI, String[] groupNest,
									    			   Map<Long, DataObject.Assay> data, Map<Long, DataObject.Assay> allData)
	{
		List<NodeResult> list = new ArrayList<>();
		
		Map<String, Integer> valueCount = getValueCounts(propURI, groupNest, data, false);
		Map<String, Integer> totalCount = getValueCounts(propURI, groupNest, allData, false);
		Map<String, Integer> curatedCount = getValueCounts(propURI, groupNest, data, true); // how many assays for this value are curated

		boolean[] mask = new boolean[flat.length];
		int[] count = new int[flat.length];
		for (int n = 0; n < flat.length; n++) if (valueCount.containsKey(flat[n].uri))
		{
			mask[n] = true;
			count[n] = valueCount.get(flat[n].uri);
			for (int p = flat[n].parentIndex; p >= 0; p = flat[p].parentIndex) mask[p] = true;
		}
		
		int[] mapidx = new int[flat.length];
		for (int n = 0, p = 0; n < flat.length; n++) if (mask[n]) mapidx[n] = p++;
	
		for (int n = 0; n < flat.length; n++) if (mask[n])
		{
			int childCount = count[n];
			for (int i = n + 1; i < flat.length && flat[i].depth > flat[n].depth; i++) childCount += count[i];
		
			NodeResult node = new NodeResult();
			node.depth = flat[n].depth;
			node.parent = flat[n].parentIndex < 0 ? -1 : mapidx[flat[n].parentIndex];
			node.uri = flat[n].uri;
			node.name = flat[n].label;
			node.count = count[n];
			node.childCount = childCount;
			node.totalCount = totalCount.getOrDefault(flat[n].uri, 0);
			node.curatedCount = curatedCount.getOrDefault(flat[n].uri, 0);
			list.add(node);
		}
		
		return list.toArray(new NodeResult[list.size()]);
	}
	
	// tallies up the counts for an assignment section, given a list of applicable assays
	protected static Map<String, Integer> getValueCounts(String propURI, String[] groupNest, Map<Long, DataObject.Assay> data, boolean curatedOnly)
	{
		Map<String, Integer> counts = new HashMap<>();
		for (DataObject.Assay assay : data.values()) 
		{
			if (curatedOnly && !assay.isCurated) continue;
			for (DataObject.Annotation annot : assay.annotations)
			{
				if (Schema.compatiblePropGroupNest(propURI, groupNest, annot.propURI, annot.groupNest))
					counts.put(annot.valueURI, counts.getOrDefault(annot.valueURI, 0) + 1);
			}
		}
		return counts;
	}
	
	// eliminates assays unless one of the annotations or text labels passes the keyword matcher; if a list of assignments is given,
	// only applies to those; if null, any match is considered good enough
	protected static void reduceDataKeywords(Map<Long, DataObject.Assay> data, Schema.Assignment[] assnList, KeywordMatcher matcher)
	{
		skip: for (Iterator<DataObject.Assay> it = data.values().iterator(); it.hasNext();)
		{
			// if any annotation or text label passes, will skip the outer loop

			DataObject.Assay assay = it.next();
			Schema schema = Common.getSchema(assay.schemaURI);
			for (DataObject.Annotation annot : assay.annotations)
			{
				if (assnList != null && !hasMatchingProperty(annot, assnList)) continue;

				String label = null;
				if (schema != null) label = Common.getCustomName(schema, annot.propURI, annot.groupNest, annot.valueURI);
				if (label == null) label = Common.getOntoValues().getLabel(annot.valueURI);
				if (label != null && matcher.matches(label)) continue skip;

				String[] altLabels = Common.getOntoValues().getAltLabels(annot.valueURI);
				if (!ArrayUtils.isEmpty(altLabels)) for (String altLab : altLabels)
				{
					if (altLab != null && matcher.matches(altLab)) continue skip;
				}
			}

			for (DataObject.TextLabel label : assay.textLabels)
			{
				if (assnList != null && !hasMatchingProperty(label, assnList)) continue;
				if (matcher.matches(label.text)) continue skip;
			}
			it.remove();
		}
	}

	protected static boolean hasMatchingProperty(DataObject.TextLabel label, Schema.Assignment[] assnList)
	{
		for (Schema.Assignment assn : assnList)
			if (label.matchesProperty(assn.propURI, assn.groupNest())) return true;
		return false;
	}

	protected static boolean hasMatchingProperty(DataObject.Annotation annotation, Schema.Assignment[] assnList)
	{
		for (Schema.Assignment assn : assnList)
			if (annotation.matchesProperty(assn.propURI, assn.groupNest())) return true;
		return false;
	}
	
	// eliminates assays unless their full text passes the keyword matcher
	protected static void reduceDataFullText(Map<Long, DataObject.Assay> data, KeywordMatcher matcher)
	{
		for (Iterator<DataObject.Assay> it = data.values().iterator(); it.hasNext();)
		{
			String fullText = it.next().text;
			if (!matcher.matches(fullText)) it.remove();
		}	
	}

	// goes through the identifiers and applies the matcher criteria	
	protected static void reduceDataIdentifier(Map<Long, DataObject.Assay> data, String[] prefixes, KeywordMatcher matcher)
	{
		if (prefixes.length == 0 && matcher.isBlank())
		{
			data.clear();
			return;
		}
	
		Set<String> allowed = new HashSet<>(Arrays.asList(prefixes));
		Identifier ident = Common.getIdentifier();

		List<String> matcherList = new ArrayList<String>();
		
		for (Block block : matcher.getSelectorBlocks()) matcherList.add(block.value);
		
		for (Iterator<DataObject.Assay> it = data.values().iterator(); it.hasNext();)
		{
			String uniqueID = it.next().uniqueID;
			Identifier.UID uid = ident.parseKey(uniqueID);
			
			if (uid == null) it.remove();
			else if (prefixes.length > 0 && !allowed.contains(uid.source.prefix)) it.remove();
			else if (!matcher.isBlank() && (!matcher.matches(uid.id) && !matcherList.contains(uniqueID))) it.remove();
		}	
	}

	// create a mask for all of the eligible values in a flattened list, and creates something to return
	protected static LiteralResult[] selectCommonLiterals(String propURI, String[] groupNest, Map<Long, DataObject.Assay> data)
	{
		List<LiteralResult> list = new ArrayList<>();
	
		Map<String, Integer> labelCounts = new HashMap<>();
		
		for (DataObject.Assay assay : data.values())
		{
			if (assay == null || assay.textLabels == null) continue;
			for (DataObject.TextLabel lbl : assay.textLabels)
			{
				if (!Schema.compatiblePropGroupNest(propURI, groupNest, lbl.propURI, lbl.groupNest)) continue;
				labelCounts.put(lbl.text, labelCounts.getOrDefault(lbl.text, 0) + 1);
			}
		}
		
		String[] labels = labelCounts.keySet().toArray(new String[labelCounts.size()]);
		Arrays.sort(labels, (l1, l2) ->
		{
			int c1 = labelCounts.get(l1), c2 = labelCounts.get(l2);
			if (c1 > c2) return -1;
			if (c1 < c2) return 1;
			return l1.compareTo(l2);
		});
	
		for (String label : labels)
		{
			LiteralResult literal = new LiteralResult();
			literal.label = label;
			literal.count = labelCounts.get(label);
			list.add(literal);
		}
		
		return list.toArray(new LiteralResult[list.size()]);
	}
	
	// create a list of all the available identifiers that have survived the triage
	protected static NodeResult[] selectIdentifiers(Map<Long, DataObject.Assay> data)
	{
		List<NodeResult> list = new ArrayList<>();
		
		Identifier ident = Common.getIdentifier();
	
		Map<String, Integer> identCount = new HashMap<>();
		for (DataObject.Assay assay : data.values())
		{
			Identifier.UID uid = ident.parseKey(assay.uniqueID);
			if (uid == null) continue;
			identCount.put(uid.source.prefix, identCount.getOrDefault(uid.source.prefix, 0) + 1);
		}
		
		for (Identifier.Source source : Common.getIdentifier().getSources()) 
		{
			Integer count = identCount.get(source.prefix);
			if (count == null) continue;
			
			NodeResult node = new NodeResult(); // pretending to be a "node"
			node.depth = 0;
			node.parent = -1;
			node.uri = source.prefix;
			node.name = source.name;
			node.count = count;
			node.childCount = 0;
			node.totalCount = count;
			list.add(node);
		}
		
		return list.toArray(new NodeResult[list.size()]);
	}
	
	// create an artificial "absence branch" at the end of the 
	private SchemaTree.Node[] appendAbsenceBranch(SchemaTree.Node[] flat)
	{
		int sz = flat.length;
		flat = Arrays.copyOf(flat, sz + 1 + AssayUtil.ABSENCE_TERMS.length);
		
		SchemaTree.Node parent = flat[sz] = new SchemaTree.Node();
		parent.uri = AssayUtil.URI_ABSENCE;
		parent.depth = 0;
		parent.parentIndex = -1;
		parent.childCount = AssayUtil.ABSENCE_TERMS.length;
		
		for (int n = 0; n < AssayUtil.ABSENCE_TERMS.length; n++)
		{
			SchemaTree.Node node = flat[sz + 1 + n] = new SchemaTree.Node();
			node.uri = AssayUtil.ABSENCE_TERMS[n];
			node.depth = 1;
			node.parent = parent;
			node.parentIndex = sz;
		}
		
		for (int n = sz; n < flat.length; n++)
		{
			flat[n].label = AssayUtil.ABSENCE_LABEL.get(flat[n].uri);
			flat[n].descr = AssayUtil.ABSENCE_DESCR.get(flat[n].uri);
			flat[n].inSchema = true;
			parent.children.add(flat[n]);
		}
		
		return flat;
	}
}



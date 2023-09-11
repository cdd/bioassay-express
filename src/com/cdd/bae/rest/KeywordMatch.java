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
import com.cdd.bao.template.SchemaTree.*;
import com.cdd.bao.util.*;

import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

import org.apache.commons.lang3.*;
import org.apache.commons.text.similarity.*;
import org.json.*;

/*
	Keyword matching: takes an arbitrary short string, and a partially annotated assay for context, and makes a judgment call about
	which (if any) annotations within the schema have some kind of match. The results are sorted from most to least likely.
	
	Parameters:
		keywords: short string
		schemaURI: which schema (null = common assay template)
		schemaBranches, schemaDuplication: optional details
		annotations: preexisting annotations [[propURI,valueURI],...]
		propURI: (optional) restrict to an assignment
		groupNest: (optional) ditto
		select: three possible states -
					null: all annotations are allowed (default)
					[]: all annotations that are used somewhere are allowed
					[...]: tree selection criteria are applied (same as SelectionTree)
*/

public class KeywordMatch extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;
	protected static final int MAX_PROPOSALS = 100;
	private static FuzzyScore scorer = new FuzzyScore(Locale.ENGLISH);

	protected static final class Proposal
	{
		Schema.Assignment assn;
		SchemaTree.Node node;
		int matchCount;
		// highlighted labels
		String highlightLabel = null;
		String highlightAltLabel = null;
		
		Proposal(Schema.Assignment assn, SchemaTree.Node node, int matchCount)
		{
			this.assn = assn; 
			this.node = node; 
			this.matchCount = matchCount;
		}
	}

	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		String keywords = input.getString("keywords");	
		Schema schema = input.isNull("schemaURI") ? Common.getSchemaCAT() : Common.getSchema(input.optString("schemaURI", null));
		String propURI = input.optString("propURI");
		String[] groupNest = input.optJSONArrayEmpty("groupNest").toStringArray();

		JSONArray schemaBranches = input.optJSONArray("schemaBranches"), schemaDuplication = input.optJSONArray("schemaDuplication");
		if (schemaBranches != null || schemaDuplication != null)
		{
			SchemaDynamic graft = new SchemaDynamic(schema.getSchemaPrefix(), schemaBranches, schemaDuplication);
			SchemaDynamic.SubTemplate subt = graft.relativeAssignment(propURI, groupNest);
			if (subt != null)
			{
				schema = subt.schema;
				groupNest = subt.groupNest;
			}
		}
		
		JSONArray jsonAnnot = input.getJSONArray("annotations");
		Set<String> already = new HashSet<>();
		for (int n = 0; n < jsonAnnot.length(); n++)
		{
			JSONArray pair = jsonAnnot.getJSONArray(n);
			if (pair.length() < 2) continue;
			String annotPropURI = pair.getString(0), value = pair.getString(1);
			String[] annotGroupNest = Arrays.copyOfRange(pair.toStringArray(), 2, pair.length());
			already.add(Schema.keyPropGroupValue(annotPropURI, annotGroupNest, value));
		}	
		
		WinnowTree.Layer[] layers = SelectionTree.parseSelectionLayers(input.optJSONArray("select"));
		Set<String> eligibility = null;
		if (layers != null)
		{
			eligibility = new HashSet<>();
			if (layers.length > 0)
			{
				WinnowTree winnow = new WinnowTree(schema, layers, false);
				winnow.perform();
				determineEligibility(eligibility, winnow.getMatched(), layers, winnow.getResults());
			}
			else
			{
				determineEligibility(eligibility, Common.getDataStore().assay().fetchAllCuratedAssayID(), layers, new WinnowTree.Result[0]);
			}
		}
		
		List<Proposal> proposals = new ArrayList<>();
		
		JSONArray results = new JSONArray();
		searchByPropURI(proposals, keywords, schema);
		if (proposals.isEmpty()) determineProposals(proposals, keywords, schema, already, propURI, groupNest, eligibility);
		for (Proposal prop : proposals)
		{
			JSONObject obj = new JSONObject();
			obj.put("propURI", prop.assn.propURI);
			obj.put("propLabel", prop.assn.name);
			obj.put("valueURI", prop.node.uri);
			obj.put("valueLabel", prop.node.label);
			obj.put("groupNest", prop.assn.groupNest());
			obj.put("groupLabel", prop.assn.groupLabel());

			String[] altLabels = Common.getOntoValues().getAltLabels(prop.node.uri);
			if (altLabels != null) obj.put("altLabels", new JSONArray(altLabels));

			obj.put("highlightLabel", prop.highlightLabel);
			obj.put("highlightAltLabel", prop.highlightAltLabel);
			results.put(obj);
		}
		return new JSONObject().put(RETURN_JSONARRAY, results);
	}

	// ------------ private methods ------------
	
	protected static void searchByPropURI(List<Proposal> proposals, String keywords, Schema schema)
	{
		Collection<String> prefixes = ModelSchema.getPrefixes().values();
		Set<String> wordList = new HashSet<>();
		for (String keyword : keywords.split("\\s+"))
		{
			if (keyword.toLowerCase().startsWith("http"))
			{
				wordList.add(keyword);
			}
			else if (keyword.contains(":"))
			{
				String[] s = keyword.split(":");
				wordList.add(ModelSchema.expandPrefix(s[0].toLowerCase() + ":" + s[1].toUpperCase()));
			}
			else
				wordList.addAll(prefixes.stream().map(pfx -> pfx + keyword.toUpperCase()).collect(Collectors.toList()));
		}
		List<Schema.Group> stack = new ArrayList<>();
		stack.add(schema.getRoot());
		while (!stack.isEmpty())
		{
			Schema.Group grp = stack.remove(0);
			for (Schema.Assignment assn : grp.assignments) 
			{
				for (SchemaTree.Node node : Common.obtainTree(schema, assn).getFlat())
				{
					if (!wordList.contains(node.uri)) continue;
					Proposal proposal = new Proposal(assn, node, 1);
					proposal.highlightLabel = node.label;
					proposal.highlightAltLabel = "**" + ModelSchema.collapsePrefix(node.uri) + "**";
					proposals.add(proposal);
				}
			}
			stack.addAll(grp.subGroups);
		}
	}

	// perform all of the keyword matchings, for everything in the schema (or just one assignment if propURI/groupNest given), and with an
	// optional list of values that are eligible for consideration
	protected static void determineProposals(List<Proposal> proposals, String keywords, 
								   Schema schema, Set<String> already, String propURI, String[] groupNest, 
								   Set<String> eligibility)
	{
		Set<String> wordList = toWords(keywords);

		// gather all preliminary matches
		List<Schema.Group> stack = new ArrayList<>();
		stack.add(schema.getRoot());
		while (!stack.isEmpty())
		{
			Schema.Group grp = stack.remove(0);
			for (Schema.Assignment assn : grp.assignments) 
			{
				if (Util.notBlank(propURI)) 
					if (!Schema.samePropGroupNest(propURI, groupNest, assn.propURI, assn.groupNest())) continue;
				collectTerms(schema, proposals, assn, wordList, already, eligibility);
			}
			stack.addAll(grp.subGroups);
		}
	
		// sort by most well matched first; reduce the total size by winnowing,
		// or just plain dumping if there's a huge number all the same
		Collections.sort(proposals, (p1, p2) -> p2.matchCount - p1.matchCount);
		for (int n = MAX_PROPOSALS; n < proposals.size(); n++) 
			if (n == 1000 || proposals.get(n).matchCount < proposals.get(n - 1).matchCount)
			{
				proposals.subList(n, proposals.size()).clear();
				break;
			}
	
		// reorder: anything that exists in the model collection goes to the front of the line
		/*for (int i = 0, j = 0; i < proposals.size(); i++) 
		{
			Proposal prop = proposals.get(i);
			if (!store.annot().hasAnnotation(prop.assn.propURI, prop.node.uri)) continue;
			proposals.remove(i);
			proposals.add(j++, prop);
		}*/
		
		
		// fine-grained sorting
		wordList.add(String.join(" ", wordList));
		for (Proposal proposal : proposals)
			fuzzyScore(proposal, wordList);
		Collections.sort(proposals, (p1, p2) -> p2.matchCount - p1.matchCount);

		// if there are still lots of contenders, want to keep the bandwidth fairly low
		if (proposals.size() > MAX_PROPOSALS) 
			proposals.subList(MAX_PROPOSALS, proposals.size()).clear();
	}

	// gather all terms from a particular assignment that match in some capacity
	protected static void collectTerms(Schema schema, List<Proposal> proposals, Schema.Assignment assn, Set<String> wordList, 
									  Set<String> already, Set<String> eligibility)
	{
		// nothing to do if assignment is found in already
		if (already.contains(Schema.keyPropGroup(assn.propURI, assn.groupNest()))) return;
		
		Set<String> containers = AssayUtil.enumerateContainerTerms(assn);
		Set<String> propBits = toWords(assn.name);
		
		// find proposals, skipping over container terms
		SchemaTree tree = Common.obtainTree(schema, assn);
		for (SchemaTree.Node node : tree.getFlat())
		{
			if (containers.contains(node.uri)) continue;
			if (eligibility != null && !eligibility.contains(Schema.keyPropGroupValue(assn.propURI, assn.groupNest(), node.uri))) continue;

			// augment labelBits with altLabels, if any
			Set<String> valueBits = toWords(node.label);
			if (!ArrayUtils.isEmpty(node.altLabels)) for (String altLbl : node.altLabels)
				valueBits.addAll(toWords(altLbl));

			// has to have a preliminary match within the value part, otherwise skip
			if (!hasPreliminaryMatch(wordList, valueBits)) continue;

			// add proposal when match count is positive
			int matchCount = tallyMatches(wordList, propBits, valueBits);
			if (matchCount > 0) proposals.add(new Proposal(assn, node, matchCount));
		}
	}
	
	private static Set<String> toWords(String s)
	{
		return new HashSet<>(Arrays.asList(s.trim().toLowerCase().split("\\s+")));
	}
	
	// check if there is a match with the value part
	private static boolean hasPreliminaryMatch(Set<String> wordList, Set<String> valueBits)
	{
		for (String bit : valueBits) for (String word : wordList)
		{
			if (bit.startsWith(word) || (word.length() > 1 && bit.indexOf(word) >= 0)) return true;
		}
		return false;
	}
	
	// performs a relatively coarse estimate of "score" for matching keywords to a prop/value pair
	protected static int tallyMatches(Set<String> wordList, Set<String> propBits, Set<String> valueBits)
	{
		int matchCount = 0;
		for (String word : wordList)
		{
			// better scores for matching value; and this is first priority
			int score = 2 * getWordScore(word, valueBits);
			
			// if not value, then property
			if (score == 0) score = getWordScore(word, propBits);
			
			if (score > 0)
				matchCount += score;
			else
				matchCount--; // any part matches nothing: penalty
		}
		return matchCount;
	}

	protected static void fuzzyScore(Proposal proposal, Set<String> wordList)
	{
		Node node = proposal.node;
		
		int score = scoreBit(node.label.trim().toLowerCase(), wordList);
		proposal.highlightLabel = highlightQuery(node.label, wordList);
		
		if (!ArrayUtils.isEmpty(node.altLabels)) for (String altLbl : node.altLabels)
		{
			String bit = altLbl.trim().toLowerCase();
			int bitScore = scoreBit(bit, wordList);
			if (bitScore > score)
			{
				score = bitScore;
				proposal.highlightAltLabel = highlightQuery(altLbl, wordList);
			}
		}
		proposal.matchCount = score;
	}
	
	private static int scoreBit(String bit, Set<String> wordList)
	{
		int bitScore = 0;
		for (String word : wordList)
		{
			bitScore += scorer.fuzzyScore(bit, word);
		}
		return bitScore - bit.length();
	}
	
	protected static String highlightQuery(String label, Set<String> wordList)
	{
		String lcLabel = label.toLowerCase();
		int[] match = new int[label.length()];
		Arrays.fill(match, 0);
		for (String word : wordList)
			for (int i = 0; i < word.length() - 1; i++)
			{
				Matcher m = Pattern.compile(word.substring(i, i + 2)).matcher(lcLabel);
				while (m.find())
				{
					match[m.start()] = 1;
					match[m.start() + 1] = 1;
				}
			}
		int last = 0;
		for (int i = match.length; i > 1; i--)
		{
			if (match[i - 1] == last) continue;
			label = label.substring(0, i) + "**" + label.substring(i);
			last = 1 - last;
		}
		if (last == 1) label = "**" + label;
		return label;
	}

	protected static int getWordScore(String word, Set<String> wordSet)
	{
		int score = 0;
		for (String bit : wordSet)
		{
			if (bit.equals(word)) score = 3;
			else if (bit.startsWith(word)) score = 2;
			else if (word.length() > 1 && bit.indexOf(word) >= 0) score = 1;
			if (score > 0) {wordSet.remove(bit); break;}
		}
		return score;
	}
	
	// accumulates all of the terms that have survived the winnowing process, i.e. must occur in the given list of assays, 
	protected static void determineEligibility(Set<String> eligible, long[] assayIDList, WinnowTree.Layer[] layers, WinnowTree.Result[] winnowed)
	{
		// first part: add the assignments as they qualify in the explicit layers
		Set<String> assnLayers = new HashSet<>();
		for (int n = 0; n < winnowed.length; n++)
		{
			assnLayers.add(Schema.keyPropGroup(layers[n].propURI, layers[n].groupNest));
			
			if (winnowed[n].nodes != null) for (WinnowTree.NodeResult node : winnowed[n].nodes)
			{
				if (n < winnowed.length - 1)
				{
					// TODO: ideally check to see if adding this node would reduce any of the subsequent layers 0 qualifying matches,
					// because this will cause following layer(s) to filter out terms that are in the selection list; note that the UI
					// itself doesn't prevent this either; for now, the temporary solution is to exclude terms that occur in layers other
					// than the final one... this will remove some valid options, but it greatly reduces the potential for confusion
					// when a term is selected from the list such that it breaks subsequent layers and excludes all results without
					// any obvious reason
					continue;
				}
				eligible.add(Schema.keyPropGroupValue(layers[n].propURI, layers[n].groupNest, node.uri));
			}
		}
	
		// second part: for all of the assays that are selected by the layers, add all of the annotations that are not part of the layer spec
		DataStore store = Common.getDataStore();
		for (long assayID : assayIDList)
		{
			DataObject.Assay assay = store.assay().getAssay(assayID);
			if (assay == null) continue;
			for (DataObject.Annotation annot : assay.annotations) 
			{
				if (assnLayers.contains(Schema.keyPropGroup(annot.propURI, annot.groupNest))) continue;
				eligible.add(Schema.keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueURI));
			}
		}
	}
}

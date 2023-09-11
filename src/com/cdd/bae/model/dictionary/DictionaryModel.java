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

package com.cdd.bae.model.dictionary;

import com.cdd.bae.model.hankcs.*;
import com.cdd.bao.template.*;
import com.cdd.bao.template.SchemaTree.Node;

import java.util.*;
import java.util.stream.*;

/*
	Generic dictionary model
*/

public class DictionaryModel extends AhoCorasickDoubleArrayTrie<SchemaTree.Node>
{

	String propURI;
	private static String ignoreChars1 = "()[]-\n\t";
	private static String ignoreChars2 = ignoreChars1 + " .,";

	public DictionaryModel(String propURI, Map<String, SchemaTree.Node> map)
	{
		super();
		this.propURI = propURI;
		build(map);
	}

	@Override
	public List<Hit<Node>> parseText(String text)
	{
		String textLower = text.toLowerCase();
		int n = textLower.length();
		int currentState = 0;
		// Initiate the parsing with a space at the start
		currentState = getState(currentState, ' ');

		List<Hit<Node>> collectedEmits = new LinkedList<>();
		for (int i = 0; i < n; i++)
		{
			char ch = textLower.charAt(i);

			// skip consecutive whitespaces
			boolean nextIsWhitespace = (i == n - 1) || Character.isWhitespace(textLower.charAt(i + 1));
			if (Character.isWhitespace(ch) && nextIsWhitespace) continue;

			// some characters to ignore
			boolean doConvertToSpace = (ignoreChars1.indexOf(ch) >= 0) || (".,".indexOf(ch) >= 0 && nextIsWhitespace);
			if (doConvertToSpace) ch = ' ';

			currentState = getState(currentState, ch);
			storeEmits(textLower, i + 1, currentState, collectedEmits);
		}
		// close the parsing by adding a space at the end
		currentState = getState(currentState, ' ');
		storeEmits(textLower, n + 1, currentState, collectedEmits);

		// fix the begin of emits to match only the words
		for (DictionaryModel.Hit<Node> emit : collectedEmits) if (emit.begin >= 0 && emit.end > 0) // !! HACKY PATCH
		{
			while (emit.begin < emit.end - 1 && ignoreChars2.indexOf(textLower.charAt(emit.begin)) >= 0) emit.begin++;
			while (emit.end > emit.begin + 1 && ignoreChars2.indexOf(textLower.charAt(emit.end - 1)) >= 0) emit.end--;
		}
		return collectedEmits; 
		
	}

	protected void storeEmits(String textLower, int position, int currentState, List<Hit<Node>> collectedEmits)
	{
		int before = collectedEmits.size();
		storeEmits(position, currentState, collectedEmits);
		if (before == collectedEmits.size()) return;
		
		// fix the new emit
		DictionaryModel.Hit<Node> emit = collectedEmits.get(collectedEmits.size() - 1);
		// shift the begin of the matching if the matched area contains multiple whitespace
		for (int j = Math.min(emit.end, textLower.length()) - 2; j > emit.begin; j--)
			if (Character.isWhitespace(textLower.charAt(j)) && Character.isWhitespace(textLower.charAt(j + 1))) emit.begin--;

		emit.begin = Math.max(emit.begin, 0);
		emit.end = Math.min(emit.end, textLower.length());
	}

	public List<ScoredHit> predict(String text)
	{
		double textLength = text.length();

		List<Hit<Node>> hits = parseText(text);
		hits = removeOverlappingHits(hits);
		hits = removeUnreasonableHits(text, hits);

		// determine the parent URIs for subsequent pruning
		Set<String> parentURIs = new HashSet<>();
		for (Hit<Node> hit : hits)
		{
			Node node = hit.value;
			while (node.parent != null)
			{
				node = node.parent;
				parentURIs.add(node.uri);
			}
		}

		// score the hits and aggregate the scores for the same nodes (uri)
		Map<String, ScoredHit> scoredHits = new HashMap<>();
		for (Hit<Node> hit : hits)
		{
			if (parentURIs.contains(hit.value.uri)) continue;
			ScoredHit scored = scoredHits.computeIfAbsent(hit.value.uri, k -> new ScoredHit(hit.value));
			scored.addHit(hit, 1 - hit.begin / textLength);
		}

		Comparator<ScoredHit> comparator = Comparator.comparing(h -> -h.hit.depth);
		comparator = comparator.thenComparing(h -> -h.score);
		return scoredHits.values().stream().sorted(comparator).collect(Collectors.toList());
	}

	protected static List<Hit<Node>> removeOverlappingHits(List<Hit<Node>> hits)
	{
		if (hits.isEmpty()) return hits;

		Comparator<Hit<Node>> comparator = Comparator.comparing(hit -> hit.begin);
		comparator = comparator.thenComparing(hit -> -hit.end);
		hits.sort(comparator);

		List<Hit<Node>> filteredHits = new ArrayList<>();
		Hit<Node> last = hits.get(0);
		filteredHits.add(last);
		for (Hit<Node> hit : hits)
		{
			if (hit.end > last.end)
			{
				last = hit;
				filteredHits.add(last);
			}
		}
		return filteredHits;
	}


	protected static List<Hit<Node>> removeUnreasonableHits(String text, List<Hit<Node>> hits)
	{
		if (hits.isEmpty()) return hits;

		List<Hit<Node>> filteredHits = new ArrayList<>();
		for (Hit<Node> hit : hits)
		{
			if (hit.end + 1 < text.length())
			{
				Character lastCh = text.charAt(hit.end);
				Character nextCh = text.charAt(hit.end + 1);
				if (ignoreChars2.indexOf(lastCh) < 0 && ignoreChars2.indexOf(nextCh) < 0) continue;
			}
			filteredHits.add(hit);
		}
		return filteredHits;
	}
}

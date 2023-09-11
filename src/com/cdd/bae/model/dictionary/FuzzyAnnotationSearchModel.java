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

import com.cdd.bao.template.SchemaTree.Node;

import java.io.*;
import java.util.*;
import java.util.stream.*;

public class FuzzyAnnotationSearchModel extends AnnotationSearchModel
{
	double cutoff;
	
	protected static class ScoredHitComparator implements Comparator<ScoredHit>
	{
	    public int compare(ScoredHit a, ScoredHit b)
	    {
	    	if (a.score > b.score) return -1;
	    	else if (a.score < b.score) return 1;
	    	else return 0;
	    }
	}

	public FuzzyAnnotationSearchModel() throws IOException
	{
		this(null, 1.0);
	}

	public FuzzyAnnotationSearchModel(double cutoff) throws IOException
	{
		this(null, cutoff);
	}

	public FuzzyAnnotationSearchModel(DictionaryModelsNoIgnore dictionary, double cutoff) throws IOException
	{
		super(dictionary);
		this.cutoff = cutoff;
	}

	@Override
	public List<Hit> search(String query, String uri)
	{
		Map<String, Node> model = getModel(uri);
		if (model.isEmpty()) return new ArrayList<>();

		final String[] queries = query.trim().toLowerCase().split("\\s+");
		final double normScore = 1.0 / query.length();
		Stream<Hit> q = 
				model.entrySet().stream()
				.map(e -> 
				{
					int score = fuzzyScore(e.getKey(), queries);
					ScoredHit hit = null;
					if (score > 0) 
					{
						hit = new ScoredHit(e.getValue());
						hit.score = score * normScore;
					}
					return hit;
				})
				.filter(hit -> hit != null && hit.score > this.cutoff)
				.distinct()
				.sorted(new ScoredHitComparator())
				.map(e -> new Hit(e.hit, uri));
		return q.collect(Collectors.toList());
	}

	// algorithm derived from org.apache.commons.text.similarity/FuzzyScore.java
	protected static int fuzzyScore(String term, final String[] queries)
	{
		int score = 0;
		for (String query : queries) score += fuzzyScore(term, query);
		return score;
	}
	
	protected static int fuzzyScore(String term, final String query)
	{
		String termLowerCase = term.toLowerCase();
		String queryLowerCase = query.toLowerCase();

		int score = 0;
		int termIndex = 0;
		int previousMatchingCharacterIndex = Integer.MIN_VALUE;
		for (int queryIndex = 0; queryIndex < queryLowerCase.length(); queryIndex++)
		{
			char queryChar = queryLowerCase.charAt(queryIndex);

			boolean termCharacterMatchFound = false;
			for (; termIndex < termLowerCase.length() && !termCharacterMatchFound; termIndex++)
			{
				char termChar = termLowerCase.charAt(termIndex);
				if (queryChar == termChar)
				{
					score++;
					if (previousMatchingCharacterIndex + 1 == termIndex) score += 2;
					previousMatchingCharacterIndex = termIndex;
					termCharacterMatchFound = true;
				}
			}
		}
		return score;
	}
}

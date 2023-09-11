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

import com.cdd.bae.data.*;
import com.cdd.bao.template.*;
import com.cdd.bao.template.SchemaTree.Node;

import java.io.*;
import java.util.*;
import java.util.Map.*;
import java.util.regex.*;
import java.util.stream.*;

public class AnnotationSearchModel
{
	public class Hit
	{
		public String uri;
		public SchemaTree.Node node;

		public Hit(SchemaTree.Node node, String uri)
		{
			this.node = node;
			this.uri = uri;
		}
		
		public String toString()
		{
			return String.format("%s (%s)", node.label, ModelSchema.collapsePrefix(node.uri));
		}
		
		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;
			Hit other = (Hit)o;
			return node.uri.equals(other.node.uri);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(node.uri);
		}
	}
	
	protected static class HitComparator implements Comparator<Map.Entry<String, Node>>
	{
		private Pattern matchBegin;
		
		public HitComparator(List<Pattern> queryPatterns)
		{
			StringJoiner patterns = new StringJoiner("|", "^(", ")");
			for (Pattern p : queryPatterns) patterns.add(p.pattern());
			matchBegin = Pattern.compile(patterns.toString());
		}
		
	    public int compare(Map.Entry<String, Node> a, Map.Entry<String, Node> b)
	    {
	    	boolean aMatchBegin = matchBegin.matcher(a.getKey()).find(); 
	    	boolean bMatchBegin = matchBegin.matcher(b.getKey()).find();
	    	
	    	// hits where the query matches at the beginning are shown first
	    	if (aMatchBegin && !bMatchBegin) return -1;
	    	else if (bMatchBegin && !aMatchBegin) return 1;

	    	// in all other cases, sort by the label
	    	return a.getValue().label.trim().compareToIgnoreCase(b.getValue().label.trim());
	    }
	}
	 	
	protected class DictionaryModelsNoIgnore extends DictionaryModels
	{
		public DictionaryModelsNoIgnore() throws IOException
		{
			loadSynonymsAndIgnoreList();
			ignoreList = new HashSet<>();
			ignoreList.addAll(Arrays.asList("absence", "not determined", "not applicable", "unknown", 
					"ambiguous", "missing", "dubious", "requires term", "needs checking", "see Gene ID", "other"));
		}
	}


	protected Map<String, Map<String, SchemaTree.Node>> models;
	protected DictionaryModelsNoIgnore dictionary;

	public AnnotationSearchModel() throws IOException
	{
		this(null);
	}

	public AnnotationSearchModel(DictionaryModelsNoIgnore dictionary) throws IOException
	{
		this.dictionary = dictionary == null ? new DictionaryModelsNoIgnore() : dictionary;
		this.models = new HashMap<>();
	}

	public List<Hit> search(String query, String uri)
	{
		Map<String, Node> model = getModel(uri);
		if (model.isEmpty()) return new ArrayList<>();

		Stream<Entry<String, Node>> q = model.entrySet().stream();
		List<Pattern> patterns = new ArrayList<>();
		if (!query.trim().isEmpty())
			for (String queryWord : query.trim().split(" "))
			{
				Pattern p = getMatcher(queryWord);
				patterns.add(p);
				q = q.filter(e -> p.matcher(e.getKey()).find());
			}
		q = q.sorted(new HitComparator(patterns));
		return q.map(e -> new Hit(e.getValue(), uri))
				.distinct()
				.collect(Collectors.toList());
	}

	private Pattern getMatcher(String queryWord)
	{
		// use the same approach for pre-processing annotation labels to expand the query string
		if (queryWord.length() == 1) return Pattern.compile(queryWord);
		Set<String> querySet = new HashSet<>();
		querySet.add(queryWord);
		querySet = dictionary.expandLabels(querySet, false).stream().map(s -> s.replaceAll("[ '-0]", "")).collect(Collectors.toSet());
		return Pattern.compile(String.join("|", querySet));
	}

	protected Map<String, SchemaTree.Node> getModel(String uri)
	{
		if (models.containsKey(uri)) return models.get(uri);

		Map<String, SchemaTree.Node> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		models.put(uri, map);
		for (Schema schema : Common.getAllSchemata())
		{
			Schema.Assignment[] assignments = schema.findAssignmentByProperty(uri);
			if (assignments.length == 0) continue;

			for (Entry<String, SchemaTree.Node> e : dictionary.getDictionaryMap(schema, assignments).entrySet())
				map.put(e.getKey().replaceAll("[ '-0]", ""), e.getValue());
		}
		return models.get(uri);
	}
}

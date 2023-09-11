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
import com.cdd.bae.tasks.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.template.SchemaTree.Node;

import java.io.*;
import java.util.*;
import java.util.Map.*;
import java.util.stream.*;

import org.json.*;

/*
	Manage a set of dictionary models (extension of word lists, preprocessing of text, and prediction of text)
 */

public class DictionaryModels
{
	protected Map<String, DictionaryModel> models;
	protected Set<String> ignoreList;
	private Map<String, Map<String, List<String>>> synonyms;
	private static final String SYNONYMS_FILE = "/com/cdd/bae/model/dictionary/synonyms.json";
	private static final String IGNORE_FILE = "/com/cdd/bae/model/dictionary/ignoreList.txt";

	public List<String> goodList = Arrays.asList(
			PubChemAssays.MODE_OF_ACTION_URI,
			PubChemAssays.CELLLINE_URI,
			PubChemAssays.ASSAYTYPE_URI,
			PubChemAssays.ASSAYFOOTPRINT_URI,
			PubChemAssays.GENEID_URI,
			PubChemAssays.PROTEINID_URI,
			PubChemAssays.ORGANISM_URI);

	public List<String> mediumList = Arrays.asList(
			PubChemAssays.DETECTION_METHOD_URI,
			ModelSchema.PFX_BAO + "BAO_0002663",
			ModelSchema.PFX_BAO + "BAO_0002848",
			PubChemAssays.DETECTION_INSTRUMENT_URI);

	public List<String> badList = Arrays.asList(
			ModelSchema.PFX_BAO + "BAO_0000208",
			ModelSchema.PFX_BAO + "BAO_0000210",
			ModelSchema.PFX_BAO + "BAO_0000211");
	
	private static final Set<String> CELL_WHITELIST = new HashSet<>(Arrays.asList("mef", "cho"));

	// ------------ public methods ------------

	public DictionaryModels() throws IOException
	{
		loadSynonymsAndIgnoreList();
	}

	public List<Map<String, List<ScoredHit>>> predict(String[] texts)
	{
		if (models == null) buildModels();

		List<Map<String, List<ScoredHit>>> results = new ArrayList<>(texts.length);
		for (String text : texts) results.add(predict(text));
		return results;
	}

	public Map<String, List<ScoredHit>> predict(String text)
	{
		if (models == null) buildModels();

		Map<String, List<ScoredHit>> result = new HashMap<>();
		for (Entry<String, DictionaryModel> model : models.entrySet())
			result.put(model.getKey(), model.getValue().predict(text));
		return result;
	}

	public void buildModels()
	{
		buildModels(goodList);
	}

	public void buildModels(List<String> modelURIlist)
	{
		models = new HashMap<>();
		for (Schema schema : Common.getAllSchemata())
			for (String uri : schema.gatherAllURI())
			{
				if (modelURIlist != null && (!modelURIlist.contains(uri))) continue;
				Schema.Assignment[] assignments = schema.findAssignmentByProperty(uri);
				if (assignments.length == 0) continue;

				Map<String, Node> map = getDictionaryMap(schema, assignments);
				if (!map.isEmpty()) models.put(uri, new DictionaryModel(uri, map));
			}
	}

	public Map<String, Node> getDictionaryMap(Schema schema, Schema.Assignment[] assignments)
	{
		Map<String, SchemaTree.Node> map = new TreeMap<>();
		for (Schema.Assignment assn : assignments)
		{
			Set<String> containers = AssayUtil.enumerateContainerTerms(assn);
			SchemaTree tree = Common.obtainTree(schema, assn);
			if (tree != null) for (SchemaTree.Node node : tree.getFlat())
			{
				if (containers.contains(node.uri)) continue;

				Set<String> labels = getNodeLabels(assn, node);
				for (String word : expandLabels(labels))
				{
					word = " " + word;
					if (word.length() <= 5) word = word + " ";
					map.put(word, node);
				}
			}
		}
		return map;
	}

	// ------------ private methods ------------

	private Set<String> getNodeLabels(Schema.Assignment assn, Node node)
	{
		Set<String> labels = new HashSet<>();
		labels.add(node.label);
		if (node.altLabels != null)
			for (String label : node.altLabels)
				if (label.length() > 3) labels.add(label);
		if (synonyms.containsKey(assn.propURI) && synonyms.get(assn.propURI).containsKey(node.label))
			labels.addAll(synonyms.get(assn.propURI).get(node.label));
		labels.remove("");
		return labels;
	}

	protected Set<String> expandLabels(Set<String> labels)
	{
		return expandLabels(labels, true);
	}

	protected Set<String> expandLabels(Set<String> labels, boolean filterProblems)
	{
		Set<String> result = new HashSet<>();
		for (String s : labels)
		{
			s = s.toLowerCase().trim();
			if (ignoreList.contains(s) || s.length() == 1) continue;
			result.add(s);
			result.addAll(expandWord(s));
		}
		result = standardizeText(result);
		if (filterProblems) result = filterProblemCases(result);
		return result;
	}

	protected static String standardizeText(String text)
	{
		String s = text.toLowerCase() + " ";
		s = s.replaceAll("[.,;:] ", " "); // special characters at end of word
		s = s.replaceAll("[\\[\\]\\(\\)-]", " "); // special characters inside words
		s = s.replaceAll("\n", "\n "); // add space at the begin of a new line
		s = s.replaceAll(" +", " "); // ignore consecutive spaces
		return s.trim();
	}

	private static Set<String> standardizeText(Set<String> texts)
	{
		return texts.stream().map(DictionaryModels::standardizeText).collect(Collectors.toSet());
	}

	private static Set<String> filterProblemCases(Set<String> texts)
	{
		return texts.stream()
				.filter(word -> !word.matches("^\\d+$"))
				.collect(Collectors.toSet());
	}

	protected Set<String> expandWord(String word)
	{
		Set<String> words = new HashSet<>();
		words.add(word);
		words.add(word.replace("ior", "iour"));
		words.add(word.replaceAll("([a-zA-Z])(\\d)", "$1 $2"));
		expandCellWords(word, words);
		expandResponseTo(word, words);
		return words;
	}

	private void expandCellWords(String word, Set<String> words)
	{
		final String CELL = " cell";
		final String CELL_LINE = " cell line";
		final String CELL_LINE_CELL = " cell line cell";
		String w = word.trim();
		if (w.endsWith(CELL_LINE_CELL))
		{
			w = w.replaceAll(CELL_LINE_CELL + "$", "").trim();
			words.add(w + CELL);
			words.add(w + CELL_LINE);
			if (w.contains("-"))
			{
				w = w.replaceAll("-", "");
				words.add(w + CELL);
				words.add(w + CELL_LINE);
			}
		}
		else if (w.endsWith(CELL) || w.endsWith(CELL_LINE))
		{
			w = w.replaceAll(CELL_LINE + "$", "").replaceAll(CELL + "$", "").trim();
			if (w.length() > 3 && !ignoreList.contains(w)) words.add(w);
			if (CELL_WHITELIST.contains(w)) words.add(w);
			words.add(w + CELL);
			words.add(w + CELL_LINE);
			if (w.contains("-"))
			{
				w = w.replaceAll("-", "");
				if (w.length() > 3 && !ignoreList.contains(w)) words.add(w);
				words.add(w + CELL);
				words.add(w + CELL_LINE);
			}
		}
	}

	private static void expandResponseTo(String word, Set<String> words)
	{
		final String RESPONSE_TO = "response to ";
		if (word.startsWith(RESPONSE_TO))
		{
			words.add(word.replaceAll(RESPONSE_TO, "").trim() + " respons");
		}
	}

	protected void loadSynonymsAndIgnoreList() throws IOException
	{
		ResourceFile synonymFile = new ResourceFile(SYNONYMS_FILE);
		synonyms = new HashMap<>();
		JSONObject json = new JSONObject(synonymFile.getContent());
		for (String key : iteratorToIterable(json.keys()))
		{
			Map<String, List<String>> keySynonyms = new HashMap<>();
			JSONObject jsonSynonyms = json.getJSONObject(key);
			for (String word : iteratorToIterable(jsonSynonyms.keys()))
				keySynonyms.put(word, toStringList(jsonSynonyms.getJSONArray(word)));
			synonyms.put(key, keySynonyms);
		}
		ignoreList = new HashSet<>();
		ResourceFile ignoreListFile = new ResourceFile(IGNORE_FILE);
		for (String line : ignoreListFile.getContent().split("\n"))
		{
			line = line.trim();
			if (line.startsWith("#")) continue;
			line = line.split("#")[0].trim();
			line = line.split(" : ")[0].trim();
			line = line.toLowerCase();
			if (line.length() != 0) ignoreList.add(line);
		}
	}

	private <T> Iterable<T> iteratorToIterable(Iterator<T> iterator)
	{
		return () -> iterator;
	}

	private static List<String> toStringList(JSONArray array)
	{
		List<String> result = new ArrayList<>();
		if (array == null) return result;
		for (int i = 0; i < array.length(); i++)
			result.add(array.getString(i));
		return result;
	}
}

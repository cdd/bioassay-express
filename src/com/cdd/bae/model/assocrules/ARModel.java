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

package com.cdd.bae.model.assocrules;

import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.model.*;
import com.cdd.bae.model.assocrules.ARLearner.*;
import com.cdd.bae.model.dictionary.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.template.SchemaTree.Node;
import com.cdd.bao.template.SchemaVocab.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.stream.*;
import java.util.zip.*;

/*
	Predict annotations based on existing annotations
*/

public class ARModel
{
	private static final String SEP = ",";
	private static final String RULES_DIR = new File(Common.getConfigFN()).getParent() + "/models/";

	protected static class RulePrediction
	{
		public static final Comparator<RulePrediction> byConfidence = Comparator.comparing(h -> -h.confidence);

		public String label;
		public float confidence;
		public float lift;
		public int support;

		public RulePrediction(String label, float confidence, float lift, int support)
		{
			this.label = label;
			this.confidence = confidence;
			this.lift = lift;
			this.support = support;
		}

		@Override
		public String toString()
		{
			return String.format("%s (%.2f)", label, confidence);
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;
			RulePrediction other = (RulePrediction)o;
			return this.label.equals(other.label);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(label);
		}
	}

	public static class ARTree
	{
		protected PrefixTree<String, RulePrediction> model;

		public ARTree()
		{
			model = new PrefixTree<>();
		}

		public List<RulePrediction> predict(Set<String> items)
		{
			return new ArrayList<>(model.findAll(items));
		}

		public List<RulePrediction> lookup(String[] items)
		{
			return new ArrayList<>(model.lookup(items));
		}

		public void addRules(List<Rule> rules)
		{
			for (Rule rule : rules)
			{
				String[] lhs = rule.lhs.items;
				RulePrediction rhs = new RulePrediction(rule.rhs.getKey(), (float)rule.confidence, (float)rule.lift, rule.support);
				addRule(lhs, rhs);
			}
		}

		public void addRule(String[] lhs, RulePrediction rhs)
		{
			model.add(lhs, rhs);
		}

		@Override
		public String toString()
		{
			return model.toString();
		}

	}

	protected ARTree model;
	protected Map<String, SchemaTree.Node> mapURItoNode;
	protected Map<String, Set<String>> mapURItoPropURI;
	private int minSupport = 0;
	private int maxSize = 0;
	private double confidence = 0;

	private ARModel()
	{ /* use one of the static factory methods */ }

	public static ARModel loadDefaultModel() throws IOException
	{
		return fromFile(RULES_DIR + "associationRules.txt.gz");
	}

	public static ARModel fromFile(String filename) throws IOException
	{
		ARModel assocRulesModel = new ARModel();
		assocRulesModel.loadModel(filename);
		return assocRulesModel;
	}

	public static ARModel fromRules(List<Rule> rules)
	{
		ARModel assocRulesModel = new ARModel();
		assocRulesModel.loadRules(rules);
		return assocRulesModel;
	}

	public Map<String, List<ScoredHit>> predict(Annotation[] annotations)
	{
		Map<String, List<ScoredHit>> results = new HashMap<>();
		if (annotations.length == 0) return results;
		
		String[] valueURIs = Arrays.stream(annotations).map(a -> a.valueURI).toArray(String[]::new);
		return predict(valueURIs);
	}

	public Map<String, List<ScoredHit>> predict(String[] valueURIs)
	{
		Map<String, List<ScoredHit>> results = new HashMap<>();
		if (valueURIs.length == 0) return results;

		Set<String> items = Arrays.stream(valueURIs).map(ModelSchema::collapsePrefix).collect(Collectors.toSet());
		List<RulePrediction> rulePredictions = model.predict(items);

		rulePredictions = rulePredictions.stream().sorted(RulePrediction.byConfidence).distinct().collect(Collectors.toList());
		for (RulePrediction result : rulePredictions)
		{
			ScoredHit hit = new ScoredHit(mapURItoNode.get(result.label));
			hit.score = result.confidence;
			if (mapURItoPropURI.containsKey(result.label))
				for (String propURI : mapURItoPropURI.get(result.label))
					results.computeIfAbsent(propURI, k -> new ArrayList<>()).add(hit);
		}
		return results;
	}

	public static ARModel learn(int minSupport, int maxSize, double confidence, boolean verbose)
	{
		return learn(new ARLearner(minSupport, maxSize, confidence), verbose);
	}

	public static ARModel learn(int minSupport, int maxSize, boolean verbose)
	{
		return learn(new ARLearner(minSupport, maxSize), verbose);
	}

	public static ARModel learn(ARLearner learner, boolean verbose)
	{
		LogTimer timer = new LogTimer();
		if (verbose) Util.writeln("Get annotations");
		List<Set<String>> transactions = ARModel.getAnnotations();
		if (verbose) Util.writeln(String.format("Start learning based on %d curated assays (%.1f s)", transactions.size(), timer.getTime()));
		List<ARLearner.ItemSet> itemSets = learner.generateItemSets(transactions);
		if (verbose) Util.writeln(String.format("Found %d itemsets (%.1f s)", itemSets.size(), timer.getTime()));
		List<Rule> rules = learner.generateRules(itemSets);
		if (verbose) Util.writeln(String.format("Found %d rules (%.1f s)", rules.size(), timer.getTime()));
		if (verbose) Util.writeln(rules.get(0));
		if (verbose) Util.writeln(rules.get(rules.size() - 1));
		ARModel model = ARModel.fromRules(rules);
		model.minSupport = learner.minSupport;
		model.maxSize = learner.maxSize;
		model.confidence = learner.confidence;
		
		if (verbose) Util.writeln(String.format("Converted to ARModel (%.1f s)", timer.getTime()));
		return model;
	}

	public void loadRules(List<Rule> rules)
	{
		model = new ARTree();
		model.addRules(rules);

		Set<String> valueURIs = model.model.getValues().stream().map(r -> r.label).collect(Collectors.toSet());
		valueURIs.addAll(model.model.getKeys());
		initializeMaps(valueURIs);
	}

	public void saveModel(String filename, boolean addLabels) throws IOException
	{
		try(Writer writer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(filename), 8192), StandardCharsets.UTF_8))
		{
			saveModel(writer, addLabels);
		}
	}
	
	public void saveModel(Writer writer, boolean addLabels) throws IOException
	{
		Map<String, List<String>> assnAnnotMap = getAnnotationToAssignmentMap();
		List<String> unmapped = Arrays.asList("unknown");
		
		writer.write("version : 1\n");
		writer.write(String.format("minSupport : %d%n", minSupport));
		writer.write(String.format("maxSize : %d%n", maxSize));
		if (addLabels)
			writer.write("premise\tconclusion\tsupport\tconfidence\tlift\tpremise2\tconclusion2\tpremiseAssignment\tconclusionAssignment\n");
		else
			writer.write("premise\tconclusion\tsupport\tconfidence\tlift\n");
		model.model.visit((path, prediction) -> 
		{
			try
			{
				String row = String.format("%s\t%s\t%s\t%.2f\t%.2f", String.join(SEP, path), 
						prediction.label, prediction.support, prediction.confidence, prediction.lift);
				if (addLabels)
				{
					String pathLabels = path.stream()
							.map(s -> toLabel(s))
							.collect(Collectors.joining(" :: "));
					String pathAssignment = path.stream()
							.map(s -> assnAnnotMap.getOrDefault(s, unmapped))
							.flatMap(List::stream)
							.collect(Collectors.joining(" :: "));
					String predAssignment = String.join(" :: ", assnAnnotMap.getOrDefault(prediction.label, unmapped));
					row = String.format("%s\t\"%s\"\t\"%s\"\t\"%s\"\t\"%s\"", row, 
							pathLabels, toLabel(prediction.label),
							pathAssignment, predAssignment);
				}
				writer.write(String.format("%s%n", row));
			}
			catch (IOException e) { throw new UncheckedIOException(e); }
		});
	}
	
	private Map<String, List<String>> getAnnotationToAssignmentMap()
	{
		Map<String, List<String>> result = new HashMap<>();
		
		/*SchemaVocab schvoc = Common.getSchemaVocab();
		
		for (StoredTree storedTree : schvoc.getTrees()) 
		{
			SchemaTree tree = storedTree.tree;
			String assnName = storedTree.assignment.name;
			for (Node node : tree.getFlat())
			{
				String nodeURI = ModelSchema.collapsePrefix(node.uri);
				if (!result.containsKey(nodeURI))
					result.put(nodeURI, new ArrayList<>());
				result.get(nodeURI).add(assnName);
			}
		}*/
		
		for (var schema : Common.getAllSchemata())
		{
			Queue<Schema.Group> queue = new ArrayDeque<>();
			queue.add(schema.getRoot());
			while (!queue.isEmpty())
			{
				var group = queue.remove();
				for (var assn : group.assignments)
				{
					var tree = Common.obtainTree(schema, assn);
					for (Node node : tree.getFlat())
					{
						String nodeURI = ModelSchema.collapsePrefix(node.uri);
						if (!result.containsKey(nodeURI)) result.put(nodeURI, new ArrayList<>());
						result.get(nodeURI).add(assn.name);
					}
				}
				
				queue.addAll(group.subGroups);
			}
		}
		
		return result;
	}
	
	private String toLabel(String uri)
	{
		var branch = Common.getOntoValues().getBranch(ModelSchema.expandPrefix(uri));
		return branch == null ? uri : branch.label;
	}
	
	public void loadModel(String filename) throws IOException
	{
		model = new ARTree();
		Set<String> valueURIs = new HashSet<>();
		try(Scanner f = new Scanner(new GZIPInputStream(new FileInputStream(filename), 8192), "UTF-8"))
		{
			f.nextLine();
			minSupport = Integer.parseInt(f.nextLine().split(":")[1].trim());
			maxSize = Integer.parseInt(f.nextLine().split(":")[1].trim());
			f.nextLine();
			while (f.hasNextLine())
			{
				String[] line = f.nextLine().split("\t");
				String[] lhs = line[0].split(SEP);
				valueURIs.addAll(Arrays.asList(lhs));
				valueURIs.add(line[1]);
				model.addRule(lhs, new RulePrediction(line[1], Float.parseFloat(line[3]), Float.parseFloat(line[4]), Integer.parseInt(line[2])));
			}
		}
		initializeMaps(valueURIs);
	}
	
	private void initializeMaps(Set<String> valueURIs)
	{
		mapURItoNode = new HashMap<>();
		mapURItoPropURI = new HashMap<>();
		for (Schema schema : Common.getAllSchemata())
			for (String uri : schema.gatherAllURI())
			{
				Schema.Assignment[] assignments = schema.findAssignmentByProperty(uri);
				if (assignments.length == 0) continue;
				for (Schema.Assignment assn : assignments)
				{
					SchemaTree tree = Common.obtainTree(schema, assn);
					if (tree != null) for (SchemaTree.Node node : tree.getFlat())
					{
						String valueURI = ModelSchema.collapsePrefix(node.uri);
						if (valueURIs.contains(valueURI))
						{
							mapURItoNode.put(valueURI, node);
							mapURItoPropURI.computeIfAbsent(valueURI, k -> new HashSet<>()).add(uri);
						}
					}
				}
			}
	}

	public static void exportAnnotations(String filename) throws IOException
	{
		try (Writer wtr = new FileWriter(filename))
		{
			for (Set<String> annotations : getAnnotations())
				wtr.write(String.join(",", annotations));
		}
	}

	public static List<Set<String>> getAnnotations()
	{
		DataAssay assayStore = Common.getDataStore().assay();
		long[] assayIDList = assayStore.fetchAssayIDCurated();
		List<Set<String>> result = new ArrayList<>();
		for (int n = 0; n < assayIDList.length; n++)
		{
			DataObject.Assay assay = assayStore.getAssay(assayIDList[n]);
			PredictAnnotations.Predictions predictions = PredictAnnotations.getAnnotations(assay);
			Set<String> annotValueURIs = new HashSet<>();
			for (Set<String> annot : predictions.annotations.values())
				annotValueURIs.addAll(annot);
			annotValueURIs = annotValueURIs.stream().filter(u -> !u.startsWith(ModelSchema.PFX_BAT)).collect(Collectors.toSet());
			if (annotValueURIs.isEmpty()) continue;

			result.add(annotValueURIs.stream()
					// .filter(a -> !a.contains("sources#SRC_"))
					.filter(a -> !a.contains("GO_"))
					.map(ModelSchema::collapsePrefix)
					.collect(Collectors.toCollection(TreeSet::new)));
		}
		return result;
	}

//	protected static String toShortURL(String url)
//	{
//		return url.replace(ModelSchema.PFX_BAO, "bao/")
//				.replace(ModelSchema.PFX_BAE, "bae/")
//				.replace(ModelSchema.PFX_DTO, "dto/")
//				.replace(ModelSchema.PFX_OBO, "obo/")
//				.replace(ModelSchema.PFX_PROTEIN, "protein/")
//				.replace(ModelSchema.PFX_GENEID, "gene/");
//	}
//
//	protected static String toLongURL(String url)
//	{
//		return url.replace("bao/", ModelSchema.PFX_BAO)
//				.replace("bae/", ModelSchema.PFX_BAE)
//				.replace("dto/", ModelSchema.PFX_DTO)
//				.replace("obo/", ModelSchema.PFX_OBO)
//				.replace("protein/", ModelSchema.PFX_PROTEIN)
//				.replace("gene/", ModelSchema.PFX_GENEID);
//	}

	public String toString()
	{
		return model.toString();
	}
}

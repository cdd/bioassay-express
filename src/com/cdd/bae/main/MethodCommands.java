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

package com.cdd.bae.main;

import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.model.*;
import com.cdd.bae.model.PredictAnnotations.*;
import com.cdd.bae.model.assocrules.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.template.SchemaVocab.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;
import java.util.Map.*;
import java.util.stream.*;
import java.util.zip.*;

import org.apache.commons.collections4.*;
import org.apache.commons.lang3.*;
import org.json.*;

/*
	Command line functionality for evaluating the efficacy of prediciton methods.
*/

public class MethodCommands implements Main.ExecuteBase
{
	private final class Tally
	{
		String uri;
		String name;
		Schema.Assignment assn;
		int countFP = 0, countTP = 0;
		String[] fpAssays = null; // uniqueIDs for assays with a false positive
	}

	// ------------ public methods ------------
	private static final String MATCHES_FIRST = "first";
	private static final String CONTAINS_NONE = "none";
	private static final String CONTAINS_ANY = "any";
	private static final String CONTAINS_ALL = "all";
	private static final String NO_PREDICTIONS = "noPredictions";
	private static final String SPECIAL = "special";
	private static final String[] PERF_METRICS = new String[]{SPECIAL, NO_PREDICTIONS, CONTAINS_ALL, CONTAINS_ANY, CONTAINS_NONE, MATCHES_FIRST};

	private static final String NASSAYS = "nassays";
	private static final String SPECIAL_ANNOTATIONS = "http://www.bioassayontology.org/bat";

	@Override
	public boolean needsNLP()
	{
		return true;
	}

	public void execute(String[] args) throws IOException
	{
		if (args.length == 0)
		{
			printHelp();
			return;
		}

		String cmd = args[0];
		String[] options = ArrayUtils.remove(args, 0);

		if (cmd.equals("associationTrain")) trainAssociationModel(options);
		else if (cmd.equals("dictionaryPerformance")) analyzeModelPerformance(Arrays.asList(Method.DICTIONARY));
		else if (cmd.equals("compareWordlist")) compareWordlist(options);
		else if (cmd.equals("associationPerformance")) analyzeModelPerformance(Arrays.asList(Method.ASSOCIATION));
		else if (cmd.equals("modelPerformance")) analyzeModelPerformance();
		else if (cmd.equals("dictionaryMismatch")) analyzeDictionaryMismatch(options);
		else if (cmd.equals("assignment")) analyzeAssignment(options);
		else if (cmd.equals("assay")) predictAssay(options);
		else if (cmd.equals("transferOrganism")) transferOrganism();
		else if (cmd.equals("truthtable")) generateTruthTable(options);
		else Util.writeln("Unknown command: '" + cmd + "'.");
	}

	public void printHelp()
	{
		Util.writeln("Method commands");
		Util.writeln("    validation of methods");
		Util.writeln("Options:");
		Util.writeln("    associationTrain: create new association rules model based on current annotations and save to file");
		Util.writeln("    dictionaryPerformance: get overview of performance of dictionary method");
		Util.writeln("    dictionaryMismatch: analyze the dictionary mismatches");
		Util.writeln("    assignment: analyze the performance of the various models for assignment");
		Util.writeln("    assay: print predictions for assay (requires assayID or uniqueID)");
		Util.writeln("    transferOrganism: evaluate success rate of transferring organism information from protein target annotation");
		Util.writeln("    truthtable: generate a CSV file with assay success/fail stats");
	}

	// ------------ private methods ------------

	private void compareWordlist(String[] options) throws IOException
	{
		String fn = TransferCommands.getFilename(options, ".gz");
		if (fn == null) return;
		
		Optional<DictionaryPredict> dictPredict = getDictionaryPredict();
		if (!dictPredict.isPresent()) return;
		DictionaryPredict dictModel = dictPredict.get();

		Map<String, Map<String, Set<String>>> results = new HashMap<>();
		
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(fn)))))
		{
			String line = null;
			while ((line = reader.readLine()) != null)
			{
				for (Entry<String, List<String>> entry : dictModel.getPrediction(line).entrySet())
				{
					String assn = entry.getKey();
					if (!results.containsKey(assn)) results.put(assn, new TreeMap<>());
					Map<String, Set<String>> result = results.get(assn);
					for (String annot : entry.getValue())
					{
						annot = Common.getOntoValues().getLabel(annot);
						if (!result.containsKey(annot)) result.put(annot, new TreeSet<>());
						result.get(annot).add(line);
					}
				}
			}
		}
		for (Entry<String, Map<String, Set<String>>> assn : results.entrySet())
		{
			Util.writeln(assn.getKey() + " " + Common.getOntoValues().getLabel(assn.getKey()));
			for (Entry<String, Set<String>> hit : assn.getValue().entrySet())
			{
				String words = hit.getValue().toString().replace("[", "").replace("]", "");
				String indent = "  " + hit.getKey() + " : ";
				while (words.length() > 0)
				{
					int n = words.indexOf(" ", 60);
					if (n == -1) n = words.length();
					Util.writeln(indent + words.substring(0, n));
					words = words.substring(n).trim();
					indent = StringUtils.repeat(" ", indent.length());
				}
			}
		}
	}
	
	private void trainAssociationModel(String[] options)
	{
		boolean addLabels = false;
		if (options[0].equals("-labels"))
		{
			addLabels = true;
			options = Arrays.copyOfRange(options, 1, options.length);
		}
		
			
		String fn = TransferCommands.getFilename(options, ".gz");
		if (fn == null) return;
		
		ARModel model = ARModel.learn(5, 3, true);
		try
		{
			Util.writeln("Saving model to file " + fn);
			model.saveModel(fn, addLabels);
		}
		catch (IOException e)
		{
			Util.errmsg("Saving model failed", e);
		}
		Util.writeln("Done");
	}
	
	private void analyzeModelPerformance()
	{
		analyzeModelPerformance(Method.methods());
	}

	private void analyzeModelPerformance(List<Method> methods)
	{
		DataAssay dataAssay = Common.getDataStore().assay();
		Counters<String> counters = new Counters<>();

		Map<Method, Map<String, Counters<String>>> performanceMeasures = new TreeMap<>();
		for (Method method : methods) performanceMeasures.put(method, new TreeMap<>());

		for (long assayID : dataAssay.fetchAllCuratedAssayID())
		{
			counters.increment(NASSAYS);
			Util.writeln(counters.get(NASSAYS) + " " + assayID);
			Assay assay = dataAssay.getAssay(assayID);
			PredictAnnotations.Predictions predictions = getPredictions(assay, methods);
			if (predictions == null) return;

			for (Schema.Assignment assignment : predictions.assignments)
			{
				String propURI = assignment.propURI;
				for (Method method : methods)
					analyzePredictions(predictions.annotations.get(propURI), predictions.get(method).get(propURI), 
							performanceMeasures.get(method).computeIfAbsent(propURI, k -> new Counters<String>()));
			}
			if (counters.get(NASSAYS) % 25 == 0) printPredictionAnalysis(counters.get(NASSAYS), performanceMeasures);
		}
		counters.report();

		printPredictionAnalysis(counters.get(NASSAYS), performanceMeasures);
	}

	private void analyzePredictions(Set<String> annotation, List<String> prediction, Counters<String> perf)
	{
		if (annotation.stream().filter(u -> !u.startsWith(SPECIAL_ANNOTATIONS)).collect(Collectors.toSet()).isEmpty())
		{
			perf.increment(SPECIAL);
			return;
		}

		if (prediction == null) 
		{
			perf.increment(NO_PREDICTIONS);
			return;
		}
		prediction = prediction.subList(0, Math.min(3, prediction.size()));

		Collection<String> overlap = CollectionUtils.intersection(annotation, prediction);
		if (prediction.isEmpty())
		{
			perf.increment(NO_PREDICTIONS);
			return;
		}
		perf.increment("predictions");
		if (overlap.isEmpty())
			perf.increment(CONTAINS_NONE);
		else if (overlap.size() == annotation.size())
			perf.increment(CONTAINS_ALL);
		else
			perf.increment(CONTAINS_ANY);
		if (annotation.contains(prediction.get(0))) perf.increment(MATCHES_FIRST);
	}

	private void printPredictionAnalysis(int nAssays, Map<Method, Map<String, Counters<String>>> performanceMeasures)
	{
		List<Method> modelNames = new ArrayList<>(performanceMeasures.keySet());
		
		Util.writeln("\n\n");
		StringJoiner sj = new StringJoiner(",");
		sj.add("URI").add("nAssays");
		for (Method model : modelNames)
			for (String metric: PERF_METRICS) sj.add(model + "." + metric);
		Util.writeln(sj.toString());
		
		Set<String> assnURIs = new TreeSet<>();
		for (Map<String, Counters<String>> perfKeys : performanceMeasures.values())
			assnURIs.addAll(perfKeys.keySet());

		
		for (String key : assnURIs)
		{
			sj = new StringJoiner(",");
			sj.add(key.split("#")[1]);
			sj.add(Integer.toString(nAssays));

			for (Method model : modelNames)
			{
				Counters<String> pc = performanceMeasures.get(model).get(key);
				for (String metric: PERF_METRICS) sj.add(Integer.toString(pc.get(metric)));
			}
			Util.writeln(sj.toString());
		}
	}

	private void analyzeDictionaryMismatch(String[] options)
	{
		DataAssay dataAssay = Common.getDataStore().assay();

		Schema.Assignment assignment = getAssignment(options[0], Common.getSchemaCAT());
		if (assignment == null) return;

		String assignmentURI = assignment.propURI;
		Counters<String> counters = new Counters<>();
		Map<String, List<String>> mismatch = new TreeMap<>();
		for (long assayID : dataAssay.fetchAllCuratedAssayID())
		{
			Assay assay = dataAssay.getAssay(assayID);
			PredictAnnotations.Predictions predictions = PredictAnnotations.getAnnotations(assay);
			Set<String> annotValueURIs = predictions.annotations.get(assignmentURI);
			annotValueURIs = annotValueURIs.stream().filter(u -> !u.startsWith(SPECIAL_ANNOTATIONS)).collect(Collectors.toSet());
			if (annotValueURIs.isEmpty()) continue;

			predictions = getPredictions(assay, Arrays.asList(Method.DICTIONARY));
			if (predictions == null || !predictions.get(Method.DICTIONARY).containsKey(assignmentURI)) return;

			String annotations = annotValueURIs.stream()
					.map(uri -> Common.getOntoValues().getLabel(uri))
					.filter(Objects::nonNull)
					.collect(Collectors.joining(", "));
			if (annotations.isEmpty()) continue;

			List<String> suggestions = predictions.get(Method.DICTIONARY).get(assignmentURI);
			String suggLabels = suggestions.stream().map(uri -> Common.getOntoValues().getLabel(uri)).filter(Objects::nonNull).collect(Collectors.joining(", "));
			suggLabels += " (" + assay.uniqueID + ")";

			for (String annotValueURI : annotValueURIs)
			{
				if (suggestions.contains(annotValueURI) || Common.getOntoValues().getLabel(annotValueURI) == null) continue;
				counters.increment("missed");
				String annotation = Common.getOntoValues().getLabel(annotValueURI);
				mismatch.computeIfAbsent(annotation, k -> new ArrayList<>()).add(suggLabels);
				annotation = annotation.replaceAll("-", "").replaceAll("\\.", "").replaceAll(" line$", "").replaceAll(" cell$", "").replaceAll(" ", "").toLowerCase();
				if (suggLabels.replaceAll("-", "").replaceAll("\\.", "").replaceAll(" ", "").toLowerCase().contains(annotation))
					counters.increment("potential duplicate");
			}
			counters.increment(NASSAYS);
		}
		counters.report();
		for (Map.Entry<String, List<String>> miss : mismatch.entrySet())
		{
			Util.writeln(miss.getKey() + " (" + miss.getValue().size() + ")");
			Util.writeln("  " + String.join("\n  ", miss.getValue()));
		}
	}

	private void analyzeAssignment(String[] options)
	{
		DataAssay dataAssay = Common.getDataStore().assay();

		Schema.Assignment assignment = getAssignment(options[0], Common.getSchemaCAT());
		if (assignment == null) return;
		String assignmentURI = assignment.propURI;

		Counters<String> counters = new Counters<>();
		for (long assayID : dataAssay.fetchAllCuratedAssayID())
		{
			counters.increment(NASSAYS);

			Assay assay = dataAssay.getAssay(assayID);

			PredictAnnotations.Predictions predictions = PredictAnnotations.getAnnotations(assay);
			Set<String> annotValueURIs = predictions.annotations.get(assignmentURI);
			if (annotValueURIs.isEmpty())
			{
				counters.increment("not annotated");
				continue;
			}
			annotValueURIs = annotValueURIs.stream().filter(u -> !u.startsWith(SPECIAL_ANNOTATIONS)).collect(Collectors.toSet());
			if (annotValueURIs.isEmpty())
			{
				counters.increment(SPECIAL);
				continue;
			}

			predictions = getPredictions(assay, assignment);
			if (predictions == null) return;

			String annotations = annotValueURIs.stream()
					.map(uri -> Common.getOntoValues().getLabel(uri))
					.filter(Objects::nonNull)
					.collect(Collectors.joining(", "));
			if (annotations.isEmpty())
			{
				counters.increment("unknown value", annotValueURIs.stream().collect(Collectors.joining(", ")));
				continue;
			}

			for (Method method : Method.methods())
				analyzeSuggestions(method, predictions.get(method).getOrDefault(assignmentURI, null), annotValueURIs, counters);

			Util.writeln(assay.uniqueID + " [" + assignment.name + "] : " + annotations);
			for (Method method : Method.methods())
				printFormattedSuggestions(method, predictions.get(method).getOrDefault(assignmentURI, null), annotValueURIs);
			Util.writeln();
		}

		counters.report();
	}

	private void predictAssay(String[] options)
	{
		Assay assay;
		if (options[0].contains(":"))
			assay = Common.getDataStore().assay().getAssayFromUniqueID(options[0]);
		else
			assay = Common.getDataStore().assay().getAssay(Long.parseLong(options[0]));

		PredictAnnotations.Predictions predictions = getPredictions(assay);
		if (predictions == null) return;

		for (Schema.Assignment assignment : predictions.assignments)
		{
			Set<String> annotValueURIs = predictions.annotations.get(assignment.propURI);
			String annotations = annotValueURIs.stream()
					.map(uri -> Common.getOntoValues().getLabel(uri))
					.filter(Objects::nonNull)
					.collect(Collectors.joining(", "));
			if (annotations.isEmpty()) annotations = "<not annotated or not a value>";

			Util.writeln(assignment.name + ": " + annotations);
			for (Method method : Method.methods())
				printFormattedSuggestions(method, predictions.get(method).getOrDefault(assignment.propURI, null), annotValueURIs);
			Util.writeln();
		}
	}

	private void printFormattedSuggestions(Method title, List<String> suggestions, Set<String> annotValueURIs)
	{
		if (suggestions == null || suggestions.isEmpty())
		{
			Util.writeln("  " + title + ": <no suggestions>");
			return;
		}
		Util.writeln("  " + title);
		for (String s : suggestions.subList(0, Math.min(suggestions.size(), 6)))
		{
			Util.writeln((annotValueURIs.contains(s) ? "  * " : "    ") + Common.getOntoValues().getLabel(s));
		}
	}

	private void analyzeSuggestions(Method title, List<String> suggestions, Set<String> annotValueURIs, Counters<String> counters)
	{
		counters.increment(title + "_size");
		if (suggestions == null || suggestions.isEmpty())
		{
			counters.increment(title + "_empty");
			return;
		}

		for (String annotValueURI : annotValueURIs)
		{
			if (suggestions.subList(0, Math.min(suggestions.size(), 3)).contains(annotValueURI))
				counters.increment(title + "_match");
			else
				counters.increment(title + "_missed");
			if (annotValueURI.equals(suggestions.get(0)))
				counters.increment(title + "_match_1");
		}
	}

	private void transferOrganism()
	{
		Optional<DictionaryPredict> dictPredict = getDictionaryPredict();
		if (!dictPredict.isPresent()) return;

		DataAssay dataAssay = Common.getDataStore().assay();

		Schema.Assignment proteinAssn = getAssignment("BAX_0000012", Common.getSchemaCAT());
		Schema.Assignment organismAssn = getAssignment("BAO_0002921", Common.getSchemaCAT());
		assert proteinAssn != null && organismAssn != null;

		Map<String, Counters<String>> missMatches = new TreeMap<>();
		Counters<String> counters = new Counters<>();
		for (long assayID : dataAssay.fetchAllCuratedAssayID())
		{
			counters.increment(NASSAYS);
			Assay assay = dataAssay.getAssay(assayID);

			PredictAnnotations.Predictions predictions = PredictAnnotations.getAnnotations(assay);

			Set<String> proteinValueURIs = predictions.annotations.get(proteinAssn.propURI);
			Set<String> organismValueURIs = predictions.annotations.get(organismAssn.propURI);
			List<OntologyTree.Branch> proteinTerms = proteinValueURIs.stream().map(uri -> Common.getOntoValues().getBranch(uri)).filter(Objects::nonNull).collect(Collectors.toList());
			if (proteinTerms.isEmpty()) continue;
			counters.increment("protein Annotation");

			String proteinText = proteinTerms.stream().map(u -> u.label).collect(Collectors.joining(" "));
			Map<String, List<String>> result = dictPredict.get().getPrediction(proteinText);
			List<String> organismPredictions = result.get(organismAssn.propURI);

			if (organismPredictions.isEmpty())
			{
				counters.increment("no prediction");
				continue;
			}
			counters.increment("predictions");

			if (organismValueURIs.isEmpty())
			{
				counters.increment("no organism Annotation");
				continue;
			}
			counters.increment("full Annotation");
			if (organismPredictions.isEmpty()) counters.increment("No prediction");
			for (String organismPrediction : organismPredictions)
			{
				if (organismValueURIs.contains(organismPrediction))
					counters.increment("match");
				else
				{
					counters.increment("mismatch");
					for (String organismValueURI : organismValueURIs)
					{
						String organismValueLabel = Common.getCustomName(Common.getSchemaCAT(), organismAssn.propURI, organismAssn.groupNest(), organismValueURI);
						if (organismValueLabel == null) organismValueLabel = Common.getOntoValues().getLabel(organismValueURI);

						String organismPredictionLabel = Common.getCustomName(Common.getSchemaCAT(), organismAssn.propURI, organismAssn.groupNest(), organismPrediction);
						if (organismPredictionLabel == null) organismPredictionLabel = Common.getOntoValues().getLabel(organismPrediction);

						Counters<String> c = missMatches.computeIfAbsent(organismValueLabel, k -> new Counters<String>());
						c.increment(organismPredictionLabel, assay.uniqueID);
					}
				}
			}
		}
		Util.writeln("Deduce organism from protein");
		counters.report();
		Util.writeln("Mismatches");
		for (Map.Entry<String, Counters<String>> e : missMatches.entrySet())
		{
			Util.writeln(e.getKey());
			Util.writeln(e.getValue().toString("  ", false));
		}
	}
	
	// generate a raw list of truth for each assay in the dataset
	private void generateTruthTable(String[] options)
	{
		if (options.length == 0)
		{
			Util.writeln("Provide output filename (.csv)");
			return;
		}
		
		Counters<String> counters = new Counters<>();
		DataStore store = Common.getDataStore();
		long[] assayIDList = store.assay().fetchAllCuratedAssayID();
		int sz = assayIDList.length;
		Util.writeln("Calculating truth table metrics for " + sz + " assays...");
		
		int[] countTP = new int[sz], countFP = new int[sz], countFN = new int[sz];
		Map<String, Tally> tallyMap = new HashMap<>();
		
		for (int n = 0; n < sz; n++)
		{
			Assay assay = store.assay().getAssay(assayIDList[n]);
			Util.writeFlush((n + 1) + "/" + sz + " assay [" + assay.uniqueID + "]");

			int[] truth = calculateTruth(assay, counters, tallyMap);
			countTP[n] = truth[0];
			countFP[n] = truth[1];
			countFN[n] = truth[2];
			
			Util.writeln(" truth: TP=" + truth[0] + " FP=" + truth[1] + " FN=" + truth[2]);
			if ((n + 1) % 10 == 0 && n < sz - 1)
			{
				float[] statsTP = partialStats(countTP, n + 1), statsFP = partialStats(countFP, n + 1);
				Util.writeln("    current tally: TP=[" + statsTP[0] + " \u00B1 " + statsTP[1] + "] FP=[" + statsFP[0] + " \u00B1 " + statsFP[1] + "]");
			}
		}
		
		Util.writeln(counters.toString("", true));
		float[] statsTP = partialStats(countTP, sz), statsFP = partialStats(countFP, sz), statsFN = partialStats(countFN, sz);
		Util.writeln("Final tally:");
		Util.writeln("    TP: " + statsTP[0] + " \u00B1 " + statsTP[1]);
		Util.writeln("    FP: " + statsFP[0] + " \u00B1 " + statsFP[1]);
		Util.writeln("    FN: " + statsFN[0] + " \u00B1 " + statsFN[1]);
		
		// write the truth table to a CSV file
		File of = new File(options[0]);		
		Util.writeln("Writing to: " + of.getAbsolutePath());
		try (Writer wtr = new FileWriter(of))
		{
			wtr.write("AssayID,TP,FP,FN\n");
			for (int n = 0; n < sz; n++) wtr.write(assayIDList[n] + "," + countTP[n] + "," + countFP[n] + "," + countFN[n] + "\n");
		}
		catch (IOException ex) 
		{
			ex.printStackTrace();
		}
		
		// if requested, store information about false positive tallies as a JSON file
		if (options.length >= 2)
		{
			Tally[] termTally = tallyMap.values().toArray(new Tally[tallyMap.size()]);
			Arrays.sort(termTally, (t1, t2) -> t2.countFP - t1.countFP);
			Util.writeln("Terms tallied up: " + termTally.length);
			
			JSONArray json = new JSONArray();
			for (Tally tally : termTally) if (tally.countFP > 0)
			{
				JSONObject obj = new JSONObject();
				obj.put("name", tally.name);
				obj.put("uri", tally.uri);
				obj.put("countFP", tally.countFP);
				obj.put("countTP", tally.countTP);
				obj.put("propURI", tally.assn.propURI);
				obj.put("propLabel", tally.assn.name);
				obj.put("groupNest", tally.assn.groupNest());
				obj.put("fpAssays", tally.fpAssays);
				json.put(obj);
			}
			Util.writeln("  .. with false positives: " + json.length());
			
			of = new File(options[1]);
			Util.writeln("Writing tally to: " + of.getAbsolutePath());
			try (Writer wtr = new FileWriter(of))
			{
				wtr.write(json.toString(4));
			}
			catch (IOException ex)
			{
				ex.printStackTrace();
			}
		}
	}
	
	// returns TP/FP/FN for a single assay; note that we don't care about true negatives (because it's almost everything in the ontology)
	private int[] calculateTruth(Assay assay, Counters<String> counters, Map<String, Tally> tallyMap)
	{
		if (Util.isBlank(assay.text) || Util.length(assay.annotations) == 0) return new int[]{0, 0, 0}; // count it zero if no content to work with
		
		Schema schema = Common.getSchema(assay.schemaURI);
		if (schema == null) return new int[]{0, 0, 0, 0};
		
		Set<String> assayKeys = new HashSet<>(), predKeys = new HashSet<>();
		Map<String, Tally> predTally = new HashMap<>();
		
		Set<String> labels = new HashSet<>();
		
		assay.annotations = new Annotation[0]; // bwah!
		PredictAnnotations.Predictions predictions = getPredictions(assay);
		for (Map.Entry<String, List<String>> grp : predictions.get(Method.DICTIONARY).entrySet())
		{
			String propURI = grp.getKey();
			Schema.Assignment[] assnList = schema.findAssignmentByProperty(propURI); // note: assuming disambiguation
			if (assnList.length == 0) continue;
			String[] groupNest = assnList[0].groupNest();
			for (String valueURI : grp.getValue())
			{
				String key = Schema.keyPropGroupValue(propURI, groupNest, valueURI);
				String valueLabel = Common.getCustomName(schema, propURI, groupNest, valueURI);
				if (valueLabel == null) valueLabel = Common.getOntoValues().getLabel(valueURI);

				labels.add(valueLabel);
				predKeys.add(key);
				
				Tally tally = new Tally();
				tally.uri = valueURI;
				tally.name = valueLabel;
				tally.assn = assnList[0];
				predTally.put(key, tally);
			}
		}
		for (Annotation annot : assay.annotations)
		{
			String valueLabel = Common.getCustomName(schema, annot.propURI, annot.groupNest, annot.valueURI);
			if (valueLabel == null) valueLabel = Common.getOntoValues().getLabel(annot.valueURI);

			labels.remove(valueLabel);
			assayKeys.add(Schema.keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueURI));
		}
		
		for (String label : labels) counters.increment(label, assay.uniqueID);
		
		int tp = 0, fp = 0, fn = 0;
		for (String pred : predKeys) 
		{
			Tally tally = tallyMap.get(pred);
			if (tally == null) 
			{
				tally = predTally.get(pred);
				if (tally != null) tallyMap.put(pred, tally);
			}
			
			if (assayKeys.contains(pred)) 
			{
				tp++;
				if (tally != null) tally.countTP++;
			}
			else
			{
				fp++;
				if (tally != null) 
				{
					tally.countFP++;
					tally.fpAssays = ArrayUtils.add(tally.fpAssays, assay.uniqueID);
				}
			}
		}
		for (String got : assayKeys) if (!predKeys.contains(got)) fn++;
		return new int[]{tp, fp, fn};
	}

	// calculates average & stddev up to a size marker
	private float[] partialStats(int[] counts, int sz)
	{
		if (sz == 0) return new float[]{0, 0};
		if (sz == 1) return new float[]{counts[0], 1};

		float invsz = 1.0f / sz, avg = 0, dev = 0;
		for (int n = sz - 1; n >= 0; n--) avg += counts[n];
		avg *= invsz;
		for (int n = sz - 1; n >= 0; n--) {final float d = counts[n] - avg; dev += d * d;}
		dev = (float)Math.sqrt(dev * invsz);
		return new float[]{avg, dev};
	}

	private Optional<DictionaryPredict> getDictionaryPredict()
	{
		try
		{
			DictionaryPredict builder = new DictionaryPredict();
			return Optional.of(builder);
		}
		catch (IOException e)
		{
			Util.writeln("Error loading synonyms");
			return Optional.empty();
		}
	}

	private Schema.Assignment getAssignment(String query, Schema schema)
	{
		for (Schema.Assignment assignment : schema.getRoot().flattenedAssignments())
		{
			if (assignment.propURI.contains(query)) return assignment;
		}
		Util.writeln("Cannot find assignment matching " + query);
		return null;
	}

	private PredictAnnotations.Predictions getPredictions(Assay assay)
	{
		return getPredictions(assay, Method.methods());
	}

	private PredictAnnotations.Predictions getPredictions(Assay assay, List<Method> methods)
	{
		try
		{
			return PredictAnnotations.getPredictions(assay, methods);
		}
		catch (IOException e)
		{
			Util.writeln("Error predicting assay");
			return null;
		}
	}

	private PredictAnnotations.Predictions getPredictions(Assay assay, Schema.Assignment assignment)
	{
		try
		{
			return PredictAnnotations.getPredictions(assay, assignment);
		}
		catch (IOException e)
		{
			Util.writeln("Error predicting assay");
			return null;
		}
	}
}

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
import com.cdd.bae.model.dictionary.*;
import com.cdd.bae.tasks.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.awt.image.*;
import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;

/*
	Takes two collections of assays (training & testing) and determines how well predictions work in a "simulated reality". First, the
	training set is used to rebuild the models (NLP & correlation). Then for each assay in the testing set, the predictions begin:
	
		(1) all available annotations are predicted for likelihood
		(2) highest predicted term is proposed as a candidate
			(a) if the term is correct, count as a hit
			(b) else count as a miss
		(3) repeat (1), nothing that the remaining predictions may be altered by the correlation models
	
	This simulates what would happen if the user blindly picked the "best" prediction, then made a judgment call about whether it's
	right or wrong.
	
	The outcome is a chart that provides an easy to visualise representation of how useful the suggestions are.
*/

public class MetricsSequence
{
	private Assay[] testAssays, trainAssays;
	private File outFile;
	
	private Map<Long, int[]> assayNLP = new HashMap<>(); // per assayID: list of NLP indices
	private Map<Long, int[]> assayAnnots = new HashMap<>(); // per assayID: list of annotation indices
	private int[] explicitAnnots = new int[0]; // which of the annotations has been asserted explicitly
	private Map<Integer, Set<Integer>> annotBranch = new HashMap<>(); // per annotation: list of parent items
	private Map<Integer, Model> modelNLP = new HashMap<>(); // per target: generated model from NLP fingerprints
	private Map<Integer, Model> modelCorr = new HashMap<>(); // per target: generated model from prior annotations
	private Map<Integer, AnnotationFP> targetToAnnot = new TreeMap<>();
	private Map<String, Integer> annotToTarget = new HashMap<>();
	private Map<Integer, Integer> targetCounts = new HashMap<>(); // per target: # times it occurs in the training set
	private Map<Integer, int[]> targetRanks = new HashMap<>(); // per target: the ranks in which hits occur (i.e. when they should be there)

	public static final class TestResult
	{
		public long assayID;
		public float score;
		public boolean[] hitseq;
		public int[] hitcount;
	}
	private List<TestResult> testResults = new ArrayList<>(); // evaluation of each test set assay

	private final class Prediction
	{
		int annot;
		double value;
	}
	
	private enum Technique
	{
		NLP, // natural language probabilistic models
		CORR, // correlation probabilistic models
		TEXT, // text mining extraction
		AXIOM, // axiomatic rules
		ASSOC, // associative rules
	}
	private Set<Technique> techniques = new TreeSet<>();

	private AxiomVocab axvoc = null;

	// ------------ public methods ------------

	public MetricsSequence(Assay[] testAssays, Assay[] trainAssays, File outFile, String techniques)
	{
		this.testAssays = testAssays;
		this.trainAssays = trainAssays;
		this.outFile = outFile;
		
		for (String t : techniques.split(",")) this.techniques.add(Technique.valueOf(t.toUpperCase()));
	}
	
	public void build() throws IOException
	{
		fetchIndices();
		buildNLPModels();
		buildCorrModels();
		loadAxioms();
		evaluateTest();
		createGraphics();
		createOtherStats();
	}
	
	// ------------ private methods ------------
	
	// fetches all of the assay-to-NLP content from the BAE database
	private void fetchIndices() throws IOException
	{
		Util.writeln("Obtaining NLP fingerprints/annotations...");
		Util.writeln("    Techniques: " + techniques);
	
		// training set: invoke the fingerprint builder to create the subset of fingerprints & targets that will be used to make a model
	
		ModelBuilder builder = new ModelBuilder();
		List<int[]> fplist = new ArrayList<>(), annotlist = new ArrayList<>();
		Set<Integer> explicitSet = new HashSet<>();
		
		long[] assayIDTrain = new long[trainAssays.length];
		for (int n = 0; n < trainAssays.length; n++) assayIDTrain[n] = trainAssays[n].assayID;

		ModelUtilities.getTargetAnnotMaps(annotToTarget, targetToAnnot);
		builder.compileAssayInfo(assayIDTrain, fplist, annotlist, annotToTarget, explicitSet, 10000);
		
		for (int t : targetToAnnot.keySet()) targetCounts.put(t, 0);
		for (int[] annots : annotlist) for (int t : annots) targetCounts.put(t, targetCounts.get(t) + 1);
		
		for (int n = 0; n < assayIDTrain.length; n++) 
		{
			int[] fp = fplist.get(n), annots = annotlist.get(n);
			if (fp == null || annots == null)
			{
				trainAssays = ArrayUtils.remove(trainAssays, n);
				assayIDTrain = ArrayUtils.remove(assayIDTrain, n);
				fplist.remove(n);
				annotlist.remove(n);
				n--;
				continue;
			}
			assayNLP.put(assayIDTrain[n], fplist.get(n));
			assayAnnots.put(assayIDTrain[n], annotlist.get(n));
		}

		final String SEP = ModelUtilities.SEP;
		for (Assay assay : trainAssays)
		{
			Schema schema = Common.getSchema(assay.schemaURI);
			for (Annotation a : assay.annotations)
			{
				Integer annot = annotToTarget.get(a.propURI + SEP + a.valueURI);
				if (annot == null) continue;
				SchemaTree tree = Common.obtainTree(schema, a.propURI, a.groupNest);
				if (tree == null) continue;
				for (String valueURI : tree.expandAncestors(a.valueURI))
				{
					Integer ancestor = annotToTarget.get(a.propURI + SEP + valueURI);
					if (ancestor != null && ancestor != annot)
					{
						Set<Integer> branch = annotBranch.get(annot);
						if (branch == null) {branch = new HashSet<>(); annotBranch.put(annot, branch);}
						branch.add(ancestor);
					}
				}
			}
		}
		
		explicitAnnots = Util.primInt(explicitSet);
		Arrays.sort(explicitAnnots);
		
		// testing set: have to derive these separately so as to avoid contaminating the model's selection of fingerprints & targets
		
		for (Assay assay : testAssays)
		{
			Schema schema = Common.getSchema(assay.schemaURI);

			Set<Integer> uniqueAnnots = new HashSet<>();
			for (Annotation a : assay.annotations)
			{
				boolean want = false;
				for (Schema.Assignment assn : schema.findAssignmentByProperty(a.propURI)) 
					if (assn.suggestions == Schema.Suggestions.FULL) {want = true; break;}
				if (!want) continue;

				SchemaTree tree = Common.obtainTree(schema, a.propURI, a.groupNest);
				if (tree == null) continue; // ugh
				for (String valueURI : tree.expandAncestors(a.valueURI))
				{
					String key = a.propURI + SEP + valueURI;
					Integer val = annotToTarget.get(key);
					if (val != null) uniqueAnnots.add(val);
				}
			}
			int[] target = Util.primInt(uniqueAnnots);
			Arrays.sort(target);
			
			int[] finger = Arrays.copyOf(assay.fplist, assay.fplist.length);
			Arrays.sort(finger);

			assayNLP.put(assay.assayID, finger);
			assayAnnots.put(assay.assayID, target);
		}
	}
	
	// builds an NLP model for every target
	private void buildNLPModels() throws IOException
	{
		int[] targets = explicitAnnots;
		
		Util.writeln("Building NLP models: # targets = " + targets.length);
		
		final int trainsz = trainAssays.length;		
		int[][] fplist = new int[trainsz][];
		for (int n = 0; n < trainsz; n++) fplist[n] = assayNLP.get(trainAssays[n].assayID);
		boolean[] active = new boolean[trainsz];
		
		for (int n = 0; n < targets.length; n++)
		{
			Util.writeFlush(" ... NLP model (" + (n + 1) + "/" + targets.length + "): target=" + targets[n]);
			
			int numActive = 0;
			for (int i = 0; i < trainsz; i++) 
			{
				active[i] = Arrays.binarySearch(assayAnnots.get(trainAssays[i].assayID), targets[n]) >= 0;
				if (active[i]) numActive++;
			}
			
			Util.writeFlush(" #active=" + numActive + "/" + trainsz);
			//Util.writeFlush(" " + Util.arrayStr(active));
			
			ModelBuilder builder = new ModelBuilder();
			
			float[] rocAUC = new float[1];
			Model model = builder.buildModel(fplist, active, rocAUC);
			if (model == null)
			{
				Util.writeln(" (no model)");
				continue;
			}
			
			Util.writeln(" ROC=" + (float)rocAUC[0] + " calib=" + (float)model.calibLow + "/" + (float)model.calibHigh);
			
			model.target = targets[n];
			modelNLP.put(targets[n], model);
		}
	}
	
	// builds a correlation model for every target
	private void buildCorrModels() throws IOException
	{
		int[] targets = explicitAnnots;

		Util.writeln("Building correlation models: # targets = " + targets.length);
		
		for (int n = 0; n < targets.length; n++)
		{
			Util.writeFlush(" ... correlation model (" + (n + 1) + "/" + targets.length + "): target=" + targets[n]);
			
			List<int[]> fplist = new ArrayList<>();
			List<Boolean> active = new ArrayList<>();
			
			for (Assay assay : trainAssays)
			{
				int[] annotations = assayAnnots.get(assay.assayID);
				Set<Integer> fp = new TreeSet<>();
				boolean isActive = false;
				for (int annot : annotations)
				{
					if (annot == targets[n]) isActive = true; else fp.add(annot);
				}
				
				if (fp.isEmpty()) continue;
				
				fplist.add(Util.primInt(fp));
				active.add(isActive);
			}
			
			Model model = CorrelationBuilder.buildModel(fplist.toArray(new int[fplist.size()][]), Util.primBoolean(active));
			if (model == null)
			{
				Util.writeln(" (no model)");
				continue;
			}

			Util.writeln(" calib=" + (float)model.calibLow + "/" + (float)model.calibHigh);

			model.target = targets[n];
			modelCorr.put(targets[n], model);
		}
	}
	
	// loads up axioms: preliminary implementation that's external to the eventual integration
	private void loadAxioms() throws IOException
	{
		if (!techniques.contains(Technique.AXIOM)) return;
		
		axvoc = new AxiomVocab();
		for (File f : new File("/opt/bae/axioms").listFiles()) if (f.getName().endsWith(".json"))
		{
			AxiomVocab load = AxiomVocab.deserialise(f);
			for (AxiomVocab.Rule rule : load.getRules()) axvoc.addRule(rule);
		}
		
		Util.writeln("Total # axiom rules: " + axvoc.numRules());
	}
	
	// the money shot: figuring out how well the test set works	
	private void evaluateTest() throws IOException
	{
		Util.writeln("Evaluating testing set...");
		
		for (int n = 0; n < testAssays.length; n++)
		{
			Util.writeFlush(" ... test (" + (n + 1) + "/" + testAssays.length + "):");
			
			Assay assay = testAssays[n];
			Schema schema = SchemaDynamic.compositeSchema(Common.getSchema(assay.schemaURI), assay.schemaBranches, assay.schemaDuplication);
			
			TestResult result = new TestResult();
			result.assayID = testAssays[n].assayID;
			
			Set<Integer> annotations = new HashSet<>(); // annotations corresponding to this assay
			Set<Integer> explicit = new HashSet<>(); // annotations that are part of the explicit set
			Set<Integer> modelAnnots = new HashSet<>(); // all annotations that have a model
			List<Integer> seqAnnots = new ArrayList<>(); // predicted annotations in their incoming order (correct or otherwise)
			List<Integer> hitAnnots = new ArrayList<>(); // as above, but only the correctly predicted annotations
			List<Integer> corrAnnots = new ArrayList<>(); // as for hitAnnots, but includes the whole branch: used for correlation
			for (int annot : assayAnnots.get(testAssays[n].assayID)) 
			{
				annotations.add(annot);
				if (Arrays.binarySearch(explicitAnnots, annot) >= 0) explicit.add(annot);
			}
			if (explicit.size() == 0) {Util.writeln("  nothing"); continue;}
			
			for (Model model : modelNLP.values()) modelAnnots.add(model.target);
			int totalModels = modelAnnots.size();
			
			// if asked for, do text mining first (it precedes the probabilistic models)
			if (techniques.contains(Technique.TEXT))
			{
				int[] predict = predictTextExtraction(testAssays[n], modelAnnots);
				for (int annot : predict)
				{
					modelAnnots.remove(annot);
					seqAnnots.add(annot);
					if (explicit.contains(annot)) 
					{
						hitAnnots.add(annot);
						corrAnnots.add(annot);
						Set<Integer> branch = annotBranch.get(annot);
						if (branch != null) for (int ancestor : branch) corrAnnots.add(ancestor);
						break;
					}
				}
			}

			// calculate the NLP-based predictions just once, because they don't vary
			Map<Integer, Double> basicNLP = new HashMap<>();
			int[] fingerNLP = assayNLP.get(testAssays[n].assayID);
			for (int annot : modelAnnots) basicNLP.put(annot, computePrediction(fingerNLP, modelNLP.get(annot)));

			// iteratively predict: each time a hit is achieved, need to rederive the correlation models
			while (!modelAnnots.isEmpty() && hitAnnots.size() < explicit.size())
			{
				int[] fingerCorr = Util.primInt(hitAnnots);
				int[] predict = null;
				if (techniques.contains(Technique.CORR))
					predict = predictRemainingCorrelation(basicNLP, fingerCorr, modelAnnots);
				else
					predict = predictRemainingBasic(basicNLP, modelAnnots);

				// repartition the list so that axiom-allowed are in the first block, disallowed follows; order is otherwise preserved
				if (techniques.contains(Technique.AXIOM)) predict = applyAxioms(schema, predict, modelAnnots);
				
				// consume the predicted annotations: whenever we get a hit, have to stop and run around the loop again, because the correlations change
				for (int annot : predict)
				{
					modelAnnots.remove(annot);
					seqAnnots.add(annot);
					if (explicit.contains(annot)) 
					{
						hitAnnots.add(annot);
						corrAnnots.add(annot);
						Set<Integer> branch = annotBranch.get(annot);
						if (branch != null) for (int ancestor : branch) corrAnnots.add(ancestor);
						break;
					}
				}
			}
	
			result.hitseq = new boolean[totalModels];
			result.hitcount = new int[totalModels];
			int[] rank = new int[explicit.size()];
			int pos = 0;
			for (int i = 0; i < seqAnnots.size(); i++) 
			{
				int target = seqAnnots.get(i);
				result.hitseq[i] = explicit.contains(target);
				if (result.hitseq[i]) 
				{
					rank[pos++] = i;
					result.hitcount[i] = targetCounts.get(target);
					targetRanks.put(target, ArrayUtils.add(targetRanks.get(target), i));
				}
			}
			rank = Arrays.copyOf(rank, pos);
			
			result.score = rankScore(rank, totalModels);
						
			Util.writeln(" score=" + result.score + " [" + Util.arrayStr(rank) + "]/" + totalModels); //+ " " + Util.arrayStr(hitseq));
			
			testResults.add(result);
		}
	}
	
	// performs a complete ranking based on just the NLP predictions (a simple run-through), and returns the computed score
	private float simpleNLPScore(Map<Integer, Double> basicNLP, Set<Integer> hitAnnots, Set<Integer> modelAnnots)
	{
		int sz = modelAnnots.size();
		Prediction[] pred = new Prediction[sz];
		int pos = 0;
		for (int annot : modelAnnots)
		{
			pred[pos] = new Prediction();
			pred[pos].annot = annot;
			pred[pos].value = basicNLP.get(annot);
			pos++;
		}
		
		// sort in reverse
		Arrays.sort(pred, (p1, p2) -> -Double.compare(p1.value, p2.value));
		
		int[] rank = new int[hitAnnots.size()];
		pos = 0;
		for (int n = 0; n < pred.length; n++) if (hitAnnots.contains(pred[n].annot)) rank[pos++] = n;
		rank = Arrays.copyOf(rank, pos);
		
		return rankScore(rank, sz);
	}
		
	// given enough information to compute NLP & correlations, and a list of annotations to predict for, determine scores for everything
	// and return a list of these predictions - best first
	private int[] predictRemainingCorrelation(Map<Integer, Double> basicNLP, int[] fingerCorr, Set<Integer> todoAnnots)
	{
		int sz = todoAnnots.size();
		Prediction[] pred = new Prediction[sz];
		int pos = 0;
		for (int annot : todoAnnots)
		{
			pred[pos] = new Prediction();
			pred[pos].annot = annot;
			pred[pos].value = basicNLP.get(annot);

			Model model = modelCorr.get(annot);
			if (model != null)
			{
				double corr = computePrediction(fingerCorr, model);
				pred[pos].value = 0.5 * (pred[pos].value + corr);
			}
			
			pos++;
		}
		
		// sort in reverse
		Arrays.sort(pred, (p1, p2) -> -Double.compare(p1.value, p2.value));
		int[] ret = new int[sz];
		for (int n = 0; n < sz; n++) ret[n] = pred[n].annot;
		
		return ret;
	}
	
	// as above, but with no correlation intelligence
	private int[] predictRemainingBasic(Map<Integer, Double> basicNLP, Set<Integer> todoAnnots)
	{
		int sz = todoAnnots.size();
		Prediction[] pred = new Prediction[sz];
		int pos = 0;
		for (int annot : todoAnnots)
		{
			pred[pos] = new Prediction();
			pred[pos].annot = annot;
			pred[pos].value = basicNLP.get(annot);
			pos++;
		}
		
		// sort in reverse
		Arrays.sort(pred, (p1, p2) -> -Double.compare(p1.value, p2.value));
		int[] ret = new int[sz];
		for (int n = 0; n < sz; n++) ret[n] = pred[n].annot;
		
		return ret;
	}
	
	// given a set of predictions on remaining annotations, checks each one to see if it's implied by axioms that involve the currently
	// known list of annotations; the result is a list that is reordered by axiom match strength; note that anything that is "forbidden"
	// by axioms is not deleted, it is just moved down to the end
	private int[] applyAxioms(Schema schema, int[] predict, Set<Integer> currentAnnots)
	{
		Schema.Assignment[] assnList = schema.getRoot().flattenedAssignments();
		boolean[] removeMask = new boolean[predict.length];
		
		for (Schema.Assignment subjAssn : assnList)
		{
			SchemaTree tree = Common.obtainTree(schema, subjAssn.propURI, subjAssn.groupNest());
			SchemaTree.Node[] nodes = tree == null ? null : tree.getFlat();
			if (nodes == null || nodes.length == 0) continue;

			for (AxiomVocab.Rule rule : axvoc.getRules())
			{
				if (rule.type != AxiomVocab.Type.LIMIT && rule.type != AxiomVocab.Type.EXCLUDE) continue;

				// see which nodes (for this assignment) make up the rule
				/*Set<SchemaTree.Node> branch = nodeBranch(schema, assn, rule.subject.valueURI, rule.subject.wholeBranch);
				if (branch.isEmpty()) continue;
				Set<String> branchValues = new HashSet<>();
				for (SchemaTree.Node node : branch) branchValues.add(node.uri);*/
				Set<String> subjValues = valueBranch(schema, subjAssn, rule.subject[0].valueURI, rule.subject[0].wholeBranch);
				if (subjValues.isEmpty()) continue;
				
				// see if any of the currently accepted annotations trigger the rule
				boolean triggered = false;
				for (int t : currentAnnots)
				{
					Annotation annot = targetToAnnot.get(t);
					if (subjValues.contains(annot.valueURI)) {triggered = true; break;}
				}
				if (!triggered) continue;
								
				// look into all other assignments to see if the rule's impact affects them
				for (Schema.Assignment objAssn : assnList)
				{
					tree = Common.obtainTree(schema, subjAssn.propURI, subjAssn.groupNest());
					nodes = tree == null ? null : tree.getFlat();
					if (nodes == null || nodes.length == 0) continue;
					
					Set<String> objValues = new HashSet<>();
					for (AxiomVocab.Term term : rule.impact) objValues.addAll(valueBranch(schema, objAssn, term.valueURI, term.wholeBranch));
					if (objValues.isEmpty()) continue;
					
					// for LIMIT: prep to remove anything not in the list; for EXCLUDE, that's the set to remove
					Set<String> exclValues = new HashSet<>();
					if (rule.type == AxiomVocab.Type.LIMIT)
					{
						for (SchemaTree.Node node : nodes) exclValues.add(node.uri);
						exclValues.removeAll(objValues);
					}
					else /* EXCLUDE */ exclValues.addAll(objValues);
					for (int n = 0; n < predict.length; n++) if (!removeMask[n])
					{
						Annotation annot = targetToAnnot.get(predict[n]);
						if (annot.propURI.equals(objAssn.propURI) && exclValues.contains(annot.valueURI)) removeMask[n] = true;
					}
				}
			}
		}	
	
		// resulting prediction list is reordered: allowed first, disallowed next
		int[] ret = new int[predict.length];
		int sz = 0;
		for (int n = 0; n < predict.length; n++) if (!removeMask[n]) ret[sz++] = predict[n];
		for (int n = 0; n < predict.length; n++) if (removeMask[n]) ret[sz++] = predict[n];
		return ret;
	}
	
	// make predictions based on text mining
	private int[] predictTextExtraction(Assay assay, Set<Integer> todoAnnots) throws IOException
	{
		if (Util.isBlank(assay.text)) return new int[0];
		Schema schema = Common.getSchema(assay.schemaURI);
		if (schema == null) return new int[0];
		
		DictionaryPredict dpred = new DictionaryPredict();
		Map<String, List<ScoredHit>> results = dpred.getPredictionHits(assay);
		List<Prediction> predList = new ArrayList<>();
	
		for (Map.Entry<String, List<ScoredHit>> batch : results.entrySet())
		{
			String propURI = batch.getKey();
			for (ScoredHit hit : batch.getValue())
			{
				int target = annotToTarget.getOrDefault(propURI + "::" + hit.hit.uri, -1); // very ick
				if (target < 0) continue;
			
				Prediction pred = new Prediction();
				pred.annot = target;
				pred.value = hit.score;
				predList.add(pred);
			}
		}
		
		// sort by decreasing score, then keep only first of each target
		Collections.sort(predList, (p1, p2) -> -Double.compare(p1.value, p2.value));
		Set<Integer> already = new HashSet<>();
		for (Iterator<Prediction> it = predList.iterator(); it.hasNext();)
		{
			Prediction pred = it.next();
			if (already.contains(pred.annot)) it.remove(); else already.add(pred.annot);
		}
		int[] ret = new int[predList.size()];
		for (int n = 0; n < ret.length; n++) ret[n] = predList.get(n).annot;
		return ret;
	}
	
	
	// computes the calibrated NLP prediction for the given fingerprints
	private double computePrediction(int[] fingerprints, Model model)
	{
		double raw = 0;
		for (int fp : fingerprints)
		{
			// note: both 'fplist's are sorted, so could just parallel-track them
			int i = Arrays.binarySearch(model.fplist, fp);
			if (i >= 0) raw += model.contribs[i];
		}
		
		// convert the raw Bayesian output based on the calibration range (originally from the ROC): if there's no range, just give it a
		// more/less binary choice; if there is a range, use a sinusoidal pattern, using tan: so 0 and 1 are approached asymptotically
		
		if (model.calibLow == model.calibHigh) return raw > model.calibLow ? 0.7 : 0.3;

		double cal = (raw - model.calibLow) / (model.calibHigh - model.calibLow);
		return Math.atan(cal) / Math.PI + 0.5;
	}
	
	// converts the rank ordering of correct predictions into a score where 1 is perfection and 0 is perfectly antipredictive
	private float rankScore(int[] rank, int totalSize)
	{
		int points = 0;
		for (int n = 0; n < rank.length; n++) points += rank[n] - n;
		float worst = (totalSize - rank.length) * rank.length;
		return 1 - points / worst;
	}	
	
	// turns the result set into an image
	private void createGraphics() throws IOException
	{
		Collections.sort(testResults, (r1, r2) ->
		{
			for (int i = Math.min(r1.hitseq.length, r2.hitseq.length) - 1; i >= 0; i--)
			{
				if (r1.hitseq[i] && !r2.hitseq[i]) return 1;
				if (r2.hitseq[i] && !r1.hitseq[i]) return -1;
				if (r1.hitseq[i] && r2.hitseq[i]) return 0;
			}
			return 0;
		});
		
		double avg = 0, dev = 0;
		for (TestResult r : testResults) avg += r.score;
		avg /= testResults.size();
		for (TestResult r : testResults) dev += Util.sqr(r.score - avg);
		dev = Math.sqrt(dev / testResults.size());
		
		Util.writeln("Training set size:  " + trainAssays.length);
		Util.writeln("Testing set size:   " + testResults.size());
		Util.writeln(String.format("Average score: %.4f", avg));
		Util.writeln(String.format("          +/-: %.4f", dev));

		exportImage(outFile, null, 0);
		exportImage(outFile, "_1", 1);
		exportImage(outFile, "_2", 1);
	}
	
	// whites out a graphical representation of the test results
	private void exportImage(File outFile, String suffix, int lowThresh) throws IOException
	{
		if (suffix != null)
		{
			String path = outFile.getPath();
			int dot = path.lastIndexOf('.');
			outFile = new File(path.substring(0, dot) + suffix + path.substring(dot));
		}
	
		int width = testResults.get(0).hitseq.length, height = testResults.size();
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		for (int n = 0; n < height; n++)
		{
			TestResult result = testResults.get(n);
			int runlen = width;
			while (runlen > 0 && !result.hitseq[runlen - 1]) runlen--;
			
			for (int i = 0; i < runlen; i++)
			{
				int rgb = 0xE6EDF2;
				if (result.hitseq[i]) rgb = result.hitcount[i] <= lowThresh ? 0xFF0000 : 0x1362B3;
				img.setRGB(i, n, 0xFF000000 | rgb);
			}
		}

		Util.writeln("Writing: " + outFile.getPath());
		OutputStream ostr = new FileOutputStream(outFile);
		javax.imageio.ImageIO.write(img, "png", ostr);
		ostr.close();
	}
	
	// secondary statistics
	private void createOtherStats() throws IOException
	{
		// showing correlation between rank and target
		Integer[] targets = targetCounts.keySet().toArray(new Integer[targetCounts.size()]);
		Arrays.sort(targets, (t1, t2) -> targetCounts.get(t1) - targetCounts.get(t2));
		
		File f = new File("/tmp/target_countrank.txt");
		Util.writeln("Writing [" + f.getPath() + "]");
		try (Writer wtr = new BufferedWriter(new FileWriter(f)))
		{
			/* plots the average... actually less useful
			wtr.write("Target\tCount\tRankAvg\tRankErr\tRanks\n");
			for (int t : targets)
			{
				int[] ranks = targetRanks.get(t);
				if (ranks == null) continue;
				double avg = 0, stderr = 0;
				for (int r : ranks) avg += r;
				avg /= ranks.length;
				for (int r : ranks) stderr += Util.sqr(r - avg);
				stderr = Math.sqrt(stderr / ranks.length);
				String[] rankstr = new String[ranks.length];
				for (int n = 0; n < ranks.length; n++) rankstr[n] = String.valueOf(ranks[n]);
				wtr.write(String.format("%d\t%d\t%g\t%g\t%s\n", t, targetCounts.get(t), avg, stderr, String.join(":", rankstr)));
			}*/
			
			wtr.write("Count\tRank\n");
			for (int t : targets)
			{
				int[] ranks = targetRanks.get(t);
				if (ranks == null) continue;
				int count = targetCounts.get(t);
				for (int r : ranks) wtr.write(count + "\t" + r + "\n");
			}
		}
	}

	// for a URI, find all of the instances in the schema where it matches and has a model index
	private int[] targetsForURI(Schema schema, String uri, boolean wholeBranch)
	{
		int[] targets = new int[0];
		for (Schema.Assignment assn : schema.getRoot().flattenedAssignments())
		{
			SchemaTree tree = Common.obtainTree(schema, assn.propURI, assn.groupNest());
			if (tree == null) continue; // ugh
			SchemaTree.Node node = tree.getNode(uri);
			if (node == null) continue;
			List<SchemaTree.Node> stack = new ArrayList<>();
			stack.add(node);
			while (!stack.isEmpty())
			{
				node = stack.remove(0);
				Integer idx = annotToTarget.get(assn.propURI + ModelUtilities.SEP + uri);
				if (idx != null && ArrayUtils.indexOf(targets, idx) < 0) targets = ArrayUtils.add(targets, idx);
				if (wholeBranch) stack.addAll(node.children);
			}
			
		}
		return targets;
	}
	
	// if a particular URI is within a node branch, return all that apply (can be multiple if whole branch is requested)
	/*private Set<SchemaTree.Node> nodeBranch(Schema schema, Schema.Assignment assn, String uri, boolean wholeBranch)
	{
		Set<SchemaTree.Node> branch = new HashSet<>();
		SchemaTree tree = Common.obtainTree(schema, assn.propURI, assn.groupNest());
		if (tree == null) return branch;
		SchemaTree.Node node = tree.getNode(uri);
		if (node == null) return branch;

		List<SchemaTree.Node> stack = new ArrayList<>();
		stack.add(node);
		while (!stack.isEmpty())
		{
			node = stack.remove(0);
			branch.add(node);
			if (wholeBranch) stack.addAll(node.children);
		}

		return branch;
	}*/
	private Set<String> valueBranch(Schema schema, Schema.Assignment assn, String uri, boolean wholeBranch)
	{
		Set<String> branch = new HashSet<>();
		SchemaTree tree = Common.obtainTree(schema, assn.propURI, assn.groupNest());
		if (tree == null) return branch;
		SchemaTree.Node node = tree.getNode(uri);
		if (node == null) return branch;

		List<SchemaTree.Node> stack = new ArrayList<>();
		stack.add(node);
		while (!stack.isEmpty())
		{
			node = stack.remove(0);
			branch.add(node.uri);
			if (wholeBranch) stack.addAll(node.children);
		}

		return branch;
	}	
}

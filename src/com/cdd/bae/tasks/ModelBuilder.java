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

package com.cdd.bae.tasks;

import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.model.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;
import java.util.Map.*;

import javax.servlet.*;

/*
	Background task: creation of models based on the NLP fingerprints + assays.
*/

public class ModelBuilder extends BaseMonitor implements Runnable
{
	private static final long DELAY_SECONDS = 10;
	private static final long SHORT_PAUSE_SECONDS = 5;
	private static final long LONG_PAUSE_SECONDS = (long)60 * 60;

	private static final long PAUSE_MODEL_BUILD_SECONDS = 5;
	
	private static ModelBuilder main = null;

	private static final int MAX_NLP_FINGERPRINTS = 10000; // (probably a bit low, but will do for testing; real number is more like a million)


	// ------------ lifecycle ------------

	@Override
	public void contextInitialized(ServletContextEvent ev) 
	{
		super.contextInitialized(ev);
		
		if (Common.getConfiguration() == null || Common.getParams() == null || Common.isStateless())
		{
			logger.info("Configuration not available or invalid: disabled");
			return;
		}

		new Thread(this).start();
	}

	// no need to override contextDestroyed
	
	// ------------ public methods ------------

	public ModelBuilder()
	{
		super();
		main = this;
	}

	public static ModelBuilder main()
	{
		return main;
	}

	// run in a background thread; expected to respond promptly to flipping of the stopped flag
	public void run()
	{
		// give it a moment: allow the server a chance to get settled in, or to be terminated
		waitTask(DELAY_SECONDS);
		if (stopped) return;

		// start the main loop
		long watermark = 0;
	
		while (!stopped)
		{
			if (FingerprintCalculator.isBusy())
			{
				waitTask(SHORT_PAUSE_SECONDS);
				continue;
			}
		
			long modWatermark = Common.getDataStore().model().getWatermarkNLP();
			if (modWatermark != watermark)
			{
				watermark = modWatermark;
				
				logger.info("updating models");
				createAllModels(watermark);
				logger.info("update complete");
			}
			else
			{
				logger.info("awaiting further action...");
				// wait for a long time: will get bumped if anything interesting happens
				waitTask(LONG_PAUSE_SECONDS);
			}
		}
	}

	// updates the current models to make sure they are consistent with the current watermark; if the models are partially/completely uptodate with the
	// current watermark, the only those out of date will be updated, i.e. a relatively quick scan; if the task is stopped or the watermark changes, will
	// return prematurely, to allow the caller to cycle back again
	public void createAllModels(long watermark)
	{
		DataStore store = Common.getDataStore();
		ModelUtilities.updateAnnotationFP();
		
		long[] assayIDList = store.assay().fetchAssayIDCurated();

		logger.info("constructing with # assays = {}", assayIDList.length);
		if (assayIDList.length == 0) return;
		
		Map<String, Integer> annotToTarget = new HashMap<>();
		Map<Integer, AnnotationFP> targetToAnnot = new TreeMap<>();
		ModelUtilities.getTargetAnnotMaps(annotToTarget, targetToAnnot);

		List<int[]> listFP = new ArrayList<>();
		List<int[]> annotlist = new ArrayList<>();
		Set<Integer> explicitAnnots = new HashSet<>();

		compileAssayInfo(assayIDList, listFP, annotlist, annotToTarget, explicitAnnots, MAX_NLP_FINGERPRINTS);
		
		final int numAssays = annotlist.size();
		int[][] fplist = listFP.toArray(new int[listFP.size()][]);
		
		// start with the NLP fingerprint models: build everything that makes sense
		int count = 0;
		for (Integer target : targetInPriorityOrder(targetToAnnot.keySet(), store.model().groupNLPByWatermarks()))
		{
			// early termination condition
			if (stopped) return;
			if (store.model().getWatermarkNLP() != watermark) return;
			
			// if the watermark is already uptodate, leave it alone (usually happens because of shutdown/restart)
			if (store.model().getModelNLPWatermark(target) == watermark) continue;

			if (!targetToAnnot.containsKey(target))
			{
				logger.info("annotation#{}, not modelled", target);
				store.model().blankModelCorr(target, watermark);
				continue;
			}

			boolean[] active = new boolean[numAssays];
			for (int n = 0; n < numAssays; n++) 
			{
				int[] annots = annotlist.get(n);
				active[n] = annots == null ? false : Arrays.binarySearch(annotlist.get(n), target) >= 0;
			}
			DataObject.Model model = buildModel(fplist, active);
			if (model != null)
			{
				logger.info("annotation#{}, source {}", target, targetToAnnot.get(target));
				//model.schemaURI = schemaURI;
				model.target = target;
				model.watermark = watermark;
				model.isExplicit = explicitAnnots.contains(model.target);
				store.model().submitModelNLP(model);
			}
			else
			{
				logger.info("annotation#{}, not modelled", target);
				store.model().blankModelNLP(target, watermark);
			}
			count++;
			if (count % 50 == 0)
			{
				pauseTask(PAUSE_MODEL_BUILD_SECONDS);
			}
		}
	}
	
	// create a prioritized list of targets for model building
	public static List<Integer> targetInPriorityOrder(Set<Integer> requiredTargets, SortedMap<Long, List<Integer>> modelsByWatermark)
	{
		Set<Integer> annotFPmodels = new HashSet<>();
		for (List<Integer> m : modelsByWatermark.values()) annotFPmodels.addAll(m);

		List<Integer> annotFPs = new ArrayList<>();
		// 1. annotFP found in targetToAnnot but not in database so that these are built first
		for (Integer annotFP : requiredTargets) if (!annotFPmodels.contains(annotFP)) annotFPs.add(annotFP);
		// 2. add models in database with old watermarks. Older models come first
		for (Entry<Long, List<Integer>> wm : modelsByWatermark.entrySet()) annotFPs.addAll(wm.getValue());
		return annotFPs;
	}

	// for a list of assays, formulate a list of {annotation fingerprints} and {annotation target indices}
	public void compileAssayInfo(long[] assayIDList, List<int[]> fplist, List<int[]> annotlist, 
								 Map<String, Integer> annotToTarget, Set<Integer> explicitAnnots,
								 int maxNLPFingerprints)
	{
		DataStore store = Common.getDataStore();
	
		// seed datastructures for gathering {NLP fingerprints --> assignment indices}
		Map<Integer, Integer> fpCount = new HashMap<>();
	
		Map<String, SchemaTree> treeCache = new HashMap<>();
	
		// pull down all the applicable assays and assiminate their data
		for (long assayID : assayIDList)
		{
			if (stopped) return;

			DataObject.Assay assay = null;
			try {assay = store.assay().getAssay(assayID);}
			catch (Exception ex) {logger.warn("Cannot retrieve assay with assayID " + assayID + ".", ex);}

			if (assay == null || assay.annotations == null) continue;
			Schema schema = Common.getSchema(assay.schemaURI);

			if (schema == null || Util.length(assay.fplist) == 0 || Util.length(assay.annotations) == 0)
			{
				fplist.add(null);
				annotlist.add(null);
				continue;
			}
			
			// accumulate fingerprint counts
			for (int fp : assay.fplist) Util.incr(fpCount, fp);
			
			// map assignment fingerprint (target) indices, and create anything not already extant
			Set<Integer> uniqueAnnots = new HashSet<>();
			for (DataObject.Annotation a : assay.annotations)
			{
				boolean want = false;
				for (Schema.Assignment assn : schema.findAssignmentByProperty(a.propURI)) 
					if (assn.suggestions == Schema.Suggestions.FULL) {want = true; break;}
				if (!want) continue;

				String cacheKey = schema.getSchemaPrefix() + "::" + Schema.keyPropGroup(a.propURI, a.groupNest);
				SchemaTree tree = null;
				if (treeCache.containsKey(cacheKey))
					tree = treeCache.get(cacheKey);
				else
					treeCache.put(cacheKey, tree = Common.obtainTree(schema, a.propURI, a.groupNest));
				if (tree == null) continue; // ugh
				
				for (String valueURI : tree.expandAncestors(a.valueURI))
				{
					String key = a.propURI + ModelUtilities.SEP + valueURI;
					Integer val = annotToTarget.get(key);
					uniqueAnnots.add(val);
					if (valueURI.equals(a.valueURI)) explicitAnnots.add(val);
				}
			}
			int[] annot = Util.primInt(uniqueAnnots);
			Arrays.sort(annot);
			
			int[] sortedFP = Arrays.copyOf(assay.fplist, assay.fplist.length);
			Arrays.sort(sortedFP);
			fplist.add(sortedFP);
			annotlist.add(annot);
		}
		
		// reduce the number of fingerprints, if necessary
		pruneFingerprints(fpCount, fplist, maxNLPFingerprints);
		// !! TO DO: make a separate count just for those with "approved text"; these only should be used for the ranking
	}

	// creates a Bayesian model, using the highly pre-processed inputs; the rocAUC is an optional parameter that can be used to
	// find out the cross validation status
	public DataObject.Model buildModel(int[][] fplist, boolean[] active) {return buildModel(fplist, active, null);}
	public DataObject.Model buildModel(int[][] fplist, boolean[] active, float[] rocAUC)
	{
		NaiveBayesian.Model nbModel = NaiveBayesian.buildModel(fplist, active);
		
		// return null if it was not possible to build a model (no data or only (in)actives)
		if (nbModel == null) return null;

		// copy results
		DataObject.Model dsModel = new DataObject.Model();
		dsModel.fplist = nbModel.fplist;
		dsModel.contribs = nbModel.contribs;
		dsModel.calibLow = nbModel.calibLow;
		dsModel.calibHigh = nbModel.calibHigh;
		
		if (rocAUC != null) rocAUC[0] = nbModel.rocAUC;
		
		return dsModel;
	}

	// ------------ protected methods ------------

	// reduce the number of fingerprints if necessary: achieves this by excluding from the bottom/top in frequency - these are least likely to provide
	// useful resolving power for the models, so they can be knocked out preferentially
	protected static void pruneFingerprints(Map<Integer, Integer> count, List<int[]> list, final int maxFP)
	{
		if (count.size() <= maxFP) return;
	
		// remove from the top/bottom frequency counts until total number of fingerprints is reduced
		// the only candidates for removal are the minimum or the maximum value of count
		while (count.size() > maxFP)
		{
			int minValue = count.values().stream().min(Integer::min).orElseThrow(IllegalStateException::new);
			int maxValue = count.values().stream().max(Integer::max).orElseThrow(IllegalStateException::new);
			int elim;
			if (Math.min(minValue, list.size() + 0.5f - minValue) < Math.min(maxValue, list.size() + 0.5f - maxValue)) 
				elim = minValue;
			else
				elim = maxValue;
			count.values().removeAll(Collections.singleton(elim));
		}
		
		// process all fingerprints in the list to reflect the smaller set
		for (int n = 0; n < list.size(); n++)
		{
			int[] fp = list.get(n);
			if (fp != null) list.set(n, Arrays.stream(fp).filter(count::containsKey).toArray());
		}
	}
}

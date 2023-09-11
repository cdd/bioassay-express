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

import javax.servlet.*;

import org.slf4j.*;

/*
	Background task: creation of models for correlations between annotation-of-interest from presence/absence of other annotations.
*/

public class CorrelationBuilder extends BaseMonitor implements Runnable
{
	private static final long DELAY_SECONDS = 10;
	private static final long SHORT_PAUSE_SECONDS = 5;
	private static final long LONG_PAUSE_SECONDS = (long)60 * 60;

	private static final long PAUSE_MODEL_BUILD_SECONDS = 1;

	private static CorrelationBuilder main = null;

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

	public CorrelationBuilder() 
	{
		super();
		main = this;
	}

	public static CorrelationBuilder main()
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

		DataStore store = Common.getDataStore();
		
		long watermark = 0;
	
		while (!stopped)
		{
			if (FingerprintCalculator.isBusy())
			{
				waitTask(SHORT_PAUSE_SECONDS);
				continue;
			}
		
			long modWatermark = store.model().getWatermarkCorr();
			
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
		
		AnnotationFP[] annotFP = store.annot().fetchAnnotationFP();
		long[] assayIDList = store.assay().fetchAssayIDCurated();
	
		logger.info("constructing with # annotation targets = {}, # assays = {}", annotFP.length, assayIDList.length);
		if (annotFP.length == 0 || assayIDList.length == 0) return;

		Map<String, Integer> annotToTarget = new HashMap<>();
		Map<Integer, AnnotationFP> targetToAnnot = new TreeMap<>();
		ModelUtilities.getTargetAnnotMaps(annotToTarget, targetToAnnot);
		
		Map<Long, AssayInformation> assayInfo = compileAssayInfo(assayIDList, annotToTarget);
		
		// iterate over each of the "annotation targets", make sure they all have a model
		for (Integer target : ModelBuilder.targetInPriorityOrder(targetToAnnot.keySet(), store.model().groupCorrByWatermarks()))
		{
			// early termination condition
			if (stopped) return;
			if (store.model().getWatermarkCorr() != watermark) return;

			if (store.model().getModelCorrWatermark(target) == watermark) continue;
			
			if (!targetToAnnot.containsKey(target))
			{
				logger.info("annotation#{}, not modelled", target);
				store.model().blankModelCorr(target, watermark);
				continue;
			}

			List<int[]> fplist = new ArrayList<>();
			List<Boolean> active = new ArrayList<>();

			AnnotationFP source = targetToAnnot.get(target);
			boolean isExplicit = prepareModel(assayInfo, assayIDList, source, fplist, active);
			Model model = buildModel(fplist.toArray(new int[fplist.size()][]), Util.primBoolean(active));
			if (model != null)
			{
				logger.info("annotation#{}, source {}", target, source);
				model.target = target;
				model.watermark = watermark;
				model.isExplicit = isExplicit;
				store.model().submitModelCorr(model);
			}
			else
			{
				logger.info("annotation#{}, not modelled", target);
				store.model().blankModelCorr(target, watermark);
			}
			if (ModelBuilder.main() != null && ModelBuilder.main().isPaused()) pauseTask(PAUSE_MODEL_BUILD_SECONDS);
		}
	}

	// builds a correlation model for a given "target" (an annotation fingerprint), if there is enough content to make it happen
	public static Model buildModel(int[][] fplist, boolean[] active)
	{
		NaiveBayesian.Model nbModel = NaiveBayesian.buildModel(fplist, active);
		
		// return null if it was not possible to build a model (no data or only (in)actives)
		if (nbModel == null) return null;
		
		Model dsModel = new Model();
		dsModel.fplist = nbModel.fplist;
		dsModel.contribs = nbModel.contribs;
		dsModel.calibLow = nbModel.calibLow;
		dsModel.calibHigh = nbModel.calibHigh;
		return dsModel;
	}

	// ------------ private methods ------------
	// performs setup necessary to feed simple inputs into a model building operation
	private boolean prepareModel(Map<Long, AssayInformation> assayInfo, long[] assayIDList, AnnotationFP targAnnot,
								 List<int[]> srcFPList, List<Boolean> srcActive)
	{
		int target = targAnnot.fp;
	
		// accumulate the unique fingerprints
		boolean isExplicit = false;
		for (long assayID : assayIDList)
		{
			AssayInformation fpInfo = assayInfo.get(assayID);
			int[] fingerprints = fpInfo.fingerprint(target);
			if (fingerprints.length == 0) continue;

			isExplicit = isExplicit || fpInfo.isExplicit(targAnnot.valueURI);
			
			srcFPList.add(fingerprints);
			srcActive.add(fpInfo.isActive(target));
		}
		
		return isExplicit;
	}
	
	protected Map<Long, AssayInformation> compileAssayInfo(long[] assayIDList, Map<String, Integer> annotToTarget)
	{
		DataStore store = Common.getDataStore();
		Map<String, SchemaTree> treeCache = new HashMap<>();		
		
		Map<Long, AssayInformation> result = new HashMap<>();
		for (long assayID : assayIDList)
		{
			AssayInformation assayInformation = new AssayInformation(assayID);
			assayInformation.compile(store, annotToTarget, treeCache);
			result.put(assayID, assayInformation);
		}
		return result;
	}
	
	protected static class AssayInformation
	{
		public long assayID;
		private int[] fullFingerprint;
		private Set<String> explicitValueURIs;
		boolean isExplicit;
		private Logger logger = LoggerFactory.getLogger(CorrelationBuilder.class);
		
		public AssayInformation(long assayID) 
		{
			this.assayID = assayID;
		}
		
		public int[] fingerprint(int target)
		{
			if (fullFingerprint == null) return new int[0];
			return Arrays.stream(fullFingerprint).filter(val -> val != target).toArray();
		}
		
		public boolean isActive(int target)
		{
			return Arrays.binarySearch(fullFingerprint, target) >= 0;
		}
		
		public boolean isExplicit(String valueURI)
		{
			return explicitValueURIs.contains(valueURI);
		}
		
		public void compile(DataStore store, Map<String, Integer> annotToTarget, Map<String, SchemaTree> treeCache)
		{
			Set<Integer> fingerprint = new HashSet<>();
			explicitValueURIs = new HashSet<>();
			isExplicit = false;
			DataObject.Assay assay = null;
			try {assay = store.assay().getAssay(assayID);}
			catch (Exception ex) {logger.warn("Cannot retrieve assay with assayID " + assayID + ".", ex);}

			if (assay == null || assay.annotations == null) return;
			Schema schema = Common.getSchema(assay.schemaURI);
			if (schema == null) return;
			
			for (Annotation a : assay.annotations)
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
					int idx = annotToTarget.getOrDefault(a.propURI + ModelUtilities.SEP + valueURI, -1);
					if (idx < 0) continue;
					fingerprint.add(idx);
					if (valueURI.equals(a.valueURI)) explicitValueURIs.add(valueURI);
				}
			}
			fullFingerprint = Util.primInt(fingerprint);
			Arrays.sort(this.fullFingerprint);
		}
	}
}

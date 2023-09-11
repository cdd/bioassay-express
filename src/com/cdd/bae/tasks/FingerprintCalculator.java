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
import com.cdd.bao.util.*;

import java.util.*;

import javax.servlet.*;

/*
	Background task: looking for text-without-fingerprints, and performing the necessary calculations.
*/

public class FingerprintCalculator extends BaseMonitor implements Runnable
{
	private static final long DELAY_SECONDS = 5;
	private static final long LONG_PAUSE_SECONDS = (long)60 * 60;
	private static FingerprintCalculator main = null;
	protected DataStore store = null;
	private boolean busy = false;

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

	public FingerprintCalculator()
	{
		super();
		main = this;
	}

	public static FingerprintCalculator main()
	{
		return main;
	}

	public static boolean isBusy()
	{
		return main.busy;
	}

	// run in a background thread; expected to respond promptly to flipping of the stopped flag
	public void run()
	{	
		// give it a moment: allow the server a chance to get settled in, or to be terminated
		waitTask(DELAY_SECONDS);
		if (stopped) return;

		// start the main loop

		store = Common.getDataStore();
	
		while (!stopped)
		{
			boolean workDone = doTask();
			if (workDone)
			{
				logger.info("found nothing, pausing...");
				// wait for a goodly long time; will get bumped if something interesting happens in
				// the meanwhile
				waitTask(LONG_PAUSE_SECONDS);
			}
		}
	}
		
	// Fetch assays  - returns true if nothing else to do
	protected boolean doTask()
	{
		busy = true;
		
		logger.info("polling...");

		int totalCount = store.assay().countAssays();
		long[] todo = store.assay().fetchAssayIDWithoutFP();
			
		logger.info("found {}", todo.length);
			
		int pos = 0;
		int numApplied = 0;
		Map<String, Integer> blockToFP = null;
		for (long assayID : todo)
		{
			if (stopped) break;
						
			DataObject.Assay assay = store.assay().getAssay(assayID);
			if (Util.isBlank(getText(assay)) || (assay.fplist != null && assay.fplist.length > 0)) continue;
				
			logger.info("calculating for {} / UID={} ... {} of {}  (#assays={})", assay.assayID, assay.uniqueID, ++pos, todo.length, totalCount);

			if (blockToFP == null) blockToFP = store.nlp().fetchFingerprints();
			if (recalculate(assay, blockToFP)) numApplied++;
		}
			
		busy = false;
		
		return numApplied == 0;
	}

	// ------------ private methods ------------


	private String getText(DataObject.Assay assay)
	{
		List<String> textBlocks = new ArrayList<>();
		for (DataObject.TextLabel textLabel : assay.getTextLabels(AssayUtil.URI_ASSAYTITLE, null))
			if (!Util.isBlank(textLabel.text)) textBlocks.add(textLabel.text);
		if (!Util.isBlank(assay.text)) textBlocks.add(assay.text);
		return String.join("\n", textBlocks);
	}

	protected boolean recalculate(DataObject.Assay assay, Map<String, Integer> blockToFP)
	{
		// special deal: text that has '####' anywhere is signifying a cutoff for machine learning purposes, i.e. only
		// content up until that point is valid
		String text = getText(assay);
		int i = text.indexOf("####");
		if (i >= 0) text = text.substring(0, i);
	
		String[] blocks = new NLPCalculator(text).calculate();
		
		// match or create all NLP blocks as fingerprint indices
		int[] fplist = new int[blocks.length];
		for (int n = 0; n < blocks.length; n++)
		{
			Integer fp = blockToFP.get(blocks[n]);
			if (fp == null)
			{
				fplist[n] = 0;
				for (int f : blockToFP.values()) fplist[n] = Math.max(fplist[n], f);
				fplist[n]++;
				
				store.nlp().addNLPFingerprint(blocks[n], fplist[n]);
				blockToFP.put(blocks[n], fplist[n]);
			}
			else fplist[n] = fp;
		}
		Arrays.sort(fplist);
		
		// associate with the aid & text
		store.assay().submitAssayFingerprints(assay.assayID, fplist);

		return true;
	}
}

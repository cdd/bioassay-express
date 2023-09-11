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

import java.util.*;
import java.util.Map.*;
import java.util.stream.*;

/*
	Common functionality for building Bayesian models: used for the natural language models and correlation models.
*/

public final class NaiveBayesian
{
	public static class Model
	{
		// model
		public int[] fplist;
		public float[] contribs;
		
		// performance & calibration
		public float rocAUC;
		public float calibLow = Float.NaN, calibHigh = Float.NaN;

		public Model(int[] fplist, float[] contribs)
		{
			this.fplist = fplist;
			this.contribs = contribs;
		}

		// predict a list of fingerprints
		public float[] predict(int[][] fps)
		{
			float[] estimate = new float[fps.length];
			for (int n = 0; n < fps.length; n++)
			{
				estimate[n] = 0;
				if (fps[n] != null) for (int f : fps[n])
				{
					int i = Arrays.binarySearch(fplist, f);
					if (i >= 0) estimate[n] += contribs[i];
				}
			};
			return estimate;
		}
	}

	public static class ModelLearner
	{
		// data
		public int[][] fplist;
		public boolean[] active;
		
		// derived properties
		public int numActive;
		public int numData;
		public Map<Integer, Integer> fpCount;
		public Map<Integer, Integer> fpActive;
		
		// predictions
		public double[] estimates;
		public Integer[] idx;

		public ModelLearner(int[][] fplist, boolean[] active)
		{
			this.fplist = fplist;
			this.active = active;
			numData = fplist.length;
			assert numData == active.length;
			numActive = 0;
			for (int n = 0; n < numData; n++) if (active[n]) numActive++;
		}

		void determineFPCounts()
		{
			fpCount = new HashMap<>();
			fpActive = new HashMap<>();
			for (int n = 0; n < numData; n++) if (fplist[n] != null)
			{
				for (int fp : fplist[n])
				{
					fpCount.put(fp, fpCount.getOrDefault(fp, 0) + 1);
					if (active[n]) fpActive.put(fp, fpActive.getOrDefault(fp, 0) + 1);
				}
			}
		}

		// return a sorted array of relevant fingerprints which occur between 1 and numData-1 times
		protected int[] getRelevantFP()
		{
			if (numData == 0) return new int[0];
			
			// make sure we have the required information
			if (fpCount == null) determineFPCounts();

			Stream<Entry<Integer, Integer>> stream;
			
			// filter of fpCount to fingerprints that have a count less than numData
			stream = fpCount.entrySet().stream().filter(e -> e.getValue() < numData);
			
			// return the keys of the remaining entries as a sorted array
			return stream.map(Entry::getKey).sorted().mapToInt(Integer::intValue).toArray();
		}

		// return the Laplace modified Bayesian model
		protected Model learnBayesianModel()
		{
			// get list of sorted relevant fingerprints
			int[] relFPList = getRelevantFP();

			// create the basic uncalibrated Bayesian model
			final double pAT = (float)numActive / numData;
			float[] contribs = new float[relFPList.length];
			for (int n = 0; n < relFPList.length; n++)
			{
				final int fp = relFPList[n];
				final int nA = fpActive.getOrDefault(fp, 0);
				final int nT = fpCount.get(fp);
				final double pCorr = (nA + 1) / (nT * pAT + 1);
				contribs[n] = (float)Math.log(pCorr);
			};
			return new Model(relFPList, contribs);
		}
	}

	public static class ROCCurve
	{
		public float[] rocX;
		public float[] rocY;
		public float[] rocT;
		public float rocAUC;

		public float calibLow;
		public float calibMid;
		public float calibHigh;

		public ROCCurve(float[] estimates, boolean[] active)
		{
			if (estimates.length != active.length) throw new IllegalArgumentException("estimates and active must have equal length");
			assert estimates.length == active.length;
			// sort estimates and active in ascending order of estimates
			Integer[] idx = IntStream.range(0, estimates.length).boxed()
					.sorted((v1, v2) -> (int) Math.signum(estimates[v1] - estimates[v2])).toArray(Integer[]::new);
			float[] sortedEstimates = new float[idx.length];
			for (int n = 0; n < idx.length; n++) sortedEstimates[n] = estimates[idx[n]];
			boolean[] sortedActive = new boolean[active.length];
			for (int i = 0; i < active.length; i++) sortedActive[i] = active[idx[i]];

			float[] thresholds = determineThresholds(sortedEstimates, true);
			determineROCCurve(thresholds, sortedEstimates, sortedActive);
			determineAUC();
			determineCalibration();
		}

		// determine the rocCurve at the thresholds values, estimates and active must be sorted
		private void determineROCCurve(float[] thresholds, float[] estimates, boolean[] active)
		{
			int numData = estimates.length;
			int numActive = 0;
			for (int n = 0; n < numData; n++) if (active[n]) numActive++;
			
			// x = false positives / actual negatives
			// y = true positives / actual positives
			int tsz = thresholds.length;
			rocX = new float[tsz];
			rocY = new float[tsz];
			rocT = new float[tsz];

			final double epsilon = 1e-5;
			int posTrue = 0;
			int posFalse = 0;
			float invPos = 1.0f / numActive;
			float invNeg = 1.0f / (numData - numActive);
			int rsz = 0;
			int ipos = 0;
			for (int n = 0; n < tsz; n++)
			{
				final float th = thresholds[n];
				for (; ipos < numData; ipos++)
				{
					if (th < estimates[ipos]) break;
					if (active[ipos])
						posTrue++;
					else
						posFalse++;
				}
				float x = posFalse * invNeg;
				float y = posTrue * invPos;
				if (rsz > 0 && Math.abs(x - rocX[rsz - 1]) < epsilon && Math.abs(y - rocY[rsz - 1]) < epsilon) continue;

				rocX[rsz] = 1 - x;
				rocY[rsz] = 1 - y;
				rocT[rsz] = th;
				rsz++;
			}
			rocX = reverse(Arrays.copyOf(rocX, rsz));
			rocY = reverse(Arrays.copyOf(rocY, rsz));
			rocT = reverse(Arrays.copyOf(rocT, rsz));
		}

		// calculate area-under-curve
		private void determineAUC()
		{
			rocAUC = 0;
			for (int n = 0; n < rocX.length - 1; n++)
			{
				double w = rocX[n + 1] - rocX[n];
				double h = 0.5 * (rocY[n] + rocY[n + 1]);
				rocAUC += w * h;
			}
		}

		// find suitable calibration
		private void determineCalibration()
		{
			// find point at which rocY-rocX has maximum; the threshold associated with this point
			// defines midThresh
			int mididx = 0;
			for (int n = 1; n < rocX.length; n++) if (rocY[n] - rocX[n] > rocY[mididx] - rocX[mididx]) mididx = n;
			float midThresh = rocT[mididx];
			
			// next determine the thresholds for which the ROC curves deviates from the ideal curve
			int idxX = 0;
			int idxY = rocX.length - 1;
			for (; idxX < mididx - 1; idxX++) if (rocX[idxX] > 0) break;
			for (; idxY > mididx + 1; idxY--) if (rocY[idxY] < 1) break;
			float delta = Math.min(rocT[idxX] - midThresh, midThresh - rocT[idxY]);
			calibMid = midThresh;
			calibLow = midThresh - delta;
			calibHigh = midThresh + delta;
		}
	}

	// ------------ public methods ------------

	// creates a Bayesian model from fingerprint:target content
	public static Model buildModel(int[][] fplist, boolean[] active)
	{
		ModelLearner data = new ModelLearner(fplist, active);

		// these cases all non models make
		if (data.numData == 0 || data.numActive == 0 || data.numActive == data.numData) return null;

		// calculate "estimates" using incoming fingerprints; these will be used to make the ROC, and from there the calibration
		// (note: normally do a training/testing split, which requires extra effort; since we only want to use it for boundary
		// purposes, it's OK to skip the rigor)
		Model model = data.learnBayesianModel();
		float[] estimates = model.predict(data.fplist);

		ROCCurve roc = new ROCCurve(estimates, active);
		model.rocAUC = roc.rocAUC;
		model.calibHigh = roc.calibHigh;
		model.calibLow = roc.calibLow;
		return model;
	}

	// ------------ private methods ------------

	private NaiveBayesian() { /* class used statically only */ }

	// convenience: reverses order of array
	protected static float[] reverse(float[] arr)
	{
		float[] ret = new float[arr.length];
		for (int i = 0, j = arr.length - 1; j >= 0; i++, j--) ret[j] = arr[i];
		return ret;
	}

	// determine thresholds (points halfway between unique values and two outside the range
	protected static float[] determineThresholds(float[] values, boolean sorted)
	{
		float[] sortedValues = values;
		if (!sorted)
		{
			sortedValues = Arrays.copyOf(values, values.length);
			Arrays.sort(sortedValues);
		}

		final double epsilon = 1e-6;
		int numData = sortedValues.length;
		float range = sortedValues[numData - 1] - sortedValues[0];

		float[] thresholds = new float[numData + 1];
		int tsz = 0;
		thresholds[tsz++] = sortedValues[0] - 0.01f * range;
		for (int n = 0; n < numData - 1; n++)
		{
			final float th1 = sortedValues[n];
			final float th2 = sortedValues[n + 1];
			if (th2 - th1 < epsilon) continue;
			thresholds[tsz++] = 0.5f * (th1 + th2);
		}
		thresholds[tsz++] = sortedValues[numData - 1] + 0.01f * range;
		return Arrays.copyOf(thresholds, tsz);
	}
}

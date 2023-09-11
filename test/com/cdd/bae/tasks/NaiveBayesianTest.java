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

import com.cdd.bae.tasks.NaiveBayesian.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.*;

import org.junit.jupiter.api.*;

/*
	Test for com.cdd.bae.tasks.NaiveBayesian
 */

public class NaiveBayesianTest
{
	@Test
	public void testBuildModel()
	{
		NaiveBayesian.Model model;

		int[][] fplist = new int[][]
		{
			new int[]{1, 2, 3, 5, 6}, 
			new int[]{1, 4}, 
			new int[]{1, 4}, 
			new int[]{1, 4, 5, 6},
			new int[]{1, 2, 4, 5, 6}
		};
		boolean[] active = new boolean[]{false, true, true, true, false};

		// get the cases out of the way where we have nothing to do
		assertNull(NaiveBayesian.buildModel(new int[][]{}, new boolean[]{}), "No fingerprints");
		assertNull(NaiveBayesian.buildModel(fplist, new boolean[]{false, false, false, false, false}), "No actives");
		assertNull(NaiveBayesian.buildModel(fplist, new boolean[]{true, true, true, true, true}), "All actives");

		model = NaiveBayesian.buildModel(fplist, active);
		assertTrue(Arrays.equals(model.fplist, new int[]{2, 3, 4, 5, 6}), "Feature 1 occurs everywhere and is ignored");
		assertEquals(model.contribs[0], Math.log(1 / 2.2), 0.0001);
		assertEquals(model.contribs[1], Math.log(1 / 1.6), 0.0001);
		assertEquals(model.contribs[2], Math.log(4 / 3.4), 0.0001);
		assertEquals(model.contribs[3], Math.log(2 / 2.8), 0.0001);
		assertEquals(model.contribs[4], Math.log(2 / 2.8), 0.0001);
		assertEquals(-1.6151441, model.calibLow, 0.0001);
		assertEquals(-0.1941643, model.calibHigh, 0.0001);
		assertEquals(1, model.rocAUC, 0.0001);
	}

	@Test
	public void testModelLearner()
	{
		NaiveBayesian.ModelLearner learner;
		int[][] fplist = new int[][]{new int[]{1, 2, 6, 5, 7}, new int[]{1, 7, 2, 3, 5}, new int[]{1, 7, 2, 3, 4}};
		boolean[] active = new boolean[]{false, true, true};
		learner = new NaiveBayesian.ModelLearner(fplist, active);

		assertTrue(Arrays.equals(learner.getRelevantFP(), new int[]{3, 4, 5, 6}));
		assertTrue(Arrays.equals(learner.getRelevantFP(), new int[]{3, 4, 5, 6}), "can call it a second time");
		// System.out.println(Arrays.toString(data.getReleveantFP()));

		NaiveBayesian.Model model = learner.learnBayesianModel();
		assertTrue(Arrays.equals(new int[]{3, 4, 5, 6}, model.fplist));
		assertEquals(model.contribs[0], 0.25131442, 0.0001);
		assertEquals(model.contribs[1], 0.18232155, 0.0001);
		assertEquals(model.contribs[2], -0.1541506, 0.0001);
		assertEquals(model.contribs[3], -0.5108256, 0.0001);

		float[] estimate = model.predict(fplist);
		assertEquals(estimate[0], -0.66497630, 0.0001);
		assertEquals(estimate[1], 0.097163748, 0.0001);
		assertEquals(estimate[2], 0.433635985, 0.0001);
	}

	@Test
	public void testModel()
	{
		NaiveBayesian.ModelLearner learner;
		int[][] fplist = new int[][]{new int[]{1, 2, 6, 5, 7}, new int[]{1, 7, 2, 3, 5}, new int[]{1, 7, 2, 3, 4}};
		int[][] fplist2 = Stream.concat(Arrays.stream(fplist), Arrays.stream(fplist)).toArray(int[][]::new);
		boolean[] active = new boolean[]{false, true, true};
		learner = new NaiveBayesian.ModelLearner(fplist, active);
		NaiveBayesian.Model model = learner.learnBayesianModel();

		float[] estimates = model.predict(fplist2);
		double[] expectedDouble = new double[]{-0.6759624264799299, -0.2839062775698004, 0.2653998667642544, 0.44462210796154206};
		float[] expected = new float[expectedDouble.length];
		for (int n = 0; n < expected.length; n++) expected[n] = (float)expectedDouble[n];
		
		assertFloatArray(expected, NaiveBayesian.determineThresholds(model.predict(fplist), false));
		assertFloatArray(expected, NaiveBayesian.determineThresholds(estimates, false));
		Arrays.sort(estimates);
		assertFloatArray(expected, NaiveBayesian.determineThresholds(estimates, true));
	}

	private void assertFloatArray(float[] expected, float[] thresholds)
	{
		assertEquals(expected.length, thresholds.length);
		for (int i = 0; i < expected.length; i++)
		{
			assertEquals(expected[i], thresholds[i], 0.0001);
		}
	}

	@Test
	public void testModelLearnerEdgeCases()
	{
		NaiveBayesian.ModelLearner learner;
		int[][] fplist;
		boolean[] active = new boolean[]{false, true, true};

		learner = new NaiveBayesian.ModelLearner(new int[0][], new boolean[0]);
		assertTrue(Arrays.equals(learner.getRelevantFP(), new int[0]));

		// Provide a fplist where the outcome should be an empty list
		fplist = new int[][]{new int[]{1, 2}, new int[]{1, 2}, new int[]{1, 2}};
		learner = new NaiveBayesian.ModelLearner(fplist, active);
		assertEquals(0, learner.getRelevantFP().length);

		// Provide a fplist where the outcome are all fingerprints
		fplist = new int[][]{new int[]{1}, new int[]{2}, new int[]{3}};
		learner = new NaiveBayesian.ModelLearner(fplist, active);
		assertTrue(Arrays.equals(learner.getRelevantFP(), new int[]{1, 2, 3}));
	}

	@Test
	public void testReverse()
	{
		float[] arr = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
		float[] expected = new float[]{4.0f, 3.0f, 2.0f, 1.0f};
		assertTrue(Arrays.equals(NaiveBayesian.reverse(arr), expected));
		arr = new float[]{};
		expected = new float[]{};
		assertTrue(Arrays.equals(NaiveBayesian.reverse(arr), expected));
	}

	@Test
	public void testROCCurve()
	{
		boolean[] active = new boolean[]{false, false, false, false, false, false, false, true, true, true, true, true, true};
		float[] estimates = new float[]{0.0f, 0.1f, 0.6f, 0.6f, 0.7f, 0.8f, 0.9f, 0.6f, 0.7f, 0.8f, 0.9f, 0.9f, 1.0f};
		ROCCurve roc = new ROCCurve(estimates, active);

		float[] expected = new float[]{1.01f, 0.95f, 0.85f, 0.75f, 0.65f, 0.35f, 0.05f, -0.01f};
		assertFloatArray(expected, roc.rocT);
		expected = new float[]{0, 0, 0.1428571f, 0.285714f, 0.428571f, 0.714285f, 0.857142f, 1.0f};
		assertFloatArray(expected, roc.rocX);
		expected = new float[]{0, 0.166666f, 0.5f, 0.666666f, 0.833333f, 1.0f, 1.0f, 1.0f};
		assertFloatArray(expected, roc.rocY);
		assertEquals(0.78571426, roc.rocAUC, 0.00001);
		// calibration
		assertEquals(0.65, roc.calibMid, 0.00001);
		assertEquals(0.85, roc.calibHigh, 0.00001);
		assertEquals(0.45, roc.calibLow, 0.00001);

		// Perfect classification
		active = new boolean[]{false, false, false, false, false, false, true, true, true, true, true};
		estimates = new float[]{0, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f};
		roc = new ROCCurve(estimates, active);

		expected = new float[]{1.01f, 0.95f, 0.85f, 0.75f, 0.65f, 0.55f, 0.45f, 0.35f, 0.25f, 0.15f, 0.05f, -0.01f};
		assertFloatArray(expected, roc.rocT);
		expected = new float[]{0, 0, 0, 0, 0, 0, 0.166666f, 0.333333f, 0.5f, 0.66666f, 0.833333f, 1.0f};
		assertFloatArray(expected, roc.rocX);
		expected = new float[]{0, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
		assertFloatArray(expected, roc.rocY);
		assertEquals(1.0, roc.rocAUC, 0.00001);
		// calibration
		assertEquals(0.55, roc.calibMid, 0.00001);
		assertEquals(0.65, roc.calibHigh, 0.00001);
		assertEquals(0.45, roc.calibLow, 0.00001);

		Assertions.assertThrows(IllegalArgumentException.class, 
				() -> new ROCCurve(new float[5], new boolean[3]));
		boolean exceptionRaised = false;
		try
		{
			active = new boolean[3];
			estimates = new float[5];
			new ROCCurve(estimates, active);
		}
		catch (IllegalArgumentException e)
		{
			exceptionRaised = true;
		}
		assertTrue(exceptionRaised, "Exception raised");
	}
}

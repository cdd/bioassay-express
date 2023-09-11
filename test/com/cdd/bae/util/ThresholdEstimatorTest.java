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

package com.cdd.bae.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

public class ThresholdEstimatorTest
{
	@Test
	public void testThresholdEstimator()
	{
		selfValidateSet(new double[] {0, 2, 1}, new boolean[] {false, true, false}, true, 2, ThresholdEstimator.OPERATOR_GREQUAL);
		selfValidateSet(new double[] {0, 1, 1.99}, new boolean[] {false, false, true}, true, 1, ThresholdEstimator.OPERATOR_GREATER);
		selfValidateSet(new double[] {0.4, 0.5}, new boolean[] {false, true}, true, 0.45, ThresholdEstimator.OPERATOR_GREATER);
		selfValidateSet(new double[] {0.14, 0.15}, new boolean[] {false, true}, true, 0.145, ThresholdEstimator.OPERATOR_GREATER);
		selfValidateSet(new double[] {0.114, 0.115}, new boolean[] {false, true}, true, 0.1145, ThresholdEstimator.OPERATOR_GREATER);
		selfValidateSet(new double[] {0.114, 0.115}, new boolean[] {true, false}, true, 0.1145, ThresholdEstimator.OPERATOR_LESSTHAN);
		selfValidateSet(new double[] {0, 1}, new boolean[] {true, true}, false, 0, null);
		selfValidateSet(new double[] {0, 1}, new boolean[] {false, false}, false, 0, null);
		selfValidateSet(new double[] {0, 0}, new boolean[] {false, true}, false, 0, null);
		selfValidateSet(new double[] {1, 2, 3, 4, 5}, new boolean[] {false, false, true, false, true}, true, 3, ThresholdEstimator.OPERATOR_GREQUAL);
		selfValidateSet(new double[] {1, 2, 3, 4, 5}, new boolean[] {false, true, false, false, true}, true, 5, ThresholdEstimator.OPERATOR_GREQUAL);
		selfValidateSet(new double[] {1, 2, 3, 4, 5}, new boolean[] {true, true, false, true, false}, true, 3, ThresholdEstimator.OPERATOR_LESSTHAN);
	}

	public static void selfValidateSet(double[] values, boolean[] actives, boolean shouldWork, double resultThreshold,
			String resultOperator)
	{
		ThresholdEstimator est = new ThresholdEstimator();
		est.addValues(values, actives);

		assertEquals(shouldWork, est.calculate(), "Calculation expected to work");
		if (!shouldWork) return;

		assertEquals(resultThreshold, est.getThreshold(), 0.01, "Calculated threshold");
		assertEquals(resultOperator, est.getOperator());
	}

	@Test
	public void testFormatComparator()
	{
		assertEquals("=", ThresholdEstimator.formatComparator(ThresholdEstimator.OPERATOR_EQUAL));
		assertEquals(">", ThresholdEstimator.formatComparator(ThresholdEstimator.OPERATOR_GREATER));
		assertEquals(">=", ThresholdEstimator.formatComparator(ThresholdEstimator.OPERATOR_GREQUAL));
		assertEquals("<", ThresholdEstimator.formatComparator(ThresholdEstimator.OPERATOR_LESSTHAN));
		assertEquals("<=", ThresholdEstimator.formatComparator(ThresholdEstimator.OPERATOR_LTEQUAL));
		assertEquals("?", ThresholdEstimator.formatComparator("Unknown"));
	}
}

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

package com.cdd.bae.rest;

import com.cdd.bae.data.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

/*
	Test for Predict REST API.
*/

public class PredictTest
{
	@Test
	public void testOrderPredictions()
	{
		ModelPredict.Prediction[] predictions = new ModelPredict.Prediction[10];
		for (int i = 0; i < predictions.length; i++)
		{
			predictions[i] = new ModelPredict.Prediction();
			predictions[i].combined = i;
			predictions[i].propURI = "propURI-" + i;
		}
		ModelPredict.Prediction[] sorted = Suggest.orderPredictions(predictions);
		assertEquals(predictions.length, sorted.length);
		assertEquals(9, sorted[0].combined, 0.1);
		assertEquals(8, sorted[1].combined, 0.1);
		assertEquals(7, sorted[2].combined, 0.1);
		assertEquals(6, sorted[3].combined, 0.1);
		assertEquals(0, sorted[9].combined, 0.1);
	
		for (int i = 0; i < predictions.length; i++)
			predictions[i].propURI = "propURI-" + (i / 3);
		
		sorted = Suggest.orderPredictions(predictions);
		assertEquals(predictions.length, sorted.length);
		assertEquals(9, sorted[0].combined, 0.1, "propURI-3");
		assertEquals(8, sorted[1].combined, 0.1, "propURI-2");
		assertEquals(5, sorted[2].combined, 0.1, "propURI-1");
		assertEquals(2, sorted[3].combined, 0.1, "propURI-0");
		assertEquals(7, sorted[4].combined, 0.1, "propURI-2");
		assertEquals(6, sorted[5].combined, 0.1, "propURI-2");
		assertEquals(4, sorted[6].combined, 0.1, "propURI-1");
		assertEquals(3, sorted[7].combined, 0.1, "propURI-1");
		assertEquals(1, sorted[8].combined, 0.1, "propURI-0");
		assertEquals(0, sorted[9].combined, 0.1, "propURI-0");
	}

	// ------------ mockito-based tests ------------

	@Test
	public void testSameGroup()
	{
		assertTrue(Suggest.sameGroup(new String[] {"a", "b"}, new String[] {"a", "b"}));
		assertTrue(Suggest.sameGroup(new String[0], new String[0]));

		assertFalse(Suggest.sameGroup(new String[] {"a"}, new String[] {"a", "b"}));
		assertFalse(Suggest.sameGroup(new String[] {"a", "c"}, new String[] {"a", "b"}));
		assertFalse(Suggest.sameGroup(new String[] {"a", "b"}, new String[] {"a"}));
		
		assertTrue(Suggest.sameGroup(null, new String[0]));
		assertTrue(Suggest.sameGroup(null, null));
	}

	// ------------ faux-mongo tests ------------

	// TODO

	// ------------ private methods ------------

}

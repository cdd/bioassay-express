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

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

/*
	Tests for Counters
*/

public class CountersTest
{

	private static final String KEY0 = "key0";
	private static final String KEY1 = "key1";
	private static final String KEY2 = "key2";

	@Test
	public void testCounters()
	{
		Counters<String> counters = new Counters<>();

		// simple counter
		assertThat(counters.get(KEY1), is(0));

		counters.increment(KEY1);
		assertThat(counters.get(KEY1), is(1));

		counters.increment(KEY1);
		assertThat(counters.get(KEY1), is(2));
		assertThat(counters.getObjects(KEY1), hasSize(0));

		// store objects with the counter (stored as set)
		assertThat(counters.get(KEY2), is(0));
		assertThat(counters.getObjects(KEY2), hasSize(0));

		counters.increment(KEY2, "obj1");
		assertThat(counters.get(KEY2), is(1));
		assertThat(counters.getObjects(KEY2), hasSize(1));

		counters.increment(KEY2, "obj2");
		assertThat(counters.get(KEY2), is(2));
		assertThat(counters.getObjects(KEY2), hasSize(2));

		counters.increment(KEY2, "obj2");
		assertThat(counters.get(KEY2), is(3));
		assertThat(counters.getObjects(KEY2), hasSize(2));

		counters.increment(KEY2, "obj3");
		counters.increment(KEY2, "obj4");
		counters.increment(KEY2, "obj5");
		counters.increment(KEY2, "obj6");

		// counter with no associated objects
		counters.increment(KEY0);

		String s = counters.toString();
		assertTrue(s.contains(KEY0 + " : 1"));
		assertTrue(s.contains(KEY1 + " : 2"));
		assertTrue(s.contains(KEY2 + " : 7\n  obj1, obj2, obj3, obj4, obj5\n  obj6"));
	}
}

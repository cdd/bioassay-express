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

package com.cdd.testutil;

import com.cdd.bae.data.*;

import static org.mockito.Mockito.*;

import java.util.*;

/*
	Mock the data store to test REST functionality.
*/

public class MockDataStore
{
	private MockDataStore()
	{
	}

	public static DataStore mockedDataStore()
	{
		DataStore store = mock(DataStore.class);
		mockDataAssay(store);
		return store;
	}

	protected static void mockDataAssay(DataStore store)
	{
		DataAssay dataAssay = mock(DataAssay.class);
		when(dataAssay.breakdownProperties()).thenReturn(breakdownProperties());
		when(dataAssay.breakdownAssignments()).thenReturn(breakdownAssignments());
		when(store.assay()).thenReturn(dataAssay);
	}

	private static Map<String, Integer> breakdownProperties()
	{
		Map<String, Integer> result = new HashMap<>();
		result.put("url1", 90);
		result.put("url2", 190);
		return result;
	}

	private static Map<String, Map<String, Integer>> breakdownAssignments()
	{
		Map<String, Map<String, Integer>> result = new HashMap<>();
		result.put("url1", new HashMap<>());
		result.put("url2", new HashMap<>());
		result.get("url1").put("bao1", 50);
		result.get("url1").put("bao2", 40);
		result.get("url2").put("bao1", 200);
		return result;
	}
}

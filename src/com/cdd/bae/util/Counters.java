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

import com.cdd.bao.util.*;

import java.util.*;
import java.util.stream.*;

/*
	Generic counter class - see tests for usage
*/

public class Counters<T>
{
	private Map<String, Integer> counts = new TreeMap<>();
	private Map<String, Set<T>> objects = new TreeMap<>();

	public void increment(String key)
	{
		counts.put(key, get(key) + 1);
	}

	public void increment(String key, T object)
	{
		getObjects(key).add(object);
		counts.put(key, get(key) + 1);
	}

	public int get(String key)
	{
		return counts.getOrDefault(key, 0);
	}

	public Set<T> getObjects(String key)
	{
		return objects.computeIfAbsent(key, k -> new HashSet<>());
	}

	@Override
	public String toString()
	{
		return toString("", false);
	}

	public String toString(String indent, boolean byValue)
	{
		StringJoiner sj = new StringJoiner("\n" + indent, indent, "");
		Collection<String> keys = counts.keySet();
		if (byValue)
			keys = keys.stream().sorted((a1, a2) -> counts.get(a1).compareTo(counts.get(a2))).collect(Collectors.toList());
		for (String key : keys)
		{
			sj.add(key + " : " + counts.get(key));
			if (objects.containsKey(key))
			{
				List<String> obj = objects.get(key).stream().sorted().map(T::toString).collect(Collectors.toList());
				for (int i = 0; i < obj.size(); i += 5)
				{
					sj.add("  " + obj.subList(i, Math.min(i + 5, obj.size())).stream().collect(Collectors.joining(", ")));
				}
			}
		}
		return sj.toString();
	}

	public void report()
	{
		Util.writeln("Counts\n" + toString("  ", false));
		Util.writeln();
	}
}

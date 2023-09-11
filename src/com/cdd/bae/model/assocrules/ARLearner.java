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

package com.cdd.bae.model.assocrules;

import java.util.*;
import java.util.Map.*;
import java.util.stream.*;

/*
	Learn association rules
*/

public class ARLearner
{
	private static final String SEP = "::";

	public static class ItemSet
	{
		public String[] items;
		public int support;

		public ItemSet(String item)
		{
			items = new String[]{item};
			support = 0;
		}

		public ItemSet(String[] items, int support)
		{
			this.items = items;
			this.support = support;
		}

		public String getKey()
		{
			return String.join(SEP, items);
		}

		@Override
		public String toString()
		{
			return String.format("[%s (%d)]", getKey(), support);
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;
			ItemSet other = (ItemSet)o;
			return Arrays.equals(this.items, other.items);
		}

		@Override
		public int hashCode()
		{
			return Arrays.hashCode(this.items);
		}
	}

	public static class Rule
	{
		public ItemSet rhs;
		public ItemSet lhs;
		public int support;
		public double confidence;
		public double lift;

		public Rule(ItemSet lhs, ItemSet rhs, int support, double confidence, double lift)
		{
			this.rhs = rhs;
			this.lhs = lhs;
			this.support = support;
			this.confidence = confidence;
			this.lift = lift;
		}

		@Override
		public String toString()
		{
			return String.format("%s => %s (%d, %.2f, %.2f)", lhs.getKey(), rhs.getKey(), support, confidence, lift);
		}
	}

	protected int minSupport;
	protected int maxSize;
	protected double confidence;

	public ARLearner(int minSupport, int maxSize, double minConfidence)
	{
		this.minSupport = minSupport;
		this.maxSize = maxSize;
		this.confidence = minConfidence;
	}

	public ARLearner(int minSupport, int maxSize)
	{
		this(minSupport, maxSize, 0.6);
	}

	public List<ItemSet> generateItemSets(List<Set<String>> transactions)
	{
		// identify the single item sets with minimum support 
		List<ItemSet> result = initialItemSets(transactions);
		Set<String> frequentItems = result.stream()
				.map(is -> is.items[0])
				.collect(Collectors.toSet());

		// convert the transactions into a tree structure
		FPTree fpTree = new FPTree();
		for (Set<String> trans : transactions)
		{
			Set<String> tl = new HashSet<>(trans);
			tl.retainAll(frequentItems);
			if (tl.isEmpty()) continue;
			String[] t = tl.toArray(new String[0]);
			Arrays.sort(t);
			fpTree.add(t);
		}

		// grow current sets  
		List<ItemSet> currentSet = result;
		for (int i = 2; i <= maxSize; i++)
		{
			List<ItemSet> nextSet = nextItemSets(currentSet, fpTree);
			if (nextSet.isEmpty()) break;
			currentSet = nextSet;
			result.addAll(nextSet);
		}
		return result;
	}

	public List<Rule> generateRules(List<ItemSet> itemSets)
	{
		Map<String, ItemSet> toItemSet = itemSets.stream().collect(Collectors.toMap(ItemSet::getKey, e -> e));
		List<Rule> rules = new ArrayList<>();
		for (ItemSet itemSet : itemSets)
		{
			if (itemSet.items.length == 1) continue;
			double support = itemSet.support;
			for (String rhs : itemSet.items)
			{
				ItemSet lhs = toItemSet.get(itemSet.getKey().replace(rhs + SEP, "").replace(SEP + rhs, ""));
				double conf = support / lhs.support;
				if (conf < this.confidence) continue;
				double lift = conf / toItemSet.get(rhs).support;
				rules.add(new Rule(lhs, toItemSet.get(rhs), itemSet.support, conf, lift));
			}
		}
		return rules;
	}

	protected List<ItemSet> initialItemSets(List<Set<String>> transactions)
	{
		Map<String, ItemSet> itemSets = new HashMap<>();
		for (Set<String> transaction : transactions)
			for (String item : transaction)
				itemSets.computeIfAbsent(item, ItemSet::new).support += 1;
		return itemSets.values().stream()
				.filter(itemSet -> itemSet.support >= minSupport)
				.collect(Collectors.toList());
	}

	protected List<ItemSet> nextItemSets(List<ItemSet> currentSets, FPTree fpTree)
	{
		List<ItemSet> nextSet = new ArrayList<>();
		for (ItemSet current : currentSets)
		{
			String[] prefix = current.items;
			for (Entry<String, Integer> e : fpTree.getChildrenSupport(prefix).entrySet())
			{
				if (e.getValue() < minSupport) continue;
				nextSet.add(new ItemSet(addString(prefix, e.getKey()), e.getValue()));
			}
		}
		return nextSet;
	}

	protected static String[] addString(String[] arr, String s)
	{
		String[] tempArr = new String[arr.length + 1];
		System.arraycopy(arr, 0, tempArr, 0, arr.length);
		tempArr[arr.length] = s;
		return tempArr;

	}
}

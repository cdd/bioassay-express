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

/*
	FPTree Data structure to support identification of frequent itemsets
*/

public class FPTree
{
	protected TreeNode tree;
	protected Map<String, List<TreeNode>> header;

	public FPTree()
	{
		header = new HashMap<>();
		tree = new TreeNode(header);
	}

	public void add(String[] prefix)
	{
		tree.add(null, prefix);
	}

	// lookup the count of keys in order
	public int lookup(String[] prefix)
	{
		return tree.lookup(prefix);
	}

	public int getCount(String[] prefix)
	{
		int count = 0;
		for (TreeNode node : findTerminus(prefix)) count += node.count;
		return count;
	}

	// return children connected to the terminal nodes identified by prefix
	public Set<String> getChildren(String[] prefix)
	{
		Set<String> result = new HashSet<>();
		for (TreeNode node : findTerminus(prefix))
			if (node.children != null) result.addAll(node.getAllChildren(new HashSet<>()));
		return result;
	}

	public Map<String, Integer> getChildrenSupport(String[] prefix)
	{
		Map<String, Integer> result = new HashMap<>();
		for (TreeNode node : findTerminus(prefix))
		{
			if (node.children == null) continue;
			Map<String, Integer> aggregate = new HashMap<>();
			for (TreeNode child : node.children.values()) 
				child.aggregateChildren(aggregate);
			for (Entry<String, Integer> e : aggregate.entrySet())
				result.put(e.getKey(), result.getOrDefault(e.getKey(), 0) + e.getValue());
		}
		return result;
	}

	protected List<TreeNode> findTerminus(String[] prefix)
	{
		List<TreeNode> result = new ArrayList<>();
		for (TreeNode subTree : header.get(prefix[0]))
			subTree.findTerminus(prefix, result);
		return result;
	}

	public Set<String> getKeys()
	{
		return tree.getKeys(new HashSet<>());
	}

	@Override
	public String toString()
	{
		return tree.toString();
	}

	protected static class TreeNode
	{
		String key;
		int count = 0;
		Map<String, TreeNode> children;
		Map<String, List<TreeNode>> header;

		public TreeNode(Map<String, List<TreeNode>> header)
		{
			this.header = header;
		}

		public Map<String, Integer> aggregateChildren(Map<String, Integer> counts)
		{
			counts.put(key, counts.getOrDefault(key, 0) + count);
			if (children == null) return counts;
			for (TreeNode node : children.values())
				node.aggregateChildren(counts);
			return counts;
		}

		public Set<String> getAllChildren(Set<String> result)
		{
			if (children == null) return result;
			result.addAll(children.keySet());
			for (TreeNode node : children.values())
				node.getAllChildren(result);
			return result;
		}

		public void add(String key, String[] prefix)
		{
			count += 1;
			if (this.key == null)
			{
				this.key = key;
				header.computeIfAbsent(key, k -> new ArrayList<>()).add(this);
			}
			if (prefix.length > 0)
			{
				if (children == null) children = new HashMap<>();
				TreeNode subTree = children.computeIfAbsent(prefix[0], k -> new TreeNode(header));
				subTree.add(prefix[0], Arrays.copyOfRange(prefix, 1, prefix.length));
			}
		}

		public int lookup(String[] prefix)
		{
			if (prefix.length == 0 || children == null || !children.containsKey(prefix[0])) return count;
			return children.get(prefix[0]).lookup(Arrays.copyOfRange(prefix, 1, prefix.length));
		}

		// find list of last nodes identified by prefix 
		public void findTerminus(String[] prefix, List<TreeNode> result)
		{
			if (prefix.length == 1 && prefix[0].equals(key))
			{
				result.add(this);
				return;
			}
			if (children == null) return;
			for (Entry<String, TreeNode> e : children.entrySet())
			{
				int compare = prefix[1].compareTo(e.getKey());
				if (compare == 0)
					e.getValue().findTerminus(Arrays.copyOfRange(prefix, 1, prefix.length), result);
				else if (compare > 0)
					e.getValue().findTerminus(prefix, result);
			}
		}

		public Set<String> getKeys(Set<String> result)
		{
			if (children == null) return result;
			result.addAll(children.keySet());
			for (TreeNode child : children.values())
				child.getKeys(result);
			return result;
		}

		public String toString()
		{
			StringBuilder sb = new StringBuilder();
			treeToString("  ", sb);
			headerToString(sb);
			return sb.toString();
		}

		private void treeToString(String indent, StringBuilder sb)
		{
			sb.append(String.format("%s%s [%d]%n", indent, key, count));
			if (children == null) return;
			for (Entry<String, TreeNode> e : children.entrySet())
			{
				e.getValue().treeToString(indent + "  ", sb);
			}
		}

		private void headerToString(StringBuilder sb)
		{
			sb.append("\n");
			for (Entry<String, List<TreeNode>> e : header.entrySet())
				sb.append(String.format("%s : %d subtrees%n", e.getKey(), e.getValue().size()));
		}
	}
}

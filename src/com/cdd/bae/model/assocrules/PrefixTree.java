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
import java.util.function.*;

/*
	Predict annotations based on existing annotations
*/

public class PrefixTree<K, V>
{
	protected TreeNode<K, V> tree;

	public PrefixTree()
	{
		tree = new TreeNode<>();
	}

	public void add(K[] prefix, V value)
	{
		tree.add(null, prefix, value);
	}

	// lookup an element that has to match all keys in order
	public Set<V> lookup(K[] prefix)
	{
		return tree.lookup(prefix);
	}

	// find all values that are associated with keys (order independent)
	public Set<V> findAll(Set<K> keys)
	{
		return tree.findAll(keys, new HashSet<>());
	}

	public Set<K> getKeys()
	{
		return tree.getKeys(new HashSet<>());
	}

	public Set<V> getValues()
	{
		return tree.getValues(new HashSet<>());
	}
	
	public void visit(BiConsumer<Deque<K>, V> callback)
	{
		tree.visit(callback);
	}

	@Override
	public String toString()
	{
		return tree.toString();
	}

	protected static class TreeNode<K, V>
	{
		K key;
		List<V> values;
		TreeNode<K, V> parent;
		Map<K, TreeNode<K, V>> children;

		public void add(K key, K[] prefix, V value)
		{
			if (this.key == null) this.key = key;
			if (prefix.length == 0)
			{
				if (values == null) values = new ArrayList<>();
				values.add(value);
			}
			else
			{
				if (children == null) children = new HashMap<>();
				TreeNode<K, V> subTree = children.computeIfAbsent(prefix[0], k -> new TreeNode<>());
				subTree.add(prefix[0], Arrays.copyOfRange(prefix, 1, prefix.length), value);
			}
		}

		public Set<V> lookup(K[] prefix)
		{
			if (prefix.length == 0)
				return values == null ? new HashSet<>() : new HashSet<>(values);
			else if (children == null || !children.containsKey(prefix[0])) return new HashSet<>();
			return children.get(prefix[0]).lookup(Arrays.copyOfRange(prefix, 1, prefix.length));
		}

		public Set<V> findAll(Set<K> keys, Set<V> result)
		{
			if (children != null && !keys.isEmpty())
				for (K k : keys)
					if (children.containsKey(k)) children.get(k).findAll(keys, result);
			if (values != null) result.addAll(values);
			return result;
		}

		public Set<K> getKeys(Set<K> result)
		{
			if (children == null) return result;
			result.addAll(children.keySet());
			for (TreeNode<K, V> child : children.values())
				child.getKeys(result);
			return result;
		}

		public Set<V> getValues(Set<V> result)
		{
			if (values != null) result.addAll(values);
			if (children == null) return result;
			for (TreeNode<K, V> child : children.values())
				child.getValues(result);
			return result;
		}
		
		public void visit(BiConsumer<Deque<K>, V> callback)
		{
			visit(callback, new ArrayDeque<>());
		}
		
		private void visit(BiConsumer<Deque<K>, V> callback, Deque<K> path)
		{
			if (values != null) for (V value : values) callback.accept(path, value);
			if (children == null) return;
			for (Entry<K, TreeNode<K, V>> child : children.entrySet())
			{
				path.addLast(child.getKey());
				child.getValue().visit(callback, path);
				path.removeLast();
			}
		}

		public String toString()
		{
			return toString("  ", new StringBuilder()).toString();
		}

		private StringBuilder toString(String indent, StringBuilder sb)
		{
			sb.append(String.format("%s%s %s", indent, key, values == null || values.isEmpty() ? "" : values));
			sb.append("\n");
			if (children == null) return sb;
			for (Entry<K, TreeNode<K, V>> e : children.entrySet())
			{
				e.getValue().toString(indent + "  ", sb);
			}
			return sb;
		}
	}
}

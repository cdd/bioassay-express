/*
 * AhoCorasickDoubleArrayTrie Project
 *      https://github.com/hankcs/AhoCorasickDoubleArrayTrie
 *
 * Copyright 2008-2016 hankcs <me@hankcs.com>
 * You may modify and redistribute as long as this attribution remains.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cdd.bae.model.hankcs;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * An implementation of Aho Corasick algorithm based on Double Array Trie
 *
 * @author hankcs
 */
public class AhoCorasickDoubleArrayTrie<V> implements Serializable
{
	/**
	 * check array of the Double Array Trie structure
	 */
	protected int[] check;
	/**
	 * base array of the Double Array Trie structure
	 */
	protected int[] base;
	/**
	 * fail table of the Aho Corasick automata
	 */
	protected int[] fail;
	/**
	 * output table of the Aho Corasick automata
	 */
	protected int[][] output;
	/**
	 * outer value array
	 */
	protected V[] v;

	/**
	 * the length of every key
	 */
	protected int[] l;

	/**
	 * the size of base and check array
	 */
	protected int size;

	/**
	 * Parse text
	 * @param text The text
	 * @return a list of outputs
	 */
	public List<Hit<V>> parseText(String text)
	{
		int position = 1;
		int currentState = 0;
		List<Hit<V>> collectedEmits = new LinkedList<>();
		for (int i = 0; i < text.length(); ++i)
		{
			currentState = getState(currentState, text.charAt(i));
			storeEmits(position, currentState, collectedEmits);
			++position;
		}

		return collectedEmits;
	}

	/**
	 * Parse text
	 * @param text The text
	 * @param processor A processor which handles the output
	 */
	public void parseText(String text, IHit<V> processor)
	{
		int position = 1;
		int currentState = 0;
		for (int i = 0; i < text.length(); ++i)
		{
			currentState = getState(currentState, text.charAt(i));
			int[] hitArray = output[currentState];
			if (hitArray != null)
			{
				for (int hit : hitArray)
				{
					processor.hit(position - l[hit], position, v[hit]);
				}
			}
			++position;
		}
	}

	/**
	 * Parse text
	 * @param text The text
	 * @param processor A processor which handles the output
	 */
	public void parseText(String text, IHitCancellable<V> processor)
	{
		int currentState = 0;
		for (int i = 0; i < text.length(); i++)
		{
			final int position = i + 1;
			currentState = getState(currentState, text.charAt(i));
			int[] hitArray = output[currentState];
			if (hitArray != null)
			{
				for (int hit : hitArray)
				{
					boolean proceed = processor.hit(position - l[hit], position, v[hit]);
					if (!proceed)
					{
						return;
					}
				}
			}
		}
	}

	/**
	 * Parse text
	 * @param text The text
	 * @param processor A processor which handles the output
	 */
	public void parseText(char[] text, IHit<V> processor)
	{
		int position = 1;
		int currentState = 0;
		for (char c : text)
		{
			currentState = getState(currentState, c);
			int[] hitArray = output[currentState];
			if (hitArray != null)
			{
				for (int hit : hitArray)
				{
					processor.hit(position - l[hit], position, v[hit]);
				}
			}
			++position;
		}
	}

	/**
	 * Parse text
	 * @param text The text
	 * @param processor A processor which handles the output
	 */
	public void parseText(char[] text, IHitFull<V> processor)
	{
		int position = 1;
		int currentState = 0;
		for (char c : text)
		{
			currentState = getState(currentState, c);
			int[] hitArray = output[currentState];
			if (hitArray != null)
			{
				for (int hit : hitArray)
				{
					processor.hit(position - l[hit], position, v[hit], hit);
				}
			}
			++position;
		}
	}

	/**
	 * Save
	 * @param out An ObjectOutputStream object
	 * @throws IOException Some IOException
	 */
	public void save(ObjectOutputStream out) throws IOException
	{
		out.writeObject(base);
		out.writeObject(check);
		out.writeObject(fail);
		out.writeObject(output);
		out.writeObject(l);
		out.writeObject(v);
	}

	/**
	 * Load
	 * @param in An ObjectInputStream object
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public void load(ObjectInputStream in) throws IOException, ClassNotFoundException
	{
		base = (int[])in.readObject();
		check = (int[])in.readObject();
		fail = (int[])in.readObject();
		output = (int[][])in.readObject();
		l = (int[])in.readObject();
		v = (V[])in.readObject();
	}

	/**
	 * Get value by a String key, just like a map.get() method
	 * @param key The key
	 * @return
	 */
	public V get(String key)
	{
		int index = exactMatchSearch(key);
		if (index >= 0)
		{
			return v[index];
		}

		return null;
	}

	/**
	 * Pick the value by index in value array <br>
	 * Notice that to be more efficiently, this method DONOT check the parameter
	 * @param index The index
	 * @return The value
	 */
	public V get(int index)
	{
		return v[index];
	}

	/**
	 * Processor handles the output when hit a keyword
	 */
	public interface IHit<V>
	{
		/**
		 * Hit a keyword, you can use some code like text.substring(begin, end) to get the keyword
		 * @param begin the beginning index, inclusive.
		 * @param end   the ending index, exclusive.
		 * @param value the value assigned to the keyword
		 */
		void hit(int begin, int end, V value);
	}

	/**
	 * Processor handles the output when hit a keyword, with more detail
	 */
	public interface IHitFull<V>
	{
		/**
		 * Hit a keyword, you can use some code like text.substring(begin, end) to get the keyword
		 * @param begin the beginning index, inclusive.
		 * @param end   the ending index, exclusive.
		 * @param value the value assigned to the keyword
		 * @param index the index of the value assigned to the keyword, you can use the integer as a perfect hash value
		 */
		void hit(int begin, int end, V value, int index);
	}

	/**
	 * Callback that allows to cancel the search process.
	 */
	public interface IHitCancellable<V>
	{
		/**
		 * Hit a keyword, you can use some code like text.substring(begin, end) to get the keyword
		 * @param begin the beginning index, inclusive.
		 * @param end   the ending index, exclusive.
		 * @param value the value assigned to the keyword
		 * @return Return true for continuing the search and false for stopping it.
		 */
		boolean hit(int begin, int end, V value);
	}

	/**
	 * A result output
	 *
	 * @param <V> the value type
	 */
	@SuppressWarnings("hiding")
	public class Hit<V>
	{
		/**
		 * the beginning index, inclusive.
		 */
		public int begin;
		/**
		 * the ending index, exclusive.
		 */
		public int end;
		/**
		 * the value assigned to the keyword
		 */
		public final V value;

		public Hit(int begin, int end, V value)
		{
			this.begin = begin;
			this.end = end;
			this.value = value;
		}

		@Override
		public String toString()
		{
			return String.format("[%d:%d]=%s", begin, end, value);
		}
	}

	/**
	 * transmit state, supports failure function
	 *
	 * @param currentState
	 * @param character
	 * @return
	 */
	protected int getState(int currentState, char character)
	{
		int newCurrentState = transitionWithRoot(currentState, character); // 先按success跳转
		while (newCurrentState == -1) // 跳转失败的话，按failure跳转
		{
			currentState = fail[currentState];
			newCurrentState = transitionWithRoot(currentState, character);
		}
		return newCurrentState;
	}

	/**
	 * store output
	 *
	 * @param position
	 * @param currentState
	 * @param collectedEmits
	 */
	protected void storeEmits(int position, int currentState, List<Hit<V>> collectedEmits)
	{
		int[] hitArray = output[currentState];
		if (hitArray != null)
		{
			for (int hit : hitArray)
			{
				collectedEmits.add(new Hit<V>(position - l[hit], position, v[hit]));
			}
		}
	}

	/**
	 * transition of a state
	 *
	 * @param current
	 * @param c
	 * @return
	 */
	protected int transition(int current, char c)
	{
		int b = current;
		int p;

		p = b + c + 1;
		if (b == check[p])
			b = base[p];
		else
			return -1;

		p = b;
		return p;
	}

	/**
	 * transition of a state, if the state is root and it failed, then returns the root
	 *
	 * @param nodePos
	 * @param c
	 * @return
	 */
	protected int transitionWithRoot(int nodePos, char c)
	{
		int b = base[nodePos];
		int p;

		p = b + c + 1;
		if (b != check[p])
		{
			if (nodePos == 0) return 0;
			return -1;
		}

		return p;
	}

	/**
	 * Build a AhoCorasickDoubleArrayTrie from a map
	 * @param map a map containing key-value pairs
	 */
	public void build(Map<String, V> map)
	{
		new Builder().build(map);
	}

	/**
	 * match exactly by a key
	 *
	 * @param key the key
	 * @return the index of the key, you can use it as a perfect hash function
	 */
	public int exactMatchSearch(String key)
	{
		return exactMatchSearch(key, 0, 0, 0);
	}

	/**
	 * match exactly by a key
	 *
	 * @param key
	 * @param pos
	 * @param len
	 * @param nodePos
	 * @return
	 */
	private int exactMatchSearch(String key, int pos, int len, int nodePos)
	{
		if (len <= 0)
			len = key.length();
		if (nodePos <= 0)
			nodePos = 0;

		int result = -1;

		char[] keyChars = key.toCharArray();

		int b = base[nodePos];
		int p;

		for (int i = pos; i < len; i++)
		{
			p = b + (int)(keyChars[i]) + 1;
			if (b == check[p])
				b = base[p];
			else
				return result;
		}

		p = b;
		int n = base[p];
		if (b == check[p] && n < 0)
		{
			result = -n - 1;
		}
		return result;
	}

	/**
	 * match exactly by a key
	 *
	 * @param keyChars the char array of the key
	 * @param pos      the begin index of char array
	 * @param len      the length of the key
	 * @param nodePos  the starting position of the node for searching
	 * @return the value index of the key, minus indicates null
	 */
	private int exactMatchSearch(char[] keyChars, int pos, int len, int nodePos)
	{
		int result = -1;

		int b = base[nodePos];
		int p;

		for (int i = pos; i < len; i++)
		{
			p = b + (int)(keyChars[i]) + 1;
			if (b == check[p])
				b = base[p];
			else
				return result;
		}

		p = b;
		int n = base[p];
		if (b == check[p] && n < 0)
		{
			result = -n - 1;
		}
		return result;
	}


	/**
	 * Get the size of the keywords
	 * @return
	 */
	public int size()
	{
		return v.length;
	}

	/**
	 * A builder to build the AhoCorasickDoubleArrayTrie
	 */
	private class Builder
	{
		/**
		 * the root state of trie
		 */
		private State rootState = new State();
		/**
		 * whether the position has been used
		 */
		private boolean[] used;
		/**
		 * the allocSize of the dynamic array
		 */
		private int allocSize;
		/**
		 * a parameter controls the memory growth speed of the dynamic array
		 */
		private int progress;
		/**
		 * the next position to check unused memory
		 */
		private int nextCheckPos;
		/**
		 * the size of the key-pair sets
		 */
		private int keySize;

		/**
		 * Build from a map
		 * @param map a map containing key-value pairs
		 */
		public void build(Map<String, V> map)
		{
			// 把值保存下来
			v = (V[])map.values().toArray();
			l = new int[v.length];
			Set<String> keySet = map.keySet();
			// 构建二分trie树
			addAllKeyword(keySet);
			// 在二分trie树的基础上构建双数组trie树
			buildDoubleArrayTrie(keySet.size());
			used = null;
			// 构建failure表并且合并output表
			constructFailureStates();
			rootState = null;
			loseWeight();
		}

		/**
		 * fetch siblings of a parent node
		 *
		 * @param parent   parent node
		 * @param siblings parent node's child nodes, i . e . the siblings
		 * @return the amount of the siblings
		 */
		private int fetch(State parent, List<Map.Entry<Integer, State>> siblings)
		{
			if (parent.isAcceptable())
			{
				State fakeNode = new State(-(parent.getDepth() + 1)); // 此节点是parent的子节点，同时具备parent的输出
				fakeNode.addEmit(parent.getLargestValueId());
				siblings.add(new AbstractMap.SimpleEntry<Integer, State>(0, fakeNode));
			}
			for (Map.Entry<Character, State> entry : parent.getSuccess().entrySet())
			{
				siblings.add(new AbstractMap.SimpleEntry<Integer, State>(entry.getKey() + 1, entry.getValue()));
			}
			return siblings.size();
		}

		/**
		 * add a keyword
		 *
		 * @param keyword a keyword
		 * @param index   the index of the keyword
		 */
		private void addKeyword(String keyword, int index)
		{
			State currentState = this.rootState;
			for (Character character : keyword.toCharArray())
			{
				currentState = currentState.addState(character);
			}
			currentState.addEmit(index);
			l[index] = keyword.length();
		}

		/**
		 * add a collection of keywords
		 *
		 * @param keywordSet the collection holding keywords
		 */
		private void addAllKeyword(Collection<String> keywordSet)
		{
			int i = 0;
			for (String keyword : keywordSet)
			{
				addKeyword(keyword, i++);
			}
		}

		/**
		 * construct failure table
		 */
		private void constructFailureStates()
		{
			fail = new int[size + 1];
			fail[1] = base[0];
			output = new int[size + 1][];
			Queue<State> queue = new LinkedBlockingDeque<>();

			// 第一步，将深度为1的节点的failure设为根节点
			for (State depthOneState : this.rootState.getStates())
			{
				depthOneState.setFailure(this.rootState, fail);
				queue.add(depthOneState);
				constructOutput(depthOneState);
			}

			// 第二步，为深度 > 1 的节点建立failure表，这是一个bfs
			while (!queue.isEmpty())
			{
				State currentState = queue.remove();

				for (Character transition : currentState.getTransitions())
				{
					State targetState = currentState.nextState(transition);
					queue.add(targetState);

					State traceFailureState = currentState.failure();
					while (traceFailureState.nextState(transition) == null)
					{
						traceFailureState = traceFailureState.failure();
					}
					State newFailureState = traceFailureState.nextState(transition);
					targetState.setFailure(newFailureState, fail);
					targetState.addEmit(newFailureState.emit());
					constructOutput(targetState);
				}
			}
		}

		/**
		 * construct output table
		 */
		private void constructOutput(State targetState)
		{
			Collection<Integer> emit = targetState.emit();
			if (emit == null || emit.isEmpty()) return;
			int[] outputTable = new int[emit.size()];
			Iterator<Integer> it = emit.iterator();
			for (int i = 0; i < outputTable.length; ++i)
			{
				outputTable[i] = it.next();
			}
			AhoCorasickDoubleArrayTrie.this.output[targetState.getIndex()] = outputTable;
		}

		private void buildDoubleArrayTrie(int keySize)
		{
			progress = 0;
			this.keySize = keySize;
			resize(65536 * 32); // 32个双字节

			base[0] = 1;
			nextCheckPos = 0;

			State rootnode = this.rootState;

			List<Map.Entry<Integer, State>> siblings = new ArrayList<>(rootnode.getSuccess().entrySet().size());
			fetch(rootnode, siblings);
			insert(siblings);
		}

		/**
		 * allocate the memory of the dynamic array
		 *
		 * @param newSize
		 * @return
		 */
		private int resize(int newSize)
		{
			int[] base2 = new int[newSize];
			int[] check2 = new int[newSize];
			boolean[] used2 = new boolean[newSize];
			if (allocSize > 0)
			{
				System.arraycopy(base, 0, base2, 0, allocSize);
				System.arraycopy(check, 0, check2, 0, allocSize);
				System.arraycopy(used, 0, used2, 0, allocSize);
			}

			base = base2;
			check = check2;
			used = used2;
			allocSize = newSize;
			return allocSize;
		}

		/**
		 * insert the siblings to double array trie
		 *
		 * @param siblings the siblings being inserted
		 * @return the position to insert them
		 */
		private int insert(List<Map.Entry<Integer, State>> siblings)
		{
			int begin = 0;
			int pos = Math.max(siblings.get(0).getKey() + 1, nextCheckPos) - 1;
			int nonzeroNum = 0;
			int first = 0;

			if (allocSize <= pos)
				resize(pos + 1);

			outer:
			// 此循环体的目标是找出满足base[begin + a1...an]  == 0的n个空闲空间,a1...an是siblings中的n个节点
			while (true)
			{
				pos++;

				if (allocSize <= pos)
					resize(pos + 1);

				if (check[pos] != 0)
				{
					nonzeroNum++;
					continue;
				}
				else if (first == 0)
				{
					nextCheckPos = pos;
					first = 1;
				}

				begin = pos - siblings.get(0).getKey(); // 当前位置离第一个兄弟节点的距离
				if (allocSize <= (begin + siblings.get(siblings.size() - 1).getKey()))
				{
					// progress can be zero // 防止progress产生除零错误
					double l = (1.05 > 1.0 * keySize / (progress + 1)) ? 1.05 : 1.0 * keySize / (progress + 1);
					resize((int)(allocSize * l));
				}

				if (used[begin])
					continue;

				for (int i = 1; i < siblings.size(); i++)
					if (check[begin + siblings.get(i).getKey()] != 0)
						continue outer;

				break;
			}

			// -- Simple heuristics --
			// if the percentage of non-empty contents in check between the
			// index
			// 'next_check_pos' and 'check' is greater than some constant value
			// (e.g. 0.9),
			// new 'next_check_pos' index is written by 'check'.
			if (1.0 * nonzeroNum / (pos - nextCheckPos + 1) >= 0.95)
				nextCheckPos = pos; // 从位置 next_check_pos 开始到 pos 间，如果已占用的空间在95%以上，下次插入节点时，直接从 pos 位置处开始查找
			used[begin] = true;

			size = (size > begin + siblings.get(siblings.size() - 1).getKey() + 1) ? size : begin + siblings.get(siblings.size() - 1).getKey() + 1;

			for (Map.Entry<Integer, State> sibling : siblings)
			{
				check[begin + sibling.getKey()] = begin;
			}

			for (Map.Entry<Integer, State> sibling : siblings)
			{
				List<Map.Entry<Integer, State>> newSiblings = new ArrayList<>(sibling.getValue().getSuccess().entrySet().size() + 1);

				if (fetch(sibling.getValue(), newSiblings) == 0) // 一个词的终止且不为其他词的前缀，其实就是叶子节点
				{
					base[begin + sibling.getKey()] = -sibling.getValue().getLargestValueId() - 1;
					progress++;
				}
				else
				{
					int h = insert(newSiblings); // dfs
					base[begin + sibling.getKey()] = h;
				}
				sibling.getValue().setIndex(begin + sibling.getKey());
			}
			return begin;
		}

		/**
		 * free the unnecessary memory
		 */
		private void loseWeight()
		{
			int[] nbase = new int[size + 65535];
			System.arraycopy(base, 0, nbase, 0, size);
			base = nbase;

			int[] ncheck = new int[size + 65535];
			System.arraycopy(check, 0, ncheck, 0, size);
			check = ncheck;
		}
	}
}

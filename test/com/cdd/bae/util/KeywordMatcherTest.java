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

import com.cdd.bae.util.KeywordMatcher.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.json.*;
import org.junit.jupiter.api.*;
import org.slf4j.*;

public class KeywordMatcherTest
{
	private static final Logger logger = LoggerFactory.getLogger(KeywordMatcherTest.class);

	public TestResourceFile keywordTestsJson = new TestResourceFile("/testData/util/keywordMatcherTests.json");

	@Test
	public void testKeywordMatcher() throws JSONException, IOException
	{
		for (TestCase testCase : getTestCases())
		{
			String msg = "Pattern " + testCase.pattern + ": ";
			KeywordMatcher km = new KeywordMatcher(testCase.pattern);

			if (testCase.nblocks > 0)
			{
				assertEquals(testCase.nblocks, km.getBlocks().length);
			} 
			else
			{
				logger.info("{} Number of blocks found is {}", msg, km.getBlocks().length);
			}

			try
			{
				km.prepare();
			} 
			catch (KeywordMatcher.FormatException ex)
			{
				assertTrue(false, msg + "preparation of keyword matcher failed");
			}

			for (String s : testCase.positive)
			{
				assertTrue(km.matches(s), msg + "failed to match " + s);
			}
			for (String s : testCase.negative)
			{
				assertFalse(km.matches(s), msg + "matched " + s);
			}
		}
	}

	@Test
	public void testPrepare()
	{
		Block[] blocks;

		KeywordMatcher km = new KeywordMatcher("");
		assertTrue(km.isBlank());
		assertEquals(0, km.getBlocks().length);
		assertNull(km.getClauses());
		
		km = new KeywordMatcher("A");
		assertFalse(km.isBlank());
		assertEquals(1, km.getBlocks().length);
		assertNull(km.getClauses());
	
		km = new KeywordMatcher("> 5");
		assertFalse(km.isBlank());
		assertEquals(2, km.getBlocks().length);
		assertNull(km.getClauses());
	
		km = new KeywordMatcher("A & B");
		assertFalse(km.isBlank());
		assertEquals(3, km.getBlocks().length);
		assertNull(km.getClauses());
	
		blocks = stringToBlocks("A");
		assertEquals(KeywordMatcher.Type.TEXT, blocks[0].type);
		assertEquals("A", blocks[0].value);
		blocks = stringToBlocks("'A'");
		assertEquals(KeywordMatcher.Type.TEXT, blocks[0].type);
		assertEquals("A", blocks[0].value);
		blocks = stringToBlocks("\"A\"");
		assertEquals(KeywordMatcher.Type.TEXT, blocks[0].type);
		assertEquals("A", blocks[0].value);
		blocks = stringToBlocks("\"A B\"");
		assertEquals(KeywordMatcher.Type.TEXT, blocks[0].type);
		assertEquals("A B", blocks[0].value);

		blocks = stringToBlocks("123");
		assertEquals(KeywordMatcher.Type.NUMBER, blocks[0].type);
		assertEquals(123, blocks[0].numeric, 0.001);
		blocks = stringToBlocks("123.45");
		assertEquals(KeywordMatcher.Type.NUMBER, blocks[0].type);
		assertEquals(123.45, blocks[0].numeric, 0.001);
		blocks = stringToBlocks("-1e2");
		assertEquals(KeywordMatcher.Type.NUMBER, blocks[0].type);
		assertEquals(-100, blocks[0].numeric, 0.001);

		blocks = stringToBlocks("1-5");
		assertEquals(KeywordMatcher.Type.RANGE, blocks[1].type);
		blocks = stringToBlocks("1 - 5");
		assertEquals(KeywordMatcher.Type.RANGE, blocks[1].type);
		blocks = stringToBlocks("1..5");
		assertEquals(KeywordMatcher.Type.RANGE, blocks[1].type);
		blocks = stringToBlocks("1 .. 5");
		assertEquals(KeywordMatcher.Type.RANGE, blocks[1].type);

		blocks = stringToBlocks("A AND B");
		assertEquals(KeywordMatcher.Type.AND, blocks[1].type);
		blocks = stringToBlocks("A & B");
		assertEquals(KeywordMatcher.Type.AND, blocks[1].type);
		blocks = stringToBlocks("A && B");
		assertEquals(KeywordMatcher.Type.AND, blocks[1].type);
		blocks = stringToBlocks("A OR B");
		assertEquals(KeywordMatcher.Type.OR, blocks[1].type);
		blocks = stringToBlocks("A | B");
		assertEquals(KeywordMatcher.Type.OR, blocks[1].type);
		blocks = stringToBlocks("A || B");
		assertEquals(KeywordMatcher.Type.OR, blocks[1].type);

		blocks = stringToBlocks("> 10");
		assertEquals(KeywordMatcher.Type.GREATER, blocks[0].type);
		blocks = stringToBlocks(">10");
		assertEquals(KeywordMatcher.Type.GREATER, blocks[0].type);
		blocks = stringToBlocks(">= 10");
		assertEquals(KeywordMatcher.Type.GREQUAL, blocks[0].type);
		blocks = stringToBlocks(">=10");
		assertEquals(KeywordMatcher.Type.GREQUAL, blocks[0].type);
		blocks = stringToBlocks("< 10");
		assertEquals(KeywordMatcher.Type.LESSTHAN, blocks[0].type);
		blocks = stringToBlocks("<10");
		assertEquals(KeywordMatcher.Type.LESSTHAN, blocks[0].type);
		blocks = stringToBlocks("<= 10");
		assertEquals(KeywordMatcher.Type.LTEQUAL, blocks[0].type);
		blocks = stringToBlocks("<=10");
		assertEquals(KeywordMatcher.Type.LTEQUAL, blocks[0].type);

		// REGEX
		// blocks = stringToBlocks("/[a-z]\\s+/");

		// separator for a delta range (e.g. from [1 +/- 0.5]")
		// blocks = stringToBlocks("1 +/- 0.5");
		// assertEquals(KeywordMatcher.Type.DELTA, blocks[1].type);

		// logical NOT (!)
		// blocks = stringToBlocks("NOT A");
		// assertEquals(KeywordMatcher.Type.NOT, blocks[0].type);
		// blocks = stringToBlocks("! A");
		// assertEquals(KeywordMatcher.Type.NOT, blocks[0].type);

		// OPEN, // open bracket (,[
		// CLOSE //
		// blocks = stringToBlocks("( A AND B )");

		// for (Block block : blocks)
		// System.out.println(" " + block.type + ": [" + block.value + "]");

		assertTrue(true);
	}

	@Test
	public void testFormatExceptions()
	{
		testPattern1(null, "Expect KeywordMatcher initialisation to fail for null selector");
		testPattern2("1E", "Invalid numeric");
		testPattern2("/ABC*/", "Regex not implemented");
	}

	public void testPattern1(String pattern, String msg)
	{
		try
		{
			stringToBlocks(pattern);
			assertTrue(false, msg);
		} 
		catch (NullPointerException ex)
		{
			// pass
		}
	}

	public void testPattern2(String pattern, String msg)
	{
		try
		{
			stringToBlocks(pattern);
			assertTrue(false, msg);
		} 
		catch (FormatException ex)
		{
			// pass
		}
	}

	@Test
	public void testClauses()
	{
		KeywordMatcher km = new KeywordMatcher("A AND B AND >10");
		km.prepare();
		Clause[] clauses = km.getClauses();
		assertEquals(3, clauses.length);
		assertEquals(Type.NONE, clauses[0].compare);
		assertEquals(Type.AND, clauses[1].compare);
		assertEquals(Type.NONE, clauses[2].compare);
		// Once this code is implemented, require adding tests for getClauses 
	}

	class TestCase
	{
		String pattern;
		int nblocks; // Number of blocks found in pattern
		String[] positive;
		String[] negative;
	}

	List<TestCase> getTestCases() throws JSONException, IOException
	{
		JSONArray json = new JSONArray(keywordTestsJson.getContent());
		List<TestCase> testCases = new ArrayList<>();
		for (int n = 0; n < json.length(); n++)
		{
			JSONObject obj = json.getJSONObject(n);
			TestCase testCase = new TestCase();
			testCase.pattern = obj.getString("pattern");
			testCase.nblocks = obj.optInt("nblocks", -1);
			testCase.positive = obj.getJSONArray("positive").toStringArray();
			testCase.negative = obj.getJSONArray("negative").toStringArray();
			testCases.add(testCase);
		}
		return testCases;
	}

	public Block[] stringToBlocks(String s)
	{
		KeywordMatcher km;
		km = new KeywordMatcher(s);
		km.prepare();
		return km.getBlocks();
	}
}

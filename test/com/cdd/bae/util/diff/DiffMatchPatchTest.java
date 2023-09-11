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

package com.cdd.bae.util.diff;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.*;

public class DiffMatchPatchTest
{
	private static final String SINGLE_BEFORE = "pubchemAID:123";
	private static final String SINGLE_AFTER = "pubchemAID:456";
	private static final String SINGLE_PATCH = "@@ -8,7 +8,7 @@\n AID:\n-123\n+456\n";

	private static final String MULTI_BEFORE = String.join("\n", new String[]{"In", "the", "ning", "nang", "nong", "where", "the", "cows", "go", "bong."});
	private static final String MULTI_AFTER = String.join("\n", new String[]{"On", "the", "nong", "ning", "nang", "where", "the", "mice", "go", "clang."});
	private static final String MULTI_PATCH = String.join("\n", new String[]
	{
		"@@ -1,23 +1,23 @@", "-I", "+O", " n%0Athe%0An", "-i", "+o", " ng%0An", "-a", "+i", " ng%0An", "-o", "+a", 
		" ng%0Aw", "@@ -29,17 +29,18 @@", " the%0A", "-cows", "+mice", " %0Ago%0A", "-bo", "+cla", " ng.", ""
	});

	// ------------ private methods ------------
	
	@Test
	public void testSingleLine()
	{
		LinkedList<DiffMatchPatch.Patch> patches = DiffMatchPatch.patchMake(SINGLE_BEFORE, SINGLE_AFTER);
		String ptext = DiffMatchPatch.patchToText(patches);
		assertEquals(ptext, SINGLE_PATCH);
	}

	@Test
	public void testMultiLine()
	{
		LinkedList<DiffMatchPatch.Patch> patches = DiffMatchPatch.patchMake(MULTI_BEFORE, MULTI_AFTER);
		
		//Util.writeln("#PATCHES:"+patches.size());
		//for (int n = 0; n < patches.size(); n++) Util.writeln("(" + (n + 1) + "): " + patches.get(n));
		
		String ptext = DiffMatchPatch.patchToText(patches);
		
		//Util.writeln("PTEXT:\n["+ptext+"]");
		
		assertEquals(ptext, MULTI_PATCH);
		
		patches = DiffMatchPatch.patchFromText(ptext);
		String papplied = DiffMatchPatch.patchApplyText(patches, MULTI_BEFORE);
		
		//Util.writeln("APPL:\n"+papplied);
	}

	@Test
	public void testSameThing()
	{
		LinkedList<DiffMatchPatch.Patch> patches = DiffMatchPatch.patchMake(SINGLE_BEFORE, SINGLE_BEFORE);
		String ptext = DiffMatchPatch.patchToText(patches);
		assertEquals(ptext, "");
	}
}

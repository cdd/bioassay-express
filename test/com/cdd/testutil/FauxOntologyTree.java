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
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.net.*;
import java.util.*;

import org.json.*;

/*
	Creates a demo ontology tree from a text file.
*/

public class FauxOntologyTree extends OntologyTree
{
	public TestResourceFile treesource = new TestResourceFile("/testData/data/ontotree.txt");

	// ------------ public methods ------------

	public FauxOntologyTree() throws IOException
	{
		super();
		
		String[] lines = treesource.getContent().split("\n");
		
		var sequence = new String[0];
		OntologyTree.Branch lastBranch = null;
		int lastDepth = 0;
		
		for (String line : lines) if (Util.notBlank(line) && !line.startsWith("#"))
		{
			int depth = 0;
			while (line.startsWith("*"))
			{
				depth++;
				line = line.substring(1).trim();
			}
			int spc = line.indexOf(" ");
			if (spc < 0) throw new IOException("Invalid line: " + line);
			String uri = ModelSchema.expandPrefix(line.substring(0, spc));
			String label = line.substring(spc + 1);
			
			if (lastBranch != null) for (int d = lastDepth - depth; d >= 0; d--) lastBranch = lastBranch.parent;
			var branch = new OntologyTree.Branch(lastBranch, uri, label);
			if (lastBranch == null)
				roots.add(branch);
			else
				lastBranch.children.add(branch);
			
			uriToBranch.put(branch.uri, branch);
			for (var look = branch.parent; look != null; look = look.parent) look.descendents++;

			lastBranch = branch;
			lastDepth = depth;
		}
		
		//for (var root : roots) dumpBranch(root, 0);
	}

	// ------------ private methods ------------
	
	private void dumpBranch(Branch branch, int depth)
	{
		Util.writeln("* ".repeat(depth) + "<" + branch.uri + "> (" + branch.descendents + ") " + branch.label);
		for (var child : branch.children) dumpBranch(child, depth + 1);
	}
}



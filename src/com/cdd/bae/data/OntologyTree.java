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

package com.cdd.bae.data;

import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

/*
	Distilled representation of the ontology basis, which is compiled from a collection of input ontologies.
*/

public class OntologyTree
{
	public static final class Branch
	{
		public Branch parent = null;
		public String uri, label; // note: URI is stored in expanded form in memory, collapsed form when serialised
		public List<Branch> children = new ArrayList<>();
		public int descendents = 0;
		public boolean isProvisional = false;
		
		public Branch(Branch parent, String uri, String label)
		{
			this.parent = parent;
			this.uri = uri;
			this.label = label;
		}
		
		private Branch()
		{
		}
	}
	
	protected List<Branch> roots = new ArrayList<>();
	protected Map<String, Branch> uriToBranch = new HashMap<>();
	protected Map<String, String> uriToDescr = new HashMap<>();
	protected Map<String, String[]> uriToAltLabels = new HashMap<>();
	protected Map<String, String[]> uriToExternalURLs = new HashMap<>();
	
	// for serialisation
	private static final int MAGIC_NUMBER = 0xDEADBEEF; // has to start with this number, else is not correct
	private static final int CURRENT_VERSION = 1; // serialisation version, required to match	

    // ------------ public methods ------------

	public OntologyTree(Vocabulary vocab, Vocabulary.Hierarchy hier)
	{
		List<Vocabulary.Branch> pool = new ArrayList<>(hier.uriToBranch.values());
		while (true)
		{
			boolean anything = false;
			for (var it = pool.iterator(); it.hasNext();)
			{
				var vbranch = it.next();
				Branch branch = null;
				if (vbranch.parents.isEmpty())
				{
					branch = new Branch(null, vbranch.uri, vbranch.label);
					roots.add(branch);
				}
				else
				{
					var parent = uriToBranch.get(vbranch.parents.get(0).uri);
					if (parent == null) continue;
					branch = new Branch(parent, vbranch.uri, vbranch.label);
					parent.children.add(branch);
				}
				uriToBranch.put(branch.uri, branch);
				it.remove();
				anything = true;
			}
			if (!anything) break;
		}
		
		for (String uri : uriToBranch.keySet())
		{
			String descr = vocab.getDescr(uri);
			if (descr != null) uriToDescr.put(uri, descr);
			String[] altLabels = vocab.getAltLabels(uri), externalURLs = vocab.getExternalURLs(uri);
			if (altLabels != null) uriToAltLabels.put(uri, altLabels);
			if (externalURLs != null) uriToExternalURLs.put(uri, externalURLs);
		}
		
		for (var branch : roots) countDescendents(branch);
	}
	
	protected OntologyTree()
	{
	}
	
	// access to content
	public int countURI() {return uriToBranch.size();}
	public int countDescr() {return uriToDescr.size();}
	public int countAltLabels() {return uriToAltLabels.size();}
	public int countExternalURLs() {return uriToExternalURLs.size();}
	public Branch[] getRoots() {return roots.toArray(new Branch[roots.size()]);}
	public Branch getBranch(String uri) {return uriToBranch.get(uri);}
	public String getLabel(String uri)
	{
		var branch = uriToBranch.get(uri);
		return branch == null ? null : branch.label;
	}
	public String getDescr(String uri) {return uriToDescr.get(uri);}
	public String[] getAltLabels(String uri) {return uriToAltLabels.get(uri);}
	public String[] getExternalURLs(String uri) {return uriToExternalURLs.get(uri);}
	
	// writes everything to an outputstream using a concise binary format
	public void serialise(OutputStream ostr) throws IOException
	{	
		var data = new DataOutputStream(ostr);
		
		data.writeInt(MAGIC_NUMBER);
		data.writeInt(CURRENT_VERSION);
		
		// write out the hierarchy, using indices to denote parents
		Map<String, Integer> uriIndex = new HashMap<>();
		List<Branch> queue = new ArrayList<>(roots);
		while (!queue.isEmpty())
		{
			var branch = queue.remove(0);
			int pidx = branch.parent == null ? -1 : uriIndex.get(branch.parent.uri);
			
			data.writeInt(pidx);
			data.writeUTF(ModelSchema.collapsePrefix(branch.uri));
			data.writeUTF(branch.label);
			
			uriIndex.put(branch.uri, uriIndex.size());
			
			for (int n = 0; n < branch.children.size(); n++) queue.add(n, branch.children.get(n));
		}
		data.writeInt(-2);
		
		// write descriptions
		data.writeInt(uriToDescr.size());
		for (var entry : uriToDescr.entrySet())
		{
			data.writeUTF(entry.getKey());
			data.writeUTF(entry.getValue());
		}
		
		// write alternate labels
		data.writeInt(uriToAltLabels.size());
		for (var entry : uriToAltLabels.entrySet())
		{
			data.writeUTF(entry.getKey());
			data.writeInt(entry.getValue().length);
			for (var str : entry.getValue()) data.writeUTF(str);
		}
		
		// write alternate URLs
		data.writeInt(uriToExternalURLs.size());
		for (var entry : uriToExternalURLs.entrySet())
		{
			data.writeUTF(entry.getKey());
			data.writeInt(entry.getValue().length);
			for (var str : entry.getValue()) data.writeUTF(str);
		}
		
		data.flush();
	}
	
	// unpacks an ontology tree from an inputstream, throwing an exception if anything is wrong (intolerant)
	public static OntologyTree deserialise(InputStream istr) throws IOException
	{
		DataInputStream data = new DataInputStream(istr);
	
		int magic = data.readInt();
		if (magic != MAGIC_NUMBER) throw new IOException("Not a vocabulary file.");
		
		int version = data.readInt();
		if (version != CURRENT_VERSION) throw new IOException("Vocabulary file is the wrong version.");

		var onto = new OntologyTree();
		
		List<Branch> branchList = new ArrayList<>();
		while (true)
		{
			int pidx = data.readInt();
			if (pidx == -2) break;
			
			var branch = new Branch();
			if (pidx >= 0) branch.parent = branchList.get(pidx);
			branch.uri = ModelSchema.expandPrefix(data.readUTF());
			branch.label = data.readUTF();
			
			if (branch.parent == null)
				onto.roots.add(branch);
			else
				branch.parent.children.add(branch);
				
			onto.uriToBranch.put(branch.uri, branch);
			branchList.add(branch);
		}
		
		onto.roots.sort((b1, b2) -> b1.label.compareToIgnoreCase(b2.label));
		for (var branch : onto.roots) onto.countDescendents(branch);
		
		int numDescr = data.readInt();
		for (int n = 0; n < numDescr; n++)
		{
			String uri = data.readUTF(), descr = data.readUTF();
			onto.uriToDescr.put(uri, descr);
		}
		
		int numAltLabels = data.readInt();
		for (int n = 0; n < numAltLabels; n++)
		{
			String uri = data.readUTF();
			int count = data.readInt();
			var altLabels = new String[count];
			for (int i = 0; i < count; i++) altLabels[i] = data.readUTF();
			onto.uriToAltLabels.put(uri, altLabels);
		}
	
		int numExternalURLs = data.readInt();
		for (int n = 0; n < numExternalURLs; n++)
		{
			String uri = data.readUTF();
			int count = data.readInt();
			var externalURLs = new String[count];
			for (int i = 0; i < count; i++) externalURLs[i] = data.readUTF();
			onto.uriToExternalURLs.put(uri, externalURLs);
		}

		return onto;
	}

    // ------------ private methods ------------

	// recursively add up total number of descendents
	private void countDescendents(Branch branch)
	{
		branch.children.sort((b1, b2) -> b1.label.compareToIgnoreCase(b2.label));
		for (var child : branch.children) countDescendents(child);
		if (branch.parent != null) branch.parent.descendents += 1 + branch.descendents;
	}
}

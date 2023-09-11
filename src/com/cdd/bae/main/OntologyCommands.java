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

package com.cdd.bae.main;

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bao.template.*;
import com.cdd.bao.template.CompareSchemaVocab.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.net.*;

import org.apache.commons.lang3.*;
import org.json.*;

/*
	Command line functionality for rebuilding the ontology basis tree.
*/

public class OntologyCommands implements Main.ExecuteBase
{

	// ------------ public methods ------------

	public void execute(String[] args) throws IOException
	{
		if (args.length == 0) {printHelp(); return;}
		
		String cmd = args[0];
		String[] options = ArrayUtils.remove(args, 0);

		if (cmd.equals("build")) buildVocab(options);
		else if (cmd.equals("fragment")) buildFragments(options);
		//else if (cmd.equals("loadtemplate")) loadTemplate(options);
		//else Util.writeln("Unknown command: '" + cmd + "'.");
	}
	
	public void printHelp()
	{
		Util.writeln("Ontology commands");
		Util.writeln("    specify a sequence of directories to build; default = all in working path");
		Util.writeln("Options:");
		Util.writeln("    build: assemble ontology trees");
		Util.writeln("        <ontodir> <outputfn>");
		Util.writeln("    fragment: assemble ontology as fragmented files");
		Util.writeln("        <ontodir> <fragdir>");
		//Util.writeln("    loadtemplate: load template into database");
		//Util.writeln("        <templatefn>");
		
		//Util.writeln();
		//Util.writeln("e.g. bae vocab build -t /opt/bae/template/schema.json -s /opt/bae/ontology -o /opt/bae/vocab.dump");
	}
	
	public boolean needsConfig() {return false;}
	
	// ------------ private methods ------------
	
	// parses all of the semantic triples, applies all of the applicable schemata, and distills out the content
	private void buildVocab(String[] options) throws IOException
	{
		if (options.length < 2) throw new IOException("Options: <ontodir> <outputfn>");
		String ontodir = Util.expandFileHome(options[0]);
		String outfile = Util.expandFileHome(options[1]);
		if (!outfile.endsWith(".gz")) throw new IOException("Output filename must end with .gz");
		
		Util.writeln("Loading vocabulary from [" + ontodir + "]");
		var vocab = new Vocabulary(ontodir, null);
		
		Util.writeln("Loaded properties: " + vocab.numProperties());
		Util.writeln("       values:     " + vocab.numValues());
		
		var ontoProp = new OntologyTree(vocab, vocab.getPropertyHierarchy());
		var ontoValue = new OntologyTree(vocab, vocab.getValueHierarchy());
		
		Util.writeln("Serialising to " + outfile);
		try (var ostr = new FileOutputStream(outfile))
		{
			var gzip = new GZIPOutputStream(ostr);
			ontoProp.serialise(gzip);
			ontoValue.serialise(gzip);
			gzip.close();
		}

		Util.writeln("Done.");
	}
	
	// parses all of semantic triples like above, except writes to multiple files in order to optimise for dumb-download bandwidth
	private void buildFragments(String[] options) throws IOException
	{
		if (options.length < 2) throw new IOException("Options: <ontodir> <fragdir>");
		String ontodir = Util.expandFileHome(options[0]);
		String fragdir = Util.expandFileHome(options[1]);
		if (!new File(fragdir).isDirectory()) throw new IOException("Fragments parameter must be a dictionary");
		
		Util.writeln("Loading vocabulary from [" + ontodir + "]");
		var vocab = new Vocabulary(ontodir, null);
		
		Util.writeln("Loaded properties: " + vocab.numProperties());
		Util.writeln("       values:     " + vocab.numValues());
		
		var ontoProp = new OntologyTree(vocab, vocab.getPropertyHierarchy());
		var ontoValue = new OntologyTree(vocab, vocab.getValueHierarchy());
		
		writePrefixes(fragdir + "/prefixes.json");
				
		Util.writeln("Writing property fragments");
		new FragmentTree(ontoProp, fragdir + "/prop_").build();
		
		Util.writeln("Writing value fragments");
		new FragmentTree(ontoValue, fragdir + "/value_").build();

		Util.writeln("Done.");
	}
	
	/*private void loadTemplate(String[] options) throws IOException
	{
		if (options.length < 1) throw new IOException("Options: <templatefn>");
		String tmplfile = Util.expandFileHome(options[0]);

		Util.writeln("Reading template [" + tmplfile + "]");
		var schema = Schema.deserialise(new File(tmplfile));
		
		var db = Common.getDataStore().template();

		Util.writeln("Updating database");		
		var tmpl = new DataObject.Template();
		tmpl.schemaPrefix = schema.getSchemaPrefix();
		tmpl.json = schema.serialiseJSON().toString();
		db.updateTemplate(tmpl);
		
		Util.writeln("Identifier: " + tmpl.templateID);
	}*/
	
	private void writePrefixes(String fn) throws IOException
	{
		String[] pfx = ModelSchema.prefixMap;
		var map = new JSONObject();
		for (int n = 0; n < pfx.length; n += 2) map.put(pfx[n], pfx[n + 1]);
		try (Writer wtr = new FileWriter(fn))
		{
			map.write(wtr, 4);
		}
	}
	
	// writes out an ontology tree, the key parts being in fragments

	private static final class FragmentTree
	{
		OntologyTree tree;
		String fnbase;

		final int groupSize = 5000;
		Map<Integer, Integer> mapGroup = new HashMap<>(); // branchID -> group
		List<List<OntologyTree.Branch>> groupChunks = new ArrayList<>();

	
		FragmentTree(OntologyTree tree, String fnbase)
		{
			this.tree = tree;
			this.fnbase = fnbase;
		}
		
		void build() throws IOException
		{			
			// assign group numbers to each of the branches, using a breadth-first style
			for (var root : tree.getRoots()) appendToGroup(root);
			for (var root : tree.getRoots()) assignGroupBranch(root);
			
			// process groups breadth first
			var hierFN = fnbase + "hierarchy.txt";			
			try (Writer wtr = new FileWriter(hierFN))
			{
				for (var root : tree.getRoots()) emitBranchHierarchy(wtr, root, 0);
			}
		
			for (int n = 0; n < groupChunks.size(); n++)
			{
				var chunk = groupChunks.get(n);
				
				try (Writer wtr = new FileWriter(fnbase + String.format("label%03d.txt", n + 1)))
				{
					for (var branch : chunk)
					{
						wtr.write(ModelSchema.collapsePrefix(branch.uri) + " " + stripWhitespace(branch.label) + "\n");
					}
				}
				try (Writer wtr = new FileWriter(fnbase + String.format("detail%03d.txt", n + 1)))
				{
					for (var branch : chunk)
					{
						String descr = tree.getDescr(branch.uri);
						String[] altLabels = tree.getAltLabels(branch.uri);
						String[] externalURLs = tree.getExternalURLs(branch.uri);
						if (Util.isBlank(descr) && Util.length(altLabels) == 0 && Util.length(externalURLs) == 0) continue;
						
						wtr.write(ModelSchema.collapsePrefix(branch.uri) + "\n");
						if (Util.notBlank(descr))
						{
							String esc = JSONObject.quote(descr.trim());
							wtr.write("  D " + esc + "\n");
						}
						if (altLabels != null) for (var label : altLabels)
						{
							wtr.write("  A " + stripWhitespace(label) + "\n");
						}
						if (externalURLs != null) for (var label : externalURLs)
						{
							wtr.write("  U " + stripWhitespace(label) + "\n");
						}					
					}
				}
			}
		}
		
		// breadth first group assignment
		void assignGroupBranch(OntologyTree.Branch branch)
		{
			for (var child : branch.children) appendToGroup(child);
			for (var child : branch.children) assignGroupBranch(child);
		}
		
		private void appendToGroup(OntologyTree.Branch branch)
		{
			if (groupChunks.size() == 0) groupChunks.add(new ArrayList<>());
			var chunk = groupChunks.get(groupChunks.size() - 1);
			if (chunk.size() >= groupSize) groupChunks.add(chunk = new ArrayList<>());
			chunk.add(branch);
			
			mapGroup.put(System.identityHashCode(branch), groupChunks.size());
		}
		
		int getGroup(OntologyTree.Branch branch)
		{
			if (branch == null) return -1;
			return mapGroup.get(System.identityHashCode(branch));
		}
		
		void emitBranchHierarchy(Writer wtr, OntologyTree.Branch branch, int depth) throws IOException
		{	
			wtr.write("-".repeat(depth) + ModelSchema.collapsePrefix(branch.uri) + "=" + getGroup(branch) + "\n");
			for (var child : branch.children) emitBranchHierarchy(wtr, child, depth + 1);
		}
		
		private String stripWhitespace(String str)
		{
			str = str.strip().replaceAll("\n", " ").replaceAll("  ", " ");
			return str;
		}
	}
}



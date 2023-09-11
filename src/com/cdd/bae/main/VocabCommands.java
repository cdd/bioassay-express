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

/*
	Command line functionality pertaining to vocabulary: inferring a term hierarchy from schema + ontology.
*/

public class VocabCommands implements Main.ExecuteBase
{
	// ------------ public methods ------------

	public void execute(String[] args) throws IOException
	{
	/*
		if (args.length == 0) {printHelp(); return;}
		
		String cmd = args[0];
		String[] options = ArrayUtils.remove(args, 0);

		if (cmd.equals("update")) updateSchema(options);
		else if (cmd.equals("build")) buildVocab(options);
		else if (cmd.equals("show")) showVocab(options);
		else if (cmd.equals("diff")) diffVocab(options);
		else Util.writeln("Unknown command: '" + cmd + "'.");
	*/
		Util.writeln("** deprecated");
	}
	
	public void printHelp()
	{
		Util.writeln("Vocabulary commands");
		Util.writeln("    specify a sequence of directories to build; default = all in working path");
		Util.writeln("Options:");
		Util.writeln("    update: compare schema to linked remote content");
		Util.writeln("        --apply to overwrite current files (use with care)");
		Util.writeln("    build: distill vocabulary relative to schema files");
		Util.writeln("        -t {schema templates}");
		Util.writeln("        -s {ontology files}");
		Util.writeln("        -o {output file}");
		Util.writeln("    show {fn}: display a single vocabulary output file");
		Util.writeln("    diff {f1} {f2} {schema}: display differences between schema-dump files f1 and f2; the file `schema` is the reference schema for both dumps.");
		
		Util.writeln();
		Util.writeln("e.g. bae vocab build -t /opt/bae/template/schema.json -s /opt/bae/ontology -o /opt/bae/vocab.dump");
	}
	
	// ------------ private methods ------------
	
	/*
	// downloads updates for schema vocabulary & templates, and compares them to the current content
	private void updateSchema(String[] options) throws IOException
	{
		boolean doApply = false;
		for (String p : options)
		{
			if (p.equals("--apply")) doApply = true;
			else
			{
				Util.writeln("Unexpected parameter: " + p);
				return;
			}
		}		
	
		SchemaVocab vocab = Common.getConfiguration().getSchemaVocab();
		File vocabFile = Common.getConfiguration().getSchemaVocabFile().getFile();
		String vocabUpdate = Common.getConfiguration().getSchemaVocabFile().getUpdateURL();
		Schema[] schemaList = Common.getAllSchemata();
		Templates templates = Common.getConfiguration().getTemplateFiles();
		File[] schemaFiles = templates.getExplicitFiles();
		String[] schemaUpdates = templates.getUpdateURIs(); // is copied
		
		if (Util.isBlank(vocabUpdate)) 
		{
			Util.writeln("Configuration file does not specify a URL for the vocabulary: cannot update");
			return;
		}
		
		final int ntempl = schemaList.length;
		if (ntempl != schemaUpdates.length)
		{
			Util.writeln("Configuration file provides " + schemaUpdates.length + " template update URL(s); # templates = " + ntempl);
			return;
		}
		
		Util.writeln("Updating schema:");
		
		if (vocabUpdate.endsWith("/")) vocabUpdate += Common.getConfiguration().getSchemaVocabFile().getFile().getName();
		Util.writeln("    vocabulary: " + vocabUpdate);
		for (int n = 0; n < ntempl; n++) 
		{
			if (schemaUpdates[n].endsWith("/")) schemaUpdates[n] += schemaFiles[n].getName();
			Util.writeln("    schema #" + (n + 1) + ": " + schemaUpdates[n]);
		}
		
		byte[] vocabDump = downloadBinary(vocabUpdate);
		
		Schema[] newTemplates = new Schema[ntempl];
		byte[][] templateDump = new byte[ntempl][];
		for (int n = 0; n < ntempl; n++)
		{
			templateDump[n] = downloadBinary(schemaUpdates[n]);
			try (InputStream istr = new ByteArrayInputStream(templateDump[n])) {newTemplates[n] = SchemaUtil.deserialise(istr).schema;}
		}
		
		SchemaVocab newVocab = null;
		try (InputStream istr = new ByteArrayInputStream(vocabDump)) {newVocab = SchemaVocab.deserialise(istr, newTemplates);}

		Util.writeln();
		Util.writeln("Old vocabulary: " + vocab.numTerms());
		Util.writeln("New vocabulary: " + newVocab.numTerms());
		
		showVocabDifference(vocab, newVocab);
		
		for (int n = 0; n < ntempl; n++)
		{
			Schema template = newTemplates[n];
			Util.writeln("\n==== Template [" + template.getRoot().name + "] <" + ModelSchema.collapsePrefix(template.getSchemaPrefix()) + "> ====");
			compareTemplates(schemaList[n], vocab, newTemplates[n], newVocab);
		}
		
		if (doApply)
		{
			Util.writeln("\nApplying updated schema...");
			
			File[] backup = ArrayUtils.add(schemaFiles, vocabFile);
			backupFilesZip(new File("/tmp/schema-backup.zip"), backup);
			
			for (int n = 0; n < schemaFiles.length; n++) overwriteFile(schemaFiles[n], templateDump[n]);
			overwriteFile(vocabFile, vocabDump);
		}
		
		Util.writeln("\nDone.");
	}
	
	// parses all of the semantic triples, applies all of the applicable schemata, and distills out the content
	private void buildVocab(String[] options) throws IOException
	{
		List<String> templates = new ArrayList<>(), ontologies = new ArrayList<>();
		String ontdir = null, outfile = null;
		
		String type = null;
		for (String p : options)
		{
			if (p.equals("-t") || p.equals("-s") || p.equals("-o")) type = p;
			else if (p.startsWith("-") || type == null)
			{
				Util.writeln("Unexpected parameter: " + p);
				return;
			}
			else
			{
				if (type.equals("-t")) templates.add(p);
				else if (type.equals("-s")) 
				{
					if (new File(p).isDirectory())
						ontdir = p;
					else
						ontologies.add(p);
				}
				else if (type.equals("-o"))
				{
					if (outfile != null)
					{
						Util.writeln("Can only specify one output file.");
						return;
					}
					outfile = p;
				}
			}
		}
		
		if (templates.isEmpty()) {Util.writeln("Must specify at least one template file."); return;}
		if (ontdir == null && ontologies.isEmpty()) {Util.writeln("Must specify at least one ontology file or directory."); return;}
		if (outfile == null) {Util.writeln("Must specify an output file."); return;}
		
		Util.writeln("Building vocab with:");
		Util.writeln("    schema templates:   " + templates.size());
		Util.writeln("    ontology directory: " + ontdir);
		Util.writeln("    ontology files:     " + ontologies.size());
		Util.writeln("    output file:        " + outfile);
		
		Util.writeln("Loading schema templates...");
		Schema[] schema = new Schema[templates.size()];
		for (int n = 0; n < schema.length; n++) schema[n] = ModelSchema.deserialise(new File(templates.get(n)));
		
		Util.writeln("Loading triples...");
		Vocabulary vocab = new Vocabulary(ontdir, ontologies.toArray(new String[ontologies.size()]));
		Util.writeln("... total loaded: " + vocab.getAllURIs().length);
		
		Util.writeln("Creating branches...");
		SchemaVocab sv = new SchemaVocab(vocab, schema);
		
		Util.writeln("-------------------------");
		sv.debugSummary();
		Util.writeln("-------------------------");
		
		Util.writeln("Writing to [" + outfile + "]...");
		OutputStream ostr = new FileOutputStream(outfile);
		sv.serialise(ostr);
		ostr.close();
		
		double sz = new File(outfile).length() / (double)(1024 * 1024);
		Util.writeln(String.format("Done. Written %.2f MB.", sz));
	}
	
	// load & show vocab
	private void showVocab(String[] options) throws IOException
	{
		if (options.length == 0) 
		{
			Util.writeln("Must specify at least one template file");
			return;
		}
		String fn = options[0];
		try (InputStream istr = new FileInputStream(fn))
		{
			SchemaVocab sv = SchemaVocab.deserialise(istr, new Schema[0]); // note: giving no schemata works for this purpose
			sv.debugSummary();
		}
	}

	private void diffVocab(String[] options) throws IOException
	{
		if (options.length != 3) { printHelp(); return; }

		String fn1 = options[0], fn2 = options[1], fn3 = options[2];
		Util.writeln("Differences between vocab dumps...");
		Util.writeln("    OLD:" + fn1);
		Util.writeln("    NEW:" + fn2);

		Schema schema = null;
		try (InputStream istr = new FileInputStream(fn3))
		{
			schema = SchemaUtil.deserialise(istr).schema;
		}

		SchemaVocab sv1 = null, sv2 = null;
		try (InputStream istr = new FileInputStream(fn1))
		{
			sv1 = SchemaVocab.deserialise(istr, new Schema[]{schema}); // note: giving no schemata works for this purpose
		}
		try (InputStream istr = new FileInputStream(fn2))
		{
			sv2 = SchemaVocab.deserialise(istr, new Schema[]{schema}); // note: giving no schemata works for this purpose
		}

		Util.writeln("Term counts: [" + sv1.numTerms() + "] -> [" + sv2.numTerms() + "]");
		Util.writeln("Prefixes: [" + sv1.numPrefixes() + "] -> [" + sv2.numPrefixes() + "]");

		CompareSchemaVocab compareSchVocabs = new CompareSchemaVocab(sv1, sv2);
		TreeNode<DiffInfo> diff = compareSchVocabs.getDiffTree();
		for (TreeNode<DiffInfo> assn : diff.children) if (assn.data != null)
		{
			// add assignments
			printDiffInfo(0, assn.data);

			// add terms
			for (TreeNode<DiffInfo> term : assn.children) printDiffInfo(1, term.data);
		}
	}

	private void printDiffInfo(int depth, DiffInfo diff)
	{
		if (diff == null) return;

		String prefix = StringUtils.repeat(" ", 4 * depth);
		StringBuilder out = new StringBuilder(prefix);
		if (diff.direction == DiffType.NONE)
		{
			if (diff.assn != null) out.append(diff.assn.name);
			else out.append(diff.valueURI);
		}
		else
		{
			out.append(diff.direction == DiffType.DELETION ? "Removed: " : "Added: ");
			if (diff.valueLabel != null) out.append(diff.valueLabel + " ");
			out.append("<" + ModelSchema.collapsePrefix(diff.valueURI) + ">"); 
		}
		Util.writeln(out.toString());
	}

	// web request of the binary variety
	private byte[] downloadBinary(String url) throws IOException
	{
		Util.writeFlush("Downloading [" + url + "] ");
	
		URLConnection conn = (URLConnection)new URL(url).openConnection();
		HttpURLConnection http = conn instanceof HttpURLConnection ? (HttpURLConnection)conn : null;
		conn.setDoOutput(true);
		if (http != null) http.setRequestMethod("GET");
		int cutoff = 300000; // 5 minutes
		conn.setConnectTimeout(cutoff);
		conn.setReadTimeout(cutoff);
		conn.connect();
		
		if (http != null)
		{
			int respCode = http.getResponseCode();
			if (respCode >= 400) return null; // this is relatively graceful
			if (respCode != 200) throw new IOException("HTTP response code " + respCode + " for URL [" + url + "]");
		}
	
		// read the raw bytes into memory; abort if it's too long or too slow
		BufferedInputStream istr = new BufferedInputStream(conn.getInputStream());
		ByteArrayOutputStream buff = new ByteArrayOutputStream();
		final int DOWNLOAD_LIMIT = 100 * 1024 * 1024; // within reason
		while (true)
		{
			int b = -1;
			try {b = istr.read();} 
			catch (SocketTimeoutException ex) {throw new IOException(ex);}
			if (b < 0) break;
			if (buff.size() >= DOWNLOAD_LIMIT) 
				throw new IOException("Download size limit exceeded (max=" + DOWNLOAD_LIMIT + " bytes) for URL: " + url);
			buff.write(b);
		}
		istr.close();
		
		byte[] data = buff.toByteArray();
		Util.writeln("size: " + data.length);
		return data;
	}
	
	// displays the terms added/removed relative to the old & new vocabulary content
	private void showVocabDifference(SchemaVocab oldVocab, SchemaVocab newVocab)
	{
		Set<String> oldTerms = new HashSet<>(), newTerms = new HashSet<>();
		for (SchemaVocab.StoredTerm term : oldVocab.getTerms()) oldTerms.add(term.uri);
		for (SchemaVocab.StoredTerm term : newVocab.getTerms()) newTerms.add(term.uri);
		Set<String> added = new TreeSet<>(), removed = new TreeSet<>();
		for (String uri : newTerms) if (!oldTerms.contains(uri)) added.add(uri);
		for (String uri : oldTerms) if (!newTerms.contains(uri)) removed.add(uri);
		
		Util.writeln("# terms added: " + added.size() + ", removed: " + removed.size());
		
		if (removed.size() > 0)
		{
			Util.writeln("Removed terms:");
			for (String uri : removed)
			{
				SchemaVocab.StoredTerm term = oldVocab.getTerm(uri);
				Util.writeln("    " + term.label + " <" + ModelSchema.collapsePrefix(uri) + ">");
			}
		}
		if (added.size() > 0)
		{
			Util.writeln("Added terms:");
			for (String uri : added)
			{
				SchemaVocab.StoredTerm term = newVocab.getTerm(uri);
				Util.writeln("    " + term.label + " <" + ModelSchema.collapsePrefix(uri) + ">");
			}
		}
	}	
	
	// go through all of the assignments 
	private void compareTemplates(Schema oldTemplate, SchemaVocab oldVocab, Schema newTemplate, SchemaVocab newVocab)
	{
		List<Schema.Assignment> oldAssnList = new ArrayList<>(), newAssnList = new ArrayList<>();
		for (Schema.Assignment assn : oldTemplate.getRoot().flattenedAssignments()) oldAssnList.add(assn);
		for (Schema.Assignment assn : newTemplate.getRoot().flattenedAssignments()) newAssnList.add(assn);

		Map<Schema.Assignment, SchemaTree> oldTrees = new HashMap<>(), newTrees = new HashMap<>();
		for (SchemaVocab.StoredTree stored : oldVocab.getTrees()) oldTrees.put(stored.assignment, stored.tree);
		for (SchemaVocab.StoredTree stored : newVocab.getTrees()) newTrees.put(stored.assignment, stored.tree);
		
		for (int i = 0; i < oldAssnList.size(); i++)
		{
			Schema.Assignment assn1 = oldAssnList.get(i);
			SchemaTree tree1 = oldTrees.get(assn1);
			if (tree1 == null) continue;			
				
			for (int j = 0; j < newAssnList.size(); j++)
			{
				Schema.Assignment assn2 = newAssnList.get(j);
				if (!assn1.propURI.equals(assn2.propURI) || !Arrays.equals(assn1.groupNest(), assn2.groupNest())) continue;
				SchemaTree tree2 = newTrees.get(assn2);
				if (tree2 == null) continue;
				
				Util.writeln("  Assignment: [" + assn2.name + "] <" + ModelSchema.collapsePrefix(assn2.propURI) + ">");
				compareAssignments(assn1, tree1, assn2, tree2);
				oldAssnList.remove(i--);
				newAssnList.remove(j);
				break;
			}
		}
	}
	
	// show individual values that may have been added/deleted from a branch
	private void compareAssignments(Schema.Assignment oldAssn, SchemaTree oldTree, Schema.Assignment newAssn, SchemaTree newTree)
	{
		Set<SchemaTree.Node> added = new HashSet<>(), removed = new HashSet<>();
		for (SchemaTree.Node node : oldTree.getList()) if (newTree.getNode(node.uri) == null) removed.add(node);
		for (SchemaTree.Node node : newTree.getList()) if (oldTree.getNode(node.uri) == null) added.add(node);
		
		if (removed.size() > 0)
		{
			List<String> lines = new ArrayList<>();
			for (SchemaTree.Node node : removed) lines.add(node.label + " <" + ModelSchema.collapsePrefix(node.uri) + ">");
			Collections.sort(lines);
			Util.writeln("    Removed " + removed.size() + "...");
			for (String line : lines) Util.writeln("      - " + line);
		}
		if (added.size() > 0)
		{
			List<String> lines = new ArrayList<>();
			for (SchemaTree.Node node : added) lines.add(node.label + " <" + ModelSchema.collapsePrefix(node.uri) + ">");
			Collections.sort(lines);
			Util.writeln("    Added " + added.size() + "...");
			for (String line : lines) Util.writeln("      + " + line);
		}
	}
	
	// stash the given files into a temporary zipfile, just in case
	private void backupFilesZip(File zipfile, File[] backupFiles) throws IOException
	{
		Util.writeln("Making backup of old files to [" + zipfile.getAbsolutePath() + "]");
		zipfile.delete();
		try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipfile)))
		{
			for (File f : backupFiles)
			{
				Util.writeln("    adding " + f.getName());
				zip.putNextEntry(new ZipEntry(f.getName()));
				try (InputStream in = new BufferedInputStream(new FileInputStream(f)))
				{
					int b;
					while ((b = in.read()) >= 0) {zip.write(b);}
				}
			}
		}
	}
	
	// replaces a files contents in one big go
	private void overwriteFile(File f, byte[] dump) throws IOException
	{
		Util.writeln("Overwriting [" + f.getAbsolutePath() + "]");	
		try (OutputStream out = new BufferedOutputStream(new FileOutputStream(f)))
		{
			out.write(dump, 0, dump.length);
		}
	}*/
}



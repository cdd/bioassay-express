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

import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.json.*;

/*
	Functionality pertaining to turning Protein GI numbers (from PubChem assays) into a faux-ontology of sorts, that
	can be used as a placeholder.
	
	Taxonomy content comes from: ftp://ftp.ncbi.nih.gov/pub/taxonomy
*/

public class ProteinFunctions
{
	private String func = null, initDir = null;

	private final Pattern ptnSource = Pattern.compile("^(\\d+)\\.concise.json\\.gz$");
	private final Pattern ptnGeneID = Pattern.compile(".*gene/GID(\\d+)$");
	private final Pattern ptnTaxonID = Pattern.compile(".*taxonomy/(\\d+)$");

	private static final class Protein
	{
		int proteinGI;
		String title;
		int geneID, taxonID;
	}

	private static final class Node
	{
		Node parent;
		String uri, label, descr;
		int taxonID = 0;
		Protein protein = null;
		List<Node> children = new ArrayList<>();
		int branchCount = 0;
		
		Node(String uri, String label, String descr)
		{
			this.uri = uri;
			this.label = label;
			this.descr = descr;
		}
		Node addChild(String uri, String label, String descr)
		{
			Node node = new Node(uri, label, descr);
			node.parent = this;
			children.add(node);
			return node;
		}
	}

	// ------------ public methods ------------

	public ProteinFunctions(String[] options)
	{
		int pos = 0;
		if (options.length > pos && !options[pos].startsWith("-")) func = options[pos++];
		if (pos + 1 < options.length && options[pos].equals("-init")) initDir = options[pos + 1];
	}
	
	public void exec() throws Exception
	{
		if (func == null) {printHelp(); return;}
		if (initDir == null) throw new IOException("Must specify -init {dir}.");
		
		Util.writeln("Executing [" + func + "] with dir: " + initDir);
		if (func.equals("gather")) doGather();
		else if (func.equals("fetch")) doFetch();
		else if (func.equals("fauxtology")) doFauxtology();
		else throw new IOException("Unknown function '" + func + "'.");
	}
		
	// ------------ private methods ------------
	
	private void printHelp()
	{
		Util.writeln("Functions are:");
		Util.writeln("    gather: find unique protein IDs from assays (writes to used.txt)");
		Util.writeln("    fetch: download information for used proteins (writes to info.txt)");
		Util.writeln("    fauxtology: create a faux ontology for geneIDs (writes to protein.ttl)");
	}
	
	private void doGather() throws IOException
	{
		List<File> files = new ArrayList<>();
		for (File f : new File(initDir + "/assays").listFiles()) if (f.getName().endsWith(".zip")) files.add(f);
		Collections.sort(files);
		
		Set<Integer> proteinGI = new TreeSet<>();
		
		for (int n = 0; n < files.size(); n++)
		{
			File f = files.get(n);
			Util.writeln("Scanning: [" + f.getAbsolutePath() + "] (protein GIs so far: " + proteinGI.size() + ")");
						
			try(ZipInputStream zip = new ZipInputStream(new FileInputStream(f)))
			{	
				ZipEntry ze = zip.getNextEntry();
				while (ze != null)
				{
					String path = ze.getName(), name = new File(path).getName();
					Matcher m = ptnSource.matcher(name);
					if (m.matches())
					{
						GZIPInputStream gzip = new GZIPInputStream(zip);
						JSONObject json = new JSONObject(new JSONTokener(new BufferedReader(new InputStreamReader(gzip))));
						JSONObject root = json.optJSONObject("PC_AssaySubmit");
						if (root != null) scourProteinGI(root, proteinGI);
					}

					zip.closeEntry();
					ze = zip.getNextEntry();
				}
			}
		}
		
		File of = new File(initDir + "/protein/used.txt");
		Util.writeln("Found " + proteinGI.size() + " unique protein GIs. Writing to: " + of.getPath());
		saveList(of, proteinGI);
	}
	
	private void doFetch() throws IOException
	{
		Set<Integer> proteinGI = loadList(new File(initDir + "/protein/used.txt"));
		Util.writeln("Applicable proteins: " + proteinGI.size());
		File pfile = new File(initDir + "/protein/info.txt");
		Map<Integer, Protein> details = loadProteins(pfile);
		Util.writeln("Details previously acquired: " + details.size());
		
		int tick = 0;
		for (int id : proteinGI) if (!details.containsKey(id))
		{
			Util.writeln("  loading proteinGI: " + id);
			acquireProteinInfo(id, details);
			
			tick++;
			if (tick % 100 == 0)
			{
				Util.writeln("Waypoint: size = " + details.size());
				saveProteins(pfile, details);
			}
		}
		
		Util.writeln("Saving protein details: " + details.size());
		saveProteins(pfile, details);
	}
		
	// contemplates the used gene IDs and creates the "faux ontology" tree
	private void doFauxtology() throws IOException
	{
		File pfile = new File(initDir + "/protein/info.txt");
		Map<Integer, Protein> proteins = loadProteins(pfile);
		Util.writeln("Proteins detailed: " + proteins.size());
		
		// load up names first
		Util.writeln("Loading taxonomy names...");
		Map<Integer, Node> nodeMap = new HashMap<>();
		try(BufferedReader rdr = new BufferedReader(new FileReader(new File(initDir + "/protein/taxdump/names.dmp"))))
		{
			while (true)
			{
				String line = rdr.readLine();
				if (line == null) break;
				String[] bits = line.split("\\|");
				int taxonID = Integer.parseInt(bits[0].trim());
				String name = bits[1].trim(), type = bits[3].trim();
				if (!type.equals("scientific name")) continue;
				
				Node node = new Node(ModelSchema.PFX_TAXON + taxonID, name, null);
				node.taxonID = taxonID;
				nodeMap.put(taxonID, node);
			}
		}
		Util.writeln("Loaded taxonomy entries: " + nodeMap.size());
		
		// fill in the parent/child tree mappings
		try(BufferedReader rdr = new BufferedReader(new FileReader(new File(initDir + "/protein/taxdump/nodes.dmp"))))
		{
			while (true)
			{
				String line = rdr.readLine();
				if (line == null) break;
				String[] bits = line.split("\\|");
				int taxonID = Integer.parseInt(bits[0].trim()), parentID = Integer.parseInt(bits[1].trim());
				if (taxonID == parentID) continue; // (the first one; weird...)
				
				Node node = nodeMap.get(taxonID), parent = nodeMap.get(parentID);
				node.parent = parent;
				parent.children.add(node);
			}
		}
		
		// add the proteins in as leaf nodes
		int added = 0;
		Set<Integer> uniqBranch = new HashSet<>();
		for (Protein p : proteins.values())
		{
			Node parent = nodeMap.get(p.taxonID);
			if (parent == null)
			{
				Util.writeln("** proteinGI " + p.proteinGI + " has no taxonomy...");
				continue;
			}
			
			String descr = "Protein: " + p.title + ", Organism: " + parent.label;
			
			Node node = new Node(ModelSchema.PFX_PROTEIN + p.proteinGI, p.title, descr);
			node.protein = p;
			node.parent = parent;
			parent.children.add(node);
			
			for (Node look = node; look != null; look = look.parent) look.branchCount++;
			added++;
			uniqBranch.add(p.taxonID);
		}
		
		Util.writeln("Added " + added + " proteins, of " + proteins.size() + "; to " + uniqBranch.size() + " taxonomy branches.");
		
		// prune the tree: anything that leads to no proteins gets snipped
		for (Iterator<Node> it = nodeMap.values().iterator(); it.hasNext();)
		{
			Node node = it.next();
			if (node.branchCount == 0)
			{
				node.parent.children.remove(node);
				it.remove();
			}
		}
		
		Util.writeln("Taxonomy nodes reduced to: " + nodeMap.size());
		
		// anything that's a singleton bridge can be collapsed, to make the whole thing a bit less stupendously nested
		skip: for (Iterator<Node> it = nodeMap.values().iterator(); it.hasNext();)
		{
			Node node = it.next(), parent = node.parent;
			if (parent == null || parent.children.size() != 1) continue;
			for (Node child : node.children) if (child.protein != null) continue skip;
			
			// 'tis ripe for collapsing
			parent.label += " / " + node.label;
			parent.children.remove(node);
			for (Node child : node.children)
			{
				parent.children.add(child);
				child.parent = parent;
			}
			it.remove();
		}
				
		Util.writeln("Without singletons, reduced to: " + nodeMap.size());

		//for (Node node : nodeMap.values()) if (node.parent == null) displayTaxonTree(node, 0);
		
		Model model = ModelFactory.createDefaultModel();

		model.setNsPrefix("rdfs", ModelSchema.PFX_RDFS);
		model.setNsPrefix("xsd", ModelSchema.PFX_XSD);
		model.setNsPrefix("rdf", ModelSchema.PFX_RDF);
		model.setNsPrefix("obo", ModelSchema.PFX_OBO);
		model.setNsPrefix("taxon", ModelSchema.PFX_TAXON);
		model.setNsPrefix("protein", ModelSchema.PFX_PROTEIN);
		model.setNsPrefix("bat", ModelSchema.PFX_BAT);
		//model.setNsPrefix("pubchem", "http://rdf.ncbi.nlm.nih.gov/pubchem/");

		for (Node node : nodeMap.values()) if (node.parent == null) 
		{
			Util.writeln("Creating root...");
			node.uri = ModelSchema.PFX_TAXON + "Root";
			node.label = "Faux ProteinID";
			node.descr = "Temporary ontology terms for NCBI Protein GI";
			semanticTaxonProteinTree(model, node);
		}

		File of = new File(initDir + "/protein/protein.ttl");
		Util.writeln("Writing semantic triples to [" + of.getPath() + "]");
		
		BufferedOutputStream ostr = new BufferedOutputStream(new FileOutputStream(of));
		RDFDataMgr.write(ostr, model, RDFFormat.TURTLE);
		ostr.close();
	}

	
	// goes through the assay record looking any SID that is labelled explicitly as a "probe"
	private void scourProteinGI(JSONObject root, Set<Integer> proteinGI)
	{
		JSONObject descr = root.getJSONObject("assay").getJSONObject("descr");
		JSONArray jsonXRef = descr.optJSONArray("xref"), jsonTarget = descr.optJSONArray("target");

		if (jsonXRef != null) for (int n = 0; n < jsonXRef.length(); n++)
		{
			JSONObject obj1 = jsonXRef.getJSONObject(n), obj2 = obj1.getJSONObject("xref");
			if (!obj2.has("protein_gi")) continue;
			int id = obj2.getInt("protein_gi");
			if (!proteinGI.contains(id))
			{
				Util.writeln("    found new protein GI: " + id);
				proteinGI.add(id);
			}
		}
		
		if (jsonTarget != null) for (int n = 0; n < jsonTarget.length(); n++)
		{
			JSONObject obj = jsonTarget.getJSONObject(n);
			if (!obj.optString("molecule_type", "").equals("protein")) continue;
			int id = obj.getInt("mol_id");
			if (!proteinGI.contains(id))
			{
				Util.writeln("    found new protein GI: " + id);
				proteinGI.add(id);
			}
		}		
	}
	
	// looks up the REST RDF content for a given protein, and adds the results
	private void acquireProteinInfo(int id, Map<Integer, Protein> details) throws IOException
	{
		String url = "http://pubchem.ncbi.nlm.nih.gov/rest/rdf/protein/GI" + id + ".ttl";
		String str = null;
		try {str = Util.makeRequest(url, null);}
		catch (IOException ex) {Util.writeln(" ... request barfed with: " + ex.getMessage());}
		if (str == null) {Util.writeln(" ... no details available"); return;}
		
		//Util.writeln(str);
		
		final String base = "http://rdf.ncbi.nlm.nih.gov/pubchem/";
		
		Model model = ModelFactory.createDefaultModel();
		try {RDFDataMgr.read(model, new StringReader(str), base, Lang.TTL);}
		catch (Exception ex) {throw new IOException("Failed to parse schema", ex);}
		
		Resource resProtein = model.createResource(base + "protein/GI" + id);
		
		Property propTitle = model.createProperty("http://purl.org/dc/terms/title");
		Property propGeneID = model.createProperty("http://rdf.ncbi.nlm.nih.gov/pubchem/vocabulary#encodedBy");
		Property propTaxonID = model.createProperty("http://www.biopax.org/release/biopax-level3.owl#organism");
		
		Protein p = new Protein();
		p.proteinGI = id;
		
		Util.writeln("    <" + resProtein + ">");
		for (StmtIterator it = model.listStatements(resProtein, null, (RDFNode)null); it.hasNext();)
		{
			Statement st = it.next();
			//Util.writeln("    --> [" + st.getPredicate() + "] [" + st.getObject() + "]");
			Property pred = st.getPredicate();
			if (pred.equals(propTitle))
			{
				Util.writeln("        title: [" + st.getObject().asLiteral().getLexicalForm() + "]");
				p.title = st.getObject().asLiteral().getLexicalForm();
			}
			else if (pred.equals(propGeneID))
			{
				String uri = st.getObject().toString();
				Util.writeln("        geneID: <" + uri + ">");
				Matcher m = ptnGeneID.matcher(uri);
				if (!m.matches()) throw new IOException("Invalid geneID URI: " + uri + ", from pattern: " + ptnGeneID);
				p.geneID = Integer.valueOf(m.group(1));
			}
			else if (pred.equals(propTaxonID))
			{
				String uri = st.getObject().toString();
				Util.writeln("        taxonID: <" + uri + ">");
				Matcher m = ptnTaxonID.matcher(uri);
				if (!m.matches()) throw new IOException("Invalid taxonID URI: " + uri + ", from pattern: " + ptnTaxonID);
				p.taxonID = Integer.valueOf(m.group(1));
			}
		}
		
		Util.writeln("    protein: " + p.proteinGI + "," + p.geneID + "," + p.taxonID + ": " + p.title);
		if (p.title == null) 
		{
			Util.writeln("    ... no title; skipping it");
			return;
		}
		
		details.put(id, p);
	}
	
	// debugging view
	private void displayTaxonTree(Node node, int level)
	{
		Util.write("  ");
		for (int n = 0; n < level; n++) Util.write("* ");
		Util.writeln(node.label + " <" + node.uri + ">");
		for (Node child : node.children) displayTaxonTree(child, level + 1);
	}

	// recursively export the tree as triples
	private void semanticTaxonProteinTree(Model model, Node node)
	{
		Resource resNode = model.createResource(node.uri);
		
		model.add(resNode, model.createProperty(ModelSchema.PFX_RDFS + "label"), node.label);
		if (node.descr != null) model.add(resNode, model.createProperty(ModelSchema.PFX_OBO + "IAO_0000115"), node.descr);

		if (node.parent != null)
		{
			Resource resParent = model.createResource(node.parent.uri);
			model.add(resNode, model.createProperty(ModelSchema.PFX_RDFS + "subClassOf"), resParent);
		}

		if (node.taxonID > 0) 
		{
			String uri = "http://identifiers.org/taxonomy/" + node.taxonID;
			model.add(resNode, model.createProperty(ModelSchema.PFX_BAT + "refNCBITaxonID"), model.createResource(uri));
		}
		if (node.protein != null)
		{
			String uri = "http://rdf.ncbi.nlm.nih.gov/pubchem/protein/GI" + node.protein.proteinGI;
			model.add(resNode, model.createProperty(ModelSchema.PFX_BAT + "refNCBIProteinGI"), model.createResource(uri));
		}
				
		for (Node child : node.children) semanticTaxonProteinTree(model, child);
	}
 
	private Set<Integer> loadList(File file) throws IOException
	{
		Set<Integer> set = new TreeSet<>();
		if (!file.exists()) return set;
		try (BufferedReader rdr = new BufferedReader(new FileReader(file)))
		{
			while (true)
			{
				String line = rdr.readLine();
				if (line == null) break;
				if (line.length() > 0) set.add(Integer.valueOf(line));
			}
		}
		return set;
	}
	private void saveList(File file, Set<Integer> set) throws IOException
	{	
		try(FileWriter wtr = new FileWriter(file))
		{
			for (int id : set) wtr.write(id + "\n");
		}
	}
	private Map<Integer, Protein> loadProteins(File file) throws IOException
	{
		Map<Integer, Protein> map = new TreeMap<>();
		if (!file.exists()) return map;
		try(BufferedReader rdr = new BufferedReader(new FileReader(file)))
		{
			while (true)
			{
				String line = rdr.readLine();
				if (line == null) break;
				String[] bits = line.split("\t");
				if (bits.length < 4) continue;
				Protein p = new Protein();
				p.proteinGI = Integer.valueOf(bits[0]);
				p.title = bits[1];
				p.geneID = Integer.valueOf(bits[2]);
				p.taxonID = Integer.valueOf(bits[3]);
				map.put(p.proteinGI, p);
			}
		}
		return map;
	}
	private void saveProteins(File file, Map<Integer, Protein> map) throws IOException
	{	
		try(FileWriter wtr = new FileWriter(file))
		{
			for (Protein p : map.values())
			{
				wtr.write(p.proteinGI + "\t" + p.title + "\t" + p.geneID + "\t" + p.taxonID + "\n");
			}
		}
	}
}

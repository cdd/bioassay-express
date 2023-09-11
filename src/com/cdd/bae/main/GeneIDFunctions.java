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
	Functionality pertaining to turning Gene ID numbers into a "faux ontology", to give them temporarily enhanced meaning.
	
	Content comes from:
		ftp://ftp.ncbi.nih.gov/gene/DATA/GENE_INFO/
*/

public class GeneIDFunctions
{
	private static final String OTHER = "Other";
	private String func = null;
	private String initDir = null;

	private static final class Node
	{
		Node parent;
		String uri, label, descr;
		String ncbiURL = null, pubchemURL = null;
		List<Node> children = new ArrayList<>();
		
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

	public GeneIDFunctions(String[] options)
	{
		int pos = 0;
		if (options.length > pos && !options[pos].startsWith("-")) func = options[pos++];
		if (pos + 1 < options.length && options[pos].equals("-init")) initDir = options[pos + 1];
	}
	
	public void exec() throws IOException
	{
		if (func == null) {printHelp(); return;}
		if (initDir == null) throw new IOException("Must specify -init {dir}.");
		
		Util.writeln("Executing [" + func + "] with dir: " + initDir);
		if (func.equals("gather")) doGather();
		else if (func.equals("fauxtology")) doFauxtology();
		else throw new IOException("Unknown function '" + func + "'.");
	}
		
	// ------------ private methods ------------
	
	private void printHelp()
	{
		Util.writeln("Functions are:");
		Util.writeln("    gather: find unique gene IDs from assays (writes to used.txt)");
		Util.writeln("    fauxtology: create a faux ontology for geneIDs (writes to geneid.ttl)");
	}
	
	private void doGather() throws IOException
	{
		List<File> files = new ArrayList<>();
		for (File f : new File(initDir + "/assays").listFiles()) if (f.getName().endsWith(".zip")) files.add(f);
		Collections.sort(files);
		
		final Pattern ptnSource = Pattern.compile("^(\\d+)\\.concise.json\\.gz$");
		Set<Integer> geneID = new TreeSet<>();
		
		for (int n = 0; n < files.size(); n++)
		{
			File f = files.get(n);
			Util.writeln("Scanning: [" + f.getAbsolutePath() + "] (gene IDs so far: " + geneID.size() + ")");

			try (ZipInputStream zip = new ZipInputStream(new FileInputStream(f)))
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
						if (root != null) scourGeneID(root, geneID);
					}

					zip.closeEntry();
					ze = zip.getNextEntry();
				}
			}
		}
		
		File of = new File(initDir + "/geneid/used.txt");
		Util.writeln("Found " + geneID.size() + " unique gene IDs. Writing to: " + of.getPath());
		saveList(of, geneID);
	}
	
	// contemplates the used gene IDs and creates the "faux ontology" tree
	private void doFauxtology() throws IOException
	{
		Set<Integer> geneID = loadList(new File(initDir + "/geneid/used.txt"));
		Util.writeln("Loaded " + geneID.size() + " geneIDs.");
		
		Node root = new Node(ModelSchema.PFX_GENEID + "Root", "Faux GeneID", "Temporary ontology terms for NCBI Gene ID");
		Node archaea = root.addChild(ModelSchema.PFX_GENEID + "ArchaeaBacteria", "Archaea & Bacteria", null);
		Node mammalia = root.addChild(ModelSchema.PFX_GENEID + "Mammalia", "Mammalia", null);
		Node plants = root.addChild(ModelSchema.PFX_GENEID + "Plants", "Plants", null);
		Node viruses = root.addChild(ModelSchema.PFX_GENEID + "Viruses", "Viruses", null);
		Node fungi = root.addChild(ModelSchema.PFX_GENEID + "Fungi", "Fungi", null);
		Node nonmammal = root.addChild(ModelSchema.PFX_GENEID + "NonMammal", "Non-mammalian Vertebrates", null);
		Node plasmids = root.addChild(ModelSchema.PFX_GENEID + "Plasmids", "Plasmids", null);
		Node invertebrates = root.addChild(ModelSchema.PFX_GENEID + "Invertebrates", "Invertebrates", null);
		Node organelles = root.addChild(ModelSchema.PFX_GENEID + "Organelles", "Organelles", null);
		Node protozoa = root.addChild(ModelSchema.PFX_GENEID + "Protozoa", "Protozoa", null);
		
		processGenes(geneID, archaea.addChild(ModelSchema.PFX_GENEID + "Archaea", "Archaea", null), "Archaea_Bacteria/Archaea.gene_info.gz");
		processGenes(geneID, archaea.addChild(ModelSchema.PFX_GENEID + "Bacteria", "Bacteria", null), "Archaea_Bacteria/Bacteria.gene_info.gz");
		processGenes(geneID, archaea.addChild(ModelSchema.PFX_GENEID + "Escherichia_coli", "Escherichia coli", null), "Archaea_Bacteria/Escherichia_coli_str._K-12_substr._MG1655.gene_info.gz");
		processGenes(geneID, archaea.addChild(ModelSchema.PFX_GENEID + "Pseudomonas_aeruginosa", "Pseudomonas aeruginosa", null), "Archaea_Bacteria/Pseudomonas_aeruginosa_PAO1.gene_info.gz");
		processGenes(geneID, archaea.addChild(ModelSchema.PFX_GENEID + OTHER, OTHER, null), "Archaea_Bacteria/All_Archaea_Bacteria.gene_info.gz");
		
		processGenes(geneID, fungi.addChild(ModelSchema.PFX_GENEID + "Penicillium_chrysogenum", "Penicillium chrysogenum", null), "Fungi/Penicillium_chrysogenum_Wisconsin_54-1255.gene_info.gz");
		processGenes(geneID, fungi.addChild(ModelSchema.PFX_GENEID + "Ascomycota", "Ascomycota", null), "Fungi/Ascomycota.gene_info.gz");
		processGenes(geneID, fungi.addChild(ModelSchema.PFX_GENEID + "Saccharomyces_cerevisiae", "Saccharomyces cerevisiae", null), "Fungi/Saccharomyces_cerevisiae.gene_info.gz");
		processGenes(geneID, fungi.addChild(ModelSchema.PFX_GENEID + "Microsporidia", "Microsporidia", null), "Fungi/Microsporidia.gene_info.gz");
		processGenes(geneID, fungi.addChild(ModelSchema.PFX_GENEID + OTHER, OTHER, null), "Fungi/All_Fungi.gene_info.gz");

		processGenes(geneID, invertebrates.addChild(ModelSchema.PFX_GENEID + "Anopheles_gambiae", "Anopheles gambiae", null), "Invertebrates/Anopheles_gambiae.gene_info.gz");
		processGenes(geneID, invertebrates.addChild(ModelSchema.PFX_GENEID + "Caenorhabditis_elegans", "Caenorhabditis elegans", null), "Invertebrates/Caenorhabditis_elegans.gene_info.gz");
		processGenes(geneID, invertebrates.addChild(ModelSchema.PFX_GENEID + "Drosophila_melanogaster", "Drosophila melanogaster", null), "Invertebrates/Drosophila_melanogaster.gene_info.gz");
		processGenes(geneID, invertebrates.addChild(ModelSchema.PFX_GENEID + OTHER, OTHER, null), "Invertebrates/All_Invertebrates.gene_info.gz");

		processGenes(geneID, mammalia.addChild(ModelSchema.PFX_GENEID + "Bos_taurus", "Bos taurus", null), "Mammalia/Bos_taurus.gene_info.gz");
		processGenes(geneID, mammalia.addChild(ModelSchema.PFX_GENEID + "Canis_familiaris", "Canis familiaris", null), "Mammalia/Canis_familiaris.gene_info.gz");
		processGenes(geneID, mammalia.addChild(ModelSchema.PFX_GENEID + "Homo_sapiens", "Homo sapiens", null), "Mammalia/Homo_sapiens.gene_info.gz");
		processGenes(geneID, mammalia.addChild(ModelSchema.PFX_GENEID + "Mus_musculus", "Mus musculus", null), "Mammalia/Mus_musculus.gene_info.gz");
		processGenes(geneID, mammalia.addChild(ModelSchema.PFX_GENEID + "Pan_troglodytes", "Pan troglodytes", null), "Mammalia/Pan_troglodytes.gene_info.gz");
		processGenes(geneID, mammalia.addChild(ModelSchema.PFX_GENEID + "Rattus_norvegicus", "Rattus norvegicus", null), "Mammalia/Rattus_norvegicus.gene_info.gz");
		processGenes(geneID, mammalia.addChild(ModelSchema.PFX_GENEID + "Sus_scrofa", "Sus scrofa", null), "Mammalia/Sus_scrofa.gene_info.gz");
		processGenes(geneID, mammalia.addChild(ModelSchema.PFX_GENEID + OTHER, OTHER, null), "Mammalia/All_Mammalia.gene_info.gz");

		processGenes(geneID, nonmammal.addChild(ModelSchema.PFX_GENEID + "Danio_rerio", "Danio rerio", null), "Non-mammalian_vertebrates/Danio_rerio.gene_info.gz");
		processGenes(geneID, nonmammal.addChild(ModelSchema.PFX_GENEID + "Gallus_gallus", "Gallus gallus", null), "Non-mammalian_vertebrates/Gallus_gallus.gene_info.gz");
		processGenes(geneID, nonmammal.addChild(ModelSchema.PFX_GENEID + "Xenopus_laevis", "Xenopus laevis", null), "Non-mammalian_vertebrates/Xenopus_laevis.gene_info.gz");
		processGenes(geneID, nonmammal.addChild(ModelSchema.PFX_GENEID + "Xenopus_tropicalis", "Xenopus tropicalis", null), "Non-mammalian_vertebrates/Xenopus_tropicalis.gene_info.gz");
		processGenes(geneID, nonmammal.addChild(ModelSchema.PFX_GENEID + OTHER, OTHER, null), "Non-mammalian_vertebrates/All_Non-mammalian_vertebrates.gene_info.gz");

		processGenes(geneID, plants.addChild(ModelSchema.PFX_GENEID + "Arabidopsis_thaliana", "Arabidopsis thaliana", null), "Plants/Arabidopsis_thaliana.gene_info.gz");
		processGenes(geneID, plants.addChild(ModelSchema.PFX_GENEID + "Chlamydomonas_reinhardtii", "Chlamydomonas reinhardtii", null), "Plants/Chlamydomonas_reinhardtii.gene_info.gz");
		processGenes(geneID, plants.addChild(ModelSchema.PFX_GENEID + "Oryza_sativa", "Oryza sativa", null), "Plants/Oryza_sativa.gene_info.gz");
		processGenes(geneID, plants.addChild(ModelSchema.PFX_GENEID + "Zea_mays", "Zea mays", null), "Plants/Zea_mays.gene_info.gz");
		processGenes(geneID, plants.addChild(ModelSchema.PFX_GENEID + OTHER, OTHER, null), "Plants/All_Plants.gene_info.gz");

		processGenes(geneID, protozoa.addChild(ModelSchema.PFX_GENEID + "Plasmodium_falciparum", "Plasmodium falciparum", null), "Protozoa/Plasmodium_falciparum.gene_info.gz");
		processGenes(geneID, protozoa.addChild(ModelSchema.PFX_GENEID + OTHER, OTHER, null), "Protozoa/All_Protozoa.gene_info.gz");

		processGenes(geneID, viruses.addChild(ModelSchema.PFX_GENEID + "Retroviridae", "Retroviridae", null), "Viruses/Retroviridae.gene_info.gz");
		processGenes(geneID, viruses.addChild(ModelSchema.PFX_GENEID + "dsDNA_viruses-noRNA", "dsDNA viruses (no RNA)", null), "Viruses/dsDNA_viruses,_no_RNA_stage.gene_info.gz");
		processGenes(geneID, viruses.addChild(ModelSchema.PFX_GENEID + "dsRNA_viruses", "dsRNA viruses", null), "Viruses/dsRNA_viruses.gene_info.gz");
		processGenes(geneID, viruses.addChild(ModelSchema.PFX_GENEID + "ssDNA_viruses", "ssDNA viruses", null), "Viruses/ssDNA_viruses.gene_info.gz");
		processGenes(geneID, viruses.addChild(ModelSchema.PFX_GENEID + "ssRNA_negative-strand_viruses", "ssRNA negative-strand viruses", null), "Viruses/ssRNA_negative-strand_viruses.gene_info.gz");
		processGenes(geneID, viruses.addChild(ModelSchema.PFX_GENEID + "ssRNA_positive-strand_viruses-noDNA", "ssRNA positive strand viruses (no DNA)", null), "Viruses/ssRNA_positive-strand_viruses,_no_DNA_stage.gene_info.gz");
		processGenes(geneID, viruses.addChild(ModelSchema.PFX_GENEID + OTHER, OTHER, null), "Viruses/All_Viruses.gene_info.gz");

		processGenes(geneID, plasmids, "Plasmids.gene_info.gz");
		processGenes(geneID, organelles, "Organelles.gene_info.gz");
		
		Util.writeln("Remaining GeneIDs: " + geneID.size());
		for (int id : geneID) Util.writeln("    " + id);
		
		Util.writeln("Tree structure");
		displayGeneTree(root, 0);

		Model model = ModelFactory.createDefaultModel();

		model.setNsPrefix("rdfs", ModelSchema.PFX_RDFS);
		model.setNsPrefix("xsd", ModelSchema.PFX_XSD);
		model.setNsPrefix("rdf", ModelSchema.PFX_RDF);
		model.setNsPrefix("obo", ModelSchema.PFX_OBO);
		model.setNsPrefix("geneid", ModelSchema.PFX_GENEID);
		model.setNsPrefix("bat", ModelSchema.PFX_BAT);
		model.setNsPrefix("ncbi", "http://www.ncbi.nlm.nih.gov/gene/");
		model.setNsPrefix("pubchem", "http://rdf.ncbi.nlm.nih.gov/pubchem/");
		semanticGeneTree(model, root);
		
		File of = new File(initDir + "/geneid/geneid.ttl");
		Util.writeln("Writing semantic triples to [" + of.getPath() + "]");
		
		BufferedOutputStream ostr = new BufferedOutputStream(new FileOutputStream(of));
		RDFDataMgr.write(ostr, model, RDFFormat.TURTLE);
		ostr.close();
	}

	// digs through the indicated file and looks for any geneIDs that belong there; when found, they are added to the
	// indicated node, and removed from the geneID list (i.e. order does matter in case of duplication)
	private void processGenes(Set<Integer> geneID, Node node, String fn) throws IOException
	{
		File f = new File(initDir + "/geneid/" + fn);
		Util.writeln("Processing [" + f.getPath() + "] (genes left to assign: " + geneID.size() + ")");
		try (BufferedReader rdr = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(f)))))
		{
			String line = rdr.readLine(); // discard first row, 'tis nonsense...
			while ((line = rdr.readLine()) != null)
			{
				if (line.length() == 0) continue;
				String[] bits = line.split("\t");
				int id = Integer.parseInt(bits[1]);
				String symbol = bits[2], fullname = bits[8];

				if (!geneID.contains(id)) continue;

				Util.writeln("    " + id + " : " + symbol + " : " + fullname);

				Node child = node.addChild(ModelSchema.PFX_GENEID + id, symbol + ": " + fullname, null);
				child.ncbiURL = "http://www.ncbi.nlm.nih.gov/gene/" + id;
				child.pubchemURL = "http://rdf.ncbi.nlm.nih.gov/pubchem/gene/GID" + id;
				geneID.remove(id);
			}
		}
	}

	// goes through the assay record looking any SID that is labelled explicitly as a "probe"
	private void scourGeneID(JSONObject root, Set<Integer> geneID)
	{
		JSONObject descr = root.getJSONObject("assay").getJSONObject("descr");
		JSONArray jsonXRef = descr.optJSONArray("xref");
		if (jsonXRef == null) return;
		for (int n = 0; n < jsonXRef.length(); n++)
		{
			JSONObject obj1 = jsonXRef.getJSONObject(n), obj2 = obj1.getJSONObject("xref");
			if (!obj2.has("gene")) continue;
			int id = obj2.getInt("gene");
			if (!geneID.contains(id))
			{
				Util.writeln("    found new gene ID: " + id);
				geneID.add(id);
			}
		}
	}
	
	// debugging view
	private void displayGeneTree(Node node, int level)
	{
		Util.write("  ");
		for (int n = 0; n < level; n++) Util.write("* ");
		Util.writeln(node.label);
		for (Node child : node.children) displayGeneTree(child, level + 1);
	}

	// recursively export the tree as triples
	private void semanticGeneTree(Model model, Node node)
	{
		if (node.ncbiURL == null && node.children.isEmpty()) return; // omit empty container branches
	
		Resource resNode = model.createResource(node.uri);
		
		model.add(resNode, model.createProperty(ModelSchema.PFX_RDFS + "label"), node.label);
		if (node.descr != null) model.add(resNode, model.createProperty(ModelSchema.PFX_OBO + "IAO_0000115"), node.descr);

		if (node.parent != null)
		{
			Resource resParent = model.createResource(node.parent.uri);
			model.add(resNode, model.createProperty(ModelSchema.PFX_RDFS + "subClassOf"), resParent);
		}
		
		if (node.ncbiURL != null) model.add(resNode, model.createProperty(ModelSchema.PFX_BAT + "refNCBIGeneID"), model.createResource(node.ncbiURL));
		if (node.pubchemURL != null) model.add(resNode, model.createProperty(ModelSchema.PFX_BAT + "refPubChemRDF"), model.createResource(node.pubchemURL));
		
		for (Node child : node.children) semanticGeneTree(model, child);
	}
 
	private Set<Integer> loadList(File file) throws IOException
	{
		Set<Integer> list = new TreeSet<>();
		if (!file.exists()) return list;
		try (BufferedReader rdr = new BufferedReader(new FileReader(file)))
		{
			while (true)
			{
				String line = rdr.readLine();
				if (line == null) break;
				if (line.length() > 0) list.add(Integer.valueOf(line));
			}
		}
		return list;
	}
	private void saveList(File file, Set<Integer> list) throws IOException
	{	
		try (FileWriter wtr = new FileWriter(file))
		{
			for (int id : list) wtr.write(id + "\n");
		}
	}
}

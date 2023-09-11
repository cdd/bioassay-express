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

package com.cdd.bae.config;

import com.cdd.bae.data.*;
import com.cdd.bao.template.*;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.apache.commons.lang3.tuple.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.slf4j.*;

/*
	Scans a directory for ontology files (OWL/RDF/TTL) and contemplates them for addition to the current schema tree. This is similar to the
	functionality contained in the template editor project's Vocabulary class, except that it has a lot less machinery for resolving oddball
	ontologies: it assumes that the trees are already present, and that the only things to be appended are values with well defined syntax.
	The purpose is for implementing "after the fact" ontology trees, which didn't get compiled into the main tree (vocab.dump) but don't belong
	in the provisional term functionality.
*/

public class ExtraOntologies
{
	private static final Logger logger = LoggerFactory.getLogger(ExtraOntologies.class);

	private String directory;

	// ------------ public methods ------------

	public ExtraOntologies(String directory)
	{
		this.directory = directory;
	}

	// scans all files in the directory; note that errors will be reported to the console, but otherwise not cause problems upstream
	public void scan()
	{
		File[] files = new File(directory).listFiles();
		if (files == null)
		{
			logger.error("Extra ontology directory [" + directory + "] invalid.");
			return;
		}
		for (File f : files) 
		{
			String fn = f.getName();
			if (fn.startsWith(".")) continue;
			if (fn.endsWith(".ttl") || fn.endsWith(".rdf") || fn.endsWith(".owl")) processFile(f);
		}
	}

	// ------------ private methods ------------
	
	protected void processFile(File file)
	{
		Model model = ModelFactory.createDefaultModel();
		try (InputStream istr = new FileInputStream(file))
		{
			RDFDataMgr.read(model, istr, file.getName().endsWith(".ttl") ? Lang.TURTLE : Lang.RDFXML);
		}
		catch (IOException ex)
		{
			logger.error("Unable to read extra-ontology file: " + file.getAbsolutePath() + ", reason: " + ex.getMessage());
			return;
		}
	
		Property propLabel = model.createProperty(ModelSchema.PFX_RDFS + "label");
		Property propDescr = model.createProperty(ModelSchema.PFX_OBO + "IAO_0000115");
		Property subClassOf = model.createProperty(ModelSchema.PFX_RDFS + "subClassOf");
		Property externalURL = model.createProperty(ModelSchema.PFX_BAE + "externalURL");
		Property altLabel1 = model.createProperty(ModelSchema.PFX_BAE + "altLabel");
		Property altLabel2 = model.createProperty(ModelSchema.PFX_OBO + "IAO_0000118");
		Property altLabel3 = model.createProperty(ModelSchema.PFX_OBO + "IAO_0000111");
		Property altLabel4 = model.createProperty("http://www.ebi.ac.uk/efo/alternative_term");
		
		// obtain all the "<x> subClassOf <y>" cases, and add them the roster
		
		List<Pair<String, SchemaTree.Node>> nodes = new ArrayList<>();
		
		for (StmtIterator iter = model.listStatements(null, subClassOf, (RDFNode)null); iter.hasNext();)
		{
			Statement stmt = iter.next();
			Resource subject = stmt.getSubject();
			RDFNode object = stmt.getObject();
			if (!object.isURIResource()) continue; // not valid
			
			String parentURI = object.asResource().getURI();
			SchemaTree.Node node = new SchemaTree.Node();
			node.uri = subject.getURI();
			node.inSchema = true;
			node.isExplicit = true;
			
			for (StmtIterator subIter = model.listStatements(subject, null, (RDFNode)null); subIter.hasNext();)
			{
				Statement subStmt = subIter.next();
				Property prop = subStmt.getPredicate();
				if (!subStmt.getObject().isLiteral()) continue;
				String text = subStmt.getObject().asLiteral().getString();
				if (prop.equals(propLabel)) node.label = text;
				else if (prop.equals(propDescr)) node.descr = text;
				else if (prop.equals(externalURL)) node.externalURLs = ArrayUtils.add(node.externalURLs, text);
				else if (prop.equals(altLabel1) || prop.equals(altLabel2) ||
						 prop.equals(altLabel3) || prop.equals(altLabel4)) node.altLabels = ArrayUtils.add(node.altLabels, text);
			}

			nodes.add(Pair.of(parentURI, node));
		}
		
		// add the new terms to the general vocab lookup (this handles ordering/redundancy)
		SchemaVocab schvoc = Common.getSchemaVocab();
		List<SchemaVocab.StoredTerm> storedTerms = new ArrayList<>();
		Set<String> addedURIs = new HashSet<>();
		for (SchemaVocab.StoredTree stored : schvoc.getTrees())
		{
			List<SchemaTree.Node> added = stored.tree.addNodes(nodes);
			for (SchemaTree.Node node : added) if (addedURIs.add(node.uri))
			{
				SchemaVocab.StoredTerm term = new SchemaVocab.StoredTerm();
				term.uri = node.uri;
				term.label = node.label;
				term.descr = node.descr;
				storedTerms.add(term);
			}
		}
		schvoc.addTerms(storedTerms, new HashMap<>());
	}
}

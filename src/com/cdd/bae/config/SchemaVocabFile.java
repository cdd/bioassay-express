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
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import org.apache.commons.lang3.*;
import org.apache.commons.lang3.tuple.*;
import org.slf4j.*;

/*
	Container class for the schema vocabulary file, which holds the terms & hierarchy information that have been distilled from
	the underlying ontologies.
*/

public class SchemaVocabFile
{
	private static final Logger logger = LoggerFactory.getLogger(SchemaVocabFile.class);

	private String updateURL;
	private Templates templates;
	private InitParams.Provisional prov;
	private Schema[] schemaList;
	private SchemaVocab schemaVocab = null;
	protected static Map<String, SchemaTree> treeCache = new HashMap<>();
	protected static Map<String, Provisional> provisionalURIs = new HashMap<>(); // uri-to-term

	protected FileWatcher watcher;

	// ------------ public methods ------------

	public SchemaVocabFile(InitParams params, Templates templates) throws ConfigurationException
	{
		super();
		updateURL = params.schemaVocabUpdate;
		watcher = new FileWatcher(new File(params.schemaVocabFile));
		prov = params.provisional;

		this.schemaList = ArrayUtils.addAll(templates.getSchemaList(), templates.getSchemaBranches());
		this.templates = templates;
		load();
	}

	public boolean hasChanged()
	{
		return watcher.hasChanged();
	}

	public File getFile()
	{
		return watcher.getFile();
	}
	public void setFile(File newFile)
	{
		watcher.watchFile(newFile);
	}

	public void load() throws ConfigurationException
	{
		try (InputStream inpstr = new FileInputStream(watcher.getFile()))
		{
			boolean isGzip = watcher.getFile().getName().endsWith(".gz");
			InputStream istr = isGzip ? new GZIPInputStream(inpstr) : inpstr;
			setSchemaVocab(SchemaVocab.deserialise(istr, schemaList));
		}
		catch (IOException ex)
		{
			logger.error("Fail to load schemaVocab file " + watcher.getFile());
			ex.printStackTrace();
			throw new ConfigurationException(ex.getMessage(), ex);
		}
		watcher.reset();
	}

	public void reload() throws ConfigurationException
	{
		load();
	}

	// reload all provisional terms
	public void loadProvisionals()
	{
		DataStore store = Common.getDataStore();
		if (store == null) return;

		// scan the indicated directory for terms that are post-compilation/pre-provisional
		if (prov.directory != null) new ExtraOntologies(prov.directory).scan();

		// load provisionals into the tree
		provisionalURIs.clear();
		incorporateProvisionals(store.provisional().fetchAllTerms());
	}

	// call this method from the UI to request a new provisional term, adding it to the database and the in-memory trees
	public boolean addProvisional(DataObject.Provisional prov)
	{
		DataStore store = Common.getDataStore();
		if (store == null) return false;

		// check to see if there is already an {parent/label} match, and if so fail
		for (DataObject.Provisional look : store.provisional().fetchAllTerms())
		{
			if (look.parentURI.equals(prov.parentURI) && look.label.equals(prov.label)) return false;
		}

		store.provisional().updateProvisional(prov);
		
		synchronized (this)
		{
			incorporateProvisionals(new Provisional[]{prov});
		}

		return true;
	}

	// modify a provisional term and update its status in the tree; note that modifying uri or parentURI is not allowed, which
	// means that updating the local cache of the tree is relatively straightforward, and that current assay annotations are
	// not affected
	public boolean updateProvisional(DataObject.Provisional prov)
	{
		if (prov.provisionalID == 0) return false;
		DataStore store = Common.getDataStore();
		if (store == null) return false;
		DataObject.Provisional prev = store.provisional().getProvisional(prov.provisionalID);
		if (!prov.uri.equals(prev.uri) || !prov.parentURI.equals(prev.parentURI)) return false;
		
		store.provisional().updateProvisional(prov);

		synchronized (this)
		{
			modifyProvisionalTree(prov);
		}
		
		return true;
	}
	
	// remove a provisional term from the database and update the tree status accordingly; note that this method does not
	// check to see if any assays are using the term, or if any other terms are dependent on it - that should be done by the caller
	public boolean deleteProvisional(DataObject.Provisional prov)
	{
		if (prov.provisionalID == 0) return false;
		DataStore store = Common.getDataStore();
		if (store == null) return false;
		
		store.provisional().deleteProvisional(prov.provisionalID);
		
		synchronized (this)
		{
			deleteProvisionalTree(prov);
		}
		
		return true;
	}

	public SchemaVocab getSchemaVocab()
	{
		synchronized (this)
		{
			return schemaVocab;
		}
	}

	public void setSchemaVocab(SchemaVocab schemaVocab)
	{
		//schemaVocab.debugSummary();

		synchronized (this)
		{
			this.schemaVocab = schemaVocab;
			resetTreeCache();

			// reload provisionals each time schemavocab is reset
			loadProvisionals();
		}
	}

	public String getUpdateURL()
	{
		synchronized (this)
		{
			return updateURL;
		}
	}

	public void setSchemaList(Schema[] schemaList)
	{
		synchronized (this)
		{
			this.schemaList = schemaList;
			resetTreeCache();

			// reload provisionals if schema list changes
			loadProvisionals();
		}
	}

	// fetch all the URIs considered provisional
	public Set<String> getProvisionalURIs()
	{
		return Collections.unmodifiableSet(provisionalURIs.keySet());
	}
	public Map<String, Provisional> getProvisionalTerms()
	{
		return Collections.unmodifiableMap(provisionalURIs);
	}

	// ------------ schema trees: quick fetching from cache ------------

	// obtain a tree instance for the given assignment
	public SchemaTree obtainTree(Schema schema, String propURI, String[] groupNest)
	{
		Schema.Assignment[] match = schema.findAssignmentByProperty(propURI, groupNest);
		return match.length == 0 ? null : treeCache.get(assignmentKey(schema, match[0]));
	}

	// ------------ private methods ------------

	private void resetTreeCache()
	{
		treeCache.clear();
		for (SchemaVocab.StoredTree stored : schemaVocab.getTrees())
		{
			if (stored.assignment == null)
			{
				if (templates.getSchema(stored.schemaPrefix) != null)
					Util.writeln("WARNING: unmatched assignment, schema=" + stored.schemaPrefix +
								", propURI=" + stored.propURI + ", groupNest=" + Util.arrayStr(stored.groupNest));
				else
					Util.writeln("WARNING: check schema=" + stored.schemaPrefix + ", propURI=" + stored.propURI);
				continue;
			}
			treeCache.put(assignmentKey(stored.schemaPrefix, stored.assignment), stored.tree);
		}
	}

	// incorporate the specified provisional terms into the tree
	private void incorporateProvisionals(Provisional[] provisionals)
	{
		List<SchemaVocab.StoredTerm> provTerms = new ArrayList<>();
		Map<String, SchemaVocab.StoredRemapTo> provRemappings = new HashMap<>();

		List<Pair<String, SchemaTree.Node>> nodes = new ArrayList<>();
		for (Provisional prov : provisionals)
		{
			provisionalURIs.put(prov.uri, prov);

			SchemaTree.Node node = new SchemaTree.Node();
			node.uri = prov.uri;
			node.label = prov.label;
			node.descr = prov.description;
			nodes.add(Pair.of(prov.parentURI, node));
			if (prov.remappedTo != null)
			{
				SchemaVocab.StoredRemapTo srt = new SchemaVocab.StoredRemapTo();
				srt.fromURI = prov.uri;
				srt.toURI = prov.remappedTo;
				provRemappings.put(srt.fromURI, srt);
			}
		}

		Set<String> addedURIs = new HashSet<>();
		for (SchemaVocab.StoredTree stored : schemaVocab.getTrees())
		{
			List<SchemaTree.Node> added = stored.tree.addNodes(nodes);
			for (SchemaTree.Node node : added) if (addedURIs.add(node.uri))
			{
				SchemaVocab.StoredTerm term = new SchemaVocab.StoredTerm();
				term.uri = node.uri;
				term.label = node.label;
				term.descr = node.descr;
				provTerms.add(term);
			}
		}

		// finally, update data structures in SchemaVocab
		schemaVocab.addTerms(provTerms, provRemappings);
	}
	
	// scans through all of the existing trees looking for places where a term is mapped from this term's URI, and
	// updates the content accordingly
	private void modifyProvisionalTree(Provisional prov)
	{
		schemaVocab.defineRemapping(prov.uri, prov.remappedTo);
		for (SchemaVocab.StoredTerm term : schemaVocab.getTerms()) if (term.uri.equals(prov.uri))
		{
			term.label = prov.label;
			term.descr = prov.description;
		}
		
		provisionalURIs.put(prov.uri, prov);
		
		for (SchemaVocab.StoredTree stored : schemaVocab.getTrees())
		{
			SchemaTree.Node node = stored.tree.getNode(prov.uri);
			if (node != null)
			{
				node.label = prov.label;
				node.descr = prov.description;
			}
		}
	}
	
	// scans through trees looking for instances of this provisional term, and removes them
	private void deleteProvisionalTree(Provisional prov)
	{
		schemaVocab.defineRemapping(prov.uri, null);
		for (int n = schemaVocab.numTerms() - 1; n >= 0; n--) 
			if (schemaVocab.getTerm(n).uri.equals(prov.uri)) schemaVocab.removeTerm(n);
		
		provisionalURIs.remove(prov.uri);

		for (SchemaVocab.StoredTree stored : schemaVocab.getTrees()) stored.tree.removeNode(prov.uri);
	}

	protected static String assignmentKey(String schemaPrefix, Schema.Assignment assn)
	{
		String[] groupNest = assn.groupNest();
		for (int n = 0; n < groupNest.length; n++) groupNest[n] = Schema.removeSuffixGroupURI(groupNest[n]);
		return schemaPrefix + "::" + Schema.keyPropGroup(assn.propURI, groupNest);
	}
	protected static String assignmentKey(Schema schema, Schema.Assignment assn)
	{
		return assignmentKey(schema.getSchemaPrefix(), assn);
	}
}

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

package com.cdd.bae.web;

import com.cdd.bae.data.*;
import com.cdd.bae.rest.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import static com.cdd.bao.template.ModelSchema.*;
import static com.cdd.bae.util.ProtoRDF.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.json.*;

import org.apache.commons.lang3.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.util.*;

/*
	Package up everything in the BAE database as one gigantic gzipped TTL file.
	
	Parameters:
		wholetree={false/true} optionally force *all* of the schema tree to be included (this is typically real big)
	
*/

public class DownloadEverything extends BaseServlet 
{
	private static final long serialVersionUID = 1L;
	
	private DataStore store = Common.getDataStore();
	
	private static final String ROOT_ASSAYS = ModelSchema.expandPrefix("bae:Assays");
	private static final String ROOT_SCHEMATA = ModelSchema.expandPrefix("bae:Schemata");
	
	// placeholder for emitting all of the schema information
	private static final class AssignmentInfo
	{
		Schema schema;
		Schema.Assignment assn;
		SchemaTree tree;
		String owlURI;
	}
	private static final class SchemaContext
	{
		int watermark = 0;
		List<AssignmentInfo> assignments = new ArrayList<>();
		Map<Integer, String> assnToURI = new HashMap<>(); // assignment identity to OWL URI
		Map<Integer, String> nodeToURI = new HashMap<>(); // treenode identity to OWL URI
		
		String nextURI()
		{
			return ModelSchema.PFX_BAE + String.format("Schema_%07d", ++watermark);
		}
	}
	
	@Override
	protected void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		boolean wholeTree = "true".equalsIgnoreCase(request.getParameter("wholetree"));	
	
		response.setContentType("application/gzip");
		
		DataStore store = Common.getDataStore();
		
		try (GZIPOutputStream gzip = new GZIPOutputStream(response.getOutputStream()))
		{
			// create an empty model and push it out: this establishes the abbreviations
			Model model = createModel();
			RDFDataMgr.write(gzip, model, RDFFormat.TURTLE);
			
			// preload the assays (need these to reduce the schema trees first)
			List<DataObject.Assay> assayList = new ArrayList<>();
			for (long assayID : store.assay().fetchAssayIDCurated())
			{
				DataObject.Assay assay = store.assay().getAssay(assayID);
				if (assay != null) assayList.add(assay);
			}
			
			// include each available schema
			SchemaContext context = new SchemaContext();
			for (Schema schema : Common.getAllSchemata())
			{
				model = createModel();
				populateSchema(context, schema, model);
				writeContent(gzip, model);
			}
			
			// now for the trees that were compiled from above, blast each one out
			for (AssignmentInfo ainfo : context.assignments)
			{
				model = createModel();
				populateTree(context, ainfo.owlURI, ainfo.schema, ainfo.tree, model, wholeTree ? null : assayList);
				writeContent(gzip, model);
			}
			
			// include each assay
			String baseURI = "http://www.bioassayexpress.org/bae/";
			for (DataObject.Assay assay : assayList)
			{
				model = createModel();
				populateAssay(context, assay, model);
				writeContent(gzip, model);
			}
			
			// include the provisional terms
			model = createModel();
			DownloadProvisional.fillModelContent(model);
			writeContent(gzip, model);
			
			gzip.flush();
		}
	}

	// ------------ private methods ------------

	// creates an empty model; this will be done once for each batch of content
	private Model createModel()
	{
		Model model = ModelFactory.createDefaultModel();
		for (Map.Entry<String, String> entry : ModelSchema.getPrefixes().entrySet()) 
		{
			String pfx = entry.getKey(), url = entry.getValue();
			model.setNsPrefix(pfx.substring(0, pfx.indexOf(":")), url);
		}
		return model;
	}
	
	// write out one model, after snipping the header content (which is written at the beginning); assumes that each model
	// is relatively small
	private void writeContent(OutputStream ostr, Model model) throws IOException
	{
		// TODO: make a custom writer that just skips lines starting with '@'
	
		StringWriter wtr = new StringWriter();
		RDFDataMgr.write(wtr, model, RDFFormat.TURTLE);
		
		StringBuilder bldr = new StringBuilder();
		bldr.append("\n# ----\n");
		for (String line : wtr.toString().split("\n")) if (!line.startsWith("@")) bldr.append(line + "\n");
		
		ostr.write(bldr.toString().getBytes(Util.UTF8));
	}
	
	// fills out the contents of a schema and all of its tree content
	private void populateSchema(SchemaContext context, Schema schema, Model model) throws IOException
	{
		String schemaURI = schema.getSchemaPrefix();
		
		Property propSchemaType = model.createProperty(ModelSchema.PFX_BAT + "schemaType");
		Property propSubClass = model.createProperty(ModelSchema.PFX_RDFS + "subClassOf");
		Property propLabel = model.createProperty(ModelSchema.PFX_RDFS + "label");
				
		model.add(model.createResource(schemaURI), propSubClass, model.createResource(ROOT_SCHEMATA));
		model.add(model.createResource(schemaURI), propLabel, model.createLiteral(schema.getRoot().name));
		model.add(model.createResource(schemaURI), propSchemaType, model.createResource(ModelSchema.PFX_BAT + "Schema"));
	
		populateSchemaGroup(context, schemaURI, schema, schema.getRoot(), model);
	}
	
	// emits a schema group information and all of its sub-content
	private void populateSchemaGroup(SchemaContext context, String parentURI, Schema schema, Schema.Group group, Model model) throws IOException
	{
		Property propSubClass = model.createProperty(ModelSchema.expandPrefix("rdfs:subClassOf"));
		Resource batAssignment = model.createResource(ModelSchema.PFX_BAT + ModelSchema.BAT_ASSIGNMENT);
		Resource batGroup = model.createResource(ModelSchema.PFX_BAT + ModelSchema.BAT_GROUP);
		Property rdfLabel = model.createProperty(ModelSchema.PFX_RDFS + "label");
		Property propSchemaType = model.createProperty(ModelSchema.PFX_BAT + "schemaType");
		Property hasDescription = model.createProperty(ModelSchema.PFX_BAT + ModelSchema.HAS_DESCRIPTION);		
		Property hasProperty = model.createProperty(ModelSchema.PFX_BAT + ModelSchema.HAS_PROPERTY);
		Property hasGroupURI = model.createProperty(ModelSchema.PFX_BAT + ModelSchema.HAS_GROUPURI);
		Property hasSuggestionType = model.createProperty(ModelSchema.PFX_BAT + "suggestionType");

		for (Schema.Assignment assn : group.assignments)
		{
			String uriAssn = context.nextURI();
			Resource objAssn = model.createResource(uriAssn);

			model.add(objAssn, propSubClass, model.createResource(parentURI));
			model.add(objAssn, propSchemaType, batAssignment);
			model.add(objAssn, rdfLabel, assn.name);
			if (Util.notBlank(assn.descr)) model.add(objAssn, hasDescription, assn.descr);
			model.add(objAssn, hasSuggestionType, model.createLiteral(assn.suggestions.toString()));
			if (Util.notBlank(assn.propURI)) model.add(objAssn, hasProperty, model.createResource(assn.propURI.trim()));
			model.add(objAssn, hasGroupURI, makeURIList(model, assn.groupNest()));
			
			context.assnToURI.put(System.identityHashCode(assn), uriAssn);
			
			SchemaTree tree = Common.obtainTree(schema, assn);
			if (tree != null && tree.getTree().size() > 0)
			{
				AssignmentInfo ainfo = new AssignmentInfo();
				ainfo.schema = schema;
				ainfo.assn = assn;
				ainfo.tree = tree;
				ainfo.owlURI = uriAssn;
				context.assignments.add(ainfo);
			}
		}
		
		for (Schema.Group subGroup : group.subGroups) 
		{
			String uriGroup = context.nextURI();
			Resource objGroup = model.createResource(uriGroup);
			
			model.add(objGroup, propSubClass, model.createResource(parentURI));
			model.add(objGroup, propSchemaType, batGroup);
			model.add(objGroup, rdfLabel, subGroup.name);
			if (Util.notBlank(subGroup.descr)) model.add(objGroup, hasDescription, subGroup.descr);
			if (Util.notBlank(subGroup.groupURI)) model.add(objGroup, hasProperty, model.createResource(subGroup.groupURI.trim()));
			model.add(objGroup, hasGroupURI, makeURIList(model, subGroup.groupNest()));
		
			populateSchemaGroup(context, uriGroup, schema, subGroup, model);
		}
	}
	
	// for a given assignment resource, builds up a hierarchy tree: uses an OWL-compatible hierarchy as the primary structure
	private void populateTree(SchemaContext context, String parentURI, Schema schema, SchemaTree tree, 
							  Model model, List<DataObject.Assay> assayList) throws IOException
	{
		Property propSubClass = model.createProperty(ModelSchema.expandPrefix("rdfs:subClassOf"));
		Property propSchemaType = model.createProperty(ModelSchema.PFX_BAT + "schemaType");
		Resource batValue = model.createResource(ModelSchema.PFX_BAT + "Value");
		Property rdfLabel = model.createProperty(ModelSchema.PFX_RDFS + "label");
		Property propHasURI = model.createProperty(ModelSchema.PFX_BAT + "hasURI");
		Property propHasDescr = model.createProperty(ModelSchema.PFX_BAT + "hasDescr");
		Property propHasAltLabels = model.createProperty(ModelSchema.PFX_BAT + "hasAltLabels");
		Property propHasExternalURLs = model.createProperty(ModelSchema.PFX_BAT + "hasExternalURLs");
		final boolean WITH_EXTRA = false; // descriptions & stuff clog up the output, so not necessarily desirable
		
		SchemaTree.Node[] nodes = tree.getFlat();
		Resource objParent = model.createResource(parentURI);
		Resource[] objNodes = new Resource[nodes.length];
		
		// define a mask for which nodes get included
		boolean[] mask = new boolean[nodes.length];
		if (assayList != null)
		{
			Schema.Assignment assn = tree.getAssignment();
			Map<String, Integer> uriToIndex = new HashMap<>();
			for (int n = 0; n < nodes.length; n++) uriToIndex.put(nodes[n].uri, n);
			
			for (DataObject.Assay assay : assayList) if (schema.getSchemaPrefix().equals(assay.schemaURI))
			{
				for (DataObject.Annotation annot : assay.annotations) 
					if (Schema.samePropGroupNest(assn.propURI, assn.groupNest(), annot.propURI, annot.groupNest))
				{
					int idx = uriToIndex.getOrDefault(annot.valueURI, -1);
					for (; idx >= 0; idx = nodes[idx].parentIndex) mask[idx] = true;
				}
			}
		}
		else Arrays.fill(mask, true);

		// emit all eligible tree items
		for (int n = 0; n < nodes.length; n++) if (mask[n])
		{
			String uriNode = context.nextURI();
			objNodes[n] = model.createResource(uriNode);
			
			int pidx = nodes[n].parentIndex;
			model.add(objNodes[n], propSubClass, pidx < 0 ? objParent : objNodes[pidx]);
			model.add(objNodes[n], propSchemaType, batValue);
			model.add(objNodes[n], propHasURI, model.createResource(nodes[n].uri));
			model.add(objNodes[n], rdfLabel, model.createLiteral(nodes[n].label));
			if (WITH_EXTRA)
			{
				if (Util.notBlank(nodes[n].descr)) model.add(objNodes[n], propHasDescr, nodes[n].descr);
				if (nodes[n].altLabels != null) model.add(objNodes[n], propHasAltLabels, makeLiteralList(model, nodes[n].altLabels));
				if (nodes[n].externalURLs != null) model.add(objNodes[n], propHasExternalURLs, makeLiteralList(model, nodes[n].externalURLs));
			}
			
			context.nodeToURI.put(System.identityHashCode(nodes[n]), uriNode);
		}	
	}
	
	// fill out the given assay; annotations are done by referring to a previously created OWL-style node
	private void populateAssay(SchemaContext context, DataObject.Assay assay, Model model) throws IOException
	{
		Property propSubClass = model.createProperty(ModelSchema.expandPrefix("rdfs:subClassOf"));
		Property rdfLabel = model.createProperty(ModelSchema.PFX_RDFS + "label");
		Property propHasAssayID = model.createProperty(ModelSchema.PFX_BAT + "hasAssayID");
		Property propHasUniqueID = model.createProperty(ModelSchema.PFX_BAT + "hasUniqueID");
		Property propHasText = model.createProperty(ModelSchema.PFX_BAT + "hasText");
		Property propHasSchema = model.createProperty(ModelSchema.PFX_BAT + "hasSchema");
		Property propHasAnnotation = model.createProperty(ModelSchema.PFX_BAT + "hasAnnotation");
		Property propHasTextLabel = model.createProperty(ModelSchema.PFX_BAT + "hasTextLabel");
		Property propHasAssignment = model.createProperty(ModelSchema.PFX_BAT + "hasAssignment");
	
		String uriAssay = context.nextURI();
		Resource objAssay = model.createResource(uriAssay);

		model.add(objAssay, propSubClass, model.createResource(ROOT_ASSAYS));
		
		String label = "assayID=" + assay.assayID;
		if (Util.notBlank(assay.uniqueID)) label += ", uniqueID=" + assay.uniqueID;
		model.add(objAssay, rdfLabel, label);
		
		model.add(objAssay, propHasAssayID, model.createTypedLiteral(assay.assayID));
		if (Util.notBlank(assay.uniqueID)) model.add(objAssay, propHasUniqueID, model.createLiteral(assay.uniqueID));
		if (Util.notBlank(assay.text)) model.add(objAssay, propHasText, model.createLiteral(assay.text));
		if (Util.notBlank(assay.schemaURI)) model.add(objAssay, propHasSchema, model.createResource(assay.schemaURI));
		// TODO: branches, duplication
		
		Schema schema = Common.getSchema(assay.schemaURI);
		if (schema == null) schema = Common.getSchemaCAT();
		SchemaDynamic schdyn = new SchemaDynamic(schema, assay.schemaBranches, assay.schemaDuplication);
		
		if (assay.annotations != null) for (DataObject.Annotation annot : assay.annotations)
		{
			SchemaDynamic.SubTemplate subt = schdyn.relativeAssignment(annot.propURI, annot.groupNest);
			if (subt == null) continue;
			SchemaTree tree = Common.obtainTree(subt.schema, annot.propURI, subt.groupNest);
			if (tree == null) continue;
			SchemaTree.Node node = tree.getNode(annot.valueURI);
			if (node == null) continue;
			String treeURI = context.nodeToURI.get(System.identityHashCode(node));
			if (treeURI == null) continue;
			model.add(objAssay, propHasAnnotation, model.createResource(treeURI));
		}
		
		if (assay.textLabels != null) for (DataObject.TextLabel txtlbl : assay.textLabels)
		{
			SchemaDynamic.SubTemplate subt = schdyn.relativeAssignment(txtlbl.propURI, txtlbl.groupNest);
			if (subt == null) continue;
			Schema.Assignment[] assnList = subt.schema.findAssignmentByProperty(txtlbl.propURI, subt.groupNest);
			if (assnList.length == 0) continue;
			String assnURI = context.assnToURI.get(System.identityHashCode(assnList[0]));
			if (assnURI == null) continue;
			Resource join = model.createResource();
			model.add(objAssay, propHasTextLabel, join);
			model.add(join, propHasAssignment, model.createResource(assnURI));
			model.add(join, rdfLabel, model.createLiteral(txtlbl.text));
		}
	}
	
	// convenience methods
	private RDFList makeURIList(Model model, String[] content)
	{
		RDFNode[] list = new RDFNode[content.length];
		for (int n = 0; n < list.length; n++) list[n] = model.createResource(content[n]);
		return model.createList(list);
	}
	private RDFList makeLiteralList(Model model, String[] content)
	{
		RDFNode[] list = new RDFNode[content.length];
		for (int n = 0; n < list.length; n++) list[n] = model.createLiteral(content[n]);
		return model.createList(list);
	}
}



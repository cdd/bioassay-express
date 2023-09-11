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
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

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
	Package up all of the provisional terms into a Turtle file.
*/

public class DownloadProvisional extends BaseServlet 
{
	private static final long serialVersionUID = 1L;
	
	private DataStore store = Common.getDataStore();
	
	public static final String ROOT_PROVISIONAL = ModelSchema.expandPrefix("bae:ProvisionalTerms");
	
	@Override
	protected void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		response.setContentType("text/turtle");
		
		Model model = buildModel();
		RDFDataMgr.write(response.getOutputStream(), model, RDFFormat.TURTLE);
	}

	// populates the model with all of the triples needed to describe the provisional terms
	public static void fillModelContent(Model model)
	{
		Resource resRoot = model.createResource(ROOT_PROVISIONAL);
		Property propSubClass = model.createProperty(ModelSchema.expandPrefix("rdfs:subClassOf"));
		Property propOntoParent = model.createProperty(ModelSchema.expandPrefix("bat:ontologyParent"));
		Property propLabel = model.createProperty(ModelSchema.expandPrefix("rdfs:label"));
		Property propDescr = model.createProperty(ModelSchema.expandPrefix("obo:IAO_0000115"));
		Property propExplan = model.createProperty(ModelSchema.expandPrefix("bat:hasExplanation"));
		Property propID = model.createProperty(ModelSchema.expandPrefix("bat:hasProvisionalID"));
		Property propProposer = model.createProperty(ModelSchema.expandPrefix("bat:hasProposerID"));
		Property propRole = model.createProperty(ModelSchema.expandPrefix("bat:hasRole"));
		Property propCreated = model.createProperty(ModelSchema.expandPrefix("bat:hasCreatedDate"));
		Property propModified = model.createProperty(ModelSchema.expandPrefix("bat:hasModifiedDate"));
		Property propRemapTo = model.createProperty(ModelSchema.expandPrefix("bat:remapTo"));
		Property propBridgeStatus = model.createProperty(ModelSchema.expandPrefix("bat:hasBridgeStatus"));
		Property propBridgeURL = model.createProperty(ModelSchema.expandPrefix("bat:hasBridgeURL"));
		Property propBridgeToken = model.createProperty(ModelSchema.expandPrefix("bat:hasBridgeToken"));
		
		DataStore store = Common.getDataStore();
		for (DataObject.Provisional prov : store.provisional().fetchAllTerms())
		{
			Resource subjProv = model.createResource(prov.uri);
			
			model.add(subjProv, propSubClass, resRoot);
			model.add(subjProv, propOntoParent, model.createResource(prov.parentURI));
			model.add(subjProv, propLabel, model.createLiteral(prov.label));
			model.add(subjProv, propDescr, model.createLiteral(prov.description));
			model.add(subjProv, propExplan, model.createLiteral(prov.explanation));
			model.add(model.createLiteralStatement(subjProv, propID, prov.provisionalID));
			if (Util.notBlank(prov.proposerID)) model.add(subjProv, propProposer, model.createLiteral(prov.proposerID));
			if (prov.role != null) model.add(subjProv, propRole, model.createLiteral(prov.role.toString()));
			if (prov.createdTime != null) model.add(subjProv, propCreated, model.createLiteral(prov.createdTime.toString()));
			if (prov.modifiedTime != null) model.add(subjProv, propModified, model.createLiteral(prov.modifiedTime.toString()));
			if (Util.notBlank(prov.remappedTo)) model.add(subjProv, propRemapTo, model.createLiteral(prov.remappedTo));
			if (Util.notBlank(prov.bridgeStatus)) model.add(subjProv, propBridgeStatus, model.createLiteral(prov.bridgeStatus));
			if (Util.notBlank(prov.bridgeURL)) model.add(subjProv, propBridgeURL, model.createLiteral(prov.bridgeURL));
			if (Util.notBlank(prov.bridgeToken)) model.add(subjProv, propBridgeToken, model.createLiteral(prov.bridgeToken));
		}	
	}

	// ------------ private methods ------------

	private Model buildModel()
	{
		Model model = ModelFactory.createDefaultModel();

		for (Map.Entry<String, String> entry : ModelSchema.getPrefixes().entrySet()) 
		{
			String pfx = entry.getKey(), url = entry.getValue();
			model.setNsPrefix(pfx.substring(0, pfx.indexOf(":")), url);
		}
		
		fillModelContent(model);

		return model;
	}
}



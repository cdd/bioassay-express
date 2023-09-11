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

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.rest.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.json.*;

/*
	Displays a particular schema for editing.
*/

public class SchemaEntry extends BaseServlet 
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------

	@Override
	protected void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		long assayID = 0;
		String uniqueID = null;
		String searchQuery = null;
		String template = null;
		String identifier = null;
		String keyword = null;
		String fullText = null;
		String globalKeyword = null;
		JSONObject postJSON = null;
		boolean forceEdit = false;
		
		for (Enumeration<String> e = request.getParameterNames(); e.hasMoreElements();)
		{
			String key = e.nextElement();
			if (key.equals("uniqueID")) uniqueID = request.getParameter(key);
			else if (key.equals("pubchemAID")) uniqueID = "pubchemAID:" + request.getParameter(key); // (legacy compatibility)
			else if (key.equals("assayID")) assayID = Long.valueOf(request.getParameter(key));
			else if (key.equals("IDENTIFIER")) identifier = request.getParameter(key);
			else if (key.equals("KEYWORD")) keyword = request.getParameter(key);
			else if (key.equals("globalKeyword")) globalKeyword = request.getParameter(key);
			else if (key.equals("FULLTEXT")) fullText = request.getParameter(key);
			else if (key.equals("searchQuery")) searchQuery = request.getParameter(key);
			else if (key.equals("template")) template = request.getParameter(key);
			else if (key.equals("edit")) forceEdit = request.getParameter(key).equalsIgnoreCase("true");
			else if (key.equals("data"))
			{
				try {postJSON = new JSONObject(new JSONTokener(request.getParameter(key)));}
				catch (JSONException jsEx) {throw new IOException("Could not parse \"data\" parameter as JSON.", jsEx);}
			}
		}

		PrintWriter wtr = response.getWriter();

		DataStore store = Common.getDataStore();
		JSONObject jsonAssay = null;
		DataObject.Assay assay = null;
		boolean isCloned = false; // means it was cloned from something already in the database
		boolean isFresh = false; // means it has been created from some external source
		if (postJSON != null)
		{
			if (!postJSON.has("annotations")) postJSON.put("annotations", new JSONArray());
			isCloned = postJSON.optBoolean("isCloned", false);
			isFresh = postJSON.optBoolean("isFresh", false);
			
			assay = AssayJSON.deserialiseAssay(postJSON);
		}
		else if (assayID > 0) assay = store.assay().getAssay(assayID);
		else if (uniqueID != null) assay = store.assay().getAssayFromUniqueID(uniqueID);

		Schema schema = assay != null ? Common.getSchema(assay.schemaURI) : Common.getSchemaCAT();
		if (schema == null) throw new IOException("Schema not found: " + (assay == null ? null : assay.schemaURI));
		
		if (assay != null) 
		{
			if (assay.text == null) assay.text = "";
			jsonAssay = AssayJSON.serialiseAssay(assay);
			if (isCloned) jsonAssay.put("isCloned", true);
			if (isFresh) jsonAssay.put("isFresh", true);
		}
		else
		{
			jsonAssay = new JSONObject();
			jsonAssay.put("schemaURI", (String)null /*schema.getSchemaPrefix()*/);
			jsonAssay.put("annotations", new JSONArray());
			jsonAssay.put("text", "");
			jsonAssay.put("holdingIDList", new JSONArray());
		}

		JSONObject jsonSchema = new JSONObject();
		DataObject.SchemaBranch[] branches = assay != null ? assay.schemaBranches : null;
		DataObject.SchemaDuplication[] dupl = assay != null ? assay.schemaDuplication : null;
		DescribeSchema.fillSchema(schema, branches, dupl, jsonSchema);

		JSONArray jsonHoldingSubmit = new JSONArray(), jsonHoldingDelete = new JSONArray();
		JSONArray jsonSearchQuery = new JSONArray(), jsonTemplate = new JSONArray();
		JSONArray jsonIdentifier = new JSONArray(), jsonKeyword = new JSONArray();
		JSONArray jsonFullText = new JSONArray(), jsonGlobalKeyword = new JSONArray();
		if (assay != null && assay.assayID > 0) for (long holdingID : store.holding().fetchForAssayID(assay.assayID))
		{
			DataObject.Holding holding = store.holding().getHolding(holdingID);
			if (holding.deleteFlag) jsonHoldingDelete.put(holdingID); else jsonHoldingSubmit.put(holdingID);
			// (NOTE: could provide more information than just the IDs of the two different types, but for now that's
			// all that the service needs)
		}
		
		if (searchQuery != null) jsonSearchQuery.put(searchQuery);
		if (template != null) jsonTemplate.put(template);
		if (identifier != null) jsonIdentifier.put(identifier);
		if (keyword != null) jsonKeyword.put(keyword);
		if (fullText != null) jsonFullText.put(fullText);
		if (globalKeyword != null) jsonGlobalKeyword.put(globalKeyword);

		JSONArray jsonForms = new JSONArray();
		for (EntryForms.Entry entry : Common.getForms().getEntries())
		{
			JSONObject json = new JSONObject();
			json.put("name", entry.name);
			json.put("priority", entry.priority);
			json.put("schemaURIList", entry.schemaURIList);
			json.put("sections", entry.sections);
			jsonForms.put(json);
		}
		
		String[] absenceTerms = Common.getParams().absenceTerms;
		if (Util.length(absenceTerms) == 0) absenceTerms = AssayUtil.ABSENCE_TERMS;
		JSONArray jsonAbsence = new JSONArray(absenceTerms);

		boolean canRequestProvisionals = Common.getConfiguration().getProvisional().baseURI != null;
		wtr.println("var schema = " + jsonSchema.toString() + ";");
		wtr.println("var assay = " + jsonAssay.toString() + ";");
		wtr.println("var identifier = " + jsonIdentifier.toString() + ";");
		wtr.println("var keyword = " + jsonKeyword.toString() + ";");
		wtr.println("var fullText = " + jsonFullText.toString() + ";");
		wtr.println("var globalKeyword = " + jsonGlobalKeyword.toString() + ";");
		wtr.println("var holdingBaySubmit = " + jsonHoldingSubmit.toString() + ";");
		wtr.println("var holdingBayDelete = " + jsonHoldingDelete.toString() + ";");
		wtr.println("var entryForms = " + jsonForms.toString() + ";");
		wtr.println("var forceEdit = " + forceEdit + ";");
		wtr.println("var canRequestProvisionals = " + canRequestProvisionals + ";");
		wtr.println("var searchQuery = " + jsonSearchQuery.toString() + ";");
		wtr.println("var template = " + jsonTemplate.toString() + ";");
		wtr.println("var absenceTerms = " + jsonAbsence.toString() + ";");
	}

	// ------------ private methods ------------

}



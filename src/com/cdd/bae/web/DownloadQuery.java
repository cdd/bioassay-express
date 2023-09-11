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
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;
import java.util.stream.*;
import java.util.zip.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.commons.lang3.*;
import org.json.*;

/*
	Download by query: takes a query string (see QueryAssay class) as its primary parameter, and selects assays accordingly.
	Can return a simple plain text list of IDs, or it can fetch and return the assays, as well as their corresponding compounds.
	
	Parameters:
		query={query string}
		assays={false/default: ID list; true: zip file with matched content}
		id={assayID/uniqueID/uniqueIDRaw}
		compounds={flag, default=false: whether to include an SDfile for each assay}
		
	NOTE that this can also be used as a way to download everything in the system, such as by:
	
		{baseURL}/servlet/DownloadQuery/results.zip&assays=true
	e.g.
		https://beta.bioassayexpress.com/servlet/DownloadQuery/results.zip&assays=true
*/

public class DownloadQuery extends BaseServlet 
{
	private static final long serialVersionUID = 1L;
	
	private DataStore store = Common.getDataStore();
	
	private enum IDType
	{
		ASSAYID,
		UNIQUEID,
		UNIQUEID_RAW
	}
	
	@Override
	protected void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		Schema schema = Common.getSchemaCAT(); // TODO: selection of template...
		Map<Long, DataObject.Assay> assayMap = new HashMap<>();

		String qstr = request.getParameter("query");
		String idstr = request.getParameter("id");		
		boolean withAssays = "true".equalsIgnoreCase(request.getParameter("assays"));
		boolean withCompounds = "true".equalsIgnoreCase(request.getParameter("compounds"));
		
		IDType idtype = null;
		if (idstr == null || idstr.equals("assayID")) idtype = IDType.ASSAYID;
		else if (idstr.equals("uniqueID")) idtype = IDType.UNIQUEID;
		else if (idstr.equals("uniqueIDRaw")) idtype = IDType.UNIQUEID_RAW;
		else throw new IOException("Invalid 'id' type: " + idstr);
		
		QueryAssay qa = qstr == null ? null : QueryAssay.parse(qstr);		
		for (long assayID : store.assay().fetchAssayIDCurated())
		{
			DataObject.Assay assay = store.assay().getAssay(assayID);
			boolean match = qa == null || qa.matchesAssay(assay);
			if (match) assayMap.put(assay.assayID, assay);
		}

		if (!withAssays)
			outputSimpleList(assayMap, response, idtype);
		else
			outputWholeData(schema, assayMap, response, withCompounds);
	}

	// ------------ private methods ------------

	private void outputSimpleList(Map<Long, DataObject.Assay> assayMap, HttpServletResponse response, IDType idtype) throws IOException
	{
		response.setContentType("text/plain");
		BufferedWriter wtr = new BufferedWriter(new OutputStreamWriter(response.getOutputStream()));
		
		if (idtype == IDType.ASSAYID)
		{
			Long[] idlist = assayMap.keySet().toArray(new Long[assayMap.size()]);
			Arrays.sort(idlist);
			for (long id : idlist) wtr.write(id + "\n");
		}
		else if (idtype == IDType.UNIQUEID)
		{
			List<String> uniqueID = assayMap.values().stream().map(assay -> assay.uniqueID)
													.filter(str -> str != null).collect(Collectors.toList());
			Collections.sort(uniqueID);
			for (String id : uniqueID) wtr.write(id + "\n");
		}
		else if (idtype == IDType.UNIQUEID_RAW)
		{
			Identifier ident = Common.getIdentifier();
			List<String> payloads = assayMap.values().stream().map(
				assay -> 
				{
					Identifier.UID uid = ident.parseKey(assay.uniqueID);
					return uid == null ? null : uid.id;
				}).filter(str -> str != null).collect(Collectors.toList());
			Collections.sort(payloads);
			for (String id : payloads) wtr.write(id + "\n");
		}
		
		wtr.close();
	}
	
	private void outputWholeData(Schema schema, Map<Long, DataObject.Assay> assayMap, HttpServletResponse response, boolean withCompounds) throws IOException
	{
		if (assayMap.size() == 0) throw new IOException("No assays match the query.");
		
		Long[] idlist = assayMap.keySet().toArray(new Long[assayMap.size()]);
		Arrays.sort(idlist);

		response.setContentType("application/zip");
		ZipOutputStream zip = new ZipOutputStream(response.getOutputStream());

		// first file is the list of assays
		zip.putNextEntry(new ZipEntry("list.txt"));
		StringBuilder buff = new StringBuilder();
		for (long id : idlist) buff.append(id + "\n");
		zip.write(buff.toString().getBytes());
		zip.closeEntry();
		
		// write out the JSON-formatted assays
		for (long id : idlist)
		{
			zip.putNextEntry(new ZipEntry("assay" + id + ".json"));
			JSONObject json = AssayJSON.serialiseAssay(assayMap.get(id));
			zip.write(json.toString().getBytes());
			zip.closeEntry();
		}
		
		// write out the schema/tree structure
		zip.putNextEntry(new ZipEntry("schema.json"));
		JSONObject json = null;
		try {json = formulateSchema(schema, assayMap);}
		catch (JSONException ex) {throw new IOException(ex);}
		zip.write(json.toString().getBytes());
		zip.closeEntry();		
		
		// create an SDfile for each assay
		if (withCompounds)
		{
			zip.putNextEntry(new ZipEntry("compounds.sdf"));
			BufferedWriter wtr = new BufferedWriter(new OutputStreamWriter(zip));
			emitCompounds(wtr, assayMap);
			wtr.flush();
			zip.closeEntry();
		}

		zip.close();
	}
	
	// provides labels for all of the terms used in the assays, and the tree structure there within
	private JSONObject formulateSchema(Schema schema, Map<Long, DataObject.Assay> assayMap)
	{
		JSONObject json = new JSONObject();
		json.put("schemaPrefix", schema.getSchemaPrefix());
		
		JSONArray assnlist = new JSONArray();
		json.put("assignments", assnlist);
		for (Schema.Assignment assn : schema.getRoot().flattenedAssignments()) assnlist.put(formulateAssignment(schema, assn, assayMap));

		return json;
	}
	
	// put together information about an assignment category: labels and tree
	private JSONObject formulateAssignment(Schema schema, Schema.Assignment assn, Map<Long, DataObject.Assay> assayMap)
	{
		JSONObject json = new JSONObject();
		
		json.put("propURI", assn.propURI);
		json.put("propLabel", assn.name);
		json.put("propDescr", assn.descr);
		
		SchemaTree tree = Common.obtainTree(schema, assn);
		JSONObject jsonTree = new JSONObject();
		
		for (DataObject.Assay assay : assayMap.values()) for (DataObject.Annotation annot : assay.annotations)
		{
			if (!annot.propURI.equals(assn.propURI)) continue;
			
			SchemaTree.Node node = tree.getNode(annot.valueURI);
			while (node != null)
			{
				if (jsonTree.has(node.uri)) break;
				
				JSONObject jsonNode = new JSONObject();
				jsonNode.put("label", node.label);
				if (node.parent != null) jsonNode.put("parent", node.parent.uri);
				jsonTree.put(node.uri, jsonNode);
				
				node = node.parent;
			}
		}
		json.put("tree", jsonTree);
		
		return json;
	}

	// pushes out an SDfile with each unique compound, and the assays that it affects
	private void emitCompounds(Writer wtr, Map<Long, DataObject.Assay> assayMap) throws IOException
	{
		class CompoundValues
		{
			long[] actives = new long[0], inactives = new long[0];
		}
		Map<Long, CompoundValues> values = new HashMap<>();
	
		// grab all compounds for all activity measurements; degenerate compounds are merged together
		final String[] MEASURE_TYPES = new String[]{DataMeasure.TYPE_ACTIVITY};
		for (long assayID : assayMap.keySet())
		{
			for (DataObject.Measurement measure : store.measure().getMeasurements(assayID, MEASURE_TYPES))
			{
				for (int n = 0; n < measure.compoundID.length; n++) if (measure.value[n] != null)
				{
					 CompoundValues cpd = values.get(measure.compoundID[n]);
					 if (cpd == null) values.put(measure.compoundID[n], cpd = new CompoundValues());
					 if (measure.value[n] > 0)
					 	cpd.actives = ArrayUtils.add(cpd.actives, assayID);
					 else
					 	cpd.inactives = ArrayUtils.add(cpd.inactives, assayID);
				}
			}
		}

		// push them out as a big long SDfile
		Long[] idlist = values.keySet().toArray(new Long[values.size()]);
		Arrays.sort(idlist);

		boolean first = true;
		for (long compoundID : idlist)
		{
			CompoundValues val = values.get(compoundID);
			DataObject.Compound cpd = store.compound().getCompound(compoundID);
			if (cpd == null) continue;
			
			String molfile = cpd.molfile;
			if (molfile == null) molfile = ""; 
			if (molfile.length() > 0 && !molfile.endsWith("\n")) molfile += "\n";
			
			wtr.write(molfile);
			
			wtr.write("> <compoundID>\n" + compoundID + "\n\n");
			
			if (cpd.pubchemCID > 0) wtr.write("> <PubChemCID>\n" + cpd.pubchemCID + "\n\n");
			if (cpd.pubchemSID > 0) wtr.write("> <PubChemSID>\n" + cpd.pubchemSID + "\n\n");
			
			if (val.actives.length > 0 || first)
			{
				wtr.write("> <actives>\n");
				for (int n = 0; n < val.actives.length; n++) wtr.write((n > 0 ? " " : "") + val.actives[n]);
				wtr.write("\n\n");
			}
			if (val.inactives.length > 0 || first)
			{
				wtr.write("> <inactives>\n");
				for (int n = 0; n < val.inactives.length; n++) wtr.write((n > 0 ? " " : "") + val.inactives[n]);
				wtr.write("\n\n");
			}
						
			wtr.write("$$$$\n");
						
			first = false;	
		}
	}
}



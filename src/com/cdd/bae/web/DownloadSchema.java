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
import java.util.zip.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.json.*;

/*
	Download schema
*/

public class DownloadSchema extends BaseServlet 
{
	private static final long serialVersionUID = 1L;
	
	private DataStore store = Common.getDataStore();
	
	@Override
	protected void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		boolean withTrees = "true".equalsIgnoreCase(request.getParameter("trees"));
		
		response.setContentType("application/zip");
		ZipOutputStream zip = new ZipOutputStream(response.getOutputStream());

		int idx = 0;
		for (Schema schema : Common.getAllSchemata()) outputSchema(zip, schema, ++idx, withTrees);
		for (Schema schema : Common.getBranchSchemata()) outputSchema(zip, schema, ++idx, withTrees);

		zip.close();

	}

	// ------------ private methods ------------

	// include the schema (and maybe the tree) in the outgoing ZIP file
	private void outputSchema(ZipOutputStream zip, Schema schema, int idx, boolean withTrees) throws IOException
	{
		StringBuilder buff = new StringBuilder();
		for (String bit : schema.getRoot().name.split(" "))
		{
			if (bit.length() == 0) continue;
			char[] chars = new char[bit.length()];
			bit.getChars(0, bit.length(), chars, 0);
			chars[0] = Character.toUpperCase(chars[0]);
			for (char ch : chars) if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) buff.append(ch);
		}
		
		String fnSchema = "schema_" + idx + "_" + buff + ".json";
		String fnTree = "tree_" + idx + "_" + buff + ".json";
		
		zip.putNextEntry(new ZipEntry(fnSchema));
		SchemaUtil.serialise(schema, SchemaUtil.SerialFormat.JSON, zip);
		zip.closeEntry();
		
		if (withTrees)
		{
			JSONArray json = new JSONArray();
			for (Schema.Assignment assn : schema.getRoot().flattenedAssignments())
			{
				JSONObject tree = prepareTree(schema, assn);
				if (tree != null) json.put(tree);
			}
			
			zip.putNextEntry(new ZipEntry(fnTree));
			zip.write(json.toString(2).getBytes());
			zip.closeEntry();
		}
	}
	
	// builds a JSON representation of the hierarchy for an assignment, or null if none
	private JSONObject prepareTree(Schema schema, Schema.Assignment assn)
	{
		SchemaTree tree = Common.obtainTree(schema, assn);
		if (tree == null) return null;
		
		JSONObject jsonTree = new JSONObject();
		jsonTree.put("name", assn.name);
		jsonTree.put("descr", assn.descr);
		jsonTree.put("propURI", assn.propURI);
		jsonTree.put("groupNest", assn.groupNest());
		jsonTree.put("locator", schema.locatorID(assn));
		JSONArray roots = new JSONArray();
		jsonTree.put("children", roots);

		SchemaTree.Node[] list = tree.getFlat();
		JSONObject[] local = new JSONObject[list.length];
		for (int n = 0; n < list.length; n++)
		{
			local[n] = new JSONObject();
			local[n].put("uri", list[n].uri);
			local[n].put("label", list[n].label);
			if (Util.notBlank(list[n].descr)) local[n].put("descr", list[n].descr);
			if (list[n].altLabels != null) local[n].put("altLabels", list[n].altLabels);
			if (list[n].externalURLs != null) local[n].put("externalURLs", list[n].externalURLs);
		
			int parent = list[n].parentIndex;
			if (parent < 0) 
			{
				roots.put(local[n]);
			}
			else 
			{
				JSONArray children = local[parent].optJSONArray("children");
				if (children == null) local[parent].put("children", children = new JSONArray());
				children.put(local[n]);
			}
		}
		
		return jsonTree;
	}
}



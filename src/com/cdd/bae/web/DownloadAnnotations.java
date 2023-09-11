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

import java.io.*;
import java.util.*;
import java.util.zip.*;

import javax.servlet.*;
import javax.servlet.http.*;

/*
	Download statistics about annotation terms, in simple text file format suitable for analysis by spreadsheet.
	
	Parameters:
		(TODO: schemaURI)
		propURI: optional URI; if not provided, fetches them all as a zip file
*/

public class DownloadAnnotations extends BaseServlet 
{
	private static final long serialVersionUID = 1L;

	@Override
	protected void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		String propURI = request.getParameter("propURI");
		
		String path = request.getRequestURI();
		int posSlash = path.lastIndexOf('/'), posDot = path.lastIndexOf('.');
		String suffix = posDot > 0 && posDot > posSlash ? path.substring(posDot + 1) : "";

		if (propURI == null)
		{
			if (!suffix.equals("zip")) throw new IOException("Getting all stats: filename must have .zip extension");
		}
		else
		{
			if (!suffix.equals("tsv")) throw new IOException("Getting all stats: filename must have .tsv extension");
		}

		Schema schema = Common.getSchemaCAT(); // TBD: configurable...	
		DataStore store = Common.getDataStore();
		Map<String, Map<String, Integer>> breakdown = store.assay().breakdownAssignments();
	
		if (propURI == null)
		{
			response.setContentType("application/octet-stream");
			
			try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream()))
			{
				for (Schema.Assignment assn : schema.getRoot().flattenedAssignments()) appendAssignment(zip, assn, schema, breakdown);
			}
		}
		else
		{
			response.setContentType("application/octet-stream");

			try (BufferedWriter wtr = new BufferedWriter(new OutputStreamWriter(response.getOutputStream())))
			{
				writeAssignment(wtr, propURI, schema, breakdown);
			}
		}
	}

	// emits the information about one assignment in the form of a tab-separated file
	private void writeAssignment(BufferedWriter wtr, String propURI, Schema schema, Map<String, Map<String, Integer>> breakdown) throws IOException
	{
		Schema.Assignment assn = schema.findAssignmentByProperty(propURI)[0];
		wtr.write(assn.name + "\t" + propURI + "\n");
		wtr.write("depth\tname\tURI\tcount\n");

		Map<String, Integer> counts = breakdown.get(propURI);
		
		SchemaTree.Node[] nodes = Common.obtainTree(schema, assn).getFlat();
		for (int n = 0; n < nodes.length; n++)
		{
			int num = counts == null ? 0 : counts.getOrDefault(nodes[n].uri, 0);
			wtr.write(nodes[n].depth + "\t" + nodes[n].label + "\t" + nodes[n].uri + "\t" + num + "\n");
		}
	}
	
	// adds a single assignment into a zipfile
	private void appendAssignment(ZipOutputStream zip, Schema.Assignment assn, 
								  Schema schema, Map<String, Map<String, Integer>> breakdown) throws IOException
	{
		// construct filename based on assn.name converted to camel case
		String fn = "annotation_";
		for (String bit : assn.name.split("\\s+")) if (bit.length() > 0)
		{
			fn += Character.toUpperCase(bit.charAt(0));
			fn += bit.substring(1);
		}
		fn += ".tsv";
		
		zip.putNextEntry(new ZipEntry(fn));

		BufferedWriter wtr = new BufferedWriter(new OutputStreamWriter(zip));
		writeAssignment(wtr, assn.propURI, schema, breakdown);
		wtr.flush();

		zip.closeEntry();
	}
}



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

package com.cdd.bae.rest;

import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.model.assocrules.*;
import com.cdd.bao.util.*;

import java.io.*;

import javax.servlet.http.*;

import org.json.*;

/*
	RDF: provides access to semantically-formatted snippets that represents the useful data stored within the BAE
	database. Can be viewed in human readable form, or native semantic (RDF/TTL).
*/

public class DownloadAssociationRules extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		// not required here, we override the process method
		return null;
	}

	@Override
	protected void process(HttpServletRequest request, HttpServletResponse response, boolean isPost) throws IOException
	{
		try
		{
			int minSupport = Util.safeInt(request.getParameter("minSupport"), 5);
			double confidence = Util.safeDouble(request.getParameter("minConfidence"), 0.8);
			boolean addLabels = "checked".equals(request.getParameter("addLabels"));

			ARModel model = ARModel.learn(minSupport, 3, confidence, true);
			Writer writer = new StringWriter();
			model.saveModel(writer, addLabels);
			prepareResponse(response, writer.toString(), ContentType.TSV, "assocRules.tsv");
		}
		catch (Exception ex)
		{
			if (!(ex instanceof IOException)) ex = new IOException(ex);
			logger.error("URL causing exception : {}", request.getRequestURL());
			logger.error("Query string : {}", request.getQueryString());
			throw (IOException)ex;
		}
	}
}

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
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.slf4j.*;
import org.json.*;

/*
	RESTBaseServlet provides general handling of REST API calls

	Acceptable inputs:
	 - JSON data structure in request body
	 - POST parameter
	 - GET parameter

	Services need to provide the methods processRequest to prepare the resulting JSON object.

	For validation, services can either override getRequiredParameter if only the presence of a parameter is checked
	or override validateParameter with a more thorough validation process.

	If additional processing of the response is required, override processResponse.
*/

public abstract class RESTBaseServlet extends HttpServlet
{
	private static final long serialVersionUID = 1L;

	public static final String RETURN_JSONARRAY = "returnJsonArray";

	public enum Status
	{
		DELETED("applied"), // was deleted directly
		HOLDING("holding"), // result of request placed into the holding bay (e.g. delete or submit)
		APPLIED("applied"), // was applied directly to the assay collection (e.g. submit)
		NOLOGIN("nologin"), // login credentials not valid (probably expired)
		DENIED("denied"), // login was recognised and explicitly denied access
		NONEXISTENT("nonexistent"); // assay not present

		private final String label;

		Status(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return this.label;
		}
	}

	public enum ContentType
	{
		HTML("text/html", ".html"),
		JSON("application/json", ".json"),
		TTL("text/turtle", ".ttl"),
		RDF("application/rdf+xml", ".rdf"),
		JSONLD("application/ld+json", ".jsonld"),
		CSV("text/csv", ".csv"),
		TSV("text/tab-separated-values", ".tsv"),
		UNKNOWN(null, null);

		private final String extension;
		private final String htmlContentType;

		ContentType(String contentType, String extension)
		{
			this.htmlContentType = contentType;
			this.extension = extension;
		}

		public static ContentType fromPath(String path)
		{
			for (ContentType ct : ContentType.values())
				if (ct.extension != null && path.endsWith(ct.extension)) return ct;
			return UNKNOWN;
		}

		@Override
		public String toString()
		{
			return this.htmlContentType;
		}
	}

	protected Logger logger = null;

	// ------------ abstract methods ------------

	// process the JSON input and returns JSON output
	protected abstract JSONObject processRequest(JSONObject input, Session session) throws RESTException;

	// ------------ implementation methods ------------

	@Override
	public void init(ServletConfig config) throws ServletException
	{
		logger = LoggerFactory.getLogger(this.getClass().getName());
		Common.bootstrap(config.getServletContext());
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		process(request, response, false);
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		process(request, response, true);
	}

	protected void process(HttpServletRequest request, HttpServletResponse response, boolean isPost) throws IOException
	{
		/*
			Preprocess the input data POST and application/json: request content is interpreted as
			JSON (POST and GET parameter are added to the JSON object POST and GET: an empty JSON
			object is created and POST and GET parameter added as fields
		*/
		try
		{
			if (isNotModified(request, response)) return;

			// convert the input into JSON
			JSONObject input = processInput(request, isPost);

			// validate the input parameters
			validateParameter(input);

			// do the actual work
			Session session = null;
			if (requireSession()) session = getSession(request);

			JSONObject result;
			if (hasPermission(session))
				result = processRequest(input, session);
			else
				result = statusResponse(false, Status.NOLOGIN);

			// use the processResponse for servlet specific customization (needs to happen before we write to response)
			processResponse(response);
			setETag(response);

			// and return the result in the response
			prepareJSONResponse(response, result);
		}
		catch (RESTException e)
		{
			prepareErrorResponse(request, response, e);
		}
		catch (Exception e)
		{
			prepareErrorResponse(request, response, new RESTException(e, "Unexpected error occured", RESTException.HTTPStatus.INTERNAL_SERVER_ERROR));
		}
	}

	// minimum implementation for parameter validation using the list of required parameters
	// override with a more thorough validation strategy
	protected void validateParameter(JSONObject input) throws RESTException
	{
		// check that all required parameters are found
		StringBuilder missing = new StringBuilder();
		for (String parameter : getRequiredParameter())
		{
			if (!input.has(parameter)) missing.append(" " + parameter);
		}
		if (missing.length() > 0)
			throw new RESTException("Missing parameters:" + missing.toString(), RESTException.HTTPStatus.BAD_REQUEST);
	}

	// validate the json input using the schema if available
	protected List<String> schemaValidation(JSONObject input)
	{
		JSONSchemaValidator validator = getSchemaValidator();
		if (validator == null || input == null) return new ArrayList<>();
		return validator.validate(input);
	}

	protected JSONSchemaValidator getSchemaValidator()
	{
		return null;
	}

	// returns the list of parameters that must be present in JSON input
	protected String[] getRequiredParameter()
	{
		return new String[0];
	}

	protected void processResponse(HttpServletResponse response)
	{
		// overwrite if additional changes to the response are required (e.g. set headers)
	}

	protected JSONObject statusResponse(boolean success, Status status)
	{
		JSONObject result = new JSONObject();
		result.put("success", success);
		result.put("status", status.toString());
		return result;
	}

	// custom routines for servlets that require session objects (e.g. for permission handling)
	protected boolean requireSession()
	{
		return false;
	}

	protected boolean hasPermission(Session session)
	{
		return true;
	}

	// ETag based caching - override getETag
	protected boolean isNotModified(HttpServletRequest request, HttpServletResponse response)
	{
		String requestETag = request.getHeader("If-None-Match");
		if (requestETag == null || !requestETag.equals(getETag())) return false;

		response.setStatus(RESTException.HTTPStatus.NOT_MODIFIED.code());
		return true;
	}

	protected void setETag(HttpServletResponse response)
	{
		String etag = getETag();
		if (etag == null) return;
		response.setHeader("ETag", etag);
	}

	protected String getETag()
	{
		return null;
	}

	// required for testing
	Session getSession(HttpServletRequest request)
	{
		return new LoginSupport(request).currentSession();
	}

	// ------------ protected methods ------------

	protected JSONObject processInput(HttpServletRequest request, boolean isPost) throws RESTException
	{
		JSONObject input;
		try
		{
			if (isPost && (request.getContentType().startsWith("application/json") || request.getContentType().startsWith("application/csp-report")))
				input = new JSONObject(new JSONTokener(request.getReader()));
			else
				input = new JSONObject();
		}
		catch (JSONException | IOException ex)
		{
			throw new RESTException(ex, "Your request must be in correct JSON format", RESTException.HTTPStatus.BAD_REQUEST);
		}

		for (Enumeration<String> e = request.getParameterNames(); e.hasMoreElements();)
		{
			String key = e.nextElement();
			input.put(key, request.getParameter(key));
		}
		return input;
	}

	protected void prepareJSONResponse(HttpServletResponse response, JSONObject result)
	{
		try
		{
			String content;
			if (result.has(RETURN_JSONARRAY))
				content = result.getJSONArray(RETURN_JSONARRAY).toString();
			else
				content = result.toString();
			prepareResponse(response, content, ContentType.JSON);
			response.setStatus(RESTException.HTTPStatus.OK.code());
		}
		catch (Exception e)
		{
			logger.error("Error preparing JSON response", e);
		}
	}
	
	protected void prepareResponse(HttpServletResponse response, String content, ContentType contentType) throws IOException
	{
		prepareResponse(response, content, contentType, null);
	}

	protected void prepareResponse(HttpServletResponse response, String content, 
			ContentType contentType, String filename) throws IOException
	{
		response.setContentType(contentType.toString());
		response.setCharacterEncoding(Util.UTF8);
		if (filename != null) 
			response.setHeader("Content-Disposition", "attachment;filename=\"" + filename + "\"");

		byte[] bytes = content.getBytes(Util.UTF8);
		response.setContentLength(bytes.length);
		try
		{
			OutputStream out = response.getOutputStream();
			out.write(bytes);
			out.flush();
		}
		catch (IOException e)
		{
			if (!e.getClass().getSimpleName().equals("ClientAbortException")) throw e;
			/* ignore this exception; user migrated away from page and result is no longer required */
		}
	}

	protected void prepareErrorResponse(HttpServletRequest request, HttpServletResponse response, RESTException e) throws IOException
	{
		logger.error("URL causing exception : {}", request.getRequestURL());
		logger.error("Query string : {}", request.getQueryString());
		logger.error(e.getMessage(), e);
		response.setStatus(e.getHTTPStatus());
		prepareJSONResponse(response, e.toJSON());
		// Only throw an exception if the configuration is not production!!
		if (!Common.isProduction()) throw new IOException(e);
	}
}

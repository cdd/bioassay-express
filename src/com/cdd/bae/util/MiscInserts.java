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

package com.cdd.bae.util;

import com.cdd.bae.config.*;
import com.cdd.bae.config.InitParams.*;
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.rest.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.*;

import javax.servlet.http.*;

import org.json.*;

/*
	Miscellaneous low-impact functions that provide serverside information to include in a JSP page.
*/

public class MiscInserts
{
	private static final String CSP_POLICY_ATTR_NAME = "BAE.CSPPolicy";

	// https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Security-Policy-Report-Only
	public static class CSPPolicy
	{
		public String header;
		public String nonce;

		/*
		default-src 'self';
		base-uri 'self';
		object-src 'none';
		script-src 'self' 'unsafe-eval' 'nonce-5b3896a8bb45439cbd435b14e5df8f30';
		style-src 'self' 'unsafe-inline' https://fonts.googleapis.com/css;
		font-src 'self' https://fonts.gstatic.com/;
		report-uri REST/cspReport;
		 */
		private CSPPolicy(HttpServletResponse response, String relativePath)
		{
			if (!Common.isVerboseDebug()) return;

			nonce = UUID.randomUUID().toString().replaceAll("-", "");
			header = "default-src 'self';";
			header += "base-uri 'self';";
			header += "object-src 'none';";
			header += "frame-src 'self' https://pubchem.ncbi.nlm.nih.gov/;";
			header += "script-src 'self' 'unsafe-inline' 'unsafe-eval' 'nonce-" + nonce + "';";
			header += "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com/css;";
			header += "font-src 'self' https://fonts.gstatic.com/;";
			String reportURI = "REST/cspReport";
			if (relativePath != null) reportURI = relativePath + "/" + reportURI;
			header += "report-uri " + reportURI + ";";
			response.setHeader("Content-Security-Policy-Report-Only", header);
		}
	}

	// create or retrieve existing Content Security Policy
	public static CSPPolicy getCSPPolicy(HttpServletRequest request, HttpServletResponse response)
	{
		return getCSPPolicy(request, response, null);
	}

	public static CSPPolicy getCSPPolicy(HttpServletRequest request, HttpServletResponse response, String relativePath)
	{
		CSPPolicy cspPolicy = (CSPPolicy)request.getAttribute(CSP_POLICY_ATTR_NAME);
		if (cspPolicy == null)
		{
			cspPolicy = new CSPPolicy(response, relativePath);
			request.setAttribute(CSP_POLICY_ATTR_NAME, cspPolicy);
		}
		return cspPolicy;
	}

	// ------------ constructor ------------

	private MiscInserts()
	{
	}

	// ------------ public methods ------------

	public static BuildInformation buildInformation()
	{
		return new BuildInformation();
	}

	public static String includeGoogleAnalytics()
	{
		GoogleAnalytics ga = Common.getParams().googleAnalytics;
		if (!ga.show()) return "";

		StringBuilder buff = new StringBuilder();
		buff.append("<!-- Global site tag (gtag.js) - Google Analytics -->\n");
		buff.append("<script async src='https://www.googletagmanager.com/gtag/js?id=" + ga.trackingID + "'></script>\n");
		buff.append("<script>\n");
		buff.append("  window.dataLayer = window.dataLayer || [];\n");
		buff.append("  function gtag(){dataLayer.push(arguments);}\n");
		buff.append("  gtag('js', new Date());\n");
		buff.append("  gtag('config', '" + ga.trackingID + "');\n");
		buff.append("</script>\n");
		return buff.toString();
	}

	// emit settings and references to auxiliary files within the header section, such as CSS
	public static String includeCommonHead(int depth)
	{
		String prefix = Util.rep("../", depth);
		StringBuilder buff = new StringBuilder();

		buff.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n");

		buff.append("<link href=\"https://fonts.googleapis.com/css?family=Source+Sans+Pro:400,600\" rel=\"stylesheet\" type=\"text/css\">\n");

		buff.append("<link href=\"" + prefix + "resource/images/MainIcon.png\" rel=\"icon\" type=\"image/png\">\n");

		buff.append("<link href=\"" + prefix + "css/bootstrap-3.3.7.min.css\" rel=\"stylesheet\">\n");
		buff.append("<link href=\"" + prefix + "css/main.css?s=" + Common.stamp() + "%>\" rel=\"stylesheet\" type=\"text/css\">\n");
		buff.append("<link href=\"" + prefix + "css/style.css?s=" + Common.stamp() + "\" rel=\"stylesheet\" type=\"text/css\">\n");

		return buff.toString();
	}

	public static String includeJSLibraries(int depth, String nonce)
	{
		String prefix = Util.rep("../", depth);
		StringBuilder buff = new StringBuilder();

		buff.append("<script src=\"" + prefix + "js/jquery-3.5.1.min.js\" type=\"text/javascript\"></script>\n");
		buff.append("<script src=\"" + prefix + "js/bootstrap-3.3.7.min.js\" type=\"text/javascript\"></script>\n");
		buff.append("<script src=\"" + prefix + "js/purify.min.js\" type=\"text/javascript\"></script>\n");
		buff.append("<script src=\"" + prefix + "js/webmolkit-build.js?s=" + Common.stamp() + "\" type=\"text/javascript\"></script>\n");
		buff.append("<script src=\"" + prefix + "js/bae-build.js?s=" + Common.stamp() + "\" type=\"text/javascript\"></script>\n");
		buff.append("<script nonce='" + nonce + "'>var bae = BioAssayExpress;</script>\n");
		return buff.toString();
	}

	// returns a list of most recently updated assays
	public static JSONObject recentCuration(int maxNum, HttpServletRequest request)
	{
		com.cdd.bae.util.LoginSupport login = new com.cdd.bae.util.LoginSupport(request);
		Session session = login.currentSession();
		return GetRecentCuration.getRecentCuration(maxNum, session == null ? null : session.curatorID);
	}

	// returns an HTML-ready suffix for # of items in the holding bay
	public static String holdingBayCount()
	{
		int num = Common.getDataStore().holding().countTotal();
		if (num == 0) return "";
		return " (" + num + ")";
	}

	// returns true if there's any point in showing the holding bay
	public static boolean showHoldingBay()
	{
		// (previously holding bay was only relevant if not-necessarily-trusted authentication is applicable; but more recently it i
		// used even with full trust, e.g. bulk mapping; but still disabled for read-only implementations)
		//return Common.getAuthentication().hasAnyOAuth();
		return Common.getAuthentication().hasAnyAccess();
	}

	// returns an HTML-ready suffix for # of term requests
	public static String provisionalCount()
	{
		int num = Common.getDataStore().provisional().countProvisionals();
		if (num == 0) return "";
		return " (" + num + ")";
	}

	// returns self-contained JavaScript-in-HTML to define the list of unique identifier types
	public static String embedIdentifiers()
	{
		return embedIdentifiers(null);
	}

	public static String embedIdentifiers(String nonce)
	{
		StringBuilder buff = new StringBuilder();

		if (nonce == null)
			buff.append("<script>\n");
		else
			buff.append("<script nonce='" + nonce + "'>\n");
		buff.append("var UNIQUE_IDENTIFIER_SOURCES = \n[\n");

		JSONArray list = new JSONArray();
		for (Identifier.Source src : Common.getIdentifier().getSources())
		{
			JSONObject obj = new JSONObject();
			obj.put("name", src.name);
			obj.put("shortName", src.shortName);
			obj.put("prefix", src.prefix);
			obj.put("baseURL", src.baseURL);
			obj.put("baseRegex", src.baseRegex);
			obj.put("defaultSchema", src.defaultSchema);
			list.put(obj);
		}
		for (int n = 0; n < list.length(); n++)
		{
			String txt = list.getJSONObject(n).toString().trim();
			buff.append("    " + txt + (n < list.length() - 1 ? "," : "") + "\n");
		}
		buff.append("];\n");

		buff.append("var PREFIX_MAP =\n");
		JSONObject pfxMap = new JSONObject();
		for (int n = 0; n < ModelSchema.prefixMap.length; n += 2)
		{
			pfxMap.put(ModelSchema.prefixMap[n], ModelSchema.prefixMap[n + 1]);
		}
		buff.append(pfxMap.toString(4) + ";\n");
		buff.append("</script>\n");

		return buff.toString();
	}

	// returns self-contained JavaScript-in-HTML with a concise summary of the available templates
	public static String embedTemplates()
	{
		return embedTemplates(null);
	}

	public static String embedTemplates(String nonce)
	{
		StringBuilder buff = new StringBuilder();

		if (nonce == null)
			buff.append("<script>\n");
		else
			buff.append("<script nonce='" + nonce + "'>\n");

		writeTemplates("SCHEMA_TEMPLATES", buff, Common.getAllSchemata());
		writeTemplates("BRANCH_TEMPLATES", buff, Common.getBranchSchemata());

		buff.append("</script>\n");

		return buff.toString();
	}

	// embed a more detailed description of all of the templates, each of which lists its assignments
	public static String embedTemplateDescriptions()
	{
		return embedTemplateDescriptions(null);
	}

	public static String embedTemplateDescriptions(String nonce)
	{
		StringBuilder buff = new StringBuilder();

		if (nonce == null)
			buff.append("<script>\n");
		else
			buff.append("<script nonce='" + nonce + "'>\n");

		buff.append("var SCHEMA_DESCRIPTIONS = \n[\n");

		JSONArray list = new JSONArray();
		for (Schema schema : Common.getAllSchemata())
		{
			JSONObject obj = new JSONObject();
			DescribeSchema.fillSchema(schema, null, null, obj); // !! TODO: fill in grafted/duplicated branches
			list.put(obj);
		}
		for (int n = 0; n < list.length(); n++)
		{
			String txt = list.getJSONObject(n).toString().trim();
			buff.append("    " + txt + (n < list.length() - 1 ? "," : "") + "\n");
		}

		buff.append("];\n</script>\n");

		return buff.toString();
	}

	// embeds information about compounds, namely whether or not there are any
	public static String embedCompoundStatus()
	{
		return embedCompoundStatus(null);
	}

	public static String embedCompoundStatus(String nonce)
	{
		StringBuilder buff = new StringBuilder();

		if (nonce == null)
			buff.append("<script>\n");
		else
			buff.append("<script nonce='" + nonce + "'>\n");
		boolean any = Common.getDataStore().measure().isAnything();
		buff.append("var COMPOUNDS_EXIST = " + any + ";\n");
		buff.append("</script>\n");

		return buff.toString();
	}

	// embeds information about configures ontolo bridges
	public static String embedOntoloBridges()
	{
		return embedOntoloBridges(null);
	}

	public static String embedOntoloBridges(String nonce)
	{
		StringBuilder buff = new StringBuilder();

		if (nonce == null)
			buff.append("<script>\n");
		else
			buff.append("<script nonce='" + nonce + "'>\n");

		JSONArray jsonBridges = new JSONArray();
		InitParams.OntoloBridge[] bridges = Common.getConfiguration().getOntoloBridges();
		if (bridges != null) for (InitParams.OntoloBridge b : bridges)
		{
			JSONObject json = new JSONObject();
			json.put("name", b.name);
			json.put("description", b.description);
			json.put("baseURL", b.baseURL);
			jsonBridges.put(json);
		}
		buff.append("var ONTOLOBRIDGES = " + jsonBridges + ";\n");

		buff.append("</script>\n");

		return buff.toString();
	}

	// embed information about users
	public static String embedUserInformation()
	{
		return embedUserInformation(null);
	}

	public static String embedUserInformation(String nonce)
	{
		StringBuilder buff = new StringBuilder();

		if (nonce == null)
			buff.append("<script>\n");
		else
			buff.append("<script nonce='" + nonce + "'>\n");

		String[] curatorIDs = Common.getDataStore().user().listUsers();
		Arrays.sort(curatorIDs);
		JSONArray jsonUsers = new JSONArray();
		for (String curatorID : curatorIDs)
		{
			DataObject.User user = Common.getDataStore().user().getUser(curatorID);
			JSONObject json = new JSONObject();
			json.put("service", user.serviceName);
			json.put("curatorID", user.curatorID);
			json.put("userID", user.userID);
			json.put("status", user.status == null ? DataUser.STATUS_DEFAULT : user.status);
			json.put("name", user.name);
			json.put("email", user.email);
			jsonUsers.put(json);
		}
		JSONObject json = new JSONObject();
		json.put("users", jsonUsers);
		buff.append("var USERS = " + json + ";\n");
		buff.append("</script>\n");
		return buff.toString();
	}

	// returns self-contained JavaScript-in-HTML with a concise summary of the available uriMappingPrefix
	public static String embedURIPatternMaps()
	{
		return embedURIPatternMaps(null);
	}

	public static String embedURIPatternMaps(String nonce)
	{
		StringBuilder buff = new StringBuilder();

		if (nonce == null)
			buff.append("<script>\n");
		else
			buff.append("<script nonce='" + nonce + "'>\n");

		JSONArray jsonURIPatternMaps = new JSONArray();
		InitParams.URIPatternMap[] patterns = Common.getConfiguration().getURIPatternMaps();
		for (InitParams.URIPatternMap pattern : patterns)
		{
			JSONObject json = new JSONObject();
			json.put("matchPrefix", pattern.matchPrefix);
			json.put("externalURL", pattern.externalURL);
			json.put("label", pattern.label);
			jsonURIPatternMaps.put(json);
		}
		buff.append("var URI_PATTERN_MAPS = " + jsonURIPatternMaps + ";\n");

		buff.append("</script>\n");

		return buff.toString();
	}

	// returns true if PubChem content is expected: can determine this by availability of identifiers
	public static boolean usesPubChem()
	{
		return Common.getIdentifier().getSource(Identifier.PUBCHEM_PREFIX) != null;
	}

	// parse assay request parameter to avoid XSS attacks
	public static String parseAssayParameter(String parameter)
	{
		if (parameter == null) return "null";
		Set<Integer> assayIDs = new HashSet<>();
		for (String assayID : decodeParameter(parameter).trim().split(","))
		{
			try
			{
				assayIDs.add(Integer.valueOf(assayID));
			}
			catch (NumberFormatException e)
			{ /* ignore this assaysID */ }
		}
		return "[" + assayIDs.stream().sorted().map(String::valueOf).collect(Collectors.joining(",")) + "]";
	}

	// parse query request parameter to avoid XSS attacks
	public static String parseQueryParameter(String parameter)
	{
		if (parameter == null) return "null";
		List<String> query = new ArrayList<>();
		for (String q : decodeParameter(parameter).trim().split(";"))
		{
			query.add(xssPrevention(q));
		}
		return "\"" + query.stream().collect(Collectors.joining(";")) + "\"";
	}

	// parse schema parameter
	public static String parseSchemaParameter(String parameter)
	{
		Schema schema = parameter == null || parameter.length() == 0 ? Common.getSchemaCAT() : Common.getSchema(parameter);
		JSONObject jsonSchema = new JSONObject();
		DescribeSchema.fillSchema(schema, null, null, jsonSchema); // !! TODO: grafted/duplicated branches
		return jsonSchema.toString();
	}

	// parse model code parameter
	public static String parseModelCodeParameter(String parameter)
	{
		if (parameter == null) return "null";
		String s = decodeParameter(parameter).trim();
		return "\"" + xssPrevention(s) + "\"";
	}

	// ------------ protected methods ------------

	protected static String xssPrevention(String s)
	{
		s = s.replace("&", "&amp;");
		s = s.replace("<", "&lt;");
		s = s.replace(">", "&gt;");
		s = s.replace("\"", "&quot;");
		s = s.replace("'", "&#x27;");
		s = s.replace("/", "&#x2F;");
		return s;
	}

	// ------------ private methods ------------

	// URI decode the parameter, return empty string on exception
	private static String decodeParameter(String parameter)
	{
		try
		{
			return URLDecoder.decode(parameter, "UTF-8");
		}
		catch (UnsupportedEncodingException e)
		{
			return "";
		}
	}

	// emit miniature definitions of a template list
	private static void writeTemplates(String varName, StringBuilder buff, Schema[] schemaList)
	{
		buff.append("var " + varName + " = \n[\n");

		JSONArray list = new JSONArray();
		for (Schema schema : schemaList)
		{
			JSONObject obj = new JSONObject();
			obj.put("title", schema.getRoot().name);
			obj.put("schemaURI", schema.getSchemaPrefix());
			if (schema.getBranchGroups() != null) obj.put("branchGroups", schema.getBranchGroups());
			list.put(obj);
		}
		for (int n = 0; n < list.length(); n++)
		{
			String txt = list.getJSONObject(n).toString().trim();
			buff.append("    " + txt + (n < list.length() - 1 ? "," : "") + "\n");
		}

		buff.append("];\n");
	}

}

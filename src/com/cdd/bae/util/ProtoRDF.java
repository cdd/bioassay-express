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

import static com.cdd.bao.template.ModelSchema.*;

import com.cdd.bae.rest.RESTBaseServlet.*;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

import org.apache.commons.text.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;

/*
	Stores a list of proto-RDF information (en route to triples) that can be rendered as HTML, RDF or TTL.
*/

public class ProtoRDF 
{	
	public static final class Node
	{
		public String propURI = null, propLabel = null; // null for roots
		public String valueURI = null, valueLabel = null;
		public String[] valueURIList = null, valueLabelList = null;
		public List<Node> children = new ArrayList<>();
		public Node parent = null;
		public Model model = null;
		
		// for "root" nodes: they're not children of anything, so don't have property connectors
		public static Node root(String valueURI, String valueLabel)
		{
			Node node = new Node();
			node.valueURI = normaliseURI(valueURI);
			node.valueLabel = valueLabel;
			return node;
		}
		// for child nodes: they are connected to their parent (subject) via the property and value
		public static Node property(String propURI, String propLabel, String valueURI, String valueLabel)
		{
			Node node = new Node();
			node.propURI = normaliseURI(propURI);
			node.propLabel = propLabel;
			node.valueURI = normaliseURI(valueURI);
			node.valueLabel = valueLabel;
			return node;
		}
		// to add a parent to a root node
		public static Node property(String propURI, String propLabel, Node node)
		{
			node.propURI = normaliseURI(propURI);
			node.propLabel = propLabel;
			return node;
		}
		// for text-only content
		public static Node literal(String propURI, String propLabel, String valueLabel)
		{
			Node node = new Node();
			node.propURI = normaliseURI(propURI);
			node.propLabel = propLabel;
			node.valueLabel = valueLabel;
			return node;
		}
		// for "blank" nodes
		public static Node placeholder(String propURI, String propLabel)
		{
			Node node = new Node();
			node.propURI = normaliseURI(propURI);
			node.propLabel = propLabel;
			return node;
		}
		// for nodes that contain just a value payload
		public static Node valueList(String propURI, String propLabel, String[] valueURIList, String[] valueLabelList)
		{
			Node node = new Node();
			node.propURI = normaliseURI(propURI);
			node.propLabel = propLabel;
			node.valueURIList = new String[valueURIList.length];
			for (int n = 0; n < valueURIList.length; n++) node.valueURIList[n] = normaliseURI(valueURIList[n]);
			node.valueLabelList = valueLabelList;
			return node;
		}
		// child nodes with embedded models: in this case, the valueURI is expected to be present in the model
		// the nodes in the model will absorbed into the output
		public static Node model(String propURI, String propLabel, String valueURI, Model model)
		{
			Node node = new Node();
			node.propURI = normaliseURI(propURI);
			node.propLabel = propLabel;
			node.valueURI = normaliseURI(valueURI);
			node.model = model;
			return node;
		}
		public Node append(Node node)
		{
			node.parent = this;
			children.add(node);
			return node;
		}		
	}

	private String baseURI;
	private Map<String, String> rdfPrefixes;
	private ContentType fmt;
	private int version;
	private boolean includeDownload;

	// ------------ public methods ------------

	public ProtoRDF(String baseURI, Map<String, String> rdfPrefixes, ContentType fmt, int version, boolean includeDownload)
	{
		this.baseURI = baseURI;
		this.rdfPrefixes = rdfPrefixes;
		this.fmt = fmt;
		this.version = version;
		this.includeDownload = includeDownload;
	}
	
	public String render(Node root) throws IOException		
	{
		try
		{
			if (fmt == ContentType.HTML)
			{
				// includes the "download all" link only if the user has authenticated; the actual download isn't necessarily constrained,
				// but we don't want to encourage rando web surfers to grabbing everything just because they can
				return renderHTML(baseURI, root, includeDownload);
			}
			else
			{
				Model model = ModelFactory.createDefaultModel();
				
				for (Map.Entry<String, String> e : rdfPrefixes.entrySet()) 
				{
					String pfx = e.getKey();
					int pos = pfx.indexOf(':');
					if (pos >= 0) pfx = pfx.substring(0, pos);
					model.setNsPrefix(pfx, e.getValue());
				}
				populateModel(root, model);
				
				StringWriter sw = new StringWriter();
				if (fmt == ContentType.TTL) RDFDataMgr.write(sw, model, RDFFormat.TURTLE_PRETTY);
				else if (fmt == ContentType.RDF) RDFDataMgr.write(sw, model, RDFFormat.RDFXML_PRETTY);
				else if (fmt == ContentType.JSONLD) RDFDataMgr.write(sw, model, RDFFormat.JSONLD_PRETTY);
				return sw.toString();
			}
		}
		//catch (IOException ex) {throw ex;}
		catch (Exception ex) {throw new IOException("RDF creation error", ex);}
	}
	
	public static void renderModel(Node root, Model model)
	{
		ProtoRDF proto = new ProtoRDF(null, null, ContentType.TTL, 0, false);
		proto.populateModel(root, model);
	}
	
	// removes artifacts from URIs that are expressed in long-form
	public static String normaliseURI(String uri)
	{
		if (uri == null) return uri;
		try 
		{
			if (uri.startsWith("http://") || uri.startsWith("https://")) return new URI(uri).normalize().toString();
		}
		catch (URISyntaxException ex) {} // silent fail
		return uri;
	}		
	
	// ------------ private methods ------------
		
	private String renderHTML(String baseURI, Node document, boolean includeDownload)
	{
		StringBuilder html = new StringBuilder();
		
		html.append("<html>\n<head>\n");
		html.append("<title>" + document.valueLabel + "</title>\n");
		html.append("<style>td {vertical-align: top;}</style>\n");
		html.append("</head>\n<body>\n");
		
		html.append("<h1>");
		appendLink(html, document.valueURI, document.valueLabel);
		html.append("</h1>\n");
		
		String[] split = document.valueURI.split("\\?");
		String url = split[0], query = split.length > 1 ? "?" + split[1] : "";
		
		html.append("<p>\n");
		appendLink(html, url + ".ttl" + query, "Turtle");
		appendLink(html, url + ".rdf" + query, "RDF");
		appendLink(html, url + ".jsonld" + query, "JSON-LD");
		html.append("</p>\n");
		
		html.append("<hr>\n");
		
		Node root = document;
		while (root.parent != null) root = root.parent;
		
		appendNodeLink(baseURI, html, root.valueURI, root.valueLabel);
		
		renderHTMLNodes(baseURI, html, root);
		
		if (includeDownload)
		{
			html.append("<p>Download");
		
			SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd");
			
			String assayFN = "bioassayexpress_all_" + fmt.format(new Date()) + ".zip";
			String linkAssayURL = "../../servlet/DownloadQuery/" + assayFN + "?assays=true";
			html.append(" <a href=\"" + linkAssayURL + "\"><i>all</i> assay data</a>");
			
			String schemaFN = "bioassayexpress_schema_" + fmt.format(new Date()) + ".zip";
			String linkSchemaURL = "../../servlet/DownloadSchema/" + schemaFN + "?trees=true";
			html.append(", <a href=\"" + linkSchemaURL + "\"><i>all</i> schema data</a>");
			
			String provFN = "bioassayexpress_provisional_" + fmt.format(new Date()) + ".ttl";
			String linkProvURL = "../../servlet/DownloadProvisional/" + provFN;
			html.append(", <a href=\"" + linkProvURL + "\"><i>all</i> provisional terms</a>");
			
			String everythingFN = "bioassayexpress_everything_" + fmt.format(new Date()) + ".ttl.gz";
			String linkEverythingURL = "../../servlet/DownloadEverything/" + everythingFN;
			html.append(", <a href=\"" + linkEverythingURL + "\"><i>all</i> content</a>");

			html.append("</p>");
		}
		
		html.append("</body>\n</html>\n");
		
		return html.toString();
	}
	
	private String renderHTMLerror(String baseURI, String message)
	{
		StringBuilder html = new StringBuilder();
		
		html.append("<html>\n<head>\n");
		html.append("<title>Semantic Web/REST</title>\n");
		html.append("<style>td {vertical-align: top;}</style>\n");
		html.append("</head>\n<body>\n");
		
		html.append("<h1>Export error (Semantic Web/REST)</h1>\n");
		html.append("<p>" + message + "</p>\n");
		html.append("<p><a href='" + baseURI + "/all'>Back to 'Semantic Web/REST'</a></p>");
		html.append("</body>\n</html>\n");
		
		return html.toString();
	}
	
	private void renderHTMLNodes(String baseURI, StringBuilder html, Node parent)
	{
		if (parent.children.isEmpty()) return;
		
		html.append("<p><table>\n");
		
		for (int n = 0; n < parent.children.size(); n++)
		{
			Node node = parent.children.get(n);
			html.append("<tr><td>\n");
			if (n == 0 || !node.propURI.equals(parent.children.get(n - 1).propURI) || !parent.children.get(n - 1).children.isEmpty()) 
				appendNodeLink(baseURI, html, node.propURI, node.propLabel);

			html.append("</td><td>\n");
			
			boolean isBlank = node.valueURI == null && node.valueLabel == null;

			if (node.model != null)
			{
				StringWriter sw = new StringWriter();
				RDFDataMgr.write(sw, node.model, RDFFormat.TURTLE);
				html.append("<pre>");
				html.append(StringEscapeUtils.escapeHtml4(sw.toString()));
				html.append("</pre>\n");
			}
			else if (node.valueURIList != null)
			{
				html.append("[");
				for (int i = 0; i < node.valueURIList.length; i++)
				{
					if (i > 0) html.append(", ");
					html.append("<a href=\"" + node.valueURIList[i] + "\">");
					html.append(node.valueLabelList[i] != null ? node.valueLabelList[i] : node.valueURIList[i]);
					html.append("</a>");
				}
				html.append("]");
			}
			else if (!isBlank)
				appendNodeLink(baseURI, html, node.valueURI, node.valueLabel);
			else // isBlank
				renderHTMLNodes(baseURI, html, node);
			
			html.append("</td></tr>");

			if (!isBlank && !node.children.isEmpty())
			{
				html.append("<tr><td></td><td>\n");
				renderHTMLNodes(baseURI, html, node);
				html.append("</td></tr>\n");
			}
		}
		
		html.append("</table></p>\n");
	}
	
	// if possible add a link, otherwise add the underlined label
	private static void appendNodeLink(String baseURI, StringBuilder html, String uri, String label)
	{
		if (uri == null)
			html.append(label != null ? label : uri);
		else
		{
			int nextSlash = baseURI.indexOf('/', 8);
			String rootURL = nextSlash > 0 ? baseURI.substring(0, nextSlash + 1) : baseURI;
			boolean isLink = uri.startsWith(rootURL);
			
			html.append("<nobr>");
			if (isLink)
				appendLink(html, uri, label);
			else
				html.append("<b>" + (label != null ? label : uri) + "</b>");
			html.append("</nobr>\n");
		}
	}
	
	// append a HTML to the string buffer
	private static void appendLink(StringBuilder html, String uri, String label)
	{
		html.append("<a href=\"" + uri + "\">");
		html.append(label != null ? label : uri);
		html.append("</a> ");
	}
	
	// instantiate the hierarchy as a set of triples
	private void populateModel(Node document, Model model)
	{
		Property rdfLabel = model.createProperty(PFX_RDFS + "label");
		
		Node root = document;
		while (root.parent != null) root = root.parent;
		
		Resource value = model.createResource(root.valueURI);
		if (root.valueLabel != null) model.add(value, rdfLabel, root.valueLabel);
		
		for (Node node : root.children) appendNode(model, value, node);
	}
	
	private void appendNode(Model model, Resource resParent, Node node)
	{
		Property prop = model.createProperty(node.propURI);
		Resource value = null;
		
		if (node.model != null)
		{
			value = model.createResource(node.valueURI);
			model.add(resParent, prop, value);
			model.add(node.model);
		}
		else if (node.valueURIList != null)
		{
			RDFNode[] list = new RDFNode[node.valueURIList.length];
			for (int n = 0; n < node.valueURIList.length; n++)
				list[n] = model.createResource(node.valueURIList[n]);
			model.add(resParent, prop, model.createList(list));
		}		
		else if (node.valueURI != null)
		{
			value = model.createResource(node.valueURI);
			model.add(resParent, prop, value);
			Property rdfLabel = model.createProperty(PFX_RDFS + "label");
			if (node.valueLabel != null) model.add(value, rdfLabel, node.valueLabel);
		}
		else if (node.valueLabel != null)
		{
			model.add(resParent, prop, model.createLiteral(node.valueLabel));
		}
		else
		{
			value = model.createResource();
			model.add(resParent, prop, value);
		}

		if (value != null) for (Node child : node.children) appendNode(model, value, child);
	}
}

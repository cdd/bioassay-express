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
import com.cdd.bae.data.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;
import java.util.stream.*;

import org.apache.commons.lang3.*;
import org.apache.commons.text.StringEscapeUtils;
import org.json.*;;

/*
	Takes a boilerplate definition and applies it to assay details. Once it is complete, there should be some nice text.
*/

public class BoilerplateScript
{
	private Transliteration.Boilerplate boiler;
	private Schema schema;
	private DataObject.Assay assay;
	private List<String> ignoreAnnotations = Arrays.asList(AssayUtil.URI_NOTAPPLICABLE);

	private StringBuffer html = new StringBuffer();

	// ------------ public methods ------------
	
	public BoilerplateScript(Transliteration.Boilerplate boiler, Schema schema, DataObject.Assay assay)
	{
		this.boiler = boiler;
		this.schema = schema;
		this.assay = assay;
	}

	// call this prior to accessing results
	public void inscribe() throws DetailException
	{
		if (boiler != null) processContent(boiler.content);
	}

	public String getHTML()
	{
		return html.toString();
	}

	public void reset()
	{
		html = new StringBuffer();
	}

	// ------------ private methods ------------

	// run down a list of content items and spool them all out
	protected void processContent(JSONArray content) throws DetailException
	{
		for (int n = 0; n < content.length(); n++)
		{
			Object obj = content.get(n);
			if (obj instanceof String) html.append((String)obj);
			else if (obj instanceof JSONObject) processObject((JSONObject)obj);
			else throw new DetailException("Boilerplate content must consist of strings or objects.");
		}
	}

	// given an embedded JSON object, presumes that it must contain a higher level instruction of some kind
	protected void processObject(JSONObject json) throws DetailException
	{
		if (json.has("term")) processTerm(json);
		else if (json.has("terms")) processMultipleTerm(json);
		else if (json.has("field")) processField(json);
		else if (json.has("ifany")) processIfAny(json);
		else if (json.has("ifvalue")) processIfValue(json, "ifvalue", false);
		else if (json.has("ifbranch")) processIfValue(json, "ifbranch", true);
		else throw new DetailException("Misunderstood boilerplate item: " + json);
	}

	// unpacks a "term" or "terms" and fills in everything according to display preferences
	protected void processTerm(JSONObject json) throws DetailException
	{
		Schema.Assignment[] assnList = extractAssignments(json.get("term"));
		processAssignments(assnList, json);
	}
	private void processMultipleTerm(JSONObject json) throws DetailException
	{
		JSONArray list = json.getJSONArray("terms");
		List<Schema.Assignment> assnList = new ArrayList<>();
		for (int n = 0; n < list.length(); n++)
		{
			skip: for (Schema.Assignment assn : extractAssignments(list.get(n)))
			{
				for (Schema.Assignment look : assnList) if (look.equals(assn)) continue skip;
				assnList.add(assn);
			}
		}

		processAssignments(assnList.toArray(new Schema.Assignment[assnList.size()]), json);
	}

	// processes all of the terms for a given assignment list
	private void processAssignments(Schema.Assignment[] assnList, JSONObject json)
	{
		List<String> terms = new ArrayList<>();
		for (DataObject.Annotation annot : matchAnnotations(assnList))
		{
			String valueLabel = Common.getCustomName(schema, annot.propURI, annot.groupNest, annot.valueURI);
			if (valueLabel == null) valueLabel = Common.getOntoValues().getLabel(annot.valueURI);
			terms.add(valueLabel);
		}
		for (DataObject.TextLabel label : matchTextLabels(assnList)) terms.add(label.text);
		renderTerms(terms, json);
	}

	// add information from the assay object
	private void processField(JSONObject json) throws DetailException
	{
		String field = json.optString("field", "");

		String label = null;
		if (field.equals("uniqueID"))
		{
			Identifier.UID uid = Common.getIdentifier().parseKey(assay.uniqueID);
			if (uid != null) label = uid.source.shortName + uid.id; else label = "?";
		}
		else if (field.equals("assayID")) label = String.valueOf(assay.assayID);
		else throw new DetailException("Unknown field '" + field + "'");

		html.append(StringEscapeUtils.escapeHtml4(label));
	}

	// executes two possibilities: if an assignment has any terms, process the first block, otherwise process the second
	protected void processIfAny(JSONObject json) throws DetailException
	{
		Schema.Assignment[] assnList = parseCondition(json.get("ifany"));
		DataObject.Annotation[] annotList = matchAnnotations(assnList);
		DataObject.TextLabel[] labelList = matchTextLabels(assnList);

		String fld = annotList.length > 0 || labelList.length > 0 ? "then" : "else";
		if (!json.has(fld)) return;

		Object obj = json.get(fld);
		if (obj instanceof JSONArray) processContent((JSONArray)obj);
		else if (obj instanceof JSONObject) processObject((JSONObject)obj);
		else if (obj instanceof String) html.append((String)obj);
		else throw new DetailException("Incompatible type " + obj.getClass());
	}

	// executes two possibilities, depending on whether the term has a certain value; format is [valueURI, propURI, groupNest...]
	private void processIfValue(JSONObject json, String action, boolean branch) throws DetailException
	{
		JSONArray param = json.getJSONArray(action);
		String valueURI = ModelSchema.expandPrefix(param.getString(0));
		JSONArray term = new JSONArray();
		for (int n = 1; n < param.length(); n++) term.put(param.getString(n));
		Schema.Assignment[] assnList = extractAssignments(term);
		DataObject.Annotation[] annotList = matchAnnotations(assnList);

		boolean matched = false;
		outer: for (DataObject.Annotation annot : annotList)
		{
			if (valueURI.equals(annot.valueURI)) {matched = true; break;}

			if (!branch) continue;

			// peruse the whole branch, see if it's in there anywhere
			SchemaTree tree = Common.obtainTree(schema, annot.propURI, annot.groupNest);
			if (tree == null) continue;
			SchemaTree.Node node = tree.getNode(valueURI);
			if (node == null) continue;
			List<SchemaTree.Node> stack = new ArrayList<>();
			stack.add(node);
			while (stack.size() > 0)
			{
				node = stack.remove(0);
				if (node.uri.equals(annot.valueURI)) {matched = true; break outer;}
				stack.addAll(node.children);
			}
		}

		String fld = matched ? "then" : "else";
		if (!json.has(fld)) return;

		Object obj = json.get(fld);
		if (obj instanceof JSONArray) processContent((JSONArray)obj);
		else if (obj instanceof JSONObject) processObject((JSONObject)obj);
		else if (obj instanceof String) html.append((String)obj);
	}
	
	protected Schema.Assignment[] parseCondition(Object obj) throws DetailException
	{
		if (obj instanceof String) return extractAssignments(obj);
		JSONArray arr = (JSONArray)obj;
		if (arr.get(0) instanceof String)
		{
			return extractAssignments(arr);
		}
		
		Schema.Assignment[] assignments = new Schema.Assignment[0];
		for (int n = 0; n < arr.length(); n++)
		{
			assignments = ArrayUtils.addAll(assignments, extractAssignments(arr.get(n)));
		}
		return assignments;
	}

	// given a JSON spec (must be string or array), finds any matching assignments; if the group nest is underspecified, may end up
	// matching more than wanted
	protected Schema.Assignment[] extractAssignments(Object obj) throws DetailException
	{
		String propURI = null;
		String[] groupNest = null;

		if (obj instanceof String) propURI = ModelSchema.expandPrefix((String)obj);
		else if (obj instanceof JSONArray)
		{
			String[] bits = ((JSONArray)obj).toStringArray();
			if (bits.length > 0) propURI = ModelSchema.expandPrefix(bits[0]);
			if (bits.length > 1)
			{
				groupNest = ArrayUtils.remove(bits, 0);
				for (int n = 0; n < groupNest.length; n++) groupNest[n] = ModelSchema.expandPrefix(groupNest[n]);
			}
		}
		else throw new DetailException("Terms must be specified as a string or array");

		List<Schema.Assignment> assnList = new ArrayList<>();
		if (propURI != null) 
			for (Schema.Assignment assn : schema.findAssignmentByProperty(propURI))
		{
			if (Schema.compatibleGroupNest(assn.groupNest(), groupNest)) assnList.add(assn);
		}
		return assnList.toArray(new Schema.Assignment[assnList.size()]);
	}

	// return all of the annotations/labels that correspond to any of the given assignments
	protected DataObject.Annotation[] matchAnnotations(Schema.Assignment[] assnList)
	{
		List<DataObject.Annotation> annotList = new ArrayList<>();
		for (DataObject.Annotation annot : assay.annotations)
			for (Schema.Assignment assn : assnList)
			{
				if (Schema.compatiblePropGroupNest(annot.propURI, annot.groupNest, assn.propURI, assn.groupNest()))
				{
					annotList.add(annot);
					break;
				}
			}
		annotList = annotList.stream().filter(annot -> !ignoreAnnotations.contains(annot.valueURI)).collect(Collectors.toList());
		annotList.sort((a1, a2) ->
		{
			String a1Label = Common.getCustomName(schema, a1.propURI, a1.groupNest, a1.valueURI);
			if (a1Label == null) a1Label = Common.getOntoValues().getLabel(a1.valueURI);

			String a2Label = Common.getCustomName(schema, a2.propURI, a2.groupNest, a2.valueURI);
			if (a2Label == null) a2Label = Common.getOntoValues().getLabel(a2.valueURI);

			int cmp = Util.safeString(a1Label).compareTo(Util.safeString(a2Label));
			return cmp;
		});
		return annotList.toArray(new DataObject.Annotation[annotList.size()]);
	}
	protected DataObject.TextLabel[] matchTextLabels(Schema.Assignment[] assnList)
	{
		List<DataObject.TextLabel> labelList = new ArrayList<>();
		for (DataObject.TextLabel annot : assay.textLabels) 
			for (Schema.Assignment assn : assnList)
			{
				if (Schema.compatiblePropGroupNest(annot.propURI, annot.groupNest, assn.propURI, assn.groupNest()))
				{
					labelList.add(annot);
					break;
				}
			}
		labelList.sort((t1, t2) -> Util.safeString(t1.text).compareTo(t2.text));
		return labelList.toArray(new DataObject.TextLabel[labelList.size()]);
	}

	// processes all of the terms for a given assignment list
	protected void renderTerms(List<String> terms, JSONObject json)
	{
		String style = json.optString("style", "");
		String sep = json.optString("sep", "grammar");
		String article = json.optString("article", "");
		String empty = json.optString("empty", "");
		boolean plural = json.optBoolean("plural", false);
		boolean capitalise = json.optBoolean("capitalize", false);

		if (terms.isEmpty())
		{
			html.append(empty);
			return;
		}

		// replace empty terms with ? and pluralize if requested
		Stream<String> termsStream = terms.stream().map(term -> Util.isBlank(term) ? "?" : term);
		if (plural) termsStream = termsStream.map(term -> term + (term.endsWith("s") ? "es" : "s"));
		terms = termsStream.collect(Collectors.toList());

		// Concatenate the terms to get the content
		String content;
		if (terms.size() == 1) content = terms.get(0);
		else if (sep.equals("grammar"))
		{
			String firsts = String.join(", ", terms.subList(0, terms.size() - 1));
			String last = terms.get(terms.size() - 1);
			content = String.join(" and ", Arrays.asList(firsts, last));
		}
		else content = String.join(sep, terms);

		// add an article if requested
		if (article.equals("definite")) content = "the " + content;
		else if (article.equals("indefinite"))
		{
			char first = Character.toLowerCase(content.charAt(0));
			String indef = first == 'a' || first == 'e' || first == 'i' || first == 'o' || first == 'u' || first == 'y' ? "an " : "a ";
			content = indef + content;
		}

		// capitalize if requested
		if (capitalise) content = Character.toUpperCase(content.charAt(0)) + content.substring(1);

		// finally escape for Html4
		content = StringEscapeUtils.escapeHtml4(content);

		// and wrap in style
		if (style.equals("bold")) html.append("<b>").append(content).append("</b>");
		else if (style.equals("italic")) html.append("<i>").append(content).append("</i>");
		else if (style.equals("tinted")) html.append("<font class=\"tinted\">").append(content).append("</font>");
		else html.append(content);
	}
	
	protected DataObject.Assay getAssay()
	{
		return assay;
	}
}

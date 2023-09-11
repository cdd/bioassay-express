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

import com.cdd.bae.config.*;
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;

import java.util.*;

import org.json.*;

/*
	Transliterate: takes the details about an assay, and if there's a suitable boilerplate available, translates it into human readable
	text, which might be arguably an improvement over showing the template-derived hierarchy, for certain use cases.
	
	Parameters:
		assayID: (optional) if known, ID of existing assay
		uniqueID: (optional) globally unique identifier (e.g. from PubChem)
		schemaURI, schemaBranches, schemaDuplication
		block: (optional) if provided, uses the boilerplate for a specific block, rather than the general one
		annotations: the terms of interest
		
	Return:
		html: string of display-ready HTML describing the assay content
*/

public class TransliterateAssay extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		String schemaURI = input.getString("schemaURI");
		String block = input.optString("block"); // null or empty causes it to use the general overall boilerplate

		JSONObject result = new JSONObject();
		Schema schema = SchemaDynamic.compositeSchema(input.optString("schemaURI", null), 
													 input.optJSONArray("schemaBranches"), input.optJSONArray("schemaDuplication"));
		Transliteration.Boilerplate boiler = Common.getTransliteration().getBoilerplateBlock(schemaURI, block);
		if (schema == null || boiler == null) return result;

		DataObject.Assay assay = convertToAssay(input);
		result.put("html", transcribeAssay(boiler, schema, assay));

		return result;
	}

	// ------------ private methods ------------

	public static DataObject.Assay convertToAssay(JSONObject input)
	{
		JSONArray annotations = input.getJSONArray("annotations");
		List<DataObject.Annotation> listAnnot = new ArrayList<>();
		List<DataObject.TextLabel> listText = new ArrayList<>();
		collectAnnotations(annotations, listAnnot, listText);

		DataObject.Assay assay = new DataObject.Assay();
		assay.assayID = input.optLong("assayID", 0);
		assay.uniqueID = input.optString("uniqueID", null);
		assay.annotations = listAnnot.toArray(new DataObject.Annotation[listAnnot.size()]);
		assay.textLabels = listText.toArray(new DataObject.TextLabel[listText.size()]);
		return assay;
	}

	public static String transcribeAssay(Transliteration.Boilerplate boiler, Schema schema, DataObject.Assay assay) throws RESTException
	{
		if (boiler == null) return "";
		BoilerplateScript script = new BoilerplateScript(boiler, schema, assay);
		try {script.inscribe();}
		catch (DetailException ex) {throw new RESTException("Boilerplate inscribing failed", ex, "Transcribing failed", RESTException.HTTPStatus.INTERNAL_SERVER_ERROR);} 
		return script.getHTML();
	}

	protected static void collectAnnotations(JSONArray annots, List<DataObject.Annotation> listAnnot, List<DataObject.TextLabel> listText)
	{
		for (int n = 0; n < annots.length(); n++)
		{
			JSONObject obj = annots.getJSONObject(n);
			String propURI = obj.optString("propURI");
			String valueURI = obj.optString("valueURI", null);
			String label = obj.optString("valueLabel", null);
			String[] groupNest = obj.optJSONArrayEmpty("groupNest").toStringArray();

			if (valueURI != null && valueURI.length() > 0) listAnnot.add(new DataObject.Annotation(propURI, valueURI, groupNest));
			else if (label != null && label.length() > 0) listText.add(new DataObject.TextLabel(propURI, label, groupNest));
		}
	}
}

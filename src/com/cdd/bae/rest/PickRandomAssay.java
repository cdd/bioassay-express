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
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.apache.commons.lang3.*;
import org.json.*;

/*
	PickRandomAssay: selects an assay at random (from the non-curated list) and returns the details
	
	Parameters:
		numAssays: (default = 1) how many assays to pick & return, at most
		curated: (default = false) whether to pick from curated list, or uncurated
		blank: (default = false) whether to also include curated assays with no semantic annotations
*/

public class PickRandomAssay extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		DataStore store = Common.getDataStore();

		int numAssays = input.optInt("numAssays", 1);
		boolean curated = input.optBoolean("curated", false);
		boolean blank = input.optBoolean("blank", false);

		long[] assayIDList = curated ? store.assay().fetchAllCuratedAssayID() : store.assay().fetchAllNonCuratedAssayID();
		if (blank && !curated) assayIDList = combineCuratedBlank(store, assayIDList);

		JSONArray results = new JSONArray();
		Random rnd = new Random();
		for (int n = 0; n < numAssays && assayIDList.length > 0; n++)
		{
			int idx = rnd.nextInt(assayIDList.length);
			results.put(formulateAssay(assayIDList[idx], store));
			assayIDList = ArrayUtils.remove(assayIDList, idx);
		}
		return new JSONObject().put(RETURN_JSONARRAY, results);
	}

	// ------------ private methods ------------

	// return a list of assay IDs that are curated but have zero semantic labels
	private long[] combineCuratedBlank(DataStore store, long[] initAssayID)
	{
		Set<Long> assaySet = new TreeSet<>();
		for (long assayID : initAssayID) assaySet.add(assayID);
		for (long assayID : store.assay().fetchAssayIDWithoutAnnotations()) assaySet.add(assayID);
		return ArrayUtils.toPrimitive(assaySet.toArray(new Long[assaySet.size()]));
	}

	private JSONObject formulateAssay(long assayID, DataStore store)
	{
		DataObject.Assay assay = store.assay().getAssay(assayID);

		JSONObject json = new JSONObject();
		json.put("assayID", assay.assayID);
		json.put("text", assay.text);
		json.put("uniqueID", assay.uniqueID);
		//json.put("pubchemSource", assay.pubchemSource);

		Schema schema = Common.getSchema(assay.schemaURI);
		JSONArray annotList = new JSONArray();
		for (DataObject.Annotation annot : assay.annotations)
		{
			String valueLabel = null;
			if (schema != null) valueLabel = Common.getCustomName(schema, annot.propURI, annot.groupNest, annot.valueURI);
			if (valueLabel == null) valueLabel = Common.getOntoValues().getLabel(annot.valueURI);

			String valueDescr = null;
			if (schema != null) valueDescr = Common.getCustomDescr(schema, annot.propURI, annot.groupNest, annot.valueURI);
			if (valueDescr == null) valueDescr = Common.getOntoValues().getDescr(annot.valueURI);

			JSONObject obj = new JSONObject();
			obj.put("propURI", annot.propURI);
			obj.put("valueURI", annot.valueURI);
			obj.put("propLabel", Common.getOntoProps().getLabel(annot.propURI));
			obj.put("valueLabel", valueLabel);
			obj.put("propDescr", Common.getOntoProps().getDescr(annot.propURI));
			obj.put("valueDescr", valueDescr);
			annotList.put(obj);
		}
		json.put("annotations", annotList);

		return json;
	}
}

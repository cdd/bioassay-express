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
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import org.json.*;

/*
	GetLiteralValues: for the indicated property, obtains a list of each unique literal value.

	Parameters:
		schemaURI, schemaBranches, schemaDuplication
		locator: which part of the template
*/

public class GetLiteralValues extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		String locator = input.getString("locator");
		Schema schema = SchemaDynamic.compositeSchema(input.optString("schemaURI", null),
													 input.optJSONArray("schemaBranches"), input.optJSONArray("schemaDuplication"));

		Schema.Assignment assn = schema.obtainAssignment(locator);
		return collectUnique(schema, assn);
	}

	@Override
	protected String getETag()
	{
		return "assay-" + Common.getDataStore().assay().getWatermark();
	}

	// ------------ private methods ------------

	// builds the tree, then serialises it to JSON
	private JSONObject collectUnique(Schema schema, Schema.Assignment assn)
	{
		DataStore store = Common.getDataStore();
		JSONObject map = new JSONObject();

		for (long assayID : store.assay().fetchAssayIDWithSchemaCurated(schema.getSchemaPrefix()))
		{
			DataObject.Assay assay = store.assay().getAssay(assayID);
			if (assay == null || assay.textLabels == null) continue;
			for (DataObject.TextLabel lbl : assay.textLabels)
			{
				if (!Schema.compatiblePropGroupNest(lbl.propURI, lbl.groupNest, assn.propURI, assn.groupNest())) continue;

				boolean valid = true;
				if (assn.suggestions == Schema.Suggestions.INTEGER) valid = AssayUtil.validIntegerLiteral(lbl.text);
				else if (assn.suggestions == Schema.Suggestions.NUMBER) valid = AssayUtil.validNumberLiteral(lbl.text);
				if (!valid) continue;

				map.put(lbl.text, map.optInt(lbl.text, 0) + 1);
			}
		}

		return map;
	}
}

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
import com.cdd.bae.rest.RESTException.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import org.json.*;

/*
	BranchInfo: provides information about some part of an assignment's tree of values - relatively lightweight
	
	Parameters:
		schemaURI, schemaBranches, schemaDuplication
		propURI, groupNest: identify the assignment
		valueURIList: array of value URIs to return information about; if null, will return information
					  about the assignment instead

	Return value: either
		property:
			name
			descr
			propURI
			groupNest
			
		.. or..
		
		branches: array of...
			valueURI
			valueLabel
			valueDescr
			altLabels
			externalURLs
			valueHier
			labelHier	
*/

public class BranchInfo extends RESTBaseServlet 
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		String schemaURI = input.optString("schemaURI", null);
		JSONArray schemaBranches = input.optJSONArray("schemaBranches"), schemaDuplication = input.optJSONArray("schemaDuplication");
		String propURI = input.getString("propURI");
		String[] groupNest = input.optJSONArrayEmpty("groupNest").toStringArray();
		JSONArray valueURIList = input.optJSONArray("valueURIList");
	
		SchemaDynamic graft = new SchemaDynamic(schemaURI, schemaBranches, schemaDuplication);
		SchemaDynamic.SubTemplate subt = graft.relativeAssignment(propURI, groupNest);
		if (subt == null) throw new RESTException("Assignment not found; propURI=" + propURI + ", groupNest=" + Util.arrayStr(groupNest), HTTPStatus.BAD_REQUEST);
		Schema.Assignment[] assnList = subt.schema.findAssignmentByProperty(propURI, subt.groupNest);
		if (assnList.length == 0) throw new RESTException("Assignment not found; propURI=" + propURI + ", groupNest=" + Util.arrayStr(groupNest), HTTPStatus.BAD_REQUEST);
		
		JSONObject result = new JSONObject();
		if (valueURIList == null)
			result.put("property", makePropertyInfo(subt.schema, assnList[0]));
		else
			result.put("branches", makeBranchInfo(subt.schema, assnList[0], valueURIList.toStringArray()));	
		return result;
	}

	// ------------ private methods ------------
	
	private JSONObject makePropertyInfo(Schema schema, Schema.Assignment assn)
	{
		JSONObject json = new JSONObject();
		json.put("name", assn.name);
		json.put("descr", assn.descr);
		json.put("propURI", assn.propURI);
		json.put("groupNest", assn.groupNest());
		return json;
	}
	
	private JSONArray makeBranchInfo(Schema schema, Schema.Assignment assn, String[] valueURIList)
	{
		JSONArray list = new JSONArray();
		for (String valueURI : valueURIList)
		{
			JSONObject json = new JSONObject();
			json.put("valueURI", valueURI);
			
			String label = null, descr = null;
			for (Schema.Value v : assn.values) if (v.uri.equals(valueURI))
			{
				label = v.name;
				descr = v.descr;
				break;
			}

			SchemaTree tree = Common.obtainTree(schema, assn);
			SchemaTree.Node node = tree == null ? null : tree.getTree().get(valueURI);
						
			if (node != null && Util.isBlank(label)) label = node.label;
			if (node != null && Util.isBlank(descr)) descr = node.descr;
			json.put("valueLabel", label);
			json.put("valueDescr", descr);
			
			if (node != null) 
			{
				JSONArray valueHier = new JSONArray(), labelHier = new JSONArray();
				for (SchemaTree.Node look = node.parent; look != null; look = look.parent) 
				{
					valueHier.put(look.uri);
					labelHier.put(look.label);
				}
				json.put("altLabels", node.altLabels);
				json.put("externalURLs", node.externalURLs);
				json.put("valueHier", valueHier);
				json.put("labelHier", labelHier);
			}
			
			list.put(json);
		}

		return list;
	}
}

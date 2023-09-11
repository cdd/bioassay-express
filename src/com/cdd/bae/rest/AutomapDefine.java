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
import com.cdd.bao.util.*;

import org.json.*;

/*
	AutomapDefine: submits information about mappings between keywords and terms.	
	
	Parameters:
		mappings: array of...
			schemaURI
			propURI
			groupNest
			keyword
			valueURI (null = it's an assignment mapping)
		deletions: array of...
			schemaURI
			propURI
			groupNest
			keyword			
*/

public class AutomapDefine extends RESTBaseServlet 
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------

	@Override
	protected boolean requireSession()
	{
		return true;
	}

	@Override
	protected boolean hasPermission(Session session)
	{
		return session != null && session.canSubmitBulk();
	}

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		DataStore store = Common.getDataStore();

		for (JSONObject json : input.optJSONArrayEmpty("mappings").toObjectArray())
		{
			String schemaURI = json.getString("schemaURI");
			String propURI = json.getString("propURI");
			String[] groupNest = json.getJSONArray("groupNest").toStringArray();
			String keyword = json.getString("keyword");
			String valueURI = json.optString("valueURI", null);
			if (valueURI != null)
				store.keywordMap().defineKeywordMapValue(schemaURI, propURI, groupNest, keyword, valueURI);
			else
				store.keywordMap().defineKeywordMapAssignment(schemaURI, keyword, propURI, groupNest);
		}
		for (JSONObject json : input.optJSONArrayEmpty("deletions").toObjectArray())
		{
			String schemaURI = json.getString("schemaURI");
			String propURI = json.optString("propURI", null);
			String[] groupNest = json.optJSONArrayEmpty("groupNest").toStringArray();
			String keyword = json.getString("keyword");
			if (propURI != null)
			{
				DataObject.KeywordMap map = store.keywordMap().getKeywordMapValue(schemaURI, propURI, groupNest, keyword);
				if (map != null) store.keywordMap().deleteKeywordMap(map.keywordmapID);
			}
			else
			{
				DataObject.KeywordMap map = store.keywordMap().getKeywordMapAssignment(schemaURI, keyword);
				if (map != null) store.keywordMap().deleteKeywordMap(map.keywordmapID);
			}
		}

		JSONObject result = new JSONObject();
		result.put("success", true);
		return result;
	}

	// ------------ private methods ------------


}

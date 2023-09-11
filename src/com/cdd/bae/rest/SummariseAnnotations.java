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

import java.util.*;

import org.json.*;

/*
	SummariseAnnotations: provides statistics on how often each of them is used
	
	Parameters:
		(none)
*/

public class SummariseAnnotations extends RESTBaseServlet 
{
	private static final long serialVersionUID = 1L;

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		DataStore store = Common.getDataStore();
		Map<String, Integer> propCounts = store.assay().breakdownProperties();
		Map<String, Map<String, Integer>> annotCounts = store.assay().breakdownAssignments();
		
		JSONObject result = new JSONObject();
		result.put("propCounts", new JSONObject(propCounts));
		result.put("annotCounts", new JSONObject(annotCounts));
		return result;
	}
}

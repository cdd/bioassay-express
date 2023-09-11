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

import com.cdd.bae.config.Transliteration.*;
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.bae.util.BoilerplateAnalysis.*;

import java.util.*;

import org.apache.http.*;
import org.json.*;

/*
	AdminEnumerateTransliteration: analyse transliteration template

	Parameters:
		template: definition of transliteration template
*/

public class AdminEnumerateTransliteration extends RESTBaseServlet
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
		return session != null && session.isAdministrator();
	}

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		String template = input.getString("template");

		Boilerplate boiler = new Boilerplate();
		boiler.content = new JSONArray(template);

		BoilerplateAnalysis analysis = new BoilerplateAnalysis(boiler, Common.getSchemaCAT());

		SortedMap<String, List<AnalysisCombination>> grouped;
		try
		{
			grouped = analysis.groupedTransliterations();

		}
		catch (DetailException e)
		{
			throw new RESTException(e.getMessage(), RESTException.HTTPStatus.INTERNAL_SERVER_ERROR);
		}

		JSONArray arr = new JSONArray();
		for (String text : grouped.keySet())
		{
			JSONObject obj = new JSONObject();
			obj.put("text", text);
			JSONArray combinations = new JSONArray();
			for (AnalysisCombination combination : grouped.get(text))
			{
				combinations.put(combination.toString());
			}
			obj.put("combinations", combinations);
			arr.put(obj);
		}

		JSONObject result = new JSONObject();
		result.put("success", true);
		result.put("results", arr);
		result.put("status", HttpStatus.SC_OK);
		return result;
	}

	@Override
	protected String[] getRequiredParameter()
	{
		return new String[]{"template"};
	}
}

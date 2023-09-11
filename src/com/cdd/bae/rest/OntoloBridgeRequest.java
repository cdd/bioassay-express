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
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import org.apache.commons.io.*;
import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.entity.*;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;
import org.apache.http.message.*;
import org.json.*;

/*
	OntoloBridgeRequest: request term to the named ontology bridge.
	
	Parameters:
		bridgeName: identifies the Ontology Bridge defined in config.json.
		provisionalID: identifies the provisional term request already submitted from the BAE UI.
	
	Return:
		term: full description of the term (see GetTermRequests for format)
*/

public class OntoloBridgeRequest extends RESTBaseServlet
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
		String bridgeName = input.getString("bridgeName");
		InitParams.OntoloBridge curBridge = null;

		for (InitParams.OntoloBridge ob : Common.getConfiguration().getOntoloBridges())
			if (ob.name.equals(bridgeName)) {curBridge = ob; break;}

		if (curBridge == null)
			throw new RESTException("No OntoloBridge found for name " + bridgeName + ".", RESTException.HTTPStatus.BAD_REQUEST);

		DataStore store = Common.getDataStore();
		long provisionalID = input.getLong("provisionalID");
		DataObject.Provisional provisional = store.provisional().getProvisional(provisionalID);
		if (provisional == null)
			throw new RESTException("No Provisional ID found " + provisionalID + ".", RESTException.HTTPStatus.BAD_REQUEST);

		try 
		{
			return executeRequest(store, curBridge, provisional);
		}
		catch (IOException ex)
		{
			throw new RESTException(ex, "OntoloBridge request failure", RESTException.HTTPStatus.BAD_REQUEST);
		}
	}
	
	// ------------ private methods ------------

	protected JSONObject executeRequest(DataStore store, InitParams.OntoloBridge bridge, DataObject.Provisional prov) throws IOException
	{
		List<NameValuePair> urlParams = new ArrayList<>();
		urlParams.add(new BasicNameValuePair("description", prov.description));
		urlParams.add(new BasicNameValuePair("label", prov.label));
		urlParams.add(new BasicNameValuePair("justification", prov.explanation));
		urlParams.add(new BasicNameValuePair("submitter", prov.proposerID));
		urlParams.add(new BasicNameValuePair("superclass", prov.parentURI));

		String url = bridge.baseURL + "/requests/RequestTerm";
		HttpPost request = new HttpPost(url);
		request.setHeader("ContentType", "application/json");
		request.setHeader("Accept", "application/json");
		if (Util.notBlank(bridge.authToken)) request.setHeader("Authorization", "Token " + bridge.authToken);

		JSONObject bridgeResponse = null;
		try
		{
			request.setEntity(new UrlEncodedFormEntity(urlParams));

			HttpClient client = HttpClientBuilder.create().build();
			HttpResponse response = client.execute(request);
			int code = response.getStatusLine().getStatusCode();
			if (code == 200)
			{
				byte[] respBytes = IOUtils.toByteArray(response.getEntity().getContent());
				bridgeResponse = new JSONObject(new String(respBytes));
			}
			else throw new IOException("OntoloBridge API failure " + bridge.name + " error code " + code + " failed, url: " + url);
		}
		catch (IOException ex) {throw ex;}
		catch (Exception ex)
		{
			throw new IOException("OntoloBridge API failure " + bridge.name + " failed, url: " + url + ", because:" + ex.getMessage(), ex);
		}

		if (bridgeResponse == null) throw new IOException("Term request to OntoloBridge " + bridge.name + " failed, url: " + url);

		// persist attributes of bridge request to provisional record in BAE
		long requestID = bridgeResponse.getLong("requestID");
		prov.bridgeStatus = DataProvisional.BRIDGESTATUS_SUBMITTED;
		prov.bridgeURL = bridge.baseURL;
		prov.bridgeToken = Long.toString(requestID);
		prov.modifiedTime = new Date();
		store.provisional().updateProvisional(prov);

		Map<String, String> labelMap = new HashMap<>();
		for (DataObject.Provisional look : store.provisional().fetchAllTerms()) labelMap.put(look.uri, look.label);

		// return ID of new term request
		JSONObject result = new JSONObject();
		result.put("term", GetTermRequests.formulateProvisional(store, prov, labelMap));
		result.put("success", true);
		result.put("status", HttpStatus.SC_OK);
		return result;
	}
}

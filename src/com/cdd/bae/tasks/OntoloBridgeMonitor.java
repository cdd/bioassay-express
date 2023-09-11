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

package com.cdd.bae.tasks;

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.rest.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import javax.servlet.*;

import org.apache.commons.io.*;
import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.*;
import org.apache.http.impl.client.*;
import org.apache.http.message.*;
import org.json.*;

/*
	Periodically checks the provisional terms to see if any of them are waiting on results from
	having been escalated to an OntoloBridge connection.
*/

public class OntoloBridgeMonitor extends BaseMonitor implements Runnable
{
	private static final long DELAY_SECONDS = 10;
	private static final long LONG_PAUSE_SECONDS = (long)60 * 60; // 1 hour

	private static OntoloBridgeMonitor main = null;
	private DataStore store = null;
	
	// ------------ lifecycle ------------

	@Override
	public void contextInitialized(ServletContextEvent ev) 
	{
		super.contextInitialized(ev);
		
		if (Common.getConfiguration() == null || Common.getParams() == null || Common.isStateless())
		{
			logger.info("Configuration not available or invalid: disabled");
			return;
		}

		new Thread(this).start();
	}

	// ------------ public methods ------------

	public OntoloBridgeMonitor()
	{
		super();
		main = this;
	}

	public static OntoloBridgeMonitor main()
	{
		return main;
	}

	public void run()
	{
		// give it a moment: allow the server a chance to get settled in, or to be terminated
		waitTask(DELAY_SECONDS);
		if (stopped) return;

		store = Common.getDataStore();

		Set<String> statusPoll = new HashSet<>(); // anything not on this list doesn't need polling
		statusPoll.add(DataProvisional.BRIDGESTATUS_SUBMITTED);
		statusPoll.add(DataProvisional.BRIDGESTATUS_UNDER_REVIEW);

		while (!stopped)
		{
			boolean first = true;
			for (DataObject.Provisional prov : store.provisional().fetchAllTerms()) 
			{
				if (!statusPoll.contains(prov.bridgeStatus)) continue;
				
				if (first) logger.info("polling status");
				first = false;

				try
				{
					checkTermRequestStatus(store, prov);
				}
				catch (RESTException | JSONException ex)
				{
					logger.warn("Could not update status for provisional term with bridge token " + prov.bridgeToken, ex);
				}
			}
	
			waitTask(LONG_PAUSE_SECONDS);
		}
	}

	private void checkTermRequestStatus(DataStore store, DataObject.Provisional prov) throws RESTException
	{
		// make sure the URL is in the configured list (it could have changed, and also guard against corruption)
		boolean sanity = false;
		InitParams.OntoloBridge[] bridges = Common.getConfiguration().getOntoloBridges();
		if (bridges != null) for (InitParams.OntoloBridge look : bridges) if (look.baseURL.equals(prov.bridgeURL)) {sanity = true; break;}
		if (!sanity) return;
	
		List<NameValuePair> urlParameters = new ArrayList<>();
		urlParameters.add(new BasicNameValuePair("requestID", prov.bridgeToken));

		String queryString = URLEncodedUtils.format(urlParameters, "utf-8");
		StringBuilder url = new StringBuilder(prov.bridgeURL + "/requests/RequestStatus");
		url.append("?").append(queryString);

		HttpGet request = new HttpGet(url.toString());
		request.setHeader("Accept", "application/json");

		JSONObject jsonResponse = null;
		try
		{
			HttpClient client = HttpClientBuilder.create().build();
			HttpResponse response = client.execute(request);
			if (response.getStatusLine().getStatusCode() == 200)
			{
				byte[] respBytes = IOUtils.toByteArray(response.getEntity().getContent());
				jsonResponse = new JSONObject(new String(respBytes));
			}
		}
		catch (Exception e)
		{
			String msg = "Status check for term request " + prov.bridgeToken + " failed during API request to " + url;
			throw new RESTException(msg, RESTException.HTTPStatus.BAD_REQUEST);
		}

		String currentStatus = jsonResponse.getString("status");
		if (currentStatus.equals(prov.bridgeStatus)) return; // nothing changed

		if (currentStatus.equals(DataProvisional.BRIDGESTATUS_ACCEPTED))
		{
			prov.bridgeStatus = currentStatus;
			String approvedURI = jsonResponse.optString("uri", null);
			if (Util.notBlank(approvedURI) && !approvedURI.equals(prov.uri))
			{
				prov.remappedTo = approvedURI;
				Common.getProvCache().update();
			}
			store.provisional().updateProvisional(prov);
		}
		else if (currentStatus.equals(DataProvisional.BRIDGESTATUS_SUBMITTED) || currentStatus.equals(DataProvisional.BRIDGESTATUS_REJECTED) ||
				currentStatus.equals(DataProvisional.BRIDGESTATUS_EXPIRED) || currentStatus.equals(DataProvisional.BRIDGESTATUS_UNDER_REVIEW))
		{
			prov.bridgeStatus = currentStatus;
			store.provisional().updateProvisional(prov);
		}
		else
		{
			logger.warn("Unexpected status '" + currentStatus + "' for provisional term ID " + prov.provisionalID + ", response: " + jsonResponse);
		}
	}
}

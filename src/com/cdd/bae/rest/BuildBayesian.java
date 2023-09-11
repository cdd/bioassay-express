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
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;
import javax.servlet.http.*;

import org.json.*;
import org.openscience.cdk.fingerprint.*;
import org.openscience.cdk.fingerprint.model.*;
import org.openscience.cdk.interfaces.*;
import org.openscience.cdk.exception.*;

/*
	BayesianModel: looks up the compounds & activities for each of the assays, and throws them all a single Bayesian model
	using ECFP6 fingerprints, then returns the serialised model representation.
	
	Parameters:
	
		assayIDList: list of assay ID codes
		
	Return:
	
		model: serialised version of the resulting model
		failMsg: if model building failed, this is why (graceful failure with human-friendly message)
*/

public class BuildBayesian extends RESTBaseServlet 
{
	private static final String FAIL_MSG = "failMsg";

	private static final long serialVersionUID = 1L;
	
	protected static final int MAX_MODEL_SIZE = 1000000;

	// ------------ public methods ------------

	@Override
	protected void process(HttpServletRequest request, HttpServletResponse response, boolean isPost) throws IOException
	{
		try
		{
			// convert the input into JSON 
			JSONObject input = processInput(request, isPost);
			
			long[] assayIDList = input.getJSONArray("assayIDList").toLongArray();

			OutputStream out = response.getOutputStream(); // grabbing this early to test for connection
			
			// TODO: set a maximum # datapoints, and fail quickly & gracefully if this is exceeded...
			
			JSONObject result = new JSONObject();
			buildModel(assayIDList, result, out);

			// and return the result in the response
			prepareJSONResponse(response, result);

			// use the processResponse for servlet specific customisation
			processResponse(response);
		}
		catch (RESTException e)
		{
			prepareErrorResponse(request, response, e);
		}
		catch (Exception e)
		{
			prepareErrorResponse(request, response, new RESTException(e, "Unexpected error occured", RESTException.HTTPStatus.INTERNAL_SERVER_ERROR));
		}
	}

	// ------------ private methods ------------
	
	// creates the model; one of 3 outcomes: successful model, graceful error message, or fatal error
	protected static void buildModel(long[] assayIDList, JSONObject results, OutputStream flusher) throws IOException
	{
		DataStore store = Common.getDataStore();
		int dataPoints = 0;
		List<DataObject.Measurement> measureList = new ArrayList<>();
		for (long assayID : assayIDList)
		{
			flusher.flush(); // will cause a barf if client disconnects or system shuts down (right?)
			for (DataObject.Measurement measure : store.measure().getMeasurements(assayID, new String[]{DataMeasure.TYPE_ACTIVITY}))
			{
				dataPoints += measure.compoundID.length;
				if (dataPoints >= MAX_MODEL_SIZE)
				{
					results.put(FAIL_MSG, "Too many datapoints for one model.");
					return;
				}
				measureList.add(measure);
			}
		}
	
		Bayesian model = new Bayesian(CircularFingerprinter.CLASS_ECFP6);
			
		for (DataObject.Measurement measure : measureList)
		{
			final int batchSize = 100;
			for (int n = 0; n < measure.compoundID.length; n += batchSize)
			{
				flusher.flush();
				for (int i = 0; i < batchSize && n + i < measure.compoundID.length; i++) 
				{
					try {appendMolecule(model, store, measure, n + i);}
					catch (CDKException ex) {throw new IOException("Adding a molecule failed", ex);}
				}
			}
		}
		
		try
		{
			model.build();
			model.validateFiveFold();
			
			results.put("model", model.serialise());
			double roc = model.getROCAUC();
			if (Double.isNaN(roc) || roc <= 0)
			{
				results.put(FAIL_MSG, "Data not suitable for model.");
			}
		}
		catch (Exception ex) 
		{
			// fail gracefully: there are legitimate reasons why a model turns out to be gunk; don't need to be too
			// concerned about what it was exactly
			results.put(FAIL_MSG, "Insufficient data to build model.");
		}
	}
		
	
	// assuming the measurement denotes a boolean activity, looks up the molecule and adds it to the model
	private static void appendMolecule(Bayesian model, DataStore store, DataObject.Measurement measure, int idx) throws CDKException
	{
		if (Double.isNaN(measure.value[idx])) return;
	
		DataObject.Compound cpd = store.compound().getCompound(measure.compoundID[idx]);
		if (cpd == null || cpd.molfile == null) return;
		
		IAtomContainer mol = ChemInf.parseMolecule(cpd.molfile);
		if (mol == null || mol.getAtomCount() == 0) return;
		
		boolean active = measure.value[idx] >= 0.5;
		
		model.addMolecule(mol, active);
	}

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		/* not required as process is overriden */
		return null;
	}
}

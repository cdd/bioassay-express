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

package com.cdd.bae.model;

import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.model.assocrules.*;
import com.cdd.bae.model.dictionary.*;
import com.cdd.bao.template.*;

import java.io.*;
import java.util.*;
import java.util.Map.*;

/*
	Predict an assay or text using the dictionary methods
*/

public class AssociationPredict implements PredictionModel
{
	private ARModel models;

	public AssociationPredict() throws IOException
	{
		models = ARModel.loadDefaultModel();
	}

	public AssociationPredict(String filename) throws IOException
	{
		models = ARModel.fromFile(filename);
	}

	public Map<String, List<String>> getPrediction(Assay assay)
	{
		Map<String, List<String>> result = new HashMap<>();
		for (Entry<String, List<ScoredHit>> e : models.predict(assay.annotations).entrySet())
		{
			List<String> uriList = new ArrayList<>();
			for (ScoredHit h : e.getValue()) uriList.add(ModelSchema.expandPrefix(h.hit.uri));
			result.put(e.getKey(), uriList);
		}
		return result;
	}
}

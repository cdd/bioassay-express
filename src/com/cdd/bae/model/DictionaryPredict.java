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

import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.model.dictionary.*;
import com.cdd.bae.tasks.*;
import com.cdd.bao.template.*;

import java.io.*;
import java.util.*;
import java.util.Map.*;
import java.util.stream.*;

import org.apache.commons.lang3.*;

/*
	Predict an assay or text using the dictionary methods
*/

public class DictionaryPredict implements PredictionModel
{
	private DictionaryModels models;

	// ------------ public methods ------------

	public DictionaryPredict() throws IOException
	{
		models = new DictionaryModels();
	}

	public Map<String, List<String>> getPrediction(Assay assay)
	{
		return getPrediction(textFromAssay(assay));
	}

	public Map<String, List<ScoredHit>> getPredictionHits(Assay assay)
	{
		return getPredictionHits(textFromAssay(assay));
	}

	public Map<String, List<String>> getPrediction(String text)
	{
		return getPrediction(new String[]{text});
	}

	public Map<String, List<String>> getPrediction(String[] texts)
	{
		Map<String, List<String>> result = new HashMap<>();
		for (Entry<String, List<ScoredHit>> e : getPredictionHits(texts).entrySet())
			result.put(e.getKey(), e.getValue().stream().map(h -> h.hit.uri).collect(Collectors.toList()));
		return result;
	}

	public Map<String, List<ScoredHit>> getPredictionHits(String[] texts)
	{
		final int keepMappings = texts.length - 1; // this corresponds to the assay text
		Map<String, List<ScoredHit>> combined = new HashMap<>();
		for (int i = 0; i < texts.length; i++)
		{
			for (Entry<String, List<ScoredHit>> e : models.predict(texts[i]).entrySet())
			{
				if (e.getValue().isEmpty()) continue;
				List<ScoredHit> result = combined.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
				for (ScoredHit hit : e.getValue())
				{
					// we only keep the mappings for the final text
					if (keepMappings != i) hit.clearMapping();

					int found = result.indexOf(hit);
					if (found == -1) result.add(hit);
					else result.get(found).addMappings(hit);
				}
			}
		}
		return combined;
	}

	// ------------ private methods ------------

	// splices any necessary pieces into the raw assay text; the order is important to give matches based 
	// on annotations a higher priority; user-supplied full text is always last (see getPredictionHits above)
	private String[] textFromAssay(Assay assay)
	{
		String[] texts = new String[]{assay.text};

		for (TextLabel label : assay.getTextLabels(AssayUtil.URI_ASSAYTITLE, null))
			texts = ArrayUtils.add(texts, label.text);

		Schema schema = Common.getSchema(assay.schemaURI);

		for (Annotation annot : assay.getAnnotations(PubChemAssays.PROTEINID_URI, null))
		{
			String valueLabel = Common.getCustomName(schema, annot.propURI, annot.groupNest, annot.valueURI);
			if (valueLabel == null) valueLabel = Common.getOntoValues().getLabel(annot.valueURI);
			texts = ArrayUtils.add(texts, valueLabel + "\n" + Common.getOntoValues().getDescr(annot.valueURI));
		}
		return ArrayUtils.add(texts, assay.text);
	}
}

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

import com.cdd.bae.data.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for TransliterateAssay REST API.
*/

public class TransliterateAssayTest
{
	// ------------ mockito-based tests ------------

	@Test
	public void testCollectAnnotations()
	{
		JSONArray annotations = getJSONAnnotations();
		List<DataObject.Annotation> listAnnot = new ArrayList<>();
		List<DataObject.TextLabel> listText = new ArrayList<>();
		TransliterateAssay.collectAnnotations(annotations, listAnnot, listText);
		assertEquals(1, listAnnot.size());
		assertEquals("propURI-1", listAnnot.get(0).propURI);
		assertEquals(1, listText.size());
		assertEquals("propURI-2", listText.get(0).propURI);
	}

	@Test
	public void testConvertToAssay()
	{
		JSONObject input = getJSONAssay();
		DataObject.Assay assay = TransliterateAssay.convertToAssay(input);
		assertEquals(0, assay.assayID);
		assertEquals(null, assay.uniqueID);
		assertEquals(1, assay.annotations.length);
		assertEquals(1, assay.textLabels.length);
	}

	// ------------ private methods ------------

	private JSONObject getJSONAssay()
	{
		return new JSONObject().put("annotations", getJSONAnnotations());
	}

	private JSONArray getJSONAnnotations()
	{
		JSONArray annotations = new JSONArray();
		annotations.put(new JSONObject().put("propURI", "propURI-1").put("valueURI", "valueURI-1"));
		annotations.put(new JSONObject().put("propURI", "propURI-2").put("valueLabel", "label-2"));
		// the following are ignored
		annotations.put(new JSONObject().put("propURI", "propURI-3"));
		annotations.put(new JSONObject().put("propURI", "propURI-4").put("valueURI", ""));
		annotations.put(new JSONObject().put("propURI", "propURI-5").put("valueLabel", ""));
		return annotations;
	}
}

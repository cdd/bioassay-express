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

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;
import java.util.*;
import java.util.Map.*;

import org.junit.jupiter.api.*;

/*
	Test for ModelUtilities
*/

public class ModelUtilitiesTest
{
	DataAnnot dataAnnot;

	@BeforeEach
	public void setup() throws ConfigurationException, IOException
	{
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);

		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Common.setDataStore(mongo.getDataStore());
		dataAnnot = new DataAnnot(mongo.getDataStore());
	}

	@Test
	public void testGetAnnotToFP() throws IOException
	{
		Map<String, Integer> annotToFP = ModelUtilities.getAnnotToFP();

		assertThat(annotToFP.size(), is(7));
		assertThat(new HashSet<>(annotToFP.values()).size(), is(7));
	}

	@Test
	public void testUpdateAnnotationFP() throws IOException
	{
		// this test is currently sufficient as the test database doesn't cover all annotations
		// should this change in the future, add an annotation before calling updateAnnotationFP
		Map<String, Integer> annotToFP = ModelUtilities.getAnnotToFP();
		int oldSize = annotToFP.size();

		ModelUtilities.updateAnnotationFP();

		annotToFP = ModelUtilities.getAnnotToFP();
		assertThat(annotToFP.size(), greaterThan(oldSize));
		assertThat(new HashSet<>(annotToFP.values()).size(), is(annotToFP.size()));
	}

	@Test
	public void testGetTargetAnnotMaps()
	{
		Map<String, Integer> annotToTarget = new HashMap<>();
		Map<Integer, AnnotationFP> targetToAnnot = new HashMap<>();

		ModelUtilities.getTargetAnnotMaps(annotToTarget, targetToAnnot);

		assertThat(annotToTarget.size(), greaterThan(0));
		assertThat(annotToTarget.size(), is(targetToAnnot.size()));

		// check that the information in the two maps is consistent
		for (Entry<Integer, AnnotationFP> e : targetToAnnot.entrySet())
		{
			AnnotationFP a = e.getValue();
			String key = a.propURI + ModelUtilities.SEP + a.valueURI;
			assertThat(annotToTarget, hasKey(key));
			assertThat(annotToTarget.get(key), is(a.fp));
		}

		for (Entry<String, Integer> e : annotToTarget.entrySet())
		{
			assertThat(targetToAnnot, hasKey(e.getValue()));
			AnnotationFP a = targetToAnnot.get(e.getValue());
			String key = a.propURI + ModelUtilities.SEP + a.valueURI;
			assertThat(key, is(e.getKey()));
			assertThat(a.fp, is(e.getValue()));
		}
	}
}

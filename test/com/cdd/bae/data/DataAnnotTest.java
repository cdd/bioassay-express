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

package com.cdd.bae.data;

import com.cdd.bae.data.DataObject.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;

import org.junit.jupiter.api.*;

/*
	Test for com.cdd.bae.data.DataAnnot
*/

public class DataAnnotTest
{
	private static final String VALUE_URI = "valueURI";
	private static final String PROP_URI = "propURI";

	DataAnnot dataAnnot;

	@BeforeEach
	public void initialize()
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		dataAnnot = new DataAnnot(mongo.getDataStore());
	}

	@Test
	public void testFetchAnnotationFP()
	{
		AnnotationFP[] allFps = dataAnnot.fetchAnnotationFP();
		assertThat(allFps.length, is(7));
		
		String propURI = allFps[0].propURI;
		AnnotationFP[] propFps = dataAnnot.fetchAnnotationFP(propURI);
		assertThat(propFps.length, is(4));
		for (AnnotationFP fp: propFps)
		{
			assertThat(fp.propURI, is(propURI));
		}
	}
	
	@Test
	public void testHasAnnotation()
	{
		for (AnnotationFP fp : dataAnnot.fetchAnnotationFP())
		{
			assertThat(dataAnnot.hasAnnotation(fp.propURI, fp.valueURI), is(true));
		}
		assertThat(dataAnnot.hasAnnotation(PROP_URI, VALUE_URI), is(false));
	}
	
	@Test
	public void testDeleteAllAnnotations() throws IOException
	{
		assertThat(dataAnnot.fetchAnnotationFP().length, is(7));
		dataAnnot.deleteAllAnnotations();
		assertThat(dataAnnot.fetchAnnotationFP().length, is(0));
	}
	
	@Test
	public void testAddAssnFingerprint() throws IOException
	{
		assertThat(dataAnnot.fetchAnnotationFP().length, is(7));
		assertThat(dataAnnot.hasAnnotation(PROP_URI, VALUE_URI), is(false));

		dataAnnot.addAssnFingerprint(PROP_URI, VALUE_URI, 999);
		assertThat(dataAnnot.hasAnnotation(PROP_URI, VALUE_URI), is(true));
		assertThat(dataAnnot.fetchAnnotationFP().length, is(8));
		
		AnnotationFP[] propFps = dataAnnot.fetchAnnotationFP(PROP_URI);
		assertThat(propFps.length, is(1));
	}
}

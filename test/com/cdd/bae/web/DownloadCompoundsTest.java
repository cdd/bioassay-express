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

package com.cdd.bae.web;

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.rest.*;
import com.cdd.bae.rest.MockRESTUtilities.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.commons.lang3.*;
import org.junit.jupiter.api.*;

/*
	Test for DownloadCompounds
*/

public class DownloadCompoundsTest
{
	@Test
	public void testDoPost() throws ServletException, IOException, ConfigurationException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/compound");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		HttpServletRequest request = MockRESTUtilities.mockedPOSTRequest(getParameters(new long[] {1, 22}, 2));
		MockServletResponse response = new MockServletResponse();

		DownloadCompounds servlet = new DownloadCompounds();
		servlet.doGet(request, response.getResponse());
		String content = response.getContentUnzipped();

		assertEquals(7, StringUtils.countMatches(content, "$$$$"));
		assertEquals(5, StringUtils.countMatches(content, "PubChemCID"));
		assertThat(content, containsString("3232596"));
		assertThat(content, containsString("3232588"));
		assertThat(content, containsString("3232585"));
	}

	// ------------ private methods ------------

	private static Map<String, String> getParameters(long[] assays, int ntags)
	{
		Map<String, String> parameters = new HashMap<>();
		parameters.put("assays", StringUtils.join(assays, ','));
		parameters.put("ntags", Integer.toString(ntags));
		if (ntags > 0) 
		{
			for (int n = 0; n < ntags; n++) 
			{
				parameters.put("o" + n, "old" + n);
				parameters.put("t" + n, "new" + n);
			}
			parameters.put("a0", Integer.toString(1));
			parameters.put("a1", Integer.toString(22));
			parameters.put("o0", "assay1");
			parameters.put("o1", "assay2");
			parameters.put("t0", "main");
			parameters.put("t1", "counter");
		}
		return parameters;
	}
}

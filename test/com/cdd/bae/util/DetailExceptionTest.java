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

package com.cdd.bae.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.*;

public class DetailExceptionTest
{
	@Test
	public void testJsonSchemaValidator()
	{
		final String message = "message";
		final String detail1 = "Detail 1";
		try
		{
			throw new DetailException(message, detail1);
		}
		catch (DetailException ex)
		{
			assertEquals(message, ex.getMessage());
			assertEquals(1, ex.getDetails().size());
			assertEquals(detail1, ex.getDetails().get(0));
		}
		try
		{
			List<String> details = Arrays.asList(detail1, "Detail 2");
			throw new DetailException(message, details);
		} 
		catch (DetailException ex)
		{
			assertEquals(message, ex.getMessage());
			assertEquals(2, ex.getDetails().size());
			assertEquals(detail1, ex.getDetails().get(0));
			assertEquals("Detail 2", ex.getDetails().get(1));
		}
	}
}

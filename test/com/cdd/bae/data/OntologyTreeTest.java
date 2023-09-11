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

import com.cdd.bae.config.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.*;

/*
	Test for com.cdd.bae.data.OntologyTree
 */

public class OntologyTreeTest
{
	@Test
	public void testLoadTree() throws IOException
	{
		var onto = new FauxOntologyTree();
		var roots = onto.getRoots();
		assertEquals(4, roots.length);
		assertEquals(16, roots[0].descendents);
		assertEquals(3, roots[1].descendents);
		assertEquals(0, roots[2].descendents);
		assertEquals(0, roots[3].descendents);
		assertEquals("root A", roots[0].label);
		assertEquals("thing 1", roots[0].children.get(0).label);
	}
}

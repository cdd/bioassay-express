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

import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import org.junit.jupiter.api.*;
import org.openscience.cdk.interfaces.*;

public class ChemInfTest
{
	public TestResourceFile aspirinMol = new TestResourceFile("/testData/cheminf/aspirin.mol");

	@Test
	public void testParseMol() throws IOException
	{
		IAtomContainer mol;
		mol = ChemInf.parseMolecule("");
		assertNull(mol, "Empty string gives null");

		mol = ChemInf.parseMolecule("Incorrect format");
		assertNull(mol, "Incorrect format gives null");

		mol = ChemInf.parseMolecule(aspirinMol.getContent());
		assertEquals(13, mol.getAtomCount());
	}

	@Test
	public void testFingerprintMethods() throws IOException
	{
		IAtomContainer mol = ChemInf.parseMolecule(aspirinMol.getContent());
		assertThat(ChemInf.hashECFP6(mol), greaterThan(0));
		assertThat(ChemInf.calculateFingerprints(mol).length, greaterThan(0));

		assertThat(ChemInf.hashECFP6(null), is(0));
		assertThat(ChemInf.calculateFingerprints(null).length, is(0));
	}

	@Test
	public void testTanimoto()
	{
		assertTrue(Float.isNaN(ChemInf.tanimoto(new int[]{}, new int[]{})));

		assertEquals(0, ChemInf.tanimoto(new int[]{1, 2}, new int[]{}), 0.01);
		assertEquals(1, ChemInf.tanimoto(new int[]{1, 2}, new int[]{1, 2}), 0.01);
		assertEquals(0.5, ChemInf.tanimoto(new int[]{1}, new int[]{1, 2}), 0.01);
		assertEquals(0.5, ChemInf.tanimoto(new int[]{1, 2}, new int[]{1}), 0.01);
		assertEquals(0.6, ChemInf.tanimoto(new int[]{1, 3, 5}, new int[]{1, 2, 3, 4, 5}), 0.01);
		assertEquals(0.6, ChemInf.tanimoto(new int[]{1, 2, 3, 4, 5}, new int[]{1, 3, 5}), 0.01);
	}
}

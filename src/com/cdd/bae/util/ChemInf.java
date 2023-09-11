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

import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import org.openscience.cdk.*;
import org.openscience.cdk.fingerprint.*;
import org.openscience.cdk.interfaces.*;
import org.openscience.cdk.io.*;

/*
	Cheminformatics functionality, encapsulated here for convenience.
*/

public class ChemInf
{
	private ChemInf() {}
	
	// ------------ public methods ------------

	// parses a molecule, which is presumed to be of the MDL CTAB variety, and returns the instantiated object; any kind of 
	// failure silently evaluates to a null result
	public static IAtomContainer parseMolecule(String mdlmol)
	{
		if (Util.isBlank(mdlmol)) return null;
		try (MDLV2000Reader rdr = new MDLV2000Reader(new StringReader(mdlmol)))
		{
			return rdr.read(new AtomContainer());
		}
		catch (Exception ex) {return null;}
	}

	// calculates ECFP6 fingerprints and returns a sorted list of unique hash codes; if anything goes wrong, returns an empty array
	public static int[] calculateFingerprints(IAtomContainer mol)
	{
		if (mol == null) return new int[0];
		
		CircularFingerprinter circ = new CircularFingerprinter(CircularFingerprinter.CLASS_ECFP6);
		try {circ.calculate(mol);} 
		catch (Exception ex) {} // ignored, return empty list
		
		Set<Integer> fp = new TreeSet<>();
		for (int n = 0; n < circ.getFPCount(); n++) fp.add(circ.getFP(n).hashCode);
		return Util.primInt(fp);
	}

	// computes a hash code made up of the constituent ECFP6 fingerprints; if anything goes wrong the return value is zero; note that
	// zero is not necessarily a failure case; the result is more or less guaranteed to be successful if the molecule isn't null or blank
	public static int hashECFP6(IAtomContainer mol)
	{
		if (mol == null) return 0;
		int hash = 0;
		for (int fp : calculateFingerprints(mol)) hash = hash ^ fp;
		return hash;
	}

	// calculates the Tanimoto coefficient for two lists of hash codes: these are assumed to be sorted and unique, which
	// allows the calculation to be done in O(N) time
	public static float tanimoto(int[] hash1, int[] hash2)
	{
		int shared = 0, total = 0;
		final int sz1 = hash1.length, sz2 = hash2.length;
		for (int i1 = 0, i2 = 0; i1 < sz1 || i2 < sz2; total++)
		{
			if (i1 == sz1) {total += sz2 - i2; break;}
			if (i2 == sz2) {total += sz1 - i1; break;}
			final int v1 = hash1[i1], v2 = hash2[i2];
			if (v1 == v2) {shared++; i1++; i2++;}
			else if (v1 < v2) i1++;
			else i2++;
		}
		if (total == 0) return Float.NaN;
		return (float)shared / total;
	}

	// ------------ private methods ------------
	
}

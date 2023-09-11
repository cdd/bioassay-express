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

package com.cdd.bae.tasks;

import java.io.*;

import org.openscience.cdk.*;
import org.openscience.cdk.exception.*;
import org.openscience.cdk.interfaces.*;
import org.openscience.cdk.io.*;
import org.openscience.cdk.io.iterator.*;

import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.bao.util.*;

/*
	Takes a list of compounds with missing fields, and acquires the missing pieces for the database.
	
	Note that there are some PubChem records where substances (SID) have no compound (CID). In these cases, the CID comes
	back as zero, but the structure is still filled in.
*/

public class PubChemCompounds
{
	static final String PUBCHEM_REST_SID = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/substance/sid/";
	private DataObject.Compound[] compounds;
	
	// ------------ public methods ------------

	public PubChemCompounds(DataObject.Compound[] compounds)
	{
		this.compounds = compounds;
	}

	public void download() throws IOException, CDKException
	{
		String url = getPubChemURL();

		String sdfile = null;
		try
		{
			sdfile = makeRequest(url);
		}
		catch (IOException ex)
		{
			throw new IOException("Failed to download [" + url + "].", ex);
		}

		parseSDF(sdfile);
	}
	
	// ------------ protected methods ------------
	
	protected String getPubChemURL()
	{
		String url = PUBCHEM_REST_SID;
		for (int n = 0; n < compounds.length; n++)
		{
			if (n > 0) url += ",";
			url += Integer.toString(compounds[n].pubchemSID);
		}
		url += "/SDF";
		return url;
	}

	protected DataObject.Compound getCompound(int pubchemSID) throws IOException
	{
		DataObject.Compound cpd = null;
		for (DataObject.Compound look : compounds) if (look.pubchemSID == pubchemSID)
		{
			cpd = look;
			break;
		}
		if (cpd == null) throw new IOException("Found SID " + pubchemSID + " not in requested list. URL: " + getPubChemURL());
		return cpd;
	}

	protected void parseSDF(String sdf) throws CDKException, IOException
	{
		BufferedReader instr = new BufferedReader(new StringReader(sdf));
		try (IteratingSDFReader rdr = new IteratingSDFReader(instr, DefaultChemObjectBuilder.getInstance()))
		{
			while (rdr.hasNext())
			{
				IAtomContainer mol = rdr.next();

				StringWriter outstr = new StringWriter();
				MDLV2000Writer wtr = new MDLV2000Writer(outstr);
				wtr.write(mol);
				wtr.close();
				String molfile = outstr.toString();

				int sid = Integer.parseInt(mol.getProperty("PUBCHEM_SUBSTANCE_ID"));
				int cid = getStandardizedPubChemSID(mol.getProperty("PUBCHEM_CID_ASSOCIATIONS"));

				DataObject.Compound cpd = getCompound(sid);
				cpd.molfile = molfile;
				cpd.hashECFP6 = ChemInf.hashECFP6(ChemInf.parseMolecule(molfile));
				cpd.pubchemCID = cid;
			}
		}
	}

	// get the standardized pubchem SID from SD tag PUBCHEM_CID_ASSOCIATIONS
	protected static int getStandardizedPubChemSID(String assoc)
	{
		if (assoc != null) for (String line : assoc.split("\n"))
		{
			String[] bits = line.split("\\s+");
			if (bits.length == 2 && Integer.valueOf(bits[1]) == 1) return Integer.valueOf(bits[0]);
		}
		
		// if not found return 0
		return 0;
	}

	// this method is required to avoid calling PubChem during tests
	protected String makeRequest(String url) throws IOException
	{
		return Util.makeRequest(url, null);
	}

	// ------------ private methods ------------

}

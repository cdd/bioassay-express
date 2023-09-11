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

import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import org.json.*;
import org.openscience.cdk.*;
import org.openscience.cdk.interfaces.*;
import org.openscience.cdk.io.*;

/*
	ListCompounds: for the given list of assays, fetches all compounds associated with them by way of measurements.
	
	Parameters:
		
		assayIDList: array of assay IDs
		probesOnly: (optional) if true, only considers probes
		similarTo: (optional) MDL Molfile structure: return fingerprint similarity to this
		requireMolecule: (optional) if specified as true, measurements with no structure are skipped
					-> provides a list of compound IDs associated with these assays

	OR:

		compoundIDList: array of compound IDs
		pubchemCIDList, pubchemSIDList: (optional) alternate lookup keys
					-> provides more information for each compound requested

	Options that apply to both invocation modes:
		justIdentifiers: (optional) if true, omits the actual measurements (much faster)
		similarTo: (optional) MDL Molfile structure: return fingerprint similarity to this
		activesOnly: (optional) if true, limits to the boolean active/inactive type (reduces the amount of data);
					 when combined with justIdentifiers, it restricts to only those that are active
		hashECFP6List: (optional) list of ECFP6 hash codes - only return compounds that match one of them

*/

public class ListCompounds extends RESTBaseServlet 
{
	private static final String COMPOUND_ID_LIST = "compoundIDList";
	private static final String HASH_ECFP6_LIST = "hashECFP6List";

	private static final String OPERATOR = "operator";

	private static final long serialVersionUID = 1L;
       
    // special internal property URIs that provide relevant measurement metadata
	private static final String PROPURI_FIELD = "http://www.bioassayontology.org/bao#BAX_0000015";
	private static final String PROPURI_OPERATOR = "http://www.bioassayontology.org/bao#BAX_0000016";
	private static final String PROPURI_THRESHOLD = "http://www.bioassayontology.org/bao#BAO_0002916";
       
	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		JSONObject result = new JSONObject();
		
		boolean justIdentifiers = input.optBoolean("justIdentifiers", false);
		String similarTo = input.optString("similarTo", null);
		boolean requireMol = input.optBoolean("requireMolecule", false);
		JSONArray hashlist = input.optJSONArray(HASH_ECFP6_LIST);
		Set<Integer> hashECFP6 = null;
		if (hashlist != null)
		{
			hashECFP6 = new HashSet<>();
			for (int n = 0; n < hashlist.length(); n++) hashECFP6.add(hashlist.getInt(n));
		}

		if (input.has("assayIDList"))
		{
			boolean probesOnly = input.optBoolean("probesOnly", false);
			boolean activesOnly = input.optBoolean("activesOnly", false); 
			JSONArray list = input.getJSONArray("assayIDList");
			if (justIdentifiers)
				obtainCompounds(list, probesOnly, activesOnly, requireMol, result, hashECFP6);
			else
				obtainMeasurements(list, probesOnly, activesOnly, requireMol, result, hashECFP6);

			if (similarTo != null) computeSimilarity(similarTo, result);
		}
		else if (input.has(COMPOUND_ID_LIST))
		{
			JSONArray listID = input.getJSONArray(COMPOUND_ID_LIST);
			JSONArray listCID = input.optJSONArray("pubchemCIDList");
			JSONArray listSID = input.optJSONArray("pubchemSIDList");
			obtainStructures(listID, listCID, listSID, result, justIdentifiers, similarTo, hashECFP6);
		}
		return result;
	}

	
	// ------------ private methods ------------

	// obtain unique list of compounds per assay, returning the identifiers
	private void obtainCompounds(JSONArray list, boolean probesOnly, boolean activesOnly, boolean requireMol,
								 JSONObject result, Set<Integer> hashWhitelist) throws JSONException
	{
		DataStore store = Common.getDataStore();
		Set<Long> compounds = new HashSet<>();

		String[] types = new String[]{probesOnly ? DataMeasure.TYPE_PROBE : DataMeasure.TYPE_ACTIVITY};
		for (int n = 0; n < list.length(); n++) 
		{
			DataObject.Measurement[] measurements = store.measure().getMeasurements(list.getLong(n), types);
			for (DataObject.Measurement measure : measurements) 
			{
				if (activesOnly && !measure.type.equals(DataMeasure.TYPE_ACTIVITY)) continue;
				for (int i = 0; i < measure.compoundID.length; i++) 
				{
					if (activesOnly && measure.value[i] < 0.5) continue;
					if (requireMol)
					{
						DataObject.Compound cpd = store.compound().getCompound(measure.compoundID[i]);
						if (Util.isBlank(cpd.molfile)) continue;
					}
					compounds.add(measure.compoundID[i]);
				}
			}
		}

		Long[] sorted = compounds.toArray(new Long[compounds.size()]);
		Arrays.sort(sorted);
		int[] hash = new int[sorted.length];
		for (int n = 0; n < sorted.length; n++) hash[n] = store.compound().getHashECFP6(sorted[n]);
		
		if (hashWhitelist != null)
		{
			JSONArray jsonCpd = new JSONArray(), jsonHash = new JSONArray();
			for (int n = 0; n < hash.length; n++) if (hashWhitelist.contains(hash[n]))
			{
				jsonCpd.put(sorted[n]);
				jsonHash.put(hash[n]);
			}
			result.put(COMPOUND_ID_LIST, jsonCpd);
			result.put(HASH_ECFP6_LIST, jsonHash);
		}
		else
		{
			result.put(COMPOUND_ID_LIST, sorted);
			result.put(HASH_ECFP6_LIST, hash);
		}
	}

	/// obtain list of measurements per assay (can get pretty big)
	private void obtainMeasurements(JSONArray list, boolean probesOnly, boolean activesOnly, boolean requireMol,
									JSONObject result, Set<Integer> hashWhitelist)
	{
		DataStore store = Common.getDataStore();
		JSONArray assays = new JSONArray();
		JSONArray columns = new JSONArray();
		JSONArray measureIndex = new JSONArray();
		JSONArray measureCompound = new JSONArray();
		JSONArray measureValue = new JSONArray();
		JSONArray measureRelation = new JSONArray();
		
		Set<Long> compounds = new HashSet<>();
		
		for (long assayID : list.toLongArray())
		{
			JSONObject jsonAssay = new JSONObject();
			extractAnnotationMetaData(assayID, jsonAssay);
			assays.put(jsonAssay);
		
			// bring in the measurement objects
			DataObject.Measurement[] measurements = store.measure().getMeasurements(assayID);
			for (DataObject.Measurement measure : measurements) 
			{
				if (probesOnly && !measure.type.equals(DataMeasure.TYPE_PROBE)) continue;
				if (activesOnly && !measure.type.equals(DataMeasure.TYPE_ACTIVITY)) continue;

				JSONObject obj = new JSONObject();
				obj.put("assayID", measure.assayID);
				obj.put("name", measure.name);
				obj.put("units", measure.units);
				obj.put("type", measure.type);
				int colidx = columns.length();
				columns.put(obj);
			
				for (int n = 0; n < measure.compoundID.length; n++)
				{
					if (requireMol)
					{
						DataObject.Compound cpd = store.compound().getCompound(measure.compoundID[n]);
						if (Util.isBlank(cpd.molfile)) continue;
					}
				
					measureIndex.put(colidx);
					measureCompound.put(measure.compoundID[n]);
					measureValue.put(measure.value[n]);
					measureRelation.put(measure.relation[n]);
				
					compounds.add(measure.compoundID[n]);
				}
			}
		}

		Long[] sorted = compounds.toArray(new Long[compounds.size()]);
		Arrays.sort(sorted);
		int[] hash = new int[sorted.length];
		for (int n = 0; n < sorted.length; n++) hash[n] = store.compound().getHashECFP6(sorted[n]);
		
		if (hashWhitelist != null)
		{
			Set<Long> cpdWhitelist = new HashSet<>();
			JSONArray jsonCpd = new JSONArray(), jsonHash = new JSONArray();
			for (int n = 0; n < hash.length; n++) if (hashWhitelist.contains(hash[n]))
			{
				cpdWhitelist.add(sorted[n]);
				jsonCpd.put(sorted[n]);
				jsonHash.put(hash[n]);
			}
			result.put(COMPOUND_ID_LIST, jsonCpd);
			result.put(HASH_ECFP6_LIST, jsonHash);
			
			for (int n = measureCompound.length() - 1; n >= 0; n--) if (!cpdWhitelist.contains(measureCompound.getLong(n)))
			{
				measureIndex.remove(n);
				measureCompound.remove(n);
				measureValue.remove(n);
				measureRelation.remove(n);
			}
		}
		else
		{
			result.put(COMPOUND_ID_LIST, new JSONArray(sorted));
			result.put(HASH_ECFP6_LIST, hash);
		}

		result.put("assays", assays);
		result.put("columns", columns);
		result.put("measureIndex", measureIndex);
		result.put("measureCompound", measureCompound);
		result.put("measureValue", measureValue);
		result.put("measureRelation", measureRelation);
	}
	
	// post-acquisition: create a list of similarities for each molecule
	private void computeSimilarity(String mdlmol, JSONObject result)
	{
		DataStore store = Common.getDataStore();
		IAtomContainer qmol = readMolecule(mdlmol);
		int[] qfp = ChemInf.calculateFingerprints(qmol);
		
		JSONArray compoundIDList = result.getJSONArray(COMPOUND_ID_LIST);
		float[] similarity = new float[compoundIDList.length()];
		
		for (int n = 0; n < compoundIDList.length(); n++)
		{
			DataObject.Compound cpd = store.compound().getCompound(compoundIDList.getLong(n));
			IAtomContainer smol = readMolecule(cpd.molfile);
			if (smol != null)
			{
				int[] sfp = ChemInf.calculateFingerprints(smol);
				similarity[n] = ChemInf.tanimoto(qfp, sfp);
			}
		}
		
		result.put("similarity", new JSONArray(similarity));
	}
	
	// parses MDL Molfile string into a CDK molecule; returns null on failure
	private IAtomContainer readMolecule(String mdlmol)
	{
		try (MDLV2000Reader rdr = new MDLV2000Reader(new StringReader(mdlmol)))
		{
			return rdr.read(new AtomContainer());
		}
		catch (Exception ex) {logger.error("ListCompounds/Molecule parsing error\n{}", mdlmol);}
		return null;
	}

	// by whatever ID numbers, obtain details about each of the structures
	private void obtainStructures(JSONArray listID, JSONArray listCID, JSONArray listSID, JSONObject result, 
								  boolean justIdentifiers, String similarTo, Set<Integer> hashWhitelist) throws JSONException
	{
		DataStore store = Common.getDataStore();
		JSONArray molfiles = new JSONArray(), hashes = new JSONArray();
		JSONArray idents = new JSONArray(), cids = new JSONArray(), sids = new JSONArray(), vids = new JSONArray(), mids = new JSONArray();
		JSONArray similarity = new JSONArray();
		
		int[] qfp = null;
		if (similarTo != null)
		{
			IAtomContainer qmol = readMolecule(similarTo);
			qfp = ChemInf.calculateFingerprints(qmol);
		}
		
		for (int n = 0; n < listID.length(); n++) 
		{
			long lookID = listID.getLong(n);
			
			int lookCID = listCID != null && n < listCID.length() ? listCID.getInt(n) : 0;
			int lookSID = listSID != null && n < listSID.length() ? listSID.getInt(n) : 0;
			
			DataObject.Compound cpd = lookID > 0 ? store.compound().getCompound(lookID) : null;
			if (cpd == null && lookSID > 0)
			{
				DataObject.Compound[] found = store.compound().getCompoundsWithPubChemSID(lookSID);
				if (found != null && found.length > 0) cpd = found[0];
			}
			if (cpd == null && lookCID > 0)
			{
				DataObject.Compound[] found = store.compound().getCompoundsWithPubChemCID(lookCID);
				if (found != null && found.length > 0) cpd = found[0];
			}

			if (hashWhitelist != null && !hashWhitelist.contains(cpd.hashECFP6)) continue;

			molfiles.put(cpd == null ? null : cpd.molfile);
			hashes.put(cpd == null ? 0 : cpd.hashECFP6);
			idents.put(cpd == null ? 0 : cpd.compoundID);
			cids.put(cpd == null ? 0 : cpd.pubchemCID);
			sids.put(cpd == null ? 0 : cpd.pubchemSID);
			vids.put(cpd == null ? 0 : cpd.vaultID);
			mids.put(cpd == null ? 0 : cpd.vaultMID);
			
			if (similarTo != null)
			{
				IAtomContainer smol = cpd != null && cpd.molfile != null ? readMolecule(cpd.molfile) : null;
				float sim = 0;
				if (smol != null)
				{
					int[] sfp = ChemInf.calculateFingerprints(smol);
					sim = ChemInf.tanimoto(qfp, sfp);
				}
				similarity.put(sim);
			}
		}
		
		if (!justIdentifiers) result.put("molfileList", molfiles);
		result.put(COMPOUND_ID_LIST, idents);
		result.put(HASH_ECFP6_LIST, hashes);
		result.put("pubchemCIDList", cids);
		result.put("pubchemSIDList", sids);
		result.put("vaultIDList", vids);
		result.put("vaultMIDList", mids);
		if (similarTo != null) result.put("similarity", similarity);
	}

	// look at the assay: see if there are measurement type annotations; note that this uses hardcoded URIs that are specific
	// to the Common Assay Template, and could be used in same or different ways with other templates
	private void extractAnnotationMetaData(long assayID, JSONObject obj)
	{
		DataStore store = Common.getDataStore();
		obj.put("assayID", assayID);
		DataObject.Assay assay = store.assay().getAssay(assayID);
		if (assay == null) return;
		for (DataObject.Annotation annot : assay.annotations) if (annot.propURI.equals(PROPURI_OPERATOR))
		{
			if (annot.valueURI.equals(ThresholdEstimator.OPERATOR_GREATER)) obj.put(OPERATOR, ">");
			else if (annot.valueURI.equals(ThresholdEstimator.OPERATOR_LESSTHAN)) obj.put(OPERATOR, "<");
			else if (annot.valueURI.equals(ThresholdEstimator.OPERATOR_GREQUAL)) obj.put(OPERATOR, ">=");
			else if (annot.valueURI.equals(ThresholdEstimator.OPERATOR_LTEQUAL)) obj.put(OPERATOR, "<=");
			else if (annot.valueURI.equals(ThresholdEstimator.OPERATOR_EQUAL)) obj.put(OPERATOR, "=");
			break;
		}
		for (DataObject.TextLabel label : assay.textLabels)
		{
			if (label.propURI.equals(PROPURI_FIELD) && !obj.has("field")) obj.put("field", label.text);
			else if (label.propURI.equals(PROPURI_THRESHOLD) && !obj.has("threshold"))
			{
				double v = Util.safeDouble(label.text, Double.NaN);
				if (Double.isFinite(v)) obj.put("threshold", v);
			} 
		}
	}
}



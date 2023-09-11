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

package com.cdd.bae.main;

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;
import com.cdd.bae.tasks.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;


import org.apache.commons.lang3.*;
import org.json.*;

/*
	Command line functionality pertaining to maintenance: things like rebuilding models & such.
*/

public class MaintenanceCommands implements Main.ExecuteBase
{
	private static final String PROPURI_FIELD = "http://www.bioassayontology.org/bao#BAX_0000015";
	private static final String PROPURI_OPERATOR = "http://www.bioassayontology.org/bao#BAX_0000016";
	private static final String PROPURI_THRESHOLD = "http://www.bioassayontology.org/bao#BAO_0002916";
	
	// ------------ public methods ------------

	public void execute(String[] args)
	{
		if (args.length == 0) {printHelp(); return;}
		
		String cmd = args[0];
		String[] options = ArrayUtils.remove(args, 0);

		if (cmd.equals("rebuildmodels")) rebuildModels();
		else if (cmd.equals("refreshmodels")) refreshModels();
		else if (cmd.equals("missingschema")) missingSchema();
		else if (cmd.equals("conformschema")) conformSchema();
		else if (cmd.equals("updatepubchem")) updatePubChem();
		else if (cmd.equals("bumploadedassays")) bumpLoadedAssays();
		else if (cmd.equals("structurehash")) updateStructureHash();
		else if (cmd.equals("remeasurepubchem")) remeasurePubChem(options);
		else if (cmd.equals("thresholdpubchem")) thresholdPubChem();
		else if (cmd.equals("vault")) VaultMaintenance.execute(options);
		else Util.writeln("Unknown command: '" + cmd + "'.");
	}
	
	public void printHelp()
	{
		Util.writeln("Maintenance commands");
		Util.writeln("    general maintenance of content");
		Util.writeln("Options:");
		Util.writeln("    rebuildmodels: delete all models and trigger rebuild (*long*)");
		Util.writeln("    refreshmodels: delete suggestion models rebuild");
		Util.writeln("    missingschema: any blank schema entries get set to CAT");
		Util.writeln("    updatepubchem: ensures PubChem-derived assays are uptodate");
		Util.writeln("    bumploadedassays: cause re-examination of downloaded assay files");
		Util.writeln("    structurehash: computer any missing structure hash codes");
		Util.writeln("    remeasurepubchem: mark given PubChem AIDs for measurement re-loading");
		Util.writeln("    thresholdpubchem: autocalculate PubChem assay thresholds when possible");
		Util.writeln("    vault: maintenance options related to CDD Vault");
	}  
	
	// ------------ private methods ------------
	
	// deletes everything in the model department, causing them to be rebuilt at the next earliest convenience
	private void rebuildModels()
	{
		Util.writeln("Flushing all model content...");
	
		DataStore store = Common.getDataStore();
		
		Util.writeln(" ... deleting annotations");
		store.annot().deleteAllAnnotations();
		
		Util.writeln(" ... deleting fingerprints");
		store.nlp().deleteAllFingerprints();
		
		Util.writeln(" ... deleting models");
		store.model().deleteAllModels();
		
		Util.writeln(" ... deleting assay NLP fingerprints");
		long[] idlist = store.assay().fetchAssayIDWithAnnotations();
		for (int n = 0; n < idlist.length; n++)
		{
			store.assay().clearAssayFingerprints(idlist[n]);
			if (n % 100 == 0) Util.writeln("        " + n + " / " + idlist.length);
		}

		
		Util.writeln("Model data deleted: will be rebuilt.");
	}
	
	// less extreme version of above
	private void refreshModels()
	{
		Util.writeln("Flushing suggestion models...");
	
		DataStore store = Common.getDataStore();
		
		Util.writeln(" ... deleting suggestion models");
		store.model().deleteAllModels();
		
		Util.writeln("Suggestion models deleted: will be rebuilt.");
	}
	
	// any curated assays with no schema get defaulted to CAT
	private void missingSchema()
	{
		Util.writeln("Looking for missing schemaURI...");
	
		DataStore store = Common.getDataStore();
		
		long[] assayIDList = store.assay().fetchAllCuratedAssayID();
		Util.writeln("Curated assays: " + assayIDList.length);
		int numModified = 0;
		String schemaURI = "http://www.bioassayontology.org/bas#";
		for (int n = 0; n < assayIDList.length; n++)
		{
			DataObject.Assay assay = store.assay().getAssay(assayIDList[n]);
			if (assay.schemaURI != null && assay.schemaURI.length() > 0) continue;
			Util.writeln((n + 1) + "/" + assayIDList.length + ": ID=" + assayIDList[n]);
			store.assay().submitAssaySchema(assay.assayID, schemaURI);
			numModified++;
		}
		Util.writeln("Done: " + numModified + " assays changed.");
		
		if (numModified > 0)
		{
			store.model().nextWatermarkNLP();
			store.model().nextWatermarkCorr();
		}
	}
	
	
	// goes through assays looking for issues to autocorrect
	private void conformSchema()
	{
		Util.writeln("Conforming assays to schema...");
	
		DataStore store = Common.getDataStore();
		
		long[] assayIDList = store.assay().fetchAllCuratedAssayID();
		Util.writeln("Curated assays: " + assayIDList.length);
		int numModified = 0;
		for (int n = 0; n < assayIDList.length; n++)
		{
			DataObject.Assay assay = store.assay().getAssay(assayIDList[n]);
			if (assay.annotations == null) continue;
			Schema schema = Common.getSchema(assay.schemaURI);
			if (schema == null) continue;
			
			// fill out inadequately defined groups
			boolean grouped = false;
			for (DataObject.Annotation annot : assay.annotations)
			{
				Schema.Assignment[] assn = schema.findAssignmentByProperty(annot.propURI, annot.groupNest);
				if (assn.length == 0) continue; // delete orphans?
				if (assn.length > 1 || assn[0].groupNest().length != Util.length(annot.groupNest))
				{
					annot.groupNest = assn[0].groupNest();
					grouped = true;
				}
			}
			
			// remove duplicates
			Set<String> already = new HashSet<>();
			boolean dedupped = false;
			for (int i = 0; i < assay.annotations.length; i++)
			{
				DataObject.Annotation annot = assay.annotations[i];
				String key = annot.propURI + "::" + annot.valueURI + "::";
				if (Util.length(annot.groupNest) > 0) key += Util.arrayStr(annot.groupNest);
				if (already.contains(key))
				{
					assay.annotations = ArrayUtils.remove(assay.annotations, i);
					i--;
					dedupped = true;
				}
				else already.add(key);
			}
						
			if (grouped || dedupped)
			{
				Util.write("Modified assayID=" + assay.assayID + "/" + assay.uniqueID);
				if (grouped) Util.write(" grouped");
				if (dedupped) Util.writeln(" de-duplicated");
				Util.writeln();
				store.assay().submitAssay(assay);
				numModified++;
			}
		}
		Util.writeln("Done: " + numModified + " assays changed.");
		
		if (numModified > 0)
		{
			store.model().nextWatermarkNLP();
			store.model().nextWatermarkCorr();
		}
	}

	// goes through all of the incorporated PubChem assays and checks to see if any updating is necessary; if everything is uptodate,
	// no changes will be made; 
	private void updatePubChem()
	{
		InitParams.ModulePubChem module = Common.getModulePubChem();

		Util.writeln("Updating PubChem...");
		
		DataStore store = Common.getDataStore();
		
		// get list of assays in BAE database
		Set<Integer> aidset = new HashSet<>();
		for (String uniqueID : store.assay().fetchAllUniqueID())
		{
			Identifier.UID uid = Common.getIdentifier().parseKey(uniqueID);
			if (uid != null && uid.source.prefix.equals(Identifier.PUBCHEM_PREFIX)) aidset.add(Integer.valueOf(uid.id));
		}
		Util.writeln("Assays to check: " + aidset.size());
		
		// go once through all zipped assays and start checking matching BAE assays against them...
		List<File> files = new ArrayList<>();
		for (File f : new File(module.directory).listFiles()) if (f.getName().endsWith(".zip")) files.add(f);
		Collections.sort(files);
		
		// get list of approved sources to spend time parsing
		Map<String, SchemaTree.Node> sourceMap = PubChemAssays.getSourceMap();
		
		final Pattern ptnSource = Pattern.compile("^(\\d+)\\.concise.json\\.gz$");
	
		final int UPDATED = 0, SKIPPED = 1, TEXT = 2, TITLE = 3, SOURCE = 4, RELATED = 5;
		final int[] counts = new int[6];
		final String SEP = "::";
		final Set<PubChemAssays.MergeReason> reasons = new HashSet<>();
		
		for (int n = 0; n < files.size(); n++)
		{
			File f = files.get(n);
			Util.writeln("Scanning file [" + f.getName() + "]");
			
			try (ZipFile zipFile = new ZipFile(f))
			{
				zipFile.stream().forEach(ze ->
				{
					// the PubChem AID is encoded in the filename, and it's much faster to eliminate now than to parse
					// the whole thing
					Matcher m = ptnSource.matcher(new File(ze.getName()).getName());
					if (!m.matches()) return;
					int aid = Integer.valueOf(m.group(1));
					if (!aidset.contains(aid)) return;
					
					JSONObject json = PubChemAssays.parseGzippedZipfileEntry(zipFile, ze);
					JSONObject root = json.optJSONObject("PC_AssaySubmit");
					if (root == null) return;

					// parse assay and update previous assay text if it is in the BAE database and different
					Assay newAssay = PubChemAssays.parsePubChemAssay(root, sourceMap, new HashSet<>());
					if (newAssay == null) return;

					Assay oldAssay = store.assay().getAssayFromUniqueID(newAssay.uniqueID);
					reasons.clear();
					boolean updated = PubChemAssays.mergePubChemAssays(newAssay, oldAssay, reasons);
					
					if (updated) 
					{
						Util.writeln("    updated AID=" + aid + "/assayID=" + oldAssay.assayID + "; reasons: " + reasons);
						store.assay().setAssay(oldAssay);
						counts[UPDATED]++;
						if (reasons.contains(PubChemAssays.MergeReason.TEXT)) counts[TEXT]++;
						if (reasons.contains(PubChemAssays.MergeReason.TITLE)) counts[TITLE]++;
						if (reasons.contains(PubChemAssays.MergeReason.SOURCE)) counts[SOURCE]++;
						if (reasons.contains(PubChemAssays.MergeReason.RELATED)) counts[RELATED]++;
					}
					else counts[SKIPPED]++;
				});
			}
			catch (Exception e)
			{
				Util.writeln("Error parsing zipped file: " + e);
			}
		}
		
		Util.writeln("Done.");
		Util.writeln("    Updated: " + counts[UPDATED]);
		Util.writeln("    Skipped: " + counts[SKIPPED]);
		Util.writeln("    Text:    " + counts[TEXT]);
		Util.writeln("    Title:   " + counts[TITLE]);
		Util.writeln("    Source:  " + counts[SOURCE]);
		Util.writeln("    Related: " + counts[RELATED]);
	}

	// removes all the assay files from the "already checked this" list, so that they will be re-parsed
	public void bumpLoadedAssays()
	{
		try
		{
			if (!Common.isBootstrapped())
			{
				Util.writeln("Must provide --init parameter.");
				return;
			}
			DataStore store = Common.getDataStore();
			final String base = Common.getModulePubChem().directory;

			for (File f : new File(base).listFiles()) if (f.getName().endsWith(".zip"))
			{
				boolean deleted = store.misc().unsubmitLoadedFile(f.getAbsolutePath());
				Util.writeln("  file: " + f.getAbsolutePath() + (deleted ? " (bumped)" : " (not bumped)"));
			}

			Util.writeln("Bumped.");
		}
		catch (Exception ex)
		{
			Util.errmsg("Failed", ex);
		}
	}
	
	// goes through the compounds looking for any that have structure but no hash, and computing it
	public void updateStructureHash()
	{
		Util.writeln("Updating structure hash codes...");
		
		DataStore store = Common.getDataStore();
		
		int total = 0;
		
		while (true)
		{
			DataObject.Compound[] compounds = store.compound().fetchCompoundsNeedHash(100);
			if (compounds.length == 0) break;
			
			Util.writeln("Fetched: " + compounds.length + " (first ID=" + compounds[0].compoundID + ")");
			for (int n = 0; n < compounds.length; n++)
			{
				DataObject.Compound cpd = compounds[n];
				org.openscience.cdk.interfaces.IAtomContainer mol = ChemInf.parseMolecule(cpd.molfile);
				if (mol == null) 
				{
					Util.writeln("Warning: compoundID " + cpd.compoundID + " has structure, but is not parseable.");
					continue;
				}
				cpd.hashECFP6 = ChemInf.hashECFP6(mol);
				store.compound().updateCompound(cpd);
				total++;
			}
		}
		
		Util.writeln("Finished. Total updated: " + total);
	}
	
	// mark the given PubChem AID numbers for "reloading" (but doesn't actually delete what's there)
	public void remeasurePubChem(String[] options)
	{
		DataStore store = Common.getDataStore();

		Set<Integer> aidList = new TreeSet<>();
		for (int n = 0; n < options.length; n++)
		{
			if (options[n].equals("all"))
			{
				for (long assayID : store.assay().fetchAssayIDHaveMeasure())
				{
					DataObject.Assay assay = store.assay().getAssay(assayID);
					if (assay.uniqueID.startsWith(Identifier.PUBCHEM_PREFIX)) 
						aidList.add(Integer.valueOf(assay.uniqueID.substring(Identifier.PUBCHEM_PREFIX.length())));
				}
			}
			else aidList.add(Integer.parseInt(options[n]));
		}
		if (aidList.isEmpty()) {Util.writeln("Provide a list of PubChem AID numbers to re-measure (or 'all' to do the lot)."); return;}
		
		for (int aid : aidList)
		{
			Util.writeln("Marking AID " + aid + " for remeasurement...");
			store.assay().submitPubChemAIDMeasured(aid, false);
		}		
		
		Util.writeln("Done.");
	}
	
	// go through the entities with thresholdable activity data and make it happen
	public void thresholdPubChem()
	{
		Util.writeln("Looking for thresholds to detect...");
		
		DataStore store = Common.getDataStore();
		long[] assayIDList = store.assay().fetchAssayIDWithSchemaCurated(Common.getSchemaCAT().getSchemaPrefix());
		
		int numCalc = 0;
		skip: for (int n = 0; n < assayIDList.length; n++)
		{
			DataObject.Assay assay = store.assay().getAssay(assayIDList[n]);
			Schema schema = Common.getSchema(assay.schemaURI);
			String fieldName = null;
			if (assay.annotations == null) assay.annotations = new DataObject.Annotation[0];
			if (assay.textLabels == null) assay.textLabels = new DataObject.TextLabel[0];
			
			for (DataObject.Annotation annot : assay.annotations)
			{
				System.out.println(annot.propURI);
				if (annot.propURI.equals(PROPURI_OPERATOR)) continue skip;
			}
			for (DataObject.TextLabel label : assay.textLabels)
			{
				if (label.propURI.equals(PROPURI_FIELD)) fieldName = label.text;
				if (label.propURI.equals(PROPURI_THRESHOLD)) continue skip;
			}
			
			Util.writeFlush("  (" + (n + 1) + "/" + assayIDList.length + "): ID=" + assayIDList[n] + " ");
			
			ThresholdEstimator est = new ThresholdEstimator();
			if (!est.addAssay(assayIDList[n], fieldName))
			{
				Util.writeln("not available");
				continue;
			}
			if (!est.calculate())
			{
				Util.writeln("not calculatable");
				continue;
			}

			if (fieldName != null && !fieldName.equals(est.getFieldName())) continue;
			String op = est.getOperator();
			float thresh = (float)est.getThreshold(); // conversion to float rounds off some trailing bits; hacky but effective

			Util.writeln("[" + est.getFieldName() + "] " + ThresholdEstimator.formatComparator(op) + " " + thresh);

			for (int i = assay.annotations.length - 1; i >= 0; i--)	
			{
				if (assay.annotations[i].propURI.equals(PROPURI_OPERATOR)) assay.annotations = ArrayUtils.remove(assay.annotations, i);
			}
			for (int i = assay.textLabels.length - 1; i >= 0; i--)
			{
				if (assay.textLabels[i].propURI.equals(PROPURI_FIELD) ||
					assay.textLabels[i].propURI.equals(PROPURI_THRESHOLD)) assay.textLabels = ArrayUtils.remove(assay.textLabels, i);
			}
			
			DataObject.Annotation annotOp = null;
			DataObject.TextLabel labelField = null, labelThresh = null;

			for (Schema.Assignment assn : schema.getRoot().flattenedAssignments())
			{
				if (assn.propURI.equals(PROPURI_OPERATOR) && annotOp == null) 
					annotOp = new DataObject.Annotation(assn.propURI, op, assn.groupNest());
				if (assn.propURI.equals(PROPURI_FIELD) && labelField == null) 
					labelField = new DataObject.TextLabel(assn.propURI, est.getFieldName(), assn.groupNest());
				if (assn.propURI.equals(PROPURI_THRESHOLD) && labelThresh == null)
					labelThresh = new DataObject.TextLabel(assn.propURI, String.valueOf(thresh), assn.groupNest());
			}
			
			if (annotOp == null || labelField == null || labelThresh == null)
			{
				Util.writeln("  (schema does not have the right assignments)");
				continue;
			}
			
			assay.annotations = ArrayUtils.add(assay.annotations, annotOp);
			assay.textLabels = ArrayUtils.addAll(assay.textLabels, labelField, labelThresh);
			
			/*for (DataObject.Annotation annot : assay.annotations)
				Util.writeln(ModelSchema.collapsePrefix(annot.propURI) + " / " + ModelSchema.collapsePrefix(annot.valueURI) + " / " +
					Util.arrayStr(annot.groupNest));
			for (DataObject.TextLabel label : assay.textLabels)
				Util.writeln(ModelSchema.collapsePrefix(label.propURI) + " / [" + label.text + "] / " +
					Util.arrayStr(label.groupNest));*/
					
			store.assay().submitAssay(assay);
						
			numCalc++;
		}
		
		Util.writeln("Done. Calculated: " + numCalc);
	}
}


// repopulates the PubChem source field
/*private void updateSources(String[] options)
{
	Util.writeln("Updating PubChem sources...");
	
	DataStore store = new DataStore();

	if (options.length < 2 || !options[0].equals("--init"))
	{
		Util.writeln("Need to specify '--init {dir}' for initialization files, e.g. BAO, schema, etc.");
		return;
	}
	final String base = options[1];
	final String baseAssays = options.length >= 4 && options[2].equals("-assays") ? options[3] : base + "/assays";
	
	Util.writeln("   base=" + base + ", assays=" + baseAssays);
	
	Set<Integer> aidset = new HashSet<>();
	for (int aid : store.assay().fetchPubChemAIDWithoutSource()) aidset.add(aid);
	Util.writeln("    # assays without source = " + aidset.size());
	if (aidset.size() == 0) return;
	
	List<File> files = new ArrayList<>();
	for (File f : new File(baseAssays).listFiles()) if (f.getName().endsWith(".zip")) files.add(f);
	Collections.sort(files);
	
	final Pattern ptnSource = Pattern.compile("^(\\d+)\\.concise.json\\.gz$");
	
	for (int n = 0; n < files.size(); n++)
	{
		File f = files.get(n);
		Util.writeln("Scanning: [" + f.getAbsolutePath() + "]");
					
		ZipInputStream zip = new ZipInputStream(new FileInputStream(f));

		ZipEntry ze = zip.getNextEntry();
		while (ze != null)
		{
			String path = ze.getName(), name = new File(path).getName();
			
			Matcher m = ptnSource.matcher(name);
			if (m.matches())
			{
				int aid = Integer.valueOf(m.group(1));
				if (aidset.contains(aid))
				{
					GZIPInputStream gzip = new GZIPInputStream(zip);
					JSONObject json = new JSONObject(new JSONTokener(new BufferedReader(new InputStreamReader(gzip))));
					JSONObject root = json.optJSONObject("PC_AssaySubmit");
					if (root != null) 
					{
						JSONObject descr = root.getJSONObject("assay").getJSONObject("descr");
						String source = descr.getJSONObject("aid_source").getJSONObject("db").getString("name");
						Util.writeln("    filling source AID " + aid + " from [" + source + "]");
						store.assay().submitAssayPubChemSource(aid, source);
					}
				}
			}
			
			zip.closeEntry();
			ze = zip.getNextEntry();
		}
		
		zip.close();
	}
	
	Util.writeln("Done.");
}*/

// read through PubChem source files and pull out the 'xrefs' 
/*private void updateXrefs(String[] options)
{
	Util.writeln("Updating PubChem xrefs...");
	
	DataStore store = new DataStore();

	if (options.length < 2 || !options[0].equals("--init"))
	{
		Util.writeln("Need to specify '--init {dir}' for initialization files, e.g. BAO, schema, etc.");
		return;
	}
	final String base = options[1];
	final String baseAssays = options.length >= 4 && options[2].equals("-assays") ? options[3] : base + "/assays";
	
	Util.writeln("   base=" + base + ", assays=" + baseAssays);

	int[] aidlist = store.assay().fetchAllPubChemAID();
	Set<Integer> aidset = new HashSet<>();
	for (int aid : aidlist) aidset.add(aid);
	
	Util.writeln("  All PubChem assays: " + aidlist.length);

	List<File> files = new ArrayList<>();
	for (File f : new File(baseAssays).listFiles()) if (f.getName().endsWith(".zip")) files.add(f);
	Collections.sort(files);
	
	final Pattern ptnSource = Pattern.compile("^(\\d+)\\.concise.json\\.gz$");
	
	for (int n = 0; n < files.size(); n++)
	{
		File f = files.get(n);
		Util.writeln("Scanning: [" + f.getAbsolutePath() + "]");
					
		ZipInputStream zip = new ZipInputStream(new FileInputStream(f));

		ZipEntry ze = zip.getNextEntry();
		while (ze != null)
		{
			String path = ze.getName(), name = new File(path).getName();
			
			Matcher m = ptnSource.matcher(name);
			if (m.matches())
			{
				int aid = Integer.valueOf(m.group(1));
				if (aidset.contains(aid))
				{
					//Util.writeln(" ** replacing text for: " + aid);
					
					GZIPInputStream gzip = new GZIPInputStream(zip);
					JSONObject json = new JSONObject(new JSONTokener(new BufferedReader(new InputStreamReader(gzip))));
					JSONObject root = json.optJSONObject("PC_AssaySubmit");
					if (root != null) replaceAssayXRef(aid, root, store);
				}
			}
			
			zip.closeEntry();
			ze = zip.getNextEntry();
		}
		
		zip.close();
	}
	
	Util.writeln("Done.");
}
private void replaceAssayXRef(int aid, JSONObject root, DataStore store)
{
	JSONObject descr = root.getJSONObject("assay").getJSONObject("descr");
	JSONArray jsonXRef = descr.optJSONArray("xref"), jsonTarget = descr.optJSONArray("target");
	
	List<DataObject.PubChemXRef> xrefs = new ArrayList<>();
	Set<Integer> geneID = new TreeSet<>(), proteinGI = new TreeSet<>();

	if (jsonXRef != null) for (int n = 0; n < jsonXRef.length(); n++)
	{
		JSONObject obj1 = jsonXRef.getJSONObject(n), obj2 = obj1.getJSONObject("xref");
	
		DataObject.PubChemXRef xref = new DataObject.PubChemXRef();
		xref.comment = obj1.optString("comment", "");
		for (Iterator<String> it = obj2.keys(); it.hasNext();)
		{
			xref.type = it.next();
			xref.id = obj2.getString(xref.type);
			break;
		}
		xrefs.add(xref);
		
		if (xref.type.equals("gene")) geneID.add(Integer.valueOf(xref.id));
		else if (xref.type.equals("protein_gi")) proteinGI.add(Integer.valueOf(xref.id));
	}
	
	if (jsonTarget != null) for (int n = 0; n < jsonTarget.length(); n++)
	{
		JSONObject obj = jsonTarget.getJSONObject(n);
		if (!obj.optString("molecule_type", "").equals("protein")) continue;
		DataObject.PubChemXRef xref = new DataObject.PubChemXRef();
		xref.comment = obj.optString("name", "");
		xref.type = "protein_gi";
		xref.id = obj.optString("mol_id", "");
		if (xref.id.length() > 0) 
		{
			xrefs.add(xref);
			proteinGI.add(Integer.valueOf(xref.id));
		}
	}
	
	// remove duplicates, which happens with 'gene' from time to time
	for (int i = 0; i < xrefs.size() - 1; i++)
	{
		DataObject.PubChemXRef xref1 = xrefs.get(i);
		for (int j = i + 1; j < xrefs.size(); j++)
		{
			DataObject.PubChemXRef xref2 = xrefs.get(j);
			if (xref1.type.equals(xref2.type) && xref1.id.equals(xref2.id))
			{
				if (xref1.comment == null) xrefs.remove(i); else xrefs.remove(j);
				i--;
				break;
			}
		}
	}
	
	if (xrefs.size() == 0) return;

	Util.writeln("Updating [" + aid + "], #xrefs=" + xrefs.size() + ", geneID=" + geneID + ", proteinGI=" + proteinGI);
	store.assay().submitAssayPubChemXRefs(aid, xrefs.toArray(new DataObject.PubChemXRef[xrefs.size()]));
	
	if (geneID.size() > 0 || proteinGI.size() > 0)
	{
		DataObject.Assay assay = store.assay().getAssayFromPubChemAID(aid);
		Set<String> gotValues = new HashSet<>();
		if (assay.annotations != null) for (DataObject.Annotation annot : assay.annotations) gotValues.add(annot.valueURI);
		boolean modified = false;
		for (int id : geneID)
		{
			String valueURI = ModelSchema.PFX_GENEID + id;
			if (gotValues.contains(valueURI)) continue;
			int idx = assay.annotations.length;
			assay.annotations = Arrays.copyOf(assay.annotations, idx + 1);
			assay.annotations[idx] = new DataObject.Annotation(ModelSchema.PFX_BAO + "BAX_0000011", valueURI);
			modified = true;
		}
		for (int id : proteinGI)
		{
			String valueURI = ModelSchema.PFX_PROTEIN + id;
			if (gotValues.contains(valueURI)) continue;
			int idx = assay.annotations.length;
			assay.annotations = Arrays.copyOf(assay.annotations, idx + 1);
			assay.annotations[idx] = new DataObject.Annotation(ModelSchema.PFX_BAO + "BAX_0000012", valueURI);
			modified = true;
		}
		if (modified)
		{
			Util.writeln(" ... updating annotations for geneID/proteinGI");
			store.assay().submitAssayAnnotations(assay.assayID, assay.annotations);
		}
	}
}*/


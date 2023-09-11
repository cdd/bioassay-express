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

import com.cdd.bae.data.*;
import com.cdd.bae.model.assocrules.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import org.apache.commons.lang3.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.json.*;

/*
	Command line functionality pertaining to maintenance: things like rebuilding models & such.
*/

public class TransferCommands implements Main.ExecuteBase
{
	private static final String JSON_SUFFIX = ".json";
	private DataStore store = null;

	// ------------ public methods ------------

	public void execute(String[] args)
	{
		if (args.length == 0) {printHelp(); return;}
		
		String cmd = args[0];
		String[] options = ArrayUtils.remove(args, 0);

		if (cmd.equals("exportassays")) exportAssays(options);
		else if (cmd.equals("importassays")) importAssays(options, false);
		else if (cmd.equals("augmentassays")) importAssays(options, true);
		else if (cmd.equals("exclusivecurated")) exclusiveCurated(options);
		else if (cmd.equals("exportannotations")) exportAnnotations(options);
		else if (cmd.equals("exportholding")) exportHolding(options);
		else if (cmd.equals("importholding")) importHolding(options);
		else if (cmd.equals("importprovisional")) importProvisional(options);
		else Util.writeln("Unknown command: '" + cmd + "'.");
	}
	
	public void printHelp()
	{
		Util.writeln("Transfer commands");
		Util.writeln("    exporting/importing content");
		Util.writeln("Options:");
		Util.writeln("    exportassays {fn.zip}: writes all curated assays to a ZIP archive");
		Util.writeln("    importassays {fn.zip}: adds or updates assays from ZIP archive; clobbers existing assays");
		Util.writeln("    augmentassays {fn.zip}: as for import, but appends annotations rather than clobbering");
		Util.writeln("    exclusivecurated {fn.zip}: resets curated status of assays");
		Util.writeln("    annotations {fn.txt}: export assay annotations as items sets");
		Util.writeln("    exportholding {fn.zip}: writes holding bay content to a ZIP archive");
		Util.writeln("    importholding {fn.zip}: adds entries from ZIP archive to holding bay");
		Util.writeln("    importprovisional {fn.ttl}: recreates provisional terms");
	}
	
	// ------------ private methods ------------

	// check that options only contain a single zip file name
	protected static String getFilename(String[] options, String suffix)
	{
		String fn = null;
		if (options.length != 1)
			Util.writeln("Export: must provide filename (" + suffix + ") and nothing else");
		else if (!options[0].endsWith(suffix))
			Util.writeln("Filename must have the " + suffix + " suffix");
		else
			fn = options[0];
		return fn;
	}
	
	// check that application is initialized
	private boolean isInitialized()
	{
		if (Common.isBootstrapped()) return true;
		Util.writeln("Must specify --init parameter.");
		return false;
	}

	// pulls out all of the curated assays and writes them to a ZIP file 
	private void exportAssays(String[] options)
	{
		String fn = getFilename(options, ".zip");
		if (fn == null) return;
		if (!isInitialized()) return;

		Util.writeln("Exporting curated assays to [" + fn + "]");
	
		store = Common.getDataStore();
		
		long[] assayIDList = store.assay().fetchAssayIDCurated();
		Util.writeln("    curated assays: " + assayIDList.length);
		if (assayIDList.length == 0)
		{
			Util.writeln("Nothing to export.");
			return;
		}
		
		File file = new File(fn);
		try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file))))
		{
			int orphan = 0;
			for (int n = 0; n < assayIDList.length; n++)
			{
				DataObject.Assay assay = store.assay().getAssay(assayIDList[n]);
				if (assay == null)
				{
					Util.writeln("Error: assayID " + assayIDList[n] + " not found in database.");
					return;
				}
				
				if (n % 100 == 99) Util.writeln(String.format("    writing %d of %d...", n + 1, assayIDList.length));
				
				// NOTE: may want to exclude anything without a PubChemAID (or other subsequently added unique identifiers), since
				// they can't be reliably tracked from one database to another
				
				String code = String.valueOf(++orphan);
				if (assay.assayID > 0) code = "id" + assay.assayID;
				
				zip.putNextEntry(new ZipEntry("assay_" + code + JSON_SUFFIX));
				JSONObject json = AssayJSON.serialiseAssay(assay);

				Writer wtr = new OutputStreamWriter(zip);
				json.write(wtr);
				wtr.flush();

				zip.closeEntry();
			}
			Util.writeln("Export complete.");
		}
		catch (JSONException | IOException ex) {Util.errmsg("Export failed", ex);}
	}
	
	// import assays found in the ZIP file, as long as they are different in some way
	private void importAssays(String[] options, boolean augment)
	{
		boolean force = false;
		for (int n = 0; n < options.length; n++)
		{
			if (options[n].equals("--force")) {force = true; options = ArrayUtils.remove(options, n); n--;}
		}
		
		if (options.length == 0) {Util.errmsg("Must provide at least one filename."); return;}
		
		for (String optFN : options)
		{
			String fn = getFilename(new String[]{optFN}, ".zip");
			if (fn == null) return;
			if (!isInitialized()) return;
	
			Util.writeln("Importing curated assays from [" + fn + "]");
			File file = new File(fn);
			if (!file.exists()) {Util.writeln("File not found."); return;}
	
			if (force) Util.writeln("NOTE: force flag specified, importing even when identical");
			
			store = Common.getDataStore();
			store.setNotifier(null);
			
			try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(file))))
			{
				int numEntries = 0, numParsed = 0, numUpdated = 0;
				ZipEntry ze = zip.getNextEntry();
				while (ze != null)
				{
					String path = ze.getName();
					if (path.endsWith(JSON_SUFFIX))
					{
						try
						{
							numEntries++;
							JSONObject json = new JSONObject(new JSONTokener(new InputStreamReader(zip)));
							DataObject.Assay assay = AssayJSON.deserialiseAssay(json);
							if (assay != null) 
							{
								numParsed++;
								if (updateAssay(assay, augment, force)) numUpdated++;
							}
						}
						catch (JSONException ex) 
						{
							Util.writeln("File unparseable: " + path);
							ex.printStackTrace();
							break;
						}
					}
					
					zip.closeEntry();
					ze = zip.getNextEntry();
				}
		
				Util.writeln("Import complete.");
				Util.writeln("    Found assays: " + numEntries);
				Util.writeln("    Parsed assays: " + numParsed);
				Util.writeln("    Updated assays: " + numUpdated);
			}
			catch (IOException ex) {Util.errmsg("Import failed", ex);}
		}
	}

	// looks through the zip file for all uniqueIDs that are considered to be "curated"; anything not in this list needs gets marked
	// as uncurated
	private void exclusiveCurated(String[] options)
	{
		String fn = getFilename(options, ".zip");
		if (fn == null) return;
		if (!isInitialized()) return;

		Util.writeln("Importing curated assays from [" + fn + "]");
		File file = new File(fn);
		if (!file.exists()) {Util.writeln("File not found."); return;}
		
		store = Common.getDataStore();
		
		Set<String> valid = new HashSet<>();
		
		try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(file))))
		{
			ZipEntry ze = zip.getNextEntry();
			while (ze != null)
			{
				String path = ze.getName();
				if (path.endsWith(JSON_SUFFIX))
				{
					try
					{
						JSONObject json = new JSONObject(new JSONTokener(new InputStreamReader(zip)));
						DataObject.Assay assay = AssayJSON.deserialiseAssay(json);
						if (assay != null) valid.add(assay.uniqueID);
					}
					catch (JSONException ex) 
					{
						Util.writeln("File unparseable: " + path);
						ex.printStackTrace();
						break;
					}
				}
				
				zip.closeEntry();
				ze = zip.getNextEntry();
			}
		}
		catch (IOException ex) {Util.errmsg("Import failed", ex);}	
	
		Util.writeln("Identifiers considered as curated: " + valid.size());

		long[] assayIDList = store.assay().fetchAssayIDCurated();
		Util.writeln("Assays to check: " + assayIDList.length);

		long timeThen = new Date().getTime();
		int numChanged = 0;
		for (int n = 0; n < assayIDList.length; n++)
		{
			long timeNow = new Date().getTime();
			if (timeNow > timeThen + 1000)
			{
				Util.writeln("    progress: " + (n + 1) + "/" + assayIDList.length + ", changed: " + numChanged);
				timeThen = timeNow;
			}
			DataObject.Assay assay = store.assay().getAssay(assayIDList[n]);
			if (assay == null) continue;
			
			if (assay.isCurated && !valid.contains(assay.uniqueID))
			{
				Util.writeln("    marking uncurated: ID=" + assay.assayID + " / unique=" + assay.uniqueID);
				store.assay().submitIsCurated(assayIDList[n], false);
				numChanged++;
			}
		}
		
		Util.writeln("Done. Total assays changed: " + numChanged);
	}
	
	private void exportAnnotations(String[] options)
	{
		String fn = getFilename(options, ".txt");
		if (fn == null) return;
		if (!isInitialized()) return;
		
		store = Common.getDataStore();
		
		Util.writeln("Exporting curated assays to [" + fn + "]");
		long[] assayIDList = store.assay().fetchAssayIDCurated();
		Util.writeln("    curated assays: " + assayIDList.length);
		try
		{
			ARModel.exportAnnotations(fn);
		}
		catch (IOException e)
		{
			Util.errmsg("Export failed", e);
		}
	}
	
	// package up everything in the holding bay
	private void exportHolding(String[] options)
	{
		String fn = getFilename(options, ".zip");
		if (fn == null) return;
		if (!isInitialized()) return;

		Util.writeln("Exporting holding bay to [" + fn + "]");
	
		store = Common.getDataStore();
		
		long[] holdingIDList = store.holding().fetchHoldings();
		Util.writeln("    holding bay entries: " + holdingIDList.length);
		if (holdingIDList.length == 0)
		{
			Util.writeln("Nothing to export.");
			return;
		}
		
		File file = new File(fn);
		try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file))))
		{
			int orphan = 0;
			for (int n = 0; n < holdingIDList.length; n++)
			{
				DataObject.Holding holding = store.holding().getHolding(holdingIDList[n]);
				if (holding == null)
				{
					Util.writeln("Error: holdingID " + holdingIDList[n] + " not found in database.");
					return;
				}
				
				if (n % 100 == 99) Util.writeln(String.format("    writing %d of %d...", n + 1, holdingIDList.length));
				
				// NOTE: may want to exclude anything without a PubChemAID (or other subsequently added unique identifiers), since
				// they can't be reliably tracked from one database to another
				
				zip.putNextEntry(new ZipEntry("holding_" + holding.holdingID + JSON_SUFFIX));
				JSONObject json = serialiseHolding(holding);

				Writer wtr = new OutputStreamWriter(zip);
				json.write(wtr);
				wtr.flush();

				zip.closeEntry();
			}
			Util.writeln("Export complete.");
		}
		catch (JSONException | IOException ex) {Util.errmsg("Export failed", ex);}
	}
	
	// bring in everything to the holding bay
	private void importHolding(String[] options)
	{
		String fn = getFilename(options, ".zip");
		if (fn == null) return;
		if (!isInitialized()) return;

		Util.writeln("Importing curated holding bay from [" + fn + "]");
		File file = new File(fn);
		if (!file.exists()) {Util.writeln("File not found."); return;}

		store = Common.getDataStore();
		
		try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(file))))
		{
			int numEntries = 0, numParsed = 0;
			ZipEntry ze = zip.getNextEntry();
			while (ze != null)
			{
				String path = ze.getName();
				if (path.endsWith(JSON_SUFFIX))
				{
					try
					{
						numEntries++;
						JSONObject json = new JSONObject(new JSONTokener(new InputStreamReader(zip)));
						DataObject.Holding holding = deserialiseHolding(json);
						if (holding != null) 
						{
							numParsed++;
							store.holding().depositHolding(holding);
						}
						else Util.writeln("Unparseable file [" + path + "]");
					}
					catch (JSONException ex) 
					{
						Util.writeln("File unparseable: " + path);
						ex.printStackTrace();
						break;
					}
				}
				
				zip.closeEntry();
				ze = zip.getNextEntry();
			}
	
			Util.writeln("Holding import complete.");
			Util.writeln("    Found entries: " + numEntries);
			Util.writeln("    Parsed entries: " + numParsed);
		}
		catch (IOException ex) {Util.errmsg("Import failed", ex);}
	}
	
	// import provisional terms, that had been exported as a TTL file (interactively via DownloadProvisional class)
	private void importProvisional(String[] options)
	{
		String fn = getFilename(options, ".ttl");
		if (fn == null) return;
		if (!isInitialized()) return;
		
		Util.writeln("Importing provisional terms from [" + fn + "]");
		File file = new File(fn);
		if (!file.exists()) {Util.writeln("File not found."); return;}

		store = Common.getDataStore();
	
		Model model = ModelFactory.createDefaultModel();
		try {RDFDataMgr.read(model, new FileInputStream(file), Lang.TTL);}
		catch (Exception ex) {ex.printStackTrace(); return;}
		
		Resource resRoot = model.createResource(ModelSchema.expandPrefix("bae:ProvisionalTerms"));
		Property propSubClass = model.createProperty(ModelSchema.expandPrefix("rdfs:subClassOf"));
		Property propOntoParent = model.createProperty(ModelSchema.expandPrefix("bat:ontologyParent"));
		Property propLabel = model.createProperty(ModelSchema.expandPrefix("rdfs:label"));
		Property propDescr = model.createProperty(ModelSchema.expandPrefix("obo:IAO_0000115"));
		Property propExplan = model.createProperty(ModelSchema.expandPrefix("bat:hasExplanation"));
		Property propID = model.createProperty(ModelSchema.expandPrefix("bat:hasProvisionalID"));
		Property propProposer = model.createProperty(ModelSchema.expandPrefix("bat:hasProposerID"));
		Property propRole = model.createProperty(ModelSchema.expandPrefix("bat:hasRole"));
		Property propCreated = model.createProperty(ModelSchema.expandPrefix("bat:hasCreatedDate"));
		Property propModified = model.createProperty(ModelSchema.expandPrefix("bat:hasModifiedDate"));
		Property propRemapTo = model.createProperty(ModelSchema.expandPrefix("bat:remapTo"));
		Property propBridgeStatus = model.createProperty(ModelSchema.expandPrefix("bat:hasBridgeStatus"));
		Property propBridgeURL = model.createProperty(ModelSchema.expandPrefix("bat:hasBridgeURL"));
		Property propBridgeToken = model.createProperty(ModelSchema.expandPrefix("bat:hasBridgeToken"));		
		
		int numImported = 0;
		
		for (StmtIterator iter = model.listStatements(null, propSubClass, resRoot); iter.hasNext();)
		{
			Statement stmt = iter.next();
			Resource subject = stmt.getSubject();
			
			var prov = new DataObject.Provisional();
			prov.uri = subject.getURI();
			
			for (StmtIterator iter2 = model.listStatements(subject, null, (RDFNode)null); iter2.hasNext();)
			{
				Statement stmt2 = iter2.next();
				Property predicate = stmt2.getPredicate();
				RDFNode object = stmt2.getObject();
				
				if (predicate.equals(propOntoParent)) prov.parentURI = object.asResource().getURI();
				else if (predicate.equals(propLabel)) prov.label = object.asLiteral().toString();
				else if (predicate.equals(propDescr)) prov.description = object.asLiteral().toString();
				else if (predicate.equals(propExplan)) prov.explanation = object.asLiteral().toString();
				else if (predicate.equals(propProposer)) prov.proposerID = object.asLiteral().toString();
				else if (predicate.equals(propRole)) prov.role = DataObject.ProvisionalRole.fromString(object.asLiteral().toString());
				else if (predicate.equals(propRemapTo)) prov.remappedTo = object.asLiteral().toString();
				else if (predicate.equals(propBridgeStatus)) prov.bridgeStatus = object.asLiteral().toString();
				else if (predicate.equals(propBridgeURL)) prov.bridgeURL = object.asLiteral().toString();
				else if (predicate.equals(propBridgeToken)) prov.bridgeToken = object.asLiteral().toString();
			}
			
			store.provisional().updateProvisional(prov);
			
			numImported++;
		}
		
		Util.writeln("Done. Number imported: " + numImported);
	}
	
	// contemplates an assay for potential importing; returns true if something happened, i.e. it was added or changed on account of
	// being different to the current content; false means that it wasn't different, or lacked a suitable identifier
	private boolean updateAssay(DataObject.Assay assay, boolean augment, boolean force)
	{
		//if (Util.isBlank(assay.uniqueID)) return false; // must have a uniqueID, otherwise it isn't tracked...

		DataObject.Assay prevAssay = Util.notBlank(assay.uniqueID) ? store.assay().getAssayFromUniqueID(assay.uniqueID) : null;
		
		if (prevAssay == null)
		{
			assay.assayID = 0; // a new one will be assigned
			store.assay().setAssay(assay);
			return true;
		}

		if (!augment)
		{
			// the new assay is presumed to be self-contained, and overwrites the existing one competely
		
			if (!force && assaysEquivalent(assay, prevAssay)) return false;
			assay.assayID = prevAssay.assayID;
			if (assay.curationTime == null) assay.curationTime = new Date();
			store.assay().setAssay(assay);
		}
		else
		{
			// the new assay has some annotations which will be spliced into the old one whenever new
		
			DataObject.Assay augmentedAssay = augmentAssay(prevAssay, assay);
			if (augmentedAssay == null) return false; // this means nothing changed
			store.assay().submitAssay(augmentedAssay);
		}
	
		return true;
	}
	
	// if the two assays are equivalent enough to not bother updating, returns true
	public static boolean assaysEquivalent(DataObject.Assay assay1, DataObject.Assay assay2)
	{
		if (!Util.equals(assay1.text, assay2.text) || !Util.equals(assay1.schemaURI, assay2.schemaURI)) return false;
		
		if (!annotationsSame(assay1.annotations, assay2.annotations)) return false;
		
		if (assay1.textLabels.length != assay2.textLabels.length) return false;
		for (int n = 0; n < assay1.textLabels.length; n++)
		{
			if (!Util.equals(assay1.textLabels[n].propURI, assay2.textLabels[n].propURI) ||
				!Util.equals(assay1.textLabels[n].text, assay2.textLabels[n].text)) return false;
		}
		
		//if (!Util.equals(assay1.pubchemSource, assay2.pubchemSource)) return false;
		
		if (ArrayUtils.getLength(assay1.pubchemXRefs) != ArrayUtils.getLength(assay2.pubchemXRefs)) return false;
		for (int n = 0; n < assay1.pubchemXRefs.length; n++)
		{
			if (!Util.equals(assay1.pubchemXRefs[n].type, assay2.pubchemXRefs[n].type) ||
				!Util.equals(assay1.pubchemXRefs[n].id, assay2.pubchemXRefs[n].id) ||
				!Util.equals(assay1.pubchemXRefs[n].comment, assay2.pubchemXRefs[n].comment)) return false;
		}
		
		if (!Util.equals(assay1.curatorID, assay2.curatorID)) return false;

		if (ArrayUtils.getLength(assay1.history) != ArrayUtils.getLength(assay2.history)) return false;
		for (int n = 0; n < ArrayUtils.getLength(assay1.history); n++)
		{
			DataObject.History h1 = assay1.history[n], h2 = assay2.history[n];
			if (!h1.curationTime.equals(h2.curationTime) || !Util.equals(h1.curatorID, h2.curatorID)) return false;
			if (!annotationsSame(h1.annotsAdded, h2.annotsAdded) || !annotationsSame(h1.annotsRemoved, h2.annotsRemoved)) return false;
		}
		
		if (assay1.isCurated != assay2.isCurated) return false;

		long time1 = assay1.curationTime == null ? 0 : assay1.curationTime.getTime();
		long time2 = assay2.curationTime == null ? 0 : assay2.curationTime.getTime();
		if (time1 != time2) return false;

		return true;
	}
	
	protected static boolean annotationsSame(DataObject.Annotation[] annot1, DataObject.Annotation[] annot2)
	{
		if (ArrayUtils.getLength(annot1) != ArrayUtils.getLength(annot2)) return false;
		for (int n = 0; n < ArrayUtils.getLength(annot1); n++)
		{
			if (!Util.equals(annot1[n].propURI, annot2[n].propURI) ||
				!Util.equals(annot1[n].valueURI, annot2[n].valueURI)) return false;
			String[] groupNest1 = annot1[n].groupNest != null ? annot1[n].groupNest : new String[0];
			String[] groupNest2 = annot2[n].groupNest != null ? annot2[n].groupNest : new String[0];
			if (!Arrays.deepEquals(groupNest1, groupNest2)) return false;
		}
		return true;
	}
	
	// for a starting assay, modifies it by adding extra annotations from the second assay, whenever nondegenerate; if nothing new was
	// added, returns null
	protected static DataObject.Assay augmentAssay(DataObject.Assay assay, DataObject.Assay extra)
	{
		boolean modified = !assay.schemaURI.equals(extra.schemaURI);
		assay.schemaURI = extra.schemaURI;
		
		skip: for (DataObject.Annotation annot : extra.annotations)
		{
			for (DataObject.Annotation look : assay.annotations) 
				if (annot.valueURI.equals(look.valueURI) && annot.matchesProperty(look.propURI, look.groupNest)) continue skip;
			assay.annotations = ArrayUtils.add(assay.annotations, annot);
			modified = true;
		}
		
		skip: for (DataObject.TextLabel label : extra.textLabels)
		{
			for (DataObject.TextLabel look : assay.textLabels)
				if (label.text.equals(look.text) && label.matchesProperty(look.propURI, look.groupNest)) continue skip;
			assay.textLabels = ArrayUtils.add(assay.textLabels, label);
			modified = true;
		}

		return modified ? assay : null;
	}
	
	// convert to/from JSON for inclusion in the ZIP file (note this is similar to the REST GetHoldingBay API, not not identical)
	private JSONObject serialiseHolding(DataObject.Holding holding)
	{
		JSONObject json = new JSONObject();
		
		// lookup the assay and see if it has a uniqueID: this is the only way to complete the export/import consistency
		if (holding.assayID != 0)
		{
			DataObject.Assay assay = store.assay().getAssay(holding.assayID);
			if (assay != null && Util.notBlank(assay.uniqueID)) json.put("assayUID", assay.uniqueID);
		}
		
		if (holding.submissionTime != null) json.put("submissionTime", holding.submissionTime.getTime());
		if (Util.notBlank(holding.curatorID)) json.put("curatorID", holding.curatorID);
		if (Util.notBlank(holding.uniqueID)) json.put("uniqueID", holding.uniqueID); // (this is a modification overwrite, not a lookup-field)
		if (Util.notBlank(holding.schemaURI)) json.put("schemaURI", holding.schemaURI);
		if (holding.schemaBranches != null)
		{
			JSONArray list = new JSONArray();
			for (DataObject.SchemaBranch branch : holding.schemaBranches) list.put(AssayJSON.serialiseBranch(branch));
			json.put("schemaBranches", list);
		}
		if (holding.schemaDuplication != null)
		{
			JSONArray list = new JSONArray();
			for (DataObject.SchemaDuplication dupl : holding.schemaDuplication) list.put(AssayJSON.serialiseDuplication(dupl));
			json.put("schemaDuplication", list);
		}
		json.put("deleteFlag", holding.deleteFlag);
		if (Util.notBlank(holding.text)) json.put("text", holding.text);
		
		if (ArrayUtils.getLength(holding.annotsAdded) > 0)
		{
			JSONArray list = new JSONArray();
			for (DataObject.Annotation annot : holding.annotsAdded) list.put(AssayJSON.serialiseAnnotation(annot, null, null));
			json.put("annotsAdded", list);
		}
		if (ArrayUtils.getLength(holding.annotsRemoved) > 0)
		{
			JSONArray list = new JSONArray();
			for (DataObject.Annotation annot : holding.annotsRemoved) list.put(AssayJSON.serialiseAnnotation(annot, null, null));
			json.put("annotsRemoved", list);
		}
		if (ArrayUtils.getLength(holding.labelsAdded) > 0)
		{
			JSONArray list = new JSONArray();
			for (DataObject.TextLabel label : holding.labelsAdded) list.put(AssayJSON.serialiseTextLabel(label, null));
			json.put("labelsAdded", list);
		}
		if (ArrayUtils.getLength(holding.labelsRemoved) > 0)
		{
			JSONArray list = new JSONArray();
			for (DataObject.TextLabel label : holding.labelsRemoved) list.put(AssayJSON.serialiseTextLabel(label, null));
			json.put("labelsRemoved", list);
		}
		
		return json;
	}
	private DataObject.Holding deserialiseHolding(JSONObject json)
	{
		DataObject.Holding holding = new DataObject.Holding();
		
		// if possible, correlate the lookup uniqueID to an existing assayID
		String uid = json.optString("assayUID", null);
		if (Util.notBlank(uid))
		{
			DataObject.Assay assay = store.assay().getAssayFromUniqueID(uid);
			if (assay != null) holding.assayID = assay.assayID;
		}
		
		long time = json.optLong("submissionTime", 0);
		if (time != 0) holding.submissionTime = new Date(time);
		holding.curatorID = json.optString("curatorID", null);
		holding.uniqueID = json.optString("uniqueID", null);
		holding.schemaURI = json.optString("schemaURI", null);
		
		JSONArray listBranches = json.optJSONArrayEmpty("schemaBranches"), listDuplication = json.optJSONArrayEmpty("schemaDuplication");
		if (listBranches.length() > 0)
		{
			holding.schemaBranches = new DataObject.SchemaBranch[listBranches.length()];
			for (int n = 0; n < holding.schemaBranches.length; n++)
				holding.schemaBranches[n] = AssayJSON.deserialiseBranch(listBranches.getJSONObject(n));
		}
		if (listDuplication.length() > 0)
		{
			holding.schemaDuplication = new DataObject.SchemaDuplication[listDuplication.length()];
			for (int n = 0; n < holding.schemaDuplication.length; n++)
				holding.schemaDuplication[n] = AssayJSON.deserialiseDuplication(listDuplication.getJSONObject(n));
		}
		
		holding.deleteFlag = json.optBoolean("deleteFlag", false);
		holding.text = json.optString("text", null);
		
		JSONArray listAnnotsAdded = json.optJSONArrayEmpty("annotsAdded"), listAnnotsRemoved = json.optJSONArrayEmpty("annotsRemoved");
		if (listAnnotsAdded.length() > 0)
		{
			holding.annotsAdded = new DataObject.Annotation[listAnnotsAdded.length()];
			for (int n = 0; n < holding.annotsAdded.length; n++)
				holding.annotsAdded[n] = deserialiseAnnotation(listAnnotsAdded.getJSONObject(n));
		}
		if (listAnnotsRemoved.length() > 0)
		{
			holding.annotsRemoved = new DataObject.Annotation[listAnnotsRemoved.length()];
			for (int n = 0; n < holding.annotsRemoved.length; n++)
				holding.annotsRemoved[n] = deserialiseAnnotation(listAnnotsRemoved.getJSONObject(n));
		}
		
		JSONArray listLabelsAdded = json.optJSONArrayEmpty("labelsAdded"), listLabelsRemoved = json.optJSONArrayEmpty("labelsRemoved");
		if (listLabelsAdded.length() > 0)
		{
			holding.labelsAdded = new DataObject.TextLabel[listLabelsAdded.length()];
			for (int n = 0; n < holding.labelsAdded.length; n++)
				holding.labelsAdded[n] = deserialiseTextLabel(listLabelsAdded.getJSONObject(n));
		}
		if (listLabelsRemoved.length() > 0)
		{
			holding.labelsRemoved = new DataObject.TextLabel[listLabelsRemoved.length()];
			for (int n = 0; n < holding.labelsRemoved.length; n++)
				holding.labelsRemoved[n] = deserialiseTextLabel(listLabelsRemoved.getJSONObject(n));
		}
		
		return holding;
	}
	private DataObject.Annotation deserialiseAnnotation(JSONObject json)
	{
		DataObject.Annotation annot = new DataObject.Annotation();
		annot.propURI = json.getString(AssayJSON.PROP_URI);
		annot.groupNest = json.has(AssayJSON.GROUP_NEST) ? json.getJSONArray(AssayJSON.GROUP_NEST).toStringArray() : null;
		annot.valueURI = json.getString(AssayJSON.VALUE_URI);
		return annot;
	}
	private DataObject.TextLabel deserialiseTextLabel(JSONObject json)
	{
		DataObject.TextLabel label = new DataObject.TextLabel();
		label.propURI = json.getString(AssayJSON.PROP_URI);
		label.groupNest = json.has(AssayJSON.GROUP_NEST) ? json.getJSONArray(AssayJSON.GROUP_NEST).toStringArray() : null;
		label.text = json.getString(AssayJSON.VALUE_LABEL);
		return label;
	}
}

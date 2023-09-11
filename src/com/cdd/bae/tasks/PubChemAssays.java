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

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import static com.cdd.bae.tasks.PubChemMeasurements.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

import javax.servlet.*;

import org.apache.commons.lang3.*;
import org.json.*;

/*
	Background task: loading assays that have been stacked up in the appropriate directory, waiting to be processed.
	
	Source for PubChem assays is: ftp://ftp.ncbi.nlm.nih.gov/pubchem/Bioassay/Concise/JSON ... every zip file in the directory is fair game.
*/

public class PubChemAssays extends BaseMonitor implements Runnable
{
	private static PubChemAssays main = null;
	private final Object mutexMain = new Object(), mutexFiles = new Object(), mutexDownload = new Object();
	private final Object mutexMeasure = new Object(), mutexCompound = new Object();
	protected DataStore store = null;
	private boolean busy = false;
	private InitParams.ModulePubChem module = null;
	
	private List<Integer> whitelistAID = new ArrayList<>();
	
	public static final String MODE_OF_ACTION_URI = ModelSchema.PFX_BAO + "BAO_0000196";
	public static final String DETECTION_METHOD_URI = ModelSchema.PFX_BAO + "BAO_0000207";
	public static final String RELATED_URI = ModelSchema.PFX_BAO + "BAO_0000539";
	public static final String CELLLINE_URI = ModelSchema.PFX_BAO + "BAO_0002800";
	public static final String SOURCE_URI = ModelSchema.PFX_BAO + "BAO_0002852";
	public static final String TITLE_URI = ModelSchema.PFX_BAO + "BAO_0002853";
	public static final String ASSAYTYPE_URI = ModelSchema.PFX_BAO + "BAO_0002854";
	public static final String DETECTION_INSTRUMENT_URI = ModelSchema.PFX_BAO + "BAO_0002865";
	public static final String ASSAYFOOTPRINT_URI = ModelSchema.PFX_BAO + "BAO_0002867";
	public static final String ORGANISM_URI = ModelSchema.PFX_BAO + "BAO_0002921";
	public static final String GENEID_URI = ModelSchema.PFX_BAO + "BAX_0000011";
	public static final String PROTEINID_URI = ModelSchema.PFX_BAO + "BAX_0000012";
	public static final String DISEASE_URI = ModelSchema.PFX_BAO + "BAO_0002848";
	
	// ------------ lifecycle ------------

	@Override
	public void contextInitialized(ServletContextEvent ev) 
	{
		super.contextInitialized(ev);
		
		if (Common.getConfiguration() == null || Common.getParams() == null || Common.getModulePubChem() == null)
		{
			logger.info("Configuration not available or invalid: disabled");
			return;
		}

		module = Common.getModulePubChem();
		if (module == null || (!module.assays && !module.compounds))
		{
			logger.info("PubChem not configured: disabled");
			return;
		}
		
		if (module.assays)
		{
			File dir = new File(module.directory);
			if (module.directory == null || !dir.exists() || !dir.isDirectory())
			{
				logger.info("cannot operate on given directory [{}]", module.directory);
				return;
			}
		}
	
		new Thread(this).start();
	}

	@Override
	public void contextDestroyed(ServletContextEvent ev)
	{
		stopped = true;
		bumpThread(mutexMain);
		bumpThread(mutexFiles);
		bumpThread(mutexDownload);
		
		if (Common.getConfiguration() != null && Common.getModulePubChem().compounds)
		{
			bumpThread(mutexMeasure);
			bumpThread(mutexCompound);
		}
		else 
		{
			logger.info("compound downloading disabled.");
		}

		super.contextDestroyed(ev);
	}
	
	// ------------ public methods ------------

	public PubChemAssays()
	{
		super();
		main = this;
	}

	public static PubChemAssays main() {return main;}
	public static boolean isBusy() {return main.busy;}

	// tell the various subtasks to wake up and see if there's anything to do
	@Override
	public void bump()
	{
		bumpThread(mutexMain);
		bumpThread(mutexFiles);
		bumpThread(mutexDownload);
		bumpMeasurements();
		bumpCompounds();
	}

	// more specific
	public void bumpMeasurements()
	{
		bumpThread(mutexMeasure);
	}

	public void bumpCompounds()
	{
		bumpThread(mutexCompound);
	}
	
	// run in a background thread; expected to respond promptly to flipping of the stopped flag
	public void run()
	{	
		// give it a moment: allow the server a chance to get settled in, or to be terminated
		waitThread(mutexMain, 3);

		if (stopped) return;

		store = Common.getDataStore();
		
		// start background threads
		new Thread(() -> monitorFiles()).start();
		new Thread(() -> monitorDownload()).start();
		new Thread(() -> monitorMeasurements()).start();
		new Thread(() -> monitorCompounds()).start();
		
		// start the main loop
	
		while (!stopped)
		{
			//updateCurationFiles();
			updateAssayFiles();

			// wait forever, until shutdown or bumped
			logger.info("hibernating");
			waitThread(mutexMain);
		}
	}
	
	// adds the given list of AIDs to a special whitelist, which causes these override the default source-based whitelisting;
	// the function returns quickly, and triggers a re-reload in the background
	public void requestPubChemID(int[] aidList)
	{
		synchronized (whitelistAID)
		{
			for (int aid : aidList) whitelistAID.add(aid);
		}
		bump();
	}

	// merges some parts of a new assay parts to parts of an old assay; if the new assay has relevant new content, then these parts will
	// be added to the old assay (will not overwrite any existing labels, just adds new ones); returns true if something happened;
	// optionally a set of reasons can be provided, which will provide feedback as to why the merges happened
	public enum MergeReason {TEXT, TITLE, SOURCE, RELATED};
	public static boolean mergePubChemAssays(Assay newAssay, Assay oldAssay) {return mergePubChemAssays(newAssay, oldAssay, null);}
	public static boolean mergePubChemAssays(Assay newAssay, Assay oldAssay, Set<MergeReason> reasons)
	{
		boolean modified = false;

		// if the old text is blank, bring in the new stuff
		if (Util.isBlank(oldAssay.text) && Util.notBlank(newAssay.text)) 
		{
			oldAssay.text = newAssay.text;
			if (reasons != null) reasons.add(MergeReason.TEXT);
			modified = true;
		}

		// enumerate applicable previous label types
		Set<String> oldSource = new HashSet<>(), oldTitle = new HashSet<>(), oldRelated = new HashSet<>();
		for (Annotation annot : oldAssay.annotations)
		{
			if (annot.propURI.equals(SOURCE_URI)) oldSource.add(annot.valueURI);
		}
		for (TextLabel label : oldAssay.textLabels) 
		{
			if (label.propURI.equals(TITLE_URI)) oldTitle.add(label.text);
			else if (label.propURI.equals(RELATED_URI)) oldRelated.add(label.text);
		}
		
		// splice in any that happen to be new
		for (Annotation annot : newAssay.annotations)
		{
			if (annot.propURI.equals(SOURCE_URI) && !oldSource.contains(annot.valueURI))
			{
				oldAssay.annotations = ArrayUtils.add(oldAssay.annotations, annot);
				if (reasons != null) reasons.add(MergeReason.SOURCE);
				modified = true;
			}
		}
		for (TextLabel label : newAssay.textLabels)
		{
			if (label.propURI.equals(TITLE_URI) && !oldTitle.contains(label.text))
			{
				oldAssay.textLabels = ArrayUtils.add(oldAssay.textLabels, label);
				if (reasons != null) reasons.add(MergeReason.TITLE);
				modified = true;
			}
			else if (label.propURI.equals(RELATED_URI) && !oldRelated.contains(label.text))
			{
				oldAssay.textLabels = ArrayUtils.add(oldAssay.textLabels, label);
				if (reasons != null) reasons.add(MergeReason.RELATED);
				modified = true;
			}
		}

		return modified;
	}
	// ------------ private methods ------------

	// scans through a zip file, looking for assays that may be of interest; returns tallies of the amount of content obtained
	private static final Pattern PTN_PUBCHEM_SRC = Pattern.compile("^(\\d+)\\.concise.json\\.gz$");
	protected int[] scanZipFile(File f, Set<Integer> existingAID, Map<String, SchemaTree.Node> sourceMap,
								Set<Integer> specialAID) throws IOException, JSONException
	{
		class Counter
		{
			int numFiles = 0;
			int numMatches = 0;
			int numAdded = 0;
		}
		final Counter counter = new Counter();
	
		try (ZipFile zipFile = new ZipFile(f))
		{
			zipFile.stream().forEach(ze ->
			{
				counter.numFiles++;
				
				// filter entries for various reasons
				String name = new File(ze.getName()).getName();
				if (ze.getSize() > 100000000)
				{
					logger.info("file [{}] too big, at: ", name, ze.getSize());
					return;
				}
				Matcher m = PTN_PUBCHEM_SRC.matcher(name);
				if (!m.matches()) return;

				// process remaining entries if not already processed
				counter.numMatches++;

				int aid = Integer.parseInt(m.group(1));
				if (aid <= 0) return; // unlikely

				// the entries are gzipped json files
				JSONObject json = parseGzippedZipfileEntry(zipFile, ze);
				JSONObject root = json.optJSONObject("PC_AssaySubmit");
				if (root == null) return;

				Assay newAssay = parsePubChemAssay(root, sourceMap, specialAID);
				if (newAssay == null) return;
				
				// different logic depending on whether the assay is already in the system
				if (existingAID.contains(aid))
				{
					Assay oldAssay = store.assay().getAssayFromUniqueID(Identifier.PUBCHEM_PREFIX + aid);
					if (oldAssay != null && mergePubChemAssays(newAssay, oldAssay))
					{
						logger.info("updating assay with new PubChem content for AID = {}", aid);
						store.assay().setAssay(oldAssay); // will update history and stuff
					}
				}
				else
				{
					// the assay is new, so it can be submitted as a non-curated entry
					logger.info("adding new PubChem assay from AID = {}", aid);
					store.assay().setAssay(newAssay);
					counter.numAdded++;
				}
			});
		}
		catch (UncheckedIOException e)
		{
			throw new IOException(e);
		}
		
		// got to the end, so mark off the file as no need to check it again
		store.misc().submitLoadedFile(f.getAbsolutePath());
		
		return new int[]{counter.numFiles, counter.numMatches, counter.numAdded};
	}

	// converts the gzipped JSON file to a JSONObject
	// the recasting of the IOException to UncheckedIOException is required by the use of streams
	public static JSONObject parseGzippedZipfileEntry(ZipFile zipFile, ZipEntry zipEntry)
	{
		InputStream gzip;
		try
		{
			gzip = new GZIPInputStream(zipFile.getInputStream(zipEntry));
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
		return new JSONObject(new JSONTokener(new BufferedReader(new InputStreamReader(gzip))));
	}

	// parses the JSON format that's downloaded from the PubChem assay FTP site; returns true if it found something new and added
	// it to the database
	public static Assay parsePubChemAssay(JSONObject root, Map<String, SchemaTree.Node> sourceMap,
					    				  Set<Integer> specialAID) throws JSONException
	{
		JSONObject descr = root.getJSONObject("assay").getJSONObject("descr");
		int aid = descr.getJSONObject("aid").getInt("id");
		String source = descr.getJSONObject("aid_source").getJSONObject("db").getString("name");
		
		if (!sourceMap.containsKey(source) && !specialAID.contains(aid)) 
			return null; // will only annotate source for assays in the approved list/or the special AID list
		
		String name = descr.optString("name");
		JSONArray linesDescr = descr.optJSONArray("description");
		JSONArray linesComment = descr.optJSONArray("comment");
		JSONArray linesProtocol = descr.optJSONArray("protocol");

		StringBuilder buff = new StringBuilder();
		if (linesDescr != null && linesDescr.length() > 0)
		{
			for (int n = 0; n < linesDescr.length(); n++) buff.append(linesDescr.getString(n) + "\n");
			buff.append("\n");
		}
		if (linesComment != null && linesComment.length() > 0)
		{
			for (int n = 0; n < linesComment.length(); n++) buff.append(linesComment.getString(n) + "\n");
			buff.append("\n");
		}
		if (linesProtocol != null && linesProtocol.length() > 0)
		{
			for (int n = 0; n < linesProtocol.length(); n++) buff.append(linesProtocol.getString(n) + "\n");
			buff.append("\n");
		}

		//logger.info("loading new assay: AID={} source=[{}] text length={}", aid, source, buff.toString().trim().length());
		Assay assay = new Assay();
		assay.uniqueID = Identifier.PUBCHEM_PREFIX + aid;
		List<Annotation> annotList = new ArrayList<>();
		List<TextLabel> labelList = new ArrayList<>();
		if (Util.notBlank(name)) labelList.add(new TextLabel(TITLE_URI, name));
		SchemaTree.Node sourceNode = sourceMap.get(source);
		if (sourceNode != null) annotList.add(new Annotation(SOURCE_URI, sourceNode.uri));

		assay.text = buff.toString(); // using combination of assay narrative, comments, and protocol sections
		
		// obtain the Xrefs, then iterate through and generate fauxtology terms where applicable
		assay.pubchemXRefs = parseXRefs(descr);
		for (PubChemXRef xref : assay.pubchemXRefs)
		{
			if (xref.type.equals("gene"))
			{
				annotList.add(new Annotation(GENEID_URI, ModelSchema.PFX_GENEID + xref.id));
			}
			else if (xref.type.equals("protein_gi"))
			{
				String valueURI = ModelSchema.PFX_PROTEIN + xref.id;
				annotList.add(new Annotation(PROTEINID_URI, valueURI));
			}
			else if (xref.type.equals("aid"))
			{
				labelList.add(new TextLabel(RELATED_URI, Identifier.PUBCHEM_PREFIX + xref.id));
			}
		}	

		assay.annotations = annotList.toArray(new Annotation[annotList.size()]);
		assay.textLabels = labelList.toArray(new TextLabel[labelList.size()]);
		
		return assay;
	}
		
	// pull out the "cross reference" part of a PubChem assay
	private static PubChemXRef[] parseXRefs(JSONObject descr)
	{
		JSONArray jsonXRef = descr.optJSONArray("xref");
		JSONArray jsonTarget = descr.optJSONArray("target");
		
		List<PubChemXRef> xrefs = new ArrayList<>();

		if (jsonXRef != null) for (int n = 0; n < jsonXRef.length(); n++)
		{
			JSONObject obj1 = jsonXRef.getJSONObject(n);
			JSONObject obj2 = obj1.getJSONObject("xref");
		
			PubChemXRef xref = new PubChemXRef();
			xref.comment = obj1.optString("comment", "");
			for (Iterator<String> it = obj2.keys(); it.hasNext();)
			{
				xref.type = it.next();
				// some of the cross references are identifiers in other databases these integer values are converted to string
				try
				{
					xref.id = obj2.getString(xref.type);
				}
				catch (JSONException e)
				{
					xref.id = Integer.toString(obj2.getInt(xref.type));
				}
				break;
			}
			xrefs.add(xref);
		}
		
		if (jsonTarget != null) for (int n = 0; n < jsonTarget.length(); n++)
		{
			JSONObject obj = jsonTarget.getJSONObject(n);
			if (!obj.optString("molecule_type", "").equals("protein")) continue;
			PubChemXRef xref = new PubChemXRef();
			xref.comment = obj.optString("name", "");
			xref.type = "protein_gi";
			xref.id = obj.optString("mol_id", "");
			if (xref.id.length() > 0) xrefs.add(xref);
		}
		
		// remove duplicates, which happens with 'gene' from time to time
		for (int i = 0; i < xrefs.size() - 1; i++)
		{
			PubChemXRef xref1 = xrefs.get(i);
			for (int j = i + 1; j < xrefs.size(); j++)
			{
				PubChemXRef xref2 = xrefs.get(j);
				if (xref1.type.equals(xref2.type) && xref1.id.equals(xref2.id))
				{
					if (xref1.comment == null) xrefs.remove(i); else xrefs.remove(j);
					i--;
					break;
				}
			}
		}
		
		return xrefs.toArray(new PubChemXRef[xrefs.size()]);
	}

	// sit around looking for assays that have been copied into a loader directory: curated or fresh, new or modified
	private void monitorFiles()
	{
		if (!module.assays) return;
		logger.info("starting file monitor");

		Map<String, LoadedFile> fileList = new HashMap<>();

		while (!stopped)
		{
			Map<String, LoadedFile> newList = new HashMap<>();
			boolean different = false;
			File[] files = module.directory != null ? new File(module.directory).listFiles() : null;
			if (files != null) for (File f : files) 
			{
				LoadedFile newf = new LoadedFile();
				newf.path = f.getAbsolutePath();
				newf.date = f.lastModified();
				newf.size = f.length();
				if (!different)
				{
					LoadedFile oldf = fileList.get(newf.path);
					different = oldf == null || newf.size != oldf.size || newf.date != oldf.date;
				}
				newList.put(newf.path, newf);
			}
			
			fileList = newList;
			
			if (different)
			{
				logger.info("files noted");
				bumpThread(mutexMain);
			}
		
			// give it a minute between checking
			waitThread(mutexFiles, 60);
		}

		logger.info("stopped file monitor");
	}
	
	// sit around and every so often look for opportunities to download new assays from a remote source
	private void monitorDownload()
	{
		if (!module.assays) return;
		if (!new File(module.directory).exists())
		{	
			logger.info("Skipping assay loader: directory [{}]", module.directory);
			return;
		}
	
		final String PUBCHEM_FTP = "ftp://ftp.ncbi.nlm.nih.gov/pubchem/Bioassay/Concise/JSON/";
		// e.g. [-r--r--r--   1 ftp      anonymous 85785048 Oct 10  2015 0000001_0001000.zip]
		final Pattern ptnListBlock = Pattern.compile(".* (\\d+)\\s+\\w+\\s+\\w+\\s+[\\w:]+\\s+(\\d+_\\d+\\.zip)$");
		
		logger.info("starting occasional downloader");

		while (!stopped)
		{
			// look for new stuff in PubChem
			try
			{
				logger.info("acquiring list of PubChem assays");
			
				// enumerate current files
				Map<String, Long> currentFiles = new HashMap<>();
				File[] files = module.directory != null ? new File(module.directory).listFiles() : null;
				if (files != null) for (File f : files) currentFiles.put(f.getName(), f.length());
			
				// see what files are present
				URL url = new URL(PUBCHEM_FTP);
				BufferedReader rdr = new BufferedReader(new InputStreamReader(url.openStream()));
				List<String> todoList = new ArrayList<>();
				int numLines = 0;
				while (true)
				{
					String line = rdr.readLine();
					if (line == null) break;
					numLines++;
					Matcher m = ptnListBlock.matcher(line);
					if (m.matches())
					{
						// if the file isn't already in the list, or if it has a different size, rack it up for downloading
						long size = Long.parseLong(m.group(1));
						String fn = m.group(2);
						Long curSize = currentFiles.get(fn);
						if (curSize == null || curSize != size) todoList.add(fn);
					}
				}
				rdr.close();
				
				logger.info("number of new assays found in PubChem: {} (out of {} listed entries)", todoList.size(), numLines);

				// fetch each new file one at a time
				for (int n = 0; n < todoList.size() && !stopped; n++)
				{
					String fn = todoList.get(n);
					File outf = new File(module.directory, fn);
					logger.info("    downloading [{}] from PubChem to [{}] ({}/{})", fn, outf.getAbsolutePath(), n + 1, todoList.size());

					// read it all into a buffer
					BufferedInputStream istr = new BufferedInputStream(new URL(PUBCHEM_FTP + fn).openStream());
					ByteArrayOutputStream buff = new ByteArrayOutputStream();
					int b;
					while ((b = istr.read()) >= 0) {buff.write(b);}
					istr.close();
					
					// blast it out all at the same time (less likely to get truncated by some unforeseen event)
					OutputStream ostr = new FileOutputStream(outf);
					ostr.write(buff.toByteArray());
					ostr.close();
					
					// guarantee that it is eligible to be parsed on the next go-round
					store.misc().unsubmitLoadedFile(outf.getAbsolutePath());
				}
			
				// tell the file-checker thread to wake up	
				if (!todoList.isEmpty()) bumpThread(mutexFiles);
				
			}
			catch (IOException ex)
			{
				Util.errmsg("PubChem lookup failed", ex);
			}
		
			// only check once per day: it doesn't get updated super often
			waitThread(mutexFiles, (long)24 * 60 * 60);
		}

		logger.info("stopped occasional downloader");
	}

	// periodically check to see if any of the assays need to be investigated in more detail to acquire their corresponding measurements
	private void monitorMeasurements()
	{
		if (!module.compounds) return;
		logger.info("Measurement Monitor: starting");

		// give it a moment: allow the server a chance to get settled in, or to be terminated
		waitThread(mutexMeasure, 10);
		if (stopped) return;

		// start the main loop

		long watermark = 0;
		int blksz = 1000;
	
		while (!stopped)
		{
			long modWatermark = store.measure().getWatermarkMeasure();
			if (modWatermark != watermark)
			{
				watermark = modWatermark;
				logger.info("Measurement Monitor: looking for content");

				long[] assayIDList = store.assay().fetchAssayIDNeedMeasure();
				//assayIDList = new long[]{60}; // DEBUG: if we need a nice quick test case...				
				if (assayIDList.length > 0) 
					logger.info("Measurement Monitor: assays to investigate = {}", assayIDList.length);
				else
					logger.info("Measurement Monitor: all uptodate");
				
				PubChemMeasureBlock mblock = new PubChemMeasureBlock(assayIDList, logger);
				try
				{
					mblock.acquireBlock();
					if (mblock.blockSize() > 0) 
					{
						for (PubChemMeasureBlock.AssayContent content : mblock.getBlockContent())
						{
							logger.info("Measurement Monitor: acquired for pubchem AID = {}, assayID = {}", content.pubchemAID, content.assayID);
							investigateMeasurements(content.assayID, content.pubchemAID, content.measure);
						}
					}
					else logger.info("Measurement Monitor: no measurements available");
				}
				catch (Exception ex)
				{
					// on failure: break this loop, but do not shutdown the monitor entirely
					logger.info("** failed to acquire measurements");
					ex.printStackTrace();
					
					watermark = 0;
					waitThread(mutexMeasure, 60);
					
					break;
				}
				
				if (stopped) break;
			}
			else
			{
				// wait for a fair while before re-resting the watermark
				waitThread(mutexMeasure, 60L * 60L);
			}
		}
	
		logger.info("stopped");
	}
	
	// periodically check to see if there are any compounds that have external IDs but do not yet have their structures filled out
	private void monitorCompounds()
	{
		if (!module.compounds) return;
		logger.info("starting");

		// give it a moment: allow the server a chance to get settled in, or to be terminated
		waitThread(mutexCompound, 11);
		if (stopped) return;

		// start the main loop

		long watermark = 0;
	
		while (!stopped)
		{
			long modWatermark = store.compound().getWatermarkCompound();
			if (modWatermark != watermark)
			{
				watermark = modWatermark;
				logger.info("Compound Monitor: looking for content");
				
				while (!stopped)
				{
					Compound[] compounds = store.compound().fetchCompoundsNeedCID(1000);

					if (compounds.length == 0) break;
					
					logger.info("Compound Monitor: found compounds to download (block size: {})", compounds.length);
					
					try 
					{
						final int BLKSZ = 10;
						for (int n = 0; n < compounds.length && !stopped; n += BLKSZ)
						{
							Compound[] subset = Arrays.copyOfRange(compounds, n, Math.min(n + BLKSZ, compounds.length));
							new PubChemCompounds(subset).download();
							for (Compound cpd : subset)
							{
								if (cpd.pubchemCID == 0 && Util.isBlank(cpd.molfile))
								{
									throw new IOException("Failed to find CID for SID " + cpd.pubchemSID); // (delete it instead??
								}
								else
								{
									logger.info("Compound Monitor: matching SID {} with CID {}", cpd.pubchemSID, cpd.pubchemCID);
									store.compound().updateCompound(cpd);
								}
							}
						}
					}
					catch (Exception ex)
					{
						// on failure: break this loop, but do not shutdown the monitor entirely
						logger.error("** failed to obtain compound information");
						ex.printStackTrace();
						break;
					}
				}
			}
			else
			{
				// wait for a fair while before re-resting the watermark
				waitThread(mutexCompound, (long)60 * 60);
			}
		}

		logger.info("stopped");
	}


	// goes through the assays directory looking for anything that might've been added since last time, and loads it all into the database
	// (this can be a lengthy operation if it's a fresh database instance with lots of assay files available)
	protected void updateAssayFiles()
	{
		if (!module.assays) return;
		if (module.directory == null || !new File(module.directory).exists()) return;
	
		// if there are specially included whitelist AIDs, record them: this triggers a scan of specific assay IDs
		Set<Integer> specialAID;
		synchronized (whitelistAID)
		{
			specialAID = new HashSet<>(whitelistAID);
			whitelistAID.clear();
		}

		Map<String, SchemaTree.Node> sourceMap = getSourceMap();
		
		logger.info("Updating assay files");
	
		Set<Integer> existingAID = new HashSet<>();
		for (String uniqueID : store.assay().uniqueIDFromAssayID(store.assay().fetchAllAssayID()))
		{
			Identifier.UID uid = Common.getIdentifier().parseKey(uniqueID);
			if (uid != null && uid.source.prefix.equals(Identifier.PUBCHEM_PREFIX)) 
			{
				int aid = Util.safeInt(uid.id);
				if (aid > 0) existingAID.add(aid);
			}
		}
		Set<String> skipFiles = new HashSet<>();
		Pattern ptnFileRange = Pattern.compile("^(\\d+)_(\\d+).*");
		outer: for (LoadedFile lf : store.misc().getLoadedFiles()) 
		{
			Matcher m = ptnFileRange.matcher(new File(lf.path).getName());
			if (m.matches())
			{
				int lowAID = Util.safeInt(m.group(1)), highAID = Util.safeInt(m.group(2)); // inclusive
				for (int aid : specialAID) if (aid >= lowAID && aid <= highAID) continue outer;
			}
			skipFiles.add(lf.path); // passes on updating assays previously loaded
		}
	
		List<File> files = new ArrayList<>();
		for (File f : new File(module.directory).listFiles()) if (f.getName().endsWith(".zip")) files.add(f);
		Collections.sort(files);
		int numFiles = 0, numMatches = 0, numAdded = 0;
		for (int n = 0; n < files.size(); n++)
		{
			File f = files.get(n);
			if (skipFiles.contains(f.getAbsolutePath())) continue;
			
			logger.info("  assay group ({}/{}): {}", n + 1, files.size(), f.getAbsolutePath());
			try 
			{
				int[] tally = scanZipFile(f, existingAID, sourceMap, specialAID);
				numFiles += tally[0];
				numMatches += tally[1];
				numAdded += tally[2];
			}
			catch (Exception ex) {Util.errmsg("File parsing failed", ex);}
		}
		logger.info("  evaluated: {}, matches: {}, new assays: {}", numFiles, numMatches, numAdded);
	}

	// PubChem sources that will be importable
	public static Map<String, SchemaTree.Node> getSourceMap()
	{
		Map<String, SchemaTree.Node> sourceMap = new HashMap<>();
		// checking each node that has a parent
		for (SchemaTree.Node node : Common.obtainTree(Common.getSchemaCAT(), SOURCE_URI, null).getFlat()) 
			if (node.parent != null && node.pubchemImport) sourceMap.put(node.pubchemSource, node);
		return sourceMap;
	}

	// given that a JSON object containing the full assay record for a given assay, parse out the measurements
	protected boolean investigateMeasurements(long assayID, int aid, PubChemMeasurements dl) throws IOException, JSONException
	{
		Column[] columns = dl.getColumns();
		if (columns == null || columns.length == 0 || dl.numRows() == 0)
		{
			logger.info("Measurements: no data acquired for PubChem AID# {}, skipping", aid);
			store.assay().submitPubChemAIDMeasured(aid, true);
			return false;
		}
		
		store.measure().deleteMeasurementsForAssay(assayID);
		
		List<Row> summary = new ArrayList<>();
		List<Row> probes = new ArrayList<>();
		for (int n = 0; n < dl.numRows(); n++)
		{
			Row row = dl.getRow(n);
			if (row.outcome == Outcome.ACTIVE || row.outcome == Outcome.INACTIVE) summary.add(row);
			else if (row.outcome == Outcome.PROBE) probes.add(row);
			// (don't care about the others)
		}
		if (!summary.isEmpty())
		{
			Measurement measure = new Measurement();
			measure.assayID = assayID;
			measure.name = "Active/Inactive";
			measure.units = "binary";
			measure.type = DataMeasure.TYPE_ACTIVITY;
			fillMeasurements(measure, summary, -1);
			store.measure().appendMeasurements(measure);
		}
		if (!probes.isEmpty())
		{
			Measurement measure = new Measurement();
			measure.assayID = assayID;
			measure.name = "Probe";
			measure.units = "binary";
			measure.type = DataMeasure.TYPE_PROBE;
			fillMeasurements(measure, summary, -2);
			store.measure().appendMeasurements(measure);
		}
		
		for (int n = 0; n < columns.length; n++)
		{
			if (columns[n].type == Type.STRING) continue; // not storing these
		
			List<Row> rows = new ArrayList<>();
			for (int i = 0; i < dl.numRows(); i++)
			{
				Row row = dl.getRow(i);
				if (row.data[n] != null && row.data[n].value != null) rows.add(row);
			}
			if (rows.isEmpty()) continue;
					
			Measurement measure = new Measurement();
			measure.assayID = assayID;
			measure.name = columns[n].name;
			measure.units = columns[n].units.representation;
			measure.type = columns[n].activeColumn ? DataMeasure.TYPE_PRIMARY : DataMeasure.TYPE_MEASUREMENT;
			fillMeasurements(measure, rows, n);
			store.measure().appendMeasurements(measure);
		}
		
		store.assay().submitPubChemAIDMeasured(aid, true);
		return true;	
	}

	/* .. the old version...
	// investigate a single assay (by PubChem AID), and download the whole list of annotations from the source; downloading a
	// single "complete" assay record can get rather large, since the grid of {compound} x {measurement} can cover a pretty big
	// high throughput screen; hence why this is done as a staged background task; note also that the data format of the complete
	// record is similar to the "concise" versions that are downloaded in their entirety and stored locally; it is better to
	// download just the ones we want on demand, since storing them all for future reference would be prohibitive
	protected boolean investigateMeasurements(String progr, long assayID, int blksz) throws IOException, JSONException
	{
		Assay assay = store.assay().getAssay(assayID);
		if (assay == null || assay.uniqueID == null) return false;
		Identifier.UID uid = Common.getIdentifier().parseKey(assay.uniqueID);
		if (uid == null || !uid.source.prefix.equals(Identifier.PUBCHEM_PREFIX)) return false;
		int aid = Integer.parseInt(uid.id);
	
		logger.info("Measurements: downloading full record for AID={} ({}), block size: {}", aid, progr, blksz);
		
		PubChemMeasurements dl = getPubChemMeasurements(assayID, aid);
		dl.download(blksz);
		
		Column[] columns = dl.getColumns();
		if (columns == null || columns.length == 0 || dl.numRows() == 0)
		{
			logger.info("Measurements: no data acquired for PubChem AID# {}, skipping", aid);
			store.assay().submitPubChemAIDMeasured(aid, true);
			return false;
		}
		
		store.measure().deleteMeasurementsForAssay(assay.assayID);
		
		List<Row> summary = new ArrayList<>();
		List<Row> probes = new ArrayList<>();
		for (int n = 0; n < dl.numRows(); n++)
		{
			Row row = dl.getRow(n);
			if (row.outcome == PUBCHEM_OUTCOME_ACTIVE || row.outcome == PUBCHEM_OUTCOME_INACTIVE) summary.add(row);
			else if (row.outcome == PUBCHEM_OUTCOME_PROBE) probes.add(row);
			// (don't care about the others)
		}
		if (!summary.isEmpty())
		{
			Measurement measure = new Measurement();
			measure.assayID = assay.assayID;
			measure.name = "Active/Inactive";
			measure.units = "binary";
			measure.type = DataMeasure.TYPE_ACTIVITY;
			fillMeasurements(measure, summary, -1);
			store.measure().appendMeasurements(measure);
		}
		if (!probes.isEmpty())
		{
			Measurement measure = new Measurement();
			measure.assayID = assay.assayID;
			measure.name = "Probe";
			measure.units = "binary";
			measure.type = DataMeasure.TYPE_PROBE;
			fillMeasurements(measure, summary, -2);
			store.measure().appendMeasurements(measure);
		}
		
		for (int n = 0; n < columns.length; n++)
		{
			if (columns[n].type == PUBCHEM_TYPE_STRING) continue; // not storing these
		
			List<Row> rows = new ArrayList<>();
			for (int i = 0; i < dl.numRows(); i++)
			{
				Row row = dl.getRow(i);
				if (row.data[n] != null && row.data[n].value != null) rows.add(row);
			}
			if (rows.isEmpty()) continue;
					
			Measurement measure = new Measurement();
			measure.assayID = assay.assayID;
			measure.name = columns[n].name;
			measure.units = unitsToString(columns[n].units);
			measure.type = columns[n].activeColumn ? DataMeasure.TYPE_PRIMARY : DataMeasure.TYPE_MEASUREMENT;
			fillMeasurements(measure, rows, n);
			store.measure().appendMeasurements(measure);
		}
		
		store.assay().submitPubChemAIDMeasured(aid, true);
		return true;
	}*/
	
	// convenience for fill in the molecule records; column index is the data entity, with special deals for negative values
	protected void fillMeasurements(Measurement measure, List<Row> rows, int colidx)
	{
		int sz = rows.size();
		measure.compoundID = new long[sz];
		measure.value = new Double[sz];
		measure.relation = new String[sz];
		for (int n = 0; n < sz; n++)
		{
			Row row = rows.get(n);
			measure.compoundID[n] = store.compound().reserveCompoundPubChemSID(row.sid);
			if (colidx == -1) measure.value[n] = Double.valueOf(row.outcome == PubChemMeasurements.Outcome.ACTIVE ? 1 : 0);
			else if (colidx == -2) measure.value[n] = Double.valueOf(1);
			else measure.value[n] = row.data[colidx].value;
			measure.relation[n] = "=";
		}
	}

	// -- Required for testing --
	protected PubChemMeasurements getPubChemMeasurements(long assayID, int aid)
	{
		return new PubChemMeasurements(assayID, aid);
	}
	protected void setModule(InitParams.ModulePubChem module)
	{
		this.module = module;
	}
}

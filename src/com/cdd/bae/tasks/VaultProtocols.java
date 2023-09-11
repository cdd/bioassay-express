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
import com.cdd.bae.util.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.*;

import javax.servlet.*;

import org.apache.commons.lang3.*;
import org.json.*;
import org.slf4j.*;

/*
	Background task: connecting to Vault via the API and downloading assays as they become available.
*/

public class VaultProtocols extends BaseMonitor implements Runnable
{
	private static VaultProtocols main = null;
	public InitParams.ModuleVault module = null;
	private final Object mutexMain = new Object();
	private final Object mutexMolecule = new Object();
	protected DataStore store = null;
	private boolean busy = false;

	private static final String URLPFX = "https://app.collaborativedrug.com/api/v1/";

	// ------------ lifecycle ------------

	@Override
	public void contextInitialized(ServletContextEvent ev)
	{
		super.contextInitialized(ev);

		if (Common.getConfiguration() == null || Common.getParams() == null)
		{
			logger.info("Configuration not available or invalid: disabled");
			return;
		}

		module = Common.getModuleVault();
		if (module == null)
		{
			logger.info("Vault module not configured: disabled");
			return;
		}
		
		new Thread(this).start();
	}

	// Override of contextDestroyed not required
	
	// ------------ public methods ------------

	public VaultProtocols()
	{
		super();
		logger = LoggerFactory.getLogger(this.getClass().getName());
		try { module = Common.getModuleVault(); }
		catch (NullPointerException e) { /* ignore */ }
		main = this;
	}

	public static VaultProtocols main() {return main;}
	public static boolean isBusy() {return main.busy;}

	// tell the tasks to wake up and see if there's anything to do
	@Override
	public void bump()
	{
		bumpThread(mutexMain);
		bumpMolecules();
	}

	// more specific
	public void bumpMolecules()
	{
		bumpThread(mutexMolecule);
	}

	// run in a background thread; expected to respond promptly to flipping of the stopped flag
	public void run()
	{	
		// give it a moment: allow the server a chance to get settled in, or to be terminated
		waitThread(mutexMain, 3);
		if (stopped) return;

		store = Common.getDataStore();
		
		new Thread(() -> monitorMolecules()).start();
		
		// start the main loop
	
		exit: while (!stopped)
		{
			logger.info("polling Vault for content:");
			
			for (long vaultID : module.vaultIDList)
			{
				if (stopped) break exit;
				processVaultProtocols(vaultID, (theVaultID, protocol) ->
				{
					queryProtocol(theVaultID, protocol);
					return stopped;
				});
			}

			logger.info("hibernating...");
		
			// five minutes before another spin
			waitThread(mutexMain, (long)5 * 60);
		}
	}

	// ------------ private methods ------------
	
	public interface ProcessProtocols
	{
		// return true if processing should be stopped
		public boolean process(long vaultID, JSONObject protocol) throws IOException;
	}

	public void processVaultProtocols(long vaultID, ProcessProtocols callback)
	{
		logger.info("vault ID {}", vaultID);
		
		final int PAGE_SIZE = 30;
		for (int offset = 0; ; offset += PAGE_SIZE)
		{
			// fetch and parse the protocol list; relatively detailed error checking: if the request is going to break, it will
			// probably happen here
			
			String url = URLPFX + "vaults/" + vaultID + "/protocols";
			url += "?offset=" + offset + "&page_size=" + PAGE_SIZE;

			String raw = null;
			try {raw = makeRequest(url, null);}
			catch (IOException ex) { /* Exception ignored */ }
			if (raw == null)
			{
				logger.error("Unable to obtain Vault protocols for URL [{}]", url);
				return;
			}
			
			JSONArray protocols = null;
			try {protocols = new JSONObject(raw).getJSONArray("objects");}
			catch (Exception ex)
			{
				logger.error("Unable to parse Vault protocols for URL [{}]\n{}", url, ex);
				logger.info("Raw response:\n{}", raw);
				return;
			}

			// iterate over the list of protocols and handle each one individually (allow early stopping if callback return true)
			try
			{
				for (int n = 0; n < protocols.length(); n++)
					if (callback.process(vaultID, protocols.getJSONObject(n))) return;
			}
			catch (IOException | JSONException ex) {logger.error("Failed to handle Vault protocol {}", ex);}

			// this means we're past the page limit
			if (protocols.length() < PAGE_SIZE) break;
		}
	}

	// process a single Vault ID: look for everything interesting
	protected void pollVault(long vaultID)
	{
		processVaultProtocols(vaultID, (theVaultID, protocol) ->
		{
			queryProtocol(theVaultID, protocol);
			return stopped;
		});
	}
	
	// examine a protocol from within a Vault: if it has a suitable readout, proceed
	protected void queryProtocol(long vaultID, JSONObject obj) throws IOException
	{
		long protocolID = obj.getLong("id");
		String protocolName = obj.optString("name", "");	
		String description = obj.optString("description", "");
		JSONArray readouts = obj.optJSONArray("readout_definitions");
		JSONArray hitdefs = obj.optJSONArray("hit_definitions");
		JSONArray runs = obj.optJSONArray("runs");

		// build a state token based on the runs, which can be used to tell if the content has changed, and quick-out if not
		String measureState = getMeasureState(runs);

		logger.debug("protocol ID={} name=[{}] readouts={} hitdefs={}, measureState={}", protocolID, protocolName,
				readouts == null ? 0 : readouts.length(), hitdefs == null ? 0 : hitdefs.length(), measureState);
		
		if (readouts == null || hitdefs == null) return;

		// look through the hit definitions first: there has to be at least one that defines what makes a compound
		// classified as "most green", otherwise the protocol gets skipped
		GreenInformation greenInformation = getGreenInformation(hitdefs);
		if (greenInformation.hitID == 0)
		{
			logger.debug("Skipping protocol {}", protocolID);
			return; // nothing to see here move along
		}

		// at this point we need to find-or-create the assay
		DataObject.Assay assay = ensureAssay(protocolID, protocolName, description);
		
		// property mapping: clear the way
		removePropertyAnnotationsTextLabels(assay);
		
		// compare measure-stamp, and skip the next part if same-as-before
		final boolean ALWAYS_OVERWRITE = false; // set this to true for debugging
		logger.debug("current measureState: {}", assay.measureState);
		if (!ALWAYS_OVERWRITE && assay.measureState != null && assay.measureState.equals(measureState)) 
		{
			logger.debug("skipping due to identical measurestate");
			return;
		}
		assay.measureState = measureState;
		
		logger.debug("... green hitID={} readoutID={} {} {}", greenInformation.hitID, greenInformation.readoutID, greenInformation.relation, greenInformation.threshold);
		
		// make a list of measurements
		List<DataObject.Measurement> measureList = new ArrayList<>();
		Map<Long, Integer> readoutToIndex = new HashMap<>();
		
		DataObject.Measurement active = new DataObject.Measurement();
		active.assayID = assay.assayID;
		active.name = "Active/Inactive";
		active.units = "binary";
		active.type = DataMeasure.TYPE_ACTIVITY;
		measureList.add(active);
		
		String propField = module.propertyMap.get("field");
		String propUnits = module.propertyMap.get("units");
		String propOperator = module.propertyMap.get("operator");
		String propThreshold = module.propertyMap.get("threshold");
		for (int n = 0; n < readouts.length(); n++)
		{
			JSONObject robj = readouts.getJSONObject(n);
			if (!robj.getString("data_type").equals("Number")) continue; // others are Text & Date: we don't handle these
			
			long readoutID = robj.getLong("id");

			DataObject.Measurement measure = new DataObject.Measurement();
			measure.assayID = assay.assayID;
			measure.name = robj.getString("name");
			measure.units = robj.optString("unit_label", "");
			measure.type = readoutID == greenInformation.readoutID ? DataMeasure.TYPE_PRIMARY : DataMeasure.TYPE_MEASUREMENT;
			
			readoutToIndex.put(readoutID, measureList.size());
			measureList.add(measure);
			
			if (readoutID == greenInformation.readoutID)
			{
				appendTextLabel(assay, propField, measure.name);
				appendAnnotation(assay, propUnits, module.unitsMap.get(measure.units));
				appendAnnotation(assay, propOperator, module.operatorMap.get(greenInformation.relation));
				appendTextLabel(assay, propThreshold, String.valueOf(greenInformation.threshold));
			}
		}
		
		if (logger.isDebugEnabled()) 
		{
			logger.debug("Measurements:");
			for (DataObject.Measurement m : measureList) 
				logger.debug(" assayID={} name=[{}] units=[{}] type={}", m.assayID, m.name, m.units, m.type);
		}
		
		// now the measurement types are ready: fill them up with content
		logger.info("readouts to acquire: {}", readoutToIndex.keySet());
		queryReadouts(vaultID, protocolID, readoutToIndex, measureList, greenInformation.readoutID, greenInformation.relation, greenInformation.threshold);
		
		cullDuplicateActivity(measureList.get(0));
		
		// apply all of the measurements
		store.measure().deleteMeasurementsForAssay(assay.assayID);
		
		for (DataObject.Measurement measure : measureList)
		{
			if (measure.compoundID == null) continue; // can happen when nothing interesting was found
			measure.relation = new String[measure.compoundID.length];
			Arrays.fill(measure.relation, "=");
			store.measure().appendMeasurements(measure);
		}
		
		logger.info("submitting: assayID={} uniqueID={} measureState={}", assay.assayID, assay.uniqueID, assay.measureState);
		store.assay().submitAssay(assay);
		
		bumpMolecules(); // give the molecule loading a chance
	}

	// go through the readout contents and fill in all the corresponding measurements
	private void queryReadouts(long vaultID, long protocolID, Map<Long, Integer> readoutToIndex, List<DataObject.Measurement> measureList, 
							   long activeReadoutID, String relation, double threshold) throws IOException
	{
		List<Long> moleculeList = new ArrayList<>();
		List<double[]> valueList = new ArrayList<>();
		
		Map<String, Integer> strToIndex = new HashMap<>();
		for (long readoutID : readoutToIndex.keySet()) strToIndex.put(String.valueOf(readoutID), readoutToIndex.get(readoutID));
		int nreadouts = measureList.size();
		
		long timeThen = 0;
		
		final int PAGE_SIZE = 1000;
		for (int offset = 0; ; offset += PAGE_SIZE)
		{
			if (stopped) return;
			
			long timeNow = new Date().getTime();
			if (timeNow > timeThen + 5000)
			{
				logger.info("... downloading readouts for {} molecules={} offset={}", protocolID, moleculeList.size(), offset);
				timeThen = timeNow;
			}
						
			String url = URLPFX + "vaults/" + vaultID + "/protocols/" + protocolID + "/data";
			url += "?offset=" + offset + "&page_size=" + PAGE_SIZE;
			
			String raw = makeRequest(url, null);
			if (raw == null) break; // graceful failure: end of the set (?)
			JSONObject results = new JSONObject(raw);
			JSONArray objects = results.getJSONArray("objects");
			
			for (int n = 0; n < objects.length(); n++)
			{
				JSONObject obj = objects.getJSONObject(n);
				if (!obj.has("molecule")) continue;
				long moleculeID = obj.getLong("molecule");
				JSONObject readouts = obj.optJSONObject("readouts");
				if (readouts == null) continue;
				
				double[] values = new double[nreadouts];
				Arrays.fill(values, Double.NaN);
				
				boolean anything = false;
				for (String key : readouts.keySet())
				{
					int idx = strToIndex.get(key);
					if (idx < 0) continue;
					values[idx] = readouts.getDouble(key);
					anything = true;
				}
				
				if (anything)
				{
					moleculeList.add(moleculeID);
					valueList.add(values);
				}
			}
			
			// this means we're past the page limit
			if (objects.length() < PAGE_SIZE) break;
		}
		
		int sz = moleculeList.size();
		if (sz == 0) return;

		// make sure compounds exist for each ID (mapping from Vault to BAE)
		long[] compoundID = new long[sz];
		for (int n = 0; n < sz; n++) 
		{
			long moleculeID = moleculeList.get(n);
			compoundID[n] = store.compound().reserveCompoundVault(vaultID, moleculeID);
		}		
		
		// fill in measurement 0: this is the active/inactive determination
		int actidx = readoutToIndex.get(activeReadoutID);
		for (int n = 0; n < sz; n++)
		{
			double v = valueList.get(n)[actidx];
			if (Double.isNaN(v)) continue;
			boolean isActive = false;
			if (relation.equals("=")) isActive = v == threshold;
			else if (relation.equals("<")) isActive = v < threshold;
			else if (relation.equals(">")) isActive = v > threshold;
			else if (relation.equals("<=")) isActive = v <= threshold;
			else if (relation.equals(">=")) isActive = v >= threshold;
			
			valueList.get(n)[0] = isActive ? 1.0 : 0.0;
		}
		
		// now concatenate each readout to the appropriate measurements
		for (int n = 0; n < nreadouts; n++)
		{
			List<Integer> idxList = new ArrayList<>();
			for (int i = 0; i < sz; i++) if (Double.isFinite(valueList.get(i)[n])) idxList.add(i);
			if (idxList.isEmpty()) continue;
			
			long[] readoutCpd = new long[idxList.size()];
			Double[] readoutVal = new Double[idxList.size()];
			for (int i = 0; i < idxList.size(); i++)
			{
				int j = idxList.get(i);
				readoutCpd[i] = compoundID[j];
				readoutVal[i] = valueList.get(j)[n];
			}
		
			DataObject.Measurement measure = measureList.get(n);	
			measure.compoundID = ArrayUtils.addAll(measure.compoundID, readoutCpd);
			measure.value = ArrayUtils.addAll(measure.value, readoutVal);
		}
	}	
	
	// find the assay, or create a bare-bones assay for the protocol, store it in the database, and return it
	protected DataObject.Assay ensureAssay(long protocolID, String protocolName, String description)
	{
		// see if the assay is already there, and if so, no need to change anything
		String uniqueID = Identifier.VAULT_PREFIX + protocolID;
		DataObject.Assay assay = store.assay().getAssayFromUniqueID(uniqueID);
		if (assay != null) return assay;
	
		// create a starting point
		assay = new DataObject.Assay();
		assay.uniqueID = uniqueID;
		assay.text = protocolName;
		if (Util.notBlank(protocolName) && Util.notBlank(description)) assay.text += "\n\n";
		assay.text += description;
		assay.schemaURI = Common.getAllSchemata()[0].getSchemaPrefix(); // use the first one (the default)
		assay.curationTime = new Date();
		if (Util.notBlank(protocolName))
		{
			DataObject.TextLabel label = new DataObject.TextLabel(AssayUtil.URI_ASSAYTITLE, protocolName);
			assay.textLabels = ArrayUtils.add(assay.textLabels, label);
		}

		store.assay().submitAssay(assay);
		return assay;
	}

	// build a state token based on the runs, which can be used to tell if the content has changed, and quick-out if not
	protected static String getMeasureState(JSONArray runs)
	{
		if (runs == null || runs.length() == 0) return "?";
		StringJoiner sj = new StringJoiner("&");
		for (int n = 0; n < runs.length(); n++)
		{
			JSONObject r = runs.getJSONObject(n);
			sj.add(r.optString("run_date", "?") + ":" + r.optString("id", "?") + ":" + r.optString("person", "?"));
		}
		return sj.toString();
	}

	// look through the hit definitions first: there has to be at least one that defines what makes a compound
	// classified as "most green", otherwise the protocol gets skipped
	protected static class GreenInformation
	{
		protected static final String[] GREENLEVELS = {"red", "orange", "yellow", "lightGreen", "green"};
		protected long hitID = 0;
		protected long readoutID = 0;
		protected String relation = null;
		protected double threshold = 0;

		protected boolean assayHasHits()
		{
			return hitID > 0;
		}
	}

	protected static GreenInformation getGreenInformation(JSONArray hitdefs)
	{
		GreenInformation information = new GreenInformation();

		int greenLevel = -1;
		for (int n = 0; n < hitdefs.length(); n++)
		{
			JSONObject obj = hitdefs.getJSONObject(n);
			int g = ArrayUtils.indexOf(GreenInformation.GREENLEVELS, obj.getString("color"));
			if (g <= greenLevel) continue; // only looking for higher, and actually in the list

			greenLevel = g;
			information.hitID = obj.getLong("id");
			information.readoutID = obj.getLong("readout_definition_id");
			information.relation = obj.getString("operator");
			information.threshold = obj.getDouble("value");
		}
		return information;
	}

	protected void removePropertyAnnotationsTextLabels(DataObject.Assay assay)
	{
		String propField = module.propertyMap.get("field");
		String propUnits = module.propertyMap.get("units");
		String propOperator = module.propertyMap.get("operator");
		String propThreshold = module.propertyMap.get("threshold");
		for (int n = ArrayUtils.getLength(assay.annotations) - 1; n >= 0; n--)
		{
			String uri = assay.annotations[n].propURI;
			if (uri.equals(propUnits) || uri.equals(propOperator)) assay.annotations = ArrayUtils.remove(assay.annotations, n);
		}
		for (int n = ArrayUtils.getLength(assay.textLabels) - 1; n >= 0; n--)
		{
			String uri = assay.textLabels[n].propURI;
			if (uri.equals(propField) || uri.equals(propThreshold)) assay.textLabels = ArrayUtils.remove(assay.textLabels, n);
		}
	}

	
	private void monitorMolecules()
	{
		logger.info("Molecule Loader: starting");

		// give it a moment: allow the server a chance to get settled in, or to be terminated
		waitThread(mutexMolecule, 11);
		if (stopped) return;

		// start the main loop

		long watermark = 0;
	
		while (!stopped)
		{
			long modWatermark = store.compound().getWatermarkCompound();
			if (modWatermark != watermark)
			{
				watermark = modWatermark;
				logger.info("Molecule Loader: looking for content");
				
				while (!stopped)
				{
					DataObject.Compound[] compounds = store.compound().fetchCompoundsNeedVaultMol(100);
					if (compounds.length == 0) break;
					
					try {processMolecules(compounds);}
					catch (Exception ex)
					{
						logger.error("Molecule Loader: problem acquiring molecules: delaying\n{}", ex);
						break;
					}
				}
			}
			else
			{
				waitThread(mutexMolecule, (long)60 * 60); // wait for a fair while before re-resting the watermark
			}
		}

		logger.info("Molecule Loader: stopped");
	}
	
	// given a small batch of compound records known to have vault IDs and missing structures, make it happen
	protected void processMolecules(DataObject.Compound[] compounds) throws IOException
	{
		logger.info("Molecule Loader: found molecules to download: {}", compounds.length);
		
		// break the compounds up by vault
		Map<Long, List<DataObject.Compound>> vaultGroups = new TreeMap<>();
		for (DataObject.Compound cpd : compounds)
			vaultGroups.computeIfAbsent(cpd.vaultID, k -> new ArrayList<>()).add(cpd);

		// handle each block per Vault
		for (Map.Entry<Long, List<DataObject.Compound>> vaultInfo : vaultGroups.entrySet())
		{
			if (stopped) break;

			Long vaultID = vaultInfo.getKey();
			List<DataObject.Compound> group = vaultInfo.getValue();
			Long[] vaultMIDs = group.stream().map(c -> Long.valueOf(c.vaultMID)).toArray(Long[]::new);
			
			Map<Long, String> molFiles = importMolFiles(vaultID, vaultMIDs);

			logger.debug("#compounds {}, #vaultMID {}, #returned {}", group.size(), vaultMIDs.length, molFiles.size());

			for (DataObject.Compound cpd : group)
			{
				// if no structure is returned, we assume no molecule, set to blank string to stop it coming back
				if (logger.isDebugEnabled() && !molFiles.containsKey(cpd.vaultMID)) logger.debug(" compound {} missing", cpd.vaultMID);
				cpd.molfile = molFiles.getOrDefault(Long.valueOf(cpd.vaultMID), "");
				if (!cpd.molfile.equals("")) cpd.hashECFP6 = ChemInf.hashECFP6(ChemInf.parseMolecule(cpd.molfile));
				store.compound().updateCompound(cpd);
			}
		}
	}
	
	protected Map<Long, String> importMolFiles(long vaultID, Long[] vaultMIDs) throws IOException
	{
		String url = URLPFX + "vaults/" + vaultID + "/molecules?page_size=" + vaultMIDs.length + "&molecules=";
		url += Arrays.stream(vaultMIDs).map(String::valueOf).collect(Collectors.joining(","));
		JSONObject results = new JSONObject(makeRequest(url, null));
		JSONArray objects = results.getJSONArray("objects");

		Map<Long, String> molfiles = new HashMap<>();
		for (int n = 0; n < objects.length(); n++)
		{
			JSONObject obj = objects.getJSONObject(n);
			molfiles.put(obj.getLong("id"), obj.optString("molfile"));
		}
		return molfiles;
	}
	
	// carries out the webservice request, with the appropriate API key embedded
	protected String makeRequest(String url, String post) throws IOException
	{
		HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
		conn.setDoOutput(true);
		conn.setRequestMethod(post == null ? "GET" : "POST");
		
		int cutoff = 300000; // 5 minutes
		conn.setConnectTimeout(cutoff);
		conn.setReadTimeout(cutoff);
		conn.addRequestProperty("X-CDD-Token", module.apiKey);
		conn.connect();
		
		if (post != null)
		{
			BufferedWriter send = new BufferedWriter(new OutputStreamWriter(conn.getOutputStream(), Util.UTF8));
			send.append(post);
			send.flush();
			send.close();
		}

		int respCode = conn.getResponseCode();
		if (logger.isDebugEnabled() && respCode != 200) logger.debug("Vault response code {}, url {}", respCode, url);
		if (respCode >= 400) return null; // this is relatively graceful
		if (respCode != 200) throw new IOException("HTTP response code " + respCode + " for URL [" + url + "]");

		// read the raw bytes into memory; abort if it's too long or too slow
		BufferedInputStream istr = new BufferedInputStream(conn.getInputStream());
		ByteArrayOutputStream buff = new ByteArrayOutputStream();
		final int DOWNLOAD_LIMIT = 100 * 1024 * 1024; // within reason
		while (true)
		{
			int b = -1;
			try {b = istr.read();}
			catch (SocketTimeoutException ex) {throw new IOException(ex);}
			if (b < 0) break;
			if (buff.size() >= DOWNLOAD_LIMIT) 
				throw new IOException("Download size limit exceeded (max=" + DOWNLOAD_LIMIT + " bytes) for URL: " + url);
			buff.write(b);
		}
		istr.close();
		
		return new String(buff.toByteArray(), Util.UTF8);
	}	
	
	// convenience: adding manufactured annotations
	protected static void appendAnnotation(DataObject.Assay assay, String propURI, String valueURI)
	{
		if (propURI == null || valueURI == null) return;
		assay.annotations = ArrayUtils.add(assay.annotations, new DataObject.Annotation(propURI, valueURI));
	}
	protected static void appendTextLabel(DataObject.Assay assay, String propURI, String text)
	{
		if (propURI == null || text == null) return;
		assay.textLabels = ArrayUtils.add(assay.textLabels, new DataObject.TextLabel(propURI, text));
	}
	
	// only one "activity" value per molecule is desired, so mask out duplicates, with preference given to actives
	protected static void cullDuplicateActivity(DataObject.Measurement measure)
	{
		int sz = measure.compoundID.length;
		boolean[] mask = new boolean[sz];
		Set<Long> already = new HashSet<>();
		for (int n = 0; n < sz; n++) if (measure.value[n] == 1 && !already.contains(measure.compoundID[n]))
		{
			mask[n] = true;
			already.add(measure.compoundID[n]);
		}
		for (int n = 0; n < sz; n++) if (measure.value[n] != 1 && !already.contains(measure.compoundID[n]))
		{
			mask[n] = true;
			already.add(measure.compoundID[n]);
		}
		int nsz = 0;
		for (int n = 0; n < sz; n++) if (mask[n])
		{
			measure.compoundID[nsz] = measure.compoundID[n];
			measure.value[nsz] = measure.value[n];
			if (measure.relation != null) measure.relation[nsz] = measure.relation[n];
			nsz++;
		}
		measure.compoundID = Arrays.copyOf(measure.compoundID, nsz);
		measure.value = Arrays.copyOf(measure.value, nsz);
		if (measure.relation != null) measure.relation = Arrays.copyOf(measure.relation, nsz);
	}
}

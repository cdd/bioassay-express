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

/*
	Functionality pertaining to finding probe molecules from the PubChem collection.
	
	DEPRECATED: the code may come in useful for later investigation, but does not belong in the BAE.
*/

public class ProbeFunctions
{
	private String func = null, initDir = null;
	private static final String BASE_PUG = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/";

	// ------------ public methods ------------

/*
	public ProbeFunctions(String[] options)
	{
		int pos = 0;
		if (options.length > pos && !options[pos].startsWith("-")) func = options[pos++];
		if (pos + 1 < options.length && options[pos].equals("-init")) initDir = options[pos + 1];
	}
	
	public void exec() throws Exception
	{
		if (func == null) {printHelp(); return;}
		if (initDir == null) throw new IOException("Must specify -init {dir}.");
		
		Util.writeln("Executing [" + func + "] with dir: " + initDir);
		if (func.equals("gather")) doGather();
		else if (func.equals("collapse")) doCollapse();
		else if (func.equals("expand")) doExpand();
		else if (func.equals("assays")) doAssays();
		else if (func.equals("curated")) doCurated();
		else if (func.equals("sidmap")) doSIDMap();
		else if (func.equals("aidmap")) doAIDMap();
		else if (func.equals("targets")) doTargets();
		else throw new IOException("Unknown function '" + func + "'.");
	}
		
	// ------------ private methods ------------
	
	private void printHelp()
	{
		Util.writeln("Functions are:");
		Util.writeln("    gather: find probes from assay set (writes SIDs to probes.txt)");
		Util.writeln("    collapse: maps initial SIDs to CIDs (writes to probes_cids.txt)");
		Util.writeln("    expand: maps the CIDs to all of their SIDs (writes to probes_sids.txt)");
		Util.writeln("    assays: finds all assays containing a probe (writes to probe_aids.txt)");
		Util.writeln("    curated: lists the probe-affected assays awaiting curation");
		Util.writeln("    sidmap: creates one-to-many CID:[SID] (writes to probe_cidsid.txt");
		Util.writeln("    aidmap: creates one-to-many CID:[AID] (writes to probe_aidcid.txt");
		Util.writeln("    targets: figures out which targets these assays apply to");
	}
	
	private void doGather() throws Exception
	{
		List<File> files = new ArrayList<>();
		for (File f : new File(initDir + "/assays").listFiles()) if (f.getName().endsWith(".zip")) files.add(f);
		Collections.sort(files);
		
		final Pattern ptnSource = Pattern.compile("^(\\d+)\\.concise.json\\.gz$");
		Set<Integer> probeSID = new TreeSet<>();
		
		for (int n = 0; n < files.size(); n++)
		{
			File f = files.get(n);
			Util.writeln("Scanning: [" + f.getAbsolutePath() + "] (probes so far: " + probeSID.size() + ")");
						
			ZipInputStream zip = new ZipInputStream(new FileInputStream(f));
	
			ZipEntry ze = zip.getNextEntry();
			while (ze != null)
			{
				String path = ze.getName(), name = new File(path).getName();
				Matcher m = ptnSource.matcher(name);
				if (m.matches())
				{
					GZIPInputStream gzip = new GZIPInputStream(zip);
					JSONObject json = new JSONObject(new JSONTokener(new BufferedReader(new InputStreamReader(gzip))));
					JSONObject root = json.optJSONObject("PC_AssaySubmit");
					if (root != null) scourProbeSID(root, probeSID);
				}
				
				zip.closeEntry();
				ze = zip.getNextEntry();
			}
			
			zip.close();
		}
		
		File of = new File(initDir + "/assays/probes.txt");
		Util.writeln("Found " + probeSID.size() + " probe SIDs. Writing to: " + of.getPath());
		saveList(of, probeSID);
	}
	
	private void doCollapse() throws Exception
	{
		File sidfile = new File(initDir + "/assays/probes.txt");
		File cidfile = new File(initDir + "/assays/probes_cids.txt");
		Set<Integer> sids = loadList(sidfile), cids = loadList(cidfile);
		Util.writeln("Starting with SIDs:" + sids.size() + ", existing CIDs:" + cids.size());
		
		int numNew = 0;
		for (int sid : sids)
		{
			String url = BASE_PUG + "/substance/sid/" + sid + "/cids/JSON";
			Util.writeln(" Looking up SID=" + sid + " [" + url + "]");
			String str = Util.makeRequest(url, null);
			JSONObject json = new JSONObject(new JSONTokener(str));
			JSONArray cidlist = json.getJSONObject("InformationList").getJSONArray("Information").getJSONObject(0).getJSONArray("CID");
			if (cidlist.length() == 0) Util.writeln("  ** NO CID values found");
			for (int n = 0; n < cidlist.length(); n++)
			{
				int cid = cidlist.getInt(n);
				Util.writeln("    found CID: " + cid + " " + (cids.contains(cid) ? " (not new)" : "(new)"));
				if (!cids.contains(cid)) numNew++;
				cids.add(cid);
			}
		}
		
		Util.writeln("New CIDs found: " + numNew);
		
		saveList(cidfile, cids);
	}

	private void doExpand() throws Exception
	{
		File cidfile = new File(initDir + "/assays/probes_cids.txt");
		File sidfile = new File(initDir + "/assays/probes_sids.txt");
		Set<Integer> cids = loadList(cidfile), sids = new TreeSet<>();
		Util.writeln("Looking up " + cids.size() + "...");
		
		for (int cid : cids)
		{
			String url = BASE_PUG + "/compound/cid/" + cid + "/sids/JSON";
			Util.writeln(" CID=" + cid + " [" + url + "]");
			String str = Util.makeRequest(url, null);
			if (str == null)
			{
				Util.writeln("    ** no substances for this one");
				continue;
			}
			JSONObject json = new JSONObject(new JSONTokener(str));
			JSONArray sidlist = json.getJSONObject("InformationList").getJSONArray("Information").getJSONObject(0).getJSONArray("SID");
			if (sidlist.length() == 0) Util.writeln("  ** NO SID values found");
			for (int n = 0; n < sidlist.length(); n++)
			{
				int sid = sidlist.getInt(n);
				Util.writeln("    found SID: " + sid + " " + (sids.contains(cid) ? " (not new)" : "(new)"));
				sids.add(sid);
			}
		}
		
		Util.writeln("SIDs found: " + sids.size());
		
		saveList(sidfile, sids);	
	}
	
	private void doAssays() throws Exception
	{
		File sidfile = new File(initDir + "/assays/probes_sids.txt");
		File aidfile = new File(initDir + "/assays/probes_aids.txt");
		Set<Integer> sids = loadList(sidfile), aids = new TreeSet<>();
		Util.writeln("Scouring assays for any of " + sids.size() + " SIDs");

		List<File> files = new ArrayList<>();
		for (File f : new File(initDir + "/assays").listFiles()) if (f.getName().endsWith(".zip")) files.add(f);
		Collections.sort(files);
		
		Set<String> whitelist = new HashSet<>();
		BufferedReader rdr = new BufferedReader(new FileReader(initDir + "/assays/whitelist.txt"));
		while (true)
		{
			String line = rdr.readLine();
			if (line == null) break;
			line = line.trim();
			if (line.length() > 0 && line.charAt(0) != '#') whitelist.add(line);
		}
		rdr.close();
		Set<String> extraSources = new TreeSet<>();
		
		final Pattern ptnSource = Pattern.compile("^(\\d+)\\.concise.json\\.gz$");
		Set<Integer> probeSID = new TreeSet<>();
		
		for (int n = 0; n < files.size(); n++)
		{
			File f = files.get(n);
			Util.writeln("Scanning: [" + f.getAbsolutePath() + "] (assays so far: " + aids.size() + ")");
						
			ZipInputStream zip = new ZipInputStream(new FileInputStream(f));
	
			ZipEntry ze = zip.getNextEntry();
			while (ze != null)
			{
				String path = ze.getName(), name = new File(path).getName();
				Matcher m = ptnSource.matcher(name);
				if (m.matches())
				{
					GZIPInputStream gzip = new GZIPInputStream(zip);
					JSONObject json = new JSONObject(new JSONTokener(new BufferedReader(new InputStreamReader(gzip))));
					JSONObject root = json.optJSONObject("PC_AssaySubmit");
					if (root != null && findAnySIDs(root, sids)) 
					{
						JSONObject descr = root.getJSONObject("assay").getJSONObject("descr");
						int aid = descr.getJSONObject("aid").getInt("id");
						String src = descr.getJSONObject("aid_source").getJSONObject("db").getString("name");
						Util.writeln("    found assay: AID=" + aid + ", src=[" + src + "]");
						aids.add(aid);
						
						if (!whitelist.contains(src) && !extraSources.contains(src))
						{
							Util.writeln("    ... possible new source for whitelist");
							extraSources.add(src);
						}
					}
				}
				
				zip.closeEntry();
				ze = zip.getNextEntry();
			}
			
			zip.close();
		}
		
		Util.writeln("Found " + aids.size() + " assays with a probe SID. Writing to: " + aidfile.getPath());
		saveList(aidfile, aids);
		Util.writeln("Possible additions to whitelist: " + extraSources.size());
		for (String src : extraSources) Util.writeln("    " + src);
	}

	private void doCurated() throws Exception
	{
		DataStore store = new DataStore();

		File aidfile = new File(initDir + "/assays/probes_aids.txt");
		Set<Integer> aids = loadList(aidfile);
		Set<Long> includeList = new HashSet<>();
		for (int aid : aids)
		{
			long[] assayIDList = store.assay().
		}
				
		Set<Long> loaded = new HashSet<>(), curated = new HashSet<>(), uncurated = new HashSet<>();
		for (long assayID : store.assay().fetchAllAssayID()) if (includeList.contains(assayID)) loaded.add(assayID);
		for (long assayID : store.assay().fetchAllCuratedAssayID()) if (includeList.contains(assayID)) curated.add(assayID);
		for (long assayID : store.assay().fetchAllNonCuratedAssayID()) if (includeList.contains(assayID)) uncurated.add(assayID);
		
		Util.writeln("Total assays with probes: " + aids.size());
		Util.writeln("Loaded assays:            " + loaded.size());
		Util.writeln("Curated:                  " + curated.size());
		Util.writeln("Uncurated:                " + uncurated.size());
	}

	private void doSIDMap() throws Exception
	{
		Set<Integer> cids = loadList(new File(initDir + "/assays/probes_cids.txt"));
		Util.writeln("Mapping all SIDs for " + cids.size() + " CIDs...");
		
		int[][] mappings = new int[cids.size()][];

		int p = 0;		
		for (int cid : cids)
		{
			String url = BASE_PUG + "/compound/cid/" + cid + "/sids/JSON";
			//Util.writeln(" CID=" + cid + " [" + url + "]");
			String str = Util.makeRequest(url, null);
			if (str == null)
			{
				Util.writeln("    ** no substances for this one");
				mappings[p++] = new int[]{cid};
				continue;
			}
			JSONObject json = new JSONObject(new JSONTokener(str));
			JSONArray sidlist = json.getJSONObject("InformationList").getJSONArray("Information").getJSONObject(0).getJSONArray("SID");
			if (sidlist.length() == 0) Util.writeln("  ** NO SID values found");
			int[] maps = new int[sidlist.length() + 1];
			maps[0] = cid;
			for (int n = 0; n < sidlist.length(); n++) maps[n + 1] = sidlist.getInt(n);
			mappings[p++] = maps;
			
			Util.writeln(p + "/" + cids.size() + ": " + Arrays.toString(maps));
		}

		File outfile = new File(initDir + "/assays/probes_cidsid.txt");
		Util.writeln("Writing to: " + outfile);
		saveMapping(outfile, "Format: CID,SID1,SID2,...", mappings);
	}
	
	private void doAIDMap() throws Exception
	{
		int[][] mappings = loadMapping(new File(initDir + "/assays/probes_cidsid.txt"));
		Map<Integer, Integer> sidToCID = new HashMap<>();
		for (int[] map : mappings) for (int n = 1; n < map.length; n++) sidToCID.put(map[n], map[0]);
		
		DataStore store = new DataStore();
		Set<Integer> dbAID = new HashSet<>();
		for (int aid : store.assay().fetchAllPubChemAID()) dbAID.add(aid);
		
		Util.writeln("Mappings: " + sidToCID.size() + ", from " + mappings.length + " CIDs.");
		
		List<File> files = new ArrayList<>();
		for (File f : new File(initDir + "/assays").listFiles()) if (f.getName().endsWith(".zip")) files.add(f);
		Collections.sort(files);
		
		final Pattern ptnSource = Pattern.compile("^(\\d+)\\.concise.json\\.gz$");
		
		List<int[]> datum = new ArrayList<>();

		Map<String, Integer> activeClass = new HashMap<>();
		activeClass.put("inactive", 0);
		activeClass.put("active", 1);
		activeClass.put("probe", 2);
		activeClass.put("unspecified", 3);
		activeClass.put("inconclusive", 4);

		for (int n = 0; n < files.size(); n++)
		{
			File f = files.get(n);
			Util.writeln("Scanning: [" + f.getAbsolutePath() + "] (data so far: " + datum.size() + ")");
						
			ZipInputStream zip = new ZipInputStream(new FileInputStream(f));
	
			ZipEntry ze = zip.getNextEntry();
			while (ze != null)
			{
				String path = ze.getName(), name = new File(path).getName();
				Matcher m = ptnSource.matcher(name);
				if (m.matches())
				{
					GZIPInputStream gzip = new GZIPInputStream(zip);
					JSONObject json = new JSONObject(new JSONTokener(new BufferedReader(new InputStreamReader(gzip))));
					JSONObject root = json.optJSONObject("PC_AssaySubmit");
					JSONArray data = root == null ? null : root.getJSONArray("data");
					if (data != null) for (int i = 0; i < data.length(); i++)
					{
						JSONObject obj = data.getJSONObject(i);
						int sid = obj.getInt("sid");
						if (!sidToCID.containsKey(sid)) continue;
						String outcome = obj.optString("outcome", null);
						if (outcome == null) continue;
						if (!activeClass.containsKey(outcome))
						{
							Util.writeln("**** unexpected outcome value: [" + outcome + "]");
							return;
						}

						int cid = sidToCID.get(sid);
						JSONObject descr = root.getJSONObject("assay").getJSONObject("descr");
						int aid = descr.getJSONObject("aid").getInt("id");
						if (!dbAID.contains(aid)) continue;
						int active = activeClass.get(outcome);
						Util.writeln("    hit: AID=" + aid +", CID=" + cid + ", outcome=" + outcome + ":" + active);
						
						datum.add(new int[]{aid, cid, active});
					}
				}
				
				zip.closeEntry();
				ze = zip.getNextEntry();
			}
			
			zip.close();
		}
		
		File outfile = new File(initDir + "/assays/probes_aidcid.txt");
		Util.writeln("Writing " + datum.size() + " to: " + outfile);
		saveMapping(outfile, "Format: AID,CID,ActiveClass", datum.toArray(new int[datum.size()][]));
	}

	// loads up the unique AIDs and finds out how many targets these refer to
	private void doTargets() throws Exception
	{
		int[][] mappings = loadMapping(new File(initDir + "/assays/probes_aidcid.txt"));
		Set<Integer> aidList = new TreeSet<>(), cidList = new HashSet<>();
		for (int[] map : mappings) 
		{
			int aid = map[0], cid = map[1];
			boolean active = map[2] == 1;
			if (active) aidList.add(aid);
			cidList.add(cid);
		}
		Util.writeln("Loaded " + mappings.length + " pairs, actives of which reference " + aidList.size() + " assays.");
		
		String propURI = "http://www.bioassayontology.org/bao#BAX_0000012"; // protein target
		Set<String> targets = new HashSet<>();
		
		DataStore store = new DataStore();

		for (int aid : aidList)
		{
			DataStore.Assay assay = store.assay().getAssayFromPubChemAID(aid);
			if (assay.annotations != null) for (DataStore.Annotation annot : assay.annotations) if (annot.propURI.equals(propURI))
			{
				if (targets.contains(annot.valueURI)) continue;
				targets.add(annot.valueURI);
				Util.writeln("    " + annot.valueURI + " (total: " + targets.size() + ")");
			}
		}
		
		Util.writeln("Unique targets: " + targets.size());
		Util.writeln("Unique compounds: " + cidList.size());
	}

	// goes through the assay record looking any SID that is labelled explicitly as a "probe"
	private void scourProbeSID(JSONObject root, Set<Integer> probeSID) throws JSONException
	{
		JSONArray data = root.getJSONArray("data");
		for (int n = 0; n < data.length(); n++)
		{
			JSONObject obj = data.getJSONObject(n);
			if (!obj.optString("outcome", "").equals("probe")) continue;
			int sid = obj.getInt("sid");
			Util.writeln("    found probe SID=" + sid + " " + (probeSID.contains(sid) ? "(already seen it)" : "(new)"));
			probeSID.add(sid);
		}
	}

	// go through the assay record to see if any of the SIDs are found
 	private boolean findAnySIDs(JSONObject root, Set<Integer> sids) throws JSONException
 	{
		JSONArray data = root.getJSONArray("data");
		for (int n = 0; n < data.length(); n++)
		{
			JSONObject obj = data.getJSONObject(n);
			int sid = obj.getInt("sid");
			if (sids.contains(sid)) return true;
		}
		return false;
 	}
 	
	private Set<Integer> loadList(File file) throws IOException
	{
		Set<Integer> list = new TreeSet<>();
		if (!file.exists()) return list;
		BufferedReader rdr = new BufferedReader(new FileReader(file));
		while (true)
		{
			String line = rdr.readLine();
			if (line == null) break;
			if (line.length() > 0) list.add(Integer.valueOf(line));
		}
		rdr.close();
		return list;
	}
	private void saveList(File file, Set<Integer> list) throws IOException
	{	
		FileWriter wtr = new FileWriter(file);
		for (int id : list) wtr.write(id + "\n");
		wtr.close();
	}
	private int[][] loadMapping(File file) throws IOException
	{
		if (!file.exists()) return new int[0][];
		List<int[]> mappings = new ArrayList<>();
		BufferedReader rdr = new BufferedReader(new FileReader(file));
		while (true)
		{
			String line = rdr.readLine();
			if (line == null) break;
			if (line.length() == 0 || line.startsWith("#")) continue;
			String[] bits = line.split(",");
			int[] map = new int[bits.length];
			for (int n = 0; n < bits.length; n++) map[n] = Integer.valueOf(bits[n]);
			mappings.add(map);
		}
		rdr.close();
		return mappings.toArray(new int[mappings.size()][]);
	}
	private void saveMapping(File file, String heading, int[][] mappings) throws IOException
	{
		FileWriter wtr = new FileWriter(file);
		wtr.write("# " + heading + "\n");
		for (int[] maps : mappings)
		{
			for (int n = 0; n < maps.length; n++)
			{
				if (n > 0) wtr.write(',');
				wtr.write(String.valueOf(maps[n]));
			}
			wtr.write("\n");
		}
		wtr.close();
	}
	*/
}

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

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

import org.slf4j.*;

/*
	Obtaining a PubChem measurement block: given a list of assay IDs without corresponding measurements, checks the
	main PubChem FTP site looking for the appropriate block. These are found at:
	
		ftp://ftp.ncbi.nlm.nih.gov/pubchem/Bioassay/JSON
		
	The filenames indicate the range of PubChem AID numbers. The class lifecycle involves:
	
	(1) fetching the directory
	(2) identifying the first ZIP file that contains at least one of the desired assays
	(3) pulling out those assays and making this subset available
	(4) parsing out the measurements using the JSON streaming API
	
	The calling method should assume that returning 0 content means that there's nothing available for any of the
	given assay IDs, whereas returning >0 content items is not an exhaustive list, i.e. it should be called again
	to pick up the next block.
*/

public class PubChemMeasureBlock
{
	private final static String PUBCHEM_FTP = "ftp://ftp.ncbi.nlm.nih.gov/pubchem/Bioassay/JSON/";
	private Logger logger;

	public static final class AssayContent
	{
		public long assayID;
		public int pubchemAID;
		public PubChemMeasurements measure = null;
	}
	private List<AssayContent> block = new ArrayList<>();
	
	// ------------ public methods ------------

	// sets up the block, by pulling out assay candidates and stashing their PubChem identifiers (if any)
	public PubChemMeasureBlock(long[] assayIDList) {this(assayIDList, null);}
	public PubChemMeasureBlock(long[] assayIDList, Logger logger)
	{
		this.logger = logger;
	
		DataStore store = Common.getDataStore();
	
		for (long assayID : assayIDList)
		{
			Assay assay = store.assay().getAssay(assayID);
			if (assay == null || assay.uniqueID == null) continue;
			Identifier.UID uid = Common.getIdentifier().parseKey(assay.uniqueID);
			if (uid == null || !uid.source.prefix.equals(Identifier.PUBCHEM_PREFIX)) continue;
			
			AssayContent content = new AssayContent();
			content.assayID = assayID;
			try {content.pubchemAID = Integer.parseInt(uid.id);}
			catch (NumberFormatException ex) {continue;}
			block.add(content);
		}
	}
	
	// goes out to PubChem and finds the first block with a qualifying assay, and makes the result available; once this method
	// returns, the blockSize is indicative of the result: if there are zero results, that means PubChem has nothing to offer
	// for any of the supplied assays, and nothing further needs to happen; if there is more than one result, it is just the
	// results for within a single block; this method should be re-invoked once those are handled
	public void acquireBlock() throws IOException
	{
		if (block.size() == 0) return; // no candidates

		// e.g. [-r--r--r--   1 ftp      anonymous 85785048 Oct 10  2015 0000001_0001000.zip]
		final Pattern ptnListBlock = Pattern.compile(".*\\s+(\\d+)_(\\d+)\\.zip$");
		
		URL url = new URL(PUBCHEM_FTP);
		try (BufferedReader rdr = new BufferedReader(new InputStreamReader(url.openStream())))
		{
			List<AssayContent> theBlock = new ArrayList<>();
			while (true)
			{
				String line = rdr.readLine();
				if (line == null) break;
				Matcher m = ptnListBlock.matcher(line);
				if (m.matches())
				{
					int low = Integer.parseInt(m.group(1)), high = Integer.parseInt(m.group(2));
					for (AssayContent content : block) if (content.pubchemAID >= low && content.pubchemAID <= high) theBlock.add(content);
					if (theBlock.size() == 0) continue;
					
					block = theBlock;
					String fn = m.group(1) + "_" + m.group(2) + ".zip";
					
					if (logger != null) logger.info("Measurement Block: streaming [{}]", fn);
					downloadAnalyze(fn);
					return;				
				}
			}
		}
	}
	
	// access to acquired information
	public int blockSize() {return block.size();}
	public AssayContent[] getBlockContent() {return block.toArray(new AssayContent[block.size()]);}

	// ------------ private methods ------------

	// download an entire zip file from the FTP server: this can be big, but at least we're streaming
	private void downloadAnalyze(String fn) throws IOException
	{
		URL url = new URL(PUBCHEM_FTP + fn);
		final Pattern ptnFilename = Pattern.compile("^(\\d+)\\.json\\.gz$");
		
		Map<Integer, AssayContent> todo = new HashMap<>();
		for (AssayContent content : block) todo.put(content.pubchemAID, content);
		
		try (ZipInputStream zip = new ZipInputStream(url.openStream()))
		{
			ZipEntry ze = zip.getNextEntry();
			while (ze != null)
			{
				String name = new File(ze.getName()).getName();
				Matcher m = ptnFilename.matcher(name);
				if (m.matches())
				{
					int aid = Integer.parseInt(m.group(1));
					AssayContent content = todo.get(aid);
					if (content != null) 
					{
						if (logger != null) logger.info("Measurement Block: parsing AID {}", aid);
						InputStream gzip = new GZIPInputStream(zip);
						content.measure = new PubChemMeasurements(gzip);
					}
				}
				
				zip.closeEntry();
				ze = zip.getNextEntry();				
			}
		}
	}
}

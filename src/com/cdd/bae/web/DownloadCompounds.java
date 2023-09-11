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

package com.cdd.bae.web;

import com.cdd.bae.data.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.slf4j.*;

/*
	Download compounds: for some criteria (such as a list of assays), puts together all of the compounds and measurements
	that are attached to it, and blasts them out as a stream.
	
	Parameters:
		assays={list of assayID numbers, comma separated}
		ntags={# of tag mappings}
		a{tag#}={assay ID of tag#}
		o{tag#}={original name for tag#}
		t{tag#}={tagged collumn name for tag#}
*/

public class DownloadCompounds extends BaseServlet 
{
	private static final Logger logger = LoggerFactory.getLogger(DownloadCompounds.class);
	private static final long serialVersionUID = 1L;
	
	private static final String SEP = "::";
	
	private static final class CompoundMeasurements
	{
		List<DataObject.Measurement> measurements = new ArrayList<>();
		List<Integer> measureindex = new ArrayList<>();
	}
	
	private static final class MeasureValues
	{
		List<Double> values = new ArrayList<>();
		List<String> relations = new ArrayList<>();
	}

	@Override
	protected void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		String strAssays = request.getParameter("assays");
		if (strAssays == null) throw new IOException("Must provide at least one assay.");
		String[] bits = strAssays.split(",");
		long[] assayIDList = new long[bits.length];
		for (int n = 0; n < bits.length; n++) assayIDList[n] = Long.parseLong(bits[n]);
		
		int ntags = Integer.parseInt(request.getParameter("ntags"));
		Map<String, String> tagmap = new HashMap<>();
		for (int n = 0; n < ntags; n++)
		{
			long assayID = Long.parseLong(request.getParameter("a" + n));
			String oldName = request.getParameter("o" + n), newName = request.getParameter("t" + n);
			tagmap.put(assayID + SEP + oldName, newName);
		}
		
		DataStore store = Common.getDataStore();
		
		Map<Long, CompoundMeasurements> measurements = getMeasurements(assayIDList, store);
		Long[] cpdlist = measurements.keySet().toArray(new Long[measurements.size()]);
		Arrays.sort(cpdlist);
		
		response.setContentType("application/binary");

		logger.info("User downloading measurements: {} compounds, {} tags", cpdlist.length, ntags);
		try(Writer wtr = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(response.getOutputStream()))))
		{
			for (int n = 0; n < cpdlist.length; n++)
			{
				long compoundID = cpdlist[n];
			
				DataObject.Compound cpd = store.compound().getCompound(compoundID);
				String molfile = cpd.molfile;
				if (molfile == null) molfile = ""; 
				if (molfile.length() > 0 && !molfile.endsWith("\n")) molfile += "\n";
				
				wtr.write(molfile);
				
				wtr.write("> <index>\n" + (n + 1) + "\n\n");
				
				if (cpd.pubchemCID > 0) wtr.write("> <PubChemCID>\n" + cpd.pubchemCID + "\n\n");
				if (cpd.pubchemSID > 0) wtr.write("> <PubChemSID>\n" + cpd.pubchemSID + "\n\n");
				
				CompoundMeasurements cpdm = measurements.get(compoundID);
				Map<String, MeasureValues> values = new HashMap<>();
				for (int i = 0; i < cpdm.measurements.size(); i++)
				{
					DataObject.Measurement measure = cpdm.measurements.get(i);
					int idx = cpdm.measureindex.get(i);
					String tag = tagmap.get(measure.assayID + SEP + measure.name);
					if (tag == null) continue;
				
					MeasureValues mv = values.get(tag);
					if (mv == null)
					{
						mv = new MeasureValues();
						values.put(tag, mv);
					}
					mv.values.add(measure.value[idx]);
					mv.relations.add(measure.relation[idx]);
				}
				
				for (Map.Entry<String, DownloadCompounds.MeasureValues> entry : values.entrySet()) outputValues(wtr, entry.getKey(), entry.getValue());
				
				// TODO: if this is the first row, add an empty placeholder for any missing tags... (helps with SD parsers)
				
				wtr.write("$$$$\n");
				wtr.flush();
			}
		}
	}

	// for a group of 1-or-more values associated with the same tag, 
	private void outputValues(Writer wtr, String name, MeasureValues mv) throws IOException
	{
		wtr.write("> <" + name + ">\n");
		for (int n = 0; n < mv.values.size(); n++)
		{
			String rel = mv.relations.get(n);
			if (!rel.equals("=")) wtr.write(rel);
			wtr.write(mv.values.get(n) + "\n");
		}
		wtr.write("\n");
	}
	
	// collect information about measurements for all assays grouped by compound
	private Map<Long, CompoundMeasurements> getMeasurements(long[] assayIDList, DataStore store)
	{
		Map<Long, CompoundMeasurements> measurements = new HashMap<>();
		for (long assayID : assayIDList)
		{
			for (DataObject.Measurement measure : store.measure().getMeasurements(assayID))
			{
				for (int n = 0; n < measure.compoundID.length; n++)
				{
					CompoundMeasurements cpdm = measurements.get(measure.compoundID[n]);
					if (cpdm == null)
					{
						cpdm = new CompoundMeasurements();
						measurements.put(measure.compoundID[n], cpdm);
					}
					cpdm.measurements.add(measure);
					cpdm.measureindex.add(n);
				}
			}
		}
		return measurements;
	}
}

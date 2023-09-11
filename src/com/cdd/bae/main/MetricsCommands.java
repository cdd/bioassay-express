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

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;

/*
	Command line functionality for generating metrics, based on the current state of the database.
*/

public class MetricsCommands implements Main.ExecuteBase
{
	private DataStore store;
	
	// ------------ public methods ------------
	
	public void execute(String[] args) throws IOException
	{
		if (args.length == 0) {printHelp(); return;}
		
		String cmd = args[0];
		String[] options = ArrayUtils.remove(args, 0);

		store = Common.getDataStore();

		try
		{
			if (cmd.equals("sequence")) generateSequence(options);
			else if (cmd.equals("fairness")) measureFairness(options);
			else Util.writeln("Unknown command: '" + cmd + "'.");
		}
		catch (IOException ex) {throw ex;}
		catch (Exception ex) {throw new IOException(ex);}
	}
	
	public void printHelp()
	{
		Util.writeln("Metrics creation commands");
		Util.writeln("    generates metrics outputs based on the current state of the database");
		Util.writeln("Options:");
		Util.writeln("    sequence {testQ} {trainQ} {output.png} {techniques} [testratio]");
		Util.writeln("             test/train can be a query or '-' for all/auto");
		Util.writeln("             techniques is a comma-separated list of prediction types");
		Util.writeln("             testratio (0..1) is optional and only applies when auto-selecting");
		Util.writeln("    fairness [--schema {uri}] [--output {fn.tsv}]");
		Util.writeln("             examines all assays and evaluates increasing 'FAIR'ness over time");
		Util.writeln("             default schema is CAT; output tab-separated file is optional");
		
		Util.writeln();
	} 
	
	@Override
	public boolean needsNLP() {return true;}
	
	// ------------ private methods ------------
	
	protected void generateSequence(String[] options) throws IOException
	{
		if (options.length < 4)
		{
			Util.writeln("Parameters are {testQ} {trainQ} {output.png} {techniques}");
			return;
		}
		Util.writeln("Sequence metrics generation...");
	
		String testQ = options[0], trainQ = options[1];
		File outFile = new File(options[2]);
		if (!outFile.getAbsoluteFile().getParentFile().canWrite()) throw new IOException("Output file unwriteable: " + outFile.getAbsolutePath());
		String techniques = options[3];
		float testratio = 0.5f;
		if (options.length >= 5) testratio = Float.parseFloat(options[4]);
		
		Set<Assay> testAssays = obtainAssays(testQ);
		Set<Assay> trainAssays = obtainAssays(trainQ);
		
		// training may be a superset, and if so, remove anything from test
		Set<Long> testID = new HashSet<>();
		for (Assay assay : testAssays) testID.add(assay.assayID);
		for (Iterator<Assay> it = trainAssays.iterator(); it.hasNext();) if (testID.contains(it.next().assayID)) it.remove();
		
		if (testAssays.isEmpty() && trainAssays.isEmpty()) throw new IOException("No assays."); // only possible when DB empty (?)
		
		if (trainAssays.isEmpty()) splitAssays(trainAssays, testAssays, 1 - testratio);
		else if (testAssays.isEmpty()) splitAssays(testAssays, trainAssays, testratio);
		
		Util.writeln("    testing set size:  " + testAssays.size());
		Util.writeln("    training set size: " + trainAssays.size());
		
		MetricsSequence metseq = new MetricsSequence(testAssays.toArray(new Assay[testAssays.size()]),
													 trainAssays.toArray(new Assay[trainAssays.size()]), outFile, techniques);
		metseq.build();
		
		Util.writeln("Done.");
	}
	
	// evaluate FAIRness for assays over time
	protected void measureFairness(String[] options) throws IOException
	{
		Util.writeln("Evaluating FAIRness...");
		
		String schemaURI = Common.getSchemaCAT().getSchemaPrefix();
		String outFN = null;
		for (int n = 0; n < options.length; n++)
		{
			if (options[n].equals("--schema") && n < options.length - 1) schemaURI = options[++n];
			else if (options[n].equals("--output") && n < options.length - 1) outFN = options[++n];
			else throw new IOException("Unexpected argument: " + options[n]);
		}
		
		Util.writeln("Using schema: " + schemaURI);
		if (outFN == null) Util.writeln("Not writing output to separate file."); else Util.writeln("Output file: " + outFN);
		
		Util.writeln("Loading assays...");
		HistoricalAssays history = new HistoricalAssays(schemaURI);
		history.setup();
		
		long[] intervals = history.getTimeIntervals();
		Util.writeln("Number of time intervals (days): " + intervals.length);
		Util.writeln("    first day: " + new Date(intervals[0]));
		Util.writeln("    last day:  " + new Date(intervals[intervals.length - 1]));

		// collect all of the assignments that have an annotation
		Assay[] assays = history.getAssays();
		
		Set<Schema.Assignment> assnSet = new TreeSet<>();
		Schema.Assignment[] assnList = new Schema.Assignment[0];
		for (Assay assay : assays) 
		{
			Schema schema = Common.getSchema(assay.schemaURI);
			if (schema == null) continue;
			for (Annotation annot : assay.annotations) for (Schema.Assignment assn : schema.findAssignmentByProperty(annot.propURI, annot.groupNest))
			{
				if (ArrayUtils.indexOf(assnList, assn) < 0) assnList = ArrayUtils.add(assnList, assn);
			}
			for (TextLabel label : assay.textLabels) for (Schema.Assignment assn : schema.findAssignmentByProperty(label.propURI, label.groupNest))
			{
				if (ArrayUtils.indexOf(assnList, assn) < 0) assnList = ArrayUtils.add(assnList, assn);
			}
		}

		Util.writeln("Number of assays: " + assays.length);
		Util.writeln("Filled-in assignments: " + assnList.length);
		
		List<String> lines = new ArrayList<>();
		String header = "Time\tDate\tCount\tFAIR\tError";
		for (Schema.Assignment assn : assnList) header += "\t" + assn.name;
		lines.add(header);
		
		final int numAssn = assnList.length, numAssay = assays.length;
		
		for (int n = 0; n < intervals.length; n++)
		{
			Util.writeFlush("    interval " + (n + 1) + "/" + intervals.length + ": ");
			
			int count = 0;
			float[] scores = new float[numAssay]; // score per assay
			float[] totalValues = new float[numAssn]; // total value per assignment (i.e. across the assays)

			for (int i = 0; i < numAssay; i++)
			{
				Assay capsule = HistoricalAssays.timeCapsule(assays[i], intervals[n]);

				Schema schema = Common.getSchema(capsule.schemaURI);
				float[] value = new float[numAssn];
			
				for (Annotation annot : capsule.annotations) for (Schema.Assignment assn : schema.findAssignmentByProperty(annot.propURI, annot.groupNest))
				{
					int idx = ArrayUtils.indexOf(assnList, assn);
					if (idx < 0) continue;
					value[idx] = 1;
				}
				for (TextLabel label : capsule.textLabels) for (Schema.Assignment assn : schema.findAssignmentByProperty(label.propURI, label.groupNest))
				{
					int idx = ArrayUtils.indexOf(assnList, assn);
					if (idx < 0) continue;
					// value is 100% if it's supposed to be a literal, or 50% if it's supposed to be a URI
					value[idx] = Math.max(value[idx], assn.suggestions == Schema.Suggestions.FULL || assn.suggestions == Schema.Suggestions.DISABLED ? 0.5f : 1);
				}
				
				if (Util.notBlank(capsule.text) || capsule.annotations.length > 0 || capsule.textLabels.length > 0) count++;
				for (int j = 0; j < numAssn; j++) 
				{
					scores[i] += value[j];
					totalValues[j] += value[j];
				}
				scores[i] /= numAssn;
			}
			
			float avgScore = 0, stdDev = 0;
			for (int i = 0; i < numAssay; i++) avgScore += scores[i];
			avgScore /= numAssay;
			for (int i = 0; i < numAssay; i++) stdDev += Util.sqr(scores[i] - avgScore);
			stdDev = (float)Math.sqrt(stdDev / numAssay);
                			
			Util.writeln(" count=" + count + " avg.score=" + avgScore + " +/- " + stdDev);
			
			String line = String.format("%d\t%s\t%d\t%g\t%g", intervals[n], new Date(intervals[n]).toString(), count, avgScore, stdDev);
			for (int i = 0; i < numAssn; i++) line += "\t" + totalValues[i];
			lines.add(line);
		}
		
		if (outFN != null) 
		{
			Util.writeln("Writing to: " + outFN);
			try (Writer wtr = new BufferedWriter(new FileWriter(outFN)))
			{
				for (String line : lines) wtr.write(line + "\n");
			}
		}
		
		Util.writeln("Done.");
	}	
	
	// fetch a list of assay IDs, either from a 
	private Set<Assay> obtainAssays(String query) throws IOException
	{
		Set<Assay> assays = new HashSet<>();
		if (query.isEmpty() || query.equals("-"))
		{
			for (long assayID : store.assay().fetchAllCuratedAssayID()) 
			{
				Assay assay = store.assay().getAssay(assayID);
				
				// special deal: only PubChem assays with legit IDs
				if (assay.uniqueID == null || !assay.uniqueID.startsWith(Identifier.PUBCHEM_PREFIX)) continue;
				int aid = Util.safeInt(assay.uniqueID.substring(Identifier.PUBCHEM_PREFIX.length()));
				if (aid < 300) continue;
				
				assays.add(assay);
			}
			return assays;
		}
		
		if (query.startsWith("[") && query.endsWith("]"))
		{
			String[] bits = query.substring(1, query.length() - 1).split(",");
			for (String str : bits)
			{
				Assay assay = store.assay().getAssay(Long.parseLong(str));
				if (assay == null) throw new IOException("Invalid assay ID: " + str);
				
				assays.add(assay);
			}
			return assays;
		}
				
		QueryAssay qa = QueryAssay.parse(query);
		for (long assayID : store.assay().fetchAllCuratedAssayID())
		{
			Assay assay = store.assay().getAssay(assayID);
			if (qa.matchesAssay(assay)) assays.add(assay);
		}
		return assays;
	}
	
	// split the assay selection between the two sets, given that dest is empty and src is not
	// (note: just does a very trivial arbitrary 50:50 partition; could add additional options, such as size balance, 
	// and whether or not to create two similar sets (pushover) or very different (diabolical))
	private void splitAssays(Set<Assay> dest, Set<Assay> src, float destRatio)
	{
		// sort by PubChem AID, just because
		List<Assay> sorted = new ArrayList<>(src);
		final String PFX = Identifier.PUBCHEM_PREFIX;
		Collections.sort(sorted, (a1, a2) ->
		{
			int aid1 = a1.uniqueID.startsWith(PFX) ? Util.safeInt(a1.uniqueID.substring(PFX.length())) : 0;
			int aid2 = a2.uniqueID.startsWith(PFX) ? Util.safeInt(a2.uniqueID.substring(PFX.length())) : 0;
			return aid1 - aid2;
		});
		
		// split between the two, keeping balance
		int srcsz = 0;
		for (Assay assay : sorted)
		{
			boolean move = true;
			if (dest.size() > 0)
			{
				float curRatio = (float)dest.size() / (dest.size() + srcsz);
				move = curRatio < destRatio;
			}
			
			if (move) 
			{
				dest.add(assay);
				src.remove(assay);
			}
			else srcsz++;
		}
	}
}

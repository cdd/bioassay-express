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
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;

/*
	Command line functionality for axiom analysis.
*/

public class AxiomCommands implements Main.ExecuteBase
{
	private DataStore store;
	private AxiomVocab axvoc = null;
	
	// ------------ public methods ------------
	
	public void execute(String[] args) throws IOException
	{
		if (args.length == 0) {printHelp(); return;}
		
		String cmd = args[0];
		String[] options = ArrayUtils.remove(args, 0);

		store = Common.getDataStore();

		try
		{
			if (cmd.equals("assays")) analyseAssays(options);
			//else if (cmd.equals("cycle")) cycleAssays(options);
			else if (cmd.equals("propose")) proposeAxioms(options);
			else if (cmd.equals("specific")) proposeAxiomsSpecific(options);
			else Util.writeln("Unknown command: '" + cmd + "'.");
		}
		catch (IOException ex) {throw ex;}
		catch (Exception ex) {throw new IOException(ex);}
	}
	
	public void printHelp()
	{
		Util.writeln("Axiom analysis commands");
		Util.writeln("    analyses the current set of axiom rules");
		Util.writeln("Options:");
		Util.writeln("    assays: look for assays with rule violations");
		//Util.writeln("    cycle [-a {axiomfile}]: run each axiom individually through all assays");
		Util.writeln("    propose: look for new axioms");
		Util.writeln("    specific {propURI} {other props...}: narrow axiom discovery");
	} 
	
	@Override
	public boolean needsNLP() {return false;}
	
	// ------------ private methods ------------
	
	private void analyseAssays(String[] options) throws IOException
	{
		Util.writeln("Assay Analysis");

		Util.writeln("# axiom rules: " + Common.getAxioms().numRules());
		WinnowAxioms winnow = new WinnowAxioms(Common.getAxioms());
		
		long[] assayIDList = store.assay().fetchAllCuratedAssayID();
		
		long lastTime = new Date().getTime();
		int numProblem = 0;
		for (int n = 0; n < assayIDList.length; n++)
		{
			long curTime = new Date().getTime();
			if (curTime - lastTime > 2000) 
			{
				Util.writeln("Progress: " + (n + 1) + " of " + assayIDList.length);
				lastTime = curTime;
			}
		
			DataObject.Assay assay = store.assay().getAssay(assayIDList[n]);
			Schema schema = Common.getSchema(assay.schemaURI);
			SchemaDynamic graft = new SchemaDynamic(schema, assay.schemaBranches, assay.schemaDuplication);

			List<WinnowAxioms.SubjectContent> subjects = new ArrayList<>();
			List<WinnowAxioms.KeywordContent> keywords = new ArrayList<>();
			for (DataObject.Annotation annot : assay.annotations)
			{
				SchemaDynamic.SubTemplate subt = graft.relativeAssignment(annot.propURI, annot.groupNest);
				if (subt == null) continue;
				SchemaTree tree = Common.obtainTree(subt.schema, annot.propURI, subt.groupNest);
				if (tree == null) continue;
				subjects.add(new WinnowAxioms.SubjectContent(annot.valueURI, tree));
			}
			for (DataObject.TextLabel label : assay.textLabels)
			{
				keywords.add(new WinnowAxioms.KeywordContent(label.text, label.propURI));
			}
			if (Util.notBlank(assay.text)) keywords.add(new WinnowAxioms.KeywordContent(assay.text, null));

			List<String> errors = new ArrayList<>();
			for (Schema.Assignment assn : schema.getRoot().flattenedAssignments())
			{
				SchemaDynamic.SubTemplate subt = graft.relativeAssignment(assn.propURI, assn.groupNest());
				if (subt == null) continue;
				
				SchemaTree impactTree = Common.obtainTree(subt.schema, assn);
				if (impactTree == null) continue;
				Set<String> whitelist = WinnowAxioms.filterURI(winnow.winnowBranch(subjects, keywords, impactTree));
				if (whitelist == null) continue; // axioms have nothing to say about this
				
				boolean anything = false;
				for (DataObject.Annotation annot : assay.annotations) if (Util.notBlank(annot.valueURI))
					if (Schema.samePropGroupNest(annot.propURI, annot.groupNest, assn.propURI, subt.groupNest))
				{
					anything = true;
					if (!whitelist.contains(annot.valueURI))
					{
						SchemaTree.Node node = impactTree.getNode(annot.valueURI);
						if (node == null)
						{
							AxiomVocab.Rule[] triggers = winnow.branchTriggers(subjects, keywords, impactTree);
						
							String label = node == null ? "?" : node.label, mnem = ModelSchema.collapsePrefix(annot.valueURI);
							errors.add("Unexpected [" + label + "] <" + mnem + "> in assignment [" + assn.name + "]");
							for (AxiomVocab.Rule rule : triggers)
							{
								String line = "    trigger:" + rule.type;								
								line += " [" + Common.getOntoValues().getLabel(rule.subject[0].valueURI) + "/" + 
										ModelSchema.collapsePrefix(rule.subject[0].valueURI) + "] -> [";
								for (int i = 0; i < rule.impact.length; i++)
								{
									if (i > 0) line += ",";
									line += Common.getOntoValues().getLabel(rule.impact[i].valueURI) + "/" +
									 		ModelSchema.collapsePrefix(rule.impact[i].valueURI);
								}
								line += "]";
								errors.add(line);
							}							
						}
					}
				}
				if (!anything) errors.add("Lack of content in assignment [" + assn.name + "]");
			}
			if (!errors.isEmpty())
			{
				Util.writeln("Problem assay ID=" + assay.assayID + " uniqueID=" + assay.uniqueID);
				for (String err : errors) Util.writeln("    " + err);
				lastTime = new Date().getTime();
				numProblem++;
			}
		}
			
		Util.writeln("Done. Problem assays:" + numProblem);
	}
	
	private void proposeAxioms(String[] options) throws IOException
	{
		Util.writeln("Axiom Discovery");
		
		Util.writeln("Existing axiom rules: " + Common.getAxioms().numRules());
		Util.writeln("Loading assays...");
		List<DataObject.Assay> assays = new ArrayList<>();
		DataStore store = Common.getDataStore();
		for (long assayID : store.assay().fetchAllCuratedAssayID()) assays.add(store.assay().getAssay(assayID));
		Util.writeln("... # assays loaded: " + assays.size());
		
		Util.writeln("Discovering...");
		DiscoverAxioms discover = new DiscoverAxioms(assays.toArray(new DataObject.Assay[0]), Common.getSchemaCAT(), Common.getAxioms());
		discover.prepare();
		
		final String[] EXCLUDE = new String[]
		{
			"src:pubchem_sources"
		};
		Set<String> excludeURIs = new HashSet<>();
		for (String uri : EXCLUDE) excludeURIs.add(ModelSchema.expandPrefix(uri));
		
		DiscoverAxioms.Root[] roots = discover.getRoots();
		for (int n = 0; n < roots.length; n++)
		{
			String labelURI = ModelSchema.collapsePrefix(roots[n].assn.propURI);
			Util.writeln("\n---- root " + (n + 1) + "/" + roots.length + " [" + roots[n].assn.name + "] <" + labelURI + "> ----");
			
			//if (!roots[n].assn.name.equals("detection instrument")) continue; // !!
			
			DiscoverAxioms.Candidate[] candidates = discover.analyseRoot(roots[n]);
			Util.writeln("    new candidates: " + candidates.length);
			
			Arrays.sort(candidates, (c1, c2) -> Util.signum(c2.score - c1.score));
			for (int i = 0, count = 0; i < candidates.length && count < 100; i++)
			{
				if (excludeURIs.contains(candidates[i].subjectNode.uri) || excludeURIs.contains(candidates[i].impactNode.uri)) continue;
			
				DiscoverAxioms.Candidate cand = candidates[i];
				Util.writeln("    [" + cand.subjectNode.label + "]:[" + cand.impactNode.label + "] score=" + cand.score + 
							 " subject=" + cand.withSubject + " impact=" + cand.withImpact + "/" + cand.withoutImpact + 
							 " assn=[" + cand.impactAssn.name + "] URIs=" + 
							 ModelSchema.collapsePrefix(cand.subjectNode.uri) + "/" + ModelSchema.collapsePrefix(cand.impactNode.uri));
				
				
				/*Util.writeln(" ** subject=[" + cand.subjectNode.label + "] <" + ModelSchema.collapsePrefix(cand.subjectNode.uri) + ">");
				Util.writeln("     impact=[" + cand.impactNode.label + "] <" + ModelSchema.collapsePrefix(cand.impactNode.uri) + ">");
				Util.writeln("       assn=[" + cand.impactAssn.name + "] <" + ModelSchema.collapsePrefix(cand.impactAssn.propURI) + ">");
				Util.writeln("      score=" + cand.score + " #subject=" + cand.withSubject + " #impact=" + cand.withImpact + "/" + cand.withoutImpact);*/
				
				count++;
			}
		}
	
		Util.writeln("Done.");
	}
	
	private void proposeAxiomsSpecific(String[] options) throws IOException
	{
		if (options.length < 2) throw new IOException("Must provide {propURI} {others...}");
		String subjectPropURI = ModelSchema.expandPrefix(options[0]);
		String[] impactPropURI = ModelSchema.expandPrefixes(ArrayUtils.remove(options, 0));
	
		Util.writeln("Axiom Discovery (specific)");
		
		Util.writeln("Existing axiom rules: " + Common.getAxioms().numRules());
		Util.writeln("Loading assays...");
		List<DataObject.Assay> assays = new ArrayList<>();
		DataStore store = Common.getDataStore();
		for (long assayID : store.assay().fetchAllCuratedAssayID()) assays.add(store.assay().getAssay(assayID));
		Util.writeln("... # assays loaded: " + assays.size());

		Util.writeln("Discovering...");
		DiscoverAxioms discover = new DiscoverAxioms(assays.toArray(new DataObject.Assay[0]), Common.getSchemaCAT(), Common.getAxioms());
		discover.prepare();
		
		final String[] EXCLUDE = new String[]
		{
			"src:pubchem_sources",
			"bat:Absence"
		};
		Set<String> excludeURIs = new HashSet<>();
		for (String uri : EXCLUDE) excludeURIs.add(ModelSchema.expandPrefix(uri));
		final int WITHOUT_THRESHOLD = 5;
		
		Map<String, String> propNames = new HashMap<>();
		for (Schema.Assignment assn : Common.getSchemaCAT().getRoot().flattenedAssignments()) propNames.put(assn.propURI, assn.name);
				
		Util.writeln("Subject [" + propNames.get(subjectPropURI) + "] <" + ModelSchema.collapsePrefix(subjectPropURI) + ">"); 		
		
		for (DiscoverAxioms.Root root : discover.getRoots()) if (root.assn.propURI.equals(subjectPropURI))
		{
			DiscoverAxioms.Candidate[] candidates = discover.analyseRoot(root);
			
			for (String uri : impactPropURI) 
			{
				List<DiscoverAxioms.Candidate> impactList = new ArrayList<>();
				for (DiscoverAxioms.Candidate cand : candidates)
				{
					if (cand.withoutImpact > WITHOUT_THRESHOLD) continue;
					if (excludeURIs.contains(cand.impactNode.uri)) continue;
					impactList.add(cand);
				}
				Collections.sort(impactList, (c1, c2) -> Util.signum(c2.score - c1.score));
				
				Util.writeln("    vs [" + propNames.get(uri) + "] <" + ModelSchema.collapsePrefix(uri) + ">");
				for (int n = 0; n < 5 && n < impactList.size(); n++)
				{
					DiscoverAxioms.Candidate cand = impactList.get(n);
					Util.writeln("        [" + cand.impactNode.label + "] <" + ModelSchema.collapsePrefix(cand.impactNode.uri) + ">" + 
							     " score=" + cand.score + " stats=" + cand.withSubject + ":" + cand.withImpact + "/" + cand.withoutImpact);
				}
			}
		}
		
		Util.writeln("Done.");
	}
		
	private String formatRule(AxiomVocab.Rule rule)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(rule.type.toString() + ": ");
		sb.append("[" + ModelSchema.collapsePrefix(rule.subject[0].valueURI) + (rule.subject[0].wholeBranch ? "*" : "") + 
				  "/" + Common.getOntoValues().getLabel(rule.subject[0].valueURI) + "]");
				  
		sb.append(" ==> ");
		
		StringJoiner sj = new StringJoiner(",");
		if (rule.impact != null) for (AxiomVocab.Term s : rule.impact) 
			sj.add(ModelSchema.collapsePrefix(s.valueURI) + (s.wholeBranch ? "*" : "") + "/" + Common.getOntoValues().getLabel(s.valueURI));
		sb.append("[" + sj.toString() + "])");
		return sb.toString();	
	}
}

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

package com.cdd.bae.config;

import com.cdd.bae.util.*;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;

/*
	Container for axiom files, which are loaded from a directory with JSON-formatted content.
*/

public class AxiomFiles extends DirectoryWatcher
{
	protected AxiomVocab axioms;

	// ------------ public methods ------------

	public AxiomFiles(String axiomDir) throws ConfigurationException
	{
		super(axiomDir, true);
	}

	// there are no default files and we only accept required files
	@Override
	public boolean isValidFile(File f)
	{
		return f.exists() && f.getName().endsWith(".json");
	}

	// it is not necessary to have files
	@Override
	public boolean requireFiles()
	{
		return false;
	}

	@Override
	public void postLoad() throws ConfigurationException
	{
		synchronized (this)
		{
			axioms = new AxiomVocab();
			
			Map<String, AxiomVocab.Rule> already = new HashMap<>(); // key = type+subject
			
			// load all of the files, merging where necessary
			for (File file : getFilesOrdered()) 
			{
				for (AxiomVocab.Rule rule : loadAxiomFile(file).getRules())
				{
					String key = rule.type + "::" + rule.subject + "::" + rule.keyword;
					AxiomVocab.Rule merge = already.get(key);
					if (merge == null)
					{
						axioms.addRule(rule);
						already.put(key, rule);
					}
					else mergeRules(merge, rule);
				}
			}
		}
	}

	// fetch the merged axioms
	public AxiomVocab getAxioms() {return axioms;}

	// ------------ private methods ------------

	// loads up a single axiom vocab file, which consists of JSON-formatted axiom rules
	protected AxiomVocab loadAxiomFile(File file) throws ConfigurationException
	{
		try (Reader rdr = new BufferedReader(new FileReader(file)))
		{
			return AxiomVocab.deserialise(rdr);
		}
		catch (Exception ex) 
		{
			ex.printStackTrace();
			throw new ConfigurationException("Failed to load axiom file: " + file.getPath(), ex);
		}	
	}
	
	// given that an existing rule and a new one share the same type & subject, merge together the impact array
	protected void mergeRules(AxiomVocab.Rule current, AxiomVocab.Rule extra)
	{
		skip: for (AxiomVocab.Term look : extra.impact)
		{
			for (AxiomVocab.Term find : current.impact) 
			{
				if (look.equals(find)) continue skip;
				if (look.valueURI.equals(find.valueURI))
				{
					find.wholeBranch = find.wholeBranch || look.wholeBranch;
					continue skip;
				}
			}
			current.impact = ArrayUtils.add(current.impact, look);
		}
	}
}

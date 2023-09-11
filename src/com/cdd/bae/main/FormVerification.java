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
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.json.*;

/*
	Loads up forms and checks them out, then provides analysis.
*/

public class FormVerification implements Main.ExecuteBase
{
	private String[] formFiles = null;
	private JSONArray[] formDef = null;
	private Schema schema = null;
	private Set<String> assnKeys = new HashSet<>();

	// ------------ public methods ------------

	public void execute(String[] args) throws IOException
	{
		if (args.length == 0) {printHelp(); return;}
		
		for (int n = 0; n < args.length; n++)
		{
			if (args[n].startsWith("-"))
			{
				// (no modifier arguments currently)
				Util.writeln("Unexpected argument: " + args[n]);
				return;
			}
			else
			{
				File f = new File(args[n]);
				if (!f.exists())
				{
					Util.writeln("Form file not found: " + args[n]);
					return;
				}
				formFiles = ArrayUtils.add(formFiles, f.getCanonicalPath());
			}
		}
		
		if (formFiles == null)
		{
			Util.writeln("Must provide at least one form filename.");
			return;
		}
		
		formDef = new JSONArray[formFiles.length];

		JSONSchemaValidator validator = null;
		try
		{
			validator = JSONSchemaValidator.fromResource(EntryForms.SCHEMA_DEFINITION);
		}
		catch (JSONSchemaValidatorException ex)
		{
			Util.writeln("Failed to load schema validation: " + EntryForms.SCHEMA_DEFINITION);
			ex.printStackTrace();
			return;
		}


		Util.writeln("Form files to analyze:");
		for (int n = 0; n < formFiles.length; n++) 
		{
			Util.writeln("    (" + (n + 1) + ") " + formFiles[n]);
			try (Reader rdr = new FileReader(formFiles[n]))
			{
				formDef[n] = new JSONArray(new JSONTokener(rdr));
			}
			catch (IOException ex)
			{
				Util.writeln("Failed to read JSON file [" + formFiles[n] + "]");
				ex.printStackTrace();
				return;
			}
			
			List<String> errors = validator.validate(formDef[n]);
			if (!errors.isEmpty())
			{
				Util.writeln("** errors found:");
				for (String err : errors) Util.writeln("    -> " + err);
				return;
			}
		}
		
		Util.writeln("Analyzing...\n");
		for (int n = 0; n < formDef.length; n++)
		{
			for (int i = 0; i < formDef[n].length(); i++)
			{
				Util.writeln("---- [" + (n + 1) + (char)('a' + i) + "] ----");
				analyseForm(formDef[n].getJSONObject(i));
			}
		}
		if (schema == null)
		{
			Util.writeln("Nothing?");
			return;
		}
		
		Util.writeln("\n--- Schema Tally---");
		tallyGroup(schema.getRoot(), 1);

		Util.writeln("Done.");
	}
	
	public void printHelp()
	{
		Util.writeln("Form Verification commands");
		Util.writeln("    checking & analyzing data entry forms");
		Util.writeln("Options:");
		Util.writeln("    {list of forms}: full path to all forms to be analyzed");
	}  
	
	// ------------ private methods ------------
	
	private void analyseForm(JSONObject jsonForm) throws IOException
	{
		// use first mentioned schema, and require the whole process to refer just to one
		String[] schemaURIList = jsonForm.getJSONArray("schemaURI").toStringArray();
		if (schema != null && !schema.getSchemaPrefix().equals(schemaURIList[0])) throw new IOException("Forms must use the same template.");
		if (schema == null)
		{
			schema = Common.getSchema(schemaURIList[0]);
			if (schema == null) throw new IOException("Schema not found: " + schemaURIList[0]);
		}
		
		for (JSONObject jsonSection : jsonForm.getJSONArray("sections").toObjectArray())
		{
			Util.writeln("  Section [" + jsonSection.getString("name") + "]");
			for (JSONObject json : jsonSection.getJSONArray("layout").toObjectArray()) analyseBlock(json, 2);
		}
	}
	
	private void analyseBlock(JSONObject jsonBlk, int level) throws IOException
	{
		try
		{
			String type = jsonBlk.getString("type");
			
			if (type.equals("cell"))
			{
				Util.write(Util.rep(' ', 2 * level) + "cell: [" + jsonBlk.getString("label") + "]");
				
				String[] field = jsonBlk.getJSONArray("field").toStringArray();
				String propURI = ModelSchema.expandPrefix(field[0]);
				String[] groupNest = ModelSchema.expandPrefixes(ArrayUtils.remove(field, 0));
				
				Util.write("<" + ModelSchema.collapsePrefix(propURI));
				if (groupNest.length > 0) Util.write(";" + String.join(",", ModelSchema.collapsePrefixes(groupNest)));
				Util.write(">");
								
				Schema.Assignment[] assnList = schema.findAssignmentByProperty(propURI, groupNest);
				if (assnList.length == 0) throw new IOException("\nProperty/groupNest did not match any assignments.");
				if (assnList.length > 1) throw new IOException("\nProperty/groupNest matched multiple assignments.");
				
				if (groupNest.length != assnList[0].groupNest().length)
				{
					Util.writeln("\n-- groupNest from template: <" + String.join(",", ModelSchema.collapsePrefixes(assnList[0].groupNest())));
					throw new IOException("Matched an incomplete groupNest definition.");
				}
				
				Util.writeln(" to [" + assnList[0].name + "]");
				
				assnKeys.add(Schema.keyPropGroup(propURI, groupNest));
			}		
			else
			{
				Util.writeln(Util.rep(' ', 2 * level) + type);
				for (JSONObject json : jsonBlk.optJSONArrayEmpty("layout").toObjectArray()) analyseBlock(json, level + 1);
			}
		}
		catch (IOException ex)
		{
			Util.writeln("\n** parsing failed for JSON block:\n" + jsonBlk.toString(2));
			throw ex;
		}
	}
	
	private void tallyGroup(Schema.Group group, int level)
	{
		Util.writeln(Util.rep(' ', 2 * level) + "{" + group.name + "} <" + ModelSchema.collapsePrefix(group.groupURI) + ">");
		
		for (Schema.Assignment assn : group.assignments)
		{
			Util.write(Util.rep(' ', 2 * level));
			if (assnKeys.contains(Schema.keyPropGroup(assn.propURI, assn.groupNest()))) Util.write("* "); else Util.write("  ");
			Util.writeln("[" + assn.name + "] <" + ModelSchema.collapsePrefix(assn.propURI) + ">");
		}
		
		for (Schema.Group subg : group.subGroups) tallyGroup(subg, level + 1);
	}
	
}


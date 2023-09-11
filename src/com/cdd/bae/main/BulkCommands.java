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
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.apache.commons.lang3.*;

/*
	Command line functionality pertaining to "bulk" modifications: basically scripting data modification.
*/

public class BulkCommands implements Main.ExecuteBase
{
	// ------------ public methods ------------

	private static final String DONE_TOTAL_CHANGED = "Done. Total changed: ";
	private static final String ASSAYS_TO_CHECK = "Assays to check: ";

	public void execute(String[] args)
	{
		if (args.length == 0) {printHelp(); return;}
		
		String cmd = args[0];
		String[] options = ArrayUtils.remove(args, 0);

		/*if (cmd.equals("oosdelete")) oosDelete(options);
		else if (cmd.equals("oosmigrate")) oosMigrate(options);
		else*/ if (cmd.equals("oosrenprop")) oosRenameProp(options);
		else if (cmd.equals("oosprefix")) oosChangePrefix(options);
		else Util.writeln("Unknown command: '" + cmd + "'.");
	}
	
	public void printHelp()
	{
		Util.writeln("Bulk data modification commands");
		Util.writeln("    applies prescripted kinds of modifications to the underlying data; changes are placed in the");
		Util.writeln("    holding bay rather than being applied directly to the date");
		Util.writeln("Options:");
		//Util.writeln("    oosdelete {propURI}: out of schema delete");
		//Util.writeln("    oosmigrate {propFrom} {propTo}: out of schema migrate");
		Util.writeln("    oosrenprop {propFrom} {propTo}: rename property URIs");
		Util.writeln("    oosprefix {propURI} {oldpfx} {newpfx}: change URI prefixes");
		
		Util.writeln();
		Util.writeln("e.g. bae bulk oosdelete bao:BAX_0000002");
		Util.writeln("     bae bulk oosmigrate bao:BAX_0000002 bao:BAO_0002854");
		Util.writeln("     bae bulk oosrenprop bao:BAX_0000002 bao:BAO_0002855");
		Util.writeln("     bae bulk oosprefix bao:BAO_0002874 http://purl.org/obo/owl/UO#UO_ http://purl.obolibrary.org/obo/UO_");
	}  
	
	// ------------ private methods ------------
	
	// out of schema delete: for the given assignment (referenced by property), looks for all values that are not legitimate
	// parts of the schema, and tables them for deletion
	/* --- removed for the moment; may reinstate it later, but it should ideally be overridden by superior functionality
	protected void oosDelete(String[] options)
	{
		if (options.length != 1)
		{
			Util.writeln("Parameters are {propURI}");
			return;
		}
		Util.writeln("Out of Schema deletion...");
		
		String propURI = ModelSchema.expandPrefix(options[0]);
		Util.writeln("    Property URI: " + propURI);
		
		DataStore store = Common.getDataStore();
		long[] assayIDList = store.assay().fetchAssayIDWithAnnotations();
		Util.writeln(ASSAYS_TO_CHECK + assayIDList.length);
		
		Progress progress = new Progress(assayIDList.length);
		int numChanged = 0;
		for (int n = 0; n < assayIDList.length; n++)
		{
			progress.update(n, numChanged);
			DataStore.Assay assay = store.assay().getAssayFromAssayID(assayIDList[n]);
			if (assay == null) continue;
			Schema schema = Common.getSchema(assay.schemaURI);
			if (schema == null) continue;
			Schema.Assignment[] assnList = schema.findAssignmentByProperty(propURI);
			if (assnList.length == 0) continue;
			SchemaTree[] trees = new SchemaTree[assnList.length];
			for (int i = 0; i < assnList.length; i++) trees[i] = Common.obtainTree(assnList[i]);
			
			boolean modified = false;
			for (int i = assay.annotations.length - 1; i >= 0; i--)
			{
				DataStore.Annotation annot = assay.annotations[i];
				if (!annot.propURI.equals(propURI) || Util.isBlank(annot.valueURI)) continue;
				
				boolean contained = false;
				for (SchemaTree tree : trees) if (tree.getNode(annot.valueURI) != null) {contained = true; break;}
				if (!contained)
				{
					modified = true;
					assay.annotations = ArrayUtils.remove(assay.annotations, i);
				}
			}
			if (modified)
			{
				DataStore.Holding h = new DataStore.Holding();
				h.assayID = assay.assayID;
				h.submissionTime = new Date();
				h.curatorID = "admin";
				h.uniqueID = assay.uniqueID;
				h.schemaURI = assay.schemaURI;
				h.text = assay.text;
				h.annotations = assay.annotations;
				h.textLabels = assay.textLabels;
				store.holding().depositHolding(h);

				numChanged++;
			}
		}
		
		Util.writeln(DONE_TOTAL_CHANGED + numChanged);
	}*/
	
	// out of schema migrate: for any values found in the first schema that do not belong there, but do belong in the second
	// one, migrate them by switching the property URI; this is useful for splitting out categories
	/* --- removed for the moment; may reinstate it later, but it should ideally be overridden by superior functionality
	private void oosMigrate(String[] options)
	{
		if (options.length != 2)
		{
			Util.writeln("Parameters are {propFrom} {propTo}");
			return;
		}
		Util.writeln("Out of Schema migration...");
		String fromURI = ModelSchema.expandPrefix(options[0]), toURI = ModelSchema.expandPrefix(options[1]);
		Util.writeln("    From Property URI: " + fromURI);
		Util.writeln("      To Property URI: " + toURI);
		
		DataStore store = Common.getDataStore();
		long[] assayIDList = store.assay().fetchAllCuratedAssayID();
		Util.writeln(ASSAYS_TO_CHECK + assayIDList.length);
		
		Progress progress = new Progress(assayIDList.length);
		int numChanged = 0;
		for (int n = 0; n < assayIDList.length; n++)
		{
			progress.update(n, numChanged);
			DataStore.Assay assay = store.assay().getAssayFromAssayID(assayIDList[n]);
			if (assay == null) continue;
			Schema schema = Common.getSchema(assay.schemaURI);
			if (schema == null) continue;
			
			Schema.Assignment[] srcList = schema.findAssignmentByProperty(fromURI);
			if (srcList.length == 0) continue;
			SchemaTree[] srcTree = new SchemaTree[srcList.length];
			for (int i = 0; i < srcList.length; i++) srcTree[i] = Common.obtainTree(srcList[i]);
			
			Schema.Assignment[] dstList = schema.findAssignmentByProperty(toURI);
			if (dstList.length == 0) continue;
			SchemaTree[] dstTree = new SchemaTree[dstList.length];
			for (int i = 0; i < dstList.length; i++) dstTree[i] = Common.obtainTree(dstList[i]);
			
			boolean modified = false;
			skip: for (int i = assay.annotations.length - 1; i >= 0; i--)
			{
				DataStore.Annotation annot = assay.annotations[i];
				if (!annot.propURI.equals(fromURI) || Util.isBlank(annot.valueURI)) continue;
				
				for (SchemaTree tree : srcTree) if (tree.getNode(annot.valueURI) != null) continue skip;

				for (SchemaTree tree : dstTree) if (tree.getNode(annot.valueURI) != null)
				{
					modified = true;
					annot.propURI = toURI;
					annot.groupNest = tree.getAssignment().groupNest();
					break;
				}
			}
			if (modified)
			{
				DataStore.Holding h = new DataStore.Holding();
				h.assayID = assay.assayID;
				h.submissionTime = new Date();
				h.curatorID = "admin";
				h.uniqueID = assay.uniqueID;
				h.schemaURI = assay.schemaURI;
				h.text = assay.text;
				h.annotations = assay.annotations;
				h.textLabels = assay.textLabels;
				store.holding().depositHolding(h);

				numChanged++;
			}
		}
		
		Util.writeln(DONE_TOTAL_CHANGED + numChanged);
	}*/
	
	// property URI renaming: switches one for another without any further ado
	private void oosRenameProp(String[] options)
	{
		if (options.length != 2)
		{
			Util.writeln("Parameters are {propFrom} {propTo}");
			return;
		}
		Util.writeln("Property URI renaming...");
		String fromURI = ModelSchema.expandPrefix(options[0]), toURI = ModelSchema.expandPrefix(options[1]);
		Util.writeln("    From Property URI: " + fromURI);
		Util.writeln("      To Property URI: " + toURI);
		
		DataStore store = Common.getDataStore();
		long[] assayIDList = store.assay().fetchAllCuratedAssayID();
		Util.writeln(ASSAYS_TO_CHECK + assayIDList.length);
		
		Progress progress = new Progress(assayIDList.length);
		int numChanged = 0;
		for (int n = 0; n < assayIDList.length; n++)
		{
			progress.update(n, numChanged);
			DataObject.Assay assay = store.assay().getAssay(assayIDList[n]);
			
			boolean modified = false;
			for (DataObject.Annotation annot : assay.annotations) if (annot.propURI.equals(fromURI))
			{
				annot.propURI = toURI;
				modified = true;
			}
			for (DataObject.TextLabel label : assay.textLabels) if (label.propURI.equals(fromURI))
			{
				label.propURI = toURI;
				modified = true;
			}
			
			if (modified)
			{
				store.assay().submitAssay(assay);
				numChanged++;
			}
		}
		
		Util.writeln(DONE_TOTAL_CHANGED + numChanged);
	}
	
	// changing URI prefixes for a single property
	private void oosChangePrefix(String[] options)
	{
		if (options.length != 3)
		{
			Util.writeln("Parameters are {propURI} {oldpfx} {newpfx}");
			return;
		}
		Util.writeln("URI prefix changing...");
		String propURI = ModelSchema.expandPrefix(options[0]), oldpfx = options[1], newpfx = options[2];
		Util.writeln("    Property URI: " + propURI);
		Util.writeln("    old prefix:   " + oldpfx);
		Util.writeln("    new prefix:   " + newpfx);
		
		DataStore store = Common.getDataStore();
		long[] assayIDList = store.assay().fetchAllCuratedAssayID();
		Util.writeln(ASSAYS_TO_CHECK + assayIDList.length);
		
		Progress progress = new Progress(assayIDList.length);
		int numChanged = 0, numAlmost = 0;
		for (int n = 0; n < assayIDList.length; n++)
		{
			progress.update(n, numChanged);
			DataObject.Assay assay = store.assay().getAssay(assayIDList[n]);

			if (assay == null) continue;
			Schema schema = Common.getSchema(assay.schemaURI);
			if (schema == null) continue;
			
			Schema.Assignment[] assnList = schema.findAssignmentByProperty(propURI);
			if (assnList.length == 0) continue;
			SchemaTree[] treeList = new SchemaTree[assnList.length];
			for (int i = 0; i < assnList.length; i++) treeList[i] = Common.obtainTree(schema, assnList[i]);
			
			boolean modified = false, almost = false;
			
			for (DataObject.Annotation annot : assay.annotations) if (annot.propURI.equals(propURI) && annot.valueURI.startsWith(oldpfx))
			{
				String uri = newpfx + annot.valueURI.substring(oldpfx.length());

				boolean isValid = false;
				for (SchemaTree tree : treeList) if (tree != null && tree.getNode(uri) != null) {isValid = true; break;}
				
				if (isValid)
				{
					annot.valueURI = uri;
					modified = true;
				}
				else
				{
					Util.writeln("** turning [" + annot.valueURI + "] into [" + uri + "] not possible (not in tree)");
					almost = true;
				}
			}
			
			if (modified)
			{
				store.assay().submitAssay(assay);
				numChanged++;
			}
			if (almost) numAlmost++;
		}
		
		Util.writeln(DONE_TOTAL_CHANGED + numChanged + ", almost changed: " + numAlmost);    	
	}
	
	// prints progress information every second
	private static final class Progress
	{
		private final int size;
		private long timeThen;

		public Progress(int size)
		{
			this.size = size;
			this.timeThen = new Date().getTime();
		}
		
		public void update(long n, int numChanged)
		{
			long timeNow = new Date().getTime();
			if (timeNow > timeThen + 1000)
			{
				Util.writeln("    progress: " + (n + 1) + "/" + this.size + ", changed: " + numChanged);
				timeThen = timeNow;
			}
		}
	}
}

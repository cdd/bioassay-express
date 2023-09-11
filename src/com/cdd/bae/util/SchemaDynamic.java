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

package com.cdd.bae.util;

import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bao.template.*;
import com.cdd.bao.template.Schema.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.apache.commons.lang3.*;
import org.json.*;

/*
	Handling of "dynamic" schema: starts with a basic template, and applies all duplication & grafting modifications.
*/

public class SchemaDynamic
{
	private Schema schema, origSchema;
	private SchemaBranch[] branches;
	private SchemaDuplication[] duplication;
	
	private boolean modified = false;
	private Map<Integer, SchemaBranch> identityAssn = new HashMap<>(); // assn identity to branch
	private Map<String, SchemaBranch> branchedAssn = new HashMap<>(); // assn key to branch
	private List<SchemaDuplication> rosterDupl = new ArrayList<>(); // group duplications that have yet to be applied

	// ------------ public methods ------------

	public SchemaDynamic(Schema schema, SchemaBranch[] branches, SchemaDuplication[] duplication)
	{
		init(schema, branches, duplication);
	}
	
	public SchemaDynamic(String schemaURI, JSONArray jsonBranches, JSONArray jsonDuplication) throws JSONException
	{
		schema = schemaURI == null ? Common.getSchemaCAT() : Common.getSchema(schemaURI);
		if (schema == null) throw new JSONException("Invalid schemaURI=" + schemaURI);
		origSchema = schema;
		
		branches = new SchemaBranch[jsonBranches == null ? 0 : jsonBranches.length()];
		for (int n = 0; n < branches.length; n++) branches[n] = AssayJSON.deserialiseBranch(jsonBranches.getJSONObject(n));

		duplication = new SchemaDuplication[jsonDuplication == null ? 0 : jsonDuplication.length()];
		for (int n = 0; n < duplication.length; n++) duplication[n] = AssayJSON.deserialiseDuplication(jsonDuplication.getJSONObject(n));

		init(schema, branches, duplication);
	}

	// convenient shortcuts for repetitive use
	public static Schema compositeSchema(Schema schema, SchemaBranch[] branches, SchemaDuplication[] duplication)
	{
		return new SchemaDynamic(schema, branches, duplication).getResult();
	}
	public static Schema compositeSchema(String schemaURI, JSONArray jsonBranches, JSONArray jsonDuplication)
	{
		return new SchemaDynamic(schemaURI, jsonBranches, jsonDuplication).getResult();
	}
	
	// input content
	public Schema getInputSchema() {return origSchema;}
	public SchemaBranch[] getInputBranches() {return branches;}
	public SchemaDuplication[] getInputDuplication() {return duplication;}

	// returns true if the result schema has been formed by at least one grafting operation
	public boolean isComposite() {return modified;}
		
	// returns the resulting schema: if there were no grafted branches, this will be a direct reference to a systemwide schema instance; if
	// grafting happened, it will be a composite schema assembled for this purpose
	public Schema getResult() {return schema;}
	
	// for an assignment referenced by property/group, see if it belongs in the raw schema, or a grafted branch schema: provides the
	// independent schema and the referring groupNest as the result; this is necessary whenever functionality needs to lookup the content
	// of the schema tree
	public static final class SubTemplate
	{
		public Schema schema;
		public String[] groupNest;
	}
	public SubTemplate relativeAssignment(String propURI, String[] groupNest)
	{
		Schema.Assignment[] assnList = schema.findAssignmentByProperty(propURI, groupNest);
		if (assnList.length == 0) return null;
		
		SubTemplate ret = new SubTemplate();
		ret.schema = schema;
		ret.groupNest = groupNest;
		
		SchemaBranch branch = branchedAssn.get(Schema.keyPropGroup(propURI, groupNest));
		if (branch != null)
		{
			ret.schema = Common.getSchema(branch.schemaURI);
			ret.groupNest = Arrays.copyOf(groupNest, groupNest.length - Util.length(branch.groupNest));
			
			// returned group nesting is stripped of indexing directives
			for (int n = 0; n < ret.groupNest.length; n++)
			{
				int i = ret.groupNest[n].indexOf('@');
				if (i >= 0) ret.groupNest[n] = ret.groupNest[n].substring(0, i);
			}
		}
		
		return ret;
	}

	// ------------ private methods ------------
	
	// the real constructor
	private void init(Schema schema, SchemaBranch[] branches, SchemaDuplication[] duplication)
	{
		this.schema = schema;
		origSchema = schema;
		this.branches = branches;
		this.duplication = duplication;
		
		if (Util.length(branches) == 0 && Util.length(duplication) == 0) return;
		this.schema = this.schema.clone();

		prepareDuplication();
		applyDuplication();
		performGrafting();
		postProcess();
	}
	
	// prepares the list of requested duplications as a roster of things-to-apply in some arbitrary order
	private void prepareDuplication()
	{
		if (duplication != null) for (SchemaDuplication dupl : duplication)
		{
			if (dupl.multiplicity <= 1) continue; // probably shouldn't happen, but definitely don't care
			rosterDupl.add(new SchemaDuplication(dupl.multiplicity, Arrays.copyOf(dupl.groupNest, dupl.groupNest.length)));
		}
	}
	
	// apply any remaining duplication directives that apply at this point
	private void applyDuplication()
	{
		if (rosterDupl.isEmpty()) return;
		
		Group[] groupList = schema.getRoot().flattenedGroups();
	
		while (true)
		{
			boolean anything = false;
			for (Iterator<SchemaDuplication> it = rosterDupl.iterator(); it.hasNext();)
			{
				SchemaDuplication dupl = it.next();
				if (effectDuplication(dupl, groupList))
				{
					it.remove();
					groupList = schema.getRoot().flattenedGroups();
					anything = true;
					modified = true;
				}
			}
			if (!anything) break;
		}
	}
	
	// apply a singular duplication directive, if possible; this should ideally hit just one root stem, which will be cloned as many times
	// as necessary
	private boolean effectDuplication(SchemaDuplication dupl, Group[] groupList)
	{
		boolean hit = false;
		for (Group g : groupList)
		{
			String[] groupNest = ArrayUtils.insert(0, g.groupNest(), g.groupURI);
			if (!Schema.sameGroupNest(dupl.groupNest, groupNest)) continue;
		
			int idx = -1; // find the right spot to insert clones
			for (int n = 0; n < g.parent.subGroups.size(); n++) if (g == g.parent.subGroups.get(n)) {idx = n; break;}
		
			Group[] extra = new Group[dupl.multiplicity - 1];
			for (int n = 0; n < extra.length; n++)
			{
				extra[n] = g.clone(g.parent);
				extra[n].canDuplicate = g.canDuplicate;
				extra[n].groupURI = g.groupURI + "@" + (n + 2);
				g.parent.subGroups.add(++idx, extra[n]);
				
				// copy over the id-to-branch mappings as well, taking advantage of identical ordering
				Schema.Assignment[] list1 = g.flattenedAssignments(), list2 = extra[n].flattenedAssignments();
				for (int i = 0; i < list1.length; i++) 
				{
					SchemaBranch branch = identityAssn.get(System.identityHashCode(list1[i]));
					identityAssn.put(System.identityHashCode(list2[i]), branch);
				}
			}
			g.groupURI = g.groupURI + "@1";

			hit = true;
			break;
		}
		return hit;
	}
	
	// performs the grafting operation; returns true if anything actually happened
	private void performGrafting()
	{
		if (Util.length(branches) == 0) return;
		
		for (SchemaBranch branch : branches)
		{
			Schema branchSchema = Common.getSchema(branch.schemaURI);
			if (branchSchema == null) continue; // silent failure: nop
			
			if (graftSchema(branchSchema, branch)) 
			{
				applyDuplication();
				modified = true;
			}
		}
	}	
	
	// insert a branch schema at the indicated position
	private boolean graftSchema(Schema subSchema, SchemaBranch branch)
	{
		Schema.Group parent = schema.findGroupByNest(branch.groupNest);
		if (parent == null) return false;

		for (Schema.Assignment assn : subSchema.getRoot().assignments)
		{
			Schema.Assignment child = assn.clone(parent);
			parent.assignments.add(child);
			identityAssn.put(System.identityHashCode(child), branch);
		}
		
		List<Schema.Group> stack = new ArrayList<>();
		for (Schema.Group grp : subSchema.getRoot().subGroups)
		{
			Schema.Group child = grp.clone(parent);
			parent.subGroups.add(child);
			stack.add(child);
		}
		
		while (stack.size() > 0)
		{
			Schema.Group grp = stack.remove(0);
			stack.addAll(grp.subGroups);
			for (Schema.Assignment assn : grp.assignments) identityAssn.put(System.identityHashCode(assn), branch);
		}
		
		return true;		
	}
	
	// convert the temporary "identity pointers" into keys, so they can be referenced later
	private void postProcess()
	{
		// turn identity assignment/group references into keys
		for (Schema.Assignment assn : schema.getRoot().flattenedAssignments())
		{
			SchemaBranch branch = identityAssn.get(System.identityHashCode(assn));
			if (branch != null) branchedAssn.put(Schema.keyPropGroup(assn.propURI, assn.groupNest()), branch);
		}
	}	
}

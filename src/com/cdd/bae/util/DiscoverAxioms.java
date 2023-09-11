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
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;

/*
	Given a collection of assays, tries to discover candidates for new axioms by looking at the correlations between trees.

	Should typically be involved with an array of all available assays, so it's quite memory intensive.
*/

public class DiscoverAxioms
{
	private DataObject.Assay[] assays;
	private Schema schema;
	private AxiomVocab axioms;
	
	private static final class Branch
	{
		SchemaTree.Node node;
		Set<Integer> assays = new HashSet<>();
	}
	
	public static final class Root
	{
		public Schema.Assignment assn;
		public SchemaTree tree;
		private Map<String, Branch> branches = new HashMap<>(); // uri-to-branch
		private Set<Integer> assaysInvolved = new HashSet<>(); // indices of all assays participating in any capacity
	}
	private List<Root> roots = new ArrayList<>();
	
	public static final class Candidate
	{
		public Schema.Assignment subjectAssn, impactAssn;
		public SchemaTree.Node subjectNode, impactNode;
		public int withSubject, withImpact, withoutImpact;
		public float score;
	}
	
	// ------------ public methods ------------

	public DiscoverAxioms(DataObject.Assay[] assays, Schema schema, AxiomVocab axioms)
	{
		this.assays = Arrays.copyOf(assays, assays.length);
		this.schema = schema;
		this.axioms = axioms;
	}
	
	// sets up the preliminary representations
	public void prepare()
	{
		// for each assignment in the template that has a corresponding tree, add a root
		Map<String, Integer> assnMap = new HashMap<>();
		for (Schema.Assignment assn : schema.getRoot().flattenedAssignments())
		{
			if (assn.propURI.equals(ModelSchema.expandPrefix("bao:BAO_0003107"))) continue; // no GO terms
		
			SchemaTree tree = Common.obtainTree(schema, assn);
			if (tree == null || tree.getTree().size() == 0) continue;
			Root root = new Root();
			root.assn = assn;
			root.tree = tree;
			roots.add(root);
			
			assnMap.put(Schema.keyPropGroup(assn.propURI, assn.groupNest()), roots.size() - 1);
		}
		//Util.writeln("ROOTS:"+roots.size());		
		
		// expand out each annotation for each assay into a convenient datastructure
		for (int n = 0; n < assays.length; n++)
		{
			if (assays[n].annotations != null) for (DataStore.Annotation annot : assays[n].annotations)
			{
				if (Util.isBlank(annot.valueURI)) continue;
				Integer idxRoot = assnMap.get(Schema.keyPropGroup(annot.propURI, annot.groupNest));
				if (idxRoot == null) continue;

				// find the position in the tree, and walk up the parent line, appending the current assay # to each reference
				Root root = roots.get(idxRoot);
				for (SchemaTree.Node node = root.tree.getNode(annot.valueURI); node != null; node = node.parent)
				{
					Branch branch = root.branches.get(node.uri);
					if (branch == null)
					{
						branch = new Branch();
						branch.node = node;
						branch.assays.add(n);
						root.branches.put(node.uri, branch);
					}
					else branch.assays.add(n);
				}
			}
		}
		//for (Root root : roots) Util.writeln("ROOT ["+root.assn.name+"] branches="+root.branches.size());
		
		for (Root root : roots)
		{
			for (Branch branch : root.branches.values()) for (int idxAssay : branch.assays) root.assaysInvolved.add(idxAssay);
		}
	}
	
	// pre-result access
	public Root[] getRoots() {return roots.toArray(new Root[roots.size()]);}
	
	// consider all the options within a given root, in combination with other roots
	public Candidate[] analyseRoot(Root root)
	{
		List<Candidate> candidates = new ArrayList<>();
		
		final int MIN_SUBJECT = 2; // at least this many instances to consider a subject
		final int MIN_IMPACT = 2; // at least this many instances to for a noteworthy impact
	
		Set<String> axiomsAlready = new HashSet<>();
		for (AxiomVocab.Rule rule : axioms.getRules()) for (AxiomVocab.Term term : rule.impact)
			 axiomsAlready.add(rule.subject[0].valueURI + "::" + term.valueURI);
	
		for (Branch branch1 : root.branches.values())
		{
			int assaysWithA = branch1.assays.size();
			if (assaysWithA < MIN_SUBJECT) continue;
		
			for (Root other : roots) if (other != root)
			{
				for (Branch branch2 : other.branches.values())
				{
					// skip axioms that are already defined
					if (axiomsAlready.contains(branch1.node.uri + "::" + branch2.node.uri)) continue;
					
					// if this is at the head of a branch then skip; may reconsider this later though
					if (branch2.node.parent == null) continue;
				
					//int assaysWithB = branch2.assays.size();
					int assaysWithB = 0;
					for (int assayID : branch2.assays) if (branch1.assays.contains(assayID)) assaysWithB++;
					if (assaysWithB < MIN_IMPACT) continue;
					
					// want all of the assays that are in branch1, and are in root2 but not branch2...
					Set<Integer> assays = new HashSet<>(other.assaysInvolved);
					for (int idxAssay : branch2.assays) assays.remove(idxAssay);
					assays.removeIf(idx -> !branch1.assays.contains(idx));
					int assaysWithoutB = assays.size();

					if (assaysWithoutB > assaysWithB) continue; // !! impose a ratio...
					
					//Util.writeln(" [" + branch1.node.label + "]:[" + branch2.node.label +"] A="+assaysWithA+" B="+assaysWithB+"/"+assaysWithoutB);
					Candidate cand = new Candidate();
					cand.subjectAssn = root.assn;
					cand.subjectNode = branch1.node;
					cand.impactAssn = other.assn;
					cand.impactNode = branch2.node;
					cand.withSubject = assaysWithA;
					cand.withImpact = assaysWithB;
					cand.withoutImpact = assaysWithoutB;
					cand.score = (float)assaysWithA * assaysWithB / (assaysWithoutB + 1);
					candidates.add(cand);
				}
			}
		}
		
		return candidates.toArray(new Candidate[candidates.size()]);
	}
		
	// ------------ private methods ------------
	
}

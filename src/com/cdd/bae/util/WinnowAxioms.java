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
import java.util.stream.*;

import org.apache.commons.lang3.*;

/*
	Takes the current batch of axiom rules, and a partially annotated assay, and figures out which values in
	a certain assignment branch are still allowed.
*/

public class WinnowAxioms
{
	private AxiomVocab axioms;
	
	// a "subject" (for input purposes) is a valueURI and the schema tree for the assignment that it belongs to
	public static final class SubjectContent
	{
		public String uri;
		public SchemaTree tree;
		
		public SubjectContent(String uri, SchemaTree tree)
		{
			this.uri = uri;
			this.tree = tree;
		}
	}
	
	// a "keyword" (for input & stashing purposes) is a literal belonging to an assignment (or free text, if propURI is null)
	public static final class KeywordContent
	{
		public String text;
		public String propURI;
		private String substrate; // processed version of text that's searchable by indexOf
		
		public KeywordContent(String text, String propURI)
		{
			this.text = text;
			this.propURI = propURI;
			substrate = " " + String.join(" ", text.trim().split("\\s+")) + " ";
		}

		// returns true if the keyword query is applicable to the content
		private boolean matchKeyword(AxiomVocab.Keyword query)
		{
			if (!Util.equals(query.propURI, propURI)) return false;
			return substrate.indexOf(" " + query.text + " ") >= 0;
		}		
	}
	
	// outcome of a winnowing: it's either a valueURI or a text literal, just one of them is defined
	public static final class Result
	{
		public String uri;
		public String literal;
		
		public Result(String uri, String literal)
		{
			this.uri = uri;
			this.literal = literal;
		}
		public static Result fromURI(String uri) {return new Result(uri, null);}
		public static Result fromLiteral(String literal) {return new Result(null, literal);}

		@Override
		public boolean equals(Object obj)
		{
			if (obj == null || !(obj instanceof Result)) return false;
			Result other = (Result)obj;
			return Util.safeString(uri).equals(Util.safeString(other.uri)) && 
				   Util.safeString(literal).equals(Util.safeString(other.literal));
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(uri, literal);
		}
	}
	
	// working datastructure for processing a winnowing request
	private static final class Process
	{
		// each entry of inputValues starts with {uri,parent,parent',..,root}, i.e. a backward sequence tracing back to the beginning
		// of the tree, which is sufficient information to match subjects where wholeBranch is set
		List<SchemaTree> subjectTrees = new ArrayList<>();
		List<List<String>> subjectLineages = new ArrayList<>();
		
		// text & literal values stashed here, for triggering keyword rules
		List<KeywordContent> keywordContents = new ArrayList<>();
		
		// the tree for the values that are to be winnowed down
		SchemaTree impactTree;
		Set<String> impactValues = new HashSet<>();
		
		// list of all permitted values, which starts as everything in the tree, and gets reduced as axioms are applied
		Set<String> allowedValues = new HashSet<>();
	}

	// branches that are just fundamentally uninteresting	
	private static final Set<String> SPECIAL_EXCLUSIONS = new HashSet<>();
	static
	{
		SPECIAL_EXCLUSIONS.add(AssayUtil.URI_ABSENCE);
	}

	// ------------ public methods ------------

	public WinnowAxioms(AxiomVocab axioms)
	{
		this.axioms = axioms;
	}
	
	// return a set containing all of the valid URIs for particular "impactTree", given the preexisting subject content, which
	// may trigger any number of axioms; the return value is null if none of the axioms had any impact, otherwise it is a whitelist
	// of all allowed values; the whitelist may include everything (which implies that some value is required) and it can be blank
	// (which implies that all possible values are ruled out)
	public Set<Result> winnowBranch(List<SubjectContent> subjects, List<KeywordContent> keywords, SchemaTree impactTree)
	{
		return winnowBranch(subjects.toArray(new SubjectContent[subjects.size()]), 
							keywords.toArray(new KeywordContent[keywords.size()]), impactTree);
	}
	public Set<Result> winnowBranch(SubjectContent[] subjects, KeywordContent[] keywords, SchemaTree impactTree)
	{
		Process proc = setupProcess(subjects, keywords, impactTree);
		
		Set<String> whitelist = new HashSet<>(); // whitelist which is built up from the union of impact terms
		Set<String> whiteexcl = new HashSet<>(); // exclusive whitelist (which takes precedence over above)
		Set<String> blacklist = new HashSet<>(); // blacklist: knocks out options explicitly
		
		for (AxiomVocab.Rule rule : axioms.getRules()) if (isTriggered(proc, rule))
		{
			Set<String> impact = assembleImpact(proc, rule);
			if (rule.type == AxiomVocab.Type.LIMIT) 
			{
				if (!rule.exclusive)
					whitelist.addAll(impact);
				else
					whiteexcl.addAll(impact);
			}
			else if (rule.type == AxiomVocab.Type.EXCLUDE) blacklist.addAll(impact);
		}
		
		if (whitelist.isEmpty() && whiteexcl.isEmpty() && blacklist.isEmpty()) return null;
		
		for (String uri : proc.impactValues)
		{
			if (blacklist.contains(uri)) continue;
			if (!whiteexcl.isEmpty())
			{
				if (whiteexcl.contains(uri)) proc.allowedValues.add(uri);
			}
			else if (whitelist.isEmpty() || whitelist.contains(uri)) proc.allowedValues.add(uri);
		}
		
		Set<Result> results = new HashSet<>();
		for (String uri : proc.allowedValues) results.add(Result.fromURI(uri));
		return results;
	}
	
	// companion to the winnowing process: rules that trigger literals aren't reducing a tree - they're adding to an unconstrained
	// list, but the triggering mechanism is the same; returns null if nothing
	public Set<Result> impliedLiterals(List<SubjectContent> subjects, List<KeywordContent> keywords, Schema.Assignment assn)
	{
		return impliedLiterals(subjects.toArray(new SubjectContent[subjects.size()]), 
							   keywords.toArray(new KeywordContent[keywords.size()]), assn);
	}
	public Set<Result> impliedLiterals(SubjectContent[] subjects, KeywordContent[] keywords, Schema.Assignment assn)
	{
		Set<Result> results = null;

		Process proc = setupProcess(subjects, keywords, null);
		for (AxiomVocab.Rule rule : axioms.getRules())
		{
			boolean checkedTrigger = false;
			for (AxiomVocab.Term term : rule.impact) if (term.valueURI == null && assn.propURI.equals(term.propURI))
			{
				if (!checkedTrigger && !isTriggered(proc, rule)) break;
				checkedTrigger = true;
				if (results == null) results = new HashSet<>();
				results.add(Result.fromLiteral(term.valueLabel));
			}
		}
		
		return results;
	}
	
	// convenience methods to obtain just the URI or literal subsets
	public static Set<String> filterURI(Set<Result> results)
	{
		if (results == null) return null;
		return results.stream().map(r -> r.uri).filter(u -> u != null).collect(Collectors.toSet());
	}
	public static Set<String> filterLiteral(Set<Result> results)
	{
		if (results == null) return null;
		return results.stream().map(r -> r.literal).filter(l -> l != null).collect(Collectors.toSet());
	}
	
	// returns a list of rules that were triggered with regard to a given tree: this is very useful for figuring out why axioms
	// eliminated some particular option
	public AxiomVocab.Rule[] branchTriggers(List<SubjectContent> subjects, List<KeywordContent> keywords, SchemaTree impactTree)
	{
		return branchTriggers(subjects.toArray(new SubjectContent[subjects.size()]), 
							 keywords.toArray(new KeywordContent[keywords.size()]), impactTree);
	}
	public AxiomVocab.Rule[] branchTriggers(SubjectContent[] subjects, KeywordContent[] keywords, SchemaTree impactTree)
	{
		Process proc = setupProcess(subjects, keywords, impactTree);
		
		List<AxiomVocab.Rule> triggers = new ArrayList<>();
		for (AxiomVocab.Rule rule : axioms.getRules()) if (isTriggered(proc, rule)) triggers.add(rule);
		return triggers.toArray(new AxiomVocab.Rule[triggers.size()]);
	}
	
	// composite use case: for a list of annotations, return a list of those which are in violation, i.e. basically contradictions
	public DataObject.Annotation[] violatingAxioms(SchemaDynamic graft, DataObject.Annotation[] annotations)
	{
		Schema schema = graft.getResult();
		
		class GroupAssn
		{
			String propURI;
			String[] groupNest;
			SchemaTree tree;
			List<SubjectContent> subjects = new ArrayList<>();
		}
		Map<String, GroupAssn> mapGroup = new HashMap<>(); // one for assignment with at least one term
		
		// for each unique assignment, gather together relevant information, most importantly the tree
		for (DataObject.Annotation annot : annotations) if (annot.valueURI != null)
		{
			String key = Schema.keyPropGroup(annot.propURI, annot.groupNest);
			GroupAssn grp = mapGroup.get(key);
			if (grp == null) 
			{
				Schema.Assignment[] assnList = schema.findAssignmentByProperty(annot.propURI, annot.groupNest);
				if (assnList.length == 0) continue; // orphaned, skip
				
				SchemaDynamic.SubTemplate subt = graft.relativeAssignment(annot.propURI, annot.groupNest);
				if (subt == null) continue;
				SchemaTree tree = Common.obtainTree(subt.schema, annot.propURI, subt.groupNest);
				if (tree == null) continue;

				mapGroup.put(key, grp = new GroupAssn());
				grp.propURI = annot.propURI;
				grp.groupNest = annot.groupNest;
				grp.tree = tree;
			}
			grp.subjects.add(new SubjectContent(annot.valueURI, grp.tree));
		}
		
		// run through each distinct assignment and observe any winnowing effects
		List<SubjectContent> subjects = new ArrayList<>();
		List<KeywordContent> keywords = new ArrayList<>(); // (stays empty)
		for (GroupAssn grp : mapGroup.values()) subjects.addAll(grp.subjects);

		List<DataObject.Annotation> violations = new ArrayList<>();
		for (Map.Entry<String, GroupAssn> entry : mapGroup.entrySet())
		{
			String key = entry.getKey();
			GroupAssn grp = entry.getValue();
			Set<Result> applicable = winnowBranch(subjects, keywords, grp.tree);
			if (applicable == null) continue; // means unaffected
			
			
			for (SubjectContent subject : grp.subjects) if (!applicable.stream().anyMatch(r -> subject.uri.equals(r.uri)))
				violations.add(new DataObject.Annotation(grp.propURI, subject.uri, grp.groupNest));
		}
		return violations.toArray(new DataObject.Annotation[violations.size()]);
	}
	
	// for a given annotation that was found to be implied by axiom rules, find the list of annotations that are implicated in narrowing it
	// down to a singleton; there can be more than one
	public String[] findJustificationTriggers(SchemaDynamic graft, DataObject.Annotation[] annotList, DataObject.Annotation target)
	{
		Schema schema = graft.getResult();
		List<SubjectContent> subjects = new ArrayList<>();
		
		for (DataObject.Annotation annot : annotList) 
		{
			SubjectContent subj = annotationToSubject(graft, annot);
			if (subj != null) subjects.add(subj);
		}
		if (subjects.size() == 0) return new String[0];

		SchemaDynamic.SubTemplate subt = graft.relativeAssignment(target.propURI, target.groupNest);
		if (subt == null) return null;
		SchemaTree targetTree = Common.obtainTree(subt.schema, target.propURI, subt.groupNest);
		if (targetTree == null) return null;
		Schema.Assignment[] assnList = subt.schema.findAssignmentByProperty(target.propURI, subt.groupNest);
		if (assnList.length == 0) return null;

		// scan through all of the rules, and each time one triggers, capture the URI(s) responsible for it
		Process proc = setupProcess(subjects.toArray(new SubjectContent[subjects.size()]), null, targetTree);
		Set<String> allTriggers = new HashSet<>();
		for (AxiomVocab.Rule rule : axioms.getRules()) if (rule.subject != null && isTriggered(proc, rule))
		{
			// make sure the rule impacts the target
			/* already checked the impactValues, so this is redundant - and it also breaks the special deal
			   with absence terms...
			boolean hasImpact = false;
			for (AxiomVocab.Term term : rule.impact) if (targetTree.getNode(term.valueURI) != null) 
			{
				hasImpact = true; 
				break;
			}
			if (!hasImpact) continue;*/
			
			// find the specific annotation(s) that triggered the rule
			for (SubjectContent subj : subjects)
			{
				for (AxiomVocab.Term term : rule.subject)
				{
					boolean matched = false;
					if (term.wholeBranch)
					{
						SchemaTree.Node node = subj.tree.getNode(subj.uri);
						for (; node != null; node = node.parent)
						{
							if (node.uri.equals(term.valueURI)) {matched = true; break;}
						}
					}
					else matched = term.valueURI.equals(subj.uri);
					if (matched) allTriggers.add(subj.uri);
				}
			}
		}
		
		return allTriggers.toArray(new String[allTriggers.size()]);
	}
	
	// find why a certain literal addition got triggered
	public String[] findLiteralTriggers(SchemaDynamic graft, DataObject.Annotation[] annotList, 
										Schema.Assignment assn, String literal)
	{
		Schema schema = graft.getResult();
		List<SubjectContent> subjects = new ArrayList<>();
		
		for (DataObject.Annotation annot : annotList) 
		{
			SubjectContent subj = annotationToSubject(graft, annot);
			if (subj != null) subjects.add(subj);
		}
		if (subjects.size() == 0) return new String[0];

		SchemaDynamic.SubTemplate subt = graft.relativeAssignment(assn.propURI, assn.groupNest());
		if (subt == null) return null;
		
		// scan through all of the rules, and each time one triggers, capture the URI(s) responsible for it
		Process proc = setupProcess(subjects.toArray(new SubjectContent[subjects.size()]), null, null);
		Set<String> allTriggers = new HashSet<>();
		for (AxiomVocab.Rule rule : axioms.getRules()) if (rule.subject != null && isTriggered(proc, rule))
		{
			// make sure the rule impacts the target
			boolean hasImpact = false;
			for (AxiomVocab.Term term : rule.impact) 
				if (term.valueURI == null && assn.propURI.equals(term.propURI) && literal.equals(term.valueLabel)) 
			{
				hasImpact = true; 
				break;
			}
			if (!hasImpact) continue;
			
			// find the specific annotation(s) that triggered the rule
			for (SubjectContent subj : subjects)
			{
				for (AxiomVocab.Term term : rule.subject)
				{
					boolean matched = false;
					if (term.wholeBranch)
					{
						SchemaTree.Node node = subj.tree.getNode(subj.uri);
						for (; node != null; node = node.parent)
						{
							if (node.uri.equals(term.valueURI)) {matched = true; break;}
						}
					}
					else matched = term.valueURI.equals(subj.uri);
					if (matched) allTriggers.add(subj.uri);
				}
			}
		}
		
		return allTriggers.toArray(new String[allTriggers.size()]);
	}	
	
	// for a given target annotation that was found to be in violation by axiom rules, find the list of annotations from the main list
	// that are implicated in the rejection; there can be more than one
	public String[] findViolationTriggers(SchemaDynamic graft, DataObject.Annotation[] annotList, DataObject.Annotation target)
	{
		Schema schema = graft.getResult();
		List<SubjectContent> subjects = new ArrayList<>();
		SchemaTree targetTree = null;
		
		for (DataObject.Annotation annot : annotList) 
		{
			SubjectContent subj = annotationToSubject(graft, annot);
			if (subj == null) continue;
			if (Schema.samePropGroupNest(annot.propURI, annot.groupNest, target.propURI, target.groupNest) && 
										 annot.valueURI.equals(target.valueURI))
				targetTree = subj.tree;
			else
				subjects.add(subj);
		}
		
		if (subjects.size() == 0 || targetTree == null) return new String[0];
		
		// scan through all of the rules, and each time one triggers, capture the URI(s) responsible for it
		Process proc = setupProcess(subjects.toArray(new SubjectContent[subjects.size()]), null, targetTree);
		Set<String> allTriggers = new HashSet<>();
		for (AxiomVocab.Rule rule : axioms.getRules())
		{
			String[] uriList = whichTriggeredURIExclude(proc, rule, target.valueURI, targetTree);
			if (uriList != null) for (String uri : uriList) allTriggers.add(uri);
		}
		
		return allTriggers.toArray(new String[allTriggers.size()]);
	}
	
	// convenience method to convert an annotation to a subject, which involves looking up its schematree; returns null 
	// if something goes wrong, such as out of schema or no tree
	public static SubjectContent annotationToSubject(SchemaDynamic graft, DataObject.Annotation annot)
	{
		Schema.Assignment[] assnList = graft.getResult().findAssignmentByProperty(annot.propURI, annot.groupNest);
		if (assnList.length == 0) return null; // orphaned, skip
		
		SchemaDynamic.SubTemplate subt = graft.relativeAssignment(annot.propURI, annot.groupNest);
		if (subt == null) return null;
		SchemaTree tree = Common.obtainTree(subt.schema, annot.propURI, subt.groupNest);
		if (tree == null) return null;
		
		return new SubjectContent(annot.valueURI, tree);
	}
	
	// ------------ private methods ------------
	
	// populates the content necessary to start a winnowing process
	private Process setupProcess(SubjectContent[] subjects, KeywordContent[] keywords, SchemaTree impactTree)
	{
		Process proc = new Process();
		
		skip: for (SubjectContent subject : subjects)
		{
			List<String> lineage = new ArrayList<>();
			for (SchemaTree.Node node = subject.tree.getNode(subject.uri); node != null; node = node.parent) 
			{
				if (SPECIAL_EXCLUSIONS.contains(node.uri)) continue skip;
				lineage.add(node.uri);
			}
			if (lineage.size() > 0) 
			{
				proc.subjectTrees.add(subject.tree);
				proc.subjectLineages.add(lineage);
			}
		}
		if (keywords != null) for (KeywordContent keyword : keywords) proc.keywordContents.add(keyword);
		proc.impactTree = impactTree;
		
		if (impactTree != null) for (SchemaTree.Node node : impactTree.getFlat()) proc.impactValues.add(node.uri);
		
		// special deal: the N/A placeholder is always included implicitly
		proc.impactValues.add(AssayUtil.URI_NOTAPPLICABLE);
		
		return proc;
	}
	
	// returns true if the rule could have any impact (preliminary filter)
	private boolean isImpacted(Process proc, AxiomVocab.Rule rule)
	{
		for (AxiomVocab.Term term : rule.impact) if (proc.impactValues.contains(term.valueURI)) 
		{
			// match assignment if it's specified
			if (term.propURI != null)
			{
				Schema.Assignment assn = proc.impactTree.getAssignment();
				if (!term.propURI.equals(assn.propURI)) continue;
				if (term.groupNest != null && !Schema.sameGroupNest(term.groupNest, assn.groupNest())) continue;
			}
			return true;
		}
		return false;
	}	
	
	// returns true if the rule is triggered by the subject input conditions; the trigger must have some effect on the values that
	// we're interested in, otherwise can skip
	private boolean isTriggered(Process proc, AxiomVocab.Rule rule)
	{
		if (proc.impactTree != null && !isImpacted(proc, rule)) return false;
	
		boolean triggered = false;
		if (rule.subject != null)
		{
			for (AxiomVocab.Term term : rule.subject)
			{
				boolean matched = false;
				for (int n = 0; n < proc.subjectLineages.size(); n++)
				{
					// match assignment if it's specified
					if (term.propURI != null)
					{
						SchemaTree tree = proc.subjectTrees.get(n);
						Schema.Assignment assn = tree.getAssignment();
						if (!term.propURI.equals(assn.propURI)) continue;
						if (term.groupNest != null && !Schema.sameGroupNest(term.groupNest, assn.groupNest())) continue;
					}
					
					List<String> lineage = proc.subjectLineages.get(n);
					if (term.wholeBranch)
					{
						if (lineage.contains(term.valueURI)) {matched = true; break;}
					}
					else
					{
						if (lineage.get(0).equals(term.valueURI)) {matched = true; break;}
					}
				}
				if (!matched) return false;
			}
			return true;
		}
		else if (rule.keyword != null)
		{
			for (KeywordContent kc : proc.keywordContents) if (kc.matchKeyword(rule.keyword)) return true;
		}
		return false;
	}
	
	// variation on isTriggered: looks to see what (if any) value URIs trigger the rule causing the targetURI to be excluded; returns 
	// all of the subject URIs that apply, or null if none
	private String[] whichTriggeredURIExclude(Process proc, AxiomVocab.Rule rule, String targetURI, SchemaTree targetTree)
	{
		if (rule.subject == null) return null;
		if (!isImpacted(proc, rule)) return null;

		String[] uriList = null;
		for (AxiomVocab.Term term : rule.subject)
		{
			for (int n = 0; n < proc.subjectLineages.size(); n++)
			{
				// match assignment if it's specified
				if (term.propURI != null)
				{
					SchemaTree tree = proc.subjectTrees.get(n);
					Schema.Assignment assn = tree.getAssignment();
					if (!term.propURI.equals(assn.propURI)) continue;
					if (term.groupNest != null && !Schema.sameGroupNest(term.groupNest, assn.groupNest())) continue;
				}
				
				List<String> lineage = proc.subjectLineages.get(n);
				if (term.wholeBranch)
				{
					if (lineage.contains(term.valueURI)) uriList = ArrayUtils.add(uriList, lineage.get(0));
				}
				else
				{
					if (lineage.get(0).equals(term.valueURI)) uriList = ArrayUtils.add(uriList, lineage.get(0));
				}
			}
			
		}
		if (uriList == null) return null;

		Set<String> impact = assembleImpact(proc, rule);
		if (rule.type == AxiomVocab.Type.LIMIT)
		{
			return impact.contains(targetURI) ? null : uriList; // only applies if it's being excluded from the impact
		}
		else if (rule.type == AxiomVocab.Type.EXCLUDE)
		{
			return impact.contains(targetURI) ? uriList : null; // only applies if it's be included in the impact
		}
		return null;
	}
	
	// collect the union of the impacts as a set
	private Set<String> assembleImpact(Process proc, AxiomVocab.Rule rule)
	{
		Set<String> values = new HashSet<>();
		
		for (AxiomVocab.Term term : rule.impact)
		{
			SchemaTree.Node node = proc.impactTree.getNode(term.valueURI);
			if (node == null || !term.wholeBranch)
			{
				values.add(term.valueURI);
				continue;
			}
			List<SchemaTree.Node> stack = new ArrayList<>();
			stack.add(node);
			while (!stack.isEmpty())
			{
				node = stack.remove(0);
				values.add(node.uri);
				stack.addAll(node.children);
			}
		}
		
		return values;
	}
}

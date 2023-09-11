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

import com.cdd.bao.template.*; 
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import org.json.*;

/*
	AxiomVocab: serialisable collection of "axioms", which are distilled out from various sources to provide useful guidelines
	about how to annotate an assay.
	
	JSON serialisation format: array of axiom rules, of the form:
	
		{
			"type": string
			"subject": term(s) OR "keyword": keyword
			"impact": term(s)
		}
		
	Where "term(s)" is either an object, or an array of objects, of the form:
		{
			"valueURI": URI that matches at least one schema tree; can be abbreviated
			"valueLabel": if defined when URI is not, this indicates a literal
			"wholeBranch: boolean (default = false); if true, matches the whole branch
			"propURI"/"groupNest": (default = null) optional parameters to be more specific about location
		}
		
	and "keyword" is an object of:
		{	
			"keyword": identifier to match a plain text literal
			"propURI": the assignment(s) to apply to
		}
			
	When a subject has more than one term, each of them must apply to trigger the axiom ("AND").
	
	The terms in the impact list are added to the whitelist for each tree to which they apply. Any tree that has entries in its
	whitelist becomes restricted to just those values. Individual axioms can have the "exclusive" property switched on, which
	makes the whitelist reduction operation into an intersection rather than a union.
*/

public class AxiomVocab
{
	/*
		LIMIT = presence of a term implies the exclusive existence of other terms
		EXCLUDE = presence of a term implies that other terms are not eligible
		BLANK = presence of a term implies that another branch category should not be populated
		REQUIRED = presence of a term implies that another branch category should have something (i.e. not blank)
	*/
	
	public enum Type
	{
		LIMIT(1),
		EXCLUDE(2);
		/* use case is unclear for these
		BLANK(3),
		REQUIRED(4);*/
		
		private final int raw;
		Type(int raw) {this.raw = raw;}
		public int raw() {return this.raw;}
		public static Type valueOf(int rawVal)
		{
			for (Type t : values()) if (t.raw == rawVal) return t;
			return null;
		}
	}

	public static class Term
	{
		public String valueURI = null;
		public String valueLabel = null;
		public boolean wholeBranch = false;
		public String propURI = null;
		public String[] groupNest = null;
		
		public Term() {}
		public Term(String valueURI, boolean wholeBranch)
		{
			this.valueURI = valueURI;
			this.wholeBranch = wholeBranch;
		}
		public Term(String valueURI, boolean wholeBranch, String propURI, String[] groupNest)
		{
			this.valueURI = valueURI;
			this.wholeBranch = wholeBranch;
			this.propURI = propURI;
			this.groupNest = groupNest;
		}
		public Term(String valueURI, String valueLabel, boolean wholeBranch, String propURI, String[] groupNest)
		{
			this.valueURI = valueURI;
			this.valueLabel = valueLabel;
			this.wholeBranch = wholeBranch;
			this.propURI = propURI;
			this.groupNest = groupNest;
		}
		
		@Override
		public String toString()
		{
			String str = ModelSchema.collapsePrefix(valueURI) + "/" + wholeBranch;
			if (valueLabel != null) str += "/\"" + valueLabel + "\"";
			if (propURI != null) str += "/" + ModelSchema.collapsePrefix(propURI);
			if (groupNest != null) str += "/" + Util.arrayStr(ModelSchema.collapsePrefixes(groupNest));
			return str;
		}
		
		@Override
		public boolean equals(Object obj)
		{
			if (obj == null || !(obj instanceof Term)) return false;
			Term other = (Term)obj;

			return Util.equals(valueURI, other.valueURI) && wholeBranch == other.wholeBranch && 
				Util.equals(valueLabel, other.valueLabel) &&
				Schema.samePropGroupNest(Util.safeString(propURI), groupNest, Util.safeString(other.propURI), other.groupNest);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(valueURI, wholeBranch, valueLabel, propURI, groupNest);
		}
	}
	
	public static class Keyword
	{
		public String text = null; // short string that must be present, and separated by a boundary
		public String propURI = null; // identifies eligible literal-type assignments; null = look in main text
		
		public Keyword() {}
		public Keyword(String text, String propURI)
		{
			this.text = text;
			this.propURI = propURI;
		}
		
		@Override
		public String toString()
		{
			return ModelSchema.collapsePrefix(text) + "/" + propURI;
		}
		
		@Override
		public boolean equals(Object obj)
		{
			if (obj == null || !(obj instanceof Keyword)) return false;
			Keyword other = (Keyword)obj;
			return Util.safeString(text).equals(Util.safeString(other.text)) && propURI == other.propURI;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(text, propURI);
		}		
	}
	
	public static class Rule
	{
		public Type type = null;
		public boolean exclusive = false; // when true, filters branches more aggressively using intersection rather
										  // than union of related axioms

		// selection of the subject domain; either subject or keyword must be filled out
		public Term[] subject = null;
		public Keyword keyword = null; 
		
		// the object domain of the rule: the meaning varies depending on type
		public Term[] impact = null;
		
		public Rule() {}
		public Rule(Type type)
		{
			this.type = type;
		}
		public Rule(Type type, Term[] subject) {this(type, subject, null);}
		public Rule(Type type, Term[] subject, Term[] impact)
		{
			this.type = type;
			this.subject = subject;
			this.impact = impact;
		}
		public Rule(Type type, Keyword keyword) {this(type, keyword, null);}
		public Rule(Type type, Keyword keyword, Term[] impact)
		{
			this.type = type;
			this.keyword = keyword;
			this.impact = impact;
		}
		
		@Override
		public String toString()
		{
			StringBuilder str = new StringBuilder();
			str.append(type + " type axiom; ");
			if (exclusive) str.append("exclusive; ");
		
			StringJoiner sj = new StringJoiner(",", "[", "]");
			if (subject != null) 
			{
				for (Term s : subject) sj.add(s.toString());
				str.append("subject: " + sj);
			}
			if (keyword != null) str.append("keyword: [" + keyword + "]");
			
			sj = new StringJoiner(",", "[", "]");
			if (impact != null) for (Term s : impact) sj.add(s.toString());
			str.append(", impacts: " + sj);

			return str.toString();
		}
		
		@Override
		public boolean equals(Object obj)
		{
			if (obj == null || !(obj instanceof Rule)) return false;
			Rule other = (Rule)obj;
			if (type != other.type) return false;
			return Arrays.equals(subject, other.subject) && Objects.equals(keyword, other.keyword) && 
					Arrays.equals(impact, other.impact) && exclusive == other.exclusive;
		}
		
		@Override
		public int hashCode()
		{
			return Objects.hash(type, keyword, Arrays.hashCode(subject), Arrays.hashCode(impact), exclusive);
		}
	}
	
	private List<Rule> rules = new ArrayList<>();

	// ------------ public methods ------------

	public AxiomVocab()
	{
	}

	// access to content	
	public int numRules() {return rules.size();}
	public Rule getRule(int idx) {return rules.get(idx);}
	public Rule[] getRules() {return rules.toArray(new Rule[rules.size()]);}
	public void addRule(Rule rule) {rules.add(rule);}
	public void setRule(int idx, Rule rule) {rules.set(idx, rule);}
	public void deleteRule(int idx) {rules.remove(idx);}
	public void deleteAllRules() {rules.clear();}
	
	// write the content as JSON (somewhat human readable, with optional vocab-to-label translation)
	public void serialise(File file) throws IOException {serialise(file, null);}
	public void serialise(File file, SchemaVocab schvoc) throws IOException
	{
		try (Writer wtr = new FileWriter(file)) {serialise(wtr, schvoc);}
	}
	public void serialise(Writer wtr, SchemaVocab schvoc) throws IOException
	{
		JSONArray jsonRules = new JSONArray();
		for (Rule rule : rules) jsonRules.put(formatJSONRule(rule, schvoc));
		jsonRules.write(wtr, 2);
	}
	
	// parse out the raw binary into a living object
	public static AxiomVocab deserialise(File file) throws IOException
	{
		try (Reader rdr = new FileReader(file)) {return deserialise(rdr);}
	}
	public static AxiomVocab deserialise(Reader rdr) throws IOException
	{
		JSONArray json = new JSONArray(new JSONTokener(rdr));
	
		AxiomVocab av = new AxiomVocab();
		for (int n = 0; n < json.length(); n++)
		{
			JSONObject jsonRule = json.optJSONObject(n);
			if (jsonRule == null) continue; // is OK to skip
			try
			{
				Rule rule = parseJSONRule(jsonRule);
				av.addRule(rule);
			}
			catch (IOException ex) {throw new IOException("Parsing error: " + ex.getMessage() + " for rule: " + jsonRule.toString(), ex);}
		}

		return av;
	}

	// ------------ private methods ------------
	
	// turning rule objects into JSON
	private JSONObject formatJSONRule(Rule rule, SchemaVocab schvoc)
	{
		JSONObject json = new JSONObject();
		json.put("type", rule.type.toString().toLowerCase());
		
		if (rule.subject != null) 
		{
			JSONArray jsonSubject = new JSONArray();
			for (Term term : rule.subject) jsonSubject.put(formatJSONTerm(term, schvoc));
			if (jsonSubject.length() != 1)
				json.put("subject", jsonSubject);
			else
				json.put("subject", jsonSubject.getJSONObject(0));
		}
		
		if (rule.keyword != null) json.put("keyword", formatJSONKeyword(rule.keyword, schvoc));
		
		JSONArray jsonImpact = new JSONArray();
		if (rule.impact != null) for (Term term : rule.impact) jsonImpact.put(formatJSONTerm(term, schvoc));
		if (jsonImpact.length() != 1)
			json.put("impact", jsonImpact);
		else
			json.put("impact", jsonImpact.getJSONObject(0));
		
		return json;
	}
	private JSONObject formatJSONTerm(Term term, SchemaVocab schvoc)
	{
		JSONObject json = new JSONObject();
		if (schvoc != null) json.put("label", schvoc.getLabel(term.valueURI));
		json.put("valueURI", term.valueURI);
		json.put("wholeBranch", term.wholeBranch);
		if (term.valueLabel != null) json.put("valueLabel", term.valueLabel);
		if (term.propURI != null) json.put("propURI", term.propURI);
		if (term.groupNest != null) json.put("groupNest", term.groupNest);
		return json;
	}
	private JSONObject formatJSONKeyword(Keyword keyword, SchemaVocab schvoc)
	{
		JSONObject json = new JSONObject();
		json.put("text", keyword.text);
		if (keyword.propURI != null)
		{
			json.put("propURI", keyword.propURI);
			if (schvoc != null) json.put("propLabel", schvoc.getLabel(keyword.propURI));
		}
		return json;
	}

	// unpacking JSON-formatted objects into rules (or hard fail)
	private static Rule parseJSONRule(JSONObject json) throws IOException
	{
		Rule rule = new Rule();

		String strType = json.getString("type");
		try {rule.type = Type.valueOf(strType.toUpperCase());}
		catch (Exception ex) {throw new IOException("Invalid rule type: " + strType);}
		
		rule.exclusive = json.optBoolean("exclusive", false);
		
		if (json.has("subject"))
		{
			List<Term> subject = new ArrayList<>();
			JSONArray jsonSubject = json.optJSONArray("subject");
			if (jsonSubject == null)
			{
				JSONObject jsonRule = json.getJSONObject("subject");
				subject.add(parseJSONTerm(jsonRule));
			}
			else
			{
				for (int n = 0; n < jsonSubject.length(); n++)
				{
					JSONObject jsonRule = jsonSubject.optJSONObject(n);
					if (jsonRule != null) subject.add(parseJSONTerm(jsonRule));
				}
			}
			rule.subject = subject.toArray(new Term[subject.size()]);
		}
		else 
		{
			JSONObject jsonKeyword = json.optJSONObject("keyword");
			if (jsonKeyword != null) rule.keyword = parseJSONKeyword(jsonKeyword);
			else throw new IOException("Rule must provide subject or keyword.");
		}
		
		List<Term> impact = new ArrayList<>();
		JSONArray jsonImpact = json.optJSONArray("impact");
		if (jsonImpact == null)
		{
			JSONObject jsonRule = json.getJSONObject("impact");
			impact.add(parseJSONTerm(jsonRule));
		}
		else
		{
			for (int n = 0; n < jsonImpact.length(); n++)
			{
				JSONObject jsonRule = jsonImpact.optJSONObject(n);
				if (jsonRule != null) impact.add(parseJSONTerm(jsonRule));
			}
		}
		rule.impact = impact.toArray(new Term[impact.size()]);
		
		return rule;
	}
	private static Term parseJSONTerm(JSONObject json) throws IOException
	{
		Term term = new Term();
		term.valueURI = ModelSchema.expandPrefix(json.optString("valueURI", null));
		term.wholeBranch = json.optBoolean("wholeBranch", false);
		term.valueLabel = json.optString("valueLabel", null);
		term.propURI = ModelSchema.expandPrefix(json.optString("propURI", null));
		if (json.has("groupNest")) term.groupNest = ModelSchema.expandPrefixes(json.getJSONArray("groupNest").toStringArray());
		return term;
	}
	private static Keyword parseJSONKeyword(JSONObject json) throws IOException
	{
		Keyword keyword = new Keyword();
		keyword.text = json.getString("text");
		if (json.has("propURI")) keyword.propURI = ModelSchema.expandPrefix(json.getString("propURI"));
		return keyword;
	}
}




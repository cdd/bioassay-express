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

import com.cdd.bao.util.*;

import java.util.*;
import java.util.regex.*;

/*
	Keyword matching: takes a selection string (which has various meta formatting options) and allows it to be used to select
	various forms of text, whether they be labels or long text descriptions.
*/

public class KeywordMatcher
{
	private String selector;
	
	public enum Type
	{
		NONE,
		TEXT, // search for text substring in the most simplistic way
		NUMBER, // number-formatted string 
		REGEX, // regular expression
		RANGE, // separator for a range (e.g. the connecting part of [1..10] or [1-10]
		DELTA, // separator for a delta range (e.g. from [1 +/- 0.5]")
		AND, // logical AND (&, &&)
		OR, // logical OR (|, ||)
		NOT, // logical NOT (!)
		GREATER, // greater than (>)
		GREQUAL, // greater than or equal (>=)
		LESSTHAN, // less than (<)
		LTEQUAL, // less than or equal (<=)
		OPEN, // open bracket (,[
		CLOSE, // close bracket ),]
	}
	
	public static final class Block
	{
		public Type type = Type.TEXT;
		public String value;
		public double numeric = Double.NaN; // only defined for Type.NUMBER
		
		public Block() {}
		public Block(Type type, String value) {this.type = type; this.value = value;}
	}
	private Block[] blocks;
	
	public static final class Clause
	{
		public Type compare = Type.NONE;
		public boolean invert = false;
		
		// either blocks OR subClauses must be defined, but not both
		public Block[] blocks = null;
		public Clause[] subClauses = null;
	}
	private Clause[] rootClauses = null;
	
	// custom exception for reporting a malformed keyword selector query
	public static final class FormatException extends IllegalArgumentException
	{
		public FormatException(String msg) {super(msg);}
	}
	
	// special splitters for blocks with particular syntactic meaning
	private static final Pattern PTN_NUMERIC = Pattern.compile("^([\\d\\.\\-][\\d\\.\\-eE]*)$");
	private static final Pattern PTN_INEQUALITY = Pattern.compile("^(\\<|\\>|\\<=|>=)([\\d\\.\\-eE]+)$");
	private static final Pattern PTN_RANGE = Pattern.compile("^(\\-?\\d*\\.?\\d+)(\\-|\\.\\.)(\\-?\\d*\\.?\\d+)$");

	// ------------ public methods ------------

	public KeywordMatcher(String selector)
	{
		this.selector = selector;
		
		splitSegments();
		assignBlocks();
	}
	
	// the original selection string
	public String getSelector() {return selector;}
	
	public Block[] getSelectorBlocks() {return blocks;}
	
	// returns true if the selector is effectively empty
	public boolean isBlank() {return blocks.length == 0;}
	
	// obtain information about the lexicography of the selector
	public Block[] getBlocks() {return blocks;}
	
	// obtain the marked up list of clauses
	public Clause[] getClauses() {return rootClauses;}
	
	// marks up the blocks into clauses, throwing an exception if there is a syntax error of some kind; note that this will be done
	// lazily if not called, but this is an opportunity to validate the query
	public void prepare() throws FormatException
	{
		for (Block blk : blocks) if (blk.type == Type.NUMBER)
		{
			try {blk.numeric = Double.valueOf(blk.value);}
			catch (NumberFormatException ex) {throw new FormatException("Invalid numeric: [" + blk.value + "]");}
		}
	
		List<Clause> root = new ArrayList<>();

		Type compare = Type.AND;
		final int sz = blocks.length;
		for (int n = 0; n < sz; n++)
		{
			Block blk = blocks[n];
			
			if (root.size() > 0 && (blk.type == Type.AND || blk.type == Type.OR)) compare = blk.type;
			else if (blk.type == Type.NUMBER && n + 2 < sz && blocks[n + 1].type == Type.RANGE && blocks[n + 2].type == Type.NUMBER)
			{
				Clause clause = new Clause();
				//clause.compare = blk.type;
				clause.blocks = new Block[]{blk, blocks[n + 1], blocks[n + 2]};
				root.add(clause);	
				n += 2;
			}
			else if (blk.type == Type.TEXT || blk.type == Type.NUMBER)
			{
				Clause clause = new Clause();
				clause.compare = root.size() == 0 ? Type.NONE : compare;
				clause.blocks = new Block[]{blk};
				root.add(clause);
			}
			else if ((blk.type == Type.LESSTHAN || blk.type == Type.LTEQUAL || blk.type == Type.GREATER || blk.type == Type.GREQUAL) &&
					(n + 1 < sz && blocks[n + 1].type == Type.NUMBER))
			{
				Clause clause = new Clause();
				//clause.compare = blk.type;
				clause.blocks = new Block[]{blk, blocks[n + 1]};
				root.add(clause);	
				n++;
			}
			// !! this is a "this isn't implemented yet" message
			else throw new FormatException("Unhandled block type for '" + blk.value + "' (type=" + blk.type + ")");
		}
		
		rootClauses = root.toArray(new Clause[root.size()]);
	}
	
	// returns true if the selector is compatible with the text
	public boolean matches(String text)
	{
		if (rootClauses == null) prepare();
	
		if (Util.isBlank(text) || Util.isBlank(selector)) return false;
		
		return recursiveMatch(rootClauses, text, text.toLowerCase());
	}

	// ------------ private methods ------------
	
	// chop up the input string into segments, taking into account whitespace, escaping and quotations
	private void splitSegments()
	{
		List<String> chunks = new ArrayList<>();
		StringBuffer buff = new StringBuffer();
		char[] chars = selector.toCharArray();
		int quote = 0; // 0=none, 1=', 2=", 3=/
		int sz = chars.length;
		for (int n = 0; n < sz; n++)
		{
			char ch = chars[n];
			int chQuote = ch == '\'' ? 1 : ch == '"' ? 2 : ch == '/' ? 3 : 0;
			if (chQuote > 0)
			{
				if (quote == chQuote) 
				{
					buff.append(ch); 
					chunks.add(buff.toString()); 
					buff.delete(0, buff.length()); 
					quote = 0;
				}
				else
				{
					if (buff.length() > 0) {chunks.add(buff.toString()); buff.delete(0, buff.length());}
					buff.append(ch);
					quote = chQuote;
				}
			}
			else if (ch == '\\' && n < sz - 1 && chars[n + 1] == '\\') {buff.append('\\'); n++;}
			else if (ch == '\\' && n < sz - 1 && chars[n + 1] == '\'') {buff.append('\''); n++;}
			else if (ch == '\\' && n < sz - 1 && chars[n + 1] == '\"') {buff.append('"'); n++;}
			else if (quote > 0) buff.append(ch);
			else if (ch <= 32) // whitespace
			{
				if (buff.length() > 0) {chunks.add(buff.toString()); buff.delete(0, buff.length());}
			}
			else
			{
				buff.append(ch);
			}
		}
		if (buff.length() > 0) chunks.add(buff.toString());
		
		blocks = new Block[chunks.size()];
		for (int n = 0; n < chunks.size(); n++)
		{
			blocks[n] = new Block();
			blocks[n].value = chunks.get(n);
		}
	}
	
	private boolean isAlphaNumeric(char ch)
	{
		return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '.';
	}
	
	// go through the blocks and post-process by adding types (everything is "TEXT" by default)
	private void assignBlocks()
	{
		for (int n = 0; n < blocks.length; n++)
		{
			Block blk = blocks[n];
			String val = blk.value;
			Matcher m;
			if (val.length() >= 2 && val.startsWith("'") && val.endsWith("'")) blk.value = val.substring(1, val.length() - 1);
			if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) blk.value = val.substring(1, val.length() - 1);
			if (val.length() >= 2 && val.startsWith("/") && val.endsWith("/")) 
			{
				blk.value = val.substring(1, val.length() - 1);
				blk.type = Type.REGEX;
			}
			else if (val.equals("-") || val.equals("..")) blk.type = Type.RANGE;
			else if (val.equals("+/-")) blk.type = Type.DELTA;
			else if (val.equals("AND") || val.equals("&") || val.equals("&&")) blk.type = Type.AND;
			else if (val.equals("OR") || val.equals("|") || val.equals("||")) blk.type = Type.OR;
			else if (val.equals("NOT") || val.equals("!")) blk.type = Type.NOT;
			else if (val.equals(">")) blk.type = Type.GREATER;
			else if (val.equals(">=")) blk.type = Type.GREQUAL;
			else if (val.equals("<")) blk.type = Type.LESSTHAN;
			else if (val.equals("<=")) blk.type = Type.LTEQUAL;
			else if (val.equals("(") || val.equals("[")) blk.type = Type.OPEN;
			else if (val.equals(")") || val.equals("]")) blk.type = Type.CLOSE;
			else if ((m = PTN_INEQUALITY.matcher(val)).matches())
			{
				blocks = Arrays.copyOf(blocks, blocks.length + 1);
				for (int i = blocks.length - 1; i > n + 1; i--) blocks[i] = blocks[i - 1];
				
				Type ineq = m.group(1).equals("<") ? Type.LESSTHAN :
						    m.group(1).equals(">") ? Type.GREATER :
						    m.group(1).equals("<=") ? Type.LTEQUAL : Type.GREQUAL;
				blocks[n] = new Block(ineq, m.group(1));
				blocks[n + 1] = new Block(Type.NUMBER, m.group(2));
				n++;
			}
			else if ((m = PTN_RANGE.matcher(val)).matches())
			{
				blocks = Arrays.copyOf(blocks, blocks.length + 2);
				for (int i = blocks.length - 1; i > n + 2; i--) blocks[i] = blocks[i - 2];
				blocks[n] = new Block(Type.NUMBER, m.group(1));
				blocks[n + 1] = new Block(Type.RANGE, m.group(2));
				blocks[n + 2] = new Block(Type.NUMBER, m.group(3));
				n += 2;
			}
			else if (PTN_NUMERIC.matcher(val).matches()) blk.type = Type.NUMBER;
			//else if (StringUtils.isNumeric(val)) blk.type = Type.NUMBER;
		}
	}

	// matches top level clauses, and burrows down 	
	private boolean recursiveMatch(Clause[] clauses, String text, String textLC)
	{
		boolean eval = false;
		for (int n = 0; n < clauses.length; n++)
		{
			if (clauses[n].compare == Type.NONE) eval = matchOneClause(clauses[n], text, textLC);
			else if (clauses[n].compare == Type.AND)
			{
				if (!eval) return false; // lazy
				eval = eval && matchOneClause(clauses[n], text, textLC);
			}
			else if (clauses[n].compare == Type.OR)
			{
				if (eval) return true; // lazy
				eval = eval || matchOneClause(clauses[n], text, textLC);
			}
		}
		return eval;
	}
	
	// match either the blocks or the subclauses
	private boolean matchOneClause(Clause clause, String text, String textLC)
	{
		if (clause.subClauses != null) return recursiveMatch(clause.subClauses, text, textLC);
		
		if (clause.blocks.length == 1)
		{
			Block blk = clause.blocks[0];
			if (blk.type == Type.TEXT) return textLC.indexOf(blk.value.toLowerCase()) >= 0;
			if (blk.type == Type.NUMBER)
			{
				try {return Util.dblEqual(blk.numeric, Double.parseDouble(text));}
				catch (NumberFormatException ex) {return false;} // not a number, so can't match
			}
		}
		else if (clause.blocks.length == 2)
		{
			Block blk1 = clause.blocks[0], blk2 = clause.blocks[1];
			try
			{
				if (blk1.type == Type.LESSTHAN && blk2.type == Type.NUMBER) return Double.parseDouble(text) < blk2.numeric;
				if (blk1.type == Type.GREATER && blk2.type == Type.NUMBER) return Double.parseDouble(text) > blk2.numeric;
				if (blk1.type == Type.LTEQUAL && blk2.type == Type.NUMBER) return Double.parseDouble(text) <= blk2.numeric;
				if (blk1.type == Type.GREQUAL && blk2.type == Type.NUMBER) return Double.parseDouble(text) >= blk2.numeric;
			}
			catch (NumberFormatException ex) {return false;} // not a number, so can't match
		}
		else if (clause.blocks.length == 3)
		{
			Block blk1 = clause.blocks[0], blk2 = clause.blocks[1], blk3 = clause.blocks[2];
			if (blk1.type == Type.NUMBER && blk2.type == Type.RANGE && blk3.type == Type.NUMBER)
			{
				try 
				{
					double num = Double.parseDouble(text);
					return num >= blk1.numeric && num <= blk3.numeric;
				}
				catch (NumberFormatException ex) {return false;} // not a number, so can't match			
			}
		}
		
		// report the inability to match
		String descr = "";
		for (Block blk : clause.blocks) descr += (descr.length() == 0 ? "" : ",") + "(" + blk.type + ":" + blk.value + ")";
		throw new FormatException("Unhandled clause: " + descr);
	}
}

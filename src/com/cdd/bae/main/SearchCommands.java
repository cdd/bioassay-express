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

import com.cdd.bae.util.*;
import com.cdd.bao.util.*;

import java.io.*;

import org.apache.commons.lang3.*;

/*
	Command line functionality pertaining to vocabulary: inferring a term hierarchy from schema + ontology.
*/

public class SearchCommands implements Main.ExecuteBase
{
	// ------------ public methods ------------

	public void execute(String[] args) throws IOException
	{
		if (args.length == 0) {printHelp(); return;}
		
		String cmd = args[0];
		String[] options = ArrayUtils.remove(args, 0);

		if (cmd.equals("keyword")) keywordSearch(options);
		else if (cmd.equals("keytests")) prescribedTests();
		else Util.writeln("Unknown command: '" + cmd + "'.");
	}
	
	public void printHelp()
	{
		Util.writeln("Search commands");
		Util.writeln("    find assays based on search patterns");
		Util.writeln("Options:");
		Util.writeln("    keyword: analyze (and execute) a keyword pattern");
		Util.writeln("        -f        apply to full text");
		Util.writeln("        -a        apply to all assignments");
		Util.writeln("        -p {uri}  apply to just indicated property");
		Util.writeln("    keytests: run through some prescribed keyword tests");
		
		Util.writeln();
		Util.writeln("e.g. bae search 'mouse or rat' -f");
	}  
	
	// ------------ private methods ------------
	
	// parses and then optionally executes a keyword search
	private void keywordSearch(String[] options) throws IOException
	{
		String keyword = null;
//		boolean fulltext = false, allassn = false;
//		String propURI = null;
		for (int n = 0; n < options.length; n++)
		{
//			if (options[n].equals("-f")) fulltext = true;
//			else if (options[n].equals("-a")) allassn = true;
//			else if (options[n].equals("-p") && n + 1 < options.length) propURI = options[++n];
			if (options[n].charAt(0) != '-' && keyword == null) keyword = options[n];
			else throw new IOException("Unexpected parameter: " + options[n]);
		}
		if (keyword == null)
		{
			Util.writeln("Keyword is required");
			return;
		}
		
		Util.writeln("Keyword: [" + keyword + "]");
		
		KeywordMatcher km = new KeywordMatcher(keyword);
		KeywordMatcher.Block[] blocks = km.getBlocks();
		Util.writeln("Blocks:");
		for (int n = 0; n < blocks.length; n++) Util.writeln("    [" + blocks[n].value + "] " + blocks[n].type);
		
		try {km.prepare();}
		catch (KeywordMatcher.FormatException ex)
		{
			Util.writeln("Unable to parse keyword specification:\n    " + ex.getMessage());
			return;
		}
		
		Util.writeln("Formulated clauses:");
		for (KeywordMatcher.Clause clause : km.getClauses()) printClause(clause, 1);
	}
	private void printClause(KeywordMatcher.Clause clause, int indent)
	{
		String pfx = Util.rep(' ', indent * 4);
		Util.writeln(pfx + "Clause: compare [" + clause.compare + "]" + (clause.invert ? " NOT" : ""));
		if (clause.blocks != null) for (KeywordMatcher.Block blk : clause.blocks) Util.writeln(pfx + "  [" + blk.value + "] " + blk.type);
		if (clause.subClauses != null) for (KeywordMatcher.Clause sub : clause.subClauses)
		{
			Util.writeln(pfx + "  SubClause:");
			printClause(sub, indent + 1);
		}
	}   

	// format: [pattern, positive match, negative match]
	private static final String[][] KEYWORD_TESTS =
	{
		{"green", "GrEeN eggs", "gr een"},
		{"green OR eggs AND ham", "eggs ham", "brown eggs"},
		{"'green eggs' AND 'ham'", "ham green eggs", "brown eggs"},
		{"10", "10.0", "9.9"},
		{">10", "10.01", "9"},
		{"<=1.5", "1.5", "1.51"},
		{"1E05", "100000", "1.01e05"},
		{"-1", "-1.0", "-1.01"},
		{"-1.5e-01", "-0.15", "fnord"},
		{"1 - 10", "5", "0"},
		{"1..10", "1", "11"},
		{"-5--1", "-3", "-5.1"},
		{"-5..-1", "-1", "-0.9"},
		{"'1E05' OR '>10' OR '-5--1'", "1E05", "1E 05"},
	};
	
	// basically a sanity check with pre-encoded strings to keywordificate
	private void prescribedTests()
	{
		Util.writeln("Prescribed keyword tests:");

		for (int n = 0; n < KEYWORD_TESTS.length; n++)
		{
			String ptn = KEYWORD_TESTS[n][0], pos = KEYWORD_TESTS[n][1], neg = KEYWORD_TESTS[n][2];
		
			Util.writeln("\n[" + (n + 1) + "] : " + ptn);
			KeywordMatcher km = new KeywordMatcher(ptn);

			Util.writeln("  Blocks:");
			for (KeywordMatcher.Block block : km.getBlocks()) Util.writeln("     " + block.type + ": [" + block.value + "]");

			try {km.prepare();}
			catch (KeywordMatcher.FormatException ex) {ex.printStackTrace(); return;}
			
			Util.writeln("  Clauses:");
			for (KeywordMatcher.Clause clause : km.getClauses()) displayClause(clause, 2);
			
			Util.writeln("  Positive: [" + pos + "]");
			if (!km.matches(pos)) {Util.writeln("  ** FAILED TO MATCH"); return;}
			Util.writeln("  Negative: [" + neg + "]");
			if (km.matches(neg)) {Util.writeln("  ** MATCHES ERRONEOUSLY"); return;}
		}
	}
	private void displayClause(KeywordMatcher.Clause clause, int indent)
	{
		String pfx = Util.rep(' ', 2 * indent);
		
		Util.writeln(pfx + "Clause: " + clause.compare + (clause.invert ? " (inverted)" : ""));
		if (clause.blocks != null) for (KeywordMatcher.Block block : clause.blocks)
			Util.writeln(pfx + "  " + block.type + ": [" + block.value + "]");
		if (clause.subClauses != null) for (KeywordMatcher.Clause sub : clause.subClauses) displayClause(sub, indent + 1);
	}
}



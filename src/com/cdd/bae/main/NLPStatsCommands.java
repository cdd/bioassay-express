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
import com.cdd.bae.data.DataObject.*;
import com.cdd.bao.util.*;

import java.io.*;

import org.apache.commons.lang3.*;

import opennlp.tools.chunker.*;
import opennlp.tools.namefind.*;
import opennlp.tools.postag.*;
import opennlp.tools.sentdetect.*;
import opennlp.tools.tokenize.*;
import opennlp.tools.util.*;

/*
	Command line functionality for tallying NLP statistics from assays.
*/

public class NLPStatsCommands implements Main.ExecuteBase
{
	// parts of speech from The Penn Treebank Project
	private enum PosType
	{
		CC(0), // Coordinating
		CD(1), // Cardinal
		DT(2), // Determiner
		EX(3), // Existential
		FW(4), // Foreign
		IN(5), // Preposition
		JJ(6), // Adjective
		JJR(7), // Adjective,
		JJS(8), // Adjective,
		LS(9), // List
		MD(10), // Modal
		NN(11), // Noun,
		NNS(12), // Noun,
		NNP(13), // Proper
		NNPS(14), // Proper
		PDT(15), // Predeterminer
		POS(16), // Possessive
		PRP(17), // Personal
		PRP$(18), // Possessive
		RB(19), // Adverb
		RBR(20), // Adverb,
		RBS(21), // Adverb,
		RP(22), // Particle
		SYM(23), // Symbol
		TO(24), // to
		UH(25), // Interjection
		VB(26), // Verb,
		VBD(27), // Verb,
		VBG(28), // Verb,
		VBN(29), // Verb,
		VBP(30), // Verb,
		VBZ(31), // Verb,
		WDT(32), // Wh­determiner
		WP(33), // Wh­pronoun
		WP$(34), // Possessive
		WRB(35); // Wh­adverb

		private int index;
		
		private PosType(int index)
		{
			this.index = index;
		}

		public int getIndex() {return this.index;}
	}

	@Override
	public boolean needsNLP() {return true;}
	
	public void execute(String[] args) throws IOException
	{
		if (args.length == 0) {printHelp(); return;}
		
		// sanity-check for the existence of the required NLP models
		if (Common.getTokenModel() == null) throw new IOException("Missing NLP model for tokens.");
		if (Common.getPosModel() == null) throw new IOException("Missing NLP model for parts of speech.");
		if (Common.getChunkModel() == null) throw new IOException("Missing NLP model for chunks.");
		if (Common.getLocationModel() == null) throw new IOException("Missing NLP model for locations.");
		if (Common.getPersonModel() == null) throw new IOException("Missing NLP model for persons.");
		if (Common.getOrganizationModel() == null) throw new IOException("Missing NLP model for organizations.");

		String cmd = args[0];
		String[] options = ArrayUtils.remove(args, 0);

		if (cmd.equals("all")) tallyAll(options);
		else if (cmd.equals("pos")) tallyPOS(options);
		else Util.writeln("Unknown command: '" + cmd + "'.");
	}

	public void printHelp()
	{
		Util.writeln("NLP Statistics commands");
		Util.writeln("    tally counts for NLP constituents");
		Util.writeln("Options:");
		Util.writeln("    all: tally counts for all NLP constituents");
		Util.writeln("        -a {assay ID}");
		Util.writeln("        -v {list all text samples for each count}");
		Util.writeln("    pos: tally count for parts of speech (POS)");
		Util.writeln("        -a {assay ID}");
		Util.writeln("        -v {list all text samples for each count}");

		Util.writeln();
		Util.writeln("e.g. bae nlpstats all -a {assayID}");
	}

	// tally #sentences, #chunks, #tokens, #person names, #locations, #organizations
	private void tallyAll(String[] options) throws IOException
	{
		long assayID = 0;
		boolean verbose = false;

		// parse options
		for (int n = 0; n < options.length; n++)
		{
			if (options[n].equals("-a")) assayID = Long.parseLong(options[++n]);
			else if (options[n].equals("-v")) verbose = true;
			else
			{
				Util.writeln("Unexpected parameter: " + options[n]);
				return;
			}
		}
		if (assayID <= 0) throw new IOException("Please specify a valid ID for the assay.");

		// get database and read named assay
		DataStore store = Common.getDataStore();
		Assay assay = store.assay().getAssay(assayID);
		if (assay == null) throw new IOException("Assay with ID " + assayID + " not found.");

		if (StringUtils.length(assay.text) <= 0)
			throw new RuntimeException("Assay with ID " + assayID + " has no associated text.");

		SentenceDetectorME sentDetector = new SentenceDetectorME(Common.getSentenceModel());
		String[] sentences = sentDetector.sentDetect(assay.text);

		TokenizerME tokenizer = new TokenizerME(Common.getTokenModel());
		POSTaggerME tagger = new POSTaggerME(Common.getPosModel());
		ChunkerME chunker = new ChunkerME(Common.getChunkModel());
		NameFinderME locationFinder = new NameFinderME(Common.getLocationModel());
		NameFinderME personFinder = new NameFinderME(Common.getPersonModel());
		NameFinderME orgFinder = new NameFinderME(Common.getOrganizationModel());

		Util.writeln("Sentence count: " + sentences.length);
		for (String curSent : sentences)
		{
			int indent = 4;
			if (verbose)
			{
				Util.writeln(StringUtils.repeat(' ', indent) + curSent);
				indent += 4;
			}

			String[] tokens = tokenizer.tokenize(curSent);
			String[] tags = tagger.tag(tokens);
			String[] chunks = chunker.chunk(tokens, tags);

			Span[] locations = locationFinder.find(tokens);
			Span[] persons = personFinder.find(tokens);
			Span[] organizations = orgFinder.find(tokens);

			Util.writeln(StringUtils.repeat(' ', indent) + "Token count: " + tokens.length);
			if (verbose) for (String curTok : tokens)
				Util.writeln(StringUtils.repeat(' ', indent + 4) + curTok);

			Util.writeln(StringUtils.repeat(' ', indent) + "Chunk count: " + chunks.length);
			if (verbose) for (String curChunk : chunks)
				Util.writeln(StringUtils.repeat(' ', indent + 4) + curChunk);

			Util.writeln(StringUtils.repeat(' ', indent) + "Location count: " + locations.length);
			if (verbose)
			{
				for (String curLoc : Span.spansToStrings(locations, tokens))
					Util.writeln(StringUtils.repeat(' ', indent + 4) + curLoc);
			}

			Util.writeln(StringUtils.repeat(' ', indent) + "Person count: " + persons.length);
			if (verbose)
			{
				for (String curPerson : Span.spansToStrings(persons, tokens))
					Util.writeln(StringUtils.repeat(' ', indent + 4) + curPerson);
			}

			Util.writeln(StringUtils.repeat(' ', indent) + "Organization count: " + organizations.length);
			if (verbose)
			{
				for (String curOrg : Span.spansToStrings(organizations, tokens))
					Util.writeln(StringUtils.repeat(' ', indent + 4) + curOrg);
			}

			Util.writeln("\n");
		}
	}
	
	// tally parts of speech:
	private void tallyPOS(String[] options) throws IOException
	{
		long assayID = 0;
		boolean verbose = false;

		// parse options
		for (int n = 0; n < options.length; n++)
		{
			if (options[n].equals("-a")) assayID = Long.parseLong(options[++n]);
			else if (options[n].equals("-v")) verbose = true;
			else
			{
				Util.writeln("Unexpected parameter: " + options[n]);
				return;
			}
		}
		if (assayID <= 0) throw new IOException("Please specify a valid ID for the assay.");

		// get database and read named assay
		DataStore store = Common.getDataStore();
		Assay assay = store.assay().getAssay(assayID);
		if (assay == null) throw new IOException("Assay with ID " + assayID + " not found.");

		if (StringUtils.length(assay.text) <= 0)
			throw new RuntimeException("Assay with ID " + assayID + " has no associated text.");

		SentenceDetectorME sentDetector = new SentenceDetectorME(Common.getSentenceModel());
		String[] sentences = sentDetector.sentDetect(assay.text);

		TokenizerME tokenizer = new TokenizerME(Common.getTokenModel());
		POSTaggerME tagger = new POSTaggerME(Common.getPosModel());

		// tally of each POS here
		int[] countPOS = new int[PosType.values().length];
		
		Util.writeln("Sentence count: " + sentences.length);
		for (String curSent : sentences)
		{
			int indent = 4;
			if (verbose)
			{
				Util.writeln(StringUtils.repeat(' ', indent) + curSent);
				indent += 4;
			}

			String[] tokens = tokenizer.tokenize(curSent);
			String[] tags = tagger.tag(tokens);
			for (String curTag : tags)
			{
				if (isPunctuation(curTag)) continue;

				try {countPOS[PosType.valueOf(curTag).getIndex()] += 1;}
				catch (IllegalArgumentException iae) {Util.writeln("error processing tag " + curTag + ": " + iae.getMessage());}
			}

			Util.writeln(StringUtils.repeat(' ', indent) + "Token count: " + tokens.length);
			if (verbose)
			{
				for (String curTok : tokens)
					Util.writeln(StringUtils.repeat(' ', indent + 4) + curTok);
				Util.writeln("\n");
			}
		}

		Util.writeln("Summary:");
		for (PosType curPT : PosType.values())
		{
			Util.writeln("\t" + curPT.toString() + ": " + countPOS[curPT.getIndex()]);
		}
	}
	
	// -LRB- is left parenthesis and -RRB- is right parenthesis
	private static String[] punctuation = new String[]{"#", "$", "\"", "-LRB-", "-RRB-", ",", ".", ":", "`", "'"};
	private boolean isPunctuation(String tag)
	{
		return ArrayUtils.indexOf(punctuation, tag) >= 0;
	}
}

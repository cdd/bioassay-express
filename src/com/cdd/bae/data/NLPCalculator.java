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

package com.cdd.bae.data;

import com.cdd.bao.util.*;
import com.google.common.collect.*;

import java.util.*;

import opennlp.tools.chunker.*;
import opennlp.tools.cmdline.parser.*;
import opennlp.tools.parser.*;
import opennlp.tools.postag.*;
import opennlp.tools.sentdetect.*;
import opennlp.tools.tokenize.*;

/*
	Takes a text document and converts it into a list of processed blocks, which are NLP-tagged.
*/

public class NLPCalculator
{
	private String text;

	private static Set<String> blacklistBlocks = new HashSet<>();

	// useless text blocks: they're overly common and just clog up the works, so throw them out
	private static final String[] BLACKLIST_BLOCKS =
	{
		"(-RRB- -RRB-)", "(-LRB- -LRB-)", "(, ,)", "(CC and)", "(DT the)", "(IN of)", "(TO to)", "(VBD were)", "(IN in)",
		"(IN for)", "(DT a)", "(VBZ is)", "(IN at)", "(RB then)", "(VBD was)", "(POS .)", "(: :)", "(IN on)", "(DT each)", "(IN with)", "(IN as)",
		"(IN by)", "(VBP are)", "(IN from)", "(: ;)", "(NN part)", "(VBG using)", "(DT an)", "(CC or)", "(: -)", "(NNP .)", "(IN than)",
		"(NN well)", "(VBN used)", "(WDT which)", "(VB be)", "(ADVP (RB then))", "(SYM =)", "(VBP have)", "(IN into)", "(DT A)", "(NN buffer)",
		"(VBZ has)", "(RB well)", "(NN \")", "(RB only)", "(RB also)", "(IN that)", "(VBN been)", "(DT this)", "(MD will)", "(DT all)",
		"(DT these)", "(RB not)", "(DT no)", "(WRB where)", "(DT both)", "(MD can)", "(JJ such)", "(RB as)", "(# #)", "(IN after)", "(CC but)",
		"(MD would)", "(POS 's)", "(NNP \")", "(WDT that)", "('' ')", "(IN through)", "(PRP$ its)", "(NN /)", "(NNP /)", "(IN without)", "(PRP it)",
		"(JJ same)", "(DT that)", "(RB thus)", "(PRP we)", "(DT any)", "(VB see)", "(VBZ uses)", "(PRP they)", "(VBD did)", "(IN #)", "(IN if)",
		"(RB thus)", "(JJ first)", "(VBN known)", "(IN over)", "(FW i)", "(NNP *)", "(WRB when)", "(EX there)", "(JJ -RRB-)", "(NN type)",
		"(NN -RRB-)", "(JJ many)", "(NNP next)", "(JJ various)", "(PRP it)", "(NP (PRP t))", "(IN below)", "(NN use)", "(NP (DT this))", "(NN x)",
		"(VBD had)", "(VBN found)", "(PRP I)", "(POS ')", "(POS ')", "(DT 2.)", "(RB therefore)", "(RB however)", "(RB either)", "(IN i)",
		"(JJ \")", "(RB once)", "(, .)", "(RB here)", "(VBN done)", "(NNP +)", "(IN within)", "(NP (NNP \"))", "(NN +)", "(IN \")", "(JJ ')",
		"(NNS \")", "(VB get)", "(, cat#)", "(SYM [)", "(SYM ])", "(VBZ does)", "(VBD \")", "(VBD .)", "(DT every)", "(JJ <)", "(JJ >)", "(JJ ml)",
		"(VBD /)", "(VB use)", "(IN across)", "(NP (DT that))", "(JJ ~)", "(NN no)", "(VBG .)", "(VBZ 3.)", "(MD must)", "(VBZ remains)", "(NN |)",
		"(JJ certain)", "(IN however)", "(IN although)", "(NP (. .))", "(NN [)", "(NN ])", "(IN while)", "(RB unfortunately)", "(IN whereas)",
		"(VBG having)", "(IN >)", "(IN unlike)", "(IN because)", "(VBD 2.)", "(VBG giving)", "(IN until)", "(RB now)", "(IN *)", "(VBD +)",
		"(VBZ 2.)", "(RB later)", "(RB first)", "(JJ /)", "(IN /)", "(PRP itself)", "(, \")", "(NN *)", "(JJ *)", "(VBD *)", "(DT \")", "(IN @)",
		"(NN #)", "(IN +)", "('' \")", "(JJ +)", "(RP on)", "(NNS *)", "(NN are)", "(DT |)", "(NN [)", "(NN ])", "(RB \")", "(VBN \")", "(VBP \")",
		"(VBD @)", "(: ...)", "(RB /)", "(JJ [)", "(JJ ])", "(NN had)", "(WP who)", "(NN etc)", "(DT which)", "(NN and/or)", "(DT .)", "(NN @)",
		"(JJ @)", "(NNP [)", "(NNP ])", "(NNP :)", "(NNP @)", "(NNP \\)", "(IN [)", "(NN ,)", "(NN _)", "(NNP ~)", "(VBP /)", "(IN be)", "(VBZ *)",
		"(RB So)"	
	};
	
	// ne'er do well prefixes that should always be left out
	private static final String[] BLACKLIST_PREFIXES =
	{
		"(CD ",	// cardinal numbers
		"(NP (CD ", // ditto
		"(LS ", // list item marker
		"(LST (LS ", // ditto
		"('' ", // whatever this means
		"(, " // ditto
	};

	// lifetime setup
	static
	{
		for (String block : BLACKLIST_BLOCKS) blacklistBlocks.add(block.toLowerCase());
	}
	
	// cache most recently calculated text snippets (because repeated submission is commonfold)
	private static final class Cache
	{
		String text;
		String[] blocks;
	}
	private static List<Cache> cache = new ArrayList<>();
	private static final int CACHE_SIZE = 10;

	// ------------ public methods ------------

	public NLPCalculator(String text)
	{
		this.text = text;
	}
	
	// turns an English-text string into an array of unique NLP tagged blocks, restricting the list to those that have passed the "approval" metrics
	public String[] calculate()
	{
		if (Util.isBlank(text)) return new String[0];
	
		synchronized (cache)
		{
			for (int n = 0; n < cache.size(); n++)
			{
				Cache item = cache.get(n);
				if (item.text.equals(text))
				{
					if (n < cache.size() - 1)
					{
						cache.remove(n);
						cache.add(item);
					}
					return item.blocks;
				}
			}
		}	
	
		SentenceDetectorME detector = new SentenceDetectorME(Common.getSentenceModel());
		Tokenizer tokener = new TokenizerME(Common.getTokenModel());
		POSTaggerME tagger = new POSTaggerME(Common.getPosModel());
		ChunkerME chunker = new ChunkerME(Common.getChunkModel());

		String[] sentences = detector.sentDetect(text);
		List<String[]> tokens = new ArrayList<>();
		List<String[]> postags = new ArrayList<>();
		List<String[]> chunks = new ArrayList<>();
		Set<String> accum = new HashSet<>();
		
		for (String sentence : sentences)
		{
			List<String> sentenceChunks = new ArrayList<>();
			sentenceChunks.add(sentence);
			
			String[] words = sentence.split(" ");
			if (words.length > 100)
			{
				sentenceChunks.clear();
				List<String> listToBeSplit = Arrays.asList(words);
				for (List<String> chunk : Lists.partition(listToBeSplit, 100))
					sentenceChunks.add(String.join(" ", chunk));
			}
			
			for (String s : sentenceChunks)
			{
				String[] t = tokener.tokenize(s);
				tokens.add(t);
				String[] p = tagger.tag(t);
				postags.add(p);
				String[] c = chunker.chunk(t, p);
				chunks.add(c);

				Parser parser = ParserFactory.create(Common.getParserModel());
				((opennlp.tools.parser.chunking.Parser)parser).setErrorReporting(false);
				try
				{
					Parse prs = ParserTool.parseLine(Util.join(t, " "), parser, 1)[0]; // (only asked for 1)
					recursiveGrabBlocks(accum, prs);
				}
				catch (NullPointerException ex)
				{
					Util.writeln("Parsing failed for sentence: [" + s + "]");
					throw ex;
				}
			}
		}
		
		String[] blocks = accum.toArray(new String[accum.size()]);
		synchronized (cache)
		{
			Cache item = new Cache();
			item.text = text;
			item.blocks = blocks;
			cache.add(item);
			while (cache.size() > CACHE_SIZE) cache.remove(0);
		}
		return blocks;
	}
	

	// ------------ private methods ------------

	
	// follows the parse object's hierarchy, and collect it up in a set
	private void recursiveGrabBlocks(Set<String> accum, Parse prs)
	{
		for (Parse kid : prs.getChildren()) recursiveGrabBlocks(accum, kid);
		
		String t = prs.getType();
		
		final String[] SKIP_LIST = {"TOP", "S", ".", AbstractBottomUpParser.TOK_NODE};
		for (int n = 0; n < SKIP_LIST.length; n++) if (t.equals(SKIP_LIST[n])) return;

		StringBuffer sb = new StringBuffer();
		prs.show(sb);
		String block = sb.toString();
		if (approveBlock(block)) accum.add(block);
	}
	
	private static final int MAX_TEXT_LENGTH = 200; // throw out blocks longer than this

	// apply criteria for whether to include a block: not too long & not blacklisted
	private boolean approveBlock(String block)
	{
		if (block.length() > MAX_TEXT_LENGTH) return false;
		if (blacklistBlocks.contains(block.toLowerCase())) return false;
		for (String pfx : BLACKLIST_PREFIXES) if (block.startsWith(pfx)) return false;
		return true;
	}

}

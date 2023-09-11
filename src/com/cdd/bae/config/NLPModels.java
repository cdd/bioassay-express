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

package com.cdd.bae.config;

import com.cdd.bae.util.*;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import org.apache.commons.lang3.*;
import org.slf4j.*;

import opennlp.tools.chunker.*;
import opennlp.tools.namefind.*;
import opennlp.tools.parser.*;
import opennlp.tools.postag.*;
import opennlp.tools.sentdetect.*;
import opennlp.tools.tokenize.*;

/*
	Handles loading & serving of natural language processing modules, which leverages the OpenNLP library. The
	library needs to be bootstrapped with a number of dictionaries for its tokenising work. Loading these is slow
	enough that it is undesirable to have this delay the boot process (esp. for command line use).
*/

public class NLPModels
{
	private static final Logger logger = LoggerFactory.getLogger(NLPModels.class);

	public SentenceModel sentenceModel = null;
	public TokenizerModel tokenModel = null;
	public POSModel posModel = null;
	public ChunkerModel chunkModel = null;
	public ParserModel parserModel = null;
	public TokenNameFinderModel locationModel = null;
	public TokenNameFinderModel personModel = null;
	public TokenNameFinderModel organizationModel = null;

	private FileWatcher watcher;

	private String[] names = new String[]
	{
		"en-sent.bin", "en-token.bin", "en-pos-maxent.bin", "en-chunker.bin", "en-parser-chunking.bin"
	};

	// names of optional NLP models
	private String[] optNames = new String[]
	{
		"en-ner-location.bin", "en-ner-person.bin", "en-ner-organization.bin"
	};

	// ------------ public methods ------------

	public NLPModels(String dirname) throws IOException
	{
		super();

		// tack names of optional models to names array housing required models
		names = ArrayUtils.addAll(names, optNames);
		
		this.watcher = new FileWatcher(getFilesByName(dirname));

		List<IOException> failed = Arrays.stream(names).parallel().map(this::loadModel).filter(Objects::nonNull).collect(Collectors.toList());
		if (!failed.isEmpty()) throw new IOException("Failed to load NLP model", failed.get(0));
	}

	public boolean hasChanged()
	{
		return watcher.hasChanged();
	}

	public void setDirectory(String dirname)
	{
		this.watcher.watchFiles(getFilesByName(dirname));
	}

	public void load() throws IOException
	{
		this.load(names);
		watcher.reset();
	}

	public void reload()
	{
		SentenceModel oldSentenceModel = this.sentenceModel;
		TokenizerModel oldTokenModel = this.tokenModel;
		POSModel oldPosModel = this.posModel;
		ChunkerModel oldChunkModel = this.chunkModel;
		ParserModel oldParserModel = this.parserModel;
		
		TokenNameFinderModel oldLocationModel = this.locationModel;
		TokenNameFinderModel oldPersonModel = this.personModel;
		TokenNameFinderModel oldOrganizationModel = this.organizationModel;
		try
		{
			this.load(watcher.getChangedLabels().toArray(new String[0]));
		}
		catch (IOException e)
		{
			this.sentenceModel = oldSentenceModel;
			this.tokenModel = oldTokenModel;
			this.posModel = oldPosModel;
			this.chunkModel = oldChunkModel;
			this.parserModel = oldParserModel;
			this.locationModel = oldLocationModel;
			this.personModel = oldPersonModel;
			this.organizationModel = oldOrganizationModel;
		}
	}

	public void load(String[] labels) throws IOException
	{
		for (String label : labels)
		{
			IOException e = loadModel(label);
			if (e != null) throw e;
		}
	}
	
	// ------------ private methods ------------

	private Map<String, File> getFilesByName(String dirname)
	{
		Map<String, File> filesByName = new HashMap<>();
		
		File path = new File(dirname);
		for (String name : names) filesByName.put(name, new File(path, name));
		return filesByName;
	}

	public IOException loadModel(String label)
	{
		logger.info("Loading NLP model {}", label);
		try
		{
			LogTimer timer = new LogTimer(logger);
			File file = watcher.getFile(label);
			if (label.equals("en-sent.bin")) this.sentenceModel = new SentenceModel(file);
			else if (label.equals("en-token.bin")) this.tokenModel = new TokenizerModel(file);
			else if (label.equals("en-pos-maxent.bin")) this.posModel = new POSModel(file);
			else if (label.equals("en-chunker.bin")) this.chunkModel = new ChunkerModel(file);
			else if (label.equals("en-parser-chunking.bin")) this.parserModel = new ParserModel(file);
			else if (label.equals("en-ner-location.bin")) this.locationModel = new TokenNameFinderModel(file);
			else if (label.equals("en-ner-person.bin")) this.personModel = new TokenNameFinderModel(file);
			else if (label.equals("en-ner-organization.bin")) this.organizationModel = new TokenNameFinderModel(file);

			timer.report("NLP model {} loaded, time required: {}", label);
			return null;
		}
		catch (IOException e)
		{
			logger.error("Failure loading NLP model {}", label);

			// do not throw exception for optional models
			if (ArrayUtils.contains(optNames, label)) return null;

			return new IOException("Failure loading NLP file " + label);
		}
	}
}

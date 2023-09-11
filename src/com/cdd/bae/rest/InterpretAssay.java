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

package com.cdd.bae.rest;

import com.cdd.bae.config.*;
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import org.apache.commons.lang3.*;
import org.apache.poi.*;
import org.apache.poi.extractor.*;
import org.json.*;

/*
	Analyzes incoming assay data, which may be a blob of text or a base64-encoded binary file. Makes its best effort to turn this into a
	partially filled out assay datastructure.
	
	Parameters:
		uniqueID: (optional) user-provided identifier, if known
		schemaURI: (optional) user-provided template, if known
		format: (optional) MIME format of content; text/plain or application/octet-stream will be interpreted as open-ended
		filename: (optional) name of file, if a file is where the content originated from
		text: payload
		base64: ditto
		
	Return:
		assay: standard assay format, provided upon success 
		error: short descriptive message if something went wrong
*/

public class InterpretAssay extends RESTBaseServlet 
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		String uniqueID = input.optString("uniqueID", null);
		String schemaURI = input.optString("schemaURI", null);
		String format = input.optString("format", null);
		String filename = input.optString("filename", null);
		
		String text = input.optString("text"), base64 = input.optString("base64");
		if (Util.isBlank(text) && Util.isBlank(base64)) throw new RESTException("Must provide either 'text' or 'base64'.", RESTException.HTTPStatus.BAD_REQUEST);

		JSONObject result = new JSONObject();
		Assay assay = null;
		try
		{
			// unpack an assay by whatever means available; thrown exceptions should contain a message that is suitable for display
			// to the user
			if (Util.notBlank(text))
				assay = parseText(uniqueID, schemaURI, format, filename, text);
			else
				assay = parseBase64(uniqueID, schemaURI, format, filename, base64);

			if (Util.notBlank(filename)) parseFilename(assay, filename);				
		}
		catch (Exception ex)
		{
			result.put("error", ex.getMessage());
			ex.printStackTrace();
		}
		
		result.put("assay", AssayJSON.serialiseAssay(assay));
		
		return result;
	}

	// ------------ private methods ------------
	
	// derive an assay from text; the fallback is just an assay with text and nothing else
	protected Assay parseText(String uniqueID, String schemaURI, String mimeType, String filename, String text) throws Exception
	{
		// TODO: if mimeType or filename are provided, take this into account when trying to decide what kind of content this is
	
		// try assay-formatted JSON first; silently fail & continue
		try
		{
			JSONObject json = new JSONObject(new JSONTokener(text));
			Assay assay = AssayJSON.deserialiseAssay(json);
			if (assay != null) 
			{
				if (uniqueID != null) assay.uniqueID = uniqueID;
				if (schemaURI != null) assay.schemaURI = schemaURI;
				return assay;
			}
		}
		catch (Exception ex) {}
		
		return parseArbitrary(uniqueID, schemaURI, mimeType, filename, text.getBytes(Util.UTF8));
	}

	// derive an assay from binary
	protected Assay parseBase64(String uniqueID, String schemaURI, String format, String filename, String src) throws Exception
	{
		return parseArbitrary(uniqueID, schemaURI, format, filename, Base64.getDecoder().decode(src));
	}
	
	// parses an arbitrary blob of something, by whatever means necessary
	protected Assay parseArbitrary(String uniqueID, String schemaURI, String mimeType, String filename, byte[] bytes) throws Exception
	{
		// TODO: if mimeType or filename are provided, take this into account when trying to decide what kind of content this is; can use 
		// mimeType/file extension to limit the formats that we consider viable (e.g. only try to unpack MSOffice if it's .doc/.docx/etc)
		Assay assay = new Assay();
		assay.uniqueID = uniqueID;
		assay.schemaURI = schemaURI;

		// see if it's an MSOffice format of some kind (defer to Apache POI to do all the hard work)
		try (InputStream stream = new ByteArrayInputStream(bytes))
		{
			System.setProperty("org.apache.poi.util.POILogger", "org.apache.commons.logging.impl.NoOpLog");
			POITextExtractor extractor = ExtractorFactory.createExtractor(stream);
			assay.text = extractor.getText();
			return assay;
		}
		catch (Exception ex) {logger.debug("Not in MSOffice format", ex);}

		// (test for other binary formats here)

		// try to make it into a regular text stream
		String str = new String(bytes);
		int nchars = 0;
		for (int n = str.length() - 1; n >= 0; n--)
		{
			char ch = str.charAt(n);
			if (Character.isLetterOrDigit(ch) || ch == '\n' || ch == '\r' || ch == '\t' || ch == ' ') nchars++;
		}

		// at this point, the text is parsed correctly; it may be plain text, or some other text-derived format
		try
		{	
			JSONObject json = new JSONObject(new JSONTokener(str));
			Assay parsed = AssayJSON.deserialiseAssay(json);
			if (assay.uniqueID != null) parsed.uniqueID = assay.uniqueID;
			if (assay.schemaURI != null) parsed.schemaURI = assay.schemaURI;
			if (parsed != null) return parsed;
		}
		catch (JSONException ex) {}
			
		// TODO: interject other text-based formats, like HTML
		
		// require that most of the characters are letter/digit-like; this is suggestive of "plain text"
		if (nchars > 0.9 * str.length())
		{
			assay.text = str;
			return assay;
		}

		throw new IOException("Format undetected.");
	}
	
	// the filename often holds a lot of clues about the content: it can usually be used as the title; it often furnishes the assay
	// identifier, which can in turn lead to a default schema, which can subsequently trigger a detailed analysis of content for text mining
	private void parseFilename(Assay assay, String filename)
	{
		int idx = filename.lastIndexOf('.'); // note that if the file has no suffix but does have a "." in the regular name, this isn't so nice
		String title = idx < 0 ? filename : filename.substring(0, idx);
		String[] bits = title.trim().split("[ _]+"); // space and underscore are treated as equivalent
		Identifier ident = Common.getIdentifier();

		// see if we can pull out an identifier
		if (Util.isBlank(assay.uniqueID))
		{
			outer: for (Identifier.Source src : ident.getSources()) if (Util.notBlank(src.recogRegex))
			{
				Pattern ptn = Pattern.compile(src.recogRegex);
				for (int n = 0; n < bits.length; n++)
				{
					if (ptn.matcher(bits[n]).matches())
					{
						assay.uniqueID = Identifier.makeKey(src, bits[n]);
						bits = ArrayUtils.remove(bits, n);
						break outer;
					}
					// TODO: the clause below extends the parsing to include bracketed ID numbers, but there are plenty of other
					// permutations that could also be recognised; may want to enumerate a list of cases
					if (bits[n].startsWith("(") && bits[n].endsWith(")"))
					{
						String sub = bits[n].substring(1, bits[n].length() - 1).trim();
						if (ptn.matcher(sub).matches())
						{
							assay.uniqueID = Identifier.makeKey(src, sub);
							bits = ArrayUtils.remove(bits, n);
							break outer;
						}
					}
				}
			}
		}
		
		// if have an ID but no schema, see if there's a default
		if (Util.isBlank(assay.schemaURI) && Util.notBlank(assay.uniqueID))
		{
			Identifier.UID uid = ident.parseKey(assay.uniqueID);
			if (uid != null && uid.source.defaultSchema != null) assay.schemaURI = uid.source.defaultSchema;
		}
		
		// assuming there's content remaining, add an annotation; note that we're assuming that the template (known or otherwise)
		// has a title field; if it does not, then the annotation will be an orphan
		if (bits.length > 0 && assay.schemaURI != null)
		{
			String propURI = ModelSchema.expandPrefix("bao:BAO_0002853");
			Schema schema = Common.getSchema(assay.schemaURI);
			if (schema != null) for (Schema.Assignment assn : schema.getRoot().flattenedAssignments()) if (assn.propURI.equals(propURI)) 
			{
				TextLabel label = new TextLabel();
				label.propURI = propURI;
				label.groupNest = assn.groupNest();
				label.text = String.join(" ", bits);
				assay.textLabels = ArrayUtils.add(assay.textLabels, label);
				break;
			}
		}
	}
}

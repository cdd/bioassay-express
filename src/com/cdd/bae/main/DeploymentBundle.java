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

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.tasks.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import org.apache.commons.io.*;
import org.apache.commons.lang3.*;
import org.json.*;

/*
	Deployment: takes an existing packaged WAR file and makes a modified version that contains configuration
	information embedded within it. This allows the deliverable to be run without the server having to have
	its own /opt/bae directory, so that WAR file can be just slotted in.
*/

public class DeploymentBundle implements Main.ExecuteBase
{
	private static final String OPTDIR = "opt";
	
	@Override
	public boolean needsConfig() {return false;}
	
	public DeploymentBundle()
	{
	}

	public void execute(String[] options)
	{
		if (options.length < 3)
		{
			printHelp();
			return;
		}
		
		File infile = new File(options[0]), outfile = new File(options[1]), cfgdir = new File(options[2]);
		
		try
		{
			performDeploy(infile, outfile, cfgdir);
			
			Util.writeln("Done.");
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	public void printHelp()
	{
		Util.writeln("Deployment bundle commands");
		Util.writeln("    generating WAR files with embedded configuration");
		Util.writeln("Options:");
		Util.writeln("    {in} {out} {cfg}");
		Util.writeln("        where {in} is a basic .war file without extra configuration");
		Util.writeln("              {out} is the output .war file with embedded configuration");
		Util.writeln("              {cfg} is the directory for the configuration info (e.g. /opt/bae)");
	}

	// ------------ private methods ------------
	
	private void performDeploy(File infile, File outfile, File cfgdir) throws IOException
	{
		if (!infile.exists() || !infile.isFile()) throw new IOException("Invalid input file: " + infile);
		if (infile.getCanonicalPath().equals(outfile.getCanonicalPath())) throw new IOException("In/out file must not be the same.");
		if (!cfgdir.exists() || !cfgdir.isDirectory()) throw new IOException("Invalid configuration directory: " + cfgdir);

		Util.writeln("Input file:  " + infile.getCanonicalPath());
		Util.writeln("Output file: " + outfile.getCanonicalPath());
		Util.writeln("Config dir:  " + cfgdir.getCanonicalPath());
		
		File pkfile = new File(cfgdir.getPath() + "/packwar.json");
		if (!pkfile.exists()) throw new IOException("Packing file not found: " + pkfile);
		JSONObject packwar = null;
		try (Reader rdr = new BufferedReader(new FileReader(pkfile)))
		{
			packwar = new JSONObject(new JSONTokener(rdr));
		}
		String[] packFiles = packwar.getJSONArray("files").toStringArray();
		String[] packDirs = packwar.getJSONArray("dirs").toStringArray();

		JSONObject packInline = packwar.getJSONObject("inlineFiles");
		
		try (ZipOutputStream outzip = new ZipOutputStream(new FileOutputStream(outfile)))
		{
			Util.writeln("Spooling source bundle");
			try (ZipInputStream inzip = new ZipInputStream(new FileInputStream(infile)))
			{
				spoolSourceBundle(inzip, outzip);
			}

			Util.writeln("Packing configuration content");
			for (String fn : packFiles) fileGrab(new File(cfgdir + "/" + fn), cfgdir, outzip);
			for (String dir : packDirs) recursivelyGrab(new File(cfgdir + "/" + dir), cfgdir, outzip);
			
			for (String fn : packInline.keySet())
			{
				String[] lines = packInline.getJSONArray(fn).toStringArray();
				byte[] bytes = String.join("\n", lines).getBytes();
				outzip.putNextEntry(new ZipEntry(OPTDIR + "/" + fn));
				outzip.write(bytes);
			}
		}
	}

	// stream through everything in the original, mostly blasting it out without modification
	private void spoolSourceBundle(ZipInputStream inzip, ZipOutputStream outzip) throws IOException
	{		
		Set<String> dedup = new HashSet<>();
		ZipEntry inze = inzip.getNextEntry();
		while (inze != null)
		{
			String path = inze.getName();
			
			if (dedup.contains(path)) {}
			else
			{
				outzip.putNextEntry(new ZipEntry(path));

				if (path.equals("WEB-INF/web.xml"))
				{
					try
					{
						rewriteWebXML(inzip, outzip);
					}
					catch (IOException ex) {throw ex;}
					catch (Exception ex) {throw new IOException(ex);}
				}
				else
				{
					IOUtils.copy(inzip, outzip);
				}
				
				outzip.closeEntry();
				dedup.add(path);
			}

			inzip.closeEntry();
			inze = inzip.getNextEntry();
		}
	}
	
	// reads in the current web.xml definition and modifies it 
	private void rewriteWebXML(InputStream istr, OutputStream ostr) throws Exception
	{
		// read it in
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		builder.setEntityResolver(new EntityResolver()
		{
			public InputSource resolveEntity(String publicID, String systemID) throws SAXException, IOException
			{
				return new InputSource(new StringReader(""));
			}
		});
		String buff = Util.streamToString(istr); // have to buffer to prevent stream closing
		Document doc = builder.parse(new InputSource(new StringReader(buff)));

		// modify the configuration file location
		for (Element elParent : childElements(doc.getDocumentElement()))
		{
			if (!elParent.getTagName().equals("context-param")) continue;
			Element elName = null, elValue = null;
			for (Element el : childElements(elParent))
			{
				if (el.getTagName().equals("param-name")) elName = el;
				else if (el.getTagName().equals("param-value")) elValue = el;
			}
			if (elName != null && elValue != null && elName.getTextContent().equals("config-file"))
			{
				elValue.setTextContent("$BUNDLE/" + OPTDIR + "/config.json");
			}
		}

		// write it out
		Transformer xformer = TransformerFactory.newInstance().newTransformer();
		xformer.setOutputProperty("media-type", "text/xml");
		xformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		ByteArrayOutputStream bstr = new ByteArrayOutputStream();
		xformer.transform(new DOMSource(doc.getDocumentElement()), new StreamResult(bstr));
		ostr.write(bstr.toString().getBytes());
	}
	
	private List<Element> childElements(Element parent)
	{
		List<Element> list = new ArrayList<>();
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
		{
			if (node.getNodeType() == Node.ELEMENT_NODE) list.add((Element)node);
		}
		return list;
	}

	private void fileGrab(File file, File baseDir, ZipOutputStream outzip) throws IOException
	{
		String path = file.getPath().substring(baseDir.getPath().length());
		if (path.startsWith("/")) path = path.substring(1);
		path = OPTDIR + "/" + path;
	
		outzip.putNextEntry(new ZipEntry(path));

		try (InputStream istr = new FileInputStream(file))
		{
			IOUtils.copy(istr, outzip);
		}
				
		outzip.closeEntry();
	}

	private void recursivelyGrab(File dir, File baseDir, ZipOutputStream outzip) throws IOException
	{
		directoryEnsure(dir, baseDir, outzip);
	
		for (File f : dir.listFiles())
		{
			if (f.getName().startsWith(".")) {}
			else if (f.isFile()) fileGrab(f, baseDir, outzip);
			else if (f.isDirectory()) recursivelyGrab(f, baseDir, outzip);
		}
	}
	
	private void directoryEnsure(File dir, File baseDir, ZipOutputStream outzip) throws IOException
	{
		String path = dir.getPath().substring(baseDir.getPath().length());
		if (path.startsWith("/")) path = path.substring(1);
		path = OPTDIR + "/" + path + "/"; 
		outzip.putNextEntry(new ZipEntry(path));
	}

}

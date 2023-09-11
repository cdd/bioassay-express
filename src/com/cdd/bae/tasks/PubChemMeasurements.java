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

package com.cdd.bae.tasks;

import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import javax.json.*;
import javax.json.stream.*;

import org.slf4j.*;
import org.json.*;

/*
	Takes a given PubChem AID, and hunts down the corresponding measurements by making use of the PUG REST API.
*/

public class PubChemMeasurements
{
	private static final Logger logger = LoggerFactory.getLogger(PubChemMeasurements.class);
	private long assayID;
	private int aid;
	
	// schema for PubChem can be found at: ftp://ftp.ncbi.nih.gov/pubchem/specifications/pubchem.xsd

	// PubChem result types
	public enum Type
	{
		FLOAT(1, "float"),
		INT(2, "int"),
		BOOL(3, "bool"),
		STRING(4, "string");
		
		public final int pubchemID; // PubChem-specific ID
		public final String shortCode; // PubChem-specific string
		
		private Type(int pubchemUnit, String shortCode)
		{
			this.pubchemID = pubchemUnit;
			this.shortCode = shortCode;
		}

		@Override
		public String toString()
		{
			return this.shortCode;
		}

		// creates a new unit by looking up the "short code", which is what is found in the PubChem origination; or, sometimes the
		// numeric version is used instead (not sure why the discrepancy), which is also handled
		public static Type fromShortCode(String shortCode)
		{
			for (Type type : Type.values()) if (shortCode.equals(type.shortCode) || shortCode.equals(String.valueOf(type.pubchemID))) return type;
			return null;
		}
	}

	// PubChem unit types
	// see https://www.ncbi.nlm.nih.gov/IEB/ToolBox/CPP_DOC/asn_spec/PC-ResultType.html
	public enum Units
	{
		PPT(1, "ppt", "ppt"), 
		PPM(2, "ppm", "ppm"), 
		PPB(3, "ppb", "ppb"),
		MILLIMOLAR(4, "mm", "mM"), 
		MICROMOLAR(5, "um", "uM"), 
		NANOMOLAR(6, "nm", "nM"), 
		PICOMOLAR(7, "pm", "pM"), 
		FEMTOMOLAR(8, "fm", "fM"),
		MG_PER_ML(9, "mgml", "mg/mL"), 
		UG_PER_ML(10, "ugml", "ug/mL"),
		NG_PER_ML(11, "ngml", "ng/mL"),
		PG_PER_ML(12, "pgml", "pg/mL"),
		FG_PER_ML(13, "fgml", "fg/mL"),
		MOLAR(14, "m", "M"),
		PERCENT(15, "percent", "%"),
		RATIO(16, "ratio", ""),
		SEC(17, "sec", "sec"),
		RSEC(18, "rsec", "rsec"),
		MIN(19, "min", "min"),
		RMIN(20, "rmin", "per min"),
		DAY(21, "day", "day"),
		RDAY(22, "rday", "per day"),
		ML_MIN_KG(23, "ml-min-kg", "mL/min/kg"),
		L_PER_KG(24, "l-kg", "L/kg"),
		HR_NG_ML(25, "hr-ng-ml", "hour.ng/mL"),
		CM_SEC(26, "cm-sec", "cm/sec"),
		MG_PER_KG(27, "mg-kg", "mg/kg"),
		NONE(254, "none", ""),
		UNSPECIFIED(255, "unspecified", "?");

		public final int pubchemID; // PubChem-specific ID
		public final String shortCode; // PubChem-specific string
		public final String representation; // human-readable equivalent

		private Units(int pubchemUnit, String shortCode, String representation)
		{
			this.pubchemID = pubchemUnit;
			this.shortCode = shortCode;
			this.representation = representation;
		}

		@Override
		public String toString()
		{
			return this.representation;
		}

		// converts a PubChem units type to a common representation
		public static String unitsToString(int pubchemUnit)
		{
			for (Units unit : Units.values()) if (unit.pubchemID == pubchemUnit) return unit.toString();
			return UNSPECIFIED.toString();
		}
		
		// creates a new unit by looking up the "short code", which is what is found in the PubChem origination
		public static Units fromShortCode(String shortCode)
		{
			for (Units unit : Units.values()) if (shortCode.equals(unit.shortCode) || shortCode.equals(String.valueOf(unit.pubchemID))) return unit;
			return null;
		}
	}

	// PubChem outcome types
	public enum Outcome
	{
		INACTIVE(1, "inactive"),
		ACTIVE(2, "active"),
		INCONCLUSIVE(3, "inconclusive"),
		UNSPECIFIED(4, "unspecified"),
		PROBE(4, "probe");
		
		public final int pubchemID; // PubChem-specific ID
		public final String shortCode; // PubChem-specific string
		
		private Outcome(int pubchemUnit, String shortCode)
		{
			this.pubchemID = pubchemUnit;
			this.shortCode = shortCode;
		}

		@Override
		public String toString()
		{
			return this.shortCode;
		}

		// creates a new unit by looking up the "short code", which is what is found in the PubChem origination
		public static Outcome fromShortCode(String shortCode)
		{
			for (Outcome outcome : Outcome.values()) if (shortCode.equals(outcome.shortCode) || shortCode.equals(String.valueOf(outcome.pubchemID))) return outcome;
			return null;
		}
	}	
	
	// encapsulation of the PubChem measurements

	public static final class Column
	{
		public String name;
		public String descr;
		public Type type;
		public Units units;
		public boolean activeColumn; // the "ac" field
	}
	private Column[] columns = null;
	
	public static final class Datum
	{
		// value is used to hold float/int/boolean values, while string is a fallback for non-numeric
		public Double value;
		public String str;
	}
	public static final class Row
	{
		public int sid;
		public Outcome outcome;
		public Datum[] data;
	}
	private List<Row> rows = new ArrayList<>();
	
	// ------------ public methods ------------

	// takes a stream that is expected to be JSON formatted and unpacks the content; this avoids the overhead of creating
	// the JSON object, but the measurement column/row data still needs to be held in memory
	public PubChemMeasurements(InputStream istr) throws IOException
	{
		JsonParser parser = Json.createParser(new BufferedReader(new InputStreamReader(istr)));		
		
		if (parser.next() != JsonParser.Event.START_OBJECT) throw new JSONException("Parameter must be a JSON object.");
		scanObject(parser, new Stack<>());
		//parser.close();

		//if (parser.next() != JsonParser.Event.START_OBJECT) throw new JSONException("Parameter must be a JSON object.");
		//JSONObject json = assembleObject(parser);
		//Util.writeln("OBJECT:\n"+json.toString(2));
	}

	// DEPRECATED: not scalable for gigantic objects
	// given that the JSON object is already available, pull out the measurements
	public PubChemMeasurements(JSONObject json) throws IOException
	{
		JSONObject root = json.getJSONObject("PC_AssaySubmit");
		JSONObject descr = root.getJSONObject("assay").getJSONObject("descr");
		
		parseColumns(descr.optJSONArray("results"));

		JSONArray rows = root.getJSONArray("data");
		for (JSONObject row : root.getJSONArray("data").toObjectArray()) parseRow(row);
	}

	// DEPRECATED: uses the PubChem API to fetch the actual content
	public PubChemMeasurements(long assayID, int aid)
	{
		this.assayID = assayID;
		this.aid = aid;
	}

	// DEPRECATED: uses the PubChem API to fetch the actual content
	public void download() throws IOException
	{
		download(1000);
	}
	public void download(int idealBlkSz) throws IOException
	{	
		rows.clear();
		int[] sidlist = fetchSubstances();
		final int sz = sidlist.length;
		
		logger.info("# of molecules = {}", sz);
		if (sz == 0) return;
		
		final int nblks = Util.iceil(sz / (float)idealBlkSz);
		final int blksz = sz / nblks;
		for (int i = 0; i < sz; i += blksz)
		{
			logger.info("fetching block starting at #{}", i + 1);
			
			boolean nullFail = false;
			try
			{
				fetchBlock(i == 0, Arrays.copyOfRange(sidlist, i, Math.min(sz, i + blksz)));
			}
			catch (NullPointerException ex) 
			{
				if (idealBlkSz < 10) throw ex; // give up
				nullFail = true;
			}
			
			// progressive failure: keep restarting 
			if (nullFail)
			{
				int newBlkSz = idealBlkSz / 2;
				logger.info("service failed; reducing ideal block size from {} to {}", idealBlkSz, newBlkSz);
				rows.clear();
				download(newBlkSz);
				return;
			}
		}
	}
	
	// access to results
	public Column[] getColumns() {return columns;}
	public int numRows() {return rows.size();}
	public Row getRow(int idx) {return rows.get(idx);}

	// ------------ private methods ------------

	// setup all the columns based on the JSON content
	private void parseColumns(JSONArray json) throws IOException
	{
		if (json == null) 
		{
			columns = new Column[0];
			return;
		}
		columns = new Column[json.length()];
		for (int n = 0; n < columns.length; n++)
		{
			JSONObject obj = json.getJSONObject(n);

			int idx = obj.getInt("tid") - 1;
			columns[idx] = new Column();
			columns[idx].name = obj.getString("name");

			columns[idx].descr = "";
			JSONArray jdescr = obj.optJSONArray("description");
			if (jdescr != null) for (int i = 0; i < jdescr.length(); i++) columns[idx].descr += (i > 0 ? "\n" : "") + jdescr.getString(i);
			
			String type = obj.optString("type", "");
			columns[idx].type = Type.fromShortCode(type);
			if (columns[idx].type == null) throw new IOException("Unexpected type: [" + type + "], object: " + obj);

			String units = obj.optString("unit", null);
			if (units != null) 
			{
				columns[idx].units = Units.fromShortCode(units);
				if (columns[idx].units == null) throw new IOException("Unexpected units: [" + units + "]");
			}
			else columns[idx].units = Units.NONE;
			
			columns[idx].activeColumn = obj.optBoolean("ac", false);
		}
	}
	
	// dig out all the rows and add them to the list
	private void parseRow(JSONObject json) throws IOException
	{
		Row row = new Row();
		row.sid = json.getInt("sid");
		
		String outcome = json.optString("outcome", null);
		if (outcome == null) throw new IOException("Outcome missing, source object: " + json);
		row.outcome = Outcome.fromShortCode(outcome);
		if (row.outcome == null) throw new IOException("Unexpected outcome: [" + outcome + "]");
		
		row.data = new Datum[columns.length];
		JSONArray dlist = json.optJSONArray("data");
		if (dlist != null) parseData(dlist, row);
		rows.add(row);
	}
	
	// add the data to row
	protected void parseData(JSONArray dlist, Row row)
	{
		for (int i = 0; i < dlist.length(); i++)
		{
			JSONObject dobj = dlist.getJSONObject(i);
			int idx = dobj.getInt("tid") - 1;
			
			// this can actually happen (bug in PubChem?)
			if (idx < 0 || idx >= row.data.length) continue;
			
			Datum d = row.data[idx] = new Datum();
			
			JSONObject vobj = dobj.getJSONObject("value");
			if (columns[idx].type == Type.FLOAT) d.value = vobj.getDouble("fval");
			else if (columns[idx].type == Type.INT) d.value = Double.valueOf(vobj.getInt("ival"));
			else if (columns[idx].type == Type.BOOL) d.value = vobj.getBoolean("bval") ? 1.0 : 0.0;
			else if (columns[idx].type == Type.STRING) d.str = vobj.getString("sval");
		}
	}
	
	// fetch all the substance IDs from the current assay; this is only necessary when dividing into blocks
	protected int[] fetchSubstances() throws IOException
	{
		String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/assay/aid/" + aid + "/sids/JSON";	
	
		String str = makeRequest(url, null, 3);
		if (str == null) return new int[0];

		JSONObject json = new JSONObject(str);
		JSONObject info = json.getJSONObject("InformationList").getJSONArray("Information").getJSONObject(0);
		JSONArray sids = info.getJSONArray("SID");
		
		int[] sidlist = new int[sids.length()];
		for (int n = 0; n < sidlist.length; n++) sidlist[n] = sids.getInt(n);
		return sidlist;
	}
	
	// fetch a block containing specific substances; the header information is repeated, and only needs to be parsed the 1st time
	protected void fetchBlock(boolean isFirst, int[] sidlist) throws IOException, JSONException
	{
		String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/assay/aid/" + aid + "/JSON";
		StringBuilder body = new StringBuilder();
		body.append("sid=");
		for (int n = 0; n < sidlist.length; n++)
		{
			if (n > 0) body.append(",");
			body.append(String.valueOf(sidlist[n]));
		}

		try
		{
			String str = makeRequest(url, body.toString(), 3);
			
			if (str == null)
			{
				try {Thread.sleep(500);} 
				catch (InterruptedException ex) {}
				str = makeRequest(url, body.toString(), 3);
			}
			
			JSONObject json = new JSONObject(new JSONTokener(str));

			JSONObject root = json.getJSONArray("PC_AssayContainer").getJSONObject(0);
			JSONObject descr = root.getJSONObject("assay").getJSONObject("descr");
			
			if (isFirst) parseColumns(descr.optJSONArray("results"));
			for (JSONObject row : root.getJSONArray("data").toObjectArray()) parseRow(row);
		}
		catch (Exception ex) {throw new IOException("Failed with URL [" + url + "], sids=" + sidlist[0] + ",..", ex);}
	}

	// required to mock pubchem access in unit tests
	protected String makeRequest(String url, String post, int rerequests) throws IOException
	{
		return Util.makeRequest(url, post, rerequests);
	}

	protected void setColumns(Column[] columns)
	{
		this.columns = columns;
	}
	
	// assuming that a START_OBJECT event just been pulled out, puts together all of the content into a container
	private JSONObject assembleObject(JsonParser parser)
	{
		JSONObject json = new JSONObject();
		String key = null;
		
		while (parser.hasNext()) 
		{
			JsonParser.Event event = parser.next();
			if (event == JsonParser.Event.END_OBJECT) break;
			else if (event == JsonParser.Event.KEY_NAME) key = parser.getString();
			else if (event == JsonParser.Event.START_ARRAY) json.put(key, assembleArray(parser));
			else if (event == JsonParser.Event.START_OBJECT) json.put(key, assembleObject(parser));
			else if (event == JsonParser.Event.VALUE_STRING) json.put(key, parser.getString());
			else if (event == JsonParser.Event.VALUE_NUMBER)
			{
				if (parser.isIntegralNumber()) json.put(key, parser.getInt()); else json.put(key, parser.getBigDecimal().doubleValue());
			}
			else if (event == JsonParser.Event.VALUE_FALSE) json.put(key, false);
			else if (event == JsonParser.Event.VALUE_TRUE) json.put(key, true);
			else if (event == JsonParser.Event.VALUE_NULL) json.put(key, (Object)null);
		}
		
		return json;
	}
	
	// assuming that a a START_ARRAY event just been pulled out, puts together all of the content into a container 
	private JSONArray assembleArray(JsonParser parser)
	{
		JSONArray json = new JSONArray();
		
		while (parser.hasNext()) 
		{
			JsonParser.Event event = parser.next();
			if (event == JsonParser.Event.END_ARRAY) break;
			else if (event == JsonParser.Event.START_ARRAY) json.put(assembleArray(parser));
			else if (event == JsonParser.Event.START_OBJECT) json.put(assembleObject(parser));
			else if (event == JsonParser.Event.VALUE_STRING) json.put(parser.getString());
			else if (event == JsonParser.Event.VALUE_NUMBER)
			{
				if (parser.isIntegralNumber()) json.put(parser.getInt()); else json.put(parser.getBigDecimal().doubleValue());
			}
			else if (event == JsonParser.Event.VALUE_FALSE) json.put(false);
			else if (event == JsonParser.Event.VALUE_TRUE) json.put(true);
			else if (event == JsonParser.Event.VALUE_NULL) json.put((Object)null);
		}
		
		return json;	
	}
	
	// runs down the hierarchy looking for the outline; there are two action sections of interest: [results] and [data]; the latter
	// is an array that can be large enough to bust the memory constraints, but each item is small
	//
	//	PC_AssaySubmit: {
	//		assay: {
	//			descr: {
	//				results: {...
	//		data: [...
	private void scanObject(JsonParser parser, Stack<String> stack) throws IOException
	{
		while (parser.hasNext()) 
		{
			JsonParser.Event event = parser.next();
			String key = null;
			if (event == JsonParser.Event.KEY_NAME)
			{
				key = parser.getString();
				event = parser.next();
			}
			
			if (event == JsonParser.Event.END_OBJECT) break;
			else if (event == JsonParser.Event.START_ARRAY) 
			{
				if (key != null) stack.push(key);
				
				//Util.writeln("STACK:"+stack);
				if (stack.size() == 4 && stack.get(0).equals("PC_AssaySubmit") && 
					stack.get(1).equals("assay") && stack.get(2).equals("descr") && stack.get(3).equals("results"))
				{
					// the next in stream is an array of column descriptions
					JSONArray json = assembleArray(parser);
					//Util.writeln("COLUMNS:\n"+json.toString(2));
					parseColumns(json);
				}
				else if (stack.size() == 2 && stack.get(0).equals("PC_AssaySubmit") && stack.get(1).equals("data"))
				{
					// next in stream is an array of objects, each of which represents a row; after the last object is analyzed,
					// the operation is complete
					while (parser.hasNext())
					{
						event = parser.next();
						if (event == JsonParser.Event.START_OBJECT)
						{
							JSONObject json = assembleObject(parser);
							//Util.writeln("----ROW----");
							//Util.writeln(json.toString(2));
							parseRow(json);
						}
						else return; // totally done
					}
				}
				
				if (key != null) stack.pop();
			}
			else if (event == JsonParser.Event.START_OBJECT)
			{
				if (key != null) stack.push(key);
				
				/*Util.writeln("STACK:"+stack);
				if (stack.size() == 4 && stack.get(0).equals("PC_AssaySubmit") && 
					stack.get(1).equals("assay") && stack.get(2).equals("descr") && stack.get(3).equals("results"))
				{
					Util.writeln("BLAM!!");
					JSONObject json = assembleObject(parser);
					Util.writeln("JSON:\n"+json.toString(2));
				}
				else*/ scanObject(parser, stack);

				// !! json.put(key, assembleArray(parser));
				
				if (key != null) stack.pop();
			}
		}
	}	
}



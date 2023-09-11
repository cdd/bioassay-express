/*
	BioAssay Express (BAE)

	(c) 2016-2018 Collaborative Drug Discovery Inc.
	All rights reserved
*/

package com.cdd.bae.selenium.utilities;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;

import com.cdd.bae.selenium.*;
import com.cdd.bao.util.Util;

/*
 *	RestAPICalls
*/

public class RestAPICalls
{
	/*
	 *	Returns a randomized list of assays. 
	 *
	 * 	@param numberOfAssays 
	 * 		(Sample: 10)
	 *  @param isCurated
	 *  	(Sample: true for curated assays only)
	 * 
	 *  Found on : / (homepage)
	 */
	
	public JSONArray postPickRandomAssay(int numberOfAssays, boolean isCurated) throws IOException
	{
		return getJSONArrayForCall("/REST/PickRandomAssay", "numAssays=" + numberOfAssays + "&curated=" + isCurated);
	}	
	
	/*
	 *  From com.cdd.bae.rest.GetPropertyTree
	 *  
	 * 	GetPropertyTree: fetches information about a particular assignment within a schema, in the form of a tree-ready datastructure.
	 * 
	 * 	Parameters:
	 * 		schemaURI, schemaBranches, schemaDuplication 
	 * 			(sample: "schemaURI":"http://www.bioassayontology.org/bas#")
	 * 		locator: which part of the template
	 * 			or
	 * 		propURI, groupNest: identify the assignment
	 * 			(sample: propURI":"http://www.bioassayontology.org/bao#BAO_0095010")
	 * 			(sample: groupNest":"[]")
	 * 
	 *  Note: May need to adjust the method signature on this call
	 *  
	 *  Found on: assign.jsp
	 */
		
	public JSONObject postGetPropertyTree(String aSchemaURL, String aPropURI, List<String> aGroupNest) throws IOException
	{
		JSONArray groupNestAsJSON = new JSONArray(aGroupNest);
		String groupNestFormatted = groupNestAsJSON.toString();
		return getJSONObjectForCall("/REST/GetPropertyTree", "schemaURI=" + aSchemaURL + "&propURI=" + aPropURI + "&groupNest=" + groupNestFormatted);
	}
	
	/*
	 *  From com.cdd.bae.rest.GetPropertyList
	 *  
	 * 	GetPropertyList: fetches information about a particular assignment within a schema, with the intent to create a list to be searched, ordered and selected from.
	 * 
	 * 	Parameters:
	 * 		schemaURI, schemaBranches, schemaDuplication 
	 * 			(sample: "schemaURI":"http://www.bioassayontology.org/bas#")
	 * 		locator: which part of the template
	 * 			or
	 * 		propURI, groupNest: identify the assignment
	 * 			(sample: propURI":"http://www.bioassayontology.org/bao#BAO_0095010")
	 * 			(sample: groupNest":"[]")
	 * 
	 *  Note: May need to adjust the method signature on this call
	 *  
	 *  Found on: assign.jsp
	 */	 

	public JSONObject postGetPropertyList(String aSchemaURL, String aPropURI, List<String> aGroupNest) throws IOException
	{
		JSONArray groupNestAsJSON = new JSONArray(aGroupNest);
		String groupNestFormatted = groupNestAsJSON.toString();
		return getJSONObjectForCall("/REST/GetPropertyList", "schemaURI=" + aSchemaURL + "&propURI=" + aPropURI + "&groupNest=" + groupNestFormatted);
	}

	
	/*
	 *	Returns a JSON string containing 3 matching arrays (arrayID, uniqueID, curationTime)
	 * 
	 *  Found on : curated.jsp
	 */

	
	public JSONObject postListCuratedAssays() throws IOException
	{
		return getJSONObjectForCall("/REST/ListCuratedAssays", "");
	}

	/*
	 *	Returns an assay given an assay Id 
	 *
	 *  @param sampleAssayId
	 *  	(Sample: 4991)
	 * 
	 *  Found on : curated.jsp (called ~ 3800 times for 223 MB of transfer)
	 */
	
	public JSONObject postGetAssay(int sampleAssayId) throws IOException
	{
		return getJSONObjectForCall("/REST/GetAssay", "assayID=" + sampleAssayId);
	}

	/*
	 * Returns a string description of the term (Not a JSON Object)
	 * 	@param aValueURI
	 * 		(Sample: valueURI: bao:BAO_0002414)
	 *  @param aSchemaURI
	 *  	(Sample: schemaURI: bas)
	 *  @apram aPropURI
	 *  	(Sample: propURI: bao:BAO_0095010)
	 */
	
	public String getVocabDescription(String aValueURI, String aSchemaURI, String aPropURI) throws IOException
	{
		return getStringForCall("/vocabdescr.jsp?valueURI=" + URLEncoder.encode(aValueURI, "UTF8") + "&schemaURI=" + URLEncoder.encode(aSchemaURI, "UTF8") + "&propURI=" + URLEncoder.encode(aPropURI, "UTF8"), null);
	}
		
	/*
	 *  Private Convenience Method for returning a JSONObject for a Post
	 */
	
	private JSONObject getJSONObjectForCall(String aUrl, String aParameterString) throws IOException
	{
		String response = getStringForCall(aUrl, aParameterString);
		return new JSONObject(response);
	}
	
	/*
	 *  Private Convenience Method for returning a JSONArray for a Post
	 */

	private JSONArray getJSONArrayForCall(String aUrl, String aParameterString) throws IOException
	{
		String response = getStringForCall(aUrl, aParameterString);	
		return new JSONArray(response);
	}	

	/*
	 *  Private Convenience Method for returning a JSONArray for a Post
	 */

	private String getStringForCall(String aUrl, String aParameterString) throws IOException
	{
		System.out.println("Posting to:" + Setup.getURL(aUrl) + " with parameter string:" + aParameterString);
		return Util.makeRequest(Setup.getURL(aUrl), aParameterString);	
	}	

}


/*
	BioAssay Express (BAE)

	(c) 2016-2018 Collaborative Drug Discovery Inc.
	All rights reserved
*/

package com.cdd.bae.selenium;

import com.cdd.bae.selenium.utilities.*;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import org.json.*;


/*
	Execute JSON calls to the server - return values are stored
*/

public class RestAPI
{
	private JSONObject curatedAssays;
	private RestAPICalls api;
	
	public RestAPI()
	{
		api = new RestAPICalls();
	}
	
	public boolean hasUncuratedAssays() throws IOException
	{
		JSONArray json = api.postPickRandomAssay(1, false);
		return json.length() > 0;
	}
	
	public JSONObject getCuratedAssays() throws IOException
	{
		if (curatedAssays != null) return curatedAssays;
		
		JSONObject json = api.postListCuratedAssays();
		
		assertThat(json.keySet(), hasItem("assayIDList"));			
		assertThat(json.keySet(), hasItem("uniqueIDList"));			
		assertThat(json.keySet(), hasItem("curationTime"));			

		int assayIDListLength = this.containsNonEmptyArray(json, "assayIDList");
		int uniqueIDListLength = this.containsNonEmptyArray(json, "uniqueIDList");
		int curationTimeListLength = this.containsNonEmptyArray(json, "curationTime");
		
		assertThat(assayIDListLength, equalTo(uniqueIDListLength));
		assertThat(assayIDListLength, equalTo(curationTimeListLength));
		assertThat(uniqueIDListLength, equalTo(curationTimeListLength));
		curatedAssays = json;
		return curatedAssays;
	}
	
	public JSONObject getAssay(int sampleAssayId) throws IOException
	{
		JSONObject json = api.postGetAssay(sampleAssayId);
				
		assertThat(json.keySet(), hasItem("assayID"));			
		assertThat(json.keySet(), hasItem("uniqueID"));			
		//assertThat(json.keySet(), hasItem("notes"));			
		assertThat(json.keySet(), hasItem("assayID"));			
		assertThat(json.keySet(), hasItem("annotations"));			
		assertThat(json.keySet(), hasItem("pubchemXRef"));			
		assertThat(json.keySet(), hasItem("history"));			
		assertThat(json.keySet(), hasItem("holdingIDList"));			

		// Available only for Curated Assays
//		assertThat(json.keySet(), hasItem("curationTime"));			
//		assertThat(json.keySet(), hasItem("curatorID"));			
//		assertThat(json.keySet(), hasItem("schemaURI"));			

		assertThat(sampleAssayId, is(json.getInt("assayID")));

		return json;
	}

	private int containsNonEmptyArray(JSONObject json, String key)
	{
		assertThat(json.keySet(), hasItem(key));
		int returnSize = json.getJSONArray(key).length();
		assertTrue(returnSize > 0);
		return returnSize;
	}
	
}

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

package com.cdd.testutil;

import com.cdd.bae.config.authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;

import java.util.*;

/*
	DataStore related support methods.
*/

public class DataStoreSupport
{
	private DataStoreSupport()
	{
	}

	public static void addFingerprints(DataNLP dataNLP)
	{
		dataNLP.addNLPFingerprint("a", 1);
		dataNLP.addNLPFingerprint("b", 2);
		dataNLP.addNLPFingerprint("c", 3);
		dataNLP.addNLPFingerprint("d", 4);
		dataNLP.addNLPFingerprint("e", 5);
		dataNLP.addNLPFingerprint("f", 6);
	}

	public static Authentication.Session makeUserSession(String curatorID, String curatorName)
	{
		Authentication.Session session = new Authentication.Session();
		session.curatorID = curatorID;
		session.userID = curatorID + "-1";
		session.userName = curatorName;
		session.email = curatorName + "@b.com";
		return session;
	}

	public static Provisional makeProvisional()
	{
		Date now = new Date();

		Provisional tr = new Provisional();
		tr.parentURI = "parent-" + now.getTime();
		tr.label = "label-" + now.getTime();
		tr.uri = "http://www.bioassayontology.org/bao#BAO_" + now.getTime();
		tr.description = "description-" + now.getTime();
		tr.explanation = "explanation-" + now.getTime();
		tr.bridgeStatus = DataProvisional.BRIDGESTATUS_SUBMITTED;
		tr.proposerID = "proposerID-" + now.getTime();
		tr.createdTime = now;
		tr.modifiedTime = now;
		tr.remappedTo = "remappedTo-" + now.getTime();

		return tr;
	}

	public static Holding makeHolding(long assayID, int nsize)
	{
		return makeHolding(assayID, nsize, "curatorID");
	}

	public static Holding makeHolding(long assayID, int nsize, String curatorID)
	{
		Holding holding = new Holding();
		holding.assayID = assayID;
		holding.submissionTime = new Date();
		holding.curatorID = curatorID;
		holding.uniqueID = "uniqueID:1234";
		holding.schemaURI = "http://www.bioassayontology.org/bas#";
		holding.text = "text";
		if (nsize == 0)
		{
			holding.annotsAdded = null;
			holding.labelsAdded = null;
		}
		else
		{
			holding.annotsAdded = new Annotation[nsize];
			holding.labelsAdded = new TextLabel[nsize];
			for (int i = 0; i < nsize; i++)
			{
				holding.annotsAdded[i] = makeAnnotation(i);
				holding.labelsAdded[i] = makeTextLabel(i);
			}
		}
		return holding;
	}

	private static Annotation makeAnnotation(int index)
	{
		return new Annotation("propURI", "valueURI-" + index, new String[]{"a", "b"});
	}

	private static TextLabel makeTextLabel(int index)
	{
		return new TextLabel("propURI", "text-" + index, new String[]{"a", "b"});
	}
}

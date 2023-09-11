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
import com.cdd.bao.util.*;

import java.util.*;

import org.apache.commons.lang3.*;

/*
	Command line functionality pertaining to the holding bay, e.g. listing and approving.
*/

public class HoldingCommands implements Main.ExecuteBase
{
	private static final String DONE = "Done.";
	private static final int DELAY_EMPTY_HOLDING_BAY = 5000;
	protected int delayEmptyHoldingBay;

	// ------------ public methods ------------
	
	public HoldingCommands()
	{
		this.delayEmptyHoldingBay = DELAY_EMPTY_HOLDING_BAY;
	}

	public void execute(String[] args)
	{
		if (args.length == 0) {printHelp(); return;}

		String cmd = args[0];
		String[] options = ArrayUtils.remove(args, 0);

		if (cmd.equals("list")) listEntries();
		else if (cmd.equals("curators")) listCurators();
		else if (cmd.equals("approve")) approveEntries(options, false);
		else if (cmd.equals("approveall")) approveEntries(options, true);
		else if (cmd.equals("delete")) deleteEntries(options);
		else if (cmd.equals("empty")) deleteAllEntries();
		else Util.writeln("Unknown command: '" + cmd + "'.");
	}

	public void printHelp()
	{
		Util.writeln("Holding Bay commands");
		Util.writeln("    manage the holding bay assay - assay edits waiting to be applied to the main data");
		Util.writeln("Options:");
		Util.writeln("    list: show all entries awaiting approval");
		Util.writeln("    curators: show entries grouped by curator");
		Util.writeln("    approve {id#} ...: approve given entries (command separated list of IDs)");
		Util.writeln("    approveall: approves everything in the holding bay (use with care!)");
		Util.writeln("    delete {id#} ...: reject (i.e. delete) the indicated entries");
		Util.writeln("    empty: clear out everything in the holding bay");

		Util.writeln();
		Util.writeln("e.g. bae holding list");
		Util.writeln("     bae holding approve 1 2 3");
		Util.writeln("     bae holding delete 4 5 6");
	}

	// ------------ private methods ------------

	// lists out all the holding bay entries, if any	
	private void listEntries()
	{
		Util.writeln("Holding Bay entries...");

		DataStore store = Common.getDataStore();
		Map<Long, long[]> assayMap = new TreeMap<>();
		for (long holdingID : store.holding().fetchHoldings())
		{
			long assayID = store.holding().getHoldingAssayID(holdingID);
			if (assayID == 0) assayID = findAssayID(store, holdingID);
			assayMap.put(assayID, ArrayUtils.add(assayMap.get(assayID), holdingID));
		}

		if (assayMap.isEmpty()) Util.writeln("Holding bay is empty.");

		for (Map.Entry<Long, long[]> e : assayMap.entrySet())
		{
			long assayID = e.getKey();
			long[] list = e.getValue();

			DataObject.User[] users = new DataObject.User[list.length];
			Set<String> uniqUser = new TreeSet<>(), uniqID = new TreeSet<>();
			boolean deletion = false;
			for (int n = 0; n < list.length; n++)
			{
				DataObject.Holding holding = store.holding().getHolding(list[n]);
				users[n] = store.user().getUser(holding.curatorID);
				uniqUser.add(users[n] == null ? "" : users[n].curatorID);
				if (Util.notBlank(holding.uniqueID)) uniqID.add(holding.uniqueID);
				if (holding.deleteFlag) deletion = true;
			}

			if (assayID == 0)
			{
				Util.writeln("New assays:");
				for (int n = 0; n < list.length; n++)
					Util.writeln("    " + list[n] + formatUserDescr(users[n]));
			}
			else
			{
				String uniqueDescr = "";
				if (!uniqID.isEmpty()) for (String uid : uniqID) uniqueDescr += (uniqueDescr.length() == 0 ? " UniqueID=" : ",") + uid;

				Util.write("Assay#" + assayID + ":");
				if (uniqUser.size() > 1)
				{
					Util.writeln(uniqueDescr);
					for (int n = 0; n < list.length; n++)
						Util.writeln("      holding ID#" + list[n] + formatUserDescr(users[n]));
				}
				else Util.writeln(" holding ID# " + Util.arrayStr(list) + uniqueDescr + formatUserDescr(users[0]));
				
				if (deletion) Util.writeln(" marked for DELETION");
			}
		}
		
		Util.writeln(DONE);
	}
	
	private String formatUserDescr(DataObject.User user)
	{
		if (user == null) return " @ unknown user";
		return " @ '" + Util.safeString(user.name) + "', '" + Util.safeString(user.email) + "' (" + user.curatorID + ")";
	}
	
	// list by curator: conveniently pasteable back into the 'approve' option
	private void listCurators()
	{
		Util.writeln("Holding Bay entries by curator...");
	
		DataStore store = Common.getDataStore();
		Map<String, long[]> curatorMap = new TreeMap<>();
		for (long holdingID : store.holding().fetchHoldings())
		{
			String curatorID = store.holding().getHolding(holdingID).curatorID;
			if (curatorID == null) curatorID = "?"; // (shouldn't happen)
			long[] list = curatorMap.get(curatorID);
			curatorMap.put(curatorID, ArrayUtils.add(list, holdingID));
		}
		
		if (curatorMap.size() == 0) Util.writeln("Holding bay is empty.");
		
		for (Map.Entry<String, long[]> e : curatorMap.entrySet())
		{
			String curatorID = e.getKey();
			DataObject.User user = store.user().getUser(curatorID);
		
			if (user == null)
				Util.writeln(curatorID + " ('?', '?'):");
			else
				Util.writeln(curatorID + " ('" + Util.safeString(user.name) + "', '" + Util.safeString(user.email) + "'):");

			Util.write("   ");
			for (long holdingID : e.getValue()) Util.write(" " + holdingID);
			Util.writeln();
		}
		
		Util.writeln(DONE);
	}
	
	// approves the holding bay ID numbers indicated
	private void approveEntries(String[] options, boolean everything)
	{
		DataStore store = Common.getDataStore();

		long[] holdingIDList;
		if (!everything)
		{
			holdingIDList = new long[options.length];
			for (int n = 0; n < options.length; n++) holdingIDList[n] = Long.parseLong(options[n]);
		}
		else holdingIDList = store.holding().fetchHoldings();
		
		Util.writeln("Approving entries in Holding Bay: " + Util.arrayStr(holdingIDList));
				
		int numApproved = 0, numDeleted = 0;
		for (long holdingID : holdingIDList)
		{
			DataObject.Holding holding = store.holding().getHolding(holdingID);
			if (holding == null)
			{
				Util.writeln("holdingID #" + holdingID + " not found.");
				continue;
			}
			else if (holding.deleteFlag)
			{
				Util.writeln("Applying holdingID #" + holdingID + " (deletion)");
				
				store.measure().deleteMeasurementsForAssay(holding.assayID);
				store.assay().deleteAssay(holding.assayID);

				store.holding().deleteHolding(holdingID);
				numDeleted++;
			} 
			else 
			{
				Util.writeln("Applying holdingID #" + holdingID);
				
				DataObject.Assay assay = holding.assayID > 0 ? store.assay().getAssay(holding.assayID) : null;
				if (assay == null && holding.uniqueID != null) assay = store.assay().getAssayFromUniqueID(holding.uniqueID);
				assay = DataHolding.createAssayDelta(assay, holding);

				store.assay().submitAssay(assay);

				store.holding().deleteHolding(holdingID);
				numApproved++;
			}
		}

		Util.writeln(DONE);
		if (numApproved > 0) Util.writeln(" # approved: " + numApproved);
		if (numDeleted > 0) Util.writeln(" # deletions: " + numDeleted);
	}
		
	// deletes the holding bay ID numbers indicated
	private void deleteEntries(String[] options)
	{
		long[] holdingIDList = new long[options.length];
		for (int n = 0; n < options.length; n++) holdingIDList[n] = Long.parseLong(options[n]);
		Util.writeln("Deleting entries in Holding Bay: " + Util.arrayStr(holdingIDList));
		
		DataStore store = Common.getDataStore();
		
		for (int n = 0; n < holdingIDList.length; n++)
		{
			long holdingID = holdingIDList[n];
			DataObject.Holding holding = store.holding().getHolding(holdingID);
			if (holding == null)
			{
				Util.writeln("holdingID #" + holdingID + " not found.");
				continue;
			}
			Util.writeln("Deleting holdingID #" + holdingID);
			store.holding().deleteHolding(holdingID);
		}

		Util.writeln(DONE);
	}
	
	// removes all entries
	private void deleteAllEntries()
	{
		Util.writeln("Deleting all entries in Holding Bay...");
		
		DataStore store = Common.getDataStore();
		
		long[] holdingIDList = store.holding().fetchHoldings();
		if (holdingIDList.length == 0) {Util.writeln("... nothing to delete."); return;}
		
		Util.writeln("About to delete " + holdingIDList.length + " entries.");
		try {Thread.sleep(delayEmptyHoldingBay);} 
		catch (InterruptedException ex) {return;} // pause to cancel!
		
		for (int n = 0; n < holdingIDList.length; n++)
		{
			Util.writeln("Deleting holdingID #" + holdingIDList[n]);
			store.holding().deleteHolding(holdingIDList[n]);
		}

		Util.writeln(DONE);
	}

	// if the assayID didn't come through, make sure it can't be rediscovered by some other identifier; 0 = still couldn't find
	private long findAssayID(DataStore store, long holdingID)
	{
		DataObject.Holding holding = store.holding().getHolding(holdingID);
		if (holding == null) return 0;
		if (Util.notBlank(holding.uniqueID))
		{
			DataObject.Assay assay = store.assay().getAssayFromUniqueID(holding.uniqueID);
			if (assay != null) return assay.assayID;
		}
		return 0;
	}
}

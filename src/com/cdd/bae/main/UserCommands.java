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

import com.cdd.bae.config.authentication.*;
import com.cdd.bae.config.authentication.Access.*;
import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.apache.commons.lang3.*;

/*
	Command line functionality pertaining to user accounts.
*/

public class UserCommands implements Main.ExecuteBase
{
	private static final String[] STATUS_LIST = 
	{
		DataUser.STATUS_DEFAULT,
		DataUser.STATUS_BLOCKED,
		DataUser.STATUS_CURATOR,
		DataUser.STATUS_ADMIN
	};

	// ------------ public methods ------------

	public void execute(String[] args)
	{
		if (args.length == 0) {printHelp(); return;}
		
		String cmd = args[0];
		String[] options = ArrayUtils.remove(args, 0);

		if (cmd.equals("list")) listUsers();
		else if (cmd.equals("statistics")) userStats();
		else if (cmd.equals("status")) changeStatus(options);
		else if (cmd.equals("rename")) changeUserName(options);
		else if (cmd.equals("reset-password")) resetPassword(options);
		else Util.writeln("Unknown command: '" + cmd + "'.");
	}
	
	public void printHelp()
	{
		Util.writeln("User account commands");
		Util.writeln("    manage users: people who can authenticate for higher privileges");
		Util.writeln("Options:");
		Util.writeln("    list: summary of users");
		Util.writeln("    statistics: information about annotation behavior");
		Util.writeln("    status {curatorID} {new status}: change status of indicated user");
		Util.writeln("    rename {curatorID} {new name}: specify human name of indicated user");
		Util.writeln("    reset-password {curatorID}: reset user password, initialize user for basic authentication if required");
		Util.writeln("Notes:");
		Util.writeln("    available status types are [" + Util.arrayStr(STATUS_LIST) + "]");
		
		Util.writeln();
		Util.writeln("e.g. bae user list");
	}  
	
	// ------------ private methods ------------
	
	
	// lists out all the holding bay entries, if any	
	private void listUsers()
	{
		Util.writeln("List of users:");
	
		DataStore store = Common.getDataStore();
		
		String[] userList = store.user().listUsers();
		Arrays.sort(userList);
		for (int n = 0; n < userList.length; n++)
		{
			Util.write("[" + userList[n] + "]: ");
			Util.writeFlush();
			DataObject.User user = store.user().getUser(userList[n]);
			if (user == null)
			{
				Util.writeln("?");
				continue;
			}
			
			String status = user.status == null ? "nobody" : user.status;
			Util.writeln("<" + status + "> '" + user.name + "', '" + user.email + "' (" + user.lastAuthent + ")");
		}
	}
	
	private void changeStatus(String[] options)
	{
		if (options.length != 2) 
		{
			Util.writeln("Parameters are {curatorID} {new status}");
			return;
		}
		
		String curatorID = options[0], status = options[1];
		if (ArrayUtils.indexOf(STATUS_LIST, status) < 0)
		{
			Util.writeln("Parameter status '" + status + "' invalid.");
			return;
		}
		Util.writeln("Changing status of [" + curatorID + "] to [" + status + "]");

		DataStore store = Common.getDataStore();
		
		DataObject.User user = store.user().getUser(curatorID);
		if (user == null)
		{
			Util.writeln("User not present, nothing done.");
			return;
		}
		if (user.status != null && user.status.equals(status))
		{
			Util.writeln("Status unchanged.");
			return;
		}

		store.user().changeStatus(curatorID, status);
		Util.writeln("Change applied.");
	}
	
	private void changeUserName(String[] options)
	{
		if (options.length != 2)
		{
			Util.writeln("Parameters are {curatorID} {new name}");
			return;
		}
		
		String curatorID = options[0], newName = options[1];
		Util.writeln("Changing name of [" + curatorID + "] to [" + newName + "]");

		DataStore store = Common.getDataStore();
		
		DataObject.User user = store.user().getUser(curatorID);
		if (user == null)
		{
			Util.writeln("User not present, nothing done.");
			return;
		}
		if (user.name != null && user.name.equals(newName))
		{
			Util.writeln("Name unchanged.");
			return;
		}

		store.user().changeUserName(curatorID, newName);
		Util.writeln("Change applied.");
	}
	
	private void resetPassword(String[] options)
	{
		Access[] authentication = Common.getAuthentication().getAccessList(AccessType.BASIC);
		if (authentication.length != 1)
		{
			Util.writeln("Server not configured correctly for basic authentication");
		}
		if (options.length != 1) 
		{
			Util.writeln("Parameters are {curatorID}");
			return;
		}
		
		String curatorID = options[0];
		BasicAuthentication auth = (BasicAuthentication)authentication[0];
		if (!curatorID.startsWith(auth.prefix))
		{
			Util.writeln("curatorID must start with " + auth.prefix);
			return;
		}
		
		Util.writeln("Reset password for [" + curatorID + "]");
		DataStore store = Common.getDataStore();
		
		DataObject.User user = store.user().getUser(curatorID);
		if (user == null)
		{
			Util.writeln("User not present, initialize user.");
			String username = curatorID.split(":")[1];
			Common.getDataStore().user().submitSession(auth.createDefaultSession(username));
		}
		
		String passwd = auth.resetPassword(curatorID);
		Util.writeln("New password: " + passwd);
		Util.writeln("Change applied.");
	}
	
	private final class UserStats
	{
		public String curatorID;
		public Date lastActivity = null;
		public int count = 0;
		public int provisionalCount = 0;
		public User user;
		
		UserStats(String curatorID)
		{
			this.curatorID = curatorID;
			user = Common.getDataStore().user().getUser(curatorID);
		}
		
		public void updateCount()
		{
			count++; 
		}

		public void updateProvisionalCount()
		{
			provisionalCount++; 
		}

		public void updateLastActivity(Date date)
		{
			if (lastActivity == null || lastActivity.before(date))
				lastActivity = date;
		}
		
		public String toString()
		{
			String name = user == null ? "" : "(" + user.name + ")";
			return String.format("%-30s %6d %6d  %tF %s", curatorID, count, provisionalCount, lastActivity, name);
		}
	}
	
	private void userStats()
	{
		DataAssay dataAssay = Common.getDataStore().assay();
		DataHolding holdings = Common.getDataStore().holding();
		DataProvisional provisionals = Common.getDataStore().provisional();
		
		Map<String, UserStats> allUserStats = new TreeMap<>();
		
		Set<String> curators = new HashSet<>();
		for (long assayID : dataAssay.fetchAllAssayID())
		{
			curators.clear();
			// collect information from curated assays 
			Assay assay = dataAssay.getAssay(assayID);
			if (assay.isCurated)
				for (History history : assay.history)
				{
					curators.add(history.curatorID);
					UserStats userStats = allUserStats.computeIfAbsent(history.curatorID, UserStats::new);
					userStats.updateLastActivity(history.curationTime);
				}
			// collect information from holding bay
			for (long holdingID : holdings.fetchForAssayID(assayID))
			{
				Holding holding = holdings.getHolding(holdingID);
				curators.add(holding.curatorID);
				UserStats userStats = allUserStats.computeIfAbsent(holding.curatorID, UserStats::new);
				userStats.updateLastActivity(holding.submissionTime);
			}
			
			for (String curator : curators)
				allUserStats.get(curator).updateCount();
		}
		
		// collect information about provisional terms
		for (Provisional provisional : provisionals.fetchAllTerms())
			allUserStats.get(provisional.proposerID).updateProvisionalCount();

		
		
		Util.writeln("Curator                        Assays  Terms  Last activity");
		for (UserStats userStats : allUserStats.values()) Util.writeln(userStats);
	}
}

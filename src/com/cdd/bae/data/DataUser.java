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

import com.cdd.bae.config.authentication.*;
import com.cdd.bao.util.*;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import static com.cdd.bae.data.DataStore.*;

import java.util.*;

import org.apache.commons.codec.*;
import org.apache.commons.codec.binary.*;
import org.bson.*;
import org.slf4j.*;

/*
	Specialisation based on DataStore: keeps track of everyone who has authenticated with the system.
*/

public class DataUser
{
	private static final Logger logger = LoggerFactory.getLogger(DataUser.class);

	private DataStore store;
	
	// values used by the .status field
	public static final String STATUS_DEFAULT = "default"; // same as null: basically a walk-in
	public static final String STATUS_BLOCKED = "blocked"; // explicitly disallowed from doing anything
	public static final String STATUS_CURATOR = "curator"; // allowed to submit content directly
	public static final String STATUS_ADMIN = "admin"; // can perform sensitive tasks remotely
	public static final String[] ALL_STATUS = {STATUS_BLOCKED, STATUS_DEFAULT, STATUS_CURATOR, STATUS_ADMIN};

	// ------------ public methods ------------

	public DataUser(DataStore store)
	{
		this.store = store;
	}
	
	public int countUsers()
	{
		return (int)store.db.getCollection(COLL_USER).countDocuments();
	}

	// fetch a list of all curatorIDs
	public String[] listUsers()
	{
		List<String> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_USER);
		Document proj = new Document(FLD_USER_CURATORID, true);
		for (Document doc : coll.find().projection(proj)) list.add(doc.getString(FLD_USER_CURATORID));
		return list.toArray(new String[list.size()]);
	}
	
	// pulls out all information for a given user, or null if not found
	public User getUser(String curatorID)
	{
		if (Util.isBlank(curatorID)) return null;
		MongoCollection<Document> coll = store.db.getCollection(COLL_USER);
		Document filter = new Document(FLD_USER_CURATORID, curatorID);
		for (Document doc : coll.find(filter).limit(1)) return userFromDoc(doc);
		return null;
	}
	
	// given that an authentication event just happened, updates the user database; this will create a new user record if none existed, otherwise
	// it will update pertinent details
	public void submitSession(Authentication.Session session)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_USER);
		Document idx = new Document(FLD_USER_CURATORID, session.curatorID);
		Document doc = new Document(FLD_USER_CURATORID, session.curatorID);
		doc.append(FLD_USER_SERVICENAME, session.serviceName);
		doc.append(FLD_USER_USERID, session.userID);
		doc.append(FLD_USER_NAME, session.userName);
		doc.append(FLD_USER_EMAIL, session.email);
		doc.append(FLD_USER_LASTAUTHENT, new Date().getTime());
		coll.updateOne(idx, new Document("$set", doc), new UpdateOptions().upsert(true));
	}
	
	// changes the status of a user; if not present in the database, nothing happens
	public void changeStatus(String curatorID, String status)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_USER);
		Document idx = new Document(FLD_USER_CURATORID, curatorID);
		Document doc = new Document(FLD_USER_STATUS, status);
		coll.updateOne(idx, new Document("$set", doc));
	}
	
	// changes the name of a user; if not present in the database, nothing happens
	public void changeUserName(String curatorID, String newName)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_USER);
		Document idx = new Document(FLD_USER_CURATORID, curatorID);
		Document doc = new Document(FLD_USER_NAME, newName);
		coll.updateOne(idx, new Document("$set", doc));
	}
	
	// changes the email of a user; if not present in the database, nothing happens
	public void changeEmail(String curatorID, String newEmail)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_USER);
		Document idx = new Document(FLD_USER_CURATORID, curatorID);
		Document doc = new Document(FLD_USER_EMAIL, newEmail);
		coll.updateOne(idx, new Document("$set", doc));
	}
	
	public void changeCredentials(String curatorID, byte[] passwordHash, byte[]salt)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_USER);
		Document idx = new Document(FLD_USER_CURATORID, curatorID);
		Document doc = new Document(FLD_USER_PWHASH, Hex.encodeHexString(passwordHash));
		doc.append(FLD_USER_PWSALT, Hex.encodeHexString(salt));
		coll.updateOne(idx, new Document("$set", doc));
	}

	// ------------ private methods ------------
	
	// pulls out everything from the source document
	private User userFromDoc(Document doc)
	{
		User user = new User();
		user.curatorID = doc.getString(FLD_USER_CURATORID);
		user.status = doc.getString(FLD_USER_STATUS);
		user.serviceName = doc.getString(FLD_USER_SERVICENAME);
		user.userID = doc.getString(FLD_USER_USERID);
		user.name = doc.getString(FLD_USER_NAME);
		user.email = doc.getString(FLD_USER_EMAIL);
		user.lastAuthent = new Date(doc.getLong(FLD_USER_LASTAUTHENT));
		try
		{
			String s = doc.getString(FLD_USER_PWHASH);
			user.passwordHash = s == null ? null : Hex.decodeHex(s.toCharArray());
			s = doc.getString(FLD_USER_PWSALT);
			user.passwordSalt = s == null ? null : Hex.decodeHex(s.toCharArray());
		}
		catch (DecoderException e)
		{
			logger.error("User validation failed due to decoder exception for user {}", user.curatorID);
			user.passwordHash = null;
			user.passwordSalt = null;
		}
		return user;
	}	
}

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

package com.cdd.bae.config.authentication;

import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.security.*;
import java.security.spec.*;
import java.time.*;
import java.util.*;
import java.util.Map.*;

import javax.crypto.*;
import javax.crypto.spec.*;

import org.apache.commons.mail.*;
import org.json.*;

public class BasicAuthentication extends Access
{
	public static final AccessType TYPE = AccessType.BASIC;
	
	public String smtpHost = "localhost";
	public int smtpPort = 465;
	
	protected class ResetKey
	{
		public String key;
		public Instant created;

		public ResetKey(String authPrefix)
		{
			key = authPrefix + generatePassword(30);
			created = Instant.now();
		}
		
		@Override
		public String toString()
		{
			return key;
		}
		
		public boolean isValid()
		{
			return Duration.between(created, Instant.now()).compareTo(Duration.ofMinutes(15)) < 0;
		}
	}
	
	protected static final Map<String, ResetKey> resetKeys = new HashMap<>();

	private static final Random random = new SecureRandom();
	private static final int ITERATIONS = 10000;
	private static final int KEY_LENGTH = 256;
	private static final String ALPHA_CAPS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private static final String ALPHA = "abcdefghijklmnopqrstuvwxyz";
	private static final String NUMERIC = "0123456789";

	@Override
	public boolean equals(Object o)
	{
		if (o == null || getClass() != o.getClass()) return false;
		BasicAuthentication other = (BasicAuthentication)o;
		return name.equals(other.name) && prefix.equals(other.prefix) && smtpHost.equals(other.smtpHost) && smtpPort == other.smtpPort;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, prefix, smtpHost, smtpPort);
	}

	@Override
	public String getType()
	{
		return TYPE.toString();
	}

	@Override
	public JSONObject serializeFrontend()
	{
		JSONObject obj = new JSONObject();
		obj.put("type", getType());
		obj.put("name", name);
		obj.put("prefix", prefix);
		return obj;
	}


	@Override
	public void parseJSON(JSONObject obj)
	{
		super.parseJSON(obj);
		smtpHost = obj.optString("smtpHost", smtpHost);
		smtpPort = obj.optInt("smtpPort", smtpPort);
	}

	@Override
	public Session authenticate(String... parameter)
	{
		String username = parameter[0];
		String password = parameter[1];

		String curatorID = prefix + username;
		DataObject.User user = Common.getDataStore().user().getUser(curatorID);

		boolean valid = user != null && validUserCredentials(user, password);
		if (!valid) return null;

		// authentication successful
		Session session = createDefaultSession(username);

		// the user has been seen before, override content from the hardcoded database
		if (Util.notBlank(user.status)) session.status = user.status;
		if (Util.notBlank(user.name)) session.userName = user.name;
		if (Util.notBlank(user.email)) session.email = user.email;
		resetKeys.remove(user.curatorID);
		return session;
	}

	public String resetPassword(String curatorID)
	{
		String pw = generatePassword(10);
		this.setPassword(curatorID, pw);
		return pw;
	}

	public void setPassword(String curatorID, String pw)
	{
		if (!curatorID.startsWith(prefix)) curatorID = prefix + curatorID;
		byte[] salt = BasicAuthentication.getNextSalt();
		byte[] pwhash = BasicAuthentication.hashPassword(pw.toCharArray(), salt);
		Common.getDataStore().user().changeCredentials(curatorID, pwhash, salt);
	}

	public void registerUser(String username, String pw, String email)
	{
		Session session = createDefaultSession(username);
		if (Util.notBlank(email)) session.email = email;
		Common.getDataStore().user().submitSession(session);
		setPassword(username, pw);
	}

	public static String attemptResetPassword(String username)
	{
		if (username.indexOf(':') > -1) username = username.split(":", 2)[1];

		Authentication authentication = Common.getAuthentication();
		for (BasicAuthentication auth : getBasicAuthentication(authentication))
		{
			String curatorID = auth.prefix + username;
			if (Common.getDataStore().user().getUser(curatorID) == null) continue;
			return auth.resetPassword(curatorID);
		}
		return null;
	}

	public static DataObject.User findUser(String username)
	{
		if (username.indexOf(':') > -1) username = username.split(":", 2)[1];

		Authentication authentication = Common.getAuthentication();
		for (BasicAuthentication auth : getBasicAuthentication(authentication))
		{
			DataObject.User user = Common.getDataStore().user().getUser(auth.prefix + username);
			if (user == null) continue;
			return user;
		}
		return null;
	}

	public static Session attemptBasicAuthentication(String username, String pw) throws IOException
	{
		if (username.isEmpty() || pw.isEmpty()) return null;
		if (username.indexOf(':') > -1) username = username.split(":", 2)[1];
		Authentication authentication = Common.getAuthentication();
		for (BasicAuthentication auth : getBasicAuthentication(authentication))
		{
			Session session = authentication.authenticateSession(auth.name, username, pw);
			if (session != null) return session;
		}
		return null;
	}

	public static boolean attemptChangePassword(String username, String pw, String newpassword) throws IOException
	{
		if (newpassword.length() < 10) return false;
		Authentication authentication = Common.getAuthentication();
		for (BasicAuthentication auth : getBasicAuthentication(authentication))
		{
			Session session = authentication.authenticateSession(auth.name, username, pw);
			if (session != null)
			{
				auth.setPassword(username, newpassword);
				return true;
			}
		}
		return false;
	}

	public static String attemptResetPasswordEmail(String resetKey, String newpassword) throws Exception, IOException
	{
		if (newpassword.length() < 10) throw new Exception("Password too short");
		String username = null;
		for (Entry<String, ResetKey> key : resetKeys.entrySet())
		{
			if (!key.getValue().key.equals(resetKey)) continue; 
			username = key.getKey();
		}
		if (username == null) throw new Exception("Invalid reset key");
		if (username.indexOf(':') > -1) username = username.split(":", 2)[1];
		
		Authentication authentication = Common.getAuthentication();
		for (BasicAuthentication auth : getBasicAuthentication(authentication))
		{
			String curatorID = auth.prefix + username;
			if (!resetKeys.containsKey(curatorID)) continue;

			ResetKey expected = resetKeys.remove(curatorID);
			if (expected != null && expected.isValid() && resetKey.equals(expected.key))
			{
				auth.setPassword(username, newpassword);
				return curatorID;
			}
		}
		throw new Exception("Invalid reset key");
	}

	public void sendResetPasswordEmail(DataObject.User user) throws EmailException
	{
		prepareResetPasswordEmail(user, new HtmlEmail()).send();
	}

	// ------------ private and protected methods ------------
	
	protected Email prepareResetPasswordEmail(DataObject.User user, HtmlEmail email) throws EmailException
	{
		if (user.email == null || user.email.isEmpty()) throw new EmailException("No email defined for user");
		
		String authPrefix = user.curatorID.split(":")[0] + ":";
		ResetKey resetKey = new ResetKey(authPrefix);
		resetKeys.put(user.curatorID, resetKey);
		
		email.setHostName(smtpHost);
		email.setSmtpPort(smtpPort);
		email.setSSLOnConnect(smtpPort == 465);

		email.setFrom("do-not-reply@collaborativedrug.com");
		email.setSubject("Reset password for BioAssay Express");
		StringBuilder msg = new StringBuilder();
		msg.append("Reset key: " + resetKey + "\n");
		msg.append("<a href=\"" + Common.getParams().baseURL + "?resetkey=" + resetKey + "\">Click to reset your password</a>");
		email.setHtmlMsg(msg.toString());
		email.setTextMsg(msg.toString());
				
		email.addTo(user.email);
		return email;
	}

	private static BasicAuthentication[] getBasicAuthentication(Authentication authentication)
	{
		Access[] accessList = authentication.getAccessList(AccessType.BASIC);
		return Arrays.copyOf(accessList, accessList.length, BasicAuthentication[].class);
	}

	private boolean validUserCredentials(DataObject.User user, String password)
	{
		if (user.passwordSalt == null || user.passwordHash == null) return false;
		byte[] salt = user.passwordSalt;
		byte[] expectedHash = user.passwordHash;

		return validPassword(password.toCharArray(), salt, expectedHash);
	}

	protected static boolean validPassword(char[] password, byte[] salt, byte[] expectedHash)
	{
		if (expectedHash == null || expectedHash.length == 0) return false;

		byte[] pwHash = hashPassword(password, salt);
		return Arrays.equals(pwHash, expectedHash);
	}

	protected static byte[] getNextSalt()
	{
		byte[] salt = new byte[16];
		random.nextBytes(salt);
		return salt;
	}

	protected static byte[] hashPassword(char[] password, byte[] salt)
	{
		PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
		Arrays.fill(password, Character.MIN_VALUE);
		SecretKeyFactory skf;
		try
		{
			skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
			return skf.generateSecret(spec).getEncoded();
		}
		catch (NoSuchAlgorithmException | InvalidKeySpecException e)
		{
			return new byte[0];
		}
		finally
		{
			spec.clearPassword();
		}
	}

	public static String generatePassword(int len)
	{
		String letters = ALPHA_CAPS + ALPHA + NUMERIC;
		StringBuilder builder = new StringBuilder("");
		for (int i : random.ints(len, 0, letters.length()).toArray())
		{
			builder.append(letters.charAt(i));
		}
		return builder.toString();
	}
}

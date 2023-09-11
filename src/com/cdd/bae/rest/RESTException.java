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

import org.apache.commons.text.*;
import org.json.*;

/*
	Custom exception class for REST services.

	Use this class to document the HTTP type of error and feedback to user and developer.   
*/

public class RESTException extends Exception
{
	public enum HTTPStatus 
	{
		OK(200),
		NOT_MODIFIED(304),
		BAD_REQUEST(400), 
		UNAUTHORIZED(401), 
		FORBIDDEN(403),
		INTERNAL_SERVER_ERROR(500);
		
		private final int code;

		HTTPStatus(int code)
		{
			this.code = code;
		}
		public int code()
		{
			return this.code;
		}
	}
	
	private static final long serialVersionUID = -2986974873695239664L;
	private final HTTPStatus httpStatus;
	private final String userMessage;
	
	// convenience creation for detected errors messages
	public static RESTException bad(String msg) {return new RESTException(msg, HTTPStatus.BAD_REQUEST);}
	
	public RESTException(String message, Throwable cause, String userMessage, HTTPStatus httpStatus)
	{
		super(message, cause);
		this.userMessage = userMessage;
		this.httpStatus = httpStatus;
	}
	
	public RESTException(Throwable cause, String userMessage, HTTPStatus httpStatus)
	{
		this(cause.getMessage(), cause, userMessage, httpStatus);
	}
	
	public RESTException(String message, HTTPStatus httpStatus)
	{
		this(message, null, message, httpStatus);
	}
	
	public JSONObject toJSON()
	{
		JSONObject result = new JSONObject();
		result.put("status", getHTTPStatus());
		result.put("userMessage", StringEscapeUtils.escapeHtml4(getUserMessage()));
		result.put("developerMessage", StringEscapeUtils.escapeHtml4(getMessage()));
		return result;
	}
	
	public int getHTTPStatus()
	{
		return httpStatus.code();
	}
	
	public String getUserMessage()
	{
		return this.userMessage;
	}
}
/*
 * http://blog.restcase.com/rest-api-error-codes-101/ 
 * 
 * '{ "status" : 400, 
 * "developerMessage" : "Verbose, plain language description of the problem. Provide developers suggestions about how to solve their problems here",
 * "userMessage" : "This is a message that can be passed along to end-users, if needed.",
 * "errorCode" : "444444", 
 * "moreInfo" : "http://www.example.gov/developer/path/to/help/for/444444, http://tests.org/node/444444", }'
 * 
 * Start by using the following 3 codes. If you need more, add them. But you shouldn't go beyond 8.

200 - OK
404 - Not Found
500 - Internal Server Error
If you're not comfortable reducing all your error conditions to these 3, try picking among these additional 5:

201 - Created
304 - Not Modified
400 - Bad Request
401 - Unauthorized
403 - Forbidden

https://stackoverflow.com/questions/942951/rest-api-error-return-good-practices
 */

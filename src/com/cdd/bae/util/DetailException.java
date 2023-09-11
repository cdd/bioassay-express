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

package com.cdd.bae.util;

import java.util.*;

/*
	Custom exception for handling problems with the configuration file.
	'getMessage' contains high-level description of exception. 'getDetails' to get more details.
*/
 
public class DetailException extends Exception
{
	private final List<String> details;

	public DetailException(String message)
	{
		this(message, new ArrayList<>(), null);
	}
	public DetailException(String message, String detail)
	{
		this(message, Arrays.asList(detail), null);
	}
	public DetailException(String message, List<String> details)
	{
		this(message, details, null);
	}
	public DetailException(String message, String detail, Throwable cause)
	{
		this(message, Arrays.asList(detail), cause);
	}
	public DetailException(String message, List<String> details, Throwable cause)
	{
		super(message, cause);
		this.details = details;
	}
	public List<String> getDetails()
	{
		return this.details;
	}
}

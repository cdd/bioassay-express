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

public class JSONSchemaValidatorException extends DetailException
{
	public JSONSchemaValidatorException(String message)
	{
		this(message, new ArrayList<>(), null);
	}
	public JSONSchemaValidatorException(String message, String detail)
	{
		this(message, Arrays.asList(detail), null);
	}
	public JSONSchemaValidatorException(String message, List<String> details)
	{
		this(message, details, null);
	}
	public JSONSchemaValidatorException(String message, Throwable cause)
	{
		super(message, new ArrayList<>(), cause);
	}
	public JSONSchemaValidatorException(String message, String detail, Throwable cause)
	{
		super(message, detail, cause);
	}
	public JSONSchemaValidatorException(String message, List<String> details, Throwable cause)
	{
		super(message, details, cause);
	}
}

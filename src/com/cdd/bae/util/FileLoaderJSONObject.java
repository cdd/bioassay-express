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

import java.io.*;

import org.json.*;

/*
	Load/reload watched JSON file with object as root element.
*/

public class FileLoaderJSONObject extends FileLoaderJSON<JSONObject>
{
	public FileLoaderJSONObject(File file, String schemaDefinition) throws JSONSchemaValidatorException
	{
		super(file, schemaDefinition);
	}
}


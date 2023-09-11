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

import com.cdd.bae.config.*;
import com.cdd.bae.util.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.apache.commons.collections4.*;
import org.apache.commons.lang3.*;
import org.apache.commons.text.StringEscapeUtils;
import org.bson.*;

/*
	Functionality that is otherwise verbosely inconvenient for BSON datastructures.
*/

public class BSONUtil
{
	public static List<?> toList(String[] array)
	{
		if (array == null) return null;
		return Arrays.asList(array);
	}
	
	public static String[] fromList(List<?> list)
	{
		if (list == null) return null;
		String[] array = new String[list.size()];
		for (int n = 0; n < array.length; n++) array[n] = (String)list.get(n);
		return array;	
	}
	
	public static String[] getStringArray(Document doc, String key)
	{
		return fromList((List<?>)doc.get(key));
	}
}

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

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.tasks.*;
import com.cdd.bao.util.*;

import org.apache.commons.lang3.*;

/*
	Vault maintenance commands
*/

public class VaultMaintenance
{
	private VaultMaintenance()
	{
	}

	public static void execute(String[] options)
	{
		if (options.length == 0)
		{
			printHelp();
			return;
		}

		if (options[0].equals("reloadstructures")) reloadStructures();
		else if (options[0].equals("reloadassaytitle")) reloadAssayTitle();
	}

	public static void printHelp()
	{
		Util.writeln("Vault maintenance commands");
		Util.writeln("    general maintenance of content imported from Vault");
		Util.writeln("Options:");
		Util.writeln("    reloadstructures: Set all empty structures imported from Vault to null to trigger reload; requires restart of server.");
		Util.writeln("    reloadassaytitle: Add the protocolName as an annotation if not set.");
	}

	public static void reloadStructures()
	{
		Util.writeln("Replace empty strings with null for all Vault molecules");
		Common.getDataStore().compound().resetCompoundsEmptyVaultMol();
		Util.writeln("Done. Restart tomcat server to trigger reload of structures from CDD Vault");
	}

	public static void reloadAssayTitle()
	{
		DataStore store = Common.getDataStore();
		VaultProtocols vault = new VaultProtocols();

		for (long currentVaultID : vault.module.vaultIDList)
		{
			vault.processVaultProtocols(currentVaultID, (vaultID, protocol) ->
				{
					if (!protocol.has("name")) return false;

					String uniqueID = Identifier.VAULT_PREFIX + protocol.getLong("id");
					DataObject.Assay assay = store.assay().getAssayFromUniqueID(uniqueID);
					if (assay == null) return false;

					String title = protocol.getString("name");
					Util.writeln(uniqueID + ": " + title);
					assay.textLabels = ArrayUtils.add(assay.textLabels, new DataObject.TextLabel(AssayUtil.URI_ASSAYTITLE, title));
					store.assay().submitAssay(assay);
					return false;
				});
		}
	}
}

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

namespace BioAssayExpress /* BOF */ {

/*
	Front page panel insert to request specific AID codes from PubChem. These will be passed back into the underlying task,
	to be imported into the uncurated part of the assay landscape.
*/

export class PageRequestPubChem
{
	private textArea:JQuery;
	private divResult:JQuery;

	constructor(private mainBlock:JQuery)
	{
		mainBlock.css('background-color', '#F8FCFF');

		let divTitle = $('<div/>').appendTo(mainBlock).css({'font-weight': 'bold'});
		divTitle.text('PubChem Assay Identifiers');

		let divLine = $('<div/>').appendTo(mainBlock);
		this.textArea = $('<textarea rows="1"/>').appendTo(divLine);
		this.textArea.css({'width': '100%', 'border': '1px solid #CCD9E8', 'border-radius': '3px'});

		this.divResult = $('<div/>').appendTo(mainBlock);

		let divButtons = $('<div/>').appendTo(mainBlock).css({'text-align': 'right'});
		let btnRequest = $('<button class="btn btn-action">Request</button>').appendTo(divButtons);
		btnRequest.click(() => this.actionRequest().then());
	}

	// ------------ private methods ------------

	private async actionRequest():Promise<void>
	{
		let txt = this.textArea.val() as string;
		let bits = txt.trim().split(/[\s\,\;]+/);
		let assayAIDList:number[] = [];
		for (let aid of bits) assayAIDList.push(parseInt(aid));

		let params = {'assayAIDList': assayAIDList};
		let result = await asyncREST('REST/extern/RequestPubChemAssay', params);

		let {addedAIDList, ignoredAIDList} = result;
		let msg = '';
		if (addedAIDList.length == 0)
			msg += 'No new assays requested. ';
		else
			msg += 'New assays requested: ' + addedAIDList.join(', ') + '. ';
		if (ignoredAIDList.length > 0)
			msg += 'Assays already present: ' + ignoredAIDList.join(', ') + '. ';
		this.divResult.text(msg);

		this.textArea.val('');
	}
}

/* EOF */ }

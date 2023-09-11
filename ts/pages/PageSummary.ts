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
	Supporting functionality for the summary report.
*/

export class PageSummary
{
	private tdCounts:Record<string, JQuery> = {};
	private tallyRoster:string[] = [];

	constructor(private templates:TemplateSummary[])
	{
	}

	// assembles the page from scratch
	public buildContent():void
	{
		let main = $('#main');
		main.empty();

		main.append('<h1>Counts</h1>');

		let table = $('<table style="margin: 0;"></table>').appendTo($('<p style="margin-top: 1em;"></p>').appendTo(main));
		let pushRoster = (token:string, title:string):void =>
		{
			let tr = $('<tr></tr>').appendTo(table);
			let tdTitle = $('<td></td>').appendTo(tr);
			tdTitle.css('font-weight', 'bold');
			tdTitle.css('white-space', 'nowrap');
			tdTitle.text(title);
			let tdResult = $('<td></td>').appendTo(tr);
			for (let td of [tdTitle, tdResult])
			{
				td.css('border', '1px solid black');
				td.css('background-color', '#FFFFFF');
				td.css('text-align', 'left');
				td.css('padding', '0.5em');
				td.css('margin', '0');
			}

			this.tdCounts[token] = tdResult;
			this.tallyRoster.push(token);
		};

		pushRoster('numAssays', 'Total # of assays');
		pushRoster('curatedAssays', 'Curated assays');
		pushRoster('assaysWithoutFP', 'Assays missing fingerprints');
		pushRoster('nlpFingerprints', 'Natural language fingerprints');
		pushRoster('nlpModels', 'Natural language models');
		pushRoster('corrModels', 'Correlation models');
		//pushRoster('assaysWithMeasurements', 'Assays with measurements');
		pushRoster('numMeasurements', 'Total # measurements');
		pushRoster('numCompounds', 'Total # compounds');
		pushRoster('compoundsWithStructures', 'Compounds with structures');

		this.grabNextTally();
	}

	// ------------ private methods ------------

	private grabNextTally():void
	{
		if (this.tallyRoster.length == 0)
		{
			// !! proceed to next thing...
			return;
		}

		let params = {'tokens': [this.tallyRoster.shift()]};
		callREST('REST/TallyStats', params,
			(data:Record<string, any>) =>
			{
				for (let token in data)
				{
					let val = data[token];
					if (val == null) val = '?';
					this.tdCounts[token].text(val.toString());
				}
				this.grabNextTally();
			});
	}
}

/* EOF */ }

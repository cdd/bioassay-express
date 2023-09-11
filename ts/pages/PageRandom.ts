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
	Supporting functionality for the random assay selection page.
*/

export class PageRandom
{
	constructor(private divParent:JQuery)
	{
	}

	// assembles the page from scratch
	public build():void
	{
		let params =
		{
			'numAssays': 10,
			'curated': false,
			'blank': true
		};
		callREST('REST/PickRandomAssay', params,
			(assays:AssayDefinition[]) => this.applyAssays(assays));
	}

	// reload content (not the first time)
	public reloadAssays():void
	{
		this.build(); // (same, for now)
	}

	// ------------ private methods ------------

	// received list of assays, so define them
	private applyAssays(assays:AssayDefinition[]):void
	{
		this.divParent.empty();

		let table = $('<table></table>').appendTo(this.divParent);

		let tr = $('<tr></tr>').appendTo(table);
		tr.append('<th style="text-align: left; padding-right: 1em;">PubChem</th>');
		tr.append('<th style="text-align: left;">Description</th>');

		for (let n = 0; n < assays.length; n++)
		{
			tr = $('<tr class="assayContainer" assayId="' + assays[n].assayID + '" assayIndex="' + n + '"></tr>').appendTo(table);

			let tdView = $('<td style="padding-right: 1em;" class="assayLinkContainer" id="assayLinkContainer_' + assays[n].assayID + '"></td>').appendTo(tr);
			let linkView = $('<a target="_blank"></a>').appendTo(tdView);
			linkView.attr('href', restBaseURL + '/assign.jsp?assayID=' + assays[n].assayID);

			let [src, id] = UniqueIdentifier.parseKey(assays[n].uniqueID);
			if (src)
				linkView.text(src.name + ' ' + id);
			else
				linkView.text('#' + assays[n].assayID);

			tdView.append('&nbsp;\u{21F2}');

			/*if (assays[n].pubchemSource)
			{
				tdView.append('<br>');
				($('<i></i>').appendTo(tdView)).text(assays[n].pubchemSource);
			}*/

			let tdText = $('<td style="padding-bottom: 1em;" class="assayDataContainer"  id="assayDataContainer_' + assays[n].assayID + '"></td>').appendTo(tr);
			let html = escapeHTML(orBlank(assays[n].text).trim());
			html = html.replace(/\n+/g, '<br/>&nbsp;&nbsp;');
			tdText.html(html);

			for (let annot of assays[n].annotations)
			{
				let para = $('<p style="padding: 0.5em 0 0 0; margin: 0;"></p>').appendTo(tdText);

				let blkProp = $('<font></font>').appendTo(para);
				blkProp.css('background-color', '#E6EDF2');
				blkProp.css('border-radius', 5);
				blkProp.css('border', '1px solid black');
				blkProp.css('padding', '0.3em');
				blkProp.css('cursor', 'pointer');
				blkProp.append($(Popover.displayOntologyProp(annot).elHTML));

				para.append('&nbsp;');

				let blkValue = $('<font></font>').appendTo(para);
				blkValue.css('background-color', '#D6DDE2');
				blkValue.css('border-radius', 5);
				blkValue.css('border', '1px solid black');
				blkValue.css('padding', '0.3em');
				blkValue.css('cursor', 'pointer');
				blkValue.append($(Popover.displayOntologyValue(annot).elHTML));
			}

			for (let td of [tdView, tdText])
			{
				td.css('text-align', 'left');
				td.css('vertical-align', 'top');
				td.css('border-top', '1px solid #E6EDF2');
			}
		}
	}
}

/* EOF */ }

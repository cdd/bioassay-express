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
	Recent curation - display list of recently curated assays
*/

export interface InlineCurationSummary
{
	assayID:number;
	uniqueID:string;
	curationTime:number;
	shortText:string;
	numAnnots:number;
	userName:string;
}

interface CurationSummary
{
	all:InlineCurationSummary[];
	curator?:InlineCurationSummary[];
	holding?:InlineCurationSummary[];
}

export class RecentCuration
{
	public populateRecentlyCurated(domParent:JQuery, curationSummary:CurationSummary):void
	{
		Authentication.hookAuthentChanged = () =>
		{
			let params:any = {'maxNum': 8};
			if (Authentication.currentSession()) params.curatorID = Authentication.currentSession().curatorID;
			domParent.text('Loading...');
			callREST('REST/GetRecentCuration', params, (result:any) =>
			{
				domParent.empty();
				this.populateRecentlyCurated(domParent, result);
			});
		};

		let assays = curationSummary.all;
		if (assays.length == 0)
		{
			$('<h2></h2>').appendTo(domParent).css({'white-space': 'nowrap'}).text('Recently Curated');
			domParent.append('Nothing to show.');
			return;
		}

		let seenAssayIDs = new Set();
		let pos = 0;
		if (curationSummary.holding)
		{
			this.recentlyCuratedList(domParent, 'Your Current Work', curationSummary.holding, pos++, this.recentHoldingbay.bind(this));
			curationSummary.holding.forEach((a) => seenAssayIDs.add(a.assayID));
		}
		if (curationSummary.curator)
		{
			let list = curationSummary.curator.filter((a) => !seenAssayIDs.has(a.assayID));
			this.recentlyCuratedList(domParent, 'Your Recent Submissions', list, pos++, this.renderCuratedAssay.bind(this));
			curationSummary.curator.forEach((a) => seenAssayIDs.add(a.assayID));
		}
		let list = curationSummary.all.filter((a) => !seenAssayIDs.has(a.assayID));
		this.recentlyCuratedList(domParent, 'All Recently Curated', list, pos++, this.renderCuratedAssay.bind(this));
	}

	// ------------ private methods ------------

	private recentlyCuratedList(domParent:JQuery, header:string, assays:InlineCurationSummary[], linePos:number,
								renderEntry:(panel:JQuery, assay:InlineCurationSummary) => void):void
	{
		if (assays.length == 0) return;
		let heading = $('<h2/>').appendTo(domParent).css({'white-space': 'nowrap'});
		heading.text(header);
		if (linePos > 0) heading.css('margin-top', '1em');

		let container = $('<div/>').appendTo(domParent);
		container.css({'display': 'flex', 'flex-direction': 'column', 'align-items': 'stretch'});
		let assayStyle = {};
		for (let assay of assays)
		{
			let panel = $('<div></div>').appendTo(container).css(assayStyle);
			renderEntry(panel, assay);
			assayStyle = {...assayStyle, 'border-top': '1px solid #E6EDF2'};
		}
	}

	private renderCuratedAssay(container:JQuery, assay:InlineCurationSummary):void
	{
		let adate = $('<a></a>').appendTo(container);
		adate.attr('href', assay.uniqueID ?
				restBaseURL + '/assign.jsp?uniqueID=' + encodeURIComponent(assay.uniqueID) :
				restBaseURL + '/assign.jsp?assayID=' + assay.assayID);
		adate.text(this.formatDate(assay.curationTime));
		container.append(': ');

		let [src, id] = UniqueIdentifier.parseKey(assay.uniqueID);
		if (src && id)
		{
			 $('<b></b>').appendTo(container).text(src.shortName + ' ' + id);
			container.append(' ');
		}
		$('<i></i>').appendTo(container).text(assay.shortText);

		let num = assay.numAnnots;
		if (assay.userName)
			container.append(` (${assay.userName})`);
		else
			container.append(` (${num} annotation${this.pluralize(num, 's')})`);
	}

	private recentHoldingbay(container:JQuery, holding:InlineCurationSummary):void
	{
		let adate = $('<a></a>').appendTo(container);
		adate.attr('href',
				holding.uniqueID ? restBaseURL + '/assign.jsp?uniqueID=' + encodeURIComponent(holding.uniqueID) :
				holding.assayID ? restBaseURL + '/assign.jsp?assayID=' + holding.assayID :
				restBaseURL + '/holding.jsp');
		adate.text(this.formatDate(holding.curationTime));
		container.append(': ');

		let [src, id] = UniqueIdentifier.parseKey(holding.uniqueID);
		if (src && id)
		{
			$('<b></b>').appendTo(container).text(src.shortName + ' ' + id);
			container.append(' ');
		}
		$('<i></i>').appendTo(container).text(holding.shortText);
		let num = holding.numAnnots;
		container.append(` (${num} change${this.pluralize(num, 's')})`);
	}

	private pluralize(n:number, suffix:string):string
	{
		return n == 1 ? '' : suffix;
	}

	private formatDate(curationTime:number):string
	{
		const fmtNumber = (n:number):string => (n < 10 ? '0' : '') + n;
		let time = new Date(curationTime);
		return `${time.getFullYear()}-${fmtNumber(time.getMonth() + 1)}-${fmtNumber(time.getDate())}`;
	}

}

/* EOF */ }

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
	Supporting functionality for the curated assays page.
*/

interface AssayInformation
{
	assayID:number;
	uniqueID:string;
	curationTime:number;
	fairnessScore:number; // null unless requested
	description?:JQuery;
	row?:JQuery;
}

enum LimitType
{
	AllCurated = '',
	AbsenceTerms = 'absenceterms',
	Blanks = 'blanks',
	OutOfSchema = 'outschema',
	FAIRness = 'fairness',
}

export class PageCurated
{
	private fetchWatermark = 0;
	private currentSubset = LimitType.AllCurated;

	private assayIDList:number[] = [];
	private assayInformation:Map<number, AssayInformation> = new Map();

	private pagesize = 100;
	private pages:number[][] = [];
	private currentPage = 0;

	private roster:number[] = [];
	private tdPages:JQuery;
	private mainArea:JQuery;

	constructor()
	{
	}

	// assembles the page from scratch
	public populateContent(domParent:JQuery):void
	{
		const header = $('<div/>').appendTo(domParent);
		header.css({'display': 'flex', 'justify-content': 'space-between'});

		// create the category buttons
		const BUTTONS:[string, LimitType][] =
		[
			['All Curated', LimitType.AllCurated],
			['Absence Terms', LimitType.AbsenceTerms],
			['Blanks', LimitType.Blanks],
			['Out of Schema', LimitType.OutOfSchema],
			['FAIRness', LimitType.FAIRness],
		];
		let div = $('<div class="btn-group" data-toggle="buttons"/>').appendTo(header);
		for (const [label, subset] of BUTTONS)
		{
			const lbl = $('<label class="btn btn-radio"/>').appendTo(div);
			lbl.append(label);
			lbl.click(() =>
			{
				div.children().removeClass('active');
				lbl.addClass('active');
				this.changeSubset(subset);
			});
		}
		div.children().first().addClass('active');

		// location for page switching
		this.tdPages = $('<div/>').appendTo(header);

		this.mainArea = $('<div id="mainContainer"/>').appendTo(domParent);

		this.rebuildContent();
	}

	// ------------ private methods ------------

	// recreate the content; called at init, and whenever the parameters change
	private rebuildContent():void
	{
		this.fetchWatermark++;
		this.mainArea.text('Loading...');

		let params:any = {};
		if ([LimitType.AbsenceTerms, LimitType.Blanks, LimitType.OutOfSchema].includes(this.currentSubset))
			params.anomaly = this.currentSubset;
		if (this.currentSubset == LimitType.FAIRness) params.withFAIRness = true;

		callREST('REST/ListCuratedAssays', params,
			(data:any) => this.receivedList(data),
			() => alert('Fetching curated list failed.'));
	}

	// continue onward having received the list of applicable assays
	private receivedList(data:any):void
	{
		// convert data to list of objects
		this.assayIDList = data.assayIDList;
		let fairness:number[] = data.fairness; // can be null
		for (let n = 0; n < data.assayIDList.length; n++)
		{
			let assayInformation = this.assayInformation.get(data.assayIDList[n]);
			if (assayInformation)
			{
				assayInformation.row = null;
				if (fairness) assayInformation.fairnessScore = fairness[n];
				continue;
			}
			this.assayInformation.set(data.assayIDList[n],
				{
					'assayID': data.assayIDList[n],
					'uniqueID': data.uniqueIDList[n],
					'curationTime': data.curationTime[n],
					'fairnessScore': fairness ? fairness[n] : null,
				});
		}

		// descending sort by curation time/score
		this.assayIDList.sort((a, b) =>
		{
			let ai1 = this.assayInformation.get(a), ai2 = this.assayInformation.get(b);
			let v1 = fairness ? ai1.fairnessScore : ai1.curationTime;
			let v2 = fairness ? ai2.fairnessScore : ai2.curationTime;
			return v2 - v1;
		});

		// determine assayIDs for which we need to retrieve description
		this.roster = this.assayIDList.filter((id) =>
			this.assayInformation.get(id).description == null || this.assayInformation.get(id).description.text() == '');

		this.currentPage = 0;
		this.pages = [];
		for (let n = 0; n < this.assayIDList.length; n += this.pagesize)
			this.pages.push(this.assayIDList.slice(n, n + this.pagesize));

		this.renderData();
	}

	private renderData():void
	{
		this.mainArea.empty();

		// sort arrays by curation time
		let paraHead = $('<p id="headerContainer"/>').appendTo(this.mainArea);
		let paraTable = $('<p id="tableContainer"/>').appendTo(this.mainArea);
		let table = $('<table/>').appendTo(paraTable);

		let withFAIRness = this.currentSubset == LimitType.FAIRness;

		let tr = $('<tr/>').appendTo(table);
		$('<th>Date</th>').appendTo(tr).css('text-align', 'center');
		if (withFAIRness) $('<th>FAIRness</th>').appendTo(tr).css('text-align', 'center');
		$('<th>Description</th>').appendTo(tr).css({'text-align': 'left', 'padding-left': '0.5em'});
		$('<th>Action</th>').appendTo(tr).css('text-align', 'center');
		$('<th>Origin</th>').appendTo(tr).css('text-align', 'center');

		paraHead.html('Number of curated assays: <b>' + this.assayIDList.length + '</b>');

		for (let n = 0; n < this.assayIDList.length; n++)
		{
			let assayID = this.assayIDList[n];
			let assayInformation = this.assayInformation.get(assayID);
			if (assayInformation.row)
			{
				table.append(assayInformation.row);
				continue;
			}

			let dateHTML = this.formatDate(assayInformation.curationTime);
			let uniqueID = assayInformation.uniqueID;
			let [src, id] = UniqueIdentifier.parseKey(uniqueID);

			let refHTML = '';
			if (src)
			{
				let url = UniqueIdentifier.composeRefURL(uniqueID), label = src.shortName + ' ' + id;
				if (url)
					refHTML = `<a href="${url}" target="_blank">${label}</a>`;
				else
					refHTML = label;
			}
			let viewHTML = `<a href="assign.jsp?assayID=${assayID}" target="_blank">View</a>&nbsp;\u{21F2}`;

			const cellCSS = {'border-top': '1px solid #E6EDF2', 'white-space': 'nowrap',
					'vertical-align': 'top', 'text-align': 'center', 'padding-left': '0.5em', 'padding-right': '0.5em'};
			tr = $('<tr/>').appendTo(table);

			$('<td/>').appendTo(tr).css(cellCSS).html(dateHTML);

			if (withFAIRness)
			{
				let txt = (assayInformation.fairnessScore * 100).toFixed(1) + '%';
				$('<td/>').appendTo(tr).css(cellCSS).html(txt);
			}

			if (assayInformation.description)
				assayInformation.description.appendTo(tr);
			else
				assayInformation.description = $('<td/>').appendTo(tr).css({...cellCSS, 'white-space': 'normal', 'text-align': 'left'});

			$('<td/>').appendTo(tr).css({...cellCSS, 'vertical-align': 'bottom'}).html(viewHTML);
			$('<td/>').appendTo(tr).css({...cellCSS, 'vertical-align': 'bottom'}).html(refHTML);

			assayInformation.row = tr;
		}

		this.configurePages();

		// this.fillNextRoster(this.fetchWatermark);
	}

	private formatDate(curationTime:number):string
	{
		const fmtNumber = (n:number):string => (n < 10 ? '0' : '') + n;
		let time = new Date(curationTime);
		return `${time.getFullYear()}-${fmtNumber(time.getMonth() + 1)}-${fmtNumber(time.getDate())}`;
	}

	// cycle through the list-to-grab, in the desired order, should any remain
	private fillNextRoster(watermark:number):void
	{
		if (this.roster.length == 0) return;
		if (watermark != this.fetchWatermark) return; // flipped to a different phase

		// retrieve descriptions in current page first
		const currentPage = this.pages[this.currentPage];
		let assayID = this.roster.find((assayID) => currentPage.includes(assayID));
		// fall back to next ID in this.roster if missing
		assayID = assayID || this.roster[0];
		this.roster = this.roster.filter((e) => e != assayID);

		callREST('REST/GetAssay', {'assayID': assayID},
			(assay:AssayDefinition) =>
			{
				if (watermark != this.fetchWatermark) return; // flipped to a different phase

				let txt = deriveAssayName(assay, 300);
				this.assayInformation.get(assayID).description.text(txt);

				this.fillNextRoster(watermark);
			});
	}

	private renderPagerControl():void
	{
		// pager control: if more than one page, fill with indicators
		this.tdPages.empty();
		if (this.pages.length <= 1) return;

		this.tdPages.append(' ');
		let blk = $('<span>Prev</span>').appendTo(this.tdPages);
		if (this.currentPage > 0)
		{
			blk.addClass('pseudoLink');
			blk.click(() => this.gotoPage(this.currentPage - 1));
		}
		else blk.css('visibility', 'hidden');

		for (let n = 0; n < this.pages.length; n++)
		{
			this.tdPages.append(' ');
			let blk = $(`<span>${n + 1}</span>`).appendTo(this.tdPages);
			if (n != this.currentPage)
			{
				blk.addClass('pseudoLink');
				const pagenum = n;
				blk.click(() => this.gotoPage(pagenum));
			}
		}

		this.tdPages.append(' ');
		blk = $('<span>Next</span>').appendTo(this.tdPages);
		if (this.currentPage < this.pages.length - 1)
		{
			blk.addClass('pseudoLink');
			blk.click(() => this.gotoPage(this.currentPage + 1));
		}
		else blk.css('visibility', 'hidden');
	}

	// refills the list of pages in the appropriate section, noting which one is currently active
	private configurePages():void
	{
		this.renderPagerControl();

		const currentAssayIDs = this.pages[this.currentPage];
		for (const assayInfo of this.assayInformation.values())
		{
			const vis = currentAssayIDs.includes(assayInfo.assayID);
			assayInfo.row.css('display', vis ? 'table-row' : 'none');
		}

		// retrieve assays a few pages around the current page
		this.roster = [...currentAssayIDs];
		for (const page of this.pages.slice(Math.max(this.currentPage - 2, 0), this.currentPage + 2))
			this.roster = this.roster.concat(...page);
		this.roster = this.roster.filter((id) => this.assayInformation.get(id).description.text() == '');

		this.fillNextRoster(this.fetchWatermark);
	}

	// switches the page, and updates accordingly
	private gotoPage(pagenum:number):void
	{
		this.currentPage = pagenum;
		this.configurePages();
	}

	// change which "subset" of curated assays is currently being displayed
	private changeSubset(subset:LimitType):void
	{
		if (this.currentSubset == subset) return;
		this.currentSubset = subset;
		this.rebuildContent();
	}
}

/* EOF */ }

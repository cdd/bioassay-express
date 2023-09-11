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
	Displays all provisional term requests.
*/

export interface OntoloBridge
{
	name:string;
	description:string;
	baseURL:string;
}

declare var ONTOLOBRIDGES:OntoloBridge[];

const enum PageTermRequestColumn
{
	ID = 0,
	Parent,
	URI,
	Label,
	Proposer,
	TimeCreated,
	TimeModified,
	Role,
	Status,
	Count,
	Description,
	Explanation,
	_Count // total #
}

const COLUMN_HEADINGS = // must match indexing used by enum above
[
	'ID',
	'Parent',
	'URI',
	'Label',
	'Proposer',
	'Created',
	'Modified',
	'Role',
	'Status',
	'Count',
	'Description',
	'Explanation'
];

const STATUS_ORDERING = // order of escalation, for sorting purposes
[
	'unsubmitted',
	'submitted',
	'rejected',
	'expired',
	'underReview',
	'accepted',
	'internal',
];

export class PageTermRequests
{
	private termRequests:ProvisionalTerm[] = [];
	private columns:PageTermRequestColumn[] = Vec.identity0(PageTermRequestColumn._Count);
	private sortPrio:PageTermRequestColumn[] = [];

	private divTable:JQuery;
	private filter:JQuery;

	constructor(private parent:JQuery)
	{
	}

	// assembles the page from scratch
	public build():void
	{
		callREST('REST/GetTermRequests', {},
			(data:any) =>
			{
				this.termRequests = data.list || [];
				this.populateContent();
			});
	}

	// ------------ private methods ------------

	private populateContent():void
	{
		if (this.termRequests.length <= 0)
		{
			$('#bannerMessage').empty();
			$('#bannerMessage').text('There are no term requests.');
			$('#bannerAlert').css('display', 'block');
			this.parent.empty();
			return;
		}
		$('#bannerAlert').css('display', 'none');
		this.renderFilter();
		this.divTable = $('<div/>').appendTo(this.parent);
		this.rebuildTerms();
	}

	private renderFilter():void
	{
		let div = $('<div/>').appendTo(this.parent);
		div.addClass('search-box');
		div.css({'margin': '1em 0em', 'float': 'none'});
		this.filter = $('<input type="search" placeholder="filter term requests"/>').appendTo(div);
		this.filter.css('margin-right', '1em');
		this.filter.keyup(() => this.rebuildTerms());
	}

	private matchesFilter(req: ProvisionalTerm):boolean
	{
		let txt = this.filter.val().toString().toLocaleLowerCase().trim();
		if (txt == '') return true;
		if (req.label.toLocaleLowerCase().includes(txt)) return true;
		if (req.proposerID && req.proposerID.toLocaleLowerCase().includes(txt)) return true;
		if (req.proposerName && req.proposerName.toLocaleLowerCase().includes(txt)) return true;
		if (req.parentLabel.toLocaleLowerCase().includes(txt)) return true;
		if (collapsePrefix(req.uri).toLocaleLowerCase().includes(txt)) return true;
		if (req.description.toLocaleLowerCase().includes(txt)) return true;
		if (req.explanation.toLocaleLowerCase().includes(txt)) return true;
		return false;
	}

	// recreate the term table
	private rebuildTerms():void
	{
		this.divTable.empty();

		let table = $('<table/>').appendTo(this.divTable);

		// render column headings
		let tr = $('<tr/>').appendTo(table).css('background-color', '#C0C0C0');
		for (let col of this.columns)
		{
			let td = $('<th/>').appendTo(tr);
			td.css({'padding': '0.2em 0.5em 0.2em 0.5em'});
			let span = $('<span/>').appendTo(td);
			span.css({'font-weight': 'bold', 'cursor': 'pointer', 'margin': '-0.3em', 'padding': '0.3em'});
			span.addClass('hoverUnderline');
			if (this.sortPrio.indexOf(col) >= 0) span.css({'background-color': '#E6EDF2', 'border-radius': '5px'});
			span.click(() =>
			{
				let i = this.sortPrio.indexOf(col);
				if (i < 0) this.sortPrio.push(col); else this.sortPrio.splice(i, 1);
				this.rebuildTerms();
			});
			span.text(COLUMN_HEADINGS[col]);
		}
		tr.append('<th/>');

		let formatDate = (time:number):string =>
		{
			if (time == null) return '';
			let date = new Date();
			date.setTime(time);
			let d = date.getDate(), m = date.getMonth() + 1, y = date.getFullYear();
			return y + '-' + (m < 10 ? '0' : '') + m + '-' + (d < 10 ? '0' : '') + d;
		};

		let truncateText = (td:JQuery, txt:string):void =>
		{
			td.css('white-space', 'normal');
			if (txt.length > 20)
			{
				td.text(txt.substring(0, 15));
				let ellipsis = $('<span>...</span>').appendTo(td);
				ellipsis.css({'background-color': '#F0F0F0', 'border-radius': '5px'});
				Popover.hover(domLegacy(ellipsis), null, escapeHTML(txt));
			}
			else td.text(txt);
		};

		// render rows
		let parity = false;
		const NOWRAP = [PageTermRequestColumn.Proposer, PageTermRequestColumn.TimeCreated, PageTermRequestColumn.TimeModified];
		for (let row of this.sortedOrder())
		{
			let req = this.termRequests[row];
			if (!this.matchesFilter(req)) continue;
			// console.log(req);

			parity = !parity;
			tr = $('<tr/>').appendTo(table);
			tr.css('background-color', parity ? '#F0F0F0' : '#F8F8F8');

			for (let col of this.columns)
			{
				let td = $('<td/>').appendTo(tr);
				td.css({'padding': '0.2em 0.5em 0.2em 0.5em', 'vertical-align': 'top'});
				if (NOWRAP.indexOf(col) >= 0) td.css('white-space', 'nowrap');

				if (col == PageTermRequestColumn.ID) td.text(req.provisionalID.toString());
				else if (col == PageTermRequestColumn.Parent)
				{
					let abbrev = collapsePrefix(req.parentURI);
					if (req.parentLabel)
					{
						let span = $('<span/>').appendTo(td);
						span.text(req.parentLabel);
						Popover.hover(domLegacy(span), null, escapeHTML(abbrev));
					}
					else td.html(`<i>${escapeHTML(abbrev)}</i>`);
				}
				else if (col == PageTermRequestColumn.URI) td.text(collapsePrefix(req.uri)); // style??
				else if (col == PageTermRequestColumn.Label) td.text(req.label);
				else if (col == PageTermRequestColumn.Proposer)
				{
					if (req.proposerName)
						td.text(req.proposerName);
					else
						td.html(`<i>${req.proposerID}</i>`);
				}
				else if (col == PageTermRequestColumn.TimeCreated) td.text(formatDate(req.createdTime));
				else if (col == PageTermRequestColumn.TimeModified) td.text(formatDate(req.createdTime));
				else if (col == PageTermRequestColumn.Role) td.text(req.role == null ? 'none' : req.role);
				else if (col == PageTermRequestColumn.Count) td.text(req.countAssays + req.countHoldings);
				else if (col == PageTermRequestColumn.Status) td.text(req.bridgeStatus);
				else if (col == PageTermRequestColumn.Description) truncateText(td, req.description);
				else if (col == PageTermRequestColumn.Explanation) truncateText(td, req.explanation);
			}

			// edit button
			let canEdit = Authentication.canAccessOntoloBridge();
			if (!canEdit)
			{
				let session = Authentication.currentSession();
				canEdit = session && session.curatorID == req.proposerID;
			}
			if (canEdit)
			{
				let td = $('<td/>').appendTo(tr);
				let btnEdit = $('<button class="btn btn-xs btn-normal"><span class="glyphicon glyphicon-edit"/></button>').appendTo(td);
				btnEdit.css('margin-right', '0.2em');
				btnEdit.click(() =>
				{
					new EditProvisionalDialog(req, () => this.reloadContent()).show();
				});
			}
		}
	}

	// returns the rows in order of current sort preferences
	private sortedOrder():number[]
	{
		let compare = (col:PageTermRequestColumn, req1:ProvisionalTerm, req2:ProvisionalTerm):number =>
		{
			if (col == PageTermRequestColumn.ID) return req1.provisionalID - req2.provisionalID;
			else if (col == PageTermRequestColumn.Parent)
			{
				let s1 = req1.parentLabel ? '_' + req1.parentLabel : '~' + req1.parentURI;
				let s2 = req2.parentLabel ? '_' + req2.parentLabel : '~' + req2.parentURI;
				return s1.localeCompare(s2);
			}
			else if (col == PageTermRequestColumn.URI) return req1.uri.localeCompare(req2.uri);
			else if (col == PageTermRequestColumn.Label) return req1.label.localeCompare(req2.label);
			else if (col == PageTermRequestColumn.Proposer)
			{
				let s1 = req1.proposerName ? '_' + req1.proposerName : '~' + req1.proposerID;
				let s2 = req2.proposerName ? '_' + req2.proposerName : '~' + req2.proposerID;
				return s1.localeCompare(s2);
			}
			else if (col == PageTermRequestColumn.TimeCreated) return (req1.createdTime || 0) - (req2.createdTime || 0);
			else if (col == PageTermRequestColumn.TimeModified) return (req1.modifiedTime || 0) - (req2.modifiedTime || 0);
			else if (col == PageTermRequestColumn.Role)
			{
				const ORDER = [null, ProvisionalTermRole.Public, ProvisionalTermRole.Private, ProvisionalTermRole.Deprecated];
				return ORDER.indexOf(req1.role) - ORDER.indexOf(req2.role);
			}
			else if (col == PageTermRequestColumn.Count)
			{
				let i1 = req1.countAssays + req1.countHoldings, i2 = req2.countAssays + req2.countHoldings;
				return i1 - i2;
			}
			else if (col == PageTermRequestColumn.Status)
			{
				let i1 = STATUS_ORDERING.indexOf(req1.bridgeStatus), i2 = STATUS_ORDERING.indexOf(req2.bridgeStatus);
				return i1 - i2;
			}
			else if (col == PageTermRequestColumn.Description) return (req1.description || '').localeCompare(req2.description || '');
			else if (col == PageTermRequestColumn.Explanation) return (req1.explanation || '').localeCompare(req2.explanation || '');
			return 0;
		};

		let idx = Vec.identity0(this.termRequests.length);
		if (this.sortPrio.length > 0) idx = idx.sort((i1, i2) =>
		{
			for (let col of this.sortPrio)
			{
				let cmp = compare(col, this.termRequests[i1], this.termRequests[i2]);
				if (cmp != 0) return cmp;
			}
			return 0;
		});
		return idx;
	}

	// content is thought to have changed, so grab it all back from the server and redraw
	private reloadContent():void
	{
		callREST('REST/GetTermRequests', {},
			(data:any) =>
			{
				this.termRequests = data.list || [];
				this.rebuildTerms();
			});
	}
}

/* EOF */ }

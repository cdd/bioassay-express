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
	Supporting functionality for the search page.
*/

export class PageSearch
{
	private chosenTemplate = 0;
	private schemaMap:Record<string, SchemaSummary> = {};

	private divTemplateChoice:JQuery;
	private btnSearch:JQuery;
	private divProgress:JQuery;
	private divKeyword:JQuery;

	private keyword:GlobalKeywordBar = null;

	// for search progress/results
	private progress:ProgressBar = null;
	private searchRunning = false;
	private searchRoster:number[] = []; // identifiers waiting to be submitted
	private searchPos:number;
	private searchSize:number;
	private searchResults:SearchResult[] = []; // holds the sorted list of results obtained and displayed
	private searchMatches:number; // total number of matches (only the top N are stored in the results)
	private searchTimeStarted:number;
	private results = new ResultAssayList();
	private propGrid:PropertyGrid = null; // display of the individual assays
	private chkShowHierarchy:JQuery = null;
	private showAssignments:SchemaAssignment[] = null; // assignments to show in property grid (null means all)

	private PROGR_W = 500;
	private PROGR_H = 20;

	constructor(schemaURI:string, private terms:AssayAnnotation[], private availableTemplates:SchemaSummary[], private anyCompounds:boolean)
	{
		for (let n = 0; n < availableTemplates.length; n++) if (schemaURI == availableTemplates[n].schemaURI) {this.chosenTemplate = n; break;}

		this.results.renderQuery = (div:JQuery) => this.rebuildQuery(div);
		if (this.anyCompounds) this.results.showCompounds = () => this.showCompounds();
		this.results.showAnalysis = (pageFN:string) => this.showAnalysis(pageFN);
	}

	// get the ball rolling
	public render():void
	{
		let divAction = $('<div></div>').appendTo($('#actionLine'));
		divAction.css({'display': 'flex', 'width': '100%', 'justify-content': 'flex-start', 'align-items': 'center'});

		this.divTemplateChoice = $('<div></div>').appendTo(divAction).css({'margin-right': '0.5em'});

		this.btnSearch = $('<button class="btn btn-action"></button>').appendTo(divAction).css({'margin-right': '0.5em'});
		this.btnSearch.append('<span class="glyphicon glyphicon-play-circle"></span> Start Search');
		this.btnSearch.click(() => this.startSearch());

		this.divProgress = $('<div></div>').appendTo(divAction).css({'flex-grow': '1', 'display': 'none'});
		this.divKeyword = $('<div></div>').appendTo(divAction).css({'flex-grow': '1'});

		this.populateTemplate();

		this.results.setCurrentTemplate(this.availableTemplates[this.chosenTemplate]);
		this.results.changedAssaySelection = () =>
		{
			if (this.propGrid) this.propGrid.changeSelection(this.results.selectedAssayIDList());
		};
		this.results.render($('#searchResults'));

		this.populateKeywords();

		let schemaURI = this.availableTemplates[this.chosenTemplate].schemaURI;
		callREST('REST/DescribeSchema', {'schemaURI': schemaURI},
			(schema:SchemaSummary) =>
			{
				this.schemaMap[schemaURI] = schema;
				this.fillSchema();
				this.fillTerms();

				// zap the params, for they be ugly
				window.history.pushState('object or string', 'Title', 'search.jsp');
			},
			() => alert('Fetching schema failed'));
	}

	// initiates the search
	public startSearch():void
	{
		if (this.terms.length == 0) return;

		this.propGrid = null;
		$('#property_grid').empty();

		this.searchRunning = true;
		this.btnSearch.prop('disabled', true);

		this.divProgress.css('display', 'block');
		this.divKeyword.css('display', 'none');

		this.divProgress.empty();
		this.progress = new ProgressBar(this.PROGR_W, this.PROGR_H, () => this.stopSearch());
		this.progress.render(this.divProgress);

		this.searchPos = 0;
		this.searchSize = 0;
		this.searchResults = [];
		this.searchMatches = 0;
		this.searchTimeStarted = new Date().getTime();
		this.results.replaceSearchResults(this.searchResults);
		this.updateSearchProgress();

		let params =
		{
			'anomaly': 'nonblanks', // ask for assays with curated flag set, and at least one annotation
			'withAssayID': true,
			'withUniqueID': false,
			'withCurationTime': false,
		};
		callREST('REST/ListCuratedAssays', params,
			(data:any) =>
			{
				this.searchRoster = data.assayIDList;
				this.searchPos = 0;
				this.searchSize = this.searchRoster.length;

				this.searchNextBatch();
			},
			() => alert('Fetching search identifiers failed'));
	}

	// stops the search
	public stopSearch():void
	{
		// handy to know how long/what happened
		/*let endTime = new Date().getTime();
		console.log('Search stopped: fetched ' + this.searchPos + ' of ' +  this.searchSize);
		console.log('    time taken: ' + ((endTime -  this.searchTimeStarted) / 1000) + ' sec');*/

		this.searchRunning = false;
		this.searchRoster = [];
		this.btnSearch.prop('disabled', this.terms.length == 0);
		this.results.replaceSearchResults(this.searchResults);

		this.divProgress.css('display', 'none');
		this.divKeyword.css('display', 'block');
	}

	// setup the ability to pick terms by typing in stuff
	public populateKeywords():void
	{
		let schemaURI = this.availableTemplates[this.chosenTemplate].schemaURI;
		this.keyword = new GlobalKeywordBar(schemaURI, (annot:AssayAnnotation) => this.appendAnnotation(annot));
		this.keyword.changeAnnotations(this.terms);

		this.divKeyword.empty();
		let divBox = $('<div class="keyword-box"></div>').appendTo(this.divKeyword);
		let input = $('<input type="text" id="keywordentry" value="" placeholder="keyword search"></input>').appendTo(divBox);
		this.keyword.install(input);
	}

	// ------------ private methods ------------

	// displays the current template as a title, but also a clickable button when there are choices available
	private populateTemplate():void
	{
		this.divTemplateChoice.empty();

		let tr = $('<tr></tr>').appendTo($('<table></table>').appendTo(this.divTemplateChoice));
		let td = $('<td></td>').appendTo(tr);

		if (this.availableTemplates.length <= 1)
		{
			let div = $('<div></div>').appendTo(td);
			let span = $('<span></span>').appendTo(div);
			span.addClass('strongblue');
			span.css('border-radius', '4px');
			span.css('color', 'white');
			span.css('padding', '0.5em');
			span.css('white-space', 'nowrap');
			span.text(this.availableTemplates[0].name);
		}
		else
		{
			let div = $('<div class="btn-group"></div>').appendTo(td);

			let btn = $('<button type="button" class="form-control btn btn-action dropdown-toggle" data-toggle="dropdown"></button>').appendTo(div);
			btn.text(this.availableTemplates[this.chosenTemplate].name);
			btn.append(' <span class="caret"></span>');

			let ul = $('<ul class="dropdown-menu" role="menu"></ul>').appendTo(div);
			for (let n = 0; n < this.availableTemplates.length; n++)
			{
				let li = $('<li></li>').appendTo(ul);
				let t = this.availableTemplates[n];

				let href = $('<a href="#"></a>').appendTo(li);
				if (n == this.chosenTemplate)
				{
					let b = $('<b></b>').appendTo(href);
					b.text(t.name);
					href.click(() => false);
				}
				else
				{
					href.text(t.name);
					const idx = n;
					href.click(() => this.changeTemplate(idx));
				}
			}
		}
	}

	// change current template
	private changeTemplate(idx:number):void
	{
		let schemaURI = this.availableTemplates[idx].schemaURI;
		if (!this.schemaMap[schemaURI])
		{
			callREST('REST/DescribeSchema', {'schemaURI': schemaURI},
				(schema:SchemaSummary) =>
				{
					this.schemaMap[schemaURI] = schema;
					this.changeTemplate(idx);
				});
			return;
		}

		this.chosenTemplate = idx;
		this.populateTemplate();
		this.populateKeywords();
		this.fillSchema();
		this.fillTerms();
	}

	// populate the list of terms
	private fillTerms():void
	{
		let termsUsed = $('#termsUsed');

		this.btnSearch.prop('disabled', this.searchRunning || this.terms.length == 0);

		termsUsed.empty();
		if (this.terms.length == 0)
		{
			termsUsed.text('None selected.');
			return;
		}

		for (let n = 0; n < this.terms.length; n++)
		{
			const idx = n;
			let term = this.terms[n];

			let para = $('<p style="padding: 0.5em 0 0 0;"></p>').appendTo(termsUsed);

			if (term.groupLabel) for (let i = term.groupLabel.length - 1; i >= 0; i--)
			{
				let blkGroup = $('<font></font>').appendTo(para);
				blkGroup.css('background', 'white');
				blkGroup.css('border-radius', 5);
				blkGroup.css('border', '1px solid black');
				blkGroup.css('padding', '0.3em');
				blkGroup.text(term.groupLabel[i]);

				para.append('&nbsp;');
			}

			let blkProp = $('<font></font>').appendTo(para);
			blkProp.addClass('weakblue');
			blkProp.css({'border-radius': '5px', 'border': '1px solid black', 'padding': '0.3em'});
			blkProp.append($(Popover.displayOntologyProp(term).elHTML));

			para.append('&nbsp;');

			let blkValue = $('<font></font>').appendTo(para);
			blkValue.addClass('lightgray');
			blkValue.css({'border-radius': '5px', 'border': '1px solid black', 'padding': '0.3em'});
			blkValue.append($(Popover.displayOntologyValue(term).elHTML));

			para.append('&nbsp;');

			let btnReject = $('<button class="btn btn-xs btn-action"></button>').appendTo(para);
			btnReject.append($('<span class="glyphicon glyphicon-remove" style=\"height: 1.2em;"></span></button>'));
			btnReject.click(() => this.removeTerm(idx));
		}
	}

	// populate the schema, which shows the available assignments
	private fillSchema():void
	{
		let termsAvail = $('#termsAvail');
		termsAvail.empty();

		let schema = this.schemaMap[this.availableTemplates[this.chosenTemplate].schemaURI];
		let hier = new SchemaHierarchy(schema);
		let divTree:JQuery[] = [], toggleTree:JQuery[] = [], parentIdx:number[] = [];

		let renderAssn = (pidx:number, depth:number, assn:SchemaHierarchyAssignment):void =>
		{
			let isSemantic = assn.suggestions == SuggestionType.Full || assn.suggestions == SuggestionType.Disabled;

			let para = $('<p></p>').appendTo(termsAvail);
			para.css('margin-left', (depth + 0.3) + 'em');
			para.css('margin-bottom', '0.2em');

			let divBar = $('<div class="flexbar"></div>').appendTo(para);
			divBar.css('justify-content', 'space-between');

			let divLabel = $('<div></div>').appendTo(divBar);
			divLabel.append($(Popover.displayOntologyAssn(assn).elHTML));

			let divButtons = $('<div></div>').appendTo(divBar);
			if (isSemantic)
			{
				let btnList = $('<button class="btn btn-normal"></button>').appendTo(divButtons);
				btnList.html('<span class="glyphicon glyphicon-list" style="height: 1.2em;"></span> List');
				divButtons.append('&nbsp;');
				let btnTree = $('<button class="btn btn-normal"></button>').appendTo(divButtons);
				btnTree.html('<span class="glyphicon glyphicon-tree-conifer" style="height: 1.2em;"></span> Tree');
				let idx = assn.assnidx;
				btnList.click(() => this.openList(idx));
				btnTree.click(() => this.openTree(idx));
			}
			else
			{
				divButtons.css('text-align', 'right');
				divButtons.html('<i style="color: #C0C0C0">n/a</i>');
			}

			if (pidx >= 0)
			{
				divTree.push(para);
				toggleTree.push(null);
				parentIdx.push(pidx);
			}
		};
		let renderGroup = (pidx:number, depth:number, group:SchemaHierarchyGroup):void =>
		{
			if (group.parent != null)
			{
				let div = $('<div></div>').appendTo(termsAvail);

				let blk = $('<span></span>').appendTo(div);
				blk.css('margin-left', depth + 'em');

				let toggle = $('<span></span>').appendTo(blk);
				blk.append('&nbsp;');

				let font = $('<font style="text-decoration: underline; top: 0.2em; position: relative;"></font>').appendTo(blk);
				font.text(group.name);

				if (group.descr)
				{
					let tip = group.descr;
					if (group.groupURI)
					{
						tip = 'Abbrev: <i>' + collapsePrefix(group.groupURI) + '</i><br>' + tip;
					}
					Popover.hover(domLegacy(font), null, tip);
				}

				// add the necessary information to create the toggle open/closed tree
				divTree.push(div);
				toggleTree.push(toggle);
				parentIdx.push(pidx);
			}

			let subpidx = divTree.length - 1;
			for (let assn of group.assignments) renderAssn(subpidx, depth + 1, assn);
			for (let subgrp of group.subGroups) renderGroup(subpidx, depth + 1, subgrp);
		};

		renderGroup(-1, 0, hier.root);
		new CollapsingList(divTree, toggleTree, parentIdx).manufacture();
	}

	// lookup a new term, using the list view
	private openList(idx:number):void
	{
		let schema = this.schemaMap[this.availableTemplates[this.chosenTemplate].schemaURI];
		let assn = schema.assignments[idx];
		const settings:PickTermSettings =
		{
			'schema': this.schemaMap[this.availableTemplates[this.chosenTemplate].schemaURI],
			'annotations': this.terms,
		};
		let dlg = new PickTermDialog(assn, settings, (annot:AssayAnnotation):void => this.appendAnnotation(annot));
		dlg.showList('Lookup <b>' + assn.name + '</b>');
	}

	// lookup a new term, using the tree view
	private openTree(idx:number):void
	{
		let schema = this.schemaMap[this.availableTemplates[this.chosenTemplate].schemaURI];
		let assn = schema.assignments[idx];
		const settings:PickTermSettings =
		{
			'schema': this.schemaMap[this.availableTemplates[this.chosenTemplate].schemaURI],
			'annotations': this.terms,
		};
		let dlg = new PickTermDialog(assn, settings, (annot:AssayAnnotation):void => this.appendAnnotation(annot));
		dlg.showTree('Lookup <b>' + assn.name + '</b>');
	}

	// remove a term from the selected list
	private removeTerm(idx:number):void
	{
		this.terms.splice(idx, 1);
		this.fillTerms();
	}

	private appendAnnotation(annot:AssayAnnotation):void
	{
		for (let look of this.terms)
			if (samePropGroupNest(annot.propURI, annot.groupNest, look.propURI, look.groupNest) && annot.valueURI == look.valueURI) return;
		this.terms.push(annot);
		this.fillTerms();
	}

	// submits another batch for evaluation
	private searchNextBatch():void
	{
		if (this.searchRoster.length == 0)
		{
			this.stopSearch();
			this.results.finaliseSearchResults();
			this.rebuildGroupButtons();
			this.rebuildPropGrid();
			$('html, body').animate({'scrollTop': $('#searchResults').offset().top}, 500);
			return;
		}

		// fetch size: want a small value initially because transporting assay results takes bandwidth, and wait to see some results as soon
		// as possible; crank it up later because as the search proceeds, the threshold gets higher and filters out more
		let fetchSize = Math.min(250, Math.max(100, this.searchResults.length));
		let batch = this.searchRoster.splice(0, fetchSize);
		this.searchPos += batch.length;

		let threshold = 0;
		if (this.searchResults.length > 0) threshold = this.searchResults[this.searchResults.length - 1].similarity;

		let params =
		{
			'assayIDList': batch,
			'search': this.terms,
			'threshold': threshold,
			'countCompounds': true,
			'translitPreviews': true
		};
		callREST('REST/Search', params,
			(data:any) =>
			{
				this.updateSearchProgress();
				this.contemplateSearchResults(data.results);
				this.searchNextBatch();
			},
			() => console.log('Fetching next batch of search results failed'));
	}

	// given a batch of search results, adapt the dynamic list of things-thus-far
	private contemplateSearchResults(results:SearchResult[]):void
	{
		const MAX_SHOW = 100;

		let changed = false;

		this.searchMatches += results.length;

		for (let n = 0; n < results.length; n++) if (results[n].similarity > 0)
		{
			const len = this.searchResults.length;
			if (len < MAX_SHOW || results[n].similarity > this.searchResults[len - 1].similarity)
			{
				this.searchResults.push(results[n]);

				// only one item can be out of order, so this is O(N)
				for (let p = 0; p < this.searchResults.length - 1;)
				{
					if (this.searchResults[p].similarity < this.searchResults[p + 1].similarity)
					{
						let sr = this.searchResults[p];
						this.searchResults[p] = this.searchResults[p + 1];
						this.searchResults[p + 1] = sr;
						if (p > 0) p--;
					}
					else p++;
				}

				while (this.searchResults.length > MAX_SHOW) this.searchResults.pop();
				changed = true;
			}
		}

		if (changed) this.results.replaceSearchResults(this.searchResults);
	}

	// update progress indicator for searching
	private updateSearchProgress():void
	{
		if (this.progress) this.progress.setProgress(this.searchPos / Math.max(this.searchSize, 1));
	}

	// create the measurement table, showing selected compounds
	private showCompounds():void
	{
		let para = $('#measurement_table');
		para.empty();

		//let subset = Vec.maskIdx(this.results.selectedMask());
		let assayIDList = this.results.selectedAssayIDList();
		if (assayIDList.length == 0)
		{
			alert('Select at least one result first.');
			return;
		}

		para.text('Loading measurements...');
		para[0].scrollIntoView();

		let mdata = new MeasureData(assayIDList);
		mdata.obtainCompounds(() =>
		{
			if (mdata.compounds.length > 0)
			{
				let mtable = new MeasureTable(mdata);
				mtable.render(para);
			}
			else
			{
				para.empty();
				// (consider putting a note to say that there's nothing available?)
			}
		});
	}

	// bring up a new tab that displays the selected assays vs. compounds
	private showAnalysis(pageFN:string):void
	{
		let mask = this.results.selectedMask(), anyChecked = Vec.anyTrue(mask);

		let url = getBaseURL() + '/' + pageFN + '?assays=';

		let first = true;
		for (let n = 0; n < this.searchResults.length; n++) if (!anyChecked || mask[n])
		{
			if (first) first = false; else url += '%2C'; // (comma)
			url += this.searchResults[n].assayID;
		}

		let schema = this.availableTemplates[this.chosenTemplate];
		url += '&schema=' + encodeURIComponent(schema.schemaURI);

		window.open(url, '_blank');
	}

	// transfer the list of assays onto the clipboard in tab-separated-value format, suitable for pasting into a spreadsheet
	private copyAssays():void
	{
		let content = 'AssayID\tUniqueID\tOrigin\tBAE\n';

		let base = getBaseURL() + '/assign.jsp?assayID=';

		for (let n = 0; n < this.searchResults.length; n++)
		{
			let result = this.searchResults[n];
			let assayID = result.assayID, uid = result.uniqueID == null ? '' : result.uniqueID;
			let refURL = UniqueIdentifier.composeRefURL(result.uniqueID);
			let baeURL = base + assayID;
			content += assayID + '\t' + uid + '\t' + refURL + '\t' + baeURL + '\n';
		}

		copyToClipboard(content);
	}

	// draws the query content, if desired
	private rebuildQuery(div:JQuery):string[]
	{
		/* ... right now there's no query syntax defined for similarity searching, but that might change...
		const RESERVED = '\\()=,;*@!';
		function escape(str:string):string
		{
			for (let n = str.length - 1; n >= 0; n--)
				if (RESERVED.indexOf(str.charAt(n)) >= 0) str = str.substring(0, n) + '\\' + str.substring(n);
			return str;
		}

		let qstr1 = '';
		for (let selseq of this.select)
		{
			if (selseq.terms.length == 0) continue;
			if (qstr1.length > 0) qstr1 += ';';
			qstr1 += '(';
			let propURI = this.categories[selseq.category].uri;
			qstr1 += escape(collapsePrefix(propURI)) + '=';
			for (let n = 0; n < selseq.terms.length; n++)
			{
				if (n > 0) qstr1 += ',';
				let valueURI = selseq.terms[n];
				qstr1 += '@' + escape(collapsePrefix(valueURI));
			}
			qstr1 += ')';
		}*/

		let mask = this.results.selectedMask(), anyChecked = Vec.anyTrue(mask);
		let assayList = this.results.getAssays();

		let qstr2 = '[', qstr3 = '[', first = true;
		for (let n = 0; n < this.searchResults.length; n++)
		{
			if (!mask[n] && anyChecked) continue;
			let result = this.searchResults[n];
			let assayID = result.assayID, uid = result.uniqueID;
			if (!first)
			{
				qstr2 += ',';
				qstr3 += ',';
			}
			else first = false;
			qstr2 += uid ? 'UID/' + uid : assayID;
			qstr3 += assayID;
		}
		qstr2 += ']';
		qstr3 += ']';

		let result = [null, qstr2, qstr3];
		if (!div) return result;

		let para = $('<p></p>').appendTo(div);
		para.css('text-align', 'right');

		/* ... reactivate this when there's a similarity syntax
		para.append('Filter Query: ');
		let line = $('<input type="text" size="40" readonly onClick="this.select();"></input>').appendTo(para);
		line.attr('value', qstr1);

		para.append('<br>');*/

		para.append('Unique ID: ');
		let line = $('<input type="text" size="40" readonly onClick="this.select();"></input>').appendTo(para);
		line.attr('value', qstr2);

		para.append('<br>');

		para.append('Assay ID: ');
		line = $('<input type="text" size="40" readonly onClick="this.select();"></input>').appendTo(para);
		line.attr('value', qstr3);

		return result;
	}

	// inserts a row of buttons for controlling how order/display of assignments & results works
	private rebuildGroupButtons():void
	{
		let paraButtons = $('#group_selection');
		paraButtons.empty();
		if (Vec.arrayLength(this.results.assayList) == 0) return;

	 	let table = $('<table></table>').appendTo(paraButtons);
		let tr = $('<tr></tr>').appendTo(table);
		let tdButtons = $('<td valign="middle"></td>').appendTo(tr);

		let btnAssn = $('<button class="btn btn-normal">Show Assignments</button>').appendTo(tdButtons);
		Popover.hover(domLegacy(btnAssn), null, 'Select which assignments to show in the grid.');
		btnAssn.click(() => this.actionAssignments());

		tdButtons.append(' ');

		this.chkShowHierarchy = $('<input type="checkbox" id="checkShowHierarchy"></input>').appendTo(tdButtons);
		this.chkShowHierarchy.prop('checked', true);
		this.chkShowHierarchy.change(() =>
		{
			if (this.propGrid) this.rebuildPropGrid();
		});
		tdButtons.append(' <label for="checkShowHierarchy" style="font-weight: normal;">Show Hierarchy</label>');
	}

	// rebuilds the property grid, or displays a loading sign if assays are still being acquired
	private rebuildPropGrid():void
	{
		let paraPropGrid = $('#property_grid');
		paraPropGrid.empty();
		this.propGrid = null;
		if (this.searchRunning) return;

		if (this.searchResults.length == 0) return;

		let assayList:AssayDefinition[] = [];
		for (let n = 0; n < this.searchResults.length; n++)
		{
			// NOTE: forced invalid case of SearchResult -> AssayDefinition; there are enough common fields that the
			// JavaScript output will function OK, but this is a hack that could be fixed by giving both classes a common ancestor
			let assay = (this.searchResults[n] as any) as AssayDefinition;
			assayList.push(assay);
		}
		let schemaURI = this.availableTemplates[this.chosenTemplate].schemaURI;
		this.propGrid = new PropertyGrid(schemaURI, assayList);
		this.propGrid.showAssignments = this.showAssignments;
		this.propGrid.selectedAssayIDList = this.results.selectedAssayIDList();
		this.propGrid.clickedAssay = (assayID:number) =>
		{
			this.results.toggleAssaySelection(assayID);
			if (this.propGrid) this.propGrid.changeSelection(this.results.selectedAssayIDList());
		};

		let gotAssn = new Set<string>();
		for (let annot of this.terms)
		{
			let key = keyPropGroup(annot.propURI, annot.groupNest);
			if (gotAssn.has(key)) continue;
			gotAssn.add(key);
			let assn:any = {'propURI': annot.propURI, 'groupNest': annot.groupNest};
			this.propGrid.queryAssignments.push(assn);
		}
		this.propGrid.queryAnnotations = this.terms;

		this.propGrid.showHierarchy = this.chkShowHierarchy ? this.chkShowHierarchy.prop('checked') : true;
		this.propGrid.render(paraPropGrid);
	}

	// allows assignment categories to be switched on or off
	private actionAssignments():void
	{
		let schema = this.availableTemplates[this.chosenTemplate];
		if (!this.showAssignments) this.showAssignments = schema.assignments.slice(0);

		let dlg = new PickAssignmentDialog(schema, true);
		dlg.picked = this.showAssignments;
		dlg.showAllNone = true;
		dlg.callbackDone = (assnlist:SchemaAssignment[]) =>
		{
			this.showAssignments = assnlist;
			this.rebuildPropGrid();
		};
		dlg.show();
	}
}

/* EOF */ }

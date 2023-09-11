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
	Supporting functionality for the explore page.
*/

interface SelectNode
{
	depth:number;
	parent:number;
	uri:string;
	name:string;
	abbrev?:string;
	descr?:string;
	count?:number; // number of assays still in the game which have this term
	childCount?:number; // as above, plus the counts for all child nodes
	totalCount?:number; // number of assays with this term, without accounting for cumulative elimination
	curatedCount?:number; // as for totalCount, but only those which have the curated flag set
}

interface SelectLiteral
{
	label:string;
	count:number;
}

interface SelectSequence
{
	category:number; // index into assignment options for currently selected template (or one of CATEGORY_*)
	terms:string[]; // opt-in list of value URIs
	keyword:string; // optional keyword selector (a string query, used on various substrates)
	tree:SelectNode[]; // selected branches, as obtained from the server
	literals:SelectLiteral[]; // previously used literals
	textInput?:JQuery; // accompanying searchbox
	btnInput?:JQuery; // accompanying button
	collapseState?:CollapsingList; // checked open/closed state of widgets
}

export abstract class ExplorerDelegate
{
	public actionClickedAssignment:(assn:SchemaAssignment) => void;
	public actionDoubleClickedAssignment:(assn:SchemaAssignment) => void;
}

export class PageExplore extends ExplorerDelegate
{
	private paraResults:JQuery;
	private paraGroupSelect:JQuery;
	private propGridContainer:JQuery;

	private chosenTemplate = 0;
	private select:SelectSequence[] = [];
	private withUncurated:boolean;
	private results = new ResultAssayList();
	private propGrid:PropertyGrid = null; // display of the individual assays
	private spanWait:JQuery;
	private watermarkFetch = 0;

	// control display of assignments in property grid
	private selectedAssignments:SchemaAssignment[] = null; // assignments selected/deselected by user
	private propGridOptions = new PropertyGridOptions();

	private compoundsDisplay:JQuery = null;
	public keyword:GlobalKeywordBar = null;

	private CATEGORY_KEYWORD = -3;
	private CATEGORY_FULLTEXT = -2;
	private CATEGORY_IDENTIFIER = -1;
	private NUM_SPECIALCAT = 3;

	private MAX_SHOW_LITERALS_COUNT = 25;

	constructor(private availableTemplates:SchemaSummary[], private anyCompounds:boolean)
	{
		super();

		this.actionClickedAssignment = (assn:SchemaAssignment):void => this.clickedAssignment(assn);
		this.actionDoubleClickedAssignment = (assn:SchemaAssignment):void => this.doubleClickedAssignment(assn);

		this.paraResults = $('#results');
		this.paraGroupSelect = $('#group_selection');
		this.propGridContainer = $('#property_grid');

		let chkUncurated = $('#chk_uncurated');
		this.withUncurated = chkUncurated.prop('checked');
		chkUncurated.change(() =>
		{
			this.withUncurated = chkUncurated.prop('checked');
			this.requestTreeTerms();
			this.rebuildSelection();
		});

		this.compoundsDisplay = $('#measurement_table');
		this.results.changedAssaySelection = () =>
		{
			if (this.propGrid) this.propGrid.changeSelection(this.results.selectedAssayIDList());
		};
		this.results.changedAssayVisibility = () =>
		{
			if (this.propGrid) this.onPropGridOptionsChanged();
		};
		this.results.render(this.paraResults);
	}

	// ask if we can replace this with a json object
	public initializeSelection(searchQuery:string[], initTemplateURI:string[], identifier:string[], keyword:string[], fullText:string[]):void
	{
		this.rebuildSelection();

		if (initTemplateURI.length > 0)
		{
			for (let i = 0; i < this.availableTemplates.length; i++) if (this.availableTemplates[i].schemaURI == initTemplateURI[0])
			{
				this.chosenTemplate = i;
				break;
			}
		}

		let schema = this.availableTemplates[this.chosenTemplate];

		if (identifier.length > 0)
		{
			let cat:SelectSequence =
			{
				'category': this.CATEGORY_IDENTIFIER,
				'terms': [],
				'literals': [],
				'keyword': identifier[0],
				'tree': []
			};
			this.select.push(cat);
		}

		if (keyword.length > 0)
		{
			let cat:SelectSequence =
			{
				'category': this.CATEGORY_KEYWORD,
				'terms': [],
				'literals': [],
				'keyword': keyword[0],
				'tree': []
			};
			this.select.push(cat);
		}

		if (fullText.length > 0)
		{
			let cat:SelectSequence =
			{
				'category': this.CATEGORY_FULLTEXT,
				'terms': [],
				'literals': [],
				'keyword': fullText[0],
				'tree': []
			};
			this.select.push(cat);
		}

		if (searchQuery.length > 0)
		{
			let searchString = searchQuery[0]; // TODO: get rid of this hard coding
			let qAssay:QueryAssay = new QueryAssay(searchString);
			let propURIKeys = qAssay.getPropURIKeys();

			for (let i = 0; i < propURIKeys.length; i++)
			{
				let category = propURIKeys[i];

				let categoryLocatorString:string = null; // maps from URI to category location
				let categoryIndex:number = null;

				for (let j = 0; j < schema.assignments.length; j++)
				{
					let assignment = schema.assignments[j];
					if (assignment.propURI == category)
					{
						categoryLocatorString = assignment.locator;
						categoryIndex = j;
					}
				}

				if (categoryLocatorString != null)
				{
					// map between the categoryLocatorString and the category

					let cat:SelectSequence =
					{
						'category': categoryIndex,
						'terms': [],
						'literals': [],
						'keyword': '',
						'tree': []
					};

					for (let k = 0; k < qAssay.matchTerms[category].length; k++)
					{
						let uri = qAssay.matchTerms[category][k].uri;
						//let match = qAssay.matchTerms[category][k].match; // TODO -- implement this
						cat.terms.push(uri);
					}

					this.select.push(cat);
				}
			}
		}

		this.rebuildSelection();
		this.requestTreeTerms();
		this.results.render(this.paraResults);
	}

	// recreates the selection table
	public rebuildSelection():void
	{
		this.populateTemplate();
		this.populateQuery();
		this.compoundsDisplay.empty();

		this.results.setCurrentTemplate(this.availableTemplates[this.chosenTemplate]);
		this.results.renderQuery = (div:JQuery) => this.rebuildQuery(div);
		if (this.anyCompounds) this.results.showCompounds = () => this.showCompounds();
		this.results.showAnalysis = (pageFN:string) => this.showAnalysis(pageFN);
		history.replaceState({'id': 'explore.jsp'}, 'Explore Assays', this.sharableLink(false));
	}

	// setup the ability to pick terms by typing in stuff
	public populateKeywords(parent:JQuery):void
	{
		let schemaURI = this.availableTemplates[this.chosenTemplate].schemaURI;
		this.keyword = new GlobalKeywordBar(schemaURI, (annot:AssayAnnotation) => this.appendAnnotation(annot));
		this.keyword.changeAnnotations(this.selectedAnnotations());
		this.keyword.setSelectionTree(this.composeSelectionTerms());
		this.keyword.install(parent);
	}

	public clickedAssignment(assn:SchemaAssignment):void
	{
		// do nothing for now -- implemented for completeness
	}

	public doubleClickedAssignment(assn:SchemaAssignment):void
	{
		this.actionHideCategory(assn);
	}

	// ------------ private methods ------------

	// displays the current template as a title, but also a clickable button when there are choices available
	private populateTemplate():void
	{
		let parent = $('#template_choice');
		parent.empty();

		let tr = $('<tr/>').appendTo($('<table/>').appendTo(parent));
		let td = $('<td/>').appendTo(tr);

		if (this.availableTemplates.length <= 1)
		{
			let div = $('<div/>').appendTo(td);
			let span = $('<span/>').appendTo(div);
			span.addClass('strongblue');
			span.css('display', 'inline-block');
			span.css('border-radius', '4px');
			span.css('color', 'white');
			span.css('padding', '0.3em 0.5em 0.3em 0.5em');
			span.css('white-space', 'nowrap');
			span.text(this.availableTemplates[0].name);
		}
		else
		{
			let div = $('<div class="btn-group"/>').appendTo(td);

			let btn = $('<button type="button" class="form-control btn btn-action dropdown-toggle" data-toggle="dropdown"/>').appendTo(div);
			btn.text(this.availableTemplates[this.chosenTemplate].name);
			btn.append(' <span class="caret"/>');

			let ul = $('<ul class="dropdown-menu" role="menu"/>').appendTo(div);
			for (let n = 0; n < this.availableTemplates.length; n++)
			{
				let li = $('<li/>').appendTo(ul);
				let t = this.availableTemplates[n];

				let href = $('<a href="#"/>').appendTo(li);
				if (n == this.chosenTemplate)
				{
					let b = $('<b/>').appendTo(href);
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

		td = $('<td/>').appendTo(tr);
		this.spanWait = $('<span class="glyphicon glyphicon-hourglass"/>').appendTo(td);
		this.spanWait.css('visibility', 'hidden');
	}

	// recreates the part that shows the partial selection tree
	private populateQuery():void
	{
		let currentFocus = $(':focus');

		$('#info').css('display', this.select.length == 0 ? 'block' : 'none');

		let table = $('#select');
		table.css('width', '100%');
		table.empty();

		let schema = this.availableTemplates[this.chosenTemplate];

		// if the last entry has a term selected, there is an implied blank one after it
		let numsel = this.select.length;
		if (numsel == 0) numsel = 1;
		else if (numsel < schema.assignments.length + this.NUM_SPECIALCAT)
		{
			let last = this.select[numsel - 1];
			if (last.terms.length > 0 || last.category < 0 || last.keyword.length > 0) numsel++;
		}

		for (let n = 0; n < numsel; n++) this.populateOneQuery(n, table);

		currentFocus.focus();

		if (this.keyword) this.keyword.setSelectionTree(this.composeSelectionTerms());
	}
	private populateOneQuery(idx:number, table:JQuery):void
	{
		let schema = this.availableTemplates[this.chosenTemplate];
		let sel = idx < this.select.length ? this.select[idx] : null;

		let tr = $('<tr/>').appendTo(table);
		let td = $('<td/>').appendTo(tr);
		td.css('padding', '0.5em');

		// heading and action button

		let hdrRowContainer = $('<div/>').appendTo(td);
		let hdrRow = $('<div/>').appendTo(hdrRowContainer);
		hdrRow.css('display', 'flex');
		hdrRow.css('flex-wrap', 'nowrap');
		let divHdr1 = $('<div/>').appendTo(hdrRow), divHdr2 = $('<div/>').appendTo(hdrRow);
		divHdr2.css('flex-grow', '1');

		let btnExpandSection = $('<button class="btn btn-xs" style=\"margin: 3px;\"/>').appendTo(divHdr1);
		let chevron = $('<span class="glyphicon glyphicon-chevron-down" style=\"height: 1.2em;"/>').appendTo(btnExpandSection);
		divHdr1.css('padding-right', '1em');
		divHdr1.css('margin-top', '3px');
		divHdr1.append(this.categoryName(idx));
		let hasAssn = sel && sel.category >= 0;
		let btnType = sel ? 'btn-normal' : 'btn-action';

		const makeButton = (label:string, tag:string, eventHandler:() => void, tooltip:string):void =>
		{
			let btnCat = $(`<button class="btn ${btnType}"/>`).appendTo(divHdr2);
			btnCat.css('min-width', '5em');
			$(tag).appendTo(btnCat).text(label);
			btnCat.click(eventHandler);
			Popover.hover(domLegacy(btnCat), null, tooltip);
		};

		if (hasAssn)
		{
			makeButton(schema.assignments[sel.category].name, '<b/>', () => this.categoryDelete(idx),
				'Click to remove this assignment from the criteria.');
		}
		else if (sel && sel.category == this.CATEGORY_KEYWORD)
		{
			makeButton('keyword', '<i/>', () => this.categoryDelete(idx),
				'Click to remove the keyword criteria.');
		}
		else if (sel && sel.category == this.CATEGORY_FULLTEXT)
		{
			makeButton('full text', '<i/>', () => this.categoryDelete(idx),
				'Click to remove the full text criteria.');
		}
		else if (sel && sel.category == this.CATEGORY_IDENTIFIER)
		{
			makeButton('identifier', '<i/>', () => this.categoryDelete(idx),
				'Click to remove the identifier criteria.');
		}
		else
		{
			makeButton('select', '<i/>', () => this.categorySelect(),
				'Click to select a new assignment for the criteria.');
		}

		// text searching terms

		if (sel != null)
		{
			let keywordSearch:JQuery;
			if (sel.category >= 0)
			{
				keywordSearch = $('<div/>').appendTo(hdrRow);
				keywordSearch.css({'flex-grow': '1000', 'padding-left': '1em'});
			}
			else
			{
				keywordSearch = $('<p/>').appendTo(td);
				keywordSearch.css({'flex-grow': '1000', 'padding': '1em 0.5em 0 1em'});
			}

			let searchTextContainer = $('<div/>').appendTo(keywordSearch);
			searchTextContainer.css({'display': 'flex', 'flex-wrap': 'nowrap'});

			let keywordSearchLabel = $('<div/>').appendTo(searchTextContainer);
			keywordSearchLabel.css({'flex-grow': '1', 'margin-top': '3px'});

			if (sel.category == this.CATEGORY_KEYWORD) keywordSearchLabel.text('Search: ');
			else if (sel.category == this.CATEGORY_FULLTEXT) keywordSearchLabel.text('Text: ');
			else if (sel.category == this.CATEGORY_IDENTIFIER) keywordSearchLabel.text('Identifier: ');
			else keywordSearchLabel.text('Keywords: ');

			if (sel.textInput == null) sel.textInput = $('<input type="text" class="text-box" spellcheck="false"/>');
			if (sel.btnInput == null)
			{
				sel.btnInput = $('<button class="btn btn-xs btn-normal"/>');
				sel.btnInput.append('<span class="glyphicon glyphicon-refresh" style=\"height: 1.2em;"/>');
			}

			let searchText = sel.textInput.appendTo(searchTextContainer);
			searchText.attr('placeholder', sel.category ? 'optional keywords' : 'text search keywords');
			searchText.val(sel.keyword);
			searchText.css({'flex-grow': '1', 'margin-left': '10px', 'margin-right': '10px'});

			searchTextContainer.append(' ');
			let btnText = sel.btnInput.appendTo(searchTextContainer);

			searchText.off('keyup');
			searchText.keyup((event:JQueryKeyEventObject) =>
			{
				let keyCode = event.keyCode || event.which;
				sel.keyword = purifyTextPlainInput(searchText.val());
				if (keyCode == KeyCode.Enter) this.requestTreeTerms();
			});
			btnText.off('click');
			btnText.click(() =>
			{
				sel.keyword = purifyTextPlainInput(searchText.val());
				this.requestTreeTerms();
			});
		}

		// tree terms

		if (sel && (sel.tree.length > 0 || sel.literals.length > 0))
		{
			let para = $('<p style="margin-top: 0.5em;"/>').appendTo(td);
			let paraSummary = $('<p style="margin-left:40px; margin-top: 0.5em; display:none;">Summary</p>').appendTo(td);
			let selectedTerms = this.appendTreeTerms(para, idx, sel);
			this.appendLiteralTerms(para, idx, sel);

			// encode the text toggle on/off
			let isSectionOpen = true;
			let setState = ():void =>
			{
				btnExpandSection.toggleClass('btn-action', isSectionOpen);
				btnExpandSection.toggleClass('btn-normal', !isSectionOpen);
				chevron.toggleClass('glyphicon-chevron-down', isSectionOpen);
				chevron.toggleClass('glyphicon-chevron-right', !isSectionOpen);
				para.css('display', isSectionOpen ? 'block' : 'none');
				paraSummary.css('display', isSectionOpen ? 'none' : 'block');
				if (selectedTerms.length > 0) paraSummary.text('Selected Terms: ' + selectedTerms.join(', '));
				else if (sel.textInput.val() != null) paraSummary.text('Selected Item: ' + sel.textInput.val());
				else paraSummary.text('No Terms Selected');

			};
			setState();
			btnExpandSection.click(() =>
			{
				isSectionOpen = !isSectionOpen;
				setState();
			});
		}
	}

	// add just the tree node terms for one of the parameters
	private appendTreeTerms(parent:JQuery, idx:number, sel:SelectSequence):string[]
	{
		let divTree:JQuery[] = [], toggleTree:JQuery[] = [], parentIdx:number[] = [], returnTerms:string[] = [];

		for (let n = 0; n < sel.tree.length; n++)
		{
			const termidx = n;
			let node = n >= 0 ? sel.tree[n] : null;
			const isSelected = sel.terms.indexOf(node.uri) >= 0;

			let div = $('<div/>').appendTo(parent);
			div.css('padding-left', (4 + 2 * node.depth) + 'em');

			let toggle = $('<span/>').appendTo(div);
			div.append('&nbsp;');

			let noteSpan = $('<span style="top: 0.1em; position: relative;"/>').appendTo(div);
			let span = $('<span style="padding: 0.3em;"/>').appendTo(noteSpan);
			let font = $('<font/>').appendTo(span);
			if (isSelected)
			{
				font.css('color', 'white');
				font.css('background-color', 'black');
				returnTerms.push(node.name);
			}
			font.text(node.name);

			if (node.count > 0 || node.uri == SPECIAL_EMPTY || node.uri == SPECIAL_WITHTEXT)
			{
				font.css('font-weight', 'bold');
				font.css('cursor', 'pointer');

				if (node.count > 0)
				{
					let str = ' (';
					if (this.withUncurated) str += node.curatedCount + '/';
					str += node.count;
					if (node.totalCount > node.count) str += '<font style="color: #C0C0C0;"> of ' + node.totalCount + '</font>';
					str += ')';
					noteSpan.append(str);
				}
			}

			if (node.childCount - node.count > 0)
			{
				div.append('&nbsp;');
				let btnAdd = $('<button class="btn btn-xs btn-normal"/>').appendTo(div);
				$('<span class="glyphicon glyphicon-thumbs-up" style=\"height: 1.2em;"/>').appendTo(btnAdd);
				btnAdd.click(() => this.selectAllChildren(idx, termidx));

				div.append('&nbsp;');
				let btnRemove = $('<button class="btn btn-xs btn-normal"/>').appendTo(div);
				$('<span class="glyphicon glyphicon-thumbs-down" style=\"height: 1.2em;"/>').appendTo(btnRemove);
				btnRemove.click(() => this.unselectAllChildren(idx, termidx));
			}

			let title = escapeHTML(collapsePrefix(node.uri)), tiptext = '';

			if (!node.descr) tiptext += Popover.CACHE_DESCR_CODE;
			else tiptext += '<p align="left" style="margin: 0;">' + escapeHTML(node.descr) + '</p>';

			if (node.count > 0)
			{
				tiptext += '<p align="left" style="margin: 0; margin-top: 0.5em;">';
				tiptext += '<i>Used to annotate <u>' + node.count + '</u> assay' + (node.count == 1 ? '' : 's');
				if (this.withUncurated)
				{
					if (node.curatedCount == node.totalCount)
					{
						tiptext += node.totalCount == 1 ? ' (which is curated)' : ' (all of which are curated)';
					}
					else
					{
						tiptext += ' (of which ' + node.curatedCount + ' ' + (node.curatedCount == 1 ? 'is' : 'are') + ' curated)';
					}
				}
				if (node.totalCount > node.count)
				{
					tiptext += ' selected by previous criteria, and <u>' + node.totalCount + '</u> overall.';
				}
				tiptext += '</i>.</p>';
			}

			let schemaURI = this.availableTemplates[this.chosenTemplate].schemaURI;
			Popover.click(domLegacy(span), title, tiptext, {'schemaURI': schemaURI, 'valueURI': node.uri});

			if (node.count > 0 || node.uri == SPECIAL_EMPTY || node.uri == SPECIAL_WITHTEXT)
			{
				font.hover(() => {font.css('text-decoration', 'underline'); font.css('color', isSelected ? '#D0D0D0' : '#1362B3');},
						   () => {font.css('text-decoration', 'none'); font.css('color', isSelected ? 'white' : '#313A44');});
				font.click(() => this.selectTerm(idx, termidx));
			}

			divTree.push(div);
			toggleTree.push(toggle);
			parentIdx.push(node.parent);
		}

		// manufacture the check open/closed boxes; keep the previous open/closed state, if any
		let branchOpen = sel.collapseState && sel.collapseState.branchOpen.length == divTree.length ? sel.collapseState.branchOpen : [];
		let newPartial = !sel.collapseState && sel.terms.length > 0; // special deal: new branch with stuff, starts partially collapsed
		if (newPartial) branchOpen = Vec.booleanArray(true, sel.tree.length);
		sel.collapseState = new CollapsingList(divTree, toggleTree, parentIdx, branchOpen);
		if (newPartial) this.partialBranchCollapse(sel);
		sel.collapseState.manufacture();

		return returnTerms;
	}

	// add literals that were found to be in the database
	private appendLiteralTerms(parent:JQuery, idx:number, sel:SelectSequence):void
	{
		let schema = this.availableTemplates[this.chosenTemplate];
		let isString = sel.category < 0 ? 'string' : schema.assignments[sel.category].suggestions == 'string';

		let showNumberOfItems = sel.literals.length;
		let maxShowItems = this.MAX_SHOW_LITERALS_COUNT;
		let hiddenLiterals = $('<div/>');
		hiddenLiterals.hide();

		if (sel.literals.length > maxShowItems)
		{
			let warningDiv = $('<div/>').appendTo(parent);
			warningDiv.css('padding-left', '4em');
			warningDiv.css('background-color', '#ccc');
			warningDiv.css('border', '1px solid #888');
			warningDiv.css('padding', '10px');
			warningDiv.css('width', '100%');
			warningDiv.css('opacity', '0.8');
			warningDiv.css('text-align', 'center');
			let warningSpan = $('<span style="padding: 0.3em;"</span>').appendTo(warningDiv);
			let divMsg = $('<div/>').appendTo(warningSpan);
			divMsg.text(`Note: There were ${sel.literals.length} matches. Please use the text box above to improve your search results.`);
			let unhideLiteralTerms = $('<button class="btn btn-normal">Show All ' + sel.literals.length + ' Items</button>');
			unhideLiteralTerms.css('margin', '10px');
			unhideLiteralTerms.appendTo(warningSpan);
			Popover.hover(domLegacy(unhideLiteralTerms), null, 'Show All Items');
			unhideLiteralTerms.click(() => { hiddenLiterals.show(); warningDiv.hide(); });
			showNumberOfItems = maxShowItems;
		}

		for (let n = 0; n < showNumberOfItems; n++)
		{
			let lit = sel.literals[n];
			this.appendLiteral(parent, idx, isString, lit);
		}

		if (sel.literals.length > maxShowItems)
		{
			hiddenLiterals.appendTo(parent);
			for (let n = showNumberOfItems; n < sel.literals.length; n++)
			{
				let lit = sel.literals[n];
				this.appendLiteral(hiddenLiterals, idx, isString, lit);
			}
		}
	}

	private appendLiteral(parent:JQuery, idx:number, isString:string | boolean, lit:SelectLiteral):void
	{
		let div = $('<div/>').appendTo(parent);
		div.css('padding-left', '4em');
		let span = $('<span style="padding: 0.3em;"/>').appendTo(div);
		let ital = $('<i/>').appendTo(span);
		if (isString) ital.append('"');
		ital.append(escapeHTML(lit.label));
		if (isString) ital.append('"');
		span.append(' <font style="color: #C0C0C0;">(' + lit.count + ')</font>');

		span.css('cursor', 'pointer');
		span.click(() => this.grabKeywordText(idx, lit.label));
	}

	// returns a suitable name for the next category option
	private categoryName(idx:number):string
	{
		return 'LAYER ' + (idx + 1);
	}

	// given that the list of categories has changed in some way (more/less/different terms) initiate a request to the server
	// to go through and return the applicable terms that should be included in each tree branch
	private requestTreeTerms():void
	{
		if (this.select.length == 0) return;

		let schema = this.availableTemplates[this.chosenTemplate];
		let useTerms = this.composeSelectionTerms();

		this.spanWait.css('visibility', 'visible');
		this.getPropertyGridContainer('Searching for Matching Assays...', true);
		this.results.clearAssays();

		let params =
		{
			'schemaURI': schema.schemaURI,
			'select': useTerms,
			'withUncurated': this.withUncurated
		};
		let watermark = ++this.watermarkFetch;

		callREST('REST/SelectionTree', params,
			(data:any) =>
			{
				if (watermark != this.watermarkFetch) return; // been superceded by another request

				this.spanWait.css('visibility', 'hidden');

				let specialEmpty:SelectNode = {'depth': 0, 'parent': -1, 'uri': SPECIAL_EMPTY, 'name': 'empty'};
				specialEmpty.descr = 'Switch this on to include assays that have no terms for this assignment.';

				let specialWithText:SelectNode = {'depth': 0, 'parent': -1, 'uri': SPECIAL_WITHTEXT, 'name': 'with text'};
				specialWithText.descr = 'Switch this on to include assays with text annotations.';

				for (let n = 0; n < data.treeList.length && n < this.select.length; n++)
				{
					this.select[n].tree = data.treeList[n];
					if (this.select[n].category >= 0)
					{
						for (let node of this.select[n].tree) if (node.parent >= 0) node.parent += 2;
						this.select[n].tree.unshift(specialWithText);
						this.select[n].tree.unshift(specialEmpty);
					}
				}
				for (let n = 0; n < data.literalList.length; n++) if (this.select[n]) this.select[n].literals = data.literalList[n];

				this.results.replaceAssays(data.matchesAssayID, () => {this.onPropGridOptionsChanged();});
				this.rebuildSelection();
			});
	}

	// formulates the current selection tree in preparation for transmission to the API
	private composeSelectionTerms():any[]
	{
		let schema = this.availableTemplates[this.chosenTemplate];

		let useTerms:any[] = [];
		for (let sel of this.select)
		{
			let obj:Record<string, any> =
			{
				'valueURIList': sel.terms,
				'keywordSelector': sel.keyword
			};
			if (sel.category == this.CATEGORY_FULLTEXT) obj.propURI = 'FULLTEXT';
			else if (sel.category == this.CATEGORY_KEYWORD) obj.propURI = 'KEYWORD';
			else if (sel.category == this.CATEGORY_IDENTIFIER) obj.propURI = 'IDENTIFIER';
			else
			{
				obj.propURI = schema.assignments[sel.category].propURI;
				obj.groupNest = schema.assignments[sel.category].groupNest;
			}
			useTerms.push(obj);
		}
		return useTerms;
	}

	// brings up a list of categories thus far unselected
	private categorySelect():void
	{
		let schema = this.availableTemplates[this.chosenTemplate];
		let dlg = new PickAssignmentDialog(schema, false);
		dlg.title = 'Select Category';

		// see which specials & assignments need to be excluded
		let specials = new Set<number>();
		for (let sel of this.select)
		{
			if (sel.category >= 0)
				dlg.picked.push(schema.assignments[sel.category]);
			else
				specials.add(sel.category);
		}

		// add the prepended DOM objects for special selection
		let makeSpecial = (catidx:number, label:string, tip:string):void =>
		{
			if (specials.has(catidx)) return;
			let span = $('<span/>');
			span.css({'cursor': 'pointer', 'padding-left': '0.3em'});
			let ital = $('<i/>').appendTo(span);
			ital.text(label);

			Popover.hover(domLegacy(span), null, tip);
			span.hover(() => {span.css('text-decoration', 'underline'); span.css('color', '#1362B3');},
					   () => {span.css('text-decoration', 'none'); span.css('color', 'black');});
			span.click(() =>
			{
				this.appendCategory(catidx);
				dlg.hide();
			});

			dlg.domPrepend.push(span);
		};
		makeSpecial(this.CATEGORY_KEYWORD, 'keyword',
			'Select to provide keywords that will be searched across the labels for all assignments.');
		makeSpecial(this.CATEGORY_FULLTEXT, 'full text',
			'Select to search the full text descriptions that accompany each assay.');
		makeSpecial(this.CATEGORY_IDENTIFIER, 'identifier',
			'Select to filter the kinds of identifiers, or search their values explicitly.');

		dlg.callbackDone = (assnlist:SchemaAssignment[]) =>
		{
			if (assnlist.length == 0) return;
			let catidx = -1, key = keyPropGroup(assnlist[0].propURI, assnlist[0].groupNest);
			for (let n = 0; n < schema.assignments.length; n++)
			{
				let assnKey = keyPropGroup(schema.assignments[n].propURI, schema.assignments[n].groupNest);
				if (key == assnKey) {catidx = n; break;}
			}
			if (catidx < 0) return;

			this.appendCategory(catidx);
		};
		dlg.show();
	}

	// adds a new category to the end and syncs display elements
	private appendCategory(catidx:number):void
	{
		for (let seq of this.select) this.partialBranchCollapse(seq);

		let cat:SelectSequence =
		{
			'category': catidx,
			'terms': [],
			'literals': [],
			'keyword': '',
			'tree': []
		};
		this.select.push(cat);

		this.results.clearAssays();
		this.onPropGridOptionsChanged();
		this.rebuildSelection();
		this.requestTreeTerms();
	}

	// zap an existing category
	private categoryDelete(idx:number):void
	{
		if (idx >= this.select.length) return;
		this.select.splice(idx, 1);
		if (this.select.length == 0)
		{
			this.results.clearAssays();
			this.onPropGridOptionsChanged();
		}

		this.rebuildSelection();
		this.requestTreeTerms();
		this.keyword.changeAnnotations(this.selectedAnnotations());
	}

	// clicked on a term in the tree hierarchy
	private selectTerm(selidx:number, termidx:number):void
	{
		if (selidx >= this.select.length) return;

		let sel = this.select[selidx];

		let uri = sel.tree[termidx].uri;
		let i = sel.terms.indexOf(uri);
		if (i < 0)
			sel.terms.push(uri);
		else
			sel.terms.splice(i, 1);

		this.rebuildSelection();
		this.requestTreeTerms();
		this.keyword.changeAnnotations(this.selectedAnnotations());
	}

	// selects the given term and all its children
	private selectAllChildren(selidx:number, termidx:number):void
	{
		let sel = this.select[selidx];
		let modified = false;

		for (let n = termidx; n < sel.tree.length && (n == termidx || sel.tree[n].depth > sel.tree[termidx].depth); n++)
		{
			if (sel.tree[n].count == 0) continue;
			let i = sel.terms.indexOf(sel.tree[n].uri);
			if (i < 0)
			{
				sel.terms.push(sel.tree[n].uri);
				modified = true;
			}
		}

		if (!modified) return;
		this.rebuildSelection();
		this.requestTreeTerms();
	}

	// de-selects the given term and all its children
	private unselectAllChildren(selidx:number, termidx:number):void
	{
		let sel = this.select[selidx];
		let modified = false;

		for (let n = termidx; n < sel.tree.length && (n == termidx || sel.tree[n].depth > sel.tree[termidx].depth); n++)
		{
			if (sel.tree[n].count == 0) continue;
			let i = sel.terms.indexOf(sel.tree[n].uri);
			if (i >= 0)
			{
				sel.terms.splice(i, 1);
				modified = true;
			}
		}

		if (!modified) return;
		this.rebuildSelection();
		this.requestTreeTerms();
	}

	// inserts a row of buttons for controlling how order/display of assignments & results works
	private rebuildGroupButtons():void
	{
		let paraButtons = this.paraGroupSelect;
		paraButtons.empty();
		if (Vec.arrayLength(this.results.assayList) == 0) return;

	 	let table = $('<table/>').appendTo(paraButtons);
		let tr = $('<tr/>').appendTo(table);
		let tdButtons = $('<td valign="middle"/>').appendTo(tr);

		const makeButton = (label:string | JQuery, eventHandler:() => void, tooltip:string):void =>
		{
			let btn = $('<button class="btn btn-normal"/>').appendTo(tdButtons);
			btn.append(label);
			if (eventHandler) btn.click(eventHandler);
			Popover.hover(domLegacy(btn), null, tooltip);
			btn.css('margin-right', '3px');
		};

		makeButton('Group By', () => this.actionGroupBy(), 'Organize assays by annotation groups.');
		makeButton('Hide Selected Assays', () => this.actionHideSelectedAssays(), 'Hide the selected assays.');
		makeButton('Filter Categories', () => this.actionAssignments(), 'Select which assignments to show in the grid.');
		let showHide = this.propGridOptions.hideIdentical ? 'Show' : 'Hide';
		makeButton(`${showHide} Identical Categories`, () => this.actionToggleIdenticalCategories(), `${showHide} Identical Categories`);
		showHide = this.propGridOptions.excludeValueURIs.length > 0 ? 'Show' : 'Hide';
		makeButton(`${showHide} Absence Only Categories`, () => this.actionToggleAbsenceBlankCategories(), `${showHide} Absence/Blank Categories`);
		makeButton('Reset Categories', () => this.actionResetDisplay(), 'Reset View');
		showHide = this.propGridOptions.showHierarchy ? 'Hide' : 'Show';
		makeButton(`${showHide} Hierarchy`, () => this.actionToggleHierarchy(), 'Toggle lines indicating the hierarchy');
		makeButton($('<span class="glyphicon glyphicon-education" style="width: 0.9em; height: 1.2em;"/>'), null, this.propertyGridLegend().prop('outerHTML'));
	}

	private propertyGridLegend():JQuery
	{
		const makeEntry = (container:JQuery, icon:string, label:string):void =>
		{
			let div = $('<div/>').appendTo(container);
			div.html(icon);
			div.css({'color': Theme.STRONG_HTML, 'justify-self': 'center'});
			$('<div/>').append(label).appendTo(container);
		};
		let legend = $('<div/>');
		legend.css({'display': 'grid', 'grid-template-columns': 'auto auto', 'align-items': 'center'});
		makeEntry(legend, '&#9724;', 'annotation');
		makeEntry(legend, '&#9675;', 'free text');
		makeEntry(legend, '&#9670;', 'absence term');
		makeEntry(legend, '&#9646;', 'hierarchy');
		return legend;
	}

	// rebuilds the property grid, or displays a loading sign if assays are still being acquired
	private rebuildPropGrid():void
	{
 		let assayList = this.results.getVisibleAssays();

		if (assayList.length == 0)
		{
			this.propGridContainer.empty();
			this.propGrid = null;
			return;
		}
		this.getPropertyGridContainer('Building Property Grid...');

		this.filterCategories();

		let schema = this.availableTemplates[this.chosenTemplate];
		this.propGrid = new PropertyGrid(schema.schemaURI, assayList);
		this.propGrid.delegate = this;
		this.propGrid.showAssignments = this.filterCategories();
		this.propGrid.selectedAssayIDList = this.results.selectedAssayIDList();
		this.propGrid.clickedAssay = (assayID:number) =>
		{
			this.results.toggleAssaySelection(assayID);
			if (this.propGrid) this.propGrid.changeSelection(this.results.selectedAssayIDList());
		};

		for (let seq of this.select) if (seq.category >= 0)
		{
			let assn = schema.assignments[seq.category];
			this.propGrid.queryAssignments.push(assn);
			for (let uri of seq.terms)
			{
				let annot:AssayAnnotation =
				{
					'propURI': assn.propURI,
					'groupNest': assn.groupNest,
					'valueURI': uri
				};
				this.propGrid.queryAnnotations.push(annot);
			}
		}

		// allow a chance to update the page, then proceed to rebuild the grid
		setTimeout(() =>
		{
			this.propGrid.showHierarchy = this.propGridOptions.showHierarchy;
			this.propGrid.render(this.propGridContainer);
		}, 0);
	}

	// change current template
	private changeTemplate(idx:number):void
	{
		this.chosenTemplate = idx;

		// remove all current selections
		this.propGridContainer.empty();
		this.results.clearAssays();
		this.select = [];

		for (let n = this.select.length - 1; n >= 0; n--) if (this.select[n].category >= 0) this.select.splice(n, 1);
		this.populateTemplate();
		this.keyword.changeSchema(this.availableTemplates[this.chosenTemplate].schemaURI);
		history.replaceState({'id': 'explore.jsp'}, 'Explore Assays', this.sharableLink(false));

		this.rebuildSelection();
		this.requestTreeTerms();
		this.results.render(this.paraResults);
	}

	// returns true if there are any meaningful restrictions
	private anyQuery():boolean
	{
		for (let sel of this.select) if (sel.terms.length > 0 || sel.keyword) return true;
		return false;
	}

	// make use of a preexisting keyword and add it into the corresponding search textbox
	private grabKeywordText(idx:number, text:string):void
	{
		let sel = this.select[idx], tbox = sel.textInput;
		if (!tbox) return;

		let schema = this.availableTemplates[this.chosenTemplate];
		if (schema.assignments[this.select[idx].category].suggestions == 'string')
		{
			for (let n = text.length - 1; n >= 0; n--) if (text.charAt(n) == '"')
				text = text.substring(0, n) + '\\' + text.substring(n);
			text = '"' + text + '"';
		}

		let keyw:string = purifyTextPlainInput(tbox.val().toString()).trim();
		if (keyw.length > 0) keyw = keyw + ' OR ';
		keyw += text;
		tbox.val(keyw);

		sel.keyword = keyw;
		this.requestTreeTerms();
	}

	// got an arbitrary annotation, and want it appended to an existing sequence, or a new one
	private appendAnnotation(annot:AssayAnnotation):void
	{
		let schema = this.availableTemplates[this.chosenTemplate];
		let catidx = -1;
		for (let n = 0; n < schema.assignments.length; n++)
		{
			let assn = schema.assignments[n];
			if (samePropGroupNest(annot.propURI, annot.groupNest, assn.propURI, annot.groupNest)) {catidx = n; break;}
		}
		if (catidx < 0) return; // (shouldn't happen)

		// if the category is already present, can just push the term
		for (let sel of this.select) if (sel.category == catidx)
		{
			if (sel.terms.indexOf(annot.valueURI) >= 0) return; // (also shouldn't happen)
			sel.terms.push(annot.valueURI);
			this.partialBranchExpand(sel); // make sure the selection tree is open for it
			annot = null;
			break;
		}

		// if category not present, need to make it
		if (annot)
		{
			this.select.push(
			{
				'category': catidx,
				'terms': [annot.valueURI],
				'literals': [],
				'keyword': '',
				'tree': []
			});
		}
		this.rebuildSelection();
		this.requestTreeTerms();
		this.keyword.changeAnnotations(this.selectedAnnotations());
	}

	// extracts all the annotations from the current sequence
	private selectedAnnotations():AssayAnnotation[]
	{
		let annotList:AssayAnnotation[] = [];
		let schema = this.availableTemplates[this.chosenTemplate];

		for (let sel of this.select) if (sel.category >= 0)
		{
			let assn = schema.assignments[sel.category];
			for (let valueURI of sel.terms)
			{
				annotList.push(
				{
					'propURI': assn.propURI,
					'propLabel': null,
					'valueURI': valueURI,
					'valueLabel': null,
					'groupNest': assn.groupNest
				});
			}
		}

		return annotList;
	}

	// draws the query content, if desired
	private rebuildQuery(domParent:JQuery):string[]
	{
		let qstr1 = this.formulateQuery();

		let mask = this.results.selectedMask(), anyChecked = Vec.anyTrue(mask);
		let assayList = this.results.getAssays();

		let qstr2 = '[', qstr3 = '[', first = true;
		for (let n = 0; n < mask.length; n++)
		{
			if (!mask[n] && anyChecked) continue;
			let assayID = this.results.assayList[n].assayID;
			let uniqueID = assayList[n] ? assayList[n].uniqueID : null;
			if (!first)
			{
				qstr2 += ',';
				qstr3 += ',';
			}
			else first = false;
			qstr2 += uniqueID ? 'UID/' + uniqueID : assayID;
			qstr3 += assayID;
		}
		qstr2 += ']';
		qstr3 += ']';

		let result = [qstr1, qstr2, qstr3];
		if (!domParent) return result;

		// render onscreen, if a parent was provided

		let payload = encodeURI(qstr1);
		let qstr4 = restBaseURL + '/servlet/DownloadQuery/results.txt?id=uniqueIDRaw&query=' + payload;
		let qstr5 = restBaseURL + '/servlet/DownloadQuery/results.zip?assays=true&query=' + payload;

		let divRight = $('<div/>').appendTo(domParent).css({'text-align': 'right'});
		let grid = $('<div/>').appendTo(divRight).css({'display': 'inline-grid'});
		grid.css({'grid-gap': '0.2em', 'justify-items': 'end', 'align-items': 'center'});
		grid.css('grid-template-columns', '[label] auto [value] auto [end]');

		let cell = (row:number, col:string):JQuery => $('<div/>').appendTo(grid).css({'grid-row': row.toString(), 'grid-column': col});
		let line = (row:number, label:string, text:string):void =>
		{
			cell(row, 'label').css({'font-weight': 'bold'}).text(label);
			let input = $('<input type="text" size="60" readonly/>').appendTo(cell(row, 'value'));
			input.val(text);
			input.click(() => input.select());
		};

		line(1, 'Filter Query:', qstr1);
		line(2, 'Unique ID:', qstr2);
		line(3, 'Assay ID:', qstr3);
		line(4, 'Download IDs:', qstr4);
		line(5, 'Download Assays:', qstr5);

		return result;
	}

	// composes the browse selection in the form of a query string that is recognised by the server
	private formulateQuery():string
	{
		const RESERVED = '\\()=,;*@!';
		function escape(str:string):string
		{
			for (let n = str.length - 1; n >= 0; n--)
				if (RESERVED.indexOf(str.charAt(n)) >= 0) str = str.substring(0, n) + '\\' + str.substring(n);
			return str;
		}

		let qstr = '';
		for (let selseq of this.select)
		{
			if (selseq.terms.length == 0 || selseq.category < 0) continue;
			if (qstr.length > 0) qstr += ';';
			qstr += '(';
			let propURI = this.availableTemplates[this.chosenTemplate].assignments[selseq.category].propURI;
			qstr += escape(collapsePrefix(propURI)) + '=';
			for (let n = 0; n < selseq.terms.length; n++)
			{
				if (n > 0) qstr += ',';
				let valueURI = selseq.terms[n];
				qstr += '@' + escape(collapsePrefix(valueURI));
			}
			qstr += ')';
		}

		return qstr;
	}

	// composes the browse selection in the form of a query string that is sharable
	private sharableLink(includeWindowLoc:boolean = true):string
	{
		let returnURL = window.location.href;

		if (!includeWindowLoc) returnURL = '';

		let encodedQuery = encodeURI(this.formulateQuery());

		if (encodedQuery != '')
		{
			if (returnURL.includes('?'))
				returnURL = returnURL + '&searchQuery=' + encodedQuery;
			else
				returnURL = returnURL + '?searchQuery=' + encodedQuery;
		}

		let schema = this.availableTemplates[this.chosenTemplate];

		if (returnURL.includes('?'))
			returnURL += '&template=' + encodeURIComponent(schema.schemaURI);
		else
			returnURL += '?template=' + encodeURIComponent(schema.schemaURI);

		let selectionTerms = this.composeSelectionTerms();

		for (let selectionTerm of selectionTerms)
		{
			if (returnURL.includes('?'))
				returnURL += '&' + selectionTerm.propURI + '=' + encodeURIComponent(selectionTerm.keywordSelector);
			else
				returnURL += '?' + selectionTerm.propURI + '=' + encodeURIComponent(selectionTerm.keywordSelector);
		}

		return returnURL;
	}

	// create the measurement table, showing selected compounds
	private showCompounds():void
	{
		this.compoundsDisplay.empty();

		let assayIDList = this.results.selectedAssayIDList();
		if (assayIDList.length == 0)
		{
			alert('Select at least one result first.');
			return;
		}

		this.compoundsDisplay.text('Loading measurements...');
		this.compoundsDisplay[0].scrollIntoView();

		let mdata = new MeasureData(assayIDList);
		mdata.obtainCompounds(() =>
		{
			if (mdata.compounds.length > 0)
			{
				let mtable = new MeasureTable(mdata);
				mtable.render(this.compoundsDisplay);
			}
			else
			{
				this.compoundsDisplay.empty();
				// (consider putting a note to say that there's nothing available?)
			}
		});
	}

	// bring up a new tab that displays the selected assays vs. compounds, or predictions
	private showAnalysis(pageFN:string):void
	{
		let mask = this.results.selectedMask(), explicitList = Vec.anyTrue(mask);

		// NOTE: slightly temporary: the "query" syntax does not handle keywords or special categories, so have to drop down to
		// launching with specific assayIDs
		if (!explicitList) for (let selseq of this.select) if (selseq.category < 0 || selseq.keyword.length > 0)
		{
			explicitList = true;
			mask = Vec.booleanArray(true, mask.length);
			break;
		}

		let url = getBaseURL() + '/' + pageFN + '?';
		if (explicitList)
		{
			url += 'assays=';
			let first = true;
			for (let n = 0; n < mask.length; n++) if (mask[n])
			{
				if (first) first = false; else url += '%2C'; // (comma)
				url += this.results.assayList[n].assayID;
			}
		}
		else
		{
			let qstr = this.formulateQuery();
			url += 'query=' + encodeURIComponent(qstr);
		}

		let schema = this.availableTemplates[this.chosenTemplate];
		url += '&schema=' + encodeURIComponent(schema.schemaURI);

		window.open(url, '_blank');
	}

	// offers to determine how to group and order the assays
	private actionGroupBy():void
	{
		let dlg = new PickAssignmentDialog(this.availableTemplates[this.chosenTemplate], true);
		dlg.picked = this.results.getAssayGroup();
		dlg.callbackDone = (assnlist:SchemaAssignment[]) =>
		{
			this.results.setAssayGroup(assnlist);
			this.onPropGridOptionsChanged();
		};
		dlg.show();
	}

	// allows assignment categories to be switched on or off
	private actionAssignments():void
	{
		let schema = this.availableTemplates[this.chosenTemplate];
		if (!this.selectedAssignments) this.selectedAssignments = schema.assignments.slice(0);

		let dlg = new PickAssignmentDialog(schema, true);
		dlg.picked = this.selectedAssignments;
		dlg.showAllNone = true;
		dlg.callbackDone = (assnlist:SchemaAssignment[]) =>
		{
			this.selectedAssignments = assnlist;
			this.onPropGridOptionsChanged();
		};
		dlg.show();
	}

	// collapse everything in the branch that isn't on a selection track
	private partialBranchCollapse(seq:SelectSequence):void
	{
		if (!seq.collapseState) return;
		let terms = new Set<string>();
		for (let uri of seq.terms) terms.add(uri);
		let selidx:number[] = [];
		for (let n = 0; n < seq.tree.length; n++) if (terms.has(seq.tree[n].uri)) selidx.push(n);
		seq.collapseState.partialBranchCollapse(selidx);
	}

	// ensure that all selected items are on an expanded track
	private partialBranchExpand(seq:SelectSequence):void
	{
		if (!seq.collapseState) return;
		let terms = new Set<string>();
		for (let uri of seq.terms) terms.add(uri);
		let selidx:number[] = [];
		for (let n = 0; n < seq.tree.length; n++) if (terms.has(seq.tree[n].uri)) selidx.push(n);
		seq.collapseState.partialBranchExpand(selidx);
	}

	// selected assays get hidden
	private actionHideSelectedAssays():void
	{
		let idList = this.results.selectedAssayIDList();

		for (let n = 0; n < idList.length; n++)
		{
			this.results.toggleAssayHidden(idList[n]);
			this.results.toggleAssaySelection(idList[n]);
		}
		this.onPropGridOptionsChanged();
	}

	private actionHideCategory(assnToHide:SchemaAssignment):void
	{
		if (assnToHide == null) return;
		let schema = this.availableTemplates[this.chosenTemplate];
		if (!this.selectedAssignments) this.selectedAssignments = schema.assignments.slice(0);
		this.selectedAssignments = this.selectedAssignments.filter((assn) =>
			!samePropGroupNest(assn.propURI, assn.groupNest, assnToHide.propURI, assnToHide.groupNest));
		this.onPropGridOptionsChanged();
	}

	private actionResetDisplay():void
	{
		let schema = this.availableTemplates[this.chosenTemplate];
		this.propGridOptions.reset();
		this.selectedAssignments = schema.assignments.slice(0);
		this.onPropGridOptionsChanged();
	}

	private actionToggleHierarchy():void
	{
		this.propGridOptions.showHierarchy = !this.propGridOptions.showHierarchy;
		if (this.propGrid) this.onPropGridOptionsChanged();
	}

	private actionToggleAbsenceBlankCategories():void
	{
		if (this.propGridOptions.excludeValueURIs.length == 0)
			this.propGridOptions.excludeValueURIs = ALL_ABSENCE_TERMS;
		else
			this.propGridOptions.excludeValueURIs = [];
		this.onPropGridOptionsChanged();
	}

	private actionToggleIdenticalCategories():void
	{
		this.propGridOptions.hideIdentical = !this.propGridOptions.hideIdentical;
		this.onPropGridOptionsChanged();
	}

	private onPropGridOptionsChanged():void
	{
		this.rebuildGroupButtons();
		this.rebuildPropGrid();
	}

	// goes through all of the annotations for a given category and filters assignments
	// - selectedAssignments: assignments selected/unselected by user
	// - propGridOptions.hideIdentical: remove assignments that all have the same annotation
	// - propGridOptions.excludeValueURIs: remove assignments that have only valuesURI in this list
	private filterCategories():SchemaAssignment[]
	{
		let schema = this.availableTemplates[this.chosenTemplate];
		if (!this.selectedAssignments) this.selectedAssignments = schema.assignments.slice(0);

		let newAssignments:SchemaAssignment[] = [];

 		let visibleAssayList = this.results.getVisibleAssays();

		for (let assn of schema.assignments)
		{
			if (!this.isAssignmentSelected(assn)) continue;

			// iterate through the assays
			let valueSet = new Set<string>();
			let allIdentical = this.propGridOptions.hideIdentical;
			let onlyExcluded = true;

			// TODO: make this better -- put into a util class that returns counts also so more useful
			for (let assay of visibleAssayList)
			{
				// find annotations in assay for assignment th
				let matching = this.filterByAssignment(assay.annotations, assn);

				// filter the assignments in excludedPropURIs
				matching = matching.filter((annot) => !this.propGridOptions.excludeValueURIs.includes(annot.valueURI));
				onlyExcluded = onlyExcluded && matching.length == 0;

				// convert values into a unique key
				let values = matching.map((annot) => annot.valueURI + '::' + annot.valueLabel);
				values.sort();
				valueSet.add(values.join('||'));

				if (valueSet.size > 1) allIdentical = false;
			}

			if (!allIdentical && !onlyExcluded)
			{
				newAssignments.push(assn);
			}
		}

		return newAssignments;
	}

	private isAssignmentSelected(assignment:SchemaAssignment):boolean
	{
		let key = keyPropGroup(assignment.propURI, assignment.groupNest);
		return this.selectedAssignments.some((selected) => key == keyPropGroup(selected.propURI, selected.groupNest));
	}

	private filterByAssignment(annotations:AssayAnnotation[], assignment:SchemaAssignment):AssayAnnotation[]
	{
		return annotations.filter((annot) =>
			keyPropGroup(annot.propURI, annot.groupNest) == keyPropGroup(assignment.propURI, assignment.groupNest));
	}

	private getPropertyGridContainer(message:string = '', empty = false):JQuery
	{
		if (empty) this.propGridContainer.empty();
		this.propGrid = null;

		if (message) OverlayMessage.show(message, {}, this.propGridContainer);

		return this.propGridContainer;
	}
}

class PropertyGridOptions
{
	private readonly propertyGridOptionsKey = 'propertyGridOptions';
	private _showHierarchy:boolean;
	private _hideIdentical:boolean;
	private _excludeValueURIs:readonly string[];

	constructor()
	{
		this.restore();
	}

	public reset():void
	{
		this.showHierarchy = null;
		this.hideIdentical = null;
		this.excludeValueURIs = null;
	}

	get showHierarchy():boolean
	{
		return this._showHierarchy;
	}

	set showHierarchy(newValue:boolean)
	{
		if (newValue == null) newValue = true;
		this._showHierarchy = newValue;
		this.persist();
	}

	get hideIdentical():boolean
	{
		return this._hideIdentical;
	}

	set hideIdentical(newValue:boolean)
	{
		if (newValue == null) newValue = false;
		this._hideIdentical = newValue;
		this.persist();
	}

	get excludeValueURIs():readonly string[]
	{
		return this._excludeValueURIs;
	}

	set excludeValueURIs(newValue:readonly string[])
	{
		if (newValue == null) newValue = [];
		this._excludeValueURIs = newValue;
		this.persist();
	}

	private restore():void
	{
		let opt = JSON.parse(window.localStorage.getItem(this.propertyGridOptionsKey));
		this.hideIdentical = opt && opt.hideIdentical;
		this.showHierarchy = opt && opt.showHierarchy;
		this.excludeValueURIs = opt && opt.excludeValueURIs;
	}

	private persist():void
	{
		let opt =
		{
			'hideIdentical': this.hideIdentical,
			'showHierarchy': this.showHierarchy,
			'excludeValueURIs': this.excludeValueURIs,
		};
		window.localStorage.setItem(this.propertyGridOptionsKey, JSON.stringify(opt));
	}
}

/* EOF */ }

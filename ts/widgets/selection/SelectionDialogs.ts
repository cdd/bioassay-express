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
	Selection dialogs: popping up a dialog that shows a list of selectable items. There are two specific implementations, one for plain lists,
	and another for trees.
*/

export interface SelectionDialogDelegate
{
	createLookupNode(data:any, parent:JQuery, modfunc:any, matchCount?:number):JQuery;
	matchLookupNode(data:any, txt:string):boolean;
	isLookupNodeSelected(data:any):boolean;
}

enum ToggleState
{
	Open = 'open',
	Partial = 'partial',
	Closed = 'closed',
}

export abstract class SelectionDialog extends BootstrapDialog
{
	protected selection:(SelectionDataList | SelectionDataTree);
	protected txtSearch:JQuery;
	protected currentSearch = ''; // current search string, if any

	protected hitCount:JQuery;
	protected hideUnmatched:JQuery; // checkbox; if true, then hide terms that do not match search string

	protected data:any[];
	protected domNodes:JQuery[] = [];
	protected delayedScrollIndex = -1;

	protected stopped = false;
	private watermark = 0;

	constructor(public titleHTML:string, protected delegate:SelectionDialogDelegate,
				protected canRequestProvisionals:boolean = false, protected searchTxt:string = null,
				protected showMatchesOnlyChecked:boolean = true)
	{
		super(titleHTML);
		this.titleUsesHTML = true;
		this.withCloseButton = false;
	}

	protected populateContent():void
	{
		this.areaTopRight.css('vertical-align', 'middle');

		let btnSearch = $('<button class="btn btn-xs btn-action"></button>').appendTo(this.areaTopRight);
		btnSearch.css({'margin-left': '2em', 'margin-right': '0.5em'});
		btnSearch.append('<span class="glyphicon glyphicon-search"></span>');

		this.txtSearch = $('<input type="search"></input>').appendTo(this.areaTopRight);
		this.txtSearch.css('margin-right', '1em');
		if (this.searchTxt != null) this.txtSearch.val(this.searchTxt);

		this.hitCount = $('<span>').appendTo(this.areaTopRight);
		this.hitCount.css({'margin-left': '1em', 'margin-right': '1em', 'color': 'green'});

		let label = $('<label>').appendTo(this.areaTopRight);
		label.append('Show matches only');
		label.css('margin-right', '2em');

		this.hideUnmatched = $('<input type="checkbox">').appendTo(label);
		this.hideUnmatched.css({'margin-left': '0.5em'});
		this.hideUnmatched.prop('checked', this.showMatchesOnlyChecked);
		this.hideUnmatched.change(() => this.applySearchWatermarked(purifyTextPlainInput(this.txtSearch.val()), true));

		this.btnClose = $('<button class="close" data-dismiss="modal" aria-hidden="true">&times;</button>');
		this.btnClose.css({'float': 'none', 'padding-top': '0.5em'}); // want it v-centred
		this.btnClose.click(() => this.hide());
		this.areaTopRight.append(this.btnClose);

		this.txtSearch.keyup(() => this.applySearchWatermarked(purifyTextPlainInput(this.txtSearch.val()), false));
		btnSearch.click(() => this.applySearchWatermarked(purifyTextPlainInput(this.txtSearch.val()), false));

		this.content.html('<b>Loading...</b>');
		this.content.css(
			{
				'max-height': `${window.innerHeight - 150}px`,
				'margin-bottom': '10px',
				'overflow-y': 'scroll',
			});
	}

	public hide():void
	{
		this.stopped = true;
		super.hide();
	}

	protected onShown():void
	{
		this.txtSearch.focus();
	}

	public abstract applySearch(txt:string, hideToggle:boolean):void;
	protected abstract renderNode(idx:number):JQuery;

	protected applySearchWatermarked(txt:string, hideToggle:boolean)
	{
		let sync = ++this.watermark;
		if (this.stopped == false) this.stopped = true;
		setTimeout(() =>
		{
			this.stopped = false;
			if (sync == this.watermark) this.applySearch(txt, hideToggle);
		}, 100);
	}

	protected updateHitcount()
	{
		let count = this.selection.countHits();
		this.hitCount.text(count == 0 ? '' : `${count} found`);
	}

	public scrollToURI(uri:string)
	{
		if (!uri) return;
		const idx = this.data.findIndex((d) => d.uri == uri);
		if (idx != -1) this.scrollToIndex(idx);
	}

	// move the scroll position to the given index
	public scrollToIndex(idx:number)
	{
		// if dom doesn't exist, delay scroll until dom is available
		let dom = this.domNodes[idx];
		if (dom == null)
		{
			this.delayedScrollIndex = idx;
			return;
		}
		setTimeout(() => this.content.animate({'scrollTop': dom.offset().top}, 500), 1);
		this.delayedScrollIndex = -1;
	}

	// ------------ private/protected methods ------------

	protected renderNodes(nstart:number = 0)
	{
		if (nstart == 0)
		{
			this.content.empty();
			this.domNodes.fill(null);
		}
		const numNodes = this.data.length;
		const showUnmatched = !this.hideUnmatched.prop('checked');
		const tstart = performance.now();
		let n = nstart;
		while (n < numNodes && performance.now() - tstart < 40)
		{
			if (this.stopped) return; // terminate: dialog has been closed
			if (showUnmatched || this.selection.visMask[n])
			{
				this.domNodes[n] = this.renderNode(n);
				this.content.append(this.domNodes[n]);
			}
			n++;
		}
		if (this.delayedScrollIndex > 0 && n > this.delayedScrollIndex) this.scrollToIndex(this.delayedScrollIndex);
		if (n < numNodes) window.setTimeout(() => this.renderNodes(n), 1);
	}
}

/*
	SelectionDialogList: a dialog for rendering lines in a simple list. Relatively easy to sort and filter.
*/

export class SelectionDialogList extends SelectionDialog
{
	public populate(data:any[])
	{
		if (this.selection == null)
			this.selection = new SelectionDataList(data, this.delegate.matchLookupNode);
		else
			this.selection.setData(data);
		this.data = data;

		if (this.txtSearch.val() != this.currentSearch)
			this.applySearch(this.txtSearch.val().toString(), false);
		else
			this.renderNodes();
	}

	// change the search parameters, and update the view accordingly
	public applySearch(txt:string, hideToggle:boolean):void
	{
		if (!this.selection) return;
		if (this.currentSearch == txt && !hideToggle) return;
		this.currentSearch = txt;
		this.selection.search(this.currentSearch);

		this.updateHitcount();
		this.renderNodes();
	}

	// ------------ private/protected methods ------------

	// creates an element to render the line that is associated with the data item (from listData[n])
	protected renderNode(idx:number):JQuery
	{
		const data = this.data[idx];
		const modidx = idx;
		const modfunc = () => {this.updateNode(modidx);};

		const nodeCSS:Record<string, string> =
		{
			'display': 'flex',
			'justify-content': 'flex-start',
			'padding-left': '2.6em',
		};
		if (this.selection.selMask[idx]) nodeCSS['background-color'] = 'cornsilk';
		const node = $('<div>').css(nodeCSS);
		node.append(this.delegate.createLookupNode(data, node, modfunc));
		return node;
	}

	private updateNode(n:number)
	{
		// if existing, remove the node
		let prevNode, nextNode;
		let node = this.domNodes[n];
		if (node)
		{
			prevNode = node.prev();
			nextNode = node.next();
			node.remove();
		}

		// recreate node and link into DOM at the right spot
		node = this.renderNode(n);
		if (prevNode && prevNode.length) node.insertAfter(prevNode);
		else if (nextNode && nextNode.length) node.insertBefore(nextNode);
		else this.content.append(node);

		this.domNodes[n] = node;
	}
}

/*
	SelectionDialogTree: a widget for presenting a tree-like hierarchy of items that can be checked open/closed. The design is intended to be
	compatible with trees that are big enough that they would thrash the DOM if everything was instantiated at the same time (which
	isn't the case for some off-the-shelf widgets).

	Source content is the .treeData member, a flat array of nodes that make up the tree:
		.depth: 0 = root level, >0 = child
		.parent: index of parent node; 0-based, root nodes = -1

	The caller should stash whichever other values are useful within the node object, as these will be passed back when rendering.
*/

export class SelectionDialogTree extends SelectionDialog
{
	private revealIndices:number[] = [];

	public populate(data:SchemaTreeNode[], revealIndices:number[])
	{
		if (this.selection == null)
		{
			const revealURIs = new Set(revealIndices.map((idx) => data[idx].uri));
			const nbefore = data.length;
			data = new BranchGrouper().processNodes(data);
			if (nbefore != data.length)
			{
				revealIndices = [];
				data.forEach((d, idx) => { if (revealURIs.has(d.uri)) revealIndices.push(idx); });
			}
			this.selection = new SelectionDataTree(data, this.delegate.matchLookupNode);
		}
		else
			this.selection.setData(data);
		this.data = data;
		this.revealIndices = revealIndices;

		this.initialTreeLayout(revealIndices);

		if (this.txtSearch.val() != this.currentSearch)
			this.applySearch(this.txtSearch.val().toString(), false);
		else
			this.renderNodes();
	}

	// change the search parameters, and update the view accordingly
	public applySearch(txt:string, hideToggle:boolean):void
	{
		if (!this.selection) return;
		if (this.currentSearch == txt && !hideToggle) return; // nothing to do
		// if user clears the search, reinitialize the view
		if (this.currentSearch != '' && txt == '')
		{
			this.currentSearch = txt;
			this.populate(this.data, this.revealIndices);
			this.updateHitcount();
			return;
		}

		this.currentSearch = txt;
		this.selection.search(this.currentSearch);

		// always show existing annotations and their parents
		let annotations:number[] = [];
		for (let n = 0; n < this.data.length; n++)
			if (this.delegate.isLookupNodeSelected(this.data[n]))
				annotations.push(n);
		this.selection.show(annotations);
		this.updateHitcount();
		this.renderNodes();
	}

	// ------------ private/protected methods ------------

	private initialTreeLayout(revealIndices:number[] = [])
	{
		const data = this.data as SchemaTreeNode[];
		const selection = (this.selection as SelectionDataTree);
		const numNodes = data.length;

		// close branches at depth 1
		let rootNodes:number[] = [];
		for (let n = 0; n < numNodes; n++)
		{
			if (data[n].depth == 0) rootNodes.push(n);
			else if (data[n].depth == 1) selection.closeBranch(n);
		}

		// closes branches at depth 0 if they have more than 200 children to decrease loading time
		const MAX_CHILD_NODES = 200;
		for (const n of rootNodes)
		{
			let count = 0;
			for (let i = n + 1; i < numNodes; i++)
			{
				let d = data[i].depth;
				if (d == 0 || count > MAX_CHILD_NODES) break;
				if (d == 1) count++;
			}
			if (count < MAX_CHILD_NODES) continue; // no need to do anything
			selection.closeBranch(n);
		}

		// show existing annotations in their branches
		for (let n = 0; n < data.length; n++)
		{
			const d = data[n];
			if (this.delegate.isLookupNodeSelected(d))
				selection.openBranch(d.parent);
		}

		// if revealIndices were specified show them
		if (revealIndices) selection.show(revealIndices);
	}

	// creates an element to render the line that is associated with the data item (from treeData[n])
	protected renderNode(idx:number):JQuery
	{
		const data = this.data[idx];

		let node = $('<div>').css({'display': 'flex', 'justify-content': 'flex-start'});

		if (data.depth > 0) $('<div>').appendTo(node).css('width', `${data.depth * 1.5}em`);

		let spacer = $('<div>').appendTo(node).css('width', '2.2em');
		this.configureToggleButton(spacer, idx);

		const modidx = idx;
		let modfunc = () => {this.selection.show([modidx]); this.scrubNode(modidx); this.syncNodes();};

		let label = $('<div>').appendTo(node);
		label.css('padding-top', '0.1em');
		label.append(this.delegate.createLookupNode(data, node, modfunc, this.selection.matchCount[idx]));

		if (this.selection.selMask[idx]) node.css('background-color', 'cornsilk');
		return node;
	}

	private configureToggleButton(container:JQuery, idx:number)
	{
		const selection = (this.selection as SelectionDataTree);
		const img = $('<img>').appendTo(container);
		if (!selection.childMask[idx])
		{
			img.attr('src', restBaseURL + '/images/branch_dot.svg');
			img.css({'width': '10px', 'height': '10px', 'cursor': 'default', 'margin-left': '5px'});
			return;
		}

		// determine current state of node
		let state:ToggleState;
		if (selection.isPartialOpen(idx))
			state = ToggleState.Partial;
		else if (selection.openMask[idx])
			state = ToggleState.Open;
		else
			state = ToggleState.Closed;

		const ToggleIcon =
		{
			[ToggleState.Open]: 'branch_close.svg',
			[ToggleState.Partial]: 'branch_partial.svg',
			[ToggleState.Closed]: 'branch_open.svg',
		};
		img.attr('src', `${restBaseURL}/images/${ToggleIcon[state]}`);
		img.css({'width': '20px', 'height': '20px', 'cursor': 'pointer'});
		img.click(() => this.toggleOpen(idx, state));
	}

	// checks a node open/closed
	private toggleOpen(idx:number, current:ToggleState):void
	{
		const NextToggleState =
		{
			[ToggleState.Open]: ToggleState.Closed,
			[ToggleState.Partial]: ToggleState.Open,
			[ToggleState.Closed]: ToggleState.Partial,
		};

		const selection = this.selection as SelectionDataTree;
		this.scrubNode(idx);

		let nextState = NextToggleState[current];
		// override partial opening in certain cases
		if (nextState == ToggleState.Partial)
		{
			if (this.currentSearch == '') nextState = ToggleState.Open;
			else if (!this.hideUnmatched.prop('checked')) nextState = ToggleState.Open;
		}

		if (nextState == ToggleState.Closed)
			selection.closeBranch(idx);
		else if (nextState == ToggleState.Partial)
			selection.partialOpenBranch(idx);
		else
			selection.openBranch(idx);
		this.syncNodes();
	}

	// deletes the contents of a node from the DOM, and marks it undefined in the records
	private scrubNode(idx:number):void
	{
		let dom = this.domNodes[idx];
		if (!dom) return;
		dom.remove();
		this.domNodes[idx] = null;
	}

	// goes through all nodes and makes sure the DOM is defined if and only if visMask is true
	private syncNodes():void
	{
		const numNodes = this.data.length;

		let pos = 0;
		let prev:JQuery = null;
		let nextBatch = () =>
		{
			const tstart = performance.now();
			for (; pos < numNodes && performance.now() - tstart < 40; pos++)
			{
				if (this.stopped) return;

				// remove node if no longer visible
				if (!this.selection.visMask[pos])
				{
					this.scrubNode(pos);
					continue;
				}
				else if (this.domNodes[pos])
				{
					prev = this.domNodes[pos];
					continue;
				}

				// new node - add into the tree at the right spot
				let node = this.renderNode(pos);
				this.domNodes[pos] = node;
				if (prev)
				{
					node.insertAfter(prev);
					prev = node;
					continue;
				}
				else
				{
					// find just the right spot for it
					let found = false;
					for (let i = pos - 1; i >= 0; i--) if (this.domNodes[i])
					{
						node.insertAfter(this.domNodes[i]);
						found = true;
						break;
					}
					if (!found) for (let i = pos + 1; i < numNodes; i++) if (this.domNodes[i])
					{
						node.insertBefore(this.domNodes[i]);
						found = true;
						break;
					}
					if (!found) this.content.append(node);
				}
				prev = node;
			}
			if (pos < numNodes) setTimeout(() => nextBatch(), 1);
		};
		nextBatch();
	}
}

/* EOF */ }

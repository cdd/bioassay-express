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
	Show the branch hierarchy for selecting an ontology URI.
*/

const CSS_ONTOLOGYWIDGET = `
	*.bae-ontologywidget-basis
	{
		font-weight: bold;
		padding: 0 0.2em 0 0.2em;
		margin: 0 -0.2em 0 -0.2em;
		border-radius: 0.2em;
	}
	*.bae-ontologywidget-term
	{
	}
	*.bae-ontologywidget-term:hover
	{
		text-decoration: underline;
		background-color: #F0F0F0;
		color: #313A44;
		cursor: pointer;
	}
	*.bae-ontologywidget-selected
	{
		background-color: black !important;
		color: white !important;
	}
	*.bae-ontologywidget-preincluded
	{
		background-color: #E6EDF2;
	}
	*.bae-ontologywidget-preexcluded
	{
		background-color: #FCDB86;
	}
	*.bae-ontologywidget-matched
	{
		background-color: #FEFBE5;
	}
`;

interface OntologyBranchWidget extends OntologyBranch
{
	divLine?:DOM;
	divLabel?:DOM;
	divHier?:DOM;
	imgCheck?:DOM;
	isOpen?:boolean;
	isLoaded?:boolean;
	childWidgets?:OntologyBranchWidget[];
	sequence?:string[];
}

export interface PickOntologyWidgetPreSel
{
	uri:string;
	include:boolean; // true=they're in, false=they're out
	andChildren:boolean; // true=include children
}

export class PickOntologyWidget
{
	public assn:TemplateAssignment = null; // required if this is being used to show a "schema"
	public openDepth = 0; // auto-open up to depth level

	private divMain:DOM;

	private roots:OntologyBranchWidget[] = [];
	private branchMap = new Map<string, OntologyBranchWidget>();

	private callbackPicked:(branch:OntologyBranch) => void = null;
	private callbackDecorate:(branch:OntologyBranch, div:DOM) => void = null;

	constructor(private type:'property' | 'value' | 'schema', private selected:string[] = [], private preSelected:PickOntologyWidgetPreSel[] = [])
	{
		wmk.installInlineCSS('ontologywidget', CSS_ONTOLOGYWIDGET);
	}

	public render(domParent:DOM):void
	{
		this.divMain = dom('<div/>').appendTo(domParent);
		this.divMain.setText('Loading...');

		(async () =>
		{
			let roots = await this.downloadTrees({'branches': [], 'maxDepth': 1, 'withOther': true, 'priority': this.selected});
			this.divMain.empty();

			roots.sort((r1, r2) => r1.label.localeCompare(r2.label));

			for (let root of roots)
			{
				this.roots.push(root);
				await this.buildBranch(this.divMain, [], root);
			}
			let wantList = [...this.selected, ...this.preSelected.map((pre) => pre.uri)].filter((uri) => !this.branchMap.get(uri));
			await this.ensureSelection(wantList);
			this.showSelected();
		})();
	}

	// make sure the scroll view shows selected term(s)
	public showSelected():void
	{
		// (if there's more than one, maybe select the "highest"?)
		if (this.selected.length > 0)
		{
			let widget = this.branchMap.get(this.selected[0]);
			if (widget) widget.divLine.el.scrollIntoView({'block': 'center', 'behavior': 'smooth'});
		}
	}

	// note that the branch is guaranteed to have description filled out, or empty string if none
	public onPicked(callback:(branch:OntologyBranch) => void):void
	{
		this.callbackPicked = callback;
	}

	// option to add decorations (e.g. buttons) to the right of the text
	public onDecorate(callback:(branch:OntologyBranch, div:DOM) => void):void
	{
		this.callbackDecorate = callback;
	}

	// replace the current selection with just the indicated term
	public async changeSelection(uri:string):Promise<void>
	{
		for (let look of this.selected)
		{
			let widget = this.branchMap.get(look);
			if (widget) widget.divLabel.toggleClass({'bae-ontologywidget-selected': false, 'bae-ontologywidget-term': true});
		}

		uri = expandPrefix(uri);
		this.selected = [uri];

		for (let look of this.selected)
		{
			let widget = this.branchMap.get(look);
			if (widget) widget.divLabel.toggleClass({'bae-ontologywidget-term': false, 'bae-ontologywidget-selected': true});
		}

		let wantList = [...this.selected, ...this.preSelected.map((pre) => pre.uri)].filter((uri) => !this.branchMap.get(uri));
		await this.ensureSelection(wantList);
	}

	// fetches the desired branch, which could be in the hierarchy already, or it may need to be fetched; note that description is guaranteed to be filled
	// in if available, which may involve a trip to the server, even if the rest of it was loaded; the state of children (loaded or placeholders) could go
	// either way
	public async getBranch(uri:string):Promise<OntologyBranch>
	{
		// if it's already loaded and has had the description pulled out too, return that
		let found:OntologyBranch = this.branchMap.get(uri);
		if (found && found.descr != null) return found;

		// fetch it from the server (either wasn't present, or description was missing)
		let trees = await this.downloadTrees({'branches': [uri], 'maxDepth': 0, 'withDescr': true, 'withOther': true});
		for (let branch of trees) if (expandPrefix(branch.uri) == uri)
		{
			if (found)
				found.descr = branch.descr || '';
			else
				found = branch;
			return found;
		}

		return null;
	}

	// given a search result for a term that's known to be in the hierarchy, manufacture if necessary, open trees, then make sure it's selected
	public async selectSearchResult(matches:OntologySearch[], idx:number):Promise<void>
	{
		let match = matches[idx];
		let widget = this.branchMap.get(match.uri);
		if (!widget)
		{
			await this.ensureSelection([match.uri]);
			widget = this.branchMap.get(match.uri);
		}

		// make sure all ancestors are open
		for (let n = 0; n < widget.sequence.length - 1; n++)
		{
			let ancestor = this.branchMap.get(widget.sequence[n]);
			if (!ancestor.isOpen) await this.toggleBranch(ancestor);
		}
		widget.divLine.el.scrollIntoView({'block': 'center', 'behavior': 'smooth'});

		// update search-selection status for all widgets
		let allURI = new Set<string>();
		for (let match of matches) allURI.add(match.uri);
		for (let widget of this.branchMap.values()) widget.divLabel.toggleClass({'bae-ontologywidget-matched': allURI.has(widget.uri)});
	}

	// remove any search highlighting from previous result shown
	public deselectSearchResults():void
	{
		for (let widget of this.branchMap.values()) widget.divLabel.removeClass('bae-ontologywidget-matched');
	}

	// ------------ private methods ------------

	// fetch the desired tree branches: this forks depending on whether we're pulling directly from the baseline ontology or composing a schema tree
	private async downloadTrees(params:Record<string, any>):Promise<OntologyBranch[]>
	{
		let result:any;
		await wmk.yieldDOM();
		if (this.assn == null)
		{
			params = {'type': this.type, ...params};
			result = await asyncREST('REST/OntologyBranch', params);
		}
		else
		{
			params = {'values': this.assn.values, ...params};
			result = await asyncREST('REST/SchemaBranch', params);
		}

		return result.trees;
	}

	private async buildBranch(parent:DOM, sequence:string[], widget:OntologyBranchWidget):Promise<void>
	{
		widget.uri = expandPrefix(widget.uri);
		widget.sequence = [...sequence, widget.uri];

		this.branchMap.set(widget.uri, widget);

		widget.isOpen = widget.isOpen || sequence.length < this.openDepth;
		widget.isLoaded = false;
		widget.childWidgets = [];

		let divLine = widget.divLine = dom('<div/>').appendTo(parent);

		let divChk = dom('<div/>').appendTo(divLine).css({'display': 'inline-block', 'margin-right': '0.5em'});
		let img = widget.imgCheck = dom('<img/>').appendTo(divChk);
		if (widget.descendents)
		{
			let fn = widget.isOpen ? 'branch_close.svg' : 'branch_open.svg';
			img.setAttr('src', restBaseURL + '/images/' + fn);
			img.css({'width': '20px', 'height': '20px', 'cursor': 'pointer'});
			img.onClick(() => {this.toggleBranch(widget).then();});
		}
		else
		{
			img.setAttr('src', restBaseURL + '/images/branch_dot.svg');
			img.css({'width': '20px', 'height': '20px', 'cursor': 'default'});
		}

		let includeStatus = 0; // 1=included, 0=nothing, -1=excluded
		for (let presel of this.preSelected) if (presel.include && presel.andChildren)
		{
			if (widget.sequence.includes(presel.uri)) includeStatus = 1;
		}
		for (let presel of this.preSelected) if (!presel.include && presel.andChildren)
		{
			if (widget.sequence.includes(presel.uri)) includeStatus = -11;
		}
		for (let presel of this.preSelected) if (!presel.andChildren && widget.uri == presel.uri)
		{
			includeStatus = presel.include ? 1 : -1;
		}

		let divLabel = widget.divLabel = dom('<div/>').appendTo(divLine).class('bae-ontologywidget-basis').css({'display': 'inline-block'});

		if (this.selected.includes(expandPrefix(widget.uri)))divLabel.class('bae-ontologywidget-selected');
		else if (!this.callbackPicked) {} // no hover because it's not selectable
		else if (includeStatus > 0) divLabel.class('bae-ontologywidget-preincluded bae-ontologywidget-term');
		else if (includeStatus < 0) divLabel.class('bae-ontologywidget-preexcluded bae-ontologywidget-term');
		else divLabel.class('bae-ontologywidget-term');
		divLabel.setText(widget.label);
		divLabel.onClick(() => {this.respondClick(widget).then();});

		let divDecor = dom('<div/>').appendTo(divLine).css({'display': 'inline-block', 'margin-left': '0.5em'});
		if (widget.descendents > 0) divDecor.appendText(`(${widget.descendents})`);

		if (this.callbackDecorate)
		{
			let divDeco = dom('<div/>').css({'display': 'inline-flex', 'margin-left': '0.5em', 'gap': '0.5em'});
			this.callbackDecorate(widget, divDeco);
			if (divDeco.children.length > 0) divLine.append(divDeco);
		}

		// the help text pop-clock
		let [title, content] = Popover.popoverOntologyTerm(widget.uri, widget.label, null, widget.altLabels, widget.externalURLs);

		let info:string[] = [];
		/*if (provisional)
		{
			let tag = 'provisional';
			if (provisional.role) tag += ': ' + provisional.role;
			info.push(tag);
		}*/
		if (info.length > 0) content += '<p style="color: #808080;">(' + info.join(', ') + ')</p>';

		Popover.click(divDecor, title, content, {'valueURI': widget.uri});

		let divHier = widget.divHier = dom('<div/>').appendTo(parent).css({'margin-left': '1em'});
		divHier.setCSS('display', widget.isOpen ? 'block' : 'none');
		if (widget.isOpen && !widget.isLoaded) await this.loadBranch(widget);
	}

	private async loadBranch(widget:OntologyBranchWidget):Promise<void>
	{
		if (widget.placeholders)
		{
			widget.divHier.append(dom('<span class="glyphicon glyphicon-time" style="width: 0.9em; height: 1.2em;"/>'));
			await this.fetchBranch(widget);
			return;
		}

		widget.divHier.empty();

		let children = widget.children ? widget.children.slice(0) : [];
		children.sort((c1, c2) => c1.label.localeCompare(c2.label));

		for (let child of children)
		{
			let childWidget = child as OntologyBranchWidget;
			widget.childWidgets.push(childWidget);
			await this.buildBranch(widget.divHier, widget.sequence, childWidget);
		}
		widget.isLoaded = true;
	}

	private async fetchBranch(widget:OntologyBranchWidget):Promise<void>
	{
		let trees = await this.downloadTrees({'branches': [widget.uri], 'maxDepth': 1, 'withOther': true});
		let branch = trees[0];
		widget.children = branch.children;
		widget.placeholders = undefined;
		await this.loadBranch(widget);
	}

	private async toggleBranch(widget:OntologyBranchWidget):Promise<void>
	{
		widget.isOpen = !widget.isOpen;
		let fn = widget.isOpen ? 'branch_close.svg' : 'branch_open.svg';
		widget.imgCheck.setAttr('src', restBaseURL + '/images/' + fn);

		widget.divHier.setCSS('display', widget.isOpen ? 'block' : 'none');
		if (widget.isOpen && !widget.isLoaded) await this.loadBranch(widget);
	}

	private async respondClick(widget:OntologyBranchWidget):Promise<void>
	{
		if (!this.callbackPicked) return;
		if (widget.descr != null) this.callbackPicked(widget);

		let trees = await this.downloadTrees({'branches': [widget.uri], 'maxDepth': 0, 'withDescr': true, 'withOther': true});
		for (let branch of trees) if (expandPrefix(branch.uri) == widget.uri)
		{
			widget.descr = branch.descr || '';
			let {uri, label, descr, altLabels, externalURLs, descendents} = widget;
			this.callbackPicked({uri, label, descr, altLabels, externalURLs, descendents});
		}
	}

	// makes sure the selected terms are loaded and expanded, so they can be viewed (and scrolled to)
	private async ensureSelection(wantList:string[]):Promise<void>
	{
		let filterAlreadyOpen = (widget:OntologyBranchWidget):void =>
		{
			if (wantList.length == 0) return;
			let idx = wantList.indexOf(widget.uri);
			if (idx >= 0) wantList.splice(idx, 1);
			if (widget.isOpen && widget.childWidgets) for (let child of widget.childWidgets) filterAlreadyOpen(child);
		};
		for (let root of this.roots) filterAlreadyOpen(root);

		let scanAndLoad = async (branch:OntologyBranch, widget:OntologyBranchWidget, finalURI:string):Promise<void> =>
		{
			if (!branch.children) return;
			if (!widget.children)
			{
				let children = branch.children, branchURI = expandPrefix(branch.uri);
				if (finalURI != branchURI)
				{
					// it's a pruned one-child-only version, so fetch a shallow subset
					let trees = await this.downloadTrees({'branches': [branchURI], 'maxDepth': 1, 'withDescr': false, 'withOther': true});
					children = trees[0].children;
				}

				widget.children = children as OntologyBranchWidget[];
				widget.placeholders = undefined;
			}
			if (!widget.isOpen) await this.toggleBranch(widget);
			for (let child of branch.children) await scanAndLoad(child, this.branchMap.get(expandPrefix(child.uri)), finalURI);
		};

		for (let wantURI of wantList)
		{
			let trees = await this.downloadTrees({'branches': [], 'maxDepth': 1, 'withDescr': false, 'withOther': true, 'priority': [wantURI], 'pruned': true});
			for (let branch of trees) await scanAndLoad(branch, this.branchMap.get(expandPrefix(branch.uri)), wantURI);
		}
	}
}

/* EOF */ }

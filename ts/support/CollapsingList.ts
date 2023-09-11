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
	Collapsing List: given a bunch of preliminary information about a bunch of lines that should be arranged as a tree,
	creates the check open/closed buttons as necessary.

	lineTree: the list of lines that are potentially affected by the open/closedness
	toggleTree: an area within the corresponding line where the [+]/[-] button is drawn (may be null for leaf nodes)
	parentIdx: the tree relationship; root nodes have a value of -1
*/

export class CollapsingList
{
	private shownState:string[] = []; // the string used for visible; varies depending on DOM type (e.g. block, inline-block)
	private currentSearch:string;
	private toggleImages:JQuery[] = [];
	private hasChildren:boolean[] = [];

	constructor(private lineTree:JQuery[], private toggleTree:JQuery[], private parentIdx:number[],
				public branchOpen:boolean[] = [], public nodeContentList:any[] = [])
	{
	}

	// change the search parameters, and update the view accordingly
	public applySearch(txt:string, hideToggle:boolean):void
	{
		this.currentSearch = txt.trim();

		if (this.currentSearch == '') {this.expandTree(true); return;}
		this.collapseTree(true);

		let numNodes = this.nodeContentList.length;
		txt = txt.trim().toLowerCase();

		for (let n = 0; n < numNodes; n++)
		{
			let nodeContent = this.nodeContentList[n];

			if (nodeContent.name.toLowerCase().includes(txt))
			{
				let pos = n;
				while (pos >= 0)
				{
					let nodeContainer = this.lineTree[pos];
					nodeContainer.css('display', 'block');
					nodeContainer.css('background-color', 'cornsilk');
					pos = this.parentIdx[pos];
					this.branchOpen[pos] = true;
				}
			}
			else
			{
				this.branchOpen[n] = !hideToggle;
			}
		}

		this.updateVisibility();
	}

	// creates the UI content from scratch, based on the parameters; if the branchOpen flags are not defined yet, they will be set
	// to everything open
	public manufacture():void
	{
		let sz = this.lineTree.length;
		while (this.branchOpen.length < sz) this.branchOpen.push(true);

		this.hasChildren = Vec.booleanArray(false, sz);
		for (let n = 0; n < sz; n++)
		{
			this.shownState.push(this.lineTree[n].css('display'));
			if (this.parentIdx[n] >= 0) this.hasChildren[this.parentIdx[n]] = true;
		}

		for (let n = 0; n < sz; n++) if (this.toggleTree[n])
		{
			let img = $('<img></img>').appendTo(this.toggleTree[n]);
			if (this.hasChildren[n])
			{
				let fn = this.branchOpen[n] ? 'branch_close.svg' : 'branch_open.svg';
				img.attr('src', restBaseURL + '/images/' + fn);
				img.css({'width': '20px', 'height': '20px', 'cursor': 'pointer'});
				img.click(() => this.toggleBranch(n, img));
			}
			else
			{
				img.attr('src', restBaseURL + '/images/branch_dot.svg');
				img.css({'width': '20px', 'height': '20px', 'cursor': 'default'});
			}

			this.toggleImages[n] = img;
		}

		this.updateVisibility();
	}

	// assuming the branchOpen array is already defined, goes through and closes some of the branches, in order to collapse space; anything with
	// a selected child node is kept open, as are top-level branches
	public partialBranchCollapse(selidx:number[]):void
	{
		let sz = this.branchOpen.length, parent = this.parentIdx;
		let selMask = Vec.idxMask(selidx, sz), keepMask = selMask.slice(0);
		for (let n = 0; n < sz; n++)
		{
			let hit = false;
			for (let idx = n; idx >= 0; idx = parent[idx]) if (selMask[idx]) {hit = true; break;}
			if (hit) for (let idx = n; idx >= 0; idx = parent[idx]) keepMask[idx] = true;
		}
		for (let n = 0; n < sz; n++) if (!keepMask[n])
		{
			let idx = n;
			while (parent[idx] >= 0 && !keepMask[parent[idx]]) idx = parent[idx];
			this.branchOpen[idx] = false;
		}
	}

	// for all of the indicated indices, make sure all parent branches are open, so that they are exposed
	public partialBranchExpand(selidx:number[]):void
	{
		for (let idx of selidx)
		{
			for (let p = this.parentIdx[idx]; p >= 0; p = this.parentIdx[p]) this.branchOpen[p] = true;
		}
	}

	public collapseTree(resetBackgroundColors:boolean):void
	{
		let sz = this.lineTree.length;
		for (let n = 0; n < sz; n++)
		{
			this.branchOpen[n] = false;
			this.lineTree[n].css('display', 'none');
			if (resetBackgroundColors) this.lineTree[n].css('background-color', '');

			// only show the top level -- includes toggle nodes and non-toggle top level node
			if (this.parentIdx[n] < 0) this.lineTree[n].css('display', 'block');

			if (this.hasChildren[n]) this.setExpandCollapseImage(false, n);
		}
	}

	public expandTree(resetBackgroundColors:boolean):void
	{
		let sz = this.lineTree.length;
		for (let n = 0; n < sz; n++)
		{
			this.branchOpen[n] = false;
			this.lineTree[n].css('display', 'block');
			if (resetBackgroundColors) this.lineTree[n].css('background-color', '');

			if (this.hasChildren[n]) this.setExpandCollapseImage(true, n);
		}
	}

	// ------------ private methods ------------

	// responds to a user event for opening/closing a branch
	private toggleBranch(idx:number, img:JQuery):void
	{
		this.branchOpen[idx] = !this.branchOpen[idx];
		this.setExpandCollapseImage(this.branchOpen[idx], idx);
		this.updateVisibility();
	}

	// sets the visibility for all lines: anything that's part of a closed branch is not shown
	private updateVisibility():void
	{
		for (let n = 0; n < this.branchOpen.length; n++)
		{
			let isOpen = true, idx = this.parentIdx[n];
			while (idx >= 0)
			{
				if (!this.branchOpen[idx]) {isOpen = false; break;}
				this.setExpandCollapseImage(isOpen, idx);
				idx = this.parentIdx[idx];
			}
			this.lineTree[n].css('display', isOpen ? this.shownState[n] : 'none');
		}
	}

	private setExpandCollapseImage(isOpen:boolean, idx:number):void
	{
		let fn = isOpen ? 'branch_close.svg' : 'branch_open.svg';
		this.toggleImages[idx].attr('src', restBaseURL + '/images/' + fn);
	}

}

/* EOF */ }

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
	SelectionDataList and SelectionDataTree: manage visibility of terms in list and trees

	BranchGrouper: identify branches in the tree that should be split into groups
*/

export const BRANCH_GROUP = 'branch-group';

export class SelectionData<SelectionDataItem>
{
	public data:SelectionDataItem[] = [];
	public visMask:boolean[] = [];
	public selMask:boolean[] = [];
	public matchCount:number[] = []; // for lists
	public length:number;

	// implement this method in a derived class if visibility of nodes depends on others
	protected propagateVisibility?(newVisible:boolean[]):boolean[];

	constructor(data:SelectionDataItem[], private searchMethod:(element:SelectionDataItem, query:string) => boolean)
	{
		this.setData(data);
	}

	public search(query:string):number[]
	{
		if (query == '')
		{
			let searchResult:boolean[] = new Array(this.data.length).fill(true);
			if (this.propagateVisibility) searchResult = this.propagateVisibility(searchResult);
			this.selMask = this.selMask.fill(false);
			this.matchCount.fill(-1);
			return this.setVisibility(searchResult);
		}
		let searchResult = this.data.map((d) => this.searchMethod(d, query));
		this.matchCount = searchResult.map((match) => match ? 1 : 0);
		if (this.propagateVisibility) searchResult = this.propagateVisibility(searchResult);
		this.selMask = [...searchResult];
		return this.setVisibility(searchResult);
	}

	public countHits():number
	{
		let count = 0;
		for (let selection of this.selMask) if (selection) count++;
		return count;
	}

	public setData(data:SelectionDataItem[])
	{
		this.length = data.length;
		this.data = data;
		this.visMask = new Array(data.length).fill(true);
		this.selMask = new Array(data.length).fill(false);
		this.matchCount = new Array(data.length).fill(-1);
	}

	// select/deselect entries - returns indices of changed entries
	public show(indices:number[]):number[]
	{
		indices = indices.filter((idx) => idx < this.length);
		const newState = [...this.visMask];
		for (let idx of indices) newState[idx] = true;
		return this.setVisibility(newState);
	}

	public hide(indices:number[]):number[]
	{
		indices = indices.filter((idx) => idx < this.length);
		const needChange = indices.filter((idx) => this.visMask[idx]);
		for (let idx of needChange) this.visMask[idx] = false;
		return needChange;
	}

	public reset(newVisible:boolean = true):number[]
	{
		const changed = [];
		for (let n = 0; n < this.visMask.length; n++)
		{
			if (this.visMask[n] == newVisible) continue;
			this.visMask[n] = newVisible;
			changed.push(n);
		}
		return changed;
	}

	public setVisibility(newState:boolean[]):number[]
	{
		const changed = [];
		for (let n = 0; n < this.visMask.length; n++)
		{
			const newVisible =  newState[n];
			if (this.visMask[n] == newVisible) continue;
			this.visMask[n] = newVisible;
			changed.push(n);
		}
		return changed;
	}

	public toConsole(visOnly:boolean = false)
	{
		console.log(`Size of data ${this.data.length}`);
		for (let n = 0; n < this.data.length; n++)
		{
			const d = this.data[n] as any;
			if (visOnly && !this.visMask[n]) continue;
			console.log(`${n} v:${this.visMask[n]} ${d.name}`);
		}
	}
}

interface SelectionDataTreeItem
{
	parent:number;
	depth:number;
	childCount:number;
}

export class SelectionDataList extends SelectionData<any>
{
}

export class SelectionDataTree extends SelectionData<SelectionDataTreeItem>
{
	public openMask:boolean[];
	public childMask:boolean[];

	public toConsole(visOnly:boolean = false)
	{
		console.log(`Size of data ${this.data.length}`);
		for (let n = 0; n < this.data.length; n++)
		{
			const d = this.data[n] as any;
			if (visOnly && !this.visMask[n]) continue;
			console.log(`${n} v:${this.visMask[n]} o:${this.openMask[n]} ${d.name}`);
		}
	}

	public setData(data:SelectionDataTreeItem[])
	{
		super.setData(data);
		this.childMask = new Array(this.visMask.length);
		this.openMask = new Array(this.visMask.length);
		let current = this.data[0];
		for (let n = 0; n < this.childMask.length - 1; n++)
		{
			const next = this.data[n + 1];
			const hasChildren = next.depth > current.depth;
			this.childMask[n] = hasChildren;
			this.openMask[n] = hasChildren;
			current = next;
		}
		this.childMask[this.childMask.length - 1] = false;
		this.openMask[this.openMask.length - 1] = false;
}

	public search(query:string):number[]
	{
		for (let n = 0; n < this.openMask.length; n++) this.openMask[n] = false;
		const result = super.search(query);
		if (query == '') return result;
		for (let n = 0; n < this.matchCount.length; n++)
		{
			if (this.matchCount[n] == 0) continue;
			for (let parent of this.findParents(n)) this.matchCount[parent]++;
			this.matchCount[n]--; // don't count yourself
		}
		return result;
	}

	public showNode(idx:number):number[]
	{
		const changed:number[] = [];
		if (this.visMask[idx]) return changed;
		this.visMask[idx] = true;
		changed.push(idx);
		for (let parent of this.findParents(idx))
		{
			if (this.visMask[parent]) break;
			this.visMask[parent] = true;
			changed.push(parent);
		}
		return changed;
	}

	public show(indices:number[]):number[]
	{
		indices = indices.filter((idx) => idx < this.length);
		let result = [...indices];
		for (let idx of indices) for (let parent of this.findParents(idx))
		{
			result.push(parent);
			this.openMask[parent] = true;
		}
		return super.show(result);
	}

	public hide(indices:number[]):number[]
	{
		indices = indices.filter((idx) => idx < this.length);
		let result = [...indices];
		for (let idx of indices)
		{
			this.openMask[idx] = false;
			for (let child of this.findChildren(idx))
			{
				result.push(child);
				this.openMask[child] = false;
			}
		}
		return super.hide(result);
	}

	public reset(newVisible:boolean = true):number[]
	{
		const changed = super.reset(newVisible);
		for (let idx of changed) this.openMask[idx] = newVisible;
		return changed;
	}

	public openBranch(idx:number):number[]
	{
		const newVisible = [...this.visMask];
		this.openMask[idx] = true;
		newVisible[idx] = true;
		const pdepth = this.data[idx].depth;
		for (let n of this.findChildren(idx))
		{
			newVisible[n] = this.data[n].depth == pdepth + 1;
			this.openMask[n] = false;
		}
		return this.setVisibility(newVisible);
	}

	public partialOpenBranch(idx:number)
	{
		const newVisible = [...this.visMask];
		this.openMask[idx] = true;
		newVisible[idx] = true;
		const pdepth = this.data[idx].depth;
		for (let n of this.findChildren(idx))
		{
			newVisible[n] = (this.data[n].depth == pdepth + 1 && this.selMask[n]);
			this.openMask[n] = false;
		}
		return this.setVisibility(newVisible);
	}

	public closeBranch(idx:number):number[]
	{
		if (!this.openMask[idx]) return [];

		const newVisible = [...this.visMask];
		this.openMask[idx] = false;
		newVisible[idx] = true;
		for (let n of this.findChildren(idx))
		{
			newVisible[n] = false;
			this.openMask[n] = false;
		}
		return this.setVisibility(newVisible);
	}

	public findChildren(idx:number):number[]
	{
		const numNodes = this.data.length;
		const pdepth = this.data[idx].depth;
		const children = [];
		for (let n = idx + 1; n < numNodes; n++)
		{
			if (this.data[n].depth <= pdepth) break;
			children.push(n);
		}
		return children;
	}

	public findDirectChildren(idx:number):number[]
	{
		const numNodes = this.data.length;
		const pdepth = this.data[idx].depth;
		const children = [];
		for (let n = idx + 1; n < numNodes; n++)
		{
			const depth = this.data[n].depth;
			if (depth <= pdepth) break;
			if (depth == pdepth + 1) children.push(n);
		}
		return children;
	}

	public findParents(idx:number):number[]
	{
		const result:number[] = [];
		let pos = this.data[idx].parent;
		while (pos >= 0)
		{
			result.push(pos);
			pos = this.data[pos].parent;
		}
		return result;
	}

	public isPartialOpen(idx:number):boolean
	{
		const children = this.findDirectChildren(idx);
		let count = 0;
		for (let n of children) if (this.visMask[n]) count++;
		return children.length > count && count > 0;
	}

	public isPartialSelected(idx:number):boolean
	{
		const children = this.findDirectChildren(idx);
		let count = 0;
		for (let n of children) if (this.selMask[n]) count++;
		return children.length > count && count > 0;
	}

	// for each item that is visible, make the whole hierarchy visible
	protected propagateVisibility(newVisible:boolean[]):boolean[]
	{
		for (let n = 0; n < newVisible.length; n++)
		{
			if (!newVisible[n]) continue;
			newVisible[n] = true;
			for (let idx of this.findParents(n)) newVisible[idx] = true;
		}
		return newVisible;
	}
}

// identify branches in the tree with large number of nodes and splits into groups
export class BranchGrouper
{
	private nodes:SchemaTreeNode[];

	constructor(private chunkSize:number = 250)
	{
	}

	public processNodes(nodes:SchemaTreeNode[])
	{
		// clone the original data - this is important as we modify the node information
		this.nodes = deepClone(nodes);
		let branch = this.getBranchToGroup();
		while (branch != null)
		{
			this.chunkBranch(branch);
			branch = this.getBranchToGroup();
		}
		return this.nodes;
	}

	protected getBranchToGroup():number[]
	{
		for (let n = 0; n < this.nodes.length; n++)
		{
			// find nodes with more than chunkSize children
			const d = this.nodes[n];
			if (d.childCount <= this.chunkSize) continue;

			// determine positions of direct children
			const cdepth = d.depth + 1;
			const directChildren = [];
			for (let i = n + 1; i <= n + d.childCount; i++) if (this.nodes[i].depth == cdepth) directChildren.push(i);
			if (directChildren.length <= this.chunkSize) continue;
			return directChildren;
		}
		return null;
	}

	protected chunkBranch(branch:number[])
	{
		const parent = this.nodes[branch[0]].parent;
		const parentNode = this.nodes[parent];
		const depth = this.nodes[branch[0]].depth;

		// create group nodes
		const childCountInGroup = [];
		const firstInGroup = [];
		const lastInGroup = [];
		let count = null;
		let last = null;
		for (let n = 0; n < branch.length; n++)
		{
			const node = this.nodes[branch[n]];
			if (n % this.chunkSize == 0)
			{
				if (count != null) childCountInGroup.push(count);
				if (last != null) lastInGroup.push(last);
				count = 0;
				firstInGroup.push(branch[n]);
			}
			if (node.depth == depth) last = branch[n];
			count = count + node.childCount + 1;
		}
		childCountInGroup.push(count);
		lastInGroup.push(last);
		const groupNames = this.groupNames(firstInGroup, lastInGroup);
		const groupNodes:SchemaTreeNode[] = [];
		for (let n = 0; n < groupNames.length; n++)
		{
			groupNodes.push(
				{
					'depth': parentNode.depth + 1,
					'parent': parent,
					'uri': BRANCH_GROUP,
					'abbrev': null,
					'name': groupNames[n],
					'inSchema': false,
					'provisional': null,
					'childCount': childCountInGroup[n],
					'schemaCount': 0,
					'inModel': false,
				}
			);
		}

		// increase depth of nodes in branch
		let firstAfterBranch = branch[0] + parentNode.childCount;
		for (let node of this.nodes.slice(branch[0], firstAfterBranch)) node.depth++;

		// insert groupNodes into nodes
		let lastPos = parent + parentNode.childCount + 1;
		for (let n = firstInGroup.length - 1; n >= 0; n--)
		{
			const insertAt = firstInGroup[n];
			// attach direct children to this groupNode and adjust parent of their children
			for (let node of this.nodes.slice(insertAt, lastPos))
			{
				if (node.parent == parent)
					node.parent = insertAt + n;
				else
					node.parent = node.parent + n + 1;
			}
			this.nodes.splice(insertAt, 0, groupNodes[n]);
			lastPos = insertAt;
		}

		// adjust parent of remaining nodes
		firstAfterBranch += groupNodes.length; // adjust for added group nodes
		for (let n = firstAfterBranch; n < this.nodes.length; n++)
		{
			if (this.nodes[n].parent < parent) continue;
			this.nodes[n].parent = this.nodes[n].parent + groupNodes.length;
		}
	}

	protected groupNames(firstInGroup:number[], lastInGroup:number[]):string[]
	{
		let firstNames = firstInGroup.map((idx) => this.nodes[idx].name);
		let lastNames = lastInGroup.map((idx) => this.nodes[idx].name);
		for (let i = 0; i < firstNames.length; i++)
		{
			let compare = [firstNames[i], lastNames[i]];
			if (i > 0) compare.push(lastNames[i - 1]);
			let minLength = this.minimumDifferentiatingLength(compare);
			if (minLength > 0) firstNames[i] = firstNames[i].substr(0, minLength);

			compare = [firstNames[i], lastNames[i]];
			if (i + 1 < firstNames.length) compare.push(firstNames[i + 1]);
			minLength = this.minimumDifferentiatingLength(compare);
			if (minLength > 0) lastNames[i] = lastNames[i].substr(0, minLength);
		}
		let names = [];
		for (let i = 0; i < firstNames.length; ++i)
			names.push(`[${firstNames[i]} - ${lastNames[i]}]`);
		return names;
	}

	// leave this in for now in case we want to revert later
	protected groupNames2(firstInGroup:number[], lastInGroup:number[]):string[]
	{
		let firstNames = firstInGroup.map((idx) => this.nodes[idx].name);
		let lastNames = lastInGroup.map((idx) => this.nodes[idx].name);

		const minLength = this.minimumDifferentiatingLength([...firstNames, ...lastNames]);
		if (minLength > 0)
		{
			firstNames = firstNames.map((s) => s.substr(0, minLength));
			lastNames = lastNames.map((s) => s.substr(0, minLength));
		}
		const names = [];
		for (let i = 0; i < firstNames.length; ++i)
			names.push(`[${firstNames[i]} - ${lastNames[i]}]`);
		return names;
	}

	protected minimumDifferentiatingLength(names:string[]):number
	{
		const maxLength = Math.max(...names.map((s) => s.length));
		for (let n = 1; n <= maxLength; n++)
		{
			const groupNames = names.map((s) => s.substr(0, n).toLowerCase());
			if (groupNames.length != new Set(groupNames).size) continue;
			return n;
		}
		return 0;
	}
}

/* EOF */ }

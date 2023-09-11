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
	Takes some number of nodes, each of which has at least linear sequence of terms that are pulled from a larger tree.
	When they get clustered, the process involves first narrowing down multiplets (using a greedy winnowing algorithm),
	and then sorting in order to create a tree from the combined collection.
*/

interface TreePatternNode
{
	// one or more branches; e.g.
	//		singlet:   [ [A,B,C] ]
	//		multiplet: [ [A,B], [A,B,C], [A,B,D] ]
	branches:string[][];
}

interface TreePatternDendro
{
	x:number;
	y:number;
	parent:number;
	uri:string;
	group:number[];
}

export class TreePattern
{
	public order:number[] = null; // deliverable: the ordered sequence
	public dendrogram:TreePatternDendro[] = []; // deliverable: dendrogram layout
	public maxDepth:number = 0; // longest tree sequence (also height of dendrogram)

	private nodes:TreePatternNode[] = [];

	constructor()
	{
	}

	public addNode(branches:string[][]):void
	{
		if (branches.length == 0) branches = [['?']];

		// remove degenerates and dwarves
		branches.sort((b1:string[], b2:string[]):number => b2.length - b1.length);
		for (let n = branches.length - 1; n >= 0; n--)
		{
			let s = branches[n].join('::');
			for (let i = 0; i < n; i++) if (branches[i].join('::').startsWith(s)) {branches.splice(n, 1); break;}
		}

		this.nodes.push({'branches': branches});
	}

	// arrange the nodes, by converting them into singlets, then sorting
	public cluster():void
	{
		// reduction of multiplets
		let singlets:number[] = [], multiplets:number[] = [];
		for (let n = 0; n < this.nodes.length; n++) if (this.nodes[n].branches.length <= 1) singlets.push(n); else multiplets.push(n);
		while (multiplets.length > 0) this.collapseMultiplets(singlets, multiplets);

		// sort by residual tree hierarchy
		let tokens:string[] = [];
		for (let node of this.nodes) tokens.push(node.branches[0].join('::'));
		this.order = Vec.idxSort(tokens);

		// plot the dendrogram
		let branches:string[][] = [];
		for (let idx of this.order)
		{
			let b = this.nodes[idx].branches[0];
			this.maxDepth = Math.max(this.maxDepth, b.length);
			branches.push(b);
		}
		this.recursiveDendrogram(branches, -1, 0, 0, branches.length);
	}

	// ------------ private methods ------------

	// collapses at least one multiplet, and moves their indices over to the singlets category
	private collapseMultiplets(singlets:number[], multiplets:number[]):void
	{
		// identify the multiplet that has a constituent with the most tree-branch similarity to singlets (if any) or other
		// multiplets (otherwise)
		let overlap:number[][] = [];
		let ovscore:number[] = [];
		let compareRef = singlets.length > 0 ? singlets : multiplets;
		for (let n = 0; n < multiplets.length; n++)
		{
			let ov = this.calculateOverlapScore(multiplets[n], compareRef);
			overlap.push(ov);
			let ordered = Vec.sorted(ov);
			ovscore.push(ordered[ordered.length - 1] - ordered[ordered.length - 2]);
		}

		// operate on the one with the highest max score, and pick the highest sub-branch score within it
		let midx = Vec.idxMax(ovscore), bidx = Vec.idxMax(overlap[midx]);
		let node = this.nodes[multiplets[midx]];
		node.branches = [node.branches[bidx]];

		singlets.push(multiplets[midx]);
		multiplets.splice(midx, 1);

		// final fallback: pretty much arbitrary; find the longest branch, then collapse the first instance with a branch this long
		/*let longest = 0;
		for (let idx of multiplets) for (let b of this.nodes[idx].branches) longest = Math.max(longest, b.length);
		for (let n = 0; n < multiplets.length; n++)
		{
			let node = this.nodes[multiplets[n]];
			for (let i = 0; i < node.branches.length; i++) if (node.branches[i].length == longest)
			{
				node.branches = [node.branches[i]];
				singlets.push(multiplets[n]);
				multiplets.splice(n, 1);
				return;
			}
		}*/
	}

	// for a given node (idx) known to have multiple branches, compare to a list of reference branches which may eachhave one or more
	// branches themselves; return an array of maximum overlap for each of these
	private calculateOverlapScore(idx:number, refs:number[]):number[]
	{
		let b = this.nodes[idx].branches;
		let ov:number[] = [];
		for (let n = 0; n < b.length; n++)
		{
			let maxOverlap = 0;
			for (let other of refs) for (let ob of this.nodes[other].branches)
			{
				let same = 0;
				for (same = 0; same < b.length && same < ob.length; same++) if (b[n][same] != ob[same]) break;
				let score = same / Math.max(b.length, ob.length);
				maxOverlap = Math.max(maxOverlap, score);
			}
			ov.push(maxOverlap);
		}
		return ov;
	}

	// process a range of branches, at a certain depth: adds dendrogram points, and continues down the path
	private recursiveDendrogram(branches:string[][], parent:number, depth:number, start:number, size:number):void
	{
		if (size == 0 || depth >= this.maxDepth) return;

		// subdivide into groups of things that are the same at this level
		let groups:number[][] = [];
		for (let n = 0; n < size; n++)
		{
			let idx = start + n, thisBranch = branches[idx];
			if (n == 0 || depth >= thisBranch.length - 1) {groups.push([idx]); continue;}
			let prevBranch = branches[idx - 1];
			if (depth < prevBranch.length && prevBranch[depth] == thisBranch[depth])
				groups[groups.length - 1].push(idx);
			else
				groups.push([idx]);
		}

		// handle each of the groups
		for (let grp of groups)
		{
			let i1 = grp[0], i2 = grp[grp.length - 1];
			let y = grp.length == 1 && depth >= branches[i1].length - 1 ? this.maxDepth - 1 : depth;
			let dendro:TreePatternDendro =
			{
				'x': 0.5 * (i1 + i2),
				'y': y,
				'parent': parent,
				'uri': branches[i1][depth],
				'group': grp
			};
			let didx = this.dendrogram.length;
			this.dendrogram.push(dendro);

			if (grp.length == 1 && depth >= branches[i1].length - 1) continue; // beyond scope

			this.recursiveDendrogram(branches, didx, depth + 1, i1, grp.length);
		}
	}
}

/* EOF */ }

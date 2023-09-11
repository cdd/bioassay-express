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

///<reference path='../../../ts/widgets/selection/SelectionData.ts'/>

namespace BAETest /* BOF */ {

/*
	Tests for BranchGrouper
*/

class MockBranchGrouper extends bae.BranchGrouper
{
	public _groupNames = this.groupNames;
	public _minimumDifferentiatingLength = this.minimumDifferentiatingLength;
}

export class BranchGrouperTest extends TestModule
{
	constructor()
	{
		super('BranchGrouper');
	}

	// small branches keep the tree unchanged
	public async testOnlySmallBranches()
	{
		let grouper = new bae.BranchGrouper(12);
		let nodes = this.createTestTree(this.TREE1);
		let grouped = grouper.processNodes(nodes);
		this.assertTreeEquals(nodes, grouped);

		nodes = this.createTestTree(this.TREE2);
		grouped = grouper.processNodes(nodes);
		this.assertTreeEquals(nodes, grouped);
	}

	public async testInsertSingleGrouping()
	{
		let grouper = new bae.BranchGrouper(4);
		let nodes = this.createTestTree(this.TREE1);
		let grouped = grouper.processNodes(nodes);
		this.assertTreeEquals(this.createTestTree(this.TREE1_4), grouped);
	}

	public async testInsertNestedGrouping()
	{
		let grouper = new bae.BranchGrouper(4);
		let nodes = this.createTestTree(this.TREE2);
		let grouped = grouper.processNodes(nodes);
		this.assertTreeEquals(this.createTestTree(this.TREE2_4), grouped);
	}

	public async testMinimumDifferentiatingLength()
	{
		let grouper = new MockBranchGrouper(4);
		let names = ['testAsync', 'testPrefix', 'testGroupCompare', 'testValidIntegerLiteral'];
		assertEquals(5, grouper._minimumDifferentiatingLength(names));
		names = ['testAsync', 'testAsyna', 'testPrefix', 'testGroupCompare', 'testValidIntegerLiteral'];
		assertEquals(9, grouper._minimumDifferentiatingLength(names));
		names = ['aa', 'ab', 'ac', 'ad'];
		assertEquals(2, grouper._minimumDifferentiatingLength(names));
		names = ['a', 'b', 'c', 'd'];
		assertEquals(1, grouper._minimumDifferentiatingLength(names));
		names = ['a', 'a', 'b', 'c', 'd'];
		assertEquals(0, grouper._minimumDifferentiatingLength(names));
	}

	public async testGroupNames()
	{
		let groupNames = this.GROUPNAMES.split(/[,\n]/).map((s) => s.trim()).filter((s) => s.length > 0);
		let definition = `root,${groupNames.map((s) => '-' + s).join(',')}`;
		let nodes = this.createTestTree(definition);

		// initialize the branch grouper
		let grouper = new MockBranchGrouper(nodes.length);
		this.assertTreeEquals(nodes, grouper.processNodes(nodes));

		let result = grouper._groupNames([1, 3, 5, 7], [2, 4, 6, 8]);
		let expected = ['[abcd - abcx]', '[abcy - an]', '[b - i]', '[s - p]'];
		assertArrayEquals(expected, result);

		result = grouper._groupNames([3, 5, 7], [4, 6, 8]);
		expected = ['[ab - an]', '[b - i]', '[s - p]'];
		assertArrayEquals(expected, result);

		result = grouper._groupNames([5, 7], [6, 8]);
		expected = ['[b - i]', '[s - p]'];
		assertArrayEquals(expected, result);
	}

	private assertTreeEquals(expected:bae.SchemaTreeNode[], obtained:bae.SchemaTreeNode[])
	{
		for (let [idx, [t1, t2]] of bae.enumerate(bae.zipArrays(expected, obtained)))
		{
			try
			{
				assertEquals(t1.name, t2.name);
				assertEquals(t1.depth, t2.depth);
				assertEquals(t1.parent, t2.parent);
				assertEquals(t1.childCount, t2.childCount);
			}
			catch (e)
			{
				console.log([`node-${idx}`, t1, t2]);
				throw e;
			}
		}
	}

	private consoleLogTree(nodes:bae.SchemaTreeNode[])
	{
		for (let idx = 0; idx < nodes.length; idx++)
		{
			let node = nodes[idx];
			let indent = ' '.repeat(node.depth);
			console.log(`${idx.toString().padStart(3)}: ${indent}+ ${node.name} (${node.parent} - ${node.childCount})`);
		}
	}

	private createTestTree(definition:string):bae.SchemaTreeNode[]
	{
		let treeDefinition = definition.split(/[,\n]/).map((s) => s.trim()).filter((s) => s.length > 0);

		let parents = [0];
		let tree:bae.SchemaTreeNode[] = [];
		tree.push(this.createSchemaTreeNode(treeDefinition.shift(), 0, -1));
		for (let definition of treeDefinition)
		{
			let name = definition;
			let depth = 0;
			while (name.startsWith('-'))
			{
				depth += 1;
				name = name.slice(1);
			}
			if (depth > parents.length)
				parents.push(tree.length - 1);
			else while (depth < parents.length)
				parents.pop();
			let parent = parents.slice(-1)[0];
			tree.push(this.createSchemaTreeNode(name, depth, parent));
		}
		let selectionDataTree = new bae.SelectionDataTree(tree, (_d, _q) => true);
		for (let [idx, node] of bae.enumerate(tree))
		{
			// we only count real children, filter BRANCH_GROUP nodes
			let children = selectionDataTree.findChildren(idx);
			children = children.filter((i) => !tree[i].name.startsWith('['));
			node.childCount = children.length;
		}
		return tree;
	}

	private createSchemaTreeNode(name:string, depth:number, parent:number, childCount:number = 0, uri:string = null):bae.SchemaTreeNode
	{
		return {
			'depth': depth,
			'parent': parent,
			'uri': uri || name,
			'abbrev': uri || name,
			'name': name,
			'inSchema': true,
			'provisional': null,
			'childCount': childCount,
			'schemaCount': childCount,
			'inModel': true,
		};
	}

	private TREE1 = `
	root
	-cat1
	--a,--b,--c,--d,--e,--f,--g,--h,--i
	-cat2
	--aa,--ab,--ac,--ad,--ae,--af,--ag,--ah,--ai
	-cat3
	-cat4
	`;

	private TREE1_4 = `
	root
	-cat1
	--[a - d]
	---a,---b,---c,---d,
	--[e - h]
	---e,---f,---g,---h,
	--[i - i]
	---i
	-cat2
	--[aa - ad]
	---aa,---ab,---ac,---ad,
	--[ae - ah]
	---ae,---af,---ag,---ah,
	--[ai - ai]
	---ai
	-cat3
	-cat4
	`;

	private TREE2 = `
	root
	-cat1
	--a,--b,--c,
	---ca,---cb,---cc,---cd,---ce,---cf,---cg,---ch,---ci
	--d,
	--e,--f,--g,--h,
	--i
	-cat2
	-cat3
	-cat4
	`;

	private TREE2_4 = `
	root
	-cat1
	--[a - d]
	---a,---b,---c,
	----[ca - cd]
	-----ca,-----cb,-----cc,-----cd,
	----[ce - ch]
	-----ce,-----cf,-----cg,-----ch,
	----[ci - ci]
	-----ci
	---d,
	--[e - h]
	---e,---f,---g,---h,
	--[i - i]
	---i
	-cat2
	-cat3
	-cat4
	`;

	private GROUPNAMES = `
	abcde,abcxy
	abcyz,another
	bug,init
	search,partial
	`;
}

/* EOF */ }

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

namespace BAETest /* BOF */ {

/*
	Tests for dialog selection datastructures.
*/

class TestSelectionDataList extends bae.SelectionData<number>
{
	constructor(data:number[] = [])
	{
		super(data, (e:number, query:string) => e < parseInt(query));
	}
}

export class SelectionDataTest extends TestModule
{
	constructor()
	{
		super('SelectionData');
	}

	// make sure it got initialised properly
	public async testInit()
	{
		this.assertInit(new TestSelectionDataList(), [], 'empty list init failed');
		this.assertInit(new TestSelectionDataList([1, 2, 3, 4]), [1, 2, 3, 4], 'short list init failed');
	}

	public async testSetData()
	{
		const data0 = [1, 2, 3];
		const data1 = [1, 2, 3, 4, 5];
		const selectionData = new TestSelectionDataList(data0);
		this.assertInit(selectionData, data0);

		selectionData.setData(data1);
		this.assertInit(selectionData, data1);
	}

	private assertInit(selectionData:TestSelectionDataList, expected:number[], msg?:string)
	{
		let {data, visMask} = selectionData;
		assertEquals(expected.length, data.length, msg);
		assertArrayEquals(expected, data, msg);
		assertEquals(expected.length, visMask.length, msg);
		assertFalse(visMask.includes(false), msg);
	}

	public async testVisibilityState()
	{
		let selectionData = new TestSelectionDataList([100, 101, 102, 103]);
		assertArrayEquals(selectionData.visMask, [true, true, true, true]);

		assertArrayEquals(selectionData.hide([0]), [0]);
		assertArrayEquals(selectionData.visMask, [false, true, true, true]);

		// method only returns changed indices
		assertArrayEquals(selectionData.hide([0]), []);

		assertArrayEquals(selectionData.show([0]), [0]);
		assertArrayEquals(selectionData.visMask, [true, true, true, true]);
		assertArrayEquals(selectionData.show([0]), []);

		assertArrayEquals(selectionData.hide([1, 3]), [1, 3]);
		assertArrayEquals(selectionData.hide([0, 3]), [0]);
		assertArrayEquals(selectionData.visMask, [false, false, true, false]);
	}

	public async testResetVisibilityState()
	{
		let selectionData = new TestSelectionDataList([100, 101, 102, 103]);

		assertArrayEquals(selectionData.hide([1, 3]), [1, 3]);
		assertArrayEquals(selectionData.visMask, [true, false, true, false]);

		assertArrayEquals(selectionData.reset(), [1, 3]);
		assertArrayEquals(selectionData.visMask, [true, true, true, true]);
		assertArrayEquals(selectionData.reset(), []);

		assertArrayEquals(selectionData.reset(false), [0, 1, 2, 3]);
		assertArrayEquals(selectionData.visMask, [false, false, false, false]);
		assertArrayEquals(selectionData.reset(true), [0, 1, 2, 3]);
		assertArrayEquals(selectionData.visMask, [true, true, true, true]);
	}

	public async testSetVisibility()
	{
		// can replace visibility state with new state
		let selectionData = new TestSelectionDataList([100, 101, 102, 103]);
		let newState = [true, false, true, false];
		assertArrayEquals(selectionData.setVisibility(newState), [1, 3]);
		assertArrayEquals(selectionData.setVisibility(newState), []);
		assertArrayEquals(selectionData.visMask, newState);

		newState = [true, true, false, false];
		assertArrayEquals(selectionData.setVisibility(newState), [1, 2]);
		assertArrayEquals(selectionData.setVisibility(newState), []);
		assertArrayEquals(selectionData.visMask, newState);
	}

	public async testSearch()
	{
		// changes visibility state based on search (note definition of search function)
		let selectionData = new TestSelectionDataList([6, 5, 4, 3]);
		assertArrayEquals(selectionData.search('4'), [0, 1, 2]);
		assertArrayEquals(selectionData.visMask, [false, false, false, true]);
		assertArrayEquals(selectionData.selMask, [false, false, false, true]);
		assertArrayEquals(selectionData.search('4'), []);
		assertArrayEquals(selectionData.visMask, [false, false, false, true]);
		assertArrayEquals(selectionData.selMask, [false, false, false, true]);
		assertArrayEquals(selectionData.search('1'), [3]);
		assertArrayEquals(selectionData.visMask, [false, false, false, false]);
		assertArrayEquals(selectionData.selMask, [false, false, false, false]);
		assertArrayEquals(selectionData.search('100'), [0, 1, 2, 3]);
		assertArrayEquals(selectionData.visMask, [true, true, true, true]);
		assertArrayEquals(selectionData.selMask, [true, true, true, true]);
		assertArrayEquals(selectionData.search('100'), []);
	}
}

type SelectionDataTree = bae.SelectionDataTree;

class TreeData
{
	public childCount:number = 0;
	public depth:number = 0;
	constructor(public value:number, public parent:number = -1)
	{ }
}

export class SelectionDataTreeTest extends TestModule
{
	private selectionData:SelectionDataTree;

	constructor()
	{
		super('SelectionDataTree');
	}

	public async beforeEach()
	{
		// data = 0---1---2
		//          |   +-3
		//          |
		//          +-4---5---6
		//            |
		//            +---7---8
		const data = [-1, 0, 1, 1, 0, 4, 5, 4, 7].map((p, v) => new TreeData(v, p));
		for (let d of data)
		{
			d.depth = 0;
			let pos = d.parent;
			while (pos >= 0)
			{
				d.depth++;
				data[pos].childCount += 1;
				pos = data[pos].parent;
			}
		}
		assertArrayEquals([0, 1, 2, 2, 1, 2, 3, 2, 3], data.map((d) => d.depth));
		assertArrayEquals([8, 2, 0, 0, 4, 1, 0, 1, 0], data.map((d) => d.childCount));
		this.selectionData = new bae.SelectionDataTree(data, (e:TreeData, query:string) => e.value == parseInt(query));
	}

	public async testInitializationEmpty()
	{
		let tree = new bae.SelectionDataTree([], (e:TreeData, query:string) => e.value == parseInt(query));
		assertEquals(0, tree.length);
		assertEquals(0, tree.visMask.length);
		assertEquals(0, tree.openMask.length);
		assertEquals(0, tree.childMask.length);
	}

	public async testInitialization()
	{
		const size = 9;
		let tree = this.selectionData;
		assertEquals(size, tree.length);
		assertEquals(size, tree.visMask.length);
		assertEquals(size, tree.openMask.length);
		assertEquals(size, tree.childMask.length);

		this.assertVisible(tree, [0, 1, 2, 3, 4, 5, 6, 7, 8]);
		this.assertOpenMask(tree, [0, 1, 4, 5, 7]);
		this.assertChildMask(tree, [0, 1, 4, 5, 7]);
	}

	public async testShowNodesShowsParents()
	{
		let tree = this.selectionData;
		tree.reset(false);

		assertArrayEquals([0, 4], tree.show([4]));
		this.assertVisible(tree, [0, 4]);
		this.assertOpenMask(tree, [0]);

		assertArrayEquals([7, 8], tree.show([8]));
		this.assertVisible(tree, [0, 4, 7, 8]);
		this.assertOpenMask(tree, [0, 4, 7]);

		assertArrayEquals([1, 3], tree.show([3]));
		this.assertVisible(tree, [0, 1, 3, 4, 7, 8]);
		this.assertOpenMask(tree, [0, 1, 4, 7]);

		assertArrayEquals([5, 6], tree.show([5, 6]));
		this.assertVisible(tree, [0, 1, 3, 4, 5, 6, 7, 8]);
		this.assertOpenMask(tree, [0, 1, 4, 5, 7]);
	}

	public async testHideNodesHidesParents()
	{
		let tree = this.selectionData;
		tree.reset(true);

		this.assertVisible(tree, [0, 1, 2, 3, 4, 5, 6, 7, 8]);
		this.assertOpenMask(tree, [0, 1, 4, 5, 7]);

		assertArrayEquals([4, 5, 6, 7, 8], tree.hide([4]));
		this.assertVisible(tree, [0, 1, 2, 3]);
		this.assertOpenMask(tree, [0, 1]);

		assertArrayEquals([], tree.hide([8]));
		this.assertVisible(tree, [0, 1, 2, 3]);
		this.assertOpenMask(tree, [0, 1]);

		assertArrayEquals([3], tree.hide([3]));
		this.assertVisible(tree, [0, 1, 2]);
		this.assertOpenMask(tree, [0, 1]);

		assertArrayEquals([0, 1, 2], tree.hide([0]));
		this.assertVisible(tree, []);
		this.assertOpenMask(tree, []);
	}

	public async testPropagateVisibility()
	{
		let tree = this.selectionData;
		tree.reset(false);

		assertArrayEquals([0], tree.search('0'));
		this.assertVisible(tree, [0]);

		assertArrayEquals([1], tree.search('1'));
		this.assertVisible(tree, [0, 1]);

		assertArrayEquals([1, 4], tree.search('4'));
		this.assertVisible(tree, [0, 4]);

		assertArrayEquals([5, 6], tree.search('6'));
		this.assertVisible(tree, [0, 4, 5, 6]);

		assertArrayEquals([0, 4, 5, 6], tree.reset(false));
	}

	public async testFindChildren()
	{
		let tree = this.selectionData;
		tree.reset(true);

		assertArrayEquals([1, 2, 3, 4, 5, 6, 7, 8], tree.findChildren(0));
		assertArrayEquals([2, 3], tree.findChildren(1));
		assertArrayEquals([], tree.findChildren(2));
		assertArrayEquals([], tree.findChildren(3));
		assertArrayEquals([5, 6, 7, 8], tree.findChildren(4));
		assertArrayEquals([6], tree.findChildren(5));
		assertArrayEquals([], tree.findChildren(6));
		assertArrayEquals([8], tree.findChildren(7));
		assertArrayEquals([], tree.findChildren(8));
	}

	public async testFindDirectChildren()
	{
		let tree = this.selectionData;
		tree.reset(true);

		assertArrayEquals([1, 4], tree.findDirectChildren(0));
		assertArrayEquals([2, 3], tree.findDirectChildren(1));
		assertArrayEquals([], tree.findDirectChildren(2));
		assertArrayEquals([], tree.findDirectChildren(3));
		assertArrayEquals([5, 7], tree.findDirectChildren(4));
		assertArrayEquals([6], tree.findDirectChildren(5));
		assertArrayEquals([], tree.findDirectChildren(6));
		assertArrayEquals([8], tree.findDirectChildren(7));
		assertArrayEquals([], tree.findDirectChildren(8));
	}

	public async testFindParents()
	{
		let tree = this.selectionData;
		tree.reset(true);

		assertArrayEquals([], tree.findParents(0));
		assertArrayEquals([0], tree.findParents(1));
		assertArrayEquals([1, 0], tree.findParents(2));
		assertArrayEquals([1, 0], tree.findParents(3));
		assertArrayEquals([0], tree.findParents(4));
		assertArrayEquals([4, 0], tree.findParents(5));
		assertArrayEquals([5, 4, 0], tree.findParents(6));
		assertArrayEquals([4, 0], tree.findParents(7));
		assertArrayEquals([7, 4, 0], tree.findParents(8));
	}

	public async testOpenCloseBranches()
	{
		let tree = this.selectionData;
		tree.reset(false);

		assertArrayEquals([0, 1, 4], tree.openBranch(0));
		this.assertVisible(tree, [0, 1, 4]);
		this.assertOpenMask(tree, [0]);

		assertArrayEquals([5, 7], tree.openBranch(4));
		this.assertVisible(tree, [0, 1, 4, 5, 7]);
		this.assertOpenMask(tree, [0, 4]);

		assertArrayEquals([6], tree.openBranch(5));
		this.assertVisible(tree, [0, 1, 4, 5, 6, 7]);
		this.assertOpenMask(tree, [0, 4, 5]);

		assertArrayEquals([], tree.closeBranch(7));
		this.assertVisible(tree, [0, 1, 4, 5, 6, 7]);
		this.assertOpenMask(tree, [0, 4, 5]);

		assertArrayEquals([5, 6, 7], tree.closeBranch(4));
		this.assertVisible(tree, [0, 1, 4]);
		this.assertOpenMask(tree, [0]);

		assertArrayEquals([1, 4], tree.closeBranch(0));
		this.assertVisible(tree, [0]);
		this.assertOpenMask(tree, []);
	}

	public async testSearch()
	{
		let tree = this.selectionData;
		assertArrayEquals(tree.search('4'), [1, 2, 3, 5, 6, 7, 8]);
		this.assertVisible(tree, [0, 4]);
		this.assertOpenMask(tree, []);
		this.assertPartialOpen(tree, [0]);
		this.assertPartialSelected(tree, [0]);
		assertArrayEquals(tree.search('4'), []);
		this.assertVisible(tree, [0, 4]);
		this.assertOpenMask(tree, []);
		this.assertPartialOpen(tree, [0]);
		this.assertPartialSelected(tree, [0]);

		assertArrayEquals(tree.search('8'), [7, 8]);
		this.assertVisible(tree, [0, 4, 7, 8]);
		this.assertOpenMask(tree, []);
		this.assertPartialOpen(tree, [0, 4]);
		this.assertPartialSelected(tree, [0, 4]);

		assertArrayEquals(tree.search('100'), [0, 4, 7, 8]);
		this.assertVisible(tree, []);
		this.assertOpenMask(tree, []);
		this.assertPartialOpen(tree, []);
		this.assertPartialSelected(tree, []);

		assertArrayEquals(tree.search('100'), []);
		this.assertVisible(tree, []);
		this.assertOpenMask(tree, []);
		this.assertPartialOpen(tree, []);
		this.assertPartialSelected(tree, []);
	}

	public async testPartialOpenBranch()
	{
		let tree = this.selectionData;
		tree.reset(false);
		assertArrayEquals(tree.search('5'), [0, 4, 5]);
		this.assertSelMask(tree, [0, 4, 5]);
		this.assertPartialSelected(tree, [0, 4]);
		this.assertVisible(tree, [0, 4, 5]);
		this.assertOpenMask(tree, []);
		this.assertPartialOpen(tree, [0, 4]);

		assertArrayEquals([1, 5], tree.openBranch(0));
		this.assertVisible(tree, [0, 1, 4]);
		this.assertOpenMask(tree, [0]);
		this.assertPartialOpen(tree, []);

		assertArrayEquals([5], tree.partialOpenBranch(4));
		this.assertVisible(tree, [0, 1, 4, 5]);
		this.assertOpenMask(tree, [0, 4]);
		this.assertPartialOpen(tree, [4]);

		assertArrayEquals([7], tree.openBranch(4));
		this.assertVisible(tree, [0, 1, 4, 5, 7]);
		this.assertOpenMask(tree, [0, 4]);
		this.assertPartialOpen(tree, []);

		assertArrayEquals([5, 7], tree.closeBranch(4));
		this.assertVisible(tree, [0, 1, 4]);
		this.assertOpenMask(tree, [0]);
		this.assertPartialOpen(tree, []);

		assertArrayEquals([1], tree.partialOpenBranch(0));
		this.assertVisible(tree, [0, 4]);
		this.assertOpenMask(tree, [0]);
		this.assertPartialOpen(tree, [0]);
	}

	private assertTreeState(tree:SelectionDataTree, expected:number[], condition:(n:number) => boolean)
	{
		let actual:number[] = [];
		for (let n = 0; n < tree.length; n++)
			if (condition(n)) actual.push(n);
		assertArrayEquals(expected, actual);
	}

	private assertPartialOpen(tree:SelectionDataTree, expected:number[])
	{
		this.assertTreeState(tree, expected, (n) => tree.isPartialOpen(n));
	}

	private assertPartialSelected(tree:SelectionDataTree, expected:number[])
	{
		this.assertTreeState(tree, expected, (n) => tree.isPartialSelected(n));
	}

	private assertVisible(tree:SelectionDataTree, expected:number[])
	{
		this.assertTreeState(tree, expected, (n) => tree.visMask[n]);
	}

	private assertOpenMask(tree:SelectionDataTree, expected:number[])
	{
		this.assertTreeState(tree, expected, (n) => tree.openMask[n]);
	}

	private assertSelMask(tree:SelectionDataTree, expected:number[])
	{
		this.assertTreeState(tree, expected, (n) => tree.selMask[n]);
	}

	private assertChildMask(tree:SelectionDataTree, expected:number[])
	{
		this.assertTreeState(tree, expected, (n) => tree.childMask[n]);
	}
}

/* EOF */ }

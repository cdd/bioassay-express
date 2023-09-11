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

///<reference path='../../ts/support/TemplateManager.ts'/>

namespace BAETest /* BOF */ {

/*
	Tests for template management functionality.
*/

export class TemplateManagerTest extends TestModule
{
	constructor()
	{
		super('TemplateManager');
	}

	public async testComparePermissiveGroupURI():Promise<void>
	{
		// note: unsuffixed ==> any suffix for this function
		assertTrue(bae.TemplateManager.comparePermissiveGroupURI('foo', 'foo'));
		assertTrue(bae.TemplateManager.comparePermissiveGroupURI('foo', 'foo@1'));
		assertTrue(bae.TemplateManager.comparePermissiveGroupURI('foo@1', 'foo@1'));
		assertFalse(bae.TemplateManager.comparePermissiveGroupURI('foo@1', 'foo@2'));
		assertTrue(bae.TemplateManager.comparePermissiveGroupURI('foo', 'foo@2'));
	}

	public async testAppendRemoveSuffixGroupURI():Promise<void>
	{
		assertEquals('foo@1', bae.TemplateManager.appendSuffixGroupURI('foo', 1));
		assertEquals('foo@1', bae.TemplateManager.appendSuffixGroupURI('foo@2', 1));

		assertEquals('foo', bae.TemplateManager.removeSuffixGroupURI('foo'));
		assertEquals('foo', bae.TemplateManager.removeSuffixGroupURI('foo@1'));
	}

	public async testDecomposeSuffixGroupURI():Promise<void>
	{
		assertArrayEquals(['foo', 0], bae.TemplateManager.decomposeSuffixGroupURI('foo'));
		assertArrayEquals(['foo', 1], bae.TemplateManager.decomposeSuffixGroupURI('foo@1'));
	}

	public async testCompareBaselineGroupNest():Promise<void>
	{
		const MATERIALS:Record<string, any>[] =
		[
			{'first': null, 'second': null, 'match': true},
			{'first': [], 'second': [], 'match': true},
			{'first': null, 'second': [], 'match': true},
			{'first': ['foo'], 'second': ['foo'], 'match': true},
			{'first': ['foo'], 'second': ['bar'], 'match': false},
			{'first': ['foo'], 'second': ['foo', 'foo'], 'match': false},
			{'first': ['foo@1'], 'second': ['foo@2'], 'match': true},
			{'first': ['foo@1'], 'second': ['bar@2'], 'match': false},
			{'first': ['foo@1'], 'second': ['foo@2', 'foo'], 'match': false},
			{'first': ['foo@1', 'bar'], 'second': ['foo@2', 'bar'], 'match': true},
			{'first': ['foo@1', 'bar'], 'second': ['foo@2', 'bar@1'], 'match': false},
			{'first': ['foo@1', 'bar@1'], 'second': ['foo@2', 'bar@2'], 'match': false},
		];

		for (let sample of MATERIALS)
		{
			let groupNest1 = sample.first, groupNest2 = sample.second, match = sample.match;
			let cmp = bae.TemplateManager.compareBaselineGroupNest(groupNest1, groupNest2);
			assertEquals(match, cmp, JSON.stringify(sample));
		}
	}

	public async testBaselineGroup():Promise<void>
	{
		const MATERIALS:Record<string, any>[] =
		[
			{'in': null, 'out': null},
			{'in': [], 'out': []},
			{'in': ['foo'], 'out': ['foo']},
			{'in': ['foo@1'], 'out': ['foo']},
			{'in': ['foo', 'bar'], 'out': ['foo', 'bar']},
			{'in': ['foo@1', 'bar'], 'out': ['foo', 'bar']},
			{'in': ['foo@2', 'bar'], 'out': ['foo', 'bar']},
			{'in': ['foo', 'bar@1'], 'out': ['foo', 'bar@1']},
			{'in': ['foo@1', 'bar@1'], 'out': ['foo', 'bar@1']},
		];

		for (let sample of MATERIALS)
		{
			let preGroupNest = sample.in, postGroupNest = sample.out;
			let groupNest = bae.TemplateManager.baselineGroup(preGroupNest);
			assertArrayEquals(postGroupNest, groupNest);
		}
	}

	public async testGroupSuffix():Promise<void>
	{
		const MATERIALS:Record<string, any>[] =
		[
			{'in': null, 'dupidx': 1, 'out': null},
			{'in': [], 'dupidx': 1, 'out': []},
			{'in': ['foo'], 'dupidx': 1, 'out': ['foo@1']},
			{'in': ['foo@1'], 'dupidx': 1, 'out': ['foo@1']},
			{'in': ['foo', 'bar'], 'dupidx': 1, 'out': ['foo@1', 'bar']},
			{'in': ['foo@1', 'bar'], 'dupidx': 1, 'out': ['foo@1', 'bar']},
			{'in': ['foo@2', 'bar'], 'dupidx': 1, 'out': ['foo@1', 'bar']},
			{'in': ['foo', 'bar@1'], 'dupidx': 1, 'out': ['foo@1', 'bar@1']},
			{'in': ['foo@1', 'bar@1'], 'dupidx': 1, 'out': ['foo@1', 'bar@1']},
			{'in': ['foo', 'bar@1'], 'dupidx': 2, 'out': ['foo@2', 'bar@1']},
			{'in': ['foo@1', 'bar@1'], 'dupidx': 2, 'out': ['foo@2', 'bar@1']},
		];

		for (let sample of MATERIALS)
		{
			let preGroupNest = sample.in, postGroupNest = sample.out, dupidx = sample.dupidx, wantException = !!sample.exception;
			let groupNest = bae.TemplateManager.groupSuffix(preGroupNest, dupidx);
			assertArrayEquals(postGroupNest, groupNest);
		}
	}

	// TODO: harmoniseAnnotations
	// TODO: push made up content into the template cache, and test getting/grafting/relative

}

/* EOF */ }

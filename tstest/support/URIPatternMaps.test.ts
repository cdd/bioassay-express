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

///<reference path='../../ts/support/URIPatternMaps.ts'/>

namespace BAETest /* BOF */ {

/*
	Tests for URIPatternMaps
*/

export class URIPatternMapsTest extends TestModule
{
	constructor()
	{
		super('URIPatternMaps');
	}

	public async testDefaultInitialisation():Promise<void>
	{
		let uriPatternMaps = new bae.URIPatternMaps();
		assertFalse(uriPatternMaps.hasPatterns);
		this.assertCommonMatchesBehavior(uriPatternMaps);
	}

	public async testWithSinglePattern():Promise<void>
	{
		let uriPatternMap:bae.URIPatternMap =
		{
			'externalURL': 'external/$1',
			'matchPrefix': 'geneid:([0-9]*$)',
			'label': 'NCBI-Gene',
		};
		let uriPatternMaps = new bae.URIPatternMaps([uriPatternMap]);
		assertTrue(uriPatternMaps.hasPatterns);
		this.assertCommonMatchesBehavior(uriPatternMaps);
		let matches = [...uriPatternMaps.matches('geneid:1234')];
		assertEquals(1, matches.length);
		assertEquals('external/1234', matches[0].url);
		assertEquals('1234', matches[0].payload);
		assertEquals(1, matches[0].groups.length);
	}

	public async testWithMultiplePatterns():Promise<void>
	{
		let uriPatternMaps = new bae.URIPatternMaps(
			[
				{'externalURL': 'hip/$1', 'matchPrefix': 'geneid:([0-9]*$)', 'label': 'NCBI-Gene'},
				{'externalURL': 'hop/$1', 'matchPrefix': 'geneid:([0-9]*$)', 'label': 'Expasy'},
			]
		);
		assertTrue(uriPatternMaps.hasPatterns);
		this.assertCommonMatchesBehavior(uriPatternMaps);
		let matches = [...uriPatternMaps.matches('geneid:1234')];
		assertEquals(2, matches.length);
		assertEquals('hip/1234', matches[0].url);
		assertEquals('hop/1234', matches[1].url);
	}

	public async testWithMultipleGroups():Promise<void>
	{
		let uriPatternMap:bae.URIPatternMap =
		{
			'externalURL': 'external/$2/$1',
			'matchPrefix': '(.*):([0-9]*$)',
			'label': 'NCBI-Gene',
		};
		let uriPatternMaps = new bae.URIPatternMaps([uriPatternMap]);
		assertTrue(uriPatternMaps.hasPatterns);
		this.assertCommonMatchesBehavior(uriPatternMaps);
		let matches = [...uriPatternMaps.matches('geneid:1234')];
		assertEquals(1, matches.length);
		assertEquals('external/1234/geneid', matches[0].url);
	}

	private assertCommonMatchesBehavior(uriPatternMaps:bae.URIPatternMaps):void
	{
		let matches = [...uriPatternMaps.matches(null)];
		assertEquals(0, matches.length);
		matches = [...uriPatternMaps.matches('any text')];
		assertEquals(0, matches.length);
	}
}

/* EOF */ }

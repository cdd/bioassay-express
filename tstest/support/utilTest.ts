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
	Tests for global utility functions.
*/

export class UtilTest extends TestModule
{
	constructor()
	{
		super('Utilities');
	}

	/*public async beforeAll() {this.log('BEFOREALL!');}
	public async afterAll() {this.log('AFTERALL!');}
	public async beforeEach() {this.log('BEFOREACH!');}
	public async afterEach() {this.log('AFTEREACH!');}*/

	// placeholder for showing how to do asynchronous tests
	public async testAsync():Promise<void>
	{
		return new Promise<void>((resolve) =>
		{
			setTimeout(resolve, 1);
		});
	}

	public async testPrefix():Promise<void>
	{
		let uri = 'http://www.bioassayontology.org/bao#BAO_0002162', pfxd = 'bao:BAO_0002162';

		assertEquals(pfxd, bae.collapsePrefix(uri));
		assertEquals(uri, bae.expandPrefix(pfxd));

		assertArrayEquals([pfxd], [bae.collapsePrefixes([uri])]);
		assertArrayEquals([uri], [bae.expandPrefixes([pfxd])]);
	}

	public async testGroupCompare():Promise<void>
	{
		let [propURI1, propURI2] = bae.expandPrefixes(['bao:prop1', 'bao:prop2']);
		let [groupURI1, groupURI2] = bae.expandPrefixes(['bao:group1', 'bao:group2']);
		assertTrue(bae.sameGroupNest([groupURI1], [groupURI1]));
		assertFalse(bae.sameGroupNest([groupURI1], [groupURI2]));
		assertTrue(bae.compatibleGroupNest([groupURI1], [groupURI1, groupURI2]));
		assertFalse(bae.compatibleGroupNest([groupURI1], [groupURI2]));

		assertTrue(bae.samePropGroupNest(propURI1, [groupURI1], propURI1, [groupURI1]));
		assertFalse(bae.samePropGroupNest(propURI1, [groupURI1], propURI1, [groupURI2]));
		assertTrue(bae.compatiblePropGroupNest(propURI1, [groupURI1], propURI1, [groupURI1, groupURI2]));
		assertFalse(bae.compatiblePropGroupNest(propURI1, [groupURI1], propURI1, [groupURI2]));

		assertFalse(bae.samePropGroupNest(propURI1, [groupURI1], propURI2, [groupURI1]));
		assertFalse(bae.compatiblePropGroupNest(propURI1, [groupURI1], propURI2, [groupURI1, groupURI2]));
	}

	public async testValidIntegerLiteral():Promise<void>
	{
		const validIntegerLiteral = bae.validIntegerLiteral;
		assertFalse(validIntegerLiteral(null));
		assertFalse(validIntegerLiteral(''));
		for (let literal of ['0', '1', '-1', '10000'])
			assertTrue(validIntegerLiteral(literal), `validIntegerLiteral(${literal})`);
		for (let literal of ['+123', '1.0', '1.01', '0.01', '1e6', '9.9e3', '-0.3e05'])
			assertFalse(validIntegerLiteral(literal), `validIntegerLiteral(${literal})`);
	}

	public async testValidNumberLiteral():Promise<void>
	{
		const validNumberLiteral = bae.validNumberLiteral;
		assertFalse(validNumberLiteral(null));
		assertFalse(validNumberLiteral(''));
		for (let literal of ['0', '1', '-1', '10000', '1.0', '1.01', '0.01', '1E6', '9.9e3', '-0.3e-5', '10.123e12', '234.45e78'])
			assertTrue(validNumberLiteral(literal), `validNumberLiteral(${literal})`);
		for (let literal of ['+0', '+1', '+10.123', '1,000', '1,000,000', '10,01', '-9,99', '-1,1E6'])
			assertFalse(validNumberLiteral(literal), `validNumberLiteral(${literal})`);
	}

	public async testStandardizeNumberLiteral():Promise<void>
	{
		const standardizeNumberLiteral = bae.standardizeNumberLiteral;
		assertEquals(null, standardizeNumberLiteral(null));
		assertEquals('', standardizeNumberLiteral(''));
		assertEquals('abc', standardizeNumberLiteral('abc'));

		// integers
		let cases =
		[
			['1', '1'],
			['10000000', '10000000'],
			['-1', '-1'],
			['1', '1'],
		];
		for (let [expected, literal] of cases)
			assertEquals(expected, standardizeNumberLiteral(literal), `literal ${literal}`);

		// floats
		cases =
		[
			['1', '1.0000'],
			['-10000000', '-10000000.0'],
			['10000000000', '10000000000.0000'],
			['10000000000000', '10000000000000.0000'],
			['100000000000000000000', '1e20'],
			['1e+21', '1e21'],
			['1e+21', '1000000000000000000000'],
			['123456789', '1.234567890e8'],
			['1234567890', '1.234567890e9'],
			['1.23456789e-9', '1.234567890e-9'],
			['0.00000123456789', '1.234567890e-6'],
			['0.00123456789', '1.234567890e-3'],
			['1.23456789', '1.234567890e0'],
			['1.23456789', '1.234567890e000'],
			['1.23456789', '1.234567890e-000'],
			['0.1', '1e-1'],
		];
		for (let [expected, literal] of cases)
			assertEquals(expected, standardizeNumberLiteral(literal), `literal ${literal}`);
	}

	public async testZipArrays():Promise<void>
	{
		const zipArrays = bae.zipArrays;

		let indexes = [0, 1, 2, 3, 4];
		let array1 = [1, 2, 3, 4, 5];
		let array2 = ['a', 'b', 'c', 'd', 'e'];
		let array3 = ['z', 'y', 'x', 'w', 'v'];

		// is an iterator
		let zipIterator = zipArrays(array1, array2);
		for (let idx of indexes)
		{
			let next = zipIterator.next();
			assertArrayEquals([array1[idx], array2[idx]], next.value);
			assertEquals(false, next.done);
		}
		let next = zipIterator.next();
		assertEquals(undefined, next.value);
		assertEquals(true, next.done);

		// can take more than two arrays - additional arrays are not typed!
		let idx = 0;
		for (let [a1, a2, a3, a4, a5] of zipArrays(array1, array2, array3, array2, array1))
		{
			assertEquals(array1[idx], a1);
			assertEquals(array2[idx], a2);
			assertEquals(array3[idx], a3);
			assertEquals(array2[idx], a4);
			assertEquals(array1[idx], a5);
			idx++;
		}

		// the length of the first array limits the zip
		assertEquals(3, [...zipArrays([1, 2, 3], indexes)].length);

		// will return undefined for shorter arrays
		let result = [...zipArrays(indexes, [1, 2, 3])];
		assertEquals(5, result.length);
		assertArrayEquals([2, 3], result[2]);
		assertArrayEquals([3, undefined], result[3]);
		assertArrayEquals([4, undefined], result[4]);
	}

	public async testEnumerate():Promise<void>
	{
		const enumerate = bae.enumerate;
		let array = ['a', 'b', 'c', 'd', 'e'];
		let indexes = [0, 1, 2, 3, 4];

		// is an iterator
		let enumIterator = enumerate(array);
		for (let idx of indexes)
		{
			let next = enumIterator.next();
			assertArrayEquals([idx, array[idx]], next.value);
			assertEquals(false, next.done);
		}
		let next = enumIterator.next();
		assertEquals(undefined, next.value);
		assertEquals(true, next.done);

		// can spread to list
		let enumArray = [...enumerate(array)];
		assertEquals(indexes.length, enumArray.length);
		assertArrayEquals(indexes, enumArray.map((pair) => pair[0]));
		assertArrayEquals(array, enumArray.map((pair) => pair[1]));
		for (let idx of indexes) assertArrayEquals([idx, array[idx]], enumArray[idx]);

		// can use in for-each loop
		for (let [idx, e] of enumerate(array))
			assertEquals(array[idx], e);

		// can define a start value for the enumeratin
		let i = 0;
		for (let [idx, e] of enumerate(array, 1))
		{
			assertEquals(i + 1, idx);
			assertEquals(array[i], e);
			i++;
		}

		// works with iterator/generator
		i = 0;
		for (let [idx1, [idx2, e]] of enumerate(enumerate(array, 100)))
		{
			assertEquals(i, idx1);
			assertEquals(i + 100, idx2);
			assertEquals(array[i], e);
			i++;
		}

		for (let [idx, [e1, e2]] of enumerate(bae.zipArrays(array, array)))
		{
			assertEquals(array[idx], e1);
			assertEquals(array[idx], e2);
		}
	}

	public async testGroupBy():Promise<void>
	{
		let array =
		[
			{'key': 'k2', 'value': 'b1'},
			{'key': 'k3', 'value': 'c1'},
			{'key': 'k1', 'value': 'a1'},
			{'key': 'k2', 'value': 'b2'},
			{'key': 'k3', 'value': 'c2'},
			{'key': 'k3', 'value': 'c3'},
		];
		let grouped = bae.groupBy(array, (e) => e.key);
		assertArrayEquals(['k2', 'k3', 'k1'], Object.keys(grouped));
		grouped = bae.groupBy(array, (e) => e.key, 1);
		assertArrayEquals(['k1', 'k2', 'k3'], Object.keys(grouped));
		grouped = bae.groupBy(array, (e) => e.key, -1);
		assertArrayEquals(['k3', 'k2', 'k1'], Object.keys(grouped));

		let expected:Record<string, string[]> = {'k1': ['a1'], 'k2': ['b1', 'b2'], 'k3': ['c1', 'c2', 'c3']};
		for (let key of Object.keys(grouped))
		{
			assertArrayEquals(expected[key], grouped[key].map((e) => e.value));
		}
	}
}

export class UtilUITest extends TestModule
{
	constructor()
	{
		super('Utilities UI');
	}

	@requireWindow
	public async testJoinElements():Promise<void>
	{
		const assertResult = (elements:any[], expected:string[]):void =>
		{
			assertEquals(expected[0], joinElements($('<div/>'), elements, ',').html());
			assertEquals(expected[1], joinElements($('<div/>'), elements, ',', 'content:').html());
			assertEquals(expected[2], joinElements($('<div/>'), elements, ',', '[', ']').html());
		};
		const joinElements = bae.joinElements;
		assertResult([], ['', '', '']);
		assertResult(['a'], ['a', 'content:a', '[a]']);
		assertResult(['a', 'b'], ['a,b', 'content:a,b', '[a,b]']);
		assertResult(['a', 'b', 'c'], ['a,b,c', 'content:a,b,c', '[a,b,c]']);
		assertResult(['a', $('<i>').append('b'), 'c'], ['a,<i>b</i>,c', 'content:a,<i>b</i>,c', '[a,<i>b</i>,c]']);

		let result = joinElements($('<div/>'), ['a', 'b', 'c'], $('<br>'));
		assertEquals('a<br>b<br>c', result.html());
	}
}

/* other global functions to test:
export function samePropGroupNest(propURI1:string, groupNest1:string[], propURI2:string, groupNest2:string[]):boolean
export function compatiblePropGroupNest(propURI1:string, groupNest1:string[], propURI2:string, groupNest2:string[]):boolean
export function compareGroupURI(uri1:string, uri2:string):boolean
export function descendentGroupNest(childNest:string[], parentNest:string[]):boolean
export function comparePermissiveGroupURI(uri1:string, uri2:string):boolean
export function removeSuffixGroupURI(uri:string):string
export function decomposeSuffixGroupURI(uri:string):[string, number]
export function keyPropGroup(propURI:string, groupNest:string[])
export function keyPropGroupValue(propURI:string, groupNest:string[], value:string)
export function deriveAssayName(assay:AssayDefinition, maxLength:number = 100):string
export function purifyTextPlainInput(text:string):string
export function purifyTextHTMLInput(text:string):string
export function stringSimilarity(str1:string, str2:string):number
export function calibratedSimilarity(str1:string, str2:string):number
export function fuzzyStringSimilarity(str1:string, str2:string, ngramLength:number = 2, caseSensitive:boolean = false):number
*/

/* EOF */ }

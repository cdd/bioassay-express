/**
 * Diff Match and Patch
 * Copyright 2018 The diff-match-patch Authors.
 * https://github.com/google/diff-match-patch
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
	@fileoverview Computes the difference between two texts to create a patch.
 	Applies the patch onto another text, allowing for errors.
 	@author fraser@google.com (Neil Fraser)

	Modified for use in BioAssay Express: converted to TypeScript, and retained only the code needed to apply patches.
 */

namespace BioAssayExpress /* BOF */ {

// external entrypoint: supply a patch (using the GNU format) and the text to apply it to; returns the transformed text, or bugs out
// with an exception if something went wrong
export function applyDiffPatch(patch:string, text:string):string
{
	let patches = DiffMatchPatch.patchFromText(patch);
	return DiffMatchPatch.patchApply(patches, text)[0];
}

const enum Operation
{
	DELETE,
	INSERT,
	EQUAL
}

interface Diff
{
	operation:Operation;
	text:string;
}

class Patch
{
	public diffs:Diff[];
	public start1?:number;
	public start2?:number;
	public length1:number;
	public length2:number;
}

const MATCH_MAXBITS = 32;
const PATCH_MARGIN = 4;
const PATCH_DELETETHRESHOLD = 0.5;

class DiffMatchPatch
{
	// unpack the GNU patch format and return it as the useful localised datastructure
	public static patchFromText(textline:string):Patch[]
	{
		let patches:Patch[] = [];
		if (!textline) return patches;

		let text = textline.split('\n');
		let textPointer = 0;
		let patchHeader = /^@@ -(\d+),?(\d*) \+(\d+),?(\d*) @@$/;
		while (textPointer < text.length)
		{
			let m = text[textPointer].match(patchHeader);
			if (!m) throw new Error('Invalid patch string: ' + text[textPointer]);

			let patch:Patch = {'diffs': [], 'length1': 0, 'length2': 0};
			patches.push(patch);
			patch.start1 = parseInt(m[1], 10);
			if (m[2] === '')
			{
				patch.start1--;
				patch.length1 = 1;
			}
			else if (m[2] == '0')
			{
				patch.length1 = 0;
			}
			else
			{
				patch.start1--;
				patch.length1 = parseInt(m[2], 10);
			}

			patch.start2 = parseInt(m[3], 10);
			if (m[4] === '')
			{
				patch.start2--;
				patch.length2 = 1;
			}
			else if (m[4] == '0')
			{
				patch.length2 = 0;
			}
			else
			{
				patch.start2--;
				patch.length2 = parseInt(m[4], 10);
			}
			textPointer++;

			while (textPointer < text.length)
			{
				let sign = text[textPointer].charAt(0), line:string;
				try {line = decodeURI(text[textPointer].substring(1));}
				catch (ex) {throw new Error('Illegal escape in patch_fromText: ' + line);}

				if (sign == '-') patch.diffs.push({'operation': Operation.DELETE, 'text': line});
				else if (sign == '+') patch.diffs.push({'operation': Operation.INSERT, 'text': line});
				else if (sign == ' ') patch.diffs.push({'operation': Operation.EQUAL, 'text': line});
				else if (sign == '@') break; // start of next patch
				else if (sign === '') {} // blank line, ignore
				else throw new Error('Invalid patch mode "' + sign + '" in: ' + line);
				textPointer++;
			}
		}
		return patches;
	}

	// takes the array of patch items and applies it to the given string, returning the transformed result
	public static patchApply(patches:Patch[], text:string):[string, boolean[]]
	{
		if (patches.length == 0) return [text, []];

		// deep copy the patches so that no changes are made to originals
		patches = deepClone(patches);

		let nullPadding = this.addPadding(patches);
		text = nullPadding + text + nullPadding;

		this.patchSplitMax(patches);

		// delta keeps track of the offset between the expected and actual location
		// of the previous patch.  If there are patches expected at positions 10 and
		// 20, but the first patch was found at 12, delta is 2 and the second patch
		// has an effective expected position of 22.
		let delta = 0;
		let results:boolean[] = [];
		for (let x = 0; x < patches.length; x++)
		{
			let expected_loc = patches[x].start2 + delta;
			let text1 = this.diffText1(patches[x].diffs);
			let start_loc;
			let end_loc = -1;

			if (text1.length > MATCH_MAXBITS)
			{
				// patchSplitMax will only provide an oversized pattern in the case of a monster delete
				start_loc = this.matchMain(text, text1.substring(0, MATCH_MAXBITS), expected_loc);
				if (start_loc != -1)
				{
					end_loc = this.matchMain(text, text1.substring(text1.length - MATCH_MAXBITS), expected_loc + text1.length - MATCH_MAXBITS);
					if (end_loc == -1 || start_loc >= end_loc)
					{
						// Can't find valid trailing context.  Drop this patch.
						start_loc = -1;
					}
				}
			}
			else
			{
				start_loc = this.matchMain(text, text1, expected_loc);
			}

			if (start_loc == -1)
			{
				// no match found.  :(
				results[x] = false;
				// subtract the delta for this failed patch from subsequent patches.
				delta -= patches[x].length2 - patches[x].length1;
			}
			else
			{
				// Found a match.  :)
				results[x] = true;
				delta = start_loc - expected_loc;
				let text2;
				if (end_loc == -1)
					text2 = text.substring(start_loc, start_loc + text1.length);
				else
					text2 = text.substring(start_loc, end_loc + MATCH_MAXBITS);

				if (text1 == text2)
				{
					// Perfect match, just shove the replacement text in.
					text = text.substring(0, start_loc) + this.diffText2(patches[x].diffs) + text.substring(start_loc + text1.length);
				}
				/*else ... don't need this; we're assuming it's nothing too crazy
				{
					// Imperfect match.  Run a diff to get a framework of equivalent indices.
					let diffs = this.diff_main(text1, text2, false);
					if (text1.length > this.Match_MaxBits && this.diff_levenshtein(diffs) / text1.length > PATCH_DELETETHRESHOLD)
					{
						// The end points match, but the content is unacceptably bad.
						results[x] = false;
					}
					else
					{
						this.diff_cleanupSemanticLossless(diffs);
						let index1 = 0;
						let index2;
						for (let y = 0; y < patches[x].diffs.length; y++)
						{
							let diff = patches[x].diffs[y];
							if (diff.operation != Operation.EQUAL)
							{
								index2 = this.diff_xIndex(diffs, index1);
							}
							else if (diff.operation == Operation.INSERT)
							{
								text = text.substring(0, start_loc + index2) + diff.text + text.substring(start_loc + index2);
							}
							else if (diff.operation == Operation.DELETE)
							{
								text = text.substring(0, start_loc + index2) +
									   text.substring(start_loc + this.diff_xIndex(diffs, index1 + diff.text.length));
							}
							if (diff.operation !== Operation.DELETE) index1 += diff.text.length;
						}
					}
				}*/
			}
		}

		// strip the padding off
		text = text.substring(nullPadding.length, text.length - nullPadding.length);
		return [text, results];
	}

	// add some padding on text start and end so that edges can match something
 	private static addPadding(patches:Patch[]):string
	{
		let paddingLength = PATCH_MARGIN;
		let nullPadding = '';
		for (let x = 1; x <= paddingLength; x++) nullPadding += String.fromCharCode(x);

		// bump all the patches forward.
		for (let x = 0; x < patches.length; x++)
		{
			patches[x].start1 += paddingLength;
			patches[x].start2 += paddingLength;
		}

		// add some padding on start of first diff.
		let patch = patches[0];
		let diffs = patch.diffs;
		if (diffs.length == 0 || diffs[0].operation != Operation.EQUAL)
		{
			// add nullPadding equality
			diffs.unshift({'operation': Operation.EQUAL, 'text': nullPadding});
			patch.start1 -= paddingLength; // should be 0
			patch.start2 -= paddingLength; // should be 0
			patch.length1 += paddingLength;
			patch.length2 += paddingLength;
		}
		else if (paddingLength > diffs[0].text.length)
		{
			// grow first equality
			let extraLength = paddingLength - diffs[0].text.length;
			diffs[0].text = nullPadding.substring(diffs[0].text.length) + diffs[0].text;
			patch.start1 -= extraLength;
			patch.start2 -= extraLength;
			patch.length1 += extraLength;
			patch.length2 += extraLength;
		}

		// add some padding on end of last diff
		patch = patches[patches.length - 1];
		diffs = patch.diffs;
		if (diffs.length == 0 || diffs[diffs.length - 1].operation != Operation.EQUAL)
		{
			// add nullPadding equality
			diffs.push({'operation': Operation.EQUAL, 'text': nullPadding});
			patch.length1 += paddingLength;
			patch.length2 += paddingLength;
		}
		else if (paddingLength > diffs[diffs.length - 1].text.length)
		{
			// grow last equality
			let extraLength = paddingLength - diffs[diffs.length - 1].text.length;
			diffs[diffs.length - 1].text += nullPadding.substring(0, extraLength);
			patch.length1 += extraLength;
			patch.length2 += extraLength;
		}

		return nullPadding;
	}

	// locate the best instance of 'pattern' in 'text' near 'loc'
	private static matchMain(text:string, pattern:string, loc:number):number
	{
		// check for null inputs
		if (text == null || pattern == null || loc == null) throw new Error('Null input.');

		loc = Math.max(0, Math.min(loc, text.length));
		if (text == pattern) return 0; // shortcut (potentially not guaranteed by the algorithm)
		if (!text.length) return -1; // nothing to match
		if (text.substring(loc, loc + pattern.length) == pattern)
		{
			// perfect match at the perfect spot! (Includes case of null pattern)
			return loc;
		}

		throw new Error('Pattern too long for this browser.');
	}

	// look through the patches and break up any which are longer than the maximum limit of the match algorithm
	private static patchSplitMax(patches:Patch[]):void
	{
		let patch_size = MATCH_MAXBITS;
		for (let x = 0; x < patches.length; x++)
		{
			if (patches[x].length1 <= patch_size) continue;

			let bigpatch = patches[x];

			// remove the big old patch
			patches.splice(x--, 1);
			let start1 = bigpatch.start1;
			let start2 = bigpatch.start2;
			let precontext = '';
			while (bigpatch.diffs.length !== 0)
			{
				// create one of several smaller patches
				let patch:Patch = {'diffs': [], 'length1': 0, 'length2': 0};
				let empty = true;
				patch.start1 = start1 - precontext.length;
				patch.start2 = start2 - precontext.length;
				if (precontext !== '')
				{
					patch.length1 = patch.length2 = precontext.length;
					patch.diffs.push({'operation': Operation.EQUAL, 'text': precontext});
				}
				while (bigpatch.diffs.length !== 0 && patch.length1 < patch_size - PATCH_MARGIN)
				{
					let diff_type = bigpatch.diffs[0].operation;
					let diff_text = bigpatch.diffs[0].text;
					if (diff_type === Operation.INSERT)
					{
						// insertions are harmless
						patch.length2 += diff_text.length;
						start2 += diff_text.length;
						patch.diffs.push(bigpatch.diffs.shift());
						empty = false;
					}
					else if (diff_type === Operation.DELETE && patch.diffs.length == 1 &&
							 patch.diffs[0].operation == Operation.EQUAL && diff_text.length > 2 * patch_size)
					{
						// this is a large deletion, let it pass in one chunk
						patch.length1 += diff_text.length;
						start1 += diff_text.length;
						empty = false;
						patch.diffs.push({'operation': diff_type, 'text': diff_text});
						bigpatch.diffs.shift();
					}
					else
					{
						// deletion or equality; only take as much as we can stomach
						diff_text = diff_text.substring(0, patch_size - patch.length1 - PATCH_MARGIN);
						patch.length1 += diff_text.length;
						start1 += diff_text.length;
						if (diff_type === Operation.EQUAL)
						{
							patch.length2 += diff_text.length;
							start2 += diff_text.length;
						}
						else empty = false;

						patch.diffs.push({'operation': diff_type, 'text': diff_text});
						if (diff_text == bigpatch.diffs[0].text)
							bigpatch.diffs.shift();
						else
							bigpatch.diffs[0].text = bigpatch.diffs[0].text.substring(diff_text.length);
					}
				}

				// compute the head context for the next patch
				precontext = this.diffText2(patch.diffs);
				precontext = precontext.substring(precontext.length - PATCH_MARGIN);

				// append the end context for this patch
				let postcontext = this.diffText1(bigpatch.diffs).substring(0, PATCH_MARGIN);
				if (postcontext !== '')
				{
					patch.length1 += postcontext.length;
					patch.length2 += postcontext.length;
					if (patch.diffs.length !== 0 && patch.diffs[patch.diffs.length - 1].operation == Operation.EQUAL)
						patch.diffs[patch.diffs.length - 1].text += postcontext;
					else
						patch.diffs.push({'operation': Operation.EQUAL, 'text': postcontext});
				}
				if (!empty) patches.splice(++x, 0, patch);
			}
		}
	}

	// compute and return the source text (all equalities and deletions)
	private static diffText1(diffs:Diff[]):string
	{
		let text = [];
		for (let x = 0; x < diffs.length; x++)
		{
			if (diffs[x].operation != Operation.INSERT) text[x] = diffs[x].text;
		}
		return text.join('');
	}

	// compute and return the destination text (all equalities and insertions)
	private static diffText2(diffs:Diff[]):string
	{
		let text = [];
		for (let x = 0; x < diffs.length; x++)
		{
			if (diffs[x].operation != Operation.DELETE) text[x] = diffs[x].text;
		}
		return text.join('');
	}
}

/* EOF */ }

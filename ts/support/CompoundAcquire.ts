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
	Compound acquisition: a complex song & dance that pulls back a list of compounds that correspond to a given set of
	assays, filtered and ranked in a certain way. This has to be done strategically, since the amount of data can be
	very large, and the web calls need to be stateless and reasonably sized.
*/

export interface CompoundAcquireMolecule
{
	compoundID:number;
	mol:wmk.Molecule;
	hashECFP6:number;
	fullECFP6:number[];
	formula:string;
}

export class CompoundAcquire
{
	public compounds:number[][] = []; // each entry is a list of compoundIDs (which are thought to represent the same thing)
	public hashes:number[] = []; // the (pseudo)uniqueness hashcode for each group above (a XOR'ing of ECFP6 codes)
	public score:number[] = []; // similarity or score metric for each compound block: may be Tanimoto or something else, like frequency

	public callbackResults:() => void;
	public callbackFinished:() => void;

	protected cancelled = false;
	public hashCompound:Record<number, Set<number>> = {}; // hashECFP6:list of compoundIDs
	public molCache:Record<number, CompoundAcquireMolecule> = {}; // compoundID:details

	constructor(protected assayIDList:number[], protected maxCompounds:number)
	{
	}

	public start():void {}
	public stop():void {}

	// fraction complete (0 to 1)
	public progressFraction():number {return 0;}

	// returns an array of numbers from 0..1 that can be used to plot an indication of what's actually coming back
	public progressBars():number[] {return null;}

	// returns a text description of where it's at
	public progressText():string {return '';}

	// ------------ protected methods ------------

	// record the occurrence of a compound's hash code, so it can be easily looked up
	protected noteHash(hashECFP6:number, compoundID:number):void
	{
		let compounds = this.hashCompound[hashECFP6];
		if (!compounds)
		{
			compounds = new Set<number>();
			this.hashCompound[hashECFP6] = compounds;
		}
		else compounds.add(compoundID);
	}

	// pushes an item into the cache
	protected fillMolCache(compoundID:number, molfile:string, hashECFP6:number):void
	{
		let mol:wmk.Molecule = null;
		try
		{
			mol = wmk.MoleculeStream.readMDLMOL(molfile);
		}
		catch (ex) {} // silent failure
		if (mol == null) mol = new wmk.Molecule();

		let fp:number[] = [];
		try
		{
			let circ = wmk.CircularFingerprints.create(mol, wmk.CircularFingerprints.CLASS_ECFP6);
			circ.calculate();
			fp = circ.getUniqueHashes();
		}
		catch (ex) {} // also silent failure: no fingerprints is the default

		let cpd:CompoundAcquireMolecule =
		{
			'compoundID': compoundID,
			'mol': mol,
			'hashECFP6': hashECFP6,
			'fullECFP6': fp,
			'formula': wmk.MolUtil.molecularFormula(mol)
		};
		this.molCache[compoundID] = cpd;
	}

	// makes a judgment call about whether two structures represent the same thing; assumes that hashECFP6 is already known
	// to be the same (no need to test it again)
	// NOTE: current implementation is not strictly correct, but it is very seldom wrong; two molecules are exceedingly unlikely
	// to have the same molecular formula and identical set of ECFP6 fingerprints if the structures are different, but it's not impossible
	// the correct way is to use an isomorphism mapping as the final test after escalating short circuits, though this does have some
	// implications for group merging/splitting caused by stereoambiguity
	protected compareStructures(cpd1:CompoundAcquireMolecule, cpd2:CompoundAcquireMolecule):boolean
	{
		// (implied) if (cpd1.hashECFP6 != cpd2.hashECFP6) return false;
		if (cpd1.formula != cpd2.formula) return false;
		return Vec.equals(cpd1.fullECFP6, cpd2.fullECFP6);
	}
}

/*
	Acquisition by similarity: start with a reference compound and take the N-most-similar results.

	NOTE: it is presumed that any two compounds are the same if they have the same ECFP6 hash code, and the same similarity to the
		  reference compound (if any); this is not strictly true all the time: the structures themselves should be compared using
		  something more rigorous, e.g. an isophorphism test, or using canonical SMILES/InChI. But for now, same hash code isn't bad.
*/

export class CompoundAcquireSimilarity extends CompoundAcquire
{
	private searchAssay = 0;
	private searchSeenCompounds = new Set<number>(); // compound IDs should not be evaluated twice, since only structure matters
	private searchBucket:number[] = [];
	private searchHashes:number[] = [];
	private searchBucketPos = 0;
	private searchBucketSize = 0;

	constructor(assayIDList:number[], maxCompounds:number, private similarTo:string, private probesOnly:boolean)
	{
		super(assayIDList, maxCompounds);
	}

	public start():void
	{
		this.searchNextCompounds();
	}

	public stop():void
	{
		this.cancelled = true;
	}

	public progressFraction():number
	{
		let pos = this.searchAssay;
		if (this.searchBucketSize > 0) pos += this.searchBucketPos / this.searchBucketSize;
		return pos / Math.max(this.assayIDList.length, 1);
	}

	// the "score" member is ranked Tanimoto similarity, which is already the desired form
	public progressBars():number[] {return this.score;}

	public progressText():string
	{
		//let ncpd = this.compounds.length;
		let ncpd = 0;
		for (let n = this.score.length - 1; n >= 0; n--) if (this.score[n] > 0) ncpd++;
		return ncpd + ' compound' + (ncpd == 1 ? '' : 's');
	}

	// ------------ private methods ------------

	// grabs the next batch, or signifies completion
	private searchNextCompounds():void
	{
		if (this.cancelled) return;

		this.searchBucketPos = 0;
		this.searchBucketSize = 0;

		if (this.searchAssay >= this.assayIDList.length)
		{
			this.callbackFinished();
			return;
		}

		let assayID = this.assayIDList[this.searchAssay];
		let params =
		{
			'assayIDList': [assayID],
			'justIdentifiers': true,
			'probesOnly': this.probesOnly,
			'requireMolecule': true
		};
		callREST('REST/ListCompounds', params,
			(data:any) =>
			{
				if (this.cancelled) return; // got cancelled
				let compoundIDList:number[] = data.compoundIDList, hashECFP6List:number[] = data.hashECFP6List;
				this.processCompoundResults(compoundIDList, hashECFP6List);
			});
	}

	// a list of compound IDs
	private processCompoundResults(compoundIDList:number[], hashECFP6List:number[]):void
	{
		// if not doing similarity it's pretty simple: keep stacking until no room left
		if (this.similarTo == null)
		{
			for (let n = 0; n < compoundIDList.length; n++)
			{
				let cpdID = compoundIDList[n], hash = hashECFP6List[n];
				if (this.searchSeenCompounds.has(cpdID)) continue;
				this.searchSeenCompounds.add(cpdID);

				let idx = this.hashes.indexOf(hash);
				if (idx < 0)
				{
					this.compounds.push([cpdID]);
					this.hashes.push(hash);
				}
				else this.compounds[idx].push(cpdID);

				// (actually: premature termination is probably technically incorrect since we can still gather compounds with
				// the same structure, but this non-similarity fetching is just a quick fallback anyway, so maybe not care)
				if (this.compounds.length >= this.maxCompounds)
				{
					this.callbackFinished();
					return;
				}
			}
			this.callbackResults();
			this.searchAssay++;
			this.searchNextCompounds();
			return;
		}

		// make a list of the subset that haven't been observed before
		this.searchBucket = [];
		this.searchHashes = [];
		for (let n = 0; n < compoundIDList.length; n++)
		{
			let cpdID = compoundIDList[n], hash = hashECFP6List[n];
			if (this.searchSeenCompounds.has(cpdID)) continue;
			this.searchSeenCompounds.add(cpdID);
			this.searchBucket.push(cpdID);
			this.searchHashes.push(hash);
		}
		this.searchBucketSize = this.searchBucket.length;
		this.searchNextBucket();
	}

	// if there are compound IDs in the "bucket" (needing to have a similarity comparison done), pack off a batch of them; when none left,
	// go back to the regular search
	private searchNextBucket():void
	{
		if (this.searchBucket.length == 0)
		{
			this.searchAssay++;
			this.searchNextCompounds();
			return;
		}

		let subset = this.searchBucket.splice(0, 1000), subhash = this.searchHashes.splice(0, 1000);
		this.searchBucketPos += subset.length;

		let params =
		{
			'compoundIDList': subset,
			'justIdentifiers': true,
			'similarTo': this.similarTo
		};
		callREST('REST/ListCompounds', params,
			(data:any) =>
			{
				if (this.cancelled) return; // got cancelled
				let compoundIDList:number[] = data.compoundIDList, hashList:number[] = data.hashECFP6List, simList:number[] = data.similarity;
				this.processBucketResults(compoundIDList, hashList, simList);
			});
	}

	// details for a particular bucket batch just arrived: process them
	private processBucketResults(compoundIDList:number[], hashList:number[], simList:number[]):void
	{
		skip: for (let n = 0; n < compoundIDList.length; n++)
		{
			let cpdID = compoundIDList[n], hash = hashList[n], sim = simList[n];

			let num = this.compounds.length;
			if (num == this.maxCompounds && sim <= this.score[num - 1]) continue;

			// if hash & similarity match an existing entry, just tack on the compound ID
			for (let i = 0; i < this.hashes.length; i++) if (this.hashes[i] == hash && this.score[i] == sim)
			{
				this.compounds[i].push(cpdID);
				continue skip;
			}

			this.compounds.push([cpdID]);
			this.hashes.push(hash);
			this.score.push(sim);
			for (let p = num; p > 0; p--)
			{
				if (this.score[p - 1] >= sim) break;
				Vec.swap(this.compounds, p, p - 1);
				Vec.swap(this.score, p, p - 1);
			}
			while (this.compounds.length > this.maxCompounds) {this.compounds.pop(); this.hashes.pop(); this.score.pop();}
		}

		this.callbackResults();
		this.searchNextBucket();
	}
}

/*
	Acquisition by frequent hitting: add up the number of hits for each compound, then pick the most promiscuous.

	NOTE: it is presumed that compounds with the same ECFP6 hash represent the same structure; this assumption is too weak
		  to hold long term, but it'll do for testing
*/

export class CompoundAcquireFrequent extends CompoundAcquire
{
	public actives:number[] = [];
	public inactives:number[] = [];
	public mapCompound:Record<string, number> = {};
	public existingCompounds = false; // set this to true if the compounds are predefined & shouldn't add new distinct blocks
	public hashWhitelist:number[] = null; // restrict compounds retrieved to this set of hash codes

	private searchAssay = 0;
	private resolvePos = 0; // just used for progress indication
	private resolveCount = 0;

	constructor(assayIDList:number[], maxCompounds:number, private asProbes:boolean)
	{
		super(assayIDList, maxCompounds);
	}

	public start():void
	{
		this.searchNextCompounds();
	}

	public stop():void
	{
		this.cancelled = true;
	}

	public progressFraction():number
	{
		let pos = this.searchAssay;
		if (this.resolveCount > 0) pos += this.resolvePos / this.resolveCount;
		return pos / Math.max(this.assayIDList.length, 1);
	}

	// transforms the counts-thus-far into something that can be used to display preliminary progress
	public progressBars():number[]
	{
		if (this.compounds.length == 0) return null;
		this.calculateScores();
		let order = Vec.reverse(Vec.idxSort(this.score));
		let scale = 1.0 / this.score[order[0]];
		let bars:number[] = [];
		for (let n = 0; n < order.length && n < this.maxCompounds; n++) bars.push(this.score[order[n]] * scale);
		return bars;
	}

	public progressText():string
	{
		let ncpd = 0;
		for (let n = this.actives.length - 1; n >= 0; n--) if (this.actives[n] > 0) ncpd++;
		return ncpd + ' compound' + (ncpd == 1 ? '' : 's');
	}

	// ------------ private methods ------------

	// grabs the next batch, or signifies completion
	private searchNextCompounds():void
	{
		if (this.cancelled) return;

		if (this.searchAssay >= this.assayIDList.length)
		{
			this.finaliseResults();
			return;
		}

		// grab all activity data for one assay; note the divergence: if we're counting actives (for frequent hitter) it's enough
		// to just ask for the list of actives; if counting active/inactive ratio (for probelikeness), have to ask for data about
		// where each compound is active, and process the return format slightly differently
		let assayID = this.assayIDList[this.searchAssay];
		if (!this.asProbes)
		{
			let params =
			{
				'assayIDList': [assayID],
				'justIdentifiers': true,
				'activesOnly': true,
				'hashECFP6List': this.hashWhitelist,
				'requireMolecule': true
			};
			callREST('REST/ListCompounds', params,
				(data:any) =>
				{
					if (this.cancelled) return; // got cancelled
					let compoundIDList:number[] = data.compoundIDList, hashECFP6List:number[] = data.hashECFP6List;
					this.processActiveResults(compoundIDList, hashECFP6List);
				});
		}
		else
		{
			let params =
			{
				'assayIDList': [assayID],
				'justIdentifiers': false,
				'activesOnly': true,
				'hashECFP6List': this.hashWhitelist,
				'requireMolecule': true
			};
			callREST('REST/ListCompounds', params,
				(data:any) =>
				{
					if (this.cancelled) return; // got cancelled
					let compoundIDList:number[] = data.compoundIDList, hashECFP6List:number[] = data.hashECFP6List;
					let measureCompound:number[] = data.measureCompound, measureValue:number[] = data.measureValue;
					this.processMeasureResults(compoundIDList, hashECFP6List, measureCompound, measureValue);
				});
		}
	}

	// got a list of compound IDs: these are already prefiltered for being active against their respective target; it is known that
	// the results are just for one assay, therefore the compoundIDs are unique
	private processActiveResults(compoundIDList:number[], hashECFP6List:number[]):void
	{
		let dupID:number[] = [], dupHash:number[] = []; // possible duplicates

		// first pass: each item has three possibilities..
		//    (1) the compoundID has been seen before, so just increment its counter
		//    (2) the hash code has not been seen before, so it is safe to add a new entry
		//    (3) the compoundID is new but the hash code is not: may or may not be degenerate
		for (let n = 0; n < compoundIDList.length; n++)
		{
			let cpdID = compoundIDList[n], hash = hashECFP6List[n];
			this.noteHash(hash, cpdID);

			let idx = this.mapCompound[cpdID];
			if (idx == null)
			{
				if (this.hashes.indexOf(hash) < 0 && !this.existingCompounds)
				{
					this.mapCompound[cpdID] = this.compounds.length;
					this.compounds.push([cpdID]);
					this.hashes.push(hash);
					this.actives.push(1);
					this.inactives.push(0);
				}
				else
				{
					dupID.push(cpdID);
					dupHash.push(hash);
				}
			}
			else
			{
				this.actives[idx]++;
			}
		}

		// if there are possible duplicates, have to do an extra step
		if (dupID.length > 0)
		{
			this.resolvePos = 0;
			this.resolveCount = dupID.length;
			this.resolveDuplicates(dupID, dupHash, Vec.booleanArray(true, dupID.length));
		}
		else
		{
			this.resolveCount = 0;
			this.callbackResults();
			this.searchAssay++;
			this.searchNextCompounds();
		}
	}

	// got a list of compounds with active/inactive data; these are used to increment the active/inactive counters, so that the
	// score can subsequently be calculated (for probelikeness)
	private processMeasureResults(compoundIDList:number[], hashECFP6List:number[], measureCompound:number[], measureValue:number[]):void
	{
		// convert into the appropriate format: want active/inactive counts, and eliminate anything not mentioned
		let countActive = Vec.numberArray(0, compoundIDList.length), countInactive = Vec.numberArray(0, compoundIDList.length);
		let mask = Vec.booleanArray(false, compoundIDList.length);
		for (let n = 0; n < measureCompound.length; n++)
		{
			let idx = compoundIDList.indexOf(measureCompound[n]);
			if (measureValue[n] >= 0.5) countActive[idx]++; else countInactive[idx]++;
			mask[idx] = true;
		}
		compoundIDList = Vec.maskGet(compoundIDList, mask);
		hashECFP6List = Vec.maskGet(hashECFP6List, mask);

		let dupID:number[] = [], dupHash:number[] = [], dupActive:boolean[] = []; // possible duplicates

		// first pass: each item has three possibilities..
		//    (1) the compoundID has been seen before, so just increment its counters
		//    (2) the hash code has not been seen before, so it is safe to add a new entry
		//    (3) the compoundID is new but the hash code is not: may or may not be degenerate
		for (let n = 0; n < compoundIDList.length; n++)
		{
			let cpdID = compoundIDList[n], hash = hashECFP6List[n];
			this.noteHash(hash, cpdID);

			let idx = this.mapCompound[cpdID], isActive = countActive[n] > 0;
			if (idx == null)
			{
				if (this.hashes.indexOf(hash) < 0)
				{
					this.mapCompound[cpdID] = this.compounds.length;
					this.compounds.push([cpdID]);
					this.hashes.push(hash);
					this.actives.push(isActive ? 1 : 0);
					this.inactives.push(isActive ? 0 : 1);
				}
				else
				{
					dupID.push(cpdID);
					dupHash.push(hash);
					dupActive.push(isActive);
				}
			}
			else
			{
				if (isActive) this.actives[idx]++; else this.inactives[idx]++;
			}
		}

		// if there are possible duplicates, have to do an extra step
		if (dupID.length > 0)
		{
			this.resolveDuplicates(dupID, dupHash, dupActive);
		}
		else
		{
			this.callbackResults();
			this.searchAssay++;
			this.searchNextCompounds();
		}
	}

	// in the case of possible duplicates, this method is called instead of continuing on with the search
	private resolveDuplicates(compoundID:number[], hashECFP6:number[], isActive:boolean[]):void
	{
		if (this.cancelled) return;

		// preliminary step: if there are any compounds in the set that have not been loaded into the cache
		// for resolution
		let fetchID:number[] = [];
		for (let hash of hashECFP6) for (let cpdID of this.hashCompound[hash]) if (!this.molCache[cpdID]) fetchID.push(cpdID);
		if (fetchID.length > 0)
		{
			fetchID = fetchID.slice(0, 100); // in case it's big, this will packetise
			let params = {'compoundIDList': fetchID};
			callREST('REST/ListCompounds', params,
				(data:any) =>
				{
					let molfileList:string[] = data.molfileList, hashECFP6List:number[] = data.hashECFP6List;
					for (let n = 0; n < fetchID.length; n++) this.fillMolCache(fetchID[n], molfileList[n], hashECFP6List[n]);
					this.resolveDuplicates(compoundID, hashECFP6, isActive); // proceed where we left off
				});

			return;
		}

		// for each compound, go through and see if it's the same as something else, and react accordingly
		for (let n = 0; n < compoundID.length; n++)
		{
			// find the first other compound it's equivalent to, if any
			let cpd = this.molCache[compoundID[n]];
			let otherID = 0;
			for (let lookID of this.hashCompound[hashECFP6[n]]) if (this.mapCompound[lookID])
			{
				let ref = this.molCache[lookID];
				if (this.compareStructures(cpd, ref)) {otherID = lookID; break;}
			}

			// either merge or create a new entry
			if (otherID > 0)
			{
				let idx = this.mapCompound[otherID];
				this.compounds[idx].push(compoundID[n]);
				this.mapCompound[compoundID[n]] = idx;
				if (isActive[n]) this.actives[idx]++; else this.inactives[idx]++;
			}
			else if (!this.existingCompounds)
			{
				this.mapCompound[compoundID[n]] = this.compounds.length;
				this.compounds.push([compoundID[n]]);
				this.hashes.push(hashECFP6[n]);
				this.actives.push(isActive[n] ? 1 : 0);
				this.inactives.push(isActive[n] ? 0 : 1);
			}
		}

		this.resolvePos += compoundID.length;

		this.callbackResults();
		this.searchAssay++;
		this.searchNextCompounds();
	}

	// the content has been acquired and merged/disambiguated along the way; time to select and rank the best
	private finaliseResults():void
	{
		if (this.compounds.length == 0) return null;

		this.calculateScores();

		let order = Vec.idxSort(Vec.neg(this.score)).slice(0, this.maxCompounds);
		while (order.length > 0 && this.score[order[order.length - 1]] == 0) order.pop(); // zero score = don't want it

		this.compounds = Vec.idxGet(this.compounds, order);
		this.hashes = Vec.idxGet(this.hashes, order);
		this.score = Vec.idxGet(this.score, order);

		this.callbackFinished();
	}

	// derive the scores according to type: if looking for frequent hitters, it's just number of actives; if probelikeness, it's
	// a ratio of active to inactive
	private calculateScores():void
	{
		if (this.asProbes)
		{
			this.score = [];
			for (let n = 0; n < this.compounds.length; n++)
			{
				let s = 0;
				if (this.actives[n] > 0) s = this.inactives[n] / this.actives[n];
				this.score.push(s);
			}
		}
		else this.score = this.actives.slice(0);
	}
}

/*
	A composite search, used to obtain selectivity (probe-like) results more efficiently: starts by obtaining all compounds that
	have actives, and then using those as a prefilter for counting up actual activities. This is done by using two instances of
	the frequency variant.
*/

export class CompoundAcquireSelectivity extends CompoundAcquire
{
	private phase = 1;
	private pass1:CompoundAcquireFrequent;
	private pass2:CompoundAcquireFrequent;

	constructor(assayIDList:number[], maxCompounds:number)
	{
		super(assayIDList, maxCompounds);

		this.pass1 = new CompoundAcquireFrequent(assayIDList, Number.MAX_SAFE_INTEGER, false);
		this.pass2 = new CompoundAcquireFrequent(assayIDList, maxCompounds, true);
	}

	public start():void
	{
		this.pass1.callbackResults = () => this.callbackResults();
		this.pass2.callbackResults = () => this.callbackResults();
		this.pass1.callbackFinished = () => this.finishedFirst();
		this.pass2.callbackFinished = () => this.finishedSecond();

		this.pass1.start();
	}

	public stop():void
	{
		this.cancelled = true;
		this.pass1.stop();
		this.pass2.stop();
	}

	public progressFraction():number
	{
		return this.phase == 1 ? 0.5 * this.pass1.progressFraction() : 0.5 + 0.5 * this.pass2.progressFraction();
	}

	// transforms the counts-thus-far into something that can be used to display preliminary progress
	public progressBars():number[]
	{
		return this.phase == 1 ? this.pass1.progressBars() : this.pass2.progressBars();
	}

	public progressText():string
	{
		return this.phase == 1 ? this.pass1.progressText() : this.pass2.progressText();
	}

	// ------------ private methods ------------

	// first phase is complete, so move onto the next part
	private finishedFirst():void
	{
		this.phase = 2;

		//this.pass2.whitelist = new Set<number>();
		//for (let grp of this.pass1.compounds) for (let cpdID of grp) this.pass2.whitelist.add(cpdID);

		let hashset = new Set<number>();
		for (let hash of this.pass1.hashes) hashset.add(hash);
		this.pass2.hashWhitelist = Array.from(hashset);

		this.pass2.existingCompounds = true;

		this.pass2.molCache = this.pass1.molCache;
		this.pass2.hashCompound = this.pass1.hashCompound;
		this.pass2.mapCompound = this.pass1.mapCompound;
		this.pass2.compounds = this.pass1.compounds;
		this.pass2.hashes = this.pass1.hashes;
		this.pass2.score = Vec.numberArray(0, this.pass1.compounds.length);
		this.pass2.actives = Vec.numberArray(0, this.pass1.compounds.length);
		this.pass2.inactives = Vec.numberArray(0, this.pass1.compounds.length);

		this.pass2.start();
	}

	// second phase is complete, so we're done
	private finishedSecond():void
	{
		this.compounds = this.pass2.compounds;
		this.hashes = this.pass2.hashes;
		this.score = this.pass2.score;
		this.callbackFinished();
	}
}

/* EOF */ }

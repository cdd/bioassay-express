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
	Data structure for holding measurement data for some number of assays. Handles the acquisition of the data from
	the server, which is done incrementally on demand.
*/

export interface MeasureAssay
{
	assayID:number;
	threshold:number;
	operator:string;
	field:string;
}

export interface MeasureColumn
{
	assayID:number;
	name:string;
	units:string;
	type:string; // generally 'activity', 'primary', 'measurement' or 'probe' (others may be added later)
	tagName?:string;
}

export interface MeasureDatum
{
	column:number; // index
	value:number;
	relation:string;
}

export interface MeasureCompound
{
	compoundID:number;
	mol?:wmk.Molecule; // defined only after downloading
	pubchemSID?:number; // not always defined
	pubchemCID?:number; // not always defined
	measurements:MeasureDatum[]; // sparse: numbering between 1 and #columns
	isActive?:boolean; // true/false/null depending on explicit activity assertion
	primaryValue?:number; // from the field pointed at by the annotation, or that labelled primary
	computedActive?:boolean; // from the explicit annotation, or the annotation fields
}

export class MeasureData
{
	public assays:Record<number, MeasureAssay> = {};
	public columns:MeasureColumn[] = [];
	public compounds:MeasureCompound[] = [];

	public static TYPE_ACTIVITY = 'activity';
	public static TYPE_PROBE = 'probe';
	public static TYPE_PRIMARY = 'primary';
	public static TYPE_MEASUREMENT = 'measurement';

	constructor(public assayIDList:number[])
	{
	}

	// sends the request to obtain the compound IDs for all of the assays
	public obtainCompounds(callback:(obj:MeasureData) => void):void
	{
		let params = {'assayIDList': this.assayIDList};
		callREST('REST/ListCompounds', params,
			(data:any) =>
			{
				this.processMeasurements(data);
				if (callback != null) callback(this);
			});
	}

	// when some number of compounds have no molecule, requests those mentioned by index
	public fetchCompounds(fetch:number[], callback:(obj:MeasureData) => void):void
	{
		let idlist:number[] = [], cpdlist:MeasureCompound[] = [];
		for (let idx of fetch)
		{
			idlist.push(this.compounds[idx].compoundID);
			cpdlist.push(this.compounds[idx]);
		}
		let params = {'compoundIDList': idlist};
		callREST('REST/ListCompounds', params,
			(data:any) =>
			{
				let molfile:string[] = data.molfileList;
				let pubchemCID:number[] = data.pubchemCIDList, pubchemSID:number[] = data.pubchemSIDList;
				for (let n = 0; n < fetch.length; n++) this.updateCompound(cpdlist[n], molfile[n], pubchemCID[n], pubchemSID[n]);
				if (callback != null) callback(this);
			});
	}

	// arrange compound order to be ascending or descending (based on activity)
	public reorderCompounds(order:number):void
	{
		// may want to invert the primary order, if decreasing (but only if all assays specify the same direction)
		let pridir:number = null;
		for (let key in this.assays)
		{
			let assay = this.assays[key];
			if (assay.operator == null) continue;
			if (assay.operator == '>' || assay.operator == '>=')
			{
				if (pridir == 1) {pridir = null; break;}
				pridir = 1;
			}
			else // <, <=, =
			{
				if (pridir == -1) {pridir = null; break;}
				pridir = -1;
			}
		}
		if (pridir == null) pridir = 1;

		let idxActivity = -1, idxPrimary = -1;
		for (let n = 0; n < this.columns.length; n++)
		{
			if (this.columns[n].type == 'activity') idxActivity = n;
			if (this.columns[n].type == 'primary') idxPrimary = n;
		}

		this.compounds.sort((cpd1:MeasureCompound, cpd2:MeasureCompound):number =>
		{
			if (idxActivity >= 0)
			{
				let v1 = -1, v2 = -1;
				for (let m of cpd1.measurements) if (m.column == idxActivity && m.value != null) v1 = m.value;
				for (let m of cpd2.measurements) if (m.column == idxActivity && m.value != null) v2 = m.value;
				if (v1 < v2) return -1 * order;
				if (v1 > v2) return order;
			}
			if (idxPrimary >= 0)
			{
				// (at this point directionality might be handy)
				let v1 = Number.NEGATIVE_INFINITY, v2 = Number.NEGATIVE_INFINITY;
				for (let m of cpd1.measurements) if (m.column == idxPrimary && m.value != null) {v1 = m.value; break;}
				for (let m of cpd2.measurements) if (m.column == idxPrimary && m.value != null) {v2 = m.value; break;}
				if (v1 < v2) return -1 * order * pridir;
				if (v1 > v2) return order * pridir;
			}
			return 0;
		});
	}

	// ------------ private methods ------------

	// from the datastructure provided by the service, turn it into the format used internally
	private processMeasurements(data:any):void
	{
		for (let assay of data.assays as MeasureAssay[]) this.assays[assay.assayID] = assay;

		this.columns = data.columns;

		// unique list of compounds: placeholder for each
		let compoundIDList:number[] = data.compoundIDList;
		let mapCompoundID:Record<number, number> = {};
		for (let cpdID of compoundIDList)
		{
			mapCompoundID[cpdID] = this.compounds.length;
			this.compounds.push({'compoundID': cpdID, 'measurements': []} as MeasureCompound);
		}

		// spool in the measurements; note that these are sent in "laminated" form because that's a much more efficient
		// way to send it down the wire
		let measureIndex:number[] = data.measureIndex;
		let measureCompound:number[] = data.measureCompound;
		let measureValue:number[] = data.measureValue;
		let measureRelation:string[] = data.measureRelation;
		for (let n = 0; n < measureIndex.length; n++)
		{
			let datum = {'column': measureIndex[n], 'value': measureValue[n], 'relation': measureRelation[n]} as MeasureDatum;
			let cpd = this.compounds[mapCompoundID[measureCompound[n]]];
			cpd.measurements.push(datum);
			if (this.columns[datum.column].type == 'activity') cpd.isActive = datum.value > 0;
		}

		// fill in derived values
		for (let cpd of this.compounds)
		{
			for (let datum of cpd.measurements) if (datum.value != null)
			{
				let col = this.columns[datum.column], assay = this.assays[col.assayID];
				if (assay && assay.field == col.name) {cpd.primaryValue = datum.value; break;}
			}
			if (cpd.primaryValue == null) for (let datum of cpd.measurements)
			{
				let col = this.columns[datum.column];
				if (col.type == 'primary') {cpd.primaryValue = datum.value; break;}
			}
			if (cpd.primaryValue != null) for (let datum of cpd.measurements)
			{
				let col = this.columns[datum.column], assay = this.assays[col.assayID];
				if (assay.threshold == null) continue;
				if (assay.operator == '=') cpd.computedActive = cpd.primaryValue == assay.threshold;
				else if (assay.operator == '<') cpd.computedActive = cpd.primaryValue < assay.threshold;
				else if (assay.operator == '>') cpd.computedActive = cpd.primaryValue > assay.threshold;
				else if (assay.operator == '<=') cpd.computedActive = cpd.primaryValue <= assay.threshold;
				else if (assay.operator == '>=') cpd.computedActive = cpd.primaryValue >= assay.threshold;
			}
		}
	}

	// apply the information about one compound to the corresponding record
	private updateCompound(cpd:MeasureCompound, molfile:string, pubchemCID:number, pubchemSID:number):void
	{
		// parse the molfile, if possible; in case of blank or failure: a zero-atom molecule is used to mark the record
		// as having been downloaded, but not having content
		if (molfile != null && molfile.length > 0) cpd.mol = wmk.MoleculeStream.readMDLMOL(molfile);
		if (cpd.mol == null) cpd.mol = new wmk.Molecule();

		cpd.pubchemCID = pubchemCID;
		cpd.pubchemSID = pubchemSID;
	}
}

/* EOF */ }

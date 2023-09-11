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
	Schema Template: implementation of the Java-side JSON representation for defining a template. Provides functionality for accessing the baseline ontology
	trees to create the implied tree structures.
*/

export enum TemplateValueSpecification
{
	Item = 'item',
	Exclude = 'exclude',
	WholeBranch = 'wholebranch',
	ExcludeBranch = 'excludebranch',
	Container = 'container',
}

export interface TemplateValue
{
	uri:string;
	name:string;
	descr?:string;
	altLabels?:string[];
	externalURLs?:string[];
	spec:TemplateValueSpecification;
	parentURI?:string;
}

export interface TemplateAssignment
{
	name:string;
	descr?:string;
	propURI:string;
	suggestions:SuggestionType;
	mandatory:boolean;
	values:TemplateValue[];
}

export interface TemplateGroup
{
	name:string;
	descr?:string;
	groupURI:string;
	canDuplicate:boolean;
	assignments:TemplateAssignment[];
	subGroups:TemplateGroup[];
}

export interface Template
{
	schemaPrefix:string; // universal identifier for the template, which can also be used to prefix implied URIs
	branchGroups?:string[]; // if this is a branch template, optional list of group URIs that this template can be nested under
	root:TemplateGroup;
}

/*
	The "schema" wraps the template and provides value added functionality, particularly for composing branch hierarchy information.
*/

export class Schema
{
	constructor(public template:Template)
	{
	}

	// generate a unique custom URI based on the schema prefix, which follows the preexisting numbering scheme
	public customURI():string
	{
		let pfx = this.template.schemaPrefix;

		let nameBits = this.template.root.name.split(/\s+/), postPfx = '';
		for (let bit of nameBits)
		{
			let ch = bit.charAt(0).toUpperCase();
			if (ch >= 'A' && ch <= 'Z') postPfx += ch; else postPfx += 'X';
		}
		if (postPfx) pfx += postPfx + '_';

		let highNum = 0;

		let processURI = (uri:string):void =>
		{
			if (!uri || !uri.startsWith(pfx)) return;
			let sfx = uri.substring(pfx.length);
			if (!/^(0*)[0-9+]+$/.test(sfx)) return;
			highNum = Math.max(highNum, parseInt(sfx));
		};
		let examineGroup = (group:TemplateGroup):void =>
		{
			processURI(group.groupURI);
			for (let assn of group.assignments)
			{
				processURI(assn.propURI);
				for (let value of assn.values) processURI(value.uri);
			}
			for (let sub of group.subGroups) examineGroup(sub);
		};

		examineGroup(this.template.root);

		let txt = (highNum + 1).toString();
		return pfx + '0'.repeat(Math.max(0, 7 - txt.length)) + txt;
	}

	// ------------ private methods ------------
}

/* EOF */ }

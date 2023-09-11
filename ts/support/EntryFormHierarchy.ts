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
	Analogous SchemaHierarchyGroup -- except that we're flat here, but trying to
 	keep the objects somewhat similar; if forms become hierarchy, create a new parent class
	for SchemaHierarchyGroup and subclass this from that method.
*/

export interface EntryFormHierarchySection extends EntryFormSection
{
	parent:EntryFormHierarchySection;
	sectionidx:number;
	assignments:SchemaAssignment[];
	subSection:EntryFormHierarchySection[];
	locator:string;
}

export class EntryFormHierarchy
{
	public root:EntryFormHierarchySection;

	private mapSection:Record<string, EntryFormHierarchySection> = {};

	constructor(public form:EntryForm, public schema:SchemaSummary)
	{
		for (let n = 0; n < form.sections.length; n++)
		{
			let section:EntryFormSection = form.sections[n];
			let obj = deepClone(section) as EntryFormHierarchySection;
			if (section.locator)
			{
				let ploc = section.locator.substring(0, section.locator.lastIndexOf(':'));
				ploc = ploc.substring(0, ploc.lastIndexOf(':') + 1);
				obj.parent = this.mapSection[ploc];
			}
			else obj.parent = null;
			obj.sectionidx = n;
			obj.assignments = [];
			obj.subSection = [];
			if (obj.parent) obj.parent.subSection.push(obj); else this.root = obj;
			this.mapSection[section.locator] = obj;
			this.categorizeAssignments(obj, obj.layout);
		}
	}

	// quick lookup for locators
	public findGroup(locator:string):EntryFormHierarchySection
	{
		return this.mapSection[locator];
	}

	public findAssignmentList(locator:string, useChildren:boolean):SchemaAssignment[]
	{
		let parentGroup = this.findGroup(locator);
		let returnList:SchemaAssignment[] = [];
		for (let assignment of parentGroup.assignments) returnList.push(assignment);
		if (useChildren) for (let child of parentGroup.subSection)
		{
			for (let assignment of this.findAssignmentList(child.locator, useChildren)) returnList.push(assignment);
		}
		return returnList;
	}

	// ------------ private methods ------------

	private categorizeAssignments(parentSection:EntryFormHierarchySection, parentLayout:EntryFormLayout[]):void
	{
		for (let layoutCell of parentLayout)
		{
			if (layoutCell.field)
			{
				let propURI = expandPrefix(layoutCell.field[0]);
				let groupNest = expandPrefixes(layoutCell.field.slice(1, layoutCell.field.length));
				let count = 0;

				for (let assn of this.schema.assignments)
				{
					if (propURI == assn.propURI && TemplateManager.compareBaselineGroupNest(groupNest, assn.groupNest))
					{
						parentSection.assignments.push(assn);
						count++;
					}
				}

				if (count == 0)
				{
					console.log('FORM PARSING failure, assignment not found (form: ' + this.form.name + ')');
					console.log('propURI=[' + propURI + '], groupNest=' + JSON.stringify(groupNest));
				}
			}

			if (layoutCell.layout) this.categorizeAssignments(parentSection, layoutCell.layout);
		}
	}
}

/* EOF */ }

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
	Takes the flattened schema representation that comes back from the API and turns it into a hierarchical form, which
	is more convenient for certain applications, e.g. displaying a tree-like layout of groups & assignments.

	The flattened schema provides all necessary information about the tree hierarchy by way of the "locator" property.
	Which has the form of either:

		#:#:		for groups
		#:#:#		for assignments

	The root group has a blank locator. Assignments descended directly from the root have the form "#". Groups descended
	directly form the root hae the form "#:", while their assignments have the form "#:#". The numeric values ("#") refer
	to the index of the group or assignment within the parent (0-based).
*/

export interface SchemaHierarchyGroup extends SchemaGroup
{
	parent:SchemaHierarchyGroup;
	groupidx:number;
	assignments:SchemaHierarchyAssignment[];
	subGroups:SchemaHierarchyGroup[];
}

export interface SchemaHierarchyAssignment extends SchemaAssignment
{
	parent:SchemaHierarchyGroup;
	assnidx:number;
}

export class SchemaHierarchy
{
	public root:SchemaHierarchyGroup;

	private mapGroup:Record<string, SchemaHierarchyGroup> = {};
	private mapAssn:Record<string, SchemaHierarchyAssignment> = {};
	private mapGroupNest:Record<string, SchemaHierarchyGroup> = {};

	constructor(public schema:SchemaSummary)
	{
		for (let n = 0; n < schema.groups.length; n++)
		{
			let group = schema.groups[n];
			let obj = deepClone(group) as SchemaHierarchyGroup;
			if (group.locator)
			{
				let ploc = group.locator.substring(0, group.locator.lastIndexOf(':'));
				ploc = ploc.substring(0, ploc.lastIndexOf(':') + 1);
				obj.parent = this.mapGroup[ploc];
			}
			else obj.parent = null;

			if (group.groupURI)
			{
				let groupNest = group.groupNest.slice(0);
				groupNest.unshift(group.groupURI);
				this.mapGroupNest[groupNest.join('::')] = obj;
			}

			obj.groupidx = n;
			obj.assignments = [];
			obj.subGroups = [];
			if (obj.parent) obj.parent.subGroups.push(obj); else this.root = obj;
			this.mapGroup[group.locator] = obj;
		}
		for (let n = 0; n < schema.assignments.length; n++)
		{
			let assn = schema.assignments[n];
			let obj = deepClone(assn) as SchemaHierarchyAssignment;
			obj.parent = this.mapGroup[assn.locator.substring(0, assn.locator.lastIndexOf(':') + 1)];
			obj.assnidx = n;
			obj.parent.assignments.push(obj);
			this.mapAssn[assn.locator] = obj;
		}
	}

	// quick lookup for locators
	public findGroup(locator:string):SchemaHierarchyGroup
	{
		return this.mapGroup[locator];
	}

	public findAssignment(locator:string):SchemaHierarchyAssignment
	{
		return this.mapAssn[locator];
	}
	public findGroupNest(groupNest:string[]):SchemaHierarchyGroup
	{
		if (groupNest == null || groupNest.length == 0) return null;
		return this.mapGroupNest[groupNest.join('::')];
	}

	public findAssignmentList(locator:string, useChildren:boolean):SchemaAssignment[]
	{
		let parentGroup = this.findGroup(locator);
		let returnList:SchemaAssignment[] = [];

		for (let assignment of parentGroup.assignments)
		{
			returnList.push(assignment);
		}

		if (useChildren)
		{
			for (let child of parentGroup.subGroups)
			{
				for (let assignment of this.findAssignmentList(child.locator, useChildren))
				{
					returnList.push(assignment);
				}
			}
		}

		return returnList;
	}

	// for debugging
	public toString(grp:SchemaHierarchyGroup = null, depth:number = 0):string
	{
		if (grp == null) grp = this.root;
		let pfx = '  '.repeat(depth), pfxi = pfx + '  ';
		let str = pfx + 'name=[' + grp.name + '] uri=[' + collapsePrefix(grp.groupURI) + '] locator=[' + grp.locator + ']\n';

		str += pfx + 'assignments: ' + grp.assignments.length + '\n';
		for (let assn of grp.assignments)
		{
			str += pfxi + 'name=[' + assn.name + '] uri=[' + collapsePrefix(assn.propURI) + '] locator=[' + assn.locator + ']\n';
		}

		str += pfx + 'subGroups:' + grp.subGroups.length + '\n';
		for (let subgrp of grp.subGroups)
		{
			str += pfxi + 'group:\n';
			str += this.toString(subgrp, depth + 2);
		}

		return str;
	}

	// ------------ private methods ------------
}

/* EOF */ }

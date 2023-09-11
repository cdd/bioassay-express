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
	Values suitable for hardcoding for the benefit of the whole project.
*/

export const ABSENCE_NOTAPPLICABLE = 'http://www.bioassayontology.org/bat#NotApplicable';
export const ABSENCE_NOTDETERMINED = 'http://www.bioassayontology.org/bat#NotDetermined';
export const ABSENCE_UNKNOWN = 'http://www.bioassayontology.org/bat#Unknown';
export const ABSENCE_AMBIGUOUS = 'http://www.bioassayontology.org/bat#Ambiguous';
export const ABSENCE_MISSING = 'http://www.bioassayontology.org/bat#Missing';
export const ABSENCE_DUBIOUS = 'http://www.bioassayontology.org/bat#Dubious';
export const ABSENCE_REQUIRESTERM = 'http://www.bioassayontology.org/bat#RequiresTerm';
export const ABSENCE_NEEDSCHECKING = 'http://www.bioassayontology.org/bat#NeedsChecking';

export const ALL_ABSENCE_TERMS:readonly string[] =
[
	ABSENCE_NOTAPPLICABLE,
	ABSENCE_NOTDETERMINED,
	ABSENCE_UNKNOWN,
	ABSENCE_AMBIGUOUS,
	ABSENCE_MISSING,
	ABSENCE_DUBIOUS,
	ABSENCE_REQUIRESTERM,
	ABSENCE_NEEDSCHECKING,
];

/* EOF */ }

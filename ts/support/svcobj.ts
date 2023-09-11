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
	Webservice objects: definitions for the content that comes back from the BAE webservice, to
	facilitate type compile-time checking.
*/

// an individual piece of information from PubChem's innards
export interface PubChemDatum
{
	type:string;
	id:string;
	comment:string;
}

// an annotation (within the assay definition)
export interface AssayAnnotation
{
	propURI:string;
	propLabel?:string;
	propDescr?:string;
	valueURI?:string;
	valueLabel?:string;
	valueDescr?:string;
	groupNest?:string[];
	groupLabel?:string[];
	outOfSchema?:boolean; // usually undefined, but set to true if conspicuously not supposed to be there
	valueHier?:string[];
	labelHier?:string[];
	altLabels?:string[];
	externalURLs?:string[];
}

export interface AssayAnnotationProposal extends AssayAnnotation
{
	highlightLabel:string;
	highlightAltLabel?:string;
}

// a prediction (within the assay definition)
export const enum AssaySuggestionType
{
	Cloned = 'cloned',
	Mined = 'mined',
	Guessed = 'guessed'
}
export interface AssaySuggestion extends AssayAnnotation
{
	combined:number;
	type?:AssaySuggestionType;
	axiomFiltered?:boolean; // if filtered by axioms
	triggers?:string[]; // if triggered by specific axioms
}

// similar to a suggestion, but from a text-extraction
export interface AssayTextExtraction extends AssaySuggestion
{
	score:number;
	count:number;
	begin:number[];
	end:number[];
	referenceText?:string;
}

// a block of annotation differences that led up to an assay's current status
export interface AnnotationHistory
{
	curationTime:number;
	curatorID:string;
	curatorName:string;
	curatorEmail:string;
	textPatch:string;
	uniqueIDPatch:string;
	annotsAdded:AssayAnnotation[];
	annotsRemoved:AssayAnnotation[];
	labelsAdded:AssayAnnotation[];
	labelsRemoved:AssayAnnotation[];
}

// provided as a "preview" mini-transliteration for list display purposes
export interface AssayTranslitPreview
{
	title:string;
	html:string;
}

// reference to a subordinate schema that gets grafted onto a primary one
export interface SchemaBranch
{
	schemaURI:string;
	groupNest:string[];
}

// reference to a group that has been cloned some number of times
export interface SchemaDuplication
{
	multiplicity:number;
	groupNest:string[];
}

// working object for an assay that is currently being annotated
export interface AssayDefinition
{
	assayID:number;
	uniqueID:string;
	annotations:AssayAnnotation[];
	text:string;
	schemaURI:string;
	schemaBranches?:SchemaBranch[];
	schemaDuplication?:SchemaDuplication[];
	pubchemXRef:PubChemDatum[];
	curationTime:number;
	curatorID:string;
	curatorName:string;
	curatorEmail:string;
	history:AnnotationHistory[];
	holdingIDList:number[];

	// optional content
	countCompounds?:number;
	translitPreviews?:AssayTranslitPreview[];
	isCloned?:boolean;
	isFresh?:boolean;
}

// minimalist information about a provisional term, in context of the hierarchy
export interface ProvTermSummary
{
	provisionalID:number;
	proposerID:string;
	role:string;
	bridgeStatus:string;
}

// represents a single term within the context of a tree that corresponds to all of the available terms
// for a particular assignment
export interface SchemaTreeNode
{
	depth:number; // depth within tree; 0 for root nodes
	parent:number; // index of parent, defined only if this is a linear-packed tree; -1 for root nodes
	uri:string; // full URI of the term
	abbrev:string; // abbreviated URI (sometimes more readable)
	name:string; // label of the term
	inSchema:boolean; // true if the term is mentioned in the schema, false if coincidently sharing a branch
	provisional:ProvTermSummary; // non-null if node represents a provisional term
	childCount:number; // number of descendents in the tree
	schemaCount:number; // number of descendents that are actually part of the schema
	inModel:boolean; // true if there is an underlying model for this term
	altLabels?:string[];
	externalURLs?:string[];
	isContainer?:boolean;
	axiomApplicable?:boolean; // false = has been excluded by axiom rules
}

// a column-based representation of a tree: serialising in this way is a simple method for saving a lot of
// transmission space when sent down the wire
export interface SchemaTreeLaminated
{
	prefixMap:Record<string, string>;

	depth:number[];
	parent:number[];
	name:string[];
	abbrev:string[];
	inSchema:boolean[];
	provisional:ProvTermSummary[];
	childCount:number[];
	schemaCount:number[];
	inModel:boolean[];
	altLabels:string[][];
	externalURLs:string[][];
	patternURIPrefixes?:string[][];
	patternURIMatches?:string[][];
	axiomApplicable?:boolean[];

	containers:number[];
}

// summary information about an individual assigment in a schema (for use below)
export interface SchemaAssignment
{
	name:string;
	descr:string;
	propURI:string;
	groupNest:string[];
	groupLabel:string[];
	locator:string;
	suggestions:SuggestionType;
	mandatory:boolean;
}

export const enum SuggestionType
{
	Full = 'full',
	Disabled = 'disabled',
	Field = 'field',
	URL = 'url',
	ID = 'id',
	String = 'string',
	Number = 'number',
	Integer = 'integer',
	Date = 'date',
}

// summary information about an individual group in a schema (for use below; note: flattened)
export interface SchemaGroup
{
	name:string;
	descr:string;
	groupURI:string;
	groupNest:string[]; // sequence leading up to this group, i.e. not including groupURI
	canDuplicate:boolean; // true if it's OK to create more than one
	locator:string;
}

// serialised representation of a schema template, as an overview
export interface SchemaSummary
{
	name:string;
	descr:string;
	schemaURI:string;
	canTransliterate:boolean;
	assignments:SchemaAssignment[];
	groups:SchemaGroup[];
}

// information about a value within an assignment
export interface SchemaValue
{
	name:string;
	uri:string;
	abbrev:string;
	descr:string;
	inSchema:boolean;
	isExplicit:boolean;
	hasModel?:boolean;
	inModel?:boolean; // note: REST calls are inconsistent about this
	isProvisional?:boolean;
	schemaCount?:number;
	altLabels?:string[];
	externalURLs?:string[];
}

// search result: an assay index, with metadata
export interface SearchResult
{
	assayID:number;
	schemaURI:string;
	text:string;
	uniqueID:string;
	similarity:number;
	annotations:AssayAnnotation[];
	curationTime:number;
	countCompounds?:number;
	translitPreviews?:AssayTranslitPreview[];
}

// structure-activity relationship models
export interface SARModel
{
	code:string;
	description:string;
	annotations:string[][];
	fplist:number[];
	contribs:number[];
	assayContribs:number[];
	assayActives:number[];
	assayCounts:number[];
	calibration:number[];
	rocAUC:number;
	rocX:number[];
	rocY:number[];
	numAssays:number;
	numCompounds:number;
	numActives:number;
	numInactives:number;
}

// definition of a trusted authentication option
export interface ServiceCommon
{
	type:string;
	name:string;
	prefix:string;
}
export interface ServiceTrusted extends ServiceCommon
{
	status:string;
}

// definition of a basic authentication option
export type ServiceBasic = ServiceCommon;

// definition of a basic authentication option
export type ServiceLDAP = ServiceCommon;

// definition of an OAuth based authentication service
export interface ServiceOAuth extends ServiceCommon
{
	url:string;
	scope:string;
	responseType:string;
	clientID:string;
	redirectURI:string;
}

export type AuthenticationService = ServiceTrusted | ServiceBasic | ServiceOAuth | ServiceLDAP;

// details of the current login
export interface LoginSession
{
	type:string;
	curatorID:string;
	status:string;
	serviceName:string; // the authentication service name (not the user)
	userID:string;
	userName:string;
	email:string;
	accessToken?:string; // required during login process; later managed in cookie store
	[key:string]:string;
}

// describes a single "holding bay" entry
export interface HoldingBayAssay
{
	holdingID:number;
	assayID:number;
	submissionTime:number;
	curatorID:string;
	curatorName:string;
	curatorEmail:string;
	uniqueID:string;
	schemaURI:string;
	schemaBranches:SchemaBranch[];
	schemaDuplication:SchemaDuplication[];
	deleteFlag:boolean;
	text:string;
	added:AssayAnnotation[];
	removed:AssayAnnotation[];
	[key:string]:any;
}

// sparse information about an available template
export interface TemplateSummary
{
	schemaURI:string;
	title:string;
	branchGroups?:string[];
}

// entryforms: specifications for how to go about data entry
export interface EntryForm
{
	name:string;
	priority:number;
	schemaURIList:string[];
	sections:EntryFormSection[];
}
export interface EntryFormSection
{
	name:string;
	description?:string;
	transliteration?:string;
	duplicationGroup?:string[];
	layout:EntryFormLayout[];
	locator?:string; // assigned at runtime
	/* these two will be meaningful soon...
	translit?:string;
	noteGroup?:string;*/
}
export enum EntryFormType
{
	Table = 'table',
	Row = 'row',
	Cell = 'cell',
}
export interface EntryFormLayout
{
	type:EntryFormType;
	span?:number;
	label?:string;
	field?:string[];
	layout?:EntryFormLayout[];
}

export interface ProvisionalTermBridge
{
	token:string;
	url:string;
	name:string;
	description:string;
}

export const enum ProvisionalTermRole
{
	Private = 'private',
	Public = 'public',
	Deprecated = 'deprecated',
}

export interface ProvisionalTerm
{
	parentURI:string;
	parentLabel:string;
	uri:string;
	label:string;
	description:string;
	explanation:string;
	provisionalID:number;
	proposerID?:string;
	proposerName?:string;
	role:ProvisionalTermRole;
	createdTime?:number;
	modifiedTime?:number;
	remappedTo?:string;
	bridgeStatus?:string;
	bridge?:ProvisionalTermBridge;
	countAssays?:number;
	countHoldings?:number;
}

export interface ValueBranchInfo
{
	valueURI:string;
	valueLabel:string;
	valueDescr:string;
	altLabels:string[];
	externalURLs:string[];
	valueHier:string[];
	labelHier:string[];
}

export interface URIPatternMap
{
	matchPrefix:string;
	externalURL:string;
	label:string;
}

export interface OntologyBranch
{
	uri:string;
	label:string;
	descr?:string;
	altLabels?:string[];
	externalURLs?:string[];
	descendents?:number;
	children?:OntologyBranch[];
	placeholders?:string[];
}

export interface OntologySearch
{
	uri:string;
	label:string;
	hierarchy:string[];
}

/* EOF */ }

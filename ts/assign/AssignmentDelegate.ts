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
	Delegate class that makes all of the assignment page information available to the various panels.
*/

export abstract class AssignmentDelegate
{
	public edit:EditAssignments; // main editing widget - always available - and follows the template
	public editMode = false;
	public keyword:GlobalKeywordBar = null; // keyword lookup-bar widget
	public selectedHbayChanges:HoldingBayAssay[] = []; // incorporated holding bay changes
	public templateForm:JQuery;
	public editingForms:EditEntryForm[] = [];
	public entryForms:EntryForm[] = [];
	public selectedForm = -1; // index into editingForms, or -1 for "main"
	public dataColumns:MeasureColumn[] = []; // measurement data: columns filled in when available
	public measureTable:MeasureTable = null;
	public extractions:AssayTextExtraction[] = []; // list of all text-mined predictions, jumbled together
	public suggestions:AssaySuggestion[] = []; // list of all probabilistic predictions, jumbled together
	public literals:AssaySuggestion[] = []; // list of all literals, jumbled together
	public initialText:string = null; // assay text at time of loading (needed to work backward through history)
	public clonedText:string = null; // text from the source-of-clone
	public clonedTerms:AssayAnnotation[] = []; // annotations from the source-of-clone
	public availableTemplates:TemplateSummary[] = [];
	public branchTemplates:TemplateSummary[] = [];
	public easterEggs:AssayAnnotation[] = [];
	public uriPatternMaps:URIPatternMaps = new URIPatternMaps();
	public identifiers:Record<string, string[]> = null; // caching to reduce HTTP requests; key = propURI + SEP + groupNest
	public axiomJustifications:AssaySuggestion[] = []; // singletons implied by axioms
	public axiomViolations:AssaySuggestion[] = []; // mutually contradictory annotations, as decided by axiom rules
	public axiomAdditional:AssaySuggestion[] = []; // annotations implied from axioms outside of the original tree
	public absenceTerms:string[] = []; // whitelist of allowed absence terms

	public actionEnterEditMode:() => void;
	public actionClearNew:() => void;
	public actionCloneNew:() => void;
	public actionShowOrigin:() => void;
	public actionSubmitAssayChanges:() => void;
	public actionDownloadAssay:() => void;
	public actionDeleteAssay:() => void;
	public actionUpdateUniqueIDValue:() => void;
	public actionBuildTemplate:() => void;
	public actionRedisplayAnnotations:() => void;
	public actionRemoveAnnotation:(annot:AssayAnnotation) => void;
	public actionAppendAnnotation:(annot:AssayAnnotation) => void;
	public actionNotApplicable:(assn:SchemaAssignment) => void;
	public actionDeleteTerm:(propURI:string, groupNest:string[], valueURI:string) => void;
	public actionDeleteText:(propURI:string, groupNest:string[], valueLabel:string) => void;
	public actionRestoreFromHistory:(hist:AnnotationHistory) => void;
	public actionRestoreHistoryText:(txt:string) => void;
	public actionPredictAnnotations:() => void;
	public actionStopPrediction:() => void;
	public actionEnterCheatMode:() => void;
	public actionChangedFullText:() => void;
	public actionPickNewBranch:(groupNest?:string[]) => void;
	public actionRemoveBranch:() => void;
	public actionCopyAssayContent:(assignments:SchemaAssignment[]) => void;
	public actionRemoveAnnotations:() => void;
	public actionLookupList:(idx:number, searchTxt:string) => void;
	public actionLookupTree:(idx:number, searchTxt:string) => void;
	public actionLookupText:(idx:number, initTxt:string, editing:boolean) => void;
	public actionLookupThreshold:(idx:number) => void;
	public actionAppendCustomText:(idx:number, txt:string) => void;
	public actionRebuiltAssignments:() => void;
	public actionHighlightTextExtraction:(extr:AssayTextExtraction) => void;
	public actionDuplicateGroup:(groupNest:string[], cloneContent:boolean) => void;
	public actionEraseGroup:(groupNest:string[]) => void;
	public actionDeleteGroup:(groupNest:string[]) => void;
	public actionMoveGroup:(groupNest:string[], dir:number) => void;
	public actionHasInsertableBranch:(groupNest:string[]) => boolean;
	public actionIsModified:() => boolean;
	public actionGloballyAssignNotApplicable:() => void;
	public actionStashForUndo:() => void;
	public actionExitFullText:() => void;
	public actionEnterFullText:() => void;
	public actionAssignNotApplicable:(assnList:SchemaAssignment[]) => void;
	public actionIsSelectedFormInitialized:() => boolean;

	constructor(public schema:SchemaSummary, public assay:AssayDefinition)
	{
		this.initialText = assay.text;
	}

	// ------------ private methods ------------
}

/* EOF */ }

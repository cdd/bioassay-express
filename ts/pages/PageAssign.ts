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

const UNDO_SIZE = 100;

/*
	Supporting functionality for the assignment page.
*/

interface PageAssignmentOptions
{
	entryForms?:EntryForm[];
	availableTemplates?:TemplateSummary[];
	branchTemplates?:TemplateSummary[];
	editMode?:boolean;
	canRequestProvisionals?:boolean;
	holdingBaySubmit?:number[];
	holdingBayDelete?:number[];
	absenceTerms?:string[];
	uriPatternMaps?:URIPatternMaps;
}

const SEP = '::';
const NOT_APPLICABLE_CUTOFF = 10;

export class PageAssignment extends AssignmentDelegate
{
	private panelHeader:PanelAssnHeader;
	private panelFooter:PanelAssnFooter;
	private panelProtocol:PanelAssnProtocol;
	private panelAnnotation:PanelAssnAnnotation;
	private panelSimilar:PanelAssnSimilar;

	private mainEntry:JQuery;
	private divSidebar:JQuery;
	private contSidebar:CollapsibleController;
	private contProtocol:CollapsibleContainer;
	private contAnnotation:CollapsibleContainer;
	private contSimilar:CollapsibleContainer;

	private btnQuickAxioms:JQuery;
	private btnQuickNotApplicable:JQuery;
	private btnQuickCopySection:JQuery;
	private btnShowKey:JQuery;
	private btnApplyCloneText:JQuery;
	private btnApplyCloneAnnots:JQuery;
	private btnTemplate:JQuery = null;
	private btnUndo:JQuery;
	private btnRedo:JQuery;

	private lookupIndex = -1; // stashed while dialogs are open
	private originalAssay:AssayDefinition;
	private lastTextSuggest = '';
	private currentWatermark = 0;
	private dictSuggestions:Record<string, AssaySuggestion[]> = {};
	private dictLiterals:Record<string, AssaySuggestion[]> = {};

	private canRequestProvisionals = false;
	private selectedFormInitialized = false;
	private holdingBaySubmit:number[] = [];
	private holdingBayDelete:number[] = [];

	private undoStack:AssayDefinition[] = [];
	private redoStack:AssayDefinition[] = [];
	private enterFullTextAssayState:AssayDefinition;
	private shouldAutoSuggestOnProtocolText = false;
	private shouldAutoSuggestOnTemplateChange = false;

	constructor(schema:SchemaSummary, assay:AssayDefinition, opt:PageAssignmentOptions = {})
	{
		super(schema, assay);

		if (schema) TemplateManager.cacheTemplate(schema);

		this.originalAssay = deepClone(assay);

		if (opt.entryForms != null) this.entryForms = opt.entryForms;
		if (opt.availableTemplates != null) this.availableTemplates = opt.availableTemplates;
		if (opt.branchTemplates != null) this.branchTemplates = opt.branchTemplates;
		if (opt.editMode != null) this.editMode = opt.editMode;
		if (opt.canRequestProvisionals != null) this.canRequestProvisionals = opt.canRequestProvisionals;
		if (opt.holdingBaySubmit != null) this.holdingBaySubmit = opt.holdingBaySubmit;
		if (opt.holdingBayDelete != null) this.holdingBayDelete = opt.holdingBayDelete;
		if (opt.absenceTerms != null) this.absenceTerms = opt.absenceTerms;
		if (opt.uriPatternMaps != null) this.uriPatternMaps = opt.uriPatternMaps;
		Popover.uriPatternMaps = this.uriPatternMaps;

		// usually edit mode is off by default, but if the user is logged in and this is clearly not an assay that has been
		// fetched from the database, then flip it on
		if (Authentication.isLoggedIn())
		{
			let isBlank = assay.annotations.length == 0 && !assay.text;
			this.editMode = opt.editMode || assay.assayID == 0 || assay.isCloned || isBlank;
		}

		if (assay.isCloned)
		{
			this.clonedText = assay.text;
			this.clonedTerms = assay.annotations;
			assay.text = '';
			assay.annotations = [];
		}
		if (assay.isCloned || assay.isFresh)
		{
			this.editMode = true;
			this.originalAssay.schemaURI = null;
			this.originalAssay.schemaBranches = null;
			this.originalAssay.schemaDuplication = null;
			this.originalAssay.assayID = null;
			this.originalAssay.uniqueID = null;
			this.originalAssay.annotations = [];
			this.originalAssay.text = '';
		}

		if (!assay.schemaURI && this.availableTemplates.length > 0) assay.schemaURI = this.availableTemplates[0].schemaURI;

		// setup the delegate methods; these are stored as lambda functions in order to ensure that 'this' is correct, which
		// takes the guesswork out of passing them on to subsequent callbacks
		this.actionEnterEditMode = () => this.enterEditMode();
		this.actionClearNew = () => this.clearNew();
		this.actionCloneNew = () => this.cloneNew();
		this.actionShowOrigin = () => this.showOrigin();
		this.actionSubmitAssayChanges = () => this.submitAssayChanges();
		this.actionDownloadAssay = () => this.downloadAnnotations();
		this.actionDeleteAssay = () => this.deleteAssay();
		this.actionUpdateUniqueIDValue = () => this.updateUniqueIDValue();
		this.actionBuildTemplate = () => this.buildTemplate();
		this.actionRedisplayAnnotations = () => this.redisplayAnnotations();
		this.actionAppendAnnotation = (annot:AssayAnnotation) => this.appendAnnotation(annot);
		this.actionRemoveAnnotation = (annot:AssayAnnotation) => this.removeAnnotation(annot);
		this.actionNotApplicable = (assn:SchemaAssignment) => this.appendNotApplicable(assn);
		this.actionDeleteTerm = (propURI:string, groupNest:string[], valueURI:string) =>
			this.deleteAnnotation(propURI, groupNest, valueURI, null);
		this.actionDeleteText = (propURI:string, groupNest:string[], valueLabel:string) =>
			this.deleteAnnotation(propURI, groupNest, null, valueLabel);
		this.actionRestoreFromHistory = (hist:AnnotationHistory) => this.restoreFromHistory(hist);
		this.actionRestoreHistoryText = (txt:string) => this.restoreHistoryText(txt);
		this.actionPredictAnnotations = () => this.predictAnnotations();
		this.actionStopPrediction = () => this.stopPrediction();
		this.actionEnterCheatMode = () => this.enterCheatMode();
		this.actionChangedFullText = () => this.changedFullText();
		this.actionPickNewBranch = (groupNest?:string[]) => this.pickNewBranch(groupNest);
		this.actionRemoveBranch = () => this.pickRemoveBranch();
		this.actionCopyAssayContent = (assignments:SchemaAssignment[]) => this.copyAssayContent(assignments);
		this.actionRemoveAnnotations = () => this.removeAnnotations();
		this.actionLookupList = (idx:number, searchTxt:string) => this.performLookupList(idx, searchTxt);
		this.actionLookupTree = (idx:number, searchTxt:string) => this.performLookupTree(idx, searchTxt);
		this.actionLookupText = (idx:number, initTxt:string, editing:boolean) => this.performLookupText(idx, initTxt, editing);
		this.actionLookupThreshold = (idx:number) => this.performLookupThreshold(idx);
		this.actionAppendCustomText = (idx:number, inTxt:string) => this.performAppendCustomText(idx, inTxt);
		this.actionRebuiltAssignments = () => this.rebuiltAssignments();
		this.actionHighlightTextExtraction = (extr:AssayTextExtraction) => this.panelProtocol.highlightTextExtraction(extr);
		this.actionDuplicateGroup = (groupNest:string[], cloneContent:boolean):void => this.duplicateGroup(groupNest, cloneContent);
		this.actionEraseGroup = (groupNest:string[]):void => this.eraseGroup(groupNest);
		this.actionDeleteGroup = (groupNest:string[]):void => this.deleteGroup(groupNest);
		this.actionMoveGroup = (groupNest:string[], dir:number):void => this.moveGroup(groupNest, dir);
		this.actionHasInsertableBranch = (groupNest:string[]):boolean => this.hasInsertableBranch(groupNest);
		this.actionIsModified = ():boolean => this.isModified();
		this.actionGloballyAssignNotApplicable = () => this.globalAssignNotApplicable();
		this.actionAssignNotApplicable = (assnList:SchemaAssignment[]) => this.assignNotApplicable(assnList);
		this.actionStashForUndo = () => this.stashUndo();
		this.actionEnterFullText = () => this.enterFullText();
		this.actionExitFullText = () => this.exitFullText();
		this.actionIsSelectedFormInitialized = ():boolean => this.selectedFormInitialized;

		this.edit = new EditAssignments(schema, assay, this);

		this.mainEntry = $('#mainEntry');
		this.mainEntry.css({'max-width': '100%'});
		this.mainEntry.empty();

		let mainFlex = $('<div/>').appendTo(this.mainEntry);
		mainFlex.css({'display': 'flex'});

		this.divSidebar = $('<div/>').appendTo(mainFlex);
		let divProtocol = $('<div/>').appendTo(mainFlex);
		let divAnnotation = $('<div/>').appendTo(mainFlex).css({'flex-grow': '1'});
		let divSimilar = $('<div/>').appendTo(mainFlex);

		this.contProtocol = new CollapsibleContainer('Protocol Text', mainFlex, true, true);
		this.contProtocol.render(divProtocol);
		this.contAnnotation = new CollapsibleContainer('Assay Annotations', mainFlex, false, true);
		this.contAnnotation.render(divAnnotation);
		this.contSimilar = new CollapsibleContainer('Similar', mainFlex, false, true);
		this.contSimilar.barTitle = 'Similar Assays';
		this.contSimilar.render(divSimilar);

		this.panelHeader = new PanelAssnHeader(this, $('#topButtons'), $('#topSearch'), $('#keywordBar'), $('#identityBar'));
		this.panelHeader.render();
		this.panelFooter = new PanelAssnFooter(this, $('#bottomButtons'), $('#variousXref'), $('#annotationHistory'),
												$('#pubchemWidget'), $('#measurementTable'));
		this.panelFooter.render();
		this.panelProtocol = new PanelAssnProtocol(this, this.contProtocol.getPanelArea());
		this.panelProtocol.render();
		this.panelAnnotation = new PanelAssnAnnotation(this, this.contAnnotation.getPanelArea());
		this.panelAnnotation.render();
		this.panelSimilar = new PanelAssnSimilar(this, this.contSimilar.getPanelArea());
		this.panelSimilar.render();

		window.onbeforeunload = () => this.interceptClosePage();
		this.defineTitle();
		this.defineNotices();

		let onToggle = (cont:CollapsibleContainer):void => cont.toggleContainerVisibility();
		this.contSidebar = new CollapsibleController([this.contProtocol, this.contAnnotation, this.contSimilar], onToggle);
		this.contSidebar.render(this.divSidebar);

		const makeQuickButton = (container:JQuery, icon:string, css = {}, tooltip:string = null):JQuery =>
		{
			const btn = $('<button class="btn btn-xs btn-normal"/>').prependTo(container);
			btn.css({'margin': '3px', 'display': 'none', ...css});
			btn.append($(`<span class="glyphicon glyphicon-${icon}" style="width: 0.9em; height: 1.2em;"/>`));
			if (tooltip) Popover.hover(domLegacy(btn), null, tooltip);
			return btn;
		};
		this.btnQuickAxioms = makeQuickButton(this.contAnnotation.getButtonArea(), 'heart');
		this.btnQuickAxioms.click(() => this.openQuickAxioms());

		this.btnQuickNotApplicable = makeQuickButton(this.contAnnotation.getButtonArea(), 'ban-circle', {}, 'Assign not-applicable fields');
		this.btnQuickNotApplicable.click(() => this.globalAssignNotApplicable());

		this.btnQuickCopySection = makeQuickButton(this.contAnnotation.getButtonArea(), 'copy');
		this.btnQuickCopySection.click(() => this.copySectionAnnotations());

		this.btnShowKey = makeQuickButton(this.contAnnotation.getButtonArea(), 'education', {'display': 'inline-block'});
		this.btnShowKey.click(() => this.panelAnnotation.toggleKey(this.btnShowKey));

		this.panelProtocol.transliterateText();

		if (this.assay.isCloned && this.clonedText)
		{
			this.btnApplyCloneText = makeQuickButton(this.contProtocol.getButtonArea(), 'import',
											{'display': 'inline-block'}, 'Copy over text from clone source.');
			this.btnApplyCloneText.click(() => this.applyClonedText());
		}
		if (this.assay.isCloned && this.clonedTerms.length > 0)
		{
			this.btnApplyCloneAnnots = makeQuickButton(this.contAnnotation.getButtonArea(), 'import',
											{'display': 'inline-block'}, 'Manifest all cloned terms.');
			this.btnApplyCloneAnnots.click(() => this.applyAllCloned());
		}

		// setup drop events
		this.mainEntry[0].addEventListener('dragover', (event) =>
		{
			event.stopPropagation();
			event.preventDefault();
			event.dataTransfer.dropEffect = 'copy';
		});
		this.mainEntry[0].addEventListener('drop', (event) =>
		{
			event.stopPropagation();
			event.preventDefault();
			this.dropDataInto(event.dataTransfer);
		});

		// setup paste event
		document.addEventListener('paste', (e:ClipboardEvent) =>
		{
			if (this.panelProtocol.hasFocus()) return; // normal paste behaviour in text box

			/* legacy code, from when ID numbers were used; we can probably ignore these, because none of them
			   actually want multiline JSON content anyway, so for now the check is superfluous
			if ($('#pubchemAID').is(':focus')) return; // ditto
			if ($('#keywordEntry').is(':focus')) return; // ditto
			if ($('#uniqueIDValue').is(':focus')) return; // ditto
			if ($('#searchValue').is(':focus')) return; // ditto
			if ($('#txtLookupText').is(':focus')) return; // ditto
			*/

			let txt = null;
			if (e.clipboardData && e.clipboardData.getData) txt = e.clipboardData.getData('text/plain');
			if (txt && this.pasteText(txt)) e.preventDefault();
		});

		this.buildTemplate();
		this.predictAnnotations();

		Authentication.hookAuthentChanged = () => this.updateEditStatus();

		ScrollToTop.enable();
	}

	// access to current state
	public getAssay():AssayDefinition {return this.assay;}
	public setAssay(assay:AssayDefinition, withStashUndo:boolean = true):void
	{
		if (withStashUndo) this.stashUndo();
		this.assay = assay;
		this.panelProtocol.setFullText(assay.text);
		this.edit.setAssay(this.assay);
		for (let form of this.editingForms) form.setAssay(this.assay);
	}

	// use the current assay reference to define the page title
	public defineTitle():void
	{
		// taking out for now because title is derivative from other variables
		//  the modified status should be activated when those variables are changed.

		if (this.assay.uniqueID)
		{
			let [src, id] = UniqueIdentifier.parseKey(this.assay.uniqueID);
			if (src)
			{
				document.title = 'Assign ' + src.name + ' ' + id + ' - CDD BioAssay Express';
				return;
			}
		}
		if (this.assay.assayID > 0)
		{
			document.title = 'Assign #' + this.assay.assayID + ' - CDD BioAssay Express';
			return;
		}
		document.title = 'New Assay - CDD BioAssay Express';
	}

	// display notices about the assay, if any
	public defineNotices(holdingBaySubmit:number[] = null, holdingBayDelete:number[] = null):void
	{
		holdingBaySubmit = holdingBaySubmit != null ? holdingBaySubmit : this.holdingBaySubmit;
		holdingBayDelete = holdingBayDelete != null ? holdingBayDelete : this.holdingBayDelete;

		let banner = $('#bannerAlert');
		if (holdingBaySubmit.length == 0 && holdingBayDelete.length == 0)
		{
			banner.hide();
			return;
		}
		banner.show();
		let bannerClasses = banner.attr('class').split(/\s+/).filter((c) => c.startsWith('alert-')).join(' ');
		banner.removeClass(bannerClasses);

		let parent = $('#bannerMessage').empty();

		let para = $('<div/>').appendTo(parent);
		let ahref = $('<a>holding bay</a>');
		ahref.attr('href', 'holding.jsp');
		if (holdingBayDelete.length > 0)
		{
			banner.addClass('alert-danger');
			para.append('This assay has been marked for deletion (see ', ahref, ').');
		}
		else // holdingBaySubmit.length > 0
		{
			banner.addClass('alert-warning');
			let unusedCount = this.assay.holdingIDList.length - this.selectedHbayChanges.length;
			let anchorVerb = unusedCount <= 0 ? 'view' : 'use';

			let useHoldingBayChanges = $('<a>' + anchorVerb + ' specific changes</a>').css('cursor', 'pointer');
			useHoldingBayChanges.click(() =>
			{
				let selnAssayChangesDlg = new SelectAssayChangesDialog(
					this.assay,
					this.selectedHbayChanges,
					(hbayChanges:HoldingBayAssay[]):void => this.applySelectedAssayChanges(hbayChanges));
				selnAssayChangesDlg.show();
			});

			if (unusedCount <= 0) para.append('This assay uses annotation changes from the holding bay. ');
			else para.append('This assay has annotation changes waiting to be applied. ');
			para.append('See the ', ahref, ' for details, or ', useHoldingBayChanges, '.');
		}
	}

	// populates the template section with its assignments
	public buildTemplate():void
	{
		this.panelProtocol.setFullText(this.assay.text);

		this.renderTemplateChoice();
		this.renderFormSelector();
		this.renderUndoRedoButtons();

		this.fetchIdentifiers();
	}

	// wipes the undo & redo stacks
	public clearHistory():void
	{
		this.undoStack = [];
		this.redoStack = [];
	}

	// appends the current state to the undo-stack
	public stashUndo():void
	{
		if (this.undoStack.length == 0 && this.assay.isFresh) return; // don't put empty stuff at the beginning
		this.undoStack.push(deepClone(this.assay));
		while (this.undoStack.length > UNDO_SIZE) this.undoStack.splice(0, 1);
		this.redoStack = [];
	}

  // reports on the state of the undo/redo buffers
	public canUndo():boolean {return this.undoStack.length > 0;}
	public canRedo():boolean {return this.redoStack.length > 0;}

	// actually does the undo/redo operation
	public performUndo():void
	{
		if (this.undoStack.length == 0) return;
		this.redoStack.push(deepClone(this.assay));
		this.setAssay(this.undoStack.pop(), false);
	}

  public performRedo():void
	{
		if (this.redoStack.length == 0) return;
		this.undoStack.push(deepClone(this.assay));
		this.setAssay(this.redoStack.pop(), false);
	}

  public getLastUndo():AssayDefinition
	{
		if (this.undoStack.length == 0) return;
		else return this.undoStack[this.undoStack.length - 1];
	}

	// refill all the annotation content
	private redisplayAnnotations():void
	{
		this.edit.fillAssignments();
		for (let form of this.editingForms) form.fillAssignments();
	}

	// start the prediction process, based on text/annotations; and also check for axiom violation
	public predictAnnotations():void
	{
		if (!this.editMode) return; // not used unless the user switches to edit mode

		this.assay.text = this.panelProtocol.getFullText();
		/* having nothing to base the prediction on isn't quite the dealbreaker we thought...
		if (!this.assay.text && this.assay.annotations.length == 0) return;*/
		let isBlank = !this.assay.text && this.assay.annotations.length == 0;

		let doTextMining = this.lastTextSuggest != this.assay.text; // no need to repeat with same text
		this.lastTextSuggest = this.assay.text;

		let assignments = this.schema.assignments, annotations = this.assay.annotations;
		let annotated = new Set<number>();
		for (let n = 0; n < annotations.length; n++)
		{
			let idx = this.findAssignment(annotations[n].propURI, annotations[n].groupNest);
			if (idx >= 0) annotated.add(idx);
		}

		let roster:number[] = [];
		if (doTextMining && !isBlank) roster.push(-1);
		let wanted:string[] = [SuggestionType.Full, SuggestionType.String, SuggestionType.Number, SuggestionType.Integer];

		if (isBlank) wanted = [SuggestionType.String, SuggestionType.Number, SuggestionType.Integer];

		for (let n = 0; n < assignments.length; n++) if (!annotated.has(n) && wanted.includes(assignments[n].suggestions)) roster.push(n);
		for (let n = 0; n < assignments.length; n++) if (annotated.has(n) && wanted.includes(assignments[n].suggestions)) roster.push(n);

		this.currentWatermark++;

		this.panelProtocol.statusBeginPredict();
		this.predictStep(this.assay.text, roster, this.currentWatermark);

		// concurrently: see if there are any axiom violations
		if (!isBlank)
		{
			let params =
			{
				'schemaURI': this.assay.schemaURI,
				'schemaBranches': this.assay.schemaBranches,
				'schemaDuplication': this.assay.schemaDuplication,
				'annotations': this.assay.annotations,
			};
			callREST('REST/CheckAxioms', params,
				(result:any) => this.setAxiomEffects(result.justifications, result.violations, result.additional));
		}
	}

	// puts the kibosh on prediction, if underway
	public stopPrediction():void
	{
		this.currentWatermark++;
		this.panelProtocol.statusEndPredict();
	}

	// enables an easter-egg for suggestions
	public enterCheatMode():void
	{
		let anything = false;
		for (let annot of this.assay.annotations) if (annot.valueURI) {anything = true; break;}
		if (!anything) {alert('Annotate some entries before enabling easter-egg mode.'); return;}

		let msg = 'Easter-egg mode: this will clear the current set of annotations, but keep track of them to highlight ' +
				  'correct suggestions. Apply?';
		if (!confirm(msg)) return;

		this.easterEggs = [];
		for (let annot of this.assay.annotations) if (annot.valueURI) this.easterEggs.push(annot);
		this.edit.setEasterEggs(this.easterEggs);
		for (let form of this.editingForms) form.setEasterEggs(this.easterEggs);

		this.panelAnnotation.toggleKey(this.btnShowKey, true);

		this.assay.annotations = [];
		this.edit.fillAssignments();
		this.predictAnnotations();
	}

	// user has dragged something into the main area: see if it is a recognised format
	public dropDataInto(transfer:any):void
	{
		let items = transfer.items, files = transfer.files;

		for (let n = 0; n < items.length; n++)
		{
			if (items[n].type.startsWith('text/plain'))
			{
				items[n].getAsString((str:string) => this.loadJSON(str, true));
				return;
			}
		}
		for (let n = 0; n < files.length; n++)
		{
			if (files[n].name.endsWith('json'))
			{
				let reader = new FileReader();
				reader.onload = (event) => this.loadJSON(reader.result.toString(), true);
				reader.readAsText(files[n]);
				return;
			}
		}
	}

	// user typed something into the unique ID box, so re-read it
	public updateUniqueIDValue():void
	{
		let [src, id] = UniqueIdentifier.parseKey(this.assay.uniqueID);
		let val = purifyTextPlainInput(this.panelHeader.inputUniqueID.val());
		if (src) this.assay.uniqueID = UniqueIdentifier.makeKey(src, val);

		let isInvalid = false;
		if (src && src.baseRegex) isInvalid = !new RegExp(src.baseRegex).test(val);
		this.panelHeader.inputUniqueID.css('background-color', isInvalid ? '#FFC0C0' : 'white');

		this.updateEditStatus();
	}

	// requesting the addition of a text annotation; if the removeText parameter is given, this is treated like an "edit"
	// operation, and the previous instance will be removed
	private appendLookupText(inText:string, removeText:string = null):void
	{
		let text = purifyTextPlainInput(inText);
		if (text.length == 0) return;

		let assn = this.schema.assignments[this.lookupIndex];

		if (removeText)
		{
			for (let n = 0; n < this.assay.annotations.length; n++)
			{
				let annot = this.assay.annotations[n];
				if (!annot.valueURI && annot.valueLabel == removeText &&
					samePropGroupNest(annot.propURI, annot.groupNest, assn.propURI, assn.groupNest))
				{
					this.assay.annotations.splice(n, 1);
					break;
				}
			}
		}

		let annot:AssayAnnotation =
		{
			'propURI': assn.propURI,
			'propLabel': this.findPropLabel(assn.propURI),
			'valueURI': null,
			'valueLabel': text,
			'groupNest': assn.groupNest
		};
		this.assay.annotations.push(annot);
		this.assay.annotations = PageAssignment.cleanupAnnotations(this.assay.annotations);

		this.edit.fillAssignments();
		for (let form of this.editingForms) form.fillAssignments();
		this.updateEditStatus();
	}

	// deletes all annotations
	public removeAnnotations():void
	{
		if (this.assay.annotations.length == 0) return;

		let msg = 'Remove all of the annotations for this assay, and start afresh?';
		if (!confirm(msg)) return;

		//this.btnRemoveAll.prop('disabled', true);
		this.assay.annotations = [];
		this.edit.fillAssignments();
		for (let form of this.editingForms) form.fillAssignments();
		this.predictAnnotations();
		if (this.keyword != null) this.keyword.changeAnnotations(this.assay.annotations);
		this.updateEditStatus();
	}

	// copy annotations in currently selected form
	public copySectionAnnotations():void
	{
		this.copyAssayContent(this.editingForms[this.selectedForm].getAssnList());
	}

	// offers to copy some portion of the assay content to the clipboard
	public copyAssayContent(assignmentSubset:SchemaAssignment[] = null):void
	{
		if (assignmentSubset == null)
		{
			new CopyAssayDialog(this.assay, this.schema).show();
		}
		else
		{
			let assayCopy = deepClone(this.assay);
			assayCopy.uniqueID = null;
			assayCopy.text = null;
			assayCopy.annotations = [];

			for (let assn of assignmentSubset)
			{
				for (let annot of this.assay.annotations)
					if (samePropGroupNest(annot.propURI, annot.groupNest, assn.propURI, assn.groupNest)) assayCopy.annotations.push(annot);
			}

			let copyDialog = new CopyAssayDialog(assayCopy, this.schema);
			copyDialog.performHeadlessCopy();
		}
	}

	// bring up a new tab to show the PubChem source entry
	public showOrigin():void
	{
		let url = UniqueIdentifier.composeRefURL(this.assay.uniqueID);
		if (!url)
		{
			alert('Need to make sure there is a globally unique identifier (e.g. PubChem AID)');
			return;
		}
		window.open(url, '_blank');
	}

	// requested that the current content be submitted
	public submitAssayChanges():void
	{
		if (!Authentication.canSubmitHoldingBay())
		{
			alert('You need to login before submitting assay annotations.');
			return;
		}

		this.assay.text = this.panelProtocol.getFullText();

		// disallow the submission if there's literally no content
		if (!this.assay.uniqueID && !this.assay.text && Vec.arrayLength(this.assay.annotations) == 0)
		{
			alert('Need to provide at least one annotation before submitting.');
			return;
		}

		if (!this.isSubmittable())
		{
			alert('There are no changes to submit.');
			return;
		}

		let assayHasHoldingBay = (this.holdingBaySubmit.length + this.holdingBayDelete.length) > 0;

		(async () =>
		{
			let oa = this.originalAssay;
			let originalSchema = await TemplateManager.asyncGraftTemplates(oa.schemaURI, oa.schemaBranches, oa.schemaDuplication);

			let dlg = new SubmitDialog(deepClone(this.assay), this.originalAssay, this.schema, originalSchema, this.extractions,
														this.axiomViolations, assayHasHoldingBay, this.selectedHbayChanges);
			dlg.callbackUpdated = (assayID:number) =>
			{
				// if the submission resulted in an assayID, it means that the assay was updated at point of contact; it might be
				// nice to fill in the details dynamically, but reloading the page has the benefit of really checking that the content
				// survived the write/read cycle

				window.onbeforeunload = null;
				document.location.href = 'assign.jsp?assayID=' + assayID;
			};
			dlg.callbackHolding = (holdingID:number) =>
			{
				this.originalAssay = deepClone(this.assay);
				this.defineNotices([holdingID], []);
			};
			dlg.show();
		})();
	}

	// initiates the assay deletion process, which involves adding a request-to-delete to the holding bay
	public deleteAssay():void
	{
		if (this.assay.assayID == 0) return;

		if (!Authentication.canSubmitHoldingBay())
		{
			alert('You need to login before requesting an assay deletion.');
			return;
		}

		let msg = 'Request that this assay be deleted?';
		if (!confirm(msg)) return;

		let params = {'assayID': this.assay.assayID};
		callREST('REST/DeleteAssay', params,
			(data:any) => this.deletionResponse(data.success, data.status, data.holdingID),
			() => alert('Deletion request failed'));
	}

	// download everything that's been done so far
	public downloadAnnotations():void
	{
		this.assay.text = this.panelProtocol.getFullText();
		if (!this.assay.annotations || this.assay.annotations.length == 0)
		{
			alert('Need to provide at least one annotation before downloading.');
			return;
		}

		let str = JSON.stringify(this.assay);
		let fn = 'assay';
		let [uid, code] = UniqueIdentifier.parseKey(this.assay.uniqueID);
		if (uid) fn += '_' + uid.shortName + code;
		else if (this.assay.assayID) fn += '_' + this.assay.assayID;
		fn += '.json';

		let a = window.document.createElement('a');
		a.href = window.URL.createObjectURL(new Blob([str], {'type': 'application/octet-stream'}));
		a.download = fn;

		document.body.appendChild(a);
		a.click();
		document.body.removeChild(a);
	}

	// captured the paste command: find a place to put it
	public pasteText(content:string):boolean
	{
		// note: right now only JSON-formatted text is considered interesting, but that could change
		return this.loadJSON(content, false);
	}

	// the user has asked to edit the assay: perform any steps necessary
	public enterEditMode():void
	{
		if (this.editMode) return;
		if (!Authentication.isLoggedIn())
		{
			alert('Activating edit mode, but note that you will not be able to submit changes until you have logged in.');
		}
		this.setEditMode(true);
		this.buildTemplate();
		this.updateEditStatus();
	}

	// zap the current content, if permission is available
	public clearNew():void
	{
		if (this.panelProtocol.getFullText() == '' && this.assay.annotations.length == 0) return; // nop
		if (!confirm('Clear content and start with a fresh page?')) return;

		// easiest way to do this is just to reload (could be smarter by rearranging the page)
		let url = window.location.href;
		let i = url.indexOf('?');
		if (i >= 0) url = url.substring(0, i);
		window.location.href = url;
	}

	// clone the entry: keep the content but erase the identifiers
	public cloneNew():void
	{
		if (this.panelProtocol.getFullText() == '' && this.assay.annotations.length == 0) return; // nop

		let cloneAssay = deepClone(this.assay);
		cloneAssay.assayID = null;
		if (cloneAssay.uniqueID)
			cloneAssay.uniqueID = cloneAssay.uniqueID.substring(0, cloneAssay.uniqueID.indexOf(':') + 1); // no colon -> blank
		//cloneAssay.annotations = cloneAssay.annotations.filter((annot:AssayAnnotation) => annot.valueURI); // only semantic annotations
		//cloneAssay.text = '';
		cloneAssay.isCloned = true;
		launchTabPOST(restBaseURL + '/assign.jsp', JSON.stringify(cloneAssay));
	}

	// if there is known to be compounds, proceed
	public setupMeasurementTable():void
	{
		this.panelFooter.rebuildMeasurementTable();
	}

	// once all the init-state is assembled, followup
	public finishSetup():void
	{
		this.updateEditStatus();
	}

	// requested that the current content be submitted
	public globalAssignNotApplicable():void
	{
		let notApplicableAssnList = [];
		if (this.selectedForm < 0) notApplicableAssnList = this.edit.getNotApplicableAssnList();
		else notApplicableAssnList = this.editingForms[this.selectedForm].getNotApplicableAssnList();

		this.assignNotApplicable(notApplicableAssnList);
	}

	// requested that the current content be submitted
	public assignNotApplicable(notApplicableAssnList:SchemaAssignment[]):void
	{
		if (notApplicableAssnList.length == 0)
		{
			alert('There are no annotations to set to Not Applicable');
			return;
		}

		let notApplicableAnnotationList = this.buildNotApplicableAssayAnnotation(notApplicableAssnList);

		// If the number of the blank annotations exceed a certain number -- show the dialog
		if (notApplicableAnnotationList.length > NOT_APPLICABLE_CUTOFF)
		{
			let annotationSelected:boolean[] = [];

			for (let n = 0; n < notApplicableAnnotationList.length; n++) annotationSelected[n] = true;

			let dlg = new BatchAnnotationDialog(this.schema, notApplicableAnnotationList, true, annotationSelected);

			dlg.callbackConfirmed = (annotList:AssayAnnotation[]) => this.appendMultipleAnnotations(annotList);
			dlg.show();
		}
		else
		{
			this.appendNotApplicableList(notApplicableAssnList);
		}
	}

	// ------------ private methods ------------

	// displays the current template as a title, but also a clickable button when there are choices available
	private renderTemplateChoice():void
	{
		let parent = this.panelHeader.divTemplateChoice;
		parent.empty();

		let table = $('<table width="100%"></table>').appendTo(parent), tr = $('<tr/>').appendTo(table);
		let tdTemplate = $('<td align="left" valign="top"/>').appendTo(tr);

		// render the template display, or a dropdown if there's an actual choice

		if (this.availableTemplates.length <= 1)
		{
			let div = $('<div/>').appendTo(tdTemplate).css('margin', '0.5em 0 0.5em 0');
			let span = $('<span/>').appendTo(div);
			span.css({'background-color': '#F7FAFC', 'border': '1px solid #CCD9E8', 'border-radius': '4px'});
			span.css({'padding': '0.5em', 'white-space': 'nowrap'});
			span.text(this.schema.name);
		}
		else
		{
			let div = $('<div class="btn-group"/>').appendTo(tdTemplate);

			this.btnTemplate = $('<button type="button" data-toggle="dropdown"/>').appendTo(div);
			this.btnTemplate.addClass('form-control btn btn-action dropdown-toggle');
			this.btnTemplate.text(this.schema.name);
			this.btnTemplate.append(' <span class="caret"></span>');

			let ul = $('<ul class="dropdown-menu" role="menu"/>').appendTo(div);
			for (let n = 0; n < this.availableTemplates.length; n++)
			{
				let li = $('<li/>').appendTo(ul);
				let t = this.availableTemplates[n];

				let href = $('<a href="#"/>').appendTo(li);
				if (t.schemaURI == this.assay.schemaURI)
				{
					let b = $('<b/>').appendTo(href);
					b.text(t.title);
					href.click(() => false);
				}
				else
				{
					href.text(t.title);
					const idx = n;
					href.click(() => this.changeTemplate(idx));
				}
			}
		}
	}

	// render the radio button used to select the form, if any
	private renderFormSelector():void
	{
		let parent = this.panelHeader.divFormSelector;
		parent.empty();

		for (let n = -1; n < this.editingForms.length; n++)
		{
			let dom = n < 0 ? this.templateForm : this.editingForms[n].dom;
			dom.css('display', this.selectedForm == n ? 'block' : 'none');
		}
		if (this.selectedForm >= 0) this.editingForms[this.selectedForm].wasShown();

		if (this.editingForms.length > 0)
		{
			let div = $('<div class="btn-group" data-toggle="buttons"/>').appendTo(parent);
			div.css('margin', '0.5em 0 0.5em 0');

			let order = Vec.identity0(this.editingForms.length);
			order.unshift(-1);
			order.sort((o1:number, o2:number):number =>
			{
				let pri1 = o1 < 0 ? 0 : this.editingForms[o1].form.priority;
				let pri2 = o2 < 0 ? 0 : this.editingForms[o2].form.priority;
				return pri1 - pri2;
			});

			if (!this.selectedFormInitialized)
			{
				this.selectedForm = order[0];
				this.selectedFormInitialized = true;
			}

			for (let n of order)
			{
				let formType = n < 0 ? 'template-type' : 'section-type';
				let title = n < 0 ? 'Template' : this.editingForms[n].form.name;
				let lbl = $('<label class="btn btn-radio ' + formType + '"/>').appendTo(div);
				let input = $('<input type="radio" name="options" autocomplete="off">' + escapeHTML(title) + '</input>').appendTo(lbl);
				lbl.click(() => this.changeEditingForm(n));
				if (this.selectedForm == n)
				{
					lbl.addClass('active');
					input.prop('checked', true);
				}
			}
		}

		this.changeEditingForm(this.selectedForm);
	}

	// change template: perform a bunch of surgery and transform the viewport
	private changeTemplate(idx:number):void
	{
		let schemaURI = this.availableTemplates[idx].schemaURI;

		if (Vec.arrayLength(this.assay.schemaBranches) == 0 && Vec.arrayLength(this.assay.schemaDuplication) == 0)
		{
			TemplateManager.ensureTemplates([schemaURI], () => this.replaceTemplate(TemplateManager.getTemplate(schemaURI)));
		}
		else
		{
			TemplateManager.graftTemplates(schemaURI, this.assay.schemaBranches, this.assay.schemaDuplication,
				(schema:SchemaSummary) => this.replaceTemplate(schema));
		}
	}

	// user has asked for a different editing form, so make the switch
	private changeEditingForm(idx:number):void
	{
		if (this.selectedForm >= 0) this.editingForms[this.selectedForm].wasHidden();

		this.selectedForm = idx;
		for (let n = -1; n < this.editingForms.length; n++)
		{
			let dom = n < 0 ? this.templateForm : this.editingForms[n].dom;
			dom.css('display', this.selectedForm == n ? 'block' : 'none');
		}

		if (this.selectedForm >= 0)
		{
			this.contProtocol.hide(); // assuming form is quite wide, hide the text; maybe should be configurable
			this.editingForms[this.selectedForm].wasShown();
			this.btnQuickNotApplicable.css('display', 'none');
			this.btnQuickCopySection.css('display', 'inline-block');
		}
		else
		{
			this.btnQuickNotApplicable.css('display', 'inline-block');
			this.btnQuickCopySection.css('display', 'none');
		}

		this.updateEditStatus();
	}

	// create pair of buttons for undo/redo
	private renderUndoRedoButtons():void
	{
		let parent = this.panelHeader.divFormSelector;
		let div = $('<div class="btn-group" data-toggle="buttons"/>').appendTo(parent);
		div.css('margin', '0.5em 0em 0.5em 0.5em');
		div.css('float', 'right');

		this.btnUndo = $('<button class="btn btn-action">Undo</button>').appendTo(div);
		this.btnUndo.prop('disabled', true);
		this.btnUndo.click(() => this.performUndo());

		this.btnRedo = $('<button class="btn btn-action">Redo</button>').appendTo(div);
		this.btnRedo.prop('disabled', true);
		this.btnRedo.click(() => this.performRedo());
	}

	// change current editing mode, which affects the screen display significantly
	public setEditMode(editMode:boolean):void
	{
		this.keyword.setEnabled(this.editMode);

		if (this.editMode == editMode) return;

		// finagle the URL bar to indicate edit mode (is useful when hitting reload)
		let href = document.location.href;
		if (href.endsWith('#')) href = href.substring(0, href.length - 1);
		let qpos = href.lastIndexOf('?');
		if (qpos >= 0)
		{
			let bits = href.substring(qpos + 1).split('&').filter((value:string) => !value.startsWith('edit='));
			if (editMode) bits.push('edit=true');
			href = href.substring(0, qpos + 1) + bits.join('&');
		}
		else if (editMode) href += '?edit=true';
		window.history.pushState('?', document.title, href);

		this.editMode = editMode;
		this.panelProtocol.updateEditStatus();

		this.edit.updateEditMode();
		for (let form of this.editingForms) form.updateEditMode();
		if (editMode && this.suggestions.length == 0 && this.shouldAutoSuggestOnProtocolText) this.predictAnnotations();

		this.panelHeader.rebuildIdentifiers();
		this.updateEditStatus();
	}

	// new template fetched: apply the changes
	private replaceTemplate(schema:SchemaSummary):void
	{
		TemplateManager.harmoniseAnnotations(schema, this.assay.annotations);

		this.schema = schema;
		this.edit.replaceTemplate(schema);
		for (let form of this.editingForms) form.replaceTemplate(schema);

		this.assay.schemaURI = schema.schemaURI;
		this.edit.clearSelection();
		this.keyword.changeSchema(schema.schemaURI);

		this.buildTemplate();
		this.panelAnnotation.rebuild();

		if (this.selectedForm >= this.editingForms.length) this.selectedForm = -1;
		this.renderFormSelector();

		this.fetchIdentifiers();

		this.stopPrediction();
		if (this.shouldAutoSuggestOnTemplateChange) this.predictAnnotations();
		this.updateEditStatus();
	}

	// performs one unit of prediction: obtains the batch for a particular property, that being the first in the roster
	private predictStep(text:string, roster:number[], watermark:number):void
	{
		// premature termination: the task has been interrupted by incrementing the watermark (i.e. a replacement prediction sequence)
		if (watermark != this.currentWatermark) return;

		// normal termination: run out of stuff to do; restore the UI state
		if (roster.length == 0)
		{
			this.panelProtocol.statusEndPredict();
			return;
		}

		// special value of -1 is for a textmining extraction
		if (roster[0] < 0)
		{
			roster.shift();
			let params =
			{
				'schemaURI': this.assay.schemaURI,
				'schemaBranches': this.assay.schemaBranches,
				'schemaDuplication': this.assay.schemaDuplication,
				'text': text,
				'existing': this.assay.annotations
			};
			callREST('REST/TextMine', params,
				(data:any) =>
				{
					if (watermark == this.currentWatermark) this.replaceExtractions(data.extractions, text);
					this.predictStep(text, roster, watermark);
				});
			return;
		}

		// see if there are any suggestions to be requested; bunch them up and grab as many as possible
		let suggestIdx:number[] = [], suggestReq:any[] = [];
		for (let n = 0; n < roster.length; n++)
		{
			let assn = this.schema.assignments[roster[n]];
			if (assn.suggestions == SuggestionType.Full)
			{
				suggestIdx.push(roster[n]);
				suggestReq.push({'propURI': assn.propURI, 'groupNest': assn.groupNest});
				roster.splice(n, 1);
				n--;
			}
		}
		if (suggestIdx.length > 0)
		{
			let params =
			{
				'schemaURI': this.assay.schemaURI,
				'schemaBranches': this.assay.schemaBranches,
				'schemaDuplication': this.assay.schemaDuplication,
				'text': this.assay.text,
				'assignments': suggestReq,
				'accepted': Vec.concat(this.assay.annotations, this.extractions), // note: duplicates are rare but possible, which is OK
				'allTerms': false
			};
			callREST('REST/Suggest', params,
				(results:any[]) =>
				{
					if (watermark != this.currentWatermark) return;

					// apply the suggestions that came back (same order as submission)
					for (let n = 0; n < results.length; n++)
						this.replacePredProp(suggestIdx[n], results[n].suggestions, results[n].axiomFiltered);

					// anything that didn't get included is out of time; put it back in the queue
					for (let n = suggestIdx.length - 1; n >= results.length; n--) roster.unshift(suggestIdx[n]);

					this.predictStep(text, roster, watermark);
				});
			return;
		}

		// next: consider singleton invocations
		const idx = roster.shift(), assn = this.schema.assignments[idx];
		let sugg = assn.suggestions;
		if (sugg == SuggestionType.String || sugg == SuggestionType.Number || sugg == SuggestionType.Integer)
		{
			let params =
			{
				'schemaURI': this.assay.schemaURI,
				'schemaBranches': this.assay.schemaBranches,
				'schemaDuplication': this.assay.schemaDuplication,
				'locator': assn.locator
			};
			callREST('REST/GetLiteralValues', params,
				(values:Record<string, number>) =>
				{
					if (watermark == this.currentWatermark) this.replaceLiterals(idx, values);
					this.predictStep(text, roster, watermark);
				});
		}
		else
		{
			// just move on to the next one
			this.predictStep(text, roster, watermark);
		}
	}

	// switches out existing predictions for new ones
	private replacePredProp(idx:number, newSuggest:AssaySuggestion[], axiomFiltered:boolean):void
	{
		for (let suggest of newSuggest)
		{
			suggest.propURI = expandPrefix(suggest.propURI);
			suggest.valueURI = expandPrefix(suggest.valueURI);
			suggest.axiomFiltered = axiomFiltered;
		}

		let assn = this.schema.assignments[idx];
		for (let n = this.suggestions.length - 1; n >= 0; n--)
			if (samePropGroupNest(this.suggestions[n].propURI, this.suggestions[n].groupNest, assn.propURI, assn.groupNest))
				this.suggestions.splice(n, 1);
		this.suggestions = this.suggestions.concat(newSuggest);

		// need a dictionary version of predictions, for quick lookup
		this.resetDictSuggestions();

		this.edit.fillOneAssignment(idx);
		for (let form of this.editingForms) form.fillOneAssignment(idx);

		this.btnQuickAxioms.css('display', 'inline-block');
		this.btnQuickNotApplicable.css('display', 'inline-block');
		if (this.axiomImpliedSuggestions().length == 0)
		{
			this.btnQuickAxioms.removeClass('btn-action');
			this.btnQuickAxioms.addClass('btn-normal');
		}
		else
		{
			this.btnQuickAxioms.removeClass('btn-normal');
			this.btnQuickAxioms.addClass('btn-action');
		}
	}

	private resetDictSuggestions():void
	{
		this.dictSuggestions = {};
		for (let p of this.suggestions)
		{
			let key = p.propURI + SEP + p.valueURI;
			let list:AssaySuggestion[] = this.dictSuggestions[key];
			if (list == null) this.dictSuggestions[key] = [p]; else list.push(p);
		}
	}

	// switches out the list of currently used literals
	private replaceLiterals(idx:number, values:Record<string, number>):void
	{
		let assn = this.schema.assignments[idx];
		if (!this.literals) this.literals = [];
		for (let n = this.literals.length - 1; n >= 0; n--)
		{
			let lit = this.literals[n];
			if (samePropGroupNest(lit.propURI, lit.groupNest, assn.propURI, assn.groupNest)) this.literals.splice(n, 1);
		}

		let preds:AssaySuggestion[] = [];
		for (let value in values)
		{
			let p:AssaySuggestion =
			{
				'propURI': assn.propURI,
				'propLabel': assn.name,
				'valueURI': null,
				'valueLabel': value,
				'groupNest': assn.groupNest,
				'combined': values[value]
			};
			preds.push(p);
		}
		this.literals = this.literals.concat(preds);

		// need a dictionary version of predictions, for quick lookup
		this.dictLiterals = {};
		for (let p of this.literals)
		{
			let key = p.propURI + SEP + p.valueURI;
			let list:AssaySuggestion[] = this.dictLiterals[key];
			if (list == null) this.dictLiterals[key] = [p]; else list.push(p);
		}

		this.edit.fillOneAssignment(idx);
		for (let form of this.editingForms) form.fillOneAssignment(idx);
	}

	// new text mined extractions have come through: handle accordingly
	private replaceExtractions(extractions:AssayTextExtraction[], fromText:string):void
	{
		for (let x of extractions)
		{
			x.propURI = expandPrefix(x.propURI);
			x.valueURI = expandPrefix(x.valueURI);
			for (let n = 0; n < Vec.arrayLength(x.groupNest); n++) x.groupNest[n] = expandPrefix(x.groupNest[n]);
			x.referenceText = fromText;
		}

		// if nothing has changed, no need to bother anyone
		let before:string[] = [], after:string[] = [];
		for (let annot of this.extractions) before.push(keyPropGroup(annot.propURI, annot.groupNest) + '::' + annot.valueURI);
		for (let annot of extractions) after.push(keyPropGroup(annot.propURI, annot.groupNest) + '::' + annot.valueURI);
		before.sort();
		after.sort();
		if (Vec.equals(before, after)) return;

		this.extractions = extractions;
		this.edit.fillAssignments();
		for (let form of this.editingForms) form.fillAssignments();
	}

	// invoked a request for available terms in flattened list form (i.e. no hierarchy)
	private performLookupList(idx:number, searchTxt:string):void
	{
		this.performLookup(idx, searchTxt, true);
	}

	// invoked a request for available terms, displayed as understood by the schema-defined hierarchy
	private performLookupTree(idx:number, searchTxt:string):void
	{
		this.performLookup(idx, searchTxt, false);
	}

	private performLookup(idx:number, searchTxt:string, asList:boolean):void
	{
		this.lookupIndex = idx;
		let assn = this.schema.assignments[idx];
		const settings:PickTermSettings =
		{
			'schema': this.schema,
			'schemaBranches': this.assay.schemaBranches,
			'schemaDuplication': this.assay.schemaDuplication,
			'canRequestProvisionals': this.canRequestProvisionals,
			'searchTxt': searchTxt,
			'annotations': this.assay.annotations,
			'uriPatternMaps': this.uriPatternMaps
		};
		let dlg = new PickTermDialog(assn, settings,
									(annot:AssayAnnotation):void => this.appendAnnotation(annot));
		dlg.removeAnnotationFunc = (annot:AssayAnnotation):void => this.removeAnnotation(annot);

		if (this.dictSuggestions) for (let key in this.dictSuggestions) for (let p of this.dictSuggestions[key])
			if (samePropGroupNest(p.propURI, p.groupNest, assn.propURI, assn.groupNest)) dlg.predictionScores[p.valueURI] = p.combined;

		if (asList)
			dlg.showList('<u>' + escapeHTML(assn.name) + '</u> List');
		else
			dlg.showTree('<u>' + escapeHTML(assn.name) + '</u> Tree');
	}

	// bring up the selection dialog for threshold selection
	private performLookupThreshold(idxThresh:number):void
	{
		if (!this.measureTable) return;

		let idxField = -1, valField:string = null;
		let idxUnits = -1, valUnits:string = null, labelUnits:string = null;
		let idxOp = -1, valOperator:string = null;
		let valThreshold:number = null;
		let groupNest = this.schema.assignments[idxThresh].groupNest;

		const URI_FIELD = ThresholdDialog.URI_FIELD, URI_UNITS = ThresholdDialog.URI_UNITS;
		const URI_OPERATOR = ThresholdDialog.URI_OPERATOR, URI_THRESHOLD = ThresholdDialog.URI_THRESHOLD;

		for (let n = 0; n < this.schema.assignments.length; n++)
		{
			let assn = this.schema.assignments[n];
			if (samePropGroupNest(assn.propURI, assn.groupNest, URI_FIELD, groupNest)) idxField = n;
			if (samePropGroupNest(assn.propURI, assn.groupNest, URI_UNITS, groupNest)) idxUnits = n;
			if (samePropGroupNest(assn.propURI, assn.groupNest, URI_OPERATOR, groupNest)) idxOp = n;
		}
		if (idxField < 0 || idxUnits < 0 || idxOp < 0) return; // ought to be protected against this eventuality
		for (let annot of this.assay.annotations) if (compatibleGroupNest(annot.groupNest, groupNest))
		{
			if (annot.propURI == URI_FIELD) valField = annot.valueLabel;
			if (annot.propURI == URI_UNITS) [valUnits, labelUnits] = [annot.valueURI, annot.valueLabel];
			if (annot.propURI == URI_OPERATOR) valOperator = annot.valueURI;
			if (annot.propURI == URI_THRESHOLD) valThreshold = parseFloat(annot.valueLabel);
		}

		let dlg = new ThresholdDialog(this.measureTable.data, valField, valUnits, labelUnits, valOperator, valThreshold);
		dlg.actionAssign = (op:string, thresh:number):void =>
		{
			for (let n = this.assay.annotations.length - 1; n >= 0; n--)
			{
				let annot = this.assay.annotations[n];
				if (compatiblePropGroupNest(annot.propURI, annot.groupNest, URI_OPERATOR, groupNest) ||
					compatiblePropGroupNest(annot.propURI, annot.groupNest, URI_THRESHOLD, groupNest))
					this.assay.annotations.splice(n, 1);
			}
			let opLabel = op == ThresholdDialog.OPERATOR_GREATER ? 'greater than (>)' :
						  op == ThresholdDialog.OPERATOR_LESSTHAN ? 'less than (<)' :
						  op == ThresholdDialog.OPERATOR_GREQUAL ? 'greater than or equal (≥)' :
						  op == ThresholdDialog.OPERATOR_LTEQUAL ? 'less than or equal (≤)' :
						  op == ThresholdDialog.OPERATOR_EQUAL ? 'equal to (=)' : null;

			this.assay.annotations.push(
			{
				'propURI': URI_OPERATOR,
				'propLabel': this.findPropLabel(URI_OPERATOR),
				'valueURI': op,
				'valueLabel': opLabel,
				'groupNest': this.schema.assignments[idxOp].groupNest
			});
			this.assay.annotations.push(
			{
				'propURI': URI_THRESHOLD,
				'propLabel': this.findPropLabel(URI_THRESHOLD),
				'valueURI': null,
				'valueLabel': thresh.toString(),
				'groupNest': this.schema.assignments[idxThresh].groupNest
			});
			this.assay.annotations = PageAssignment.cleanupAnnotations(this.assay.annotations);

			this.edit.fillAssignments();
			for (let form of this.editingForms) form.fillAssignments();
			this.updateEditStatus();
		};
		dlg.show();
	}

	// add one-or-many annotations, and make sure everything is uptodate
	private appendAnnotation(annot:AssayAnnotation):void
	{
		this.appendMultipleAnnotations([annot]);
	}
	private appendMultipleAnnotations(annotList:AssayAnnotation[]):void
	{
		// stash the annotation onto the undo stack
		this.stashUndo();

		if (!this.assay.annotations) this.assay.annotations = [];

		let cloneKeys:string[] = [];
		if (this.clonedTerms) for (let annot of this.clonedTerms)
			cloneKeys.push(keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueURI) + SEP + annot.valueLabel);

		for (let annot of annotList)
		{
			if (!annot.propLabel) annot.propLabel = this.findPropLabel(annot.propURI);
			this.assay.annotations.push(annot);

			// if it's in the cloned list, remove it
			let idx = cloneKeys.indexOf(keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueURI) + SEP + annot.valueLabel);
			if (idx >= 0)
			{
				cloneKeys.splice(idx, 1);
				this.clonedTerms.splice(idx, 1);
			}
		}
		this.assay.annotations = PageAssignment.cleanupAnnotations(this.assay.annotations);

		this.edit.fillAssignments();
		for (let form of this.editingForms) form.fillAssignments();
		this.predictAnnotations();
		if (this.keyword != null) this.keyword.changeAnnotations(this.assay.annotations);

		this.updateCloneApply();
		this.updateEditStatus();
	}

	private removeAnnotation(annot:AssayAnnotation):void
	{
		// stolen from DataEntry -- not sure if is necessary

		for (let n = this.extractions.length - 1; n >= 0; n--)
		{
			let p = this.extractions[n];
			if (samePropGroupNest(annot.propURI, annot.groupNest, p.propURI, p.groupNest) && annot.valueURI == p.valueURI)
				this.extractions.splice(n, 1);
		}
		for (let n = this.suggestions.length - 1; n >= 0; n--)
		{
			let p = this.suggestions[n];
			if (samePropGroupNest(annot.propURI, annot.groupNest, p.propURI, p.groupNest) && annot.valueURI == p.valueURI)
				this.suggestions.splice(n, 1);
		}

		this.deleteAnnotation(annot.propURI, annot.groupNest, annot.valueURI, annot.valueLabel);
	}

	public static cleanupAnnotations(annotations:AssayAnnotation[]):AssayAnnotation[]
	{
		let annotatedAssignments = new Set();
		for (let annot of annotations)
		{
			if (annot.valueURI != ABSENCE_NOTAPPLICABLE)
				annotatedAssignments.add(keyPropGroup(annot.propURI, annot.groupNest));
		}

		// remove N/A annotations that have other annotations in the same assignment
		annotations = annotations.filter((a) =>
		{
			let key = keyPropGroup(a.propURI, a.groupNest);
			return !(a.valueURI == ABSENCE_NOTAPPLICABLE && annotatedAssignments.has(key));
		});

		// remove duplicates
		let seen = new Set();
		annotations = annotations.filter((a) =>
		{
			let key = keyPropGroupValue(a.propURI, a.groupNest, a.valueURI) + SEP + a.valueLabel;
			if (seen.has(key)) return false;
			seen.add(key);
			return true;
		});
		return annotations;
	}

	// mark the assignment as "not applicable" by asserting that annotation, and removing any persistent suggestions
	private appendNotApplicable(assn:SchemaAssignment):void
	{
		this.appendNotApplicableList([assn]);
	}

	// mark the assignment as "not applicable" by asserting that annotation, and removing any persistent suggestions
	private appendNotApplicableList(assnList:SchemaAssignment[]):void
	{
		this.appendMultipleAnnotations(this.buildNotApplicableAssayAnnotation(assnList));
	}

	private buildNotApplicableAssayAnnotation(assnList:SchemaAssignment[]):AssayAnnotation[]
	{
		let newAnnotList:AssayAnnotation[] = [];

		for (let assn of assnList)
		{
			// if annotation already has values skip it...
			let hasAnnots = (annotList:AssayAnnotation[]):boolean =>
			{
				let returnFlag = false;

				for (let n = 0; n < annotList.length; n++)
				{
					if (samePropGroupNest(annotList[n].propURI, annotList[n].groupNest, assn.propURI, assn.groupNest))
					{
						returnFlag = true;
					}
				}

				return returnFlag;
			};

			if ((!hasAnnots(this.assay.annotations)) && (!hasAnnots(this.extractions)) && (!hasAnnots(this.clonedTerms)))
			{
				let annot:AssayAnnotation =
				{
					'propURI': assn.propURI,
					'groupNest': assn.groupNest,
					'valueURI': ABSENCE_NOTAPPLICABLE,
					'valueLabel': 'not applicable',
				};
				newAnnotList.push(annot);
			}
		}
		return newAnnotList;
	}

	// removes a term (by URI or label, only one should be defined) and updates the visuals accordingly
	private deleteAnnotation(propURI:string, groupNest:string[], valueURI:string, valueLabel:string):void
	{
		if (!this.assay.annotations) return;
		let idx = -1;
		for (let n = 0; n < this.assay.annotations.length; n++)
		{
			let a = this.assay.annotations[n];
			if (samePropGroupNest(a.propURI, a.groupNest, propURI, groupNest))
				if ((valueURI && valueURI == a.valueURI) || (valueLabel && valueLabel == a.valueLabel)) {idx = n; break;}
		}
		if (idx < 0) return;

		// stash the annotation onto the undo stack
		this.stashUndo();

		this.assay.annotations.splice(idx, 1);

		this.edit.fillAssignments();
		for (let form of this.editingForms) form.fillAssignments();
		this.actionPredictAnnotations();
		if (this.keyword != null) this.keyword.changeAnnotations(this.assay.annotations);
		this.updateEditStatus();
	}

	// brings up a dialog for adding a text annotation
	private performLookupText(idx:number, initTxt:string, editing:boolean):void
	{
		this.lookupIndex = idx;
		initTxt = initTxt == null ? '' : initTxt;
		let dlg = new TextAnnotationDialog(this.assay, this.schema.assignments[this.lookupIndex],
				initTxt, editing,
				(txt:string) => this.appendLookupText(txt, editing ? initTxt : null),
				(annot:AssayAnnotation) => this.appendAnnotation(annot));
		dlg.show();
	}

	// set the index of the assignment and append the new text annotation
	private performAppendCustomText(idx:number, inTxt:string):void
	{
		this.lookupIndex = idx;
		this.appendLookupText(inTxt);
		this.predictAnnotations();
	}

	// a text input has arrived that should be a JSON-formatted file in the same format as used by downloads; if complain is true,
	// it will be loud if anything doesn't work; returns false if it wasn't actionable
	private loadJSON(str:string, complain:boolean):boolean
	{
		let obj:any = null;
		try
		{
			obj = JSON.parse(str);
		}
		catch (ex) {}

		if (!obj)
		{
			if (complain) alert('Content does not seem to be a JSON file.');
			return false;
		}

		let merge = new MergeAssay(this.assay, this.availableTemplates);
		if (!merge.apply(obj)) return false;

		TemplateManager.graftTemplates(this.assay.schemaURI, this.assay.schemaBranches, this.assay.schemaDuplication,
			(schema:SchemaSummary):void =>
			{
				TemplateManager.harmoniseAnnotations(schema, this.assay.annotations);

				if (merge.changedText) this.panelProtocol.setFullText(this.assay.text);
				if (merge.changedUniqueID) this.panelHeader.rebuildIdentifiers();
				if (merge.changedSchema) for (let n = 0; n < this.availableTemplates.length; n++)
					if (this.assay.schemaURI == this.availableTemplates[n].schemaURI) this.changeTemplate(n);

				this.edit.fillAssignments();
				for (let form of this.editingForms) form.fillAssignments();

				if (this.keyword != null) this.keyword.changeAnnotations(this.assay.annotations);
				this.lookupAnnotationLabels();
				this.predictAnnotations();
				this.setEditMode(true);
			});
	}

	// looks through the schema definition to match the label
	private findPropLabel(propURI:string):string
	{
		for (let assn of this.schema.assignments) if (assn.propURI == propURI) return assn.name;
		return '';
	}

	// match the assignment with the given property and group nest
	private findAssignment(propURI:string, groupNest:string[]):number
	{
		const assignments = this.schema.assignments;
		for (let n = 0; n < assignments.length; n++)
			if (samePropGroupNest(assignments[n].propURI, assignments[n].groupNest, propURI, groupNest)) return n;
		return -1;
	}

	// the server reported back from a deletion request: the UI needs to react accordingly
	private deletionResponse(success:boolean, status:string, holdingID:number):void
	{
		if (!success)
		{
			let msg = 'Response code: ' + status;
			if (status == 'nologin') msg = 'Login credentials invalid';
			else if (status == 'denied') msg = 'Access denied.';
			else if (status == 'nonexistent') msg = 'Assay not present (likely already deleted).';
			alert('Deletion failed. ' + msg);
			return;
		}

		if (status == 'applied')
		{
			alert('Assay deleted.');
			document.location.href = 'index.jsp';
		}
		else if (status == 'holding')
		{
			alert('Assay deletion request added to the holding bay.');
			document.location.href = 'assign.jsp?assayID=' + this.assay.assayID;
		}

		// (any other status is probably a bug; outcome is silent failure or success)
	}

	// user has asked to reinstate the assay to a certain historical state
	public restoreFromHistory(hist:AnnotationHistory):void
	{
		if (!this.editMode)
		{
			alert('Must be in edit mode to restore content.');
			return;
		}

		let newAnnots = this.assay.annotations.slice(0);
		let numAdded = 0, numRemoved = 0;

		let find = (annot:AssayAnnotation):number =>
		{
			for (let n = 0; n < newAnnots.length; n++)
			{
				let look = newAnnots[n];
				if (!samePropGroupNest(annot.propURI, annot.groupNest, look.propURI, look.groupNest)) continue;
				if (annot.valueURI)
					{if (annot.valueURI == look.valueURI) return n;}
				else
					{if (annot.valueLabel == look.valueLabel) return n;}
			}
			return -1;
		};

		// inverse-apply the history
		for (let annot of hist.annotsRemoved) if (find(annot) < 0) {newAnnots.push(annot); numAdded++;}
		for (let annot of hist.labelsRemoved) if (find(annot) < 0) {newAnnots.push(annot); numAdded++;}
		for (let annot of Vec.concat(hist.annotsAdded, hist.labelsAdded))
		{
			let idx = find(annot);
			if (idx >= 0) {newAnnots.splice(idx, 1); numRemoved++;}
		}

		if (numAdded == 0 && numRemoved == 0)
		{
			alert('The current annotations would not be affected by restoring this historical change.');
			return;
		}

		let msg = 'Restoring this change would result in adding ' + numAdded + ' annotation' + (numAdded == 1 ? '' : 's') +
				  ' and removing ' + numRemoved + '. ';
		msg += 'Confirm to alter the current annotations. You will still need to' +
			   ' submit the changes to apply them to the database.';
		if (!confirm(msg)) return;

		this.assay.annotations = newAnnots;
		this.edit.fillAssignments();
		for (let form of this.editingForms) form.fillAssignments();
		this.predictAnnotations();
		if (this.keyword != null) this.keyword.changeAnnotations(this.assay.annotations);
		this.updateEditStatus();
	}
	public restoreHistoryText(txt:string):void
	{
		if (!this.editMode)
		{
			alert('Must be in edit mode to restore content.');
			return;
		}
		this.panelProtocol.setFullText(txt);
		this.updateEditStatus();
	}

	// annotations have changed, so update various content accordingly
	private rebuiltAssignments():void
	{
		if (this.panelProtocol) this.panelProtocol.transliterateText();
		if (this.panelSimilar) this.panelSimilar.updateSearch();
		this.updateEditStatus();
	}

	// if the template indicates that identity types are used, need to grab them from the server
	private fetchIdentifiers():void
	{
		if (this.identifiers) return; // already got them

		callREST('REST/GetIdentifiers', {},
			(data:any) =>
			{
				this.identifiers = data;
				this.edit.updateIdentifiers();
				for (let form of this.editingForms) form.updateIdentifiers();
			});
	}

	// returns true if the assay is different from the original assay, hence justifying some encouragement to not lose changes
	// all the logic is conveniently implemented in the submission dialog, which is quite lightweight if not shown
	private isModified():boolean
	{
		let originalSchema = this.schema; // good enough for this
		return new SubmitDialog(this.assay, this.originalAssay, this.schema, originalSchema, []).isModified();
	}

	// as above, but includes text extractions as justification for enabling the submit dialog box
	private isSubmittable():boolean
	{
		if (!this.assay.assayID) return true; // a new assay is always eligible
		let originalSchema = this.schema; // good enough for this
		return new SubmitDialog(this.assay, this.originalAssay, this.schema, originalSchema, this.extractions).isModified();
	}

	// enable/disable buttons as appropriate
	private updateEditStatus():void
	{
		this.keyword.setEnabled(this.editMode);
		this.panelHeader.updateEditButton();
		if (this.btnTemplate) this.btnTemplate.prop('disabled', !this.editMode);
		if (this.btnUndo) this.btnUndo.prop('disabled', !this.canUndo());
		if (this.btnRedo) this.btnRedo.prop('disabled', !this.canRedo());
		if (this.btnQuickNotApplicable) this.btnQuickNotApplicable.prop('disabled', !this.editMode);
		this.panelHeader.btnIDType.prop('disabled', !this.editMode);

		this.panelProtocol.updateEditStatus();
		this.panelAnnotation.updateEditStatus();
		this.panelFooter.updateEditStatus();

		for (let form of this.editingForms) form.updateEditStatus();
	}

	// when the user decides to back/close/navigate, check to see if there are changes; if so, give them a chance to not do that
	private interceptClosePage():string
	{
		if (this.assay.annotations.length == 0 && !this.assay.text) return null; // who cares
		if (!this.isModified()) return null;
		return 'This assay has unsaved changes which will be lost. Continue?';
	}

	// full text has changed, so update the assay field and make sure the flags/button statuses are uptodate
	private changedFullText():void
	{
		if (this.assay.text != this.panelProtocol.getFullText())
		{
			this.assay.text = this.panelProtocol.getFullText();
			this.panelProtocol.setStatusPredict(this.assay.text && this.assay.text != this.lastTextSuggest);
			this.updateEditStatus();
			if (this.shouldAutoSuggestOnProtocolText) this.predictAnnotations();
		}
	}

	// similar to changedFullText, but triggered when focus is lost
	private enterFullText():void
	{
		let lastStash = this.getLastUndo();

		// snapshot before any changes -- otherwise changedFullText() will consume them.
		if (lastStash == null) this.enterFullTextAssayState = deepClone(this.originalAssay);
		else this.enterFullTextAssayState = deepClone(this.assay);
	}

	// similar to changedFullText, but triggered when focus is lost
	private exitFullText():void
	{
		let lastStash = this.enterFullTextAssayState;

		if (lastStash.text !== this.panelProtocol.getFullText()) this.undoStack.push(lastStash);

		this.updateEditStatus();
	}

	// returns true if any branches are insertable at the given group nest (where blank or null stands for root)
	private hasInsertableBranch(groupNest:string[]):boolean
	{
		if (groupNest == null) groupNest = [];
		let topGroup = groupNest.length == 0 ? null : groupNest[0];

		let already = new Set<string>();
		if (this.assay.schemaBranches) for (let branch of this.assay.schemaBranches)
		{
			let branchNest = branch.groupNest ? branch.groupNest : [];
			if (sameGroupNest(groupNest, branchNest)) already.add(branch.schemaURI);
		}

		for (let branch of this.branchTemplates) if (!already.has(branch.schemaURI))
		{
			if (!topGroup && branch.branchGroups.length == 0) return true;
			for (let group of branch.branchGroups) if (TemplateManager.comparePermissiveGroupURI(group, topGroup)) return true;
		}
		return false;
	}

	// offers a selection of new branches that the user may insert at the given group position
	private pickNewBranch(groupNest:string[]):void
	{
		if (groupNest == null) groupNest = [];

		// remove already-present branch templates: these can be duplicated if more than one is required
		let already = new Set<string>();
		if (this.assay.schemaBranches) for (let branch of this.assay.schemaBranches)
		{
			let branchNest = branch.groupNest ? branch.groupNest : [];
			if (Vec.equals(groupNest, branchNest)) already.add(branch.schemaURI);
		}
		let branchTemplates = this.branchTemplates.filter((branch) => !already.has(branch.schemaURI));

		// invoke the dialog, and handle affirmative results
		let dlg = new PickBranchDialog(groupNest, branchTemplates);
		dlg.callbackPicked = (template:TemplateSummary) =>
		{
			if (!this.assay.schemaBranches) this.assay.schemaBranches = [];
			this.assay.schemaBranches.push({'schemaURI': template.schemaURI, 'groupNest': groupNest});
			this.panelAnnotation.updateEditStatus();

			TemplateManager.graftTemplates(this.assay.schemaURI, this.assay.schemaBranches, this.assay.schemaDuplication,
				(schema:SchemaSummary) => this.replaceTemplate(schema));
		};
		dlg.show();
	}

	// clones the indicated group
	private duplicateGroup(groupNest:string[], cloneContent:boolean):void
	{
		let baseNest = groupNest.slice(0);
		baseNest[0] = TemplateManager.decomposeSuffixGroupURI(groupNest[0])[0];

		// adjust the template directives to ensure the multiplicity is one higher
		let got = false, newidx = 0;
		if (this.assay.schemaDuplication == null) this.assay.schemaDuplication = [];
		for (let dupl of this.assay.schemaDuplication) if (sameGroupNest(baseNest, dupl.groupNest))
		{
			newidx = ++dupl.multiplicity;
			got = true;
			break;
		}
		if (!got)
		{
			this.assay.schemaDuplication.push({'multiplicity': 2, 'groupNest': baseNest});
			newidx = 2;
		}

		// duplicate any branches dangling under the original group
		if (this.assay.schemaBranches == null) this.assay.schemaBranches = [];
		for (let n = 0, sz = this.assay.schemaBranches.length; n < sz; n++)
		{
			let branch = this.assay.schemaBranches[n];
			if (!descendentGroupNest(branch.groupNest, baseNest)) continue;

			let j = branch.groupNest.length - baseNest.length;
			let [baseURI, idx] = TemplateManager.decomposeSuffixGroupURI(branch.groupNest[j]);
			branch.groupNest[j] = TemplateManager.appendSuffixGroupURI(baseURI, idx); // normalise, in case it had no suffix

			branch = deepClone(branch);
			this.assay.schemaBranches.push(branch);
		}

		// duplicate any annotations that lived in the original group
		for (let n = 0, sz = this.assay.annotations.length; n < sz; n++)
		{
			let annot = this.assay.annotations[n];
			if (!descendentGroupNest(annot.groupNest, groupNest)) continue;

			let j = annot.groupNest.length - groupNest.length;
			let [baseURI, idx] = TemplateManager.decomposeSuffixGroupURI(annot.groupNest[j]);
			annot.groupNest[j] = TemplateManager.appendSuffixGroupURI(baseURI, idx); // normalise, in case it had no suffix

			if (cloneContent)
			{
				annot = deepClone(annot);
				this.assay.annotations.push(annot);
			}
		}

		this.assay.annotations = PageAssignment.cleanupAnnotations(this.assay.annotations);

		TemplateManager.graftTemplates(this.assay.schemaURI, this.assay.schemaBranches, this.assay.schemaDuplication,
			(schema:SchemaSummary) => this.replaceTemplate(schema));
	}

	// clears out all the annotations for the indicated group
	private eraseGroup(groupNest:string[]):void
	{
		let zap:number[] = [];
		for (let n = 0; n < this.assay.annotations.length; n++)
			if (descendentGroupNest(this.assay.annotations[n].groupNest, groupNest)) zap.push(n);
		if (zap.length == 0) return;

		if (!confirm('Clear this group by deleting ' + zap.length + ' annotation' + (zap.length == 1 ? '' : 's') + '?')) return;

		for (let n = zap.length - 1; n >= 0; n--) this.assay.annotations.splice(zap[n], 1);
		this.edit.fillAssignments();
		this.updateEditStatus();
	}

	// assuming that the group has been duplicated at least once, removes that section and shuffles down/removes duplication
	private deleteGroup(groupNest:string[]):void
	{
		let rawNest = groupNest.slice(0);
		let [rawGroup, dupidx] = TemplateManager.decomposeSuffixGroupURI(groupNest[0]);
		rawNest[0] = rawGroup;
		let dupl:SchemaDuplication = null;
		if (this.assay.schemaDuplication) for (let look of this.assay.schemaDuplication) if (sameGroupNest(rawNest, look.groupNest))
		{
			dupl = look;
			break;
		}
		if (!dupl) return;

		let zap:number[] = [];
		for (let n = 0; n < this.assay.annotations.length; n++)
			if (descendentGroupNest(this.assay.annotations[n].groupNest, groupNest)) zap.push(n);

		let msg = 'Delete copy of group';
		if (zap.length > 0) msg += ' (and ' + zap.length + ' annotation' + (zap.length == 1 ? '' : 's') + ')';
		msg += '?';
		if (!confirm(msg)) return;

		// perform the surgery
		for (let n = zap.length - 1; n >= 0; n--) this.assay.annotations.splice(zap[n], 1);
		for (let n = Vec.arrayLength(this.assay.schemaBranches) - 1; n >= 0; n--)
			if (descendentGroupNest(this.assay.schemaBranches[n].groupNest, groupNest))
				this.assay.schemaBranches.splice(n, 1);
		for (let i = dupidx + 1; i <= dupl.multiplicity; i++)
		{
			groupNest[0] = TemplateManager.appendSuffixGroupURI(rawGroup, i);
			for (let annot of this.assay.annotations) if (descendentGroupNest(annot.groupNest, groupNest))
			{
				let n = annot.groupNest.length - groupNest.length;
				annot.groupNest[n] = TemplateManager.appendSuffixGroupURI(annot.groupNest[n], (i - 1));
			}
			if (this.assay.schemaBranches) for (let branch of this.assay.schemaBranches)
				if (descendentGroupNest(branch.groupNest, groupNest))
			{
				let n = branch.groupNest.length - groupNest.length;
				branch.groupNest[n] = TemplateManager.appendSuffixGroupURI(branch.groupNest[n], (i - 1));
			}
		}
		dupl.multiplicity--;

		TemplateManager.graftTemplates(this.assay.schemaURI, this.assay.schemaBranches, this.assay.schemaDuplication,
			(schema:SchemaSummary) => this.replaceTemplate(schema));
	}

	// swaps the content within a particular group, effecting an upward/downward move
	private moveGroup(groupNest:string[], dir:number):void
	{
		let rawNest = groupNest.slice(0);
		let [rawGroup, dupidx] = TemplateManager.decomposeSuffixGroupURI(groupNest[0]);
		rawNest[0] = rawGroup;
		let dupl:SchemaDuplication = null;
		if (this.assay.schemaDuplication) for (let look of this.assay.schemaDuplication) if (sameGroupNest(rawNest, look.groupNest))
		{
			dupl = look;
			break;
		}
		if (!dupl) return;

		if (dupidx + dir < 1 || dupidx + dir > dupl.multiplicity) return;

		// swap annotations corresponding to either of the group nests
		let groupNest1 = groupNest.slice(0), groupNest2 = groupNest.slice(0);
		groupNest1[0] = TemplateManager.appendSuffixGroupURI(rawGroup, dupidx);
		groupNest2[0] = TemplateManager.appendSuffixGroupURI(rawGroup, dupidx + dir);
		for (let annot of this.assay.annotations)
		{
			let n = annot.groupNest.length - groupNest.length;
			if (descendentGroupNest(annot.groupNest, groupNest1))
			{
				annot.groupNest = annot.groupNest.slice(0);
				annot.groupNest[n] = groupNest2[0];
			}
			else if (descendentGroupNest(annot.groupNest, groupNest2))
			{
				annot.groupNest = annot.groupNest.slice(0);
				annot.groupNest[n] = groupNest1[0];
			}
		}
		let rebranched = false;
		if (this.assay.schemaBranches) for (let branch of this.assay.schemaBranches)
		{
			let n = branch.groupNest.length - groupNest.length;
			if (descendentGroupNest(branch.groupNest, groupNest1)) {branch.groupNest[n] = groupNest2[0]; rebranched = true;}
			else if (descendentGroupNest(branch.groupNest, groupNest2)) {branch.groupNest[n] = groupNest1[0]; rebranched = true;}
		}

		if (rebranched)
		{
			TemplateManager.graftTemplates(this.assay.schemaURI, this.assay.schemaBranches, this.assay.schemaDuplication,
				(schema:SchemaSummary) => this.replaceTemplate(schema));
		}
		else this.redisplayAnnotations();

		this.updateEditStatus();
	}

	// offers a list of existing branches to possibly remove
	private pickRemoveBranch():void
	{
		if (Vec.arrayLength(this.assay.schemaBranches) == 0) return;

		let dlg = new RemoveBranchDialog(this.assay.schemaBranches);
		dlg.callbackPicked = (idxList:number[]) =>
		{
			Vec.sort(idxList);
			for (let n = idxList.length - 1; n >= 0; n--) this.assay.schemaBranches.splice(idxList[n], 1);

			this.panelAnnotation.updateEditStatus();

			TemplateManager.graftTemplates(this.assay.schemaURI, this.assay.schemaBranches, this.assay.schemaDuplication,
				(schema:SchemaSummary) => this.replaceTemplate(schema));
		};
		dlg.show();
	}

	// after some content importing operations it is possible for URI-based values to come in with no label (e.g. pasting); these
	// need to be scanned and updated
	private lookupAnnotationLabels():void
	{
		let recheck:AssayAnnotation[] = [];
		for (let annot of this.assay.annotations) if (annot.valueURI && !annot.valueLabel) recheck.push(annot);

		let assaySchemaURI = this.assay.schemaURI, schemaBranches = this.assay.schemaBranches, schemaDupl = this.assay.schemaDuplication;

		let lookupNext = ():void =>
		{
			if (recheck.length == 0) return;
			let annot = recheck.shift();

			let [_, groupNest] = TemplateManager.relativeBranch(annot.propURI, annot.groupNest, assaySchemaURI, schemaBranches, schemaDupl);
			let opt:PopoverOptions = {'schemaURI': assaySchemaURI, 'propURI': annot.propURI,
									'groupNest': groupNest, 'valueURI': annot.valueURI};
			(async () =>
			{
				let label = await Popover.fetchCachedVocabLabel(opt);
				annot.valueLabel = label;
				this.edit.fillAssignments();
				for (let form of this.editingForms) form.fillAssignments();

				lookupNext();
			})();
		};
		lookupNext();
	}

	// remove the apply-clone button if nothing applies
	private updateCloneApply():void
	{
		if (this.btnApplyCloneText && !this.clonedText)
		{
			this.btnApplyCloneText.remove();
			this.btnApplyCloneText = null;
		}
		if (this.btnApplyCloneAnnots && this.clonedTerms.length == 0)
		{
			this.btnApplyCloneAnnots.remove();
			this.btnApplyCloneAnnots = null;
		}
	}

	// make all of the cloned entries into asserted annotations
	private applyClonedText():void
	{
		this.panelProtocol.setFullText(this.clonedText);
		this.clonedText = null;
		this.predictAnnotations();
		this.updateCloneApply();
		this.updateEditStatus();
	}
	private applyAllCloned():void
	{
		let toAdd = this.clonedTerms.slice(0); // need to duplicate because the original list will be auto-pruned
		this.appendMultipleAnnotations(toAdd);
	}

	// redefine the information about axioms: justifications are background information about why an implied singleton came
	// to be that way; violations are annotations that are contradicted by axioms; additional annotations are those which were
	// added outside of the original tree
	private setAxiomEffects(justifications:AssaySuggestion[], violations:AssaySuggestion[], additional:AssaySuggestion[]):void
	{
		// sort in arbitrary order and then compare: quick-out if no change
		justifications.sort((v1, v2) => JSON.stringify(v1).localeCompare(JSON.stringify(v2)));
		violations.sort((v1, v2) => JSON.stringify(v1).localeCompare(JSON.stringify(v2)));
		additional.sort((v1, v2) => JSON.stringify(v1).localeCompare(JSON.stringify(v2)));
		if (JSON.stringify(this.axiomJustifications) == JSON.stringify(justifications) &&
			JSON.stringify(this.axiomViolations) == JSON.stringify(violations) &&
			JSON.stringify(this.axiomAdditional) == JSON.stringify(additional)) return;

		this.axiomJustifications = justifications;
		this.axiomViolations = violations;
		this.axiomAdditional = additional;
		this.edit.fillAssignments();
		for (let form of this.editingForms) form.fillAssignments();
	}

	// returns all of the suggestions that are "implied" by axioms, i.e. reduced to a single case
	private axiomImpliedSuggestions():AssaySuggestion[]
	{
		let implied:AssaySuggestion[] = [];

		let keyAssn:Record<string, SchemaAssignment> = {};
		for (let assn of this.schema.assignments) keyAssn[keyPropGroup(assn.propURI, assn.groupNest)] = assn;
		for (let annot of this.assay.annotations) keyAssn[keyPropGroup(annot.propURI, annot.groupNest)] = null;
		let mapSugg:Record<string, AssaySuggestion[]> = {};
		for (let sugg of this.suggestions) if (sugg.axiomFiltered)
		{
			let key = keyPropGroup(sugg.propURI, sugg.groupNest);
			if (!keyAssn[key]) continue; // has an annotation
			let suggList = mapSugg[key];
			if (suggList) suggList.push(sugg); else mapSugg[key] = [sugg];
		}
		for (let suggList of Object.values(mapSugg)) if (suggList.length == 1) implied.push(suggList[0]);

		// also feed in the "additional" terms (i.e. those which were added to the tree, rather than reduced)
		implied = implied.concat(this.axiomAdditional as AssaySuggestion[]);

		return implied;
	}

	// bring up the "quick axioms" dialog, which shows the implied axioms and allows them to be selected in bulk
	private openQuickAxioms():void
	{
		let implied = this.axiomImpliedSuggestions();
		if (implied.length == 0)
		{
			alert('There are currently no missing annotations that are implied by axioms.');
			return;
		}

		new QuickAxiomDialog(this.schema, implied, (subset:AssaySuggestion[]):void => this.appendMultipleAnnotations(subset)).show();
	}

	// ------------ methods used by SelectAssayChangesDialog ------------

	// apply the holding bay content to the current assay
	public applySelectedAssayChanges(hbayChanges:HoldingBayAssay[]):void
	{
		this.selectedHbayChanges = [];
		for (let hbayDelta of hbayChanges) this.selectedHbayChanges.push(hbayDelta);

		if (this.selectedHbayChanges.length == 0) return;

		this.stopPrediction();

		// schema/branches/duplication: pick out the most recent one, and apply that
		let modified = false;
		for (let hbayDelta of hbayChanges)
		{
			if (hbayDelta.schemaURI) {this.assay.schemaURI = hbayDelta.schemaURI; modified = true;}
			if (hbayDelta.schemaBranches) {this.assay.schemaBranches = hbayDelta.schemaBranches; modified = true;}
			if (hbayDelta.schemaDuplication) {this.assay.schemaDuplication = hbayDelta.schemaDuplication; modified = true;}
		}
		if (modified)
		{
			TemplateManager.graftTemplates(this.assay.schemaURI, this.assay.schemaBranches, this.assay.schemaDuplication,
				(schema:SchemaSummary) => this.replaceTemplate(schema));
		}

		// iterate over selected holding bay changes
		// make sure that we apply changes in the same way as on the server
		let newText:string = null;
		for (let hbayDelta of hbayChanges)
		{
			for (let annot of hbayDelta.removed) this.deleteAnnotation(annot.propURI, annot.groupNest, annot.valueURI, annot.valueLabel);
			for (let annot of hbayDelta.added) this.actionAppendAnnotation(annot);

			if (hbayDelta.text) newText = hbayDelta.text;
		}

		if (newText != null)
		{
			this.panelProtocol.setFullText(newText);
			this.changedFullText();
		}

		// clear and rebuild local cache of suggestions
		this.resetDictSuggestions();

		// repopulate notice at top of page
		this.defineNotices();

		// repopulate text summarizing whether changes from holding bay are incorporated into the assay shown here
		this.panelFooter.rebuildHistory();

		// switch to edit mode
		this.enterEditMode();
	}
}

/* EOF */ }

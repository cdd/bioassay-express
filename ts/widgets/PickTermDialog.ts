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

// like SchemaTreeNode, but with different optional members
interface TermNode
{
	depth?:number;
	parent?:number;
	uri:string;
	name:string;
	abbrev?:string;
	inSchema?:boolean;
	provisional?:ProvTermSummary;
	childCount?:number;
	schemaCount?:number;
	inModel?:boolean;
	altLabels?:string[];
	externalURLs?:string[];
	isContainer?:boolean;
	axiomApplicable?:boolean;
}

// settings for PickTermDialog, defaultSettings are defined below
export interface PickTermSettings
{
	schema:SchemaSummary;
	schemaBranches?:SchemaBranch[];
	schemaDuplication?:SchemaDuplication[];
	multi?:boolean;
	canRequestProvisionals?:boolean;
	searchTxt?:string;
	annotations?:AssayAnnotation[];
	uriPatternMaps?:URIPatternMaps;
}

/*
	Picking terms: for a given assignment & template, brings up either a list or
	tree view that allows selection of terms. Wraps the corresponding
	PickList/BigTree dialogs with all of the semantic-specific functionality.
 */

export class PickTermDialog implements SelectionDialogDelegate
{
	private defaultSettings:PickTermSettings =
	{
		'schema': null,
		'schemaBranches': null,
		'schemaDuplication': null,
		'multi': true,
		'canRequestProvisionals': false,
		'searchTxt': null,
		'annotations': null,
		'uriPatternMaps': null,
	};

	// one of the two of these will be defined
	private picklist:SelectionDialogList;
	private bigtree:SelectionDialogTree;
	private settings:PickTermSettings;

	private treeData:SchemaTreeNode[] = null;

	private alreadySelected = new Set<string>();
	private alreadyNamed = new Set<string>();
	public revealInTree = new Set<string>(); // optional terms to ensure are expanded out in the tree
	public predictionScores:Record<string, number> = {}; // uri-to-score (where 0=bad, 1=good)
	private lastTermLabel = '';
	private delayedScrollToURI:string = null;
	public removeAnnotationFunc:(assay:AssayAnnotation) => void = null;

	constructor(private assn:SchemaAssignment, settings:PickTermSettings, private selfunc:(assay:AssayAnnotation) => void)
	{
		this.settings = {...this.defaultSettings, ...settings};
		if (this.settings.annotations)
		{
			for (let annot of this.settings.annotations) if (samePropGroupNest(annot.propURI, annot.groupNest,
																			this.assn.propURI, this.assn.groupNest))
			{
				if (annot.valueURI) this.alreadySelected.add(annot.valueURI);
				if (annot.valueLabel) this.alreadyNamed.add(annot.valueLabel);
			}
		}
	}

	public showList(titleHTML:string):void
	{
		this.picklist = new SelectionDialogList(titleHTML, this, this.settings.canRequestProvisionals, this.settings.searchTxt);
		this.picklist.show();

		if (this.assn.suggestions == SuggestionType.Full || this.assn.suggestions == SuggestionType.Disabled)
		{
			let params =
			{
				'schemaURI': this.settings.schema.schemaURI,
				'schemaBranches': this.settings.schemaBranches,
				'schemaDuplication': this.settings.schemaDuplication,
				'propURI': this.assn.propURI,
				'groupNest': this.assn.groupNest,
				'annotations': this.settings.annotations
			};
			callREST('REST/GetPropertyList', params,
				(data:any) =>
				{
					if (data.list.length == 0)
						this.populateNothing(this.picklist);
					else
						this.picklist.populate(data.list);
				});
		}
		else // it's one of the various different literal types
		{
			let params =
			{
				'schemaURI': this.settings.schema.schemaURI,
				'schemaBranches': this.settings.schemaBranches,
				'schemaDuplication': this.settings.schemaDuplication,
				'locator': this.assn.locator
			};
			callREST('REST/GetLiteralValues', params,
				(values:Record<string, number>) =>
				{
					let literals = Object.keys(values); // result is {literal:frequency}, latter unimportant
					if (literals.length > 0)
					{
						if (this.assn.suggestions == SuggestionType.Integer || this.assn.suggestions == SuggestionType.Number)
							literals.sort((v1:string, v2:string):number => parseFloat(v1) - parseFloat(v2));
						else
							literals.sort();

						let list:any[] = [];
						for (let lit of literals) list.push({'name': lit});
						this.picklist.populate(list);
					}
					else this.picklist.content.html('There are no previous values to select from.');
				});
		}
	}

	public showTree(titleHTML:string):void
	{
		this.bigtree = new SelectionDialogTree(titleHTML, this, this.settings.canRequestProvisionals, this.settings.searchTxt);
		this.bigtree.show();
		this.callGetPropertyTree();
	}

	// scroll so that an item is visible onscreen
	public scrollToItem(uri:string):void
	{
		this.delayedScrollToURI = uri;
	}

	// ------------ private methods ------------

	private callGetPropertyTree():void
	{
		let params =
		{
			'schemaURI': this.settings.schema.schemaURI,
			'schemaBranches': this.settings.schemaBranches,
			'schemaDuplication': this.settings.schemaDuplication,
			'propURI': this.assn.propURI,
			'groupNest': this.assn.groupNest,
			'annotations': this.settings.annotations
		};
		callREST('REST/GetPropertyTree', params,
			(data:any) =>
			{
				this.treeData = unlaminateTree(data.tree);
				if (this.treeData.length == 0)
				{
					this.populateNothing(this.bigtree);
					return;
				}

				// show selected terms and terms restricted by axioms
				let reveal:number[] = [];
				for (let n = 0; n < this.treeData.length; n++)
				{
					const uri = this.treeData[n].uri;
					if (this.alreadySelected.has(uri) || this.revealInTree.has(uri)) reveal.push(n);
				}

				this.revealAxiomRestricted(reveal);

				this.bigtree.populate(this.treeData, reveal);
				this.bigtree.scrollToURI(this.delayedScrollToURI);
			});
	}

	// returns true if it's appropriate to let the user ask for a new provisional term
	private canRequestProvisionalTerm():boolean
	{
		return this.settings.canRequestProvisionals && Authentication.canRequestProvisionalTerm();
	}

	// returns true if the user can edit a specific provisional term: requires either admin access or a user match
	private canEditProvisionalTerm(provisional:ProvTermSummary):boolean
	{
		if (!provisional || !this.settings.canRequestProvisionals || !Authentication.canRequestProvisionalTerm()) return false;
		if (Authentication.canAccessOntoloBridge()) return true; // assuming that ontolobridge access == good enough to edit anything
		let session = Authentication.currentSession();
		return session && session.curatorID == provisional.proposerID;
	}

	// called when the user has opened a dialog that has no semantic web terms
	private populateNothing(seldlg:SelectionDialog):void
	{
		// NOTE: it would be better to guard against this happening in the first place, but this is better than just an empty nothing
		seldlg.content.html('There are no semantic web annotations for this assignment.');
	}

	// selected a new term to add
	private selectTerm(data:TermNode, closeDialog:boolean):void
	{
		let annot:AssayAnnotation =
		{
			'propURI': this.assn.propURI,
			'propLabel': this.assn.name,
			'propDescr': this.assn.descr,
			'valueURI': data.uri,
			'valueLabel': data.name,
			'groupNest': this.assn.groupNest,
			'groupLabel': this.assn.groupLabel,
			'altLabels': data.altLabels,
			'externalURLs': data.externalURLs
		};
		this.selfunc(annot);

		if (closeDialog)
		{
			if (this.picklist) this.picklist.hide();
			if (this.bigtree) this.bigtree.hide();
		}
	}

	// selected a new term to add
	private unselectTerm(data:TermNode, closeDialog:boolean):void
	{
		let annot:AssayAnnotation =
		{
			'propURI': this.assn.propURI,
			'propLabel': this.assn.name,
			'propDescr': this.assn.descr,
			'valueURI': data.uri,
			'valueLabel': data.name,
			'groupNest': this.assn.groupNest,
			'groupLabel': this.assn.groupLabel,
			'altLabels': data.altLabels,
			'externalURLs': data.externalURLs
		};

		if (this.removeAnnotationFunc != null)
		{
			this.removeAnnotationFunc(annot);
		}

		if (closeDialog)
		{
			if (this.picklist) this.picklist.hide();
			if (this.bigtree) this.bigtree.hide();
		}
	}

	// user has asked for a new term request at a given point, so bring up an opportunity to enter a label, and then proceed to
	// the full request-term dialog
	private initiateTermRequest(blk:JQuery, parentURI:string):void
	{
		let parent = blk.closest('div');
		let divLine = $('<div/>').appendTo(parent);
		divLine.css('white-space', 'nowrap');

		let spacer = $('<span/>').appendTo(divLine);
		spacer.css({'display': 'inline-block', 'width': '1.5em', 'text-align': 'left'});
		spacer.append('<span class="glyphicon glyphicon-play" style="width: 0.9em; height: 1.2em;">');

		let input = $('<input type="text" size="40" spellcheck="false"/>').appendTo(divLine);
		input.attr('placeholder', 'enter label for new term');
		input.css({'font': 'inherit'});
		input.val(this.lastTermLabel);

		divLine.append(' ');
		let btnRequest = $('<button class="btn btn-xs btn-action"/>').appendTo(divLine);
		$('<span class="glyphicon glyphicon-bullhorn" style="width: 0.9em; height: 1.2em;"/>').appendTo(btnRequest);

		input.focus();
		input.focusout(() => setTimeout(() => divLine.remove(), 200));

		let onChange = ():void =>
		{
			this.lastTermLabel = input.val().toString().trim();
			btnRequest.prop('disabled', !this.lastTermLabel);
		};
		let onAccept = ():void =>
		{
			if (this.lastTermLabel)
				this.escalateTermRequest(parentURI, this.lastTermLabel);
			else
				divLine.remove();
		};

		input.change(onChange);
		input.keyup((event:JQueryKeyEventObject) =>
		{
			onChange();
			if (event.which == KeyCode.Escape) divLine.remove();
			else if (event.which == KeyCode.Enter) onAccept();
		});

		onChange();
		btnRequest.click(onAccept);
	}

	// have a label and a parent, and the user wants to move it up to the full request
	private escalateTermRequest(parentURI:string, label:string):void
	{
		if (this.picklist) this.picklist.hide();
		if (this.bigtree) this.bigtree.hide();

		new ProvisionalTermDialog(this.assn, this.treeData, parentURI, label, this.selfunc).show();
	}

	// special case: if any of the terms are marked as not axiom-applicable, then as a courtesy, should make sure all of the
	// roots for the are-applicable axioms get opened
	private revealAxiomRestricted(reveal:number[]):void
	{
		let any = false;
		for (let node of this.treeData) if (node.axiomApplicable == false) {any = true; break;}
		if (!any) return;

		for (let n = 0; n < this.treeData.length; n++)
		{
			let node = this.treeData[n];
			if (node.axiomApplicable != true) continue;
			let i = n;
			while (node.parent >= 0)
			{
				let parent = this.treeData[node.parent];
				if (parent.axiomApplicable != true) break;
				[node, i] = [parent, node.parent];
			}
			if (reveal.indexOf(i) < 0) reveal.push(i);
		}
	}

	// ------------ SelectionDialogDelegate methods ------------

	// creates an individual element for a tree node in the lookup dialog
	public createLookupNode(data:TermNode, parent:JQuery, modfunc:() => void, matchCount:number = -1):JQuery
	{
		let {inSchema, schemaCount, childCount, inModel, uri, name, abbrev, isContainer = false} = data;
		let provisional = data.provisional; // defined only if is provisional
		let axiomExcluded = data.axiomApplicable == false; // note that null implies that it's not excluded
		let isAnnotated = uri ? this.alreadySelected.has(uri) : this.alreadyNamed.has(name);
		let pred = this.predictionScores[uri]; // can be null
		let text = name ? name : abbrev;

		// the line content
		let main = $('<span/>');

		let css:Record<string, string> = {};
		if (axiomExcluded) {}
		else if (inSchema && !isContainer && !provisional) css['font-weight'] = 'bold';
		else if (inModel) {}
		else css['font-style'] = 'italic';
		if (isAnnotated)
		{
			css['color'] = 'white';
			css['padding'] = '0.3em';
			css['background-color'] = 'black';
		}
		else if (axiomExcluded) css['color'] = '#C0C0C0';
		else if (provisional && !inModel && schemaCount == 0) css['color'] = '#808080';
		else css['color'] = 'black';
		if (!isAnnotated && pred > 0) css['background-color'] = `rgba(224,224,255,${Math.min(0.2 + 0.8 * pred, 1)})`;
		if (provisional && provisional.role == ProvisionalTermRole.Deprecated) css['text-decoration'] = 'line-through';

		let span = $('<span class="right" data-placement="right" data-toggle="tooltip" data-html="true"/>').appendTo(main);
		let blk = $('<span/>').appendTo(span).append(text).css(css);

		const {uriPatternMaps} = this.settings;
		if (abbrev && uriPatternMaps && uriPatternMaps.hasPatterns)
		{
			let mappingContainer = $('<span/>').appendTo(main);
			for (let match of uriPatternMaps.matches(abbrev))
			{
				const {map, payload} = match;
				let href = URIPatternMaps.renderLink(match, {'externalPattern': '$1'});
				let span = $('<span/>').appendTo(mappingContainer).css({'margin-left': '0.5em'});
				span.append('[').append(href).append(']');

				Popover.hover(domLegacy(href), null, escapeHTML(map.label + ': ' + payload));
			}
		}

		if (childCount >= 1)
		{
			if (matchCount == -1 || matchCount == childCount)
				span.append(` (${childCount})`);
			else
				span.append(` (${matchCount} of ${childCount})`);
		}

		if (uri != BRANCH_GROUP)
		{
			// the help text popover
			let [title, content] = Popover.popoverOntologyTerm(uri, name, null, data.altLabels, data.externalURLs);

			let info:string[] = [];
			if (isAnnotated) info.push('using annotation');
			if (inSchema) info.push('in schema'); else info.push('not in schema');
			if (inModel) info.push('has model'); else info.push('not modelled');
			if (provisional)
			{
				let tag = 'provisional';
				if (provisional.role) tag += ': ' + provisional.role;
				info.push(tag);
			}
			if (isContainer) info.push('is container term');
			if (axiomExcluded) info.push('excluded by axioms');
			content += '<p style="color: #808080;">(' + info.join(', ') + ')</p>';

			let [schemaURI, groupNest] = TemplateManager.relativeBranch(this.assn.propURI, this.assn.groupNest,
																		this.settings.schema.schemaURI,
																		this.settings.schemaBranches, this.settings.schemaDuplication);
			Popover.click(domLegacy(span), title, content,
							{'schemaURI': schemaURI, 'propURI': this.assn.propURI, 'groupNest': groupNest, 'valueURI': uri});

			let buttons:JQuery[] = [];
			if (!isAnnotated && !isContainer)
			{
				span.css('cursor', 'pointer');
				span.addClass('hoverUnderline');
				span.one('click', () => this.selectTerm(data, true));
				span.dblclick(() => {});
			}

			if (!isContainer)
			{
				main.append(' ');

				let btnAdd = $('<button class="btn btn-xs btn-action" data-placement="right" data-toggle="tooltip" data-html="true"/>').appendTo(main);
				$('<span class="glyphicon glyphicon-asterisk" style="width: 0.9em; height: 1.2em;"/>').appendTo(btnAdd);

				Popover.hover(domLegacy(btnAdd), null, `Click to ${isAnnotated ? 'remove' : 'add'} without closing the dialog.`);
				btnAdd.click(() =>
				{
					if (isAnnotated)
					{
						this.unselectTerm(data, false);
						if (uri) this.alreadySelected.delete(uri);
						if (name) this.alreadyNamed.delete(name);
					}
					else
					{
						this.selectTerm(data, false);
						if (uri) this.alreadySelected.add(uri);
						if (name) this.alreadyNamed.add(name);
					}
					modfunc();
				});
				buttons.push(btnAdd);
			}
			if (this.canEditProvisionalTerm(provisional))
			{
				main.append(' ');
				let btnEdit = $('<button class="btn btn-xs btn-normal" data-placement="right" data-toggle="tooltip" data-html="true"/>').appendTo(main);
				$('<span class="glyphicon glyphicon-edit" style="width: 0.9em; height: 1.2em;"/>').appendTo(btnEdit);
				Popover.hover(domLegacy(btnEdit), null, 'Click to edit provisional term details.');

				btnEdit.click(() =>
				{
					// grab all the provisional terms to find out the one we want
					callREST('REST/GetTermRequests', {},
						(data:any) =>
						{
							for (let term of data.list as ProvisionalTerm[]) if (term.provisionalID == provisional.provisionalID)
							{
								if (this.picklist) this.picklist.hide();
								if (this.bigtree) this.bigtree.hide();
								new EditProvisionalDialog(term, () => {}).show();
								break;
							}
						});
				});
				buttons.push(btnEdit);
			}
			if (this.settings.multi && this.canRequestProvisionalTerm() && this.bigtree)
			{
				main.append(' ');
				let btnProv = $('<button class="btn btn-xs btn-normal" data-placement="right" data-toggle="tooltip" data-html="true"/>').appendTo(main);
				$('<span class="glyphicon glyphicon-hand-down" style="width: 0.9em; height: 1.2em;"/>').appendTo(btnProv);
				Popover.hover(domLegacy(btnProv), null, 'Click to request provisional term under this branch.');

				btnProv.click(() => this.initiateTermRequest(blk, uri));
				buttons.push(btnProv);
			}

			if (buttons.length > 0)
			{
				for (let btn of buttons) btn.css('visibility', 'hidden');
				parent.mouseenter(() => {for (let btn of buttons) btn.css('visibility', 'visible');});
				parent.mouseleave(() => {for (let btn of buttons) btn.css('visibility', 'hidden');});
			}
		}

		return main;
	}

	public isLookupNodeSelected(data:any):boolean
	{
		if (data.uri)
			return this.alreadySelected.has(data.uri);
		else
			return this.alreadyNamed.has(data.name);
	}

	// used as a callback to indicate whether user-entered text matches a given tree node (by label or any of the alternate labels)
	public matchLookupNode(data:any, txt:string):boolean
	{
		if (!txt) return false; // blank matches nothing
		txt = txt.toLowerCase();

 		if (data.name.toLowerCase().includes(txt)) return true;
		if (data.altLabels != null) for (const label of data.altLabels)
		{
			if (label.toLowerCase().includes(txt)) return true;
		}
		return false;
	}
}

/* EOF */ }

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
	Supporting functionality for the bulk remapping page.
*/

class PageBulkTranslation
{
	public assn:SchemaAssignment;
	public fromURI:string;
	public fromLabel:string;
	public toURI?:string;
	public toLabel?:string;
}

export class PageBulkMap
{
	private mainDiv:JQuery;
	private listSpanAssn:JQuery[] = [];
	private listDivValues:JQuery[] = [];

	private rosterAssays:number[] = [];
	private assayDef:Record<number, AssayDefinition> = {};
	private current = -1; // selected assignment (if any)

	private remappings:Record<string, PageBulkTranslation> = {};
	private proposed:PageBulkTranslation[] = [];

	constructor(private assayIDList:number[], private query:string, private schema:SchemaSummary)
	{
	}

	// create the baseline objects so that the user can get things started
	public build():void
	{
		let content = $('#content');
		content.empty();

		this.mainDiv = $('<div/>').appendTo(content);

		this.acquireAssays();
	}

	// ------------ private methods ------------

	// make sure the assays are all defined, or get it one step closer
	private acquireAssays():void
	{
		this.mainDiv.text('Loading assays...');

		if (this.assayIDList == null && this.query != null)
		{
			let params = {'query': this.query};
			callREST('REST/ListQueryAssays', params,
				(data:any) =>
				{
					this.assayIDList = data.assayIDList;
					this.acquireAssays();
				},
				() => this.mainDiv.text('Fetching query assays failed.'));
			return;
		}

		if (this.assayIDList != null)
		{
			this.rosterAssays = this.assayIDList.slice(0);
			this.loadNextAssay();
		}
	}
	private loadNextAssay():void
	{
		if (this.rosterAssays.length == 0)
		{
			this.buildHierarchy();
			return;
		}

		let assayID = this.rosterAssays.shift();
		let params = {'assayID': assayID, 'countCompounds': false};
		callREST('REST/GetAssay', params,
			(assay:AssayDefinition) =>
			{
				this.assayDef[assayID] = assay;
				let total = this.assayIDList.length, num = total - this.rosterAssays.length;
				this.mainDiv.text('Loading assays (' + num + ' of ' + total + ')');
				this.loadNextAssay();
			});
	}

	// given that the assays are now loaded, pull everything out and arrange into a hierarchy
	private buildHierarchy():void
	{
		this.mainDiv.empty();

		let divInfo = $('<div/>').appendTo(this.mainDiv);
		divInfo.html(`Assays selected: <b>${this.assayIDList.length}</b>`);
		let divSchema = $('<div/>').appendTo(this.mainDiv);
		divSchema.html('Schema: <b><u>' + escapeHTML(this.schema.name) + '</u></b>');

		let schema = this.schema;
		let hier = new SchemaHierarchy(schema);
		let divTree:JQuery[] = [], toggleTree:JQuery[] = [], parentIdx:number[] = [];

		let renderAssn = (pidx:number, depth:number, assn:SchemaHierarchyAssignment):void =>
		{
			const idx = assn.assnidx;

			let div = $('<div/>').appendTo(this.mainDiv);
			div.css('padding-left', (depth + 0.3) + 'em');

			let span = $('<span/>').appendTo(div);
			span.css({'padding': '0.25em 0.5em 0.25em 0.5em', 'font-weight': 'bold', 'cursor': 'pointer'});
			span.hover(() => {if (idx != this.current) {span.css('text-decoration', 'underline'); span.css('color', Theme.STRONG_HTML);}},
					   () => {span.css('text-decoration', 'none'); span.css('color', idx == this.current ? 'white' : Theme.NORMAL_HTML);});
			span.text(assn.name);

			let tip = '<p><i>' + collapsePrefix(assn.propURI) + '</i></p>';
			if (assn.descr) tip += '<p>' + escapeHTML(assn.descr) + '</p>';
			Popover.hover(domLegacy(span), null, tip);

			span.click(() => this.pickedAssignment(idx));

			this.listSpanAssn.push(span);

			let vdiv = $('<div/>').appendTo(this.mainDiv);
			vdiv.css('margin-left', (0.5 * depth) + 'em');
			this.listDivValues.push(vdiv);

			if (pidx >= 0)
			{
				divTree.push(div);
				toggleTree.push(null);
				parentIdx.push(pidx);
			}
		};
		let renderGroup = (pidx:number, depth:number, group:SchemaHierarchyGroup):void =>
		{
			if (group.parent != null)
			{
				let div = $('<div/>').appendTo(this.mainDiv);

				let blk = $('<span/>').appendTo(div);
				blk.css('padding-left', depth + 'em');

				let toggle = $('<span/>').appendTo(blk);
				blk.append('&nbsp;');

				let font = $('<font style="text-decoration: underline; top: 0.2em; position: relative;"/>').appendTo(blk);
				font.text(group.name);

				if (group.descr)
				{
					let tip = group.descr;
					if (group.groupURI)
					{
						tip = 'Abbrev: <i>' + collapsePrefix(group.groupURI) + '</i><br>' + tip;
					}
					Popover.hover(domLegacy(font), null, tip);
				}

				// add the necessary information to create the toggle open/closed tree
				divTree.push(div);
				toggleTree.push(toggle);
				parentIdx.push(pidx);
			}

			let subpidx = divTree.length - 1;
			for (let assn of group.assignments) renderAssn(subpidx, depth + 1, assn);
			for (let subgrp of group.subGroups) renderGroup(subpidx, depth + 1, subgrp);
		};

		renderGroup(-1, 0, hier.root);
		new CollapsingList(divTree, toggleTree, parentIdx).manufacture();

		let divAction = $('<div align="center"/>').appendTo(this.mainDiv);
		let btnPropose = $('<button class="btn btn-action"/>').appendTo(divAction);
		btnPropose.append('<span class="glyphicon glyphicon-upload"></span> Propose Changes');
		btnPropose.click(() => this.actionPropose());
	}

	// clicked on a category
	private pickedAssignment(idx:number):void
	{
		if (this.current == idx) return;

		if (this.current >= 0)
		{
			let span = this.listSpanAssn[this.current];
			span.css({'background-color': 'transparent', 'color': Theme.NORMAL_HTML});

			let div = this.listDivValues[this.current];
			div.css({'border': 'none', 'background-color': 'transparent', 'padding': '0'});
			div.empty();
		}

		this.current = idx;

		let span = this.listSpanAssn[this.current];
		span.css({'background-color': Theme.STRONG_HTML, 'border-radius': '3px', 'color': 'white', 'text-decoration': 'none'});

		let div = this.listDivValues[this.current].empty();
		div.css({'border': '1px solid #6090A0', 'background-color': '#F0F8FF', 'padding': '0.5em'});

		this.buildAssignment(div, this.schema.assignments[this.current]);
	}

	// builds the tree of selectables to enable remapping
	private buildAssignment(div:JQuery, assn:SchemaAssignment):void
	{
		const SEP = '::';
		let valueKeys = new Set<string>();
		let valueAnnots:Record<string, AssayAnnotation> = {};
		let valueCounts:Record<string, number> = {};

		function makeKey(annot:AssayAnnotation):string
		{
			let list = [annot.valueURI];
			if (annot.valueHier) for (let uri of annot.valueHier) list.unshift(uri);
			return list.join(SEP);
		}

		for (let assayID in this.assayDef) for (let annot of this.assayDef[assayID].annotations)
		{
			if (!compatiblePropGroupNest(assn.propURI, assn.groupNest, annot.propURI, annot.groupNest)) continue;
			let count = valueCounts[annot.valueURI];
			valueCounts[annot.valueURI] = count ? count + 1 : 1;

			let key = makeKey(annot);
			if (!valueKeys.has(key))
			{
				valueKeys.add(key);
				valueAnnots[key] = annot;

				while (Vec.arrayLength(annot.valueHier) > 0)
				{
					annot = clone(annot);
					annot.valueURI = annot.valueHier[0];
					annot.valueLabel = annot.labelHier[0];
					annot.valueDescr = '';
					annot.valueHier = annot.valueHier.slice(1);
					annot.labelHier = annot.labelHier.slice(1);
					key = makeKey(annot);
					valueKeys.add(key);
					valueAnnots[key] = annot;
				}
			}
		}

		// TODO: include annotations that are out-of-schema...

		if (valueKeys.size == 0)
		{
			div.text('No annotations.');
			return;
		}

		let keyList = Array.from(valueKeys);
		keyList.sort();

		for (let n = 0; n < keyList.length; n++)
		{
			let annot = valueAnnots[keyList[n]];
			let para = $('<p/>').appendTo(div);
			para.css({'margin': '0', 'padding': '0.1em', 'white-space': 'nowrap'});
			para.css('padding-left', Vec.arrayLength(annot.valueHier) + 'em');
			if (Vec.arrayLength(annot.valueHier) > 0) para.append('\u{2192} ');
			let span = $('<span/>').appendTo(para);
			span.css('padding', '0.2em');
			if (annot.valueURI && annot.valueLabel)
			{
				span.css('font-weight', 'bold');
				span.text(annot.valueLabel);
			}
			else if (annot.valueURI)
			{
				span.css('font-style', 'italic');
				span.text(collapsePrefix(annot.valueURI));
			}
			else
			{
				span.text('"' + annot.valueLabel + '"');
			}

			let count = valueCounts[annot.valueURI];
			if (count > 0)
			{
				span.css('cursor', 'pointer');
				para.append(' (' + count + ')');

				let spanMap = $('<span/>').appendTo(para);
				spanMap.css('padding', '0.2em');

				let transKey = assn.locator + SEP + annot.valueURI;
				let translation = this.remappings[transKey];
				if (translation == null)
				{
					translation = {'assn': assn, 'fromURI': annot.valueURI, 'fromLabel': annot.valueLabel};
					this.remappings[transKey] = translation;
				}

				if (translation.toURI)
				{
					span.css({'background-color': 'black', 'color': 'white', 'text-decoration': 'none'});

					spanMap.html('&nbsp;\u{21E8}&nbsp;');

					let spanTerm = $('<span/>').appendTo(spanMap);
					spanTerm.css({'background-color': Theme.STRONG_HTML, 'color': 'white'});
					spanTerm.css({'border-radius': '3px', 'padding': '0.25em 0.5em 0.25em 0.5em'});
					spanTerm.text(translation.toLabel);
				}

				span.hover(() => this.hoverEnterTerm(translation, span), () => this.hoverLeaveTerm(translation, span));
				span.click(() => this.clickedTerm(translation, span, spanMap));
			}
		}
	}

	// mouse in/out on clickable terms
	private hoverEnterTerm(translation:PageBulkTranslation, span:JQuery):void
	{
		span.css('text-decoration', 'underline');
		span.css('color', translation.toURI ? 'white' : Theme.STRONG_HTML);
	}
	private hoverLeaveTerm(translation:PageBulkTranslation, span:JQuery):void
	{
		span.css('text-decoration', 'none');
		span.css('color', translation.toURI ? 'white' : Theme.NORMAL_HTML);
	}

	// clicked on a term to set/clear translation
	private clickedTerm(translation:PageBulkTranslation, spanFrom:JQuery, spanTo:JQuery):void
	{
		if (translation.toURI)
		{
			translation.toURI = null;
			spanFrom.css('background-color', 'transparent');
			spanFrom.css('color', Theme.NORMAL_HTML);
			spanTo.empty();
			return;
		}

		let assn = translation.assn;

		const settings:PickTermSettings =
		{
			'schema': this.schema,
			'multi': false,
		};
		let dlg = new PickTermDialog(assn, settings,
				(annot:AssayAnnotation):void => this.selectAnnotation(translation, spanFrom, spanTo, annot));
		dlg.showTree('Remap To');
	}

	// selected a mapping to apply
	private selectAnnotation(translation:PageBulkTranslation, spanFrom:JQuery, spanTo:JQuery, annot:AssayAnnotation):void
	{
		translation.toURI = annot.valueURI;
		translation.toLabel = annot.valueLabel;

		spanFrom.css({'background-color': 'black', 'color': 'white', 'text-decoration': 'none'});

		spanTo.html('&nbsp;\u{21E8}&nbsp;');

		let spanTerm = $('<span/>').appendTo(spanTo);
		spanTerm.css({'background-color': Theme.STRONG_HTML, 'color': 'white'});
		spanTerm.css({'border-radius': '3px', 'padding': '0.25em 0.5em 0.25em 0.5em'});
		spanTerm.text(translation.toLabel);
	}

	// first step toward proposing all of the bulk changes
	private actionPropose():void
	{
		if (!Authentication.isLoggedIn())
		{
			alert('You need to login before proposing changes.');
			return;
		}
		if (!Authentication.canSubmitBulk())
		{
			alert('Insufficient user privileges to submit bulk changes.');
			return;
		}

		this.proposed = [];
		for (let key in this.remappings)
		{
			let trans = this.remappings[key];
			if (trans.toURI) this.proposed.push(trans);
		}
		if (this.proposed.length == 0)
		{
			alert('Define at least one remapping translation first.');
			return;
		}

		// (note: might be nice to sort the list, but in practice these will probably be mostly one at a time)

		this.mainDiv.empty();
		this.mainDiv.append('<h1>Proposed Remapping</h1>');

		for (let trans of this.proposed)
		{
			let para = $('<p/>').appendTo(this.mainDiv);
			para.css({'white-space': 'nowrap', 'margin': '0.25em 0 0.25em 0'});

			for (let n = trans.assn.groupLabel.length - 1; n >= 0; n--)
			{
				let ital = $('<i/>').appendTo(para);
				ital.text(trans.assn.groupLabel[n]);
				para.append('\u{2192}');
			}
			let ital = $('<i/>').appendTo(para);
			ital.text(trans.assn.name);

			para.append(' : ');

			let font = $('<font/>').appendTo(para);
			font.css('color', Theme.STRONG_HTML);
			if (trans.fromURI && trans.fromLabel)
			{
				font.css({'font-weight': 'bold', 'text-decoration': 'underline'});
				font.text(trans.fromLabel);
			}
			else if (trans.fromURI)
			{
				font.css({'font-style': 'italic', 'text-decoration': 'underline'});
				font.text(collapsePrefix(trans.fromURI));
			}
			else
			{
				font.text('"' + trans.fromLabel + '"');
			}

			para.append(' \u{21E8} ');

			font = $('<font style="font-weight: bold; text-decoration: underline;"/>').appendTo(para);
			font.css('color', Theme.STRONG_HTML);
			font.text(trans.toLabel);
		}

		let divAction = $('<div align="center"/>').appendTo(this.mainDiv);
		let btnPropose = $('<button class="btn btn-action"/>').appendTo(divAction);
		btnPropose.append('<span class="glyphicon glyphicon-upload"/> Apply Changes');
		btnPropose.click(() => {btnPropose.prop('disabled', true); this.actionApply();});
	}

	// pulls the trigger...
	private actionApply():void
	{
		let mappingList:any[] = [];
		for (let trans of this.proposed)
		{
			let assn = trans.assn;
			let mapping:Record<string, any> = {'propURI': assn.propURI, 'groupNest': assn.groupNest, 'newValueURI': trans.toURI};
			if (trans.fromURI) mapping.oldValueURI = trans.fromURI; else mapping.oldValueLabel = trans.fromLabel;
			mappingList.push(mapping);
		}

		let params = {'assayIDList': this.assayIDList, 'mappingList': mappingList};
		callREST('REST/SubmitBulkMap', params, (result:any) => this.showResult(result));
	}

	// render the results
	private showResult(result:any):void
	{
		let para = $('<p/>').appendTo(this.mainDiv);

		if (result.success)
		{
			let num = result.holdingIDList.length;
			if (num > 0)
			{
				para.append(num + ' ');
				let ahref = $('<a>holding bay</a>').appendTo(para);
				ahref.attr('href', 'holding.jsp');
				para.append(' entr' + (num == 1 ? 'y' : 'ies') + ' created. ');
			}
			else para.text('No holding bay entries were created.');
		}
		else para.text('Bulk mapping unsuccessful.');
	}
}

/* EOF */ }

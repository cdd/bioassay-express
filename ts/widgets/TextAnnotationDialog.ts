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
	Enter free text annotation.
	To push users towards semantic annotations, similar annotations are searched and
	displayed to user as options.
*/

export class TextAnnotationDialog extends BootstrapDialog
{
	private MAX_RESULTS = 5;
	private inputText:JQuery;
	private similarAnnotations:JQuery;
	private cachedProposals:Record<string, AssayAnnotationProposal[]> = {};

	constructor(private assay:AssayDefinition, private assn:SchemaAssignment,
		private initTxt:string, private editing:boolean, private onSubmit:(txt:string) => void,
		private onSelectAnnotation:(annot:AssayAnnotation) => void)
	{
		super('Text Annotation');
	}

	protected populateContent():void
	{
		let para = $('<p></p>').appendTo(this.content);
		para.text(
			'Enter a text-based annotation, which can consist of anything. Text annotations will not be used in the same way as semantic ' +
			'web terms: they are not machine readable, and so will not add any valuable meaning to the assay. As a general rule, text should ' +
			'only be used as a placeholder when an appropriate semantic term cannot be found.');

		let divInput = $('<div></div>').appendTo(this.content);
		this.inputText = $('<input type="text" size="50"></input>').appendTo(divInput);
		this.inputText.css({'width': '100%'})
				.val(this.initTxt)
				.keydown(this.onKeyDown.bind(this))
				.keyup(this.changeText.bind(this));

		this.similarAnnotations = $('<div>').appendTo(this.content);

		let divButtons = $('<div></div>').appendTo(this.content)
				.css({'text-align': 'right', 'margin-top': '0.5em'});
		let okLabel = this.editing ? 'Update' : 'Add';
		$('<button class="btn btn-action"></button>').appendTo(divButtons)
				.append(`<span class="glyphicon glyphicon-ok"></span> ${okLabel}</button>`)
				.click(() => {this.onSubmit(this.inputText.val().toString()); this.hide();});
	}

	protected onShown():void
	{
		this.inputText.focus();
		if (this.inputText.val()) this.changeText();
	}

	private changeText():void
	{
		let text = this.inputText.val().toString();
		if (this.cachedProposals[text])
		{
			this.renderSimilarAnnotations(this.cachedProposals[text]);
			return;
		}
		let params:any =
		{
			'keywords': this.inputText.val(),
			'schemaURI': this.assay.schemaURI,
			'schemaBranches': this.assay.schemaBranches,
			'schemaDuplication': this.assay.schemaDuplication,
			'propURI': this.assn.propURI,
			'groupNest': this.assn.groupNest,
			'annotations': []
		};
		callREST('REST/KeywordMatch', params,
			(proposals:AssayAnnotationProposal[]) =>
			{
				this.cachedProposals[text] = proposals;
				if (text == this.inputText.val()) this.renderSimilarAnnotations(proposals);
			});
	}

	private onKeyDown(event:KeyboardEvent):void
	{
		let keyCode = event.keyCode || event.which;
		if (keyCode == KeyCode.Enter)
		{
			this.onSubmit(this.inputText.val().toString());
			this.hide();
		}
	}

	private renderSimilarAnnotations(proposals:AssayAnnotationProposal[]):void
	{
		this.similarAnnotations.empty();
		if (proposals.length == 0) return;
		this.similarAnnotations.append($('<b>')
				.text('Similar semantic terms found - if suitable use one of them'));
		let container = $('<ul>').appendTo(this.similarAnnotations);
		for (let annot of proposals.slice(0, this.MAX_RESULTS))
		{
			let item = $('<li>').appendTo(container)
					.append(highlightedText(annot.highlightLabel))
					.css({'cursor': 'pointer'})
					.click(() => {this.onSelectAnnotation(annot); this.hide();});
			if (annot.highlightAltLabel)
				item.append(' (').append(highlightedText(annot.highlightAltLabel)).append(')');
			this.appendInfoPopover(item, annot);
		}
	}

	private appendInfoPopover(item:JQuery, annot:AssayAnnotation):void
	{
		let content = Popover.CACHE_BRANCH_CODE;
		if (annot.altLabels)
			content += `Other labels: ${annot.altLabels.sort(Intl.Collator().compare).join(', ')}`;
		content += Popover.CACHE_DESCR_CODE;
		Popover.click(domLegacy(item), annot.valueLabel, content,
				{'schemaURI': this.assay.schemaURI, 'propURI': annot.propURI,
				'groupNest': annot.groupNest, 'valueURI': annot.valueURI});
	}
}

/* EOF */ }

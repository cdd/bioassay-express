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
	Display and selection of terms from the reference ontology.
*/

interface OntologyBranchWidget extends OntologyBranch
{
	divLine?:DOM;
	divHier?:DOM;
	isOpen?:boolean;
	isLoaded?:boolean;
}

export class PickOntologyDialog extends wmk.Dialog
{
	public widget:PickOntologyWidget; // caller can preconfigure the widget after construction

	private callbackButtons:(div:DOM) => void = null;

	constructor(private type:'property' | 'value' | 'schema')
	{
		super();
		this.title = 'Ontology ' + (type == 'property' ? 'Properties' : 'Values');
		this.minPortionWidth = this.maxPortionWidth = 95;
		this.widget = new PickOntologyWidget(this.type);
	}

	// option to add buttons to the top right
	public onButtons(callback:(div:DOM) => void):void
	{
		this.callbackButtons = callback;
	}

	// ------------ private methods ------------

	protected populate():void
	{
		this.domTitle.css({'background-color': '#F8F8F8', 'background-image': 'none'});
		this.domTitleButtons.empty();

		let btnClose = dom('<button class="btn btn-action">Cancel</button>').appendTo(this.domTitleButtons).css({'margin-left': '0.5em'});
		btnClose.onClick(() => this.close());

		if (this.callbackButtons) this.callbackButtons(this.domTitleButtons);

		this.widget.render(this.domBody);
	}
}

/* EOF */ }

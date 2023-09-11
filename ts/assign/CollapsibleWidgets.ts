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
	General collapsible container with toolbar which you can put JQuery elements into and then collapse.
*/

export class CollapsibleContainer
{
	public onChangeHidden:() => void = null;
	public barTitle:string;

	private divOuter:JQuery; // overall container
	private divHeader:JQuery; // the top bar with grip & buttons
	private divButtons:JQuery; // buttons on right hand side
	private divInner:JQuery; // area for custom panel content
	private btnHide:JQuery;
	private btnUp:JQuery;
	private btnDown:JQuery;
	private btnPin:JQuery;
	private isPinned = false;
	private scrollHandler:() => void = null;

	// ------------ public methods ------------

	constructor(public title:string, private domReference:JQuery, private allowVerticalMovement:boolean = false, private isOpen:boolean = true)
	{
		this.barTitle = title;
	}

	public hide():void
	{
		if (!this.isOpen) return;
		this.isOpen = false;
		this.updateVisibleState();
		if (this.onChangeHidden) this.onChangeHidden();
	}

	public toggleContainerVisibility():void
	{
		this.isOpen = !this.isOpen;
		this.updateVisibleState();
		if (this.onChangeHidden) this.onChangeHidden();
	}

	public isContainerOpen():boolean
	{
		return this.isOpen;
	}

	// return the DOM that is used to enapsulate the main content
	public getPanelArea():JQuery
	{
		return this.divInner;
	}

	// return the container for adding small buttons at the top right
	public getButtonArea():JQuery
	{
		return this.divButtons;
	}
	
	// uses the given domParent (jQuery object) to build the list; domParent is the element into which the whole thing 
	// will be rendered
	public render(parent:JQuery):void
	{
		this.divOuter = $('<div></div>').appendTo(parent);
		this.divOuter.css({'margin-left': '0.5em', 'border': '1px solid #EEEEEE'});
		
		// setting 'relative' position is in case there are any 'absolute' children; this would be harmless, except
		// that it sometimes breaks the popovers, so only use when necessary
		if (this.allowVerticalMovement)
			this.divOuter.css({'top': '0px', 'position': 'relative'});

		this.divHeader = $('<div></div>').appendTo(this.divOuter);
		this.divHeader.css('background-color', '#CCCCCC');
		
		this.btnHide = $('<button class="btn btn-xs btn-normal" style=\"margin: 3px;\"></button>').appendTo(this.divHeader);
		$('<span class="glyphicon glyphicon-arrow-left" style=\"height: 1.2em;"></span>').appendTo(this.btnHide);
		let spanTitle = $('<span></span>').appendTo(this.divHeader);
		spanTitle.css({'margin-left': '0.5em', 'font-weight': 'bold'});
		spanTitle.text(this.title);

		let divRight = $('<div style="float: right;"></div>').appendTo(this.divHeader);
		this.divButtons = $('<div></div>').appendTo(divRight);
		this.divButtons.css('display', 'flex');

		if (this.allowVerticalMovement)
		{
			this.btnPin = $('<button class="btn btn-xs btn-normal"></button>').appendTo(this.divButtons);
			this.btnPin.css('margin', '3px');
			this.btnPin.append('<span class="glyphicon glyphicon-pushpin" style=\"height: 1.2em;"></span>');
			this.btnPin.prop('disabled', false);

			this.btnUp = $('<button class="btn btn-xs btn-normal"></button>').appendTo(this.divButtons);
			this.btnUp.css('margin', '3px');
			this.btnUp.append('<span class="glyphicon glyphicon-arrow-up" style=\"height: 1.2em;"></span>');
			this.btnUp.prop('disabled', true);

			this.btnDown = $('<button class="btn btn-xs btn-normal"></button>').appendTo(this.divButtons);
			this.btnDown.css('margin', '3px');
			this.btnDown.append('<span class="glyphicon glyphicon-arrow-down" style=\"height: 1.2em;"></span>');
			this.btnDown.prop('disabled', true);

			setTimeout(() => this.setupMovementButtons(), 1);
		}
		
		// populate the activity area	
		this.divInner = $('<div></div>').appendTo(this.divOuter);
				
		// encode the text toggle on/off
		this.updateVisibleState();
		this.btnHide.click(() => this.toggleContainerVisibility());

		this.scrollHandler = () => this.scrollWindow();
		window.addEventListener('scroll', this.scrollHandler, false);
	}

	// cleanup after itself, i.e. take everything off the page
	public remove():void
	{
		this.divOuter.remove();
		if (this.scrollHandler) window.removeEventListener('scroll', this.scrollHandler);
	}

	// ------------ private methods ------------

	private updateVisibleState():void
	{
		this.divOuter.css('display', this.isOpen ? 'block' : 'none');
	}

	private setupMovementButtons():void
	{
		this.btnUp.click(() =>
		{
			let [y, h, maxH] = this.getMetrics(), windowH = $(window).height();
			let delta = -Math.min(0.5 * (maxH - h), 0.3 * windowH);
			if (y + delta < 0) delta = -y;
			if (delta >= 0) return;

			let prop = {'top': '+=' + delta + 'px'};
			let opt = {'duration': 500, 'complete': () => this.updateVerticalNavState()};
			this.divOuter.animate(prop, opt);
		});
		this.btnDown.click(() =>
		{
			let [y, h, maxH] = this.getMetrics(), windowH = $(window).height();
			let delta = Math.min(0.5 * (maxH - h), 0.3 * windowH);
			if (y + h + delta > maxH) delta = maxH - y - h;
			if (delta <= 0) return;

			let prop = {'top': '+=' + delta + 'px'};
			let opt = {'duration': 500, 'complete': () => this.updateVerticalNavState()};
			this.divOuter.animate(prop, opt);
		});
		this.btnPin.click(() =>
		{
			this.isPinned = !this.isPinned;
			this.btnPin.toggleClass('btn-action', this.isPinned);
			this.btnPin.toggleClass('btn-normal', !this.isPinned);
			this.scrollWindow();
			this.updateVerticalNavState();
		});
		this.updateVerticalNavState();
	}

	private getMetrics():[number, number, number]
	{
		let y = parseFloat(this.divOuter.css('top')) || 0;
		let h = this.divOuter.height(), maxH = this.domReference.height();
		return [y, h, maxH];
	}

	// makes sure the buttons are synced after an adustment
	private updateVerticalNavState():void
	{
		let [y, h, maxH] = this.getMetrics();
		if (y + h > maxH)
		{
			y = Math.max(0, maxH - h);
			this.divOuter.css('top', y + 'px');
		}

		let canMoveUp = y > 1E-5;
		let canMoveDown = y + h < maxH - 1E-5;
		this.btnUp.prop('disabled', this.isPinned || !canMoveUp);
		this.btnDown.prop('disabled', this.isPinned || !canMoveDown);
	}

	// the window scrolled: may want to do something about this
	private scrollWindow():void
	{
		if (!this.isPinned) return;

		let delta = window.pageYOffset - this.domReference.offset().top;
		let deltaMax = this.domReference.height() - this.divOuter.height();
		delta = Math.max(0, Math.min(deltaMax, delta));
		this.divOuter.css('top', delta + 'px');

		this.updateVerticalNavState();
	}
}

/*
	Implements a vertical bar that displays several "collapsibles". The bar provides the ability to restore the collapsed containers
	after they have been removed from display.
*/

export class CollapsibleController
{
	private divBar:JQuery;
	private divTabs:JQuery[] = [];

	// ------------ public methods ------------

	constructor(private containers:CollapsibleContainer[], private onToggleContainer:(cont:CollapsibleContainer) => void)
	{
		for (let cont of this.containers) cont.onChangeHidden = () => this.updateBar();
	}

	public render(parent:JQuery):void
	{
		this.divBar = $('<div></div>').appendTo(parent);
		this.divBar.css({'display': 'flex', 'flex-direction': 'column'});

		const WIDTH = '2em', HEIGHT = '14em';
		const XBUMP = '1em', YBUMP = '13em';

		for (let cont of this.containers)
		{
			let div = $('<div></div>').appendTo(this.divBar);
			div.css({'font-weight': 'bold', 'padding': '0.5em', 'margin-bottom': '0.5em', 'width': WIDTH, 'height': HEIGHT, 'position': 'relative'});
			div.css({'cursor': 'pointer'});
			div.click(() => this.onToggleContainer(cont));

			let spanText = $('<span></span>').appendTo(div);
			spanText.css({'position': 'absolute', 'width': HEIGHT, 'height': WIDTH, 'left': XBUMP, 'top': YBUMP});
			spanText.css({'text-align': 'center', 'padding-top': '0.2em'});
			spanText.css({'transform': 'rotate(-90deg)', 'transform-origin': '0'});
			spanText.text(cont.barTitle);
			this.divTabs.push(div);
		}

		this.updateBar();
	}

	// ------------ private methods ------------

	private updateBar():void
	{
		for (let n = 0; n < this.containers.length; n++)
		{
			let cont = this.containers[n], div = this.divTabs[n];
			div.css('background-color', cont.isContainerOpen() ? '#EEEEEE' : '#CCCCCC');
			div.css('color', cont.isContainerOpen() ? 'black' : 'white');
		}
	}
}

/* EOF */ }
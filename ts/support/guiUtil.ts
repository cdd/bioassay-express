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
	GUI utilties for BAE project.
*/

export class OverlayMessage
{
	private static instance:OverlayMessage;
	private overlayDiv:JQuery;
	private messageDiv:JQuery;
	private minTime = 500; // minimum time the overlay message is shown
	private startTime:Date;
	private numberShown = 0;

	private constructor()
	{
		this.overlayDiv = $('<div/>');
		this.overlayDiv.hide();

		this.messageDiv = $('<div/>').appendTo(this.overlayDiv);
		this.messageDiv.css({'width': '500px', 'height': '250px'});
		this.messageDiv.css({'background-color': '#F0F8FF'});
		this.messageDiv.css({'border': '1px solid #6090A0', 'border-radius': '2px'});
		this.messageDiv.css({'font-size': '1.5em'});
		this.messageDiv.css({'display': 'flex', 'justify-content': 'center', 'align-items': 'center'});

	}

	public static getInstance():OverlayMessage
	{
		if (!OverlayMessage.instance) OverlayMessage.instance = new OverlayMessage();
		return OverlayMessage.instance;
	}

	private displayFullScreen():void
	{
		this.overlayDiv.css({'position': 'fixed', 'z-index': '100000'});
		this.overlayDiv.css({'top': '0', 'left': '0', 'right': '0', 'bottom': '0'});
		this.overlayDiv.css({'background-color': '#88888844'});

		this.messageDiv.css({'position': 'absolute', 'margin': 'auto'});
		this.messageDiv.css({'transform': 'translate(-50%, -50%)', 'top': '50%', 'left': '50%'});
	}

	private displayInContainer():void
	{
		this.overlayDiv.css({'position': '', 'z-index': ''});
		this.overlayDiv.css({'background-color': 'white'});

		this.messageDiv.css({'position': 'relative', 'margin': ''});
		this.messageDiv.css({'transform': 'translate(-50%, 0%)', 'top': '', 'left': '50%'});
	}

	public static show(message:string, options = {}, container:JQuery = null):void
	{
		let instance = OverlayMessage.getInstance();

		if (container)
		{
			instance.overlayDiv.appendTo(container);
			instance.displayInContainer();
		}
		else
		{
			instance.displayFullScreen();
			instance.overlayDiv.appendTo(document.body);
		}
		instance.messageDiv.empty();
		instance.messageDiv.css(options);
		let div = $('<div/>').appendTo(instance.messageDiv);
		$('<div/>').appendTo(div).html(message);
		instance.overlayDiv.show();
		instance.numberShown += 1;
		instance.startTime = new Date();
	}

	public static hide():void
	{
		let instance = OverlayMessage.getInstance();
		instance.delayedHide();
	}

	private delayedHide():void
	{
		let elapsedTime = new Date().getTime() - this.startTime.getTime();
		setTimeout(() =>
		{
			this.numberShown -= 1;
			if (this.numberShown <= 0) this.overlayDiv.hide();
		}, Math.max(0, this.minTime - elapsedTime));
	}
}

/* EOF */ }

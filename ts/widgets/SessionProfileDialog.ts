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
	Surface dialog when user clicks on profile icon.
*/

export class SessionProfileDialog extends BootstrapDialog
{
	private inputUserName:JQuery;
	private inputEmail:JQuery;

	constructor(private session:LoginSession, private profileWasChangedFcn:(newSession:LoginSession) => void, private modalWasDismissedFcn:() => void)
	{
		super('Account Profile');
		this.modalWidth = '50%';
	}

	// ------------ private methods ------------

	protected populateContent():void
	{
		let divProfile = $('<div></div>').appendTo(this.content);
		divProfile.css({'background-color': 'rgb(217, 237, 247, 0.33)', 'border': '1px solid rgb(150, 202, 255)'});
		divProfile.css({'box-shadow': 'rgba(66, 77, 88, 0.1) 0px 0px 5px', 'padding': '0.5em'});

		let table = $('<table width="100%"></table>').appendTo(divProfile);
		table.css({'margin-top': '0.5em'});

		let createLine = (elt:JQuery, label:string):void =>
		{
			let line = $('<tr></tr>').appendTo(table);

			let td0 = $('<td></td>').appendTo(line);
			//td0.css({'text-align': 'right', 'vertical-align': 'super', 'padding-top': '0.25em', 'padding-bottom': '0.25em', 'padding-right': '7px', 'margin': '0', 'font-weight': 'bold', 'font-size': 'large'});
			td0.css({'text-align': 'right', 'font-weight': 'bold', 'padding-right': '1em'});
			td0.text(label);

			let td1 = $('<td></td>').appendTo(line);
			elt.appendTo(td1);
			elt.css({'width': '100%'});
		};

		// username
		this.inputUserName = $('<input type="text" class="text-box" spellcheck="false"></input>');
		this.inputUserName.val(this.session.userName);
		createLine(this.inputUserName, 'Username:');

		// email is editable for basic authentication; otherwise
		// email is not editable and displayed only if non-null
		if (this.session.type == 'Basic')
		{
			this.inputEmail = $('<input type="text" class="text-box" spellcheck="false"></input>');
			this.inputEmail.val(this.session.email);
			createLine(this.inputEmail, 'E-mail:');
		}
		else if (this.session.email != null && this.session.email.length > 0)
		{
			this.inputEmail = null;
			let txtEmail = $('<span></span>').text(this.session.email);
			createLine(txtEmail, 'E-mail:');
		}

		// service name
		let txtServiceName = $('<span></span>').text(this.session.serviceName);
		createLine(txtServiceName, 'Service name:');

		// list buttons in footer
		let divFooter = $('<div></div>').appendTo(this.content);
		divFooter.css({'text-align': 'right', 'margin-top': '1em'});
		let btnCancel = $('<button class="btn btn-normal btn-secondary" data-dismiss="modal"></button>').appendTo(divFooter);
		btnCancel.css({'margin-right': '0.5em'});
		btnCancel.append('Cancel');

		let btnUpdate = $('<button class="btn btn-action btn-primary"></button>').appendTo(divFooter);
		btnUpdate.append('Update');
		btnUpdate.click(() => this.updateUserName());
	}

	protected onShown():void
	{
		this.inputUserName.focus();
	}

	private updateUserName():void
	{
		let errors:string[] = [];

		// validate user input
		let newUserName = (this.inputUserName.val() as string).trim();
		if (newUserName.length == 0) errors.push('Please enter a valid user name.');

		let newEmail = this.session.email;
		if (this.inputEmail)
		{
			newEmail = (this.inputEmail.val() as string).trim();
			if (!/\S+@\S+\.\S+/.test(newEmail)) errors.push('Email address doens\'t look right');
		}
		if (errors.length > 0)
		{
			alert(errors.join('\n'));
			return;
		}

		let nothingChanged = this.session.userName != null && this.session.userName == newUserName &&
							 this.session.email != null && this.session.email == newEmail;
		if (nothingChanged)
		{
			this.hide();
			alert('Nothing changed');
			return;
		}

		let params = {'curatorID': this.session.curatorID, 'userName': newUserName, 'email': newEmail};
		callREST('REST/UpdateUserProfile', params, (data:any) =>
			{
				confirm('You have successfully edited the user profile.');

				// coerce a new session as user attributes have changed
				let newSession:LoginSession =
				{
					...this.session,
					'userName': params.userName,
					'email': params.email
				};
				this.profileWasChangedFcn(newSession);
				this.hide();
			},
			(jqXHR:JQueryXHR, textStatus:string, errorThrow:string) =>
			{
				let rt:any = JSON.parse(jqXHR.responseText);
				alert('Error editing user profile: ' + rt.userMessage);
			});
	}
}

/* EOF */ }

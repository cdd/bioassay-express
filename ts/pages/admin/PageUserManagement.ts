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

///<reference path='../../support/Authentication.ts'/>
namespace BioAssayExpress /* BOF */ {

/*
	Admin page: user management
*/

interface UserInformation
{
	service:string;
	curatorID:string;
	userID:string;
	status:string;
	name:string;
	email:string;
	[key:string]:string;
}

const STATUS_LIST =
[
	Authentication.STATUS_BLOCKED,
	Authentication.STATUS_DEFAULT,
	Authentication.STATUS_CURATOR,
	Authentication.STATUS_ADMIN
];

export class PageUserManagement
{
	private users:UserInformation[];
	private resultDiv:JQuery;
	private tableDiv:JQuery;
	private filter:JQuery;

	constructor(userData:any)
	{
		this.users = userData.users;
		this.buildContent();
	}

	// ------------ private methods ------------

	private cellCSS = {'text-align': 'center', 'vertical-align': 'middle', 'padding': '0.25em 0.5em'};

	// builds the first-look UI components for the main block, which invite the user to provide information to kickstart a new assay
	private buildContent():void
	{
		let main = $('#main');
		main.empty();
		this.renderFilter(main);
		this.tableDiv = $('<div/>').appendTo(main);
		this.renderUserTable();
		this.resultDiv = $('<div/>').appendTo(main);
		this.rolePrivilegesDescription($('<div/>').appendTo(main));
	}

	private renderFilter(main:JQuery)
	{
		let div = $('<div/>').appendTo(main);
		div.css({'margin': '1em 2em', 'float': 'none'});
		div.addClass('search-box');

		this.filter = $('<input/>').appendTo(div);
		this.filter.attr('placeholder', 'filter by name');
		this.filter.keyup(() => this.renderUserTable());
	}

	private renderUserTable()
	{
		this.tableDiv.empty();
		this.tableDiv.css({'margin': '1em 2em'});
		let table = $('<table/>').appendTo(this.tableDiv);
		this.renderHeader(table);
		this.renderBody(table);
	}

	private renderHeader(table:JQuery)
	{
		let thead = $('<thead/>').appendTo(table);
		let headers = ['Service', 'User ID', 'Status', 'Name', 'Email', 'Actions'];
		let tr = $('<tr/>').appendTo(thead).css({'background-color': '#C0C0C0'});
		for (let header of headers)
		{
			let th = $('<th/>').appendTo(tr).css({...this.cellCSS, 'background-color': 'grey', 'color': 'white'});
			th.text(header);
		}
	}

	private renderBody(table:JQuery)
	{
		let filter = this.filter.val().toString().trim().toLocaleLowerCase();
		let users = this.users.filter((user) =>
		{
			if (filter == '') return true;
			let s = user.name + ' ' + user.email.split('@')[0] + ' ' + user.userID;
			return s.toLocaleLowerCase().includes(filter);
		});
		let tbody = $('<tbody/>').appendTo(table);
		let fields = ['service', 'userID', 'status', 'name', 'email'];

		let grouped:Record<string, UserInformation[]> = {};
		for (let user of users)
		{
			(grouped[user.service] = grouped[user.service] || []).push(user);
		}
		let tr:JQuery = null;
		for (let service in grouped)
		{
			for (let user of grouped[service])
			{
				tr = $('<tr/>').appendTo(tbody);
				for (let field of fields)
				{
					let td = $('<td/>').appendTo(tr).css(this.cellCSS);
					if (field == 'status')
						td.text(Authentication.STATUS_UI[user[field]]);
					else
						td.text(user[field]);
				}
				let td = $('<td/>').appendTo(tr).css({...this.cellCSS, 'text-align': 'left'});
				let actions:JQuery[] = [];
				if (service != 'Trusted') actions.push(this.changeStatus(user));
				if (service == 'Basic') actions.push(this.resetPassword(user));
				let sep = '';
				for (let action of actions)
				{
					td.append(sep);
					td.append(action);
					sep = ', ';
				}
			}
		}
		if (tr != null) tr.css('border-bottom', '1px solid grey');
	}

	private resetPassword(user:UserInformation)
	{
		let a = $('<a href="#"/>');
		a.append('reset password');
		a.addClass('pseudoLink');
		a.click((e) =>
		{
			e.preventDefault();
			this.resultDiv.empty();
			this.resultDiv.css({'margin': '1em 2em'});
			callREST('REST/admin/ResetPassword', {'curatorID': user.curatorID},
				(data:any) =>
				{
					$('<h1>Reset user password</h1>').appendTo(this.resultDiv);
					let p = $('<p/>').appendTo(this.resultDiv);
					p.text(`Password for user ${user.userID} changed to: ${data.password} `);
					let btn = $('<span class="glyphicon glyphicon-copy" />').appendTo(p);
					btn.addClass('btn-normal clickable');
					btn.attr('title', 'Copy to clipboard');
					btn.css({'padding': '0.2em', 'border-radius': '3px'});
					btn.click(() => copyToClipboard(data.password));
				},
				() =>
				{
					$('<h1>Reset user password</h1>').appendTo(this.resultDiv);
					this.resultDiv.append(`<p>Could not change password for ${user.userID}</p>`);
				});
		});
		return a;
	}

	private changeStatus(user:UserInformation)
	{
		let a = $('<a href="#"/>');
		a.append('change status');
		a.addClass('pseudoLink');

		a.click((e) =>
		{
			e.preventDefault();
			this.resultDiv.empty();
			this.resultDiv.css({'margin': '1em 2em'});
			$('<h1>Change user status</h1>').appendTo(this.resultDiv);
			this.resultDiv.append(`<p>Change status for ${user.userID}</p>`);

			let div = $('<div/>').appendTo(this.resultDiv);
			for (let status of STATUS_LIST)
			{
				let ui_status = Authentication.STATUS_UI[status];
				let label = $(`<label> ${ui_status}</label>`).appendTo(div);
				let input = $(`<input type="radio" name="status" value='${status}'/> `).prependTo(label);
				if (user.status == status) input.attr('checked', 'true');
				input.change(() => this.adminChangeStatus(user, status));
			}
		});
		return a;
	}

	private adminChangeStatus(user:UserInformation, status:string)
	{
		callREST('REST/admin/ChangeStatus', {'curatorID': user.curatorID, 'newStatus': status},
			() =>
			{
				let ui_status = Authentication.STATUS_UI[status];
				user.status = status;
				this.resultDiv.append(`<p>Status for user ${user.userID} changed to: ${ui_status}</p>`);
				this.renderUserTable();
			},
			() =>
			{
				this.resultDiv.append(`<p>Could not change status for ${user.userID}</p>`);
			});
	}

	private PRIVILEGES =
	[
		{'privilege': 'View and search annotations', 'roles': Authentication.ALL_STATUS},
		{'separator': true},
		{'privilege': 'Edit annotations and export', 'roles': Authentication.ALL_STATUS},
		{'privilege': 'Edit annotations and submit to holding bay', 'roles': [Authentication.STATUS_ADMIN, Authentication.STATUS_DEFAULT, Authentication.STATUS_CURATOR]},
		{'privilege': 'Edit annotations and submit to database', 'roles': [Authentication.STATUS_ADMIN, Authentication.STATUS_CURATOR]},
		{'privilege': 'Request provisional terms', 'roles': [Authentication.STATUS_ADMIN, Authentication.STATUS_CURATOR]},
		{'separator': true},
		{'privilege': 'Apply holding bay entries to database', 'roles': [Authentication.STATUS_ADMIN, Authentication.STATUS_CURATOR]},
		{'privilege': 'Remove holding bay entries', 'roles': [Authentication.STATUS_ADMIN, Authentication.STATUS_CURATOR]},
		{'separator': true},
		{'privilege': 'Bulk import data', 'roles': [Authentication.STATUS_ADMIN]},
		{'privilege': 'Manage provisional term requests', 'roles': [Authentication.STATUS_ADMIN]},
		{'privilege': 'User management', 'roles': [Authentication.STATUS_ADMIN]},
		{'separator': true},
	];

	public rolePrivilegesDescription(container:JQuery)
	{
		container.empty();
		container.css({'margin': '1em 2em'});
		let table = $('<table/>').appendTo(container);

		let thead = $('<thead/>').appendTo(table);
		let tr = $('<tr/>').appendTo(thead).css({'background-color': '#C0C0C0'});
		let th = $('<th/>').appendTo(tr).css({...this.cellCSS, 'background-color': 'grey', 'color': 'white'});
		th.text('Privilege/Role');
		for (let status of Authentication.ALL_STATUS)
		{
			th = $('<th/>').appendTo(tr).css({...this.cellCSS, 'background-color': 'grey', 'color': 'white'});
			th.text(Authentication.STATUS_UI[status]);
		}

		let tbody = $('<tbody/>').appendTo(table);
		let tick = '<span class="glyphicon glyphicon-ok" style="height: 1.2em;color: green"/>';
		for (let privilege of this.PRIVILEGES)
		{
			if (privilege.separator)
			{
				tr.css('border-bottom', '1px solid grey');
				continue;
			}
			tr = $('<tr/>').appendTo(tbody);
			let td = $('<td/>').appendTo(tr);
			td.text(privilege['privilege']);

			for (let status of Authentication.ALL_STATUS)
			{
				td = $('<td/>').appendTo(tr).css({'text-align': 'center'});
				if (privilege.roles.includes(status)) td.append($(tick));
			}
		}
	}
}

/* EOF */ }

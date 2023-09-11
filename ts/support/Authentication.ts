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
	Provides functionality for logging in/out, given a list of available services.
*/

export class Authentication
{
	// optionally set this value globally in order to get notified when authentication status changed
	public static hookAuthentChanged:() => void = null;

	private signinWindow:Window;
	private state:string;

	public static STATUS_DEFAULT = 'default'; // same as null: basically a walk-in
	public static STATUS_BLOCKED = 'blocked'; // explicitly disallowed from doing anything
	public static STATUS_CURATOR = 'curator'; // allowed to submit content directly
	public static STATUS_ADMIN = 'admin'; // can perform sensitive tasks remotely
	public static STATUS_UI:Record<string, string> = // translate to user facing description of role
	{
		[Authentication.STATUS_BLOCKED]: 'blocked',
		[Authentication.STATUS_DEFAULT]: 'read only',
		[Authentication.STATUS_CURATOR]: 'read/write',
		[Authentication.STATUS_ADMIN]: 'admin',
	};
	public static ALL_STATUS = [Authentication.STATUS_BLOCKED, Authentication.STATUS_DEFAULT, Authentication.STATUS_CURATOR, Authentication.STATUS_ADMIN];

	constructor(private serviceslist:AuthenticationService[], private parent:JQuery, resetkey?:string)
	{
		this.renderAuthentication();
		if (resetkey) this.resetBasicAuthentication(resetkey);
	}

	public static isLoggedIn():boolean
	{
		return Authentication.currentSession() != null;
	}

	// fetches the current session, which is stored in the cookies; note that it does not guarantee that the session is valid, merely that
	// it seemed to be last time it got checked
	public static currentSession():LoginSession
	{
		return Authentication.readSession();
	}

	// judgment call about what a session is allowed to do (note that this is preliminary on the client side: the server will ultimately
	// cast the final verdict on what really goes down)
	public static canSubmitHoldingBay(session?:LoginSession):boolean
	{
		if (session == null) session = Authentication.currentSession();
		if (!session) return false;
		return [Authentication.STATUS_DEFAULT, Authentication.STATUS_CURATOR, Authentication.STATUS_ADMIN].indexOf(session.status) >= 0;
	}
	public static canSubmitDirectly(session?:LoginSession):boolean
	{
		if (session == null) session = Authentication.currentSession();
		if (!session) return false;
		return [Authentication.STATUS_CURATOR, Authentication.STATUS_ADMIN].indexOf(session.status) >= 0;
	}
	public static canSubmitBulk(session?:LoginSession):boolean
	{
		if (session == null) session = Authentication.currentSession();
		if (!session) return false;
		return [Authentication.STATUS_CURATOR, Authentication.STATUS_ADMIN].indexOf(session.status) >= 0;
	}
	public static canApplyHolding(session?:LoginSession):boolean
	{
		if (session == null) session = Authentication.currentSession();
		if (!session) return false;
		return [Authentication.STATUS_CURATOR, Authentication.STATUS_ADMIN].indexOf(session.status) >= 0;
	}
	public static canRequestProvisionalTerm(session?:LoginSession):boolean
	{
		if (session == null) session = Authentication.currentSession();
		if (!session) return false;
		return [Authentication.STATUS_CURATOR, Authentication.STATUS_ADMIN].indexOf(session.status) >= 0;
	}
	public static canAccessOntoloBridge(session?:LoginSession):boolean
	{
		if (session == null) session = Authentication.currentSession();
		if (!session) return false;
		return [Authentication.STATUS_ADMIN].indexOf(session.status) >= 0;
	}
	public static canBulkChangeAnnotations(session?:LoginSession):boolean
	{
		return this.hasAccessRights(session, Authentication.STATUS_ADMIN);
	}

	// ------------ private methods ------------

	private static hasAccessRights(session?:LoginSession, ...allowedRoles:string[]):boolean
	{
		if (session == null) session = Authentication.currentSession();
		if (!session) return false;
		return allowedRoles.includes(session.status);
	}

	private static isAdministrator():boolean
	{
		let session = Authentication.currentSession();
		return session != null && session.status == Authentication.STATUS_ADMIN;
	}

	// modify page to be consistent with the authentication status
	private renderAuthentication():void
	{
		this.parent.css('white-space', 'nowrap');

		if (Authentication.isLoggedIn())
		{
			this.renderLogout();
			$('.loginRequired').show();
		}
		else
		{
			this.renderLogin();
			$('.loginRequired').hide();
		}
		if (Authentication.isAdministrator()) $('.adminRequired').show(); else $('.adminRequired').hide();

		if (Authentication.hookAuthentChanged) Authentication.hookAuthentChanged();
	}

	// render the login button (i.e. not currently logged in)
	private renderLogin():void
	{
		Authentication.removeSession();

		this.parent.empty();

		if (this.serviceslist.length == 0) return;

		// if just one way to authenticate, show a regular button
		if (this.serviceslist.length == 1)
		{
			let btn = $('<button class="btn btn-action"></button>').appendTo(this.parent);
			btn.append($('<span class="glyphicon glyphicon-log-in"></span>'));
			btn.append(' Login');
			btn.click(() => this.loginService(this.serviceslist[0]));
			return;
		}

		// make a dropdown button, with named choices
		let div = $('<div class="btn-group"></div>').appendTo(this.parent);
		let btn = $('<button class="btn btn-action dropdown-toggle" data-toggle="dropdown"></button>').appendTo(div);
		btn.append('Login ');
		btn.append($('<span class="caret"></span>'));
		let ul = $('<ul class="dropdown-menu dropdown-menu-right"></ul>').appendTo(div);

		for (let serviceType of ['Trusted', 'OAuth', 'Basic', 'LDAP'])
			this.serviceslist.filter((a) => a.type == serviceType).forEach((a) => this.renderServiceLink(a, ul));
	}

	private renderServiceLink(auth:AuthenticationService, ul:JQuery):void
	{
		let li = $('<li></li>').appendTo(ul);
		let ahref = $('<a href="#"></a>').appendTo(li);
		ahref.click(() => this.loginService(auth));
		ahref.text(auth.name);
	}

	// renders the button state for logging out (i.e. login is thought to be valid at the moment)
	public renderLogout():void
	{
		this.parent.empty();

		let session = Authentication.readSession();
		let div = $('<div class="btn-group"></div>').appendTo(this.parent);
		let profileBtn = $('<button class="btn btn-action glyphicon glyphicon-user dropdown-toggle" data-toggle="dropdown"></button>').appendTo(div);
		let unorderedList = $('<ul class="dropdown-menu dropdown-menu-right" role="menu"></ul>').appendTo(div);
		unorderedList.css({'background-color': '#F7FAFC', 'width': 'fit-content', 'padding-top': '0px'});

		// title bar of dropdown menu, including logout link
		let item = this.appendElement(unorderedList, 'li', null,
				{'background-color': '#DEEAF2', 'width': 'auto', 'display': 'flex',
				'justify-content': 'space-between', 'padding': '0px 12px'});
		this.appendElement(item, 'div', 'Account', {'font-weight': 'bold'});
		let logout = this.appendElement(this.appendElement(item, 'div'), 'a', 'Logout',
				{'cursor': 'pointer', 'font-weight': 'bold'});
		logout.click(() => this.logout());

		// account info
		let accountInfoItem = this.appendElement(unorderedList, 'li', null, {'width': 'auto'});
		for (let key of ['userName', 'email', 'serviceName'])
		{
			let v = session[key] as string;
			if (v == null || v.length == 0) continue;
			this.appendElement(accountInfoItem, 'p', v, {'margin': '2px 12px'});
		}

		if (this.isEditable(session))
		{
			// tack on edit button at bottom of account info section
			let editBtn = this.appendElement(accountInfoItem, '<button class="btn btn-link"></button>',
					'Edit', {'padding-top': '1px', 'padding-bottom': '0px'});
			editBtn.click(() =>
			{
				let dlg = new SessionProfileDialog(session, (newSession:LoginSession):void =>
				{
					// replace the existing session with the new one listed as a parameter
					Authentication.removeSession();
					this.authenticated(newSession);
				}, () => {});
				dlg.show();
			});
		}
	}

	private appendElement(anchor:JQuery, tag:string, content?:string, style?:any):JQuery
	{
		if (!tag.includes('<')) tag = `<${tag}/>`;
		let e = $(tag).appendTo(anchor);
		if (style) e.css(style);
		if (content) e.text(content);
		return e;
	}

	private loginService(auth:AuthenticationService):void
	{
		switch (auth.type)
		{
			case 'Trusted': this.loginTrusted(auth as ServiceTrusted); break;
			case 'OAuth': this.loginOAuth(auth as ServiceOAuth); break;
			case 'Basic': this.loginBasic(auth as ServiceBasic); break;
			case 'LDAP': this.loginLDAP(auth as ServiceLDAP); break;
			default: throw new Error('Unknown authentication service ' + auth.type);
		}
	}

	// login as someone who is already trusted
	private loginTrusted(trusted:ServiceTrusted):void
	{
		// TODO: make a facade for entering details like name & email, and make sure it gets stashed in the cookies

		let session:LoginSession =
		{
			'type': trusted.type,
			'curatorID': trusted.prefix + 'anyone',
			'status': trusted.status,
			'serviceName': trusted.name,
			'accessToken': 'arbitrary',
			'userID': 'anyone',
			'userName': 'Trusted',
			'email': '',
		};
		this.authenticated(session);
	}

	private resetBasicAuthentication(resetkey:string):void
	{
		let prefix = resetkey.split(':')[0] + ':';
		let auth:ServiceBasic = null;
		for (let a of this.serviceslist)
			if (a.prefix == prefix) auth = a;
		if (auth == null) return; // ignore key if authentication not valid

		this.loginBasic(auth, resetkey);
	}

	// login using username / password
	private loginBasic(auth:ServiceBasic, resetkey?:string):void
	{
		this.loginUsingForm(restBaseURL + '/authentication/basiclogin.jsp', auth, resetkey);
	}

	// login using username / password
	private loginLDAP(auth:ServiceLDAP):void
	{
		this.loginUsingForm(restBaseURL + '/authentication/ldaplogin.jsp', auth);
	}

	private loginUsingForm(url:string, auth:ServiceCommon, resetkey?:string):void
	{
		let array = new Uint32Array(3);
		window.crypto.getRandomValues(array);
		this.state = auth.name + ':' + array.toString().replace(/,/g, '');
		let wparam = this.popupParameter();

		url = url + '?state=' + encodeURIComponent(this.state);
		if (resetkey) url = url + '&resetkey=' + resetkey;
		this.signinWindow = window.open(url, 'SignIn', wparam);
		if (this.signinWindow == null)
		{
			alert('Pop-up Blocker is enabled! Please add this site to your exception list.');
			return;
		}
		setTimeout(() => this.checkLoginStatus(), 500);
		this.signinWindow.focus();
	}

	// initiate the dance for authentication using the OAuth protocol
	private loginOAuth(auth:ServiceOAuth):void
	{
		let returnURL = auth.redirectURI || restBaseURL + '/authenticated.jsp';
		let array = new Uint32Array(3);
		window.crypto.getRandomValues(array);
		this.state = auth.name + ':' + array.toString().replace(/,/g, '');
		let url = auth.url
				+ '?client_id=' + encodeURIComponent(auth.clientID)
				+ '&redirect_uri=' + encodeURIComponent(returnURL)
				+ '&scope=' + encodeURIComponent(auth.scope)
				+ '&state=' + encodeURIComponent(this.state)
				+ '&response_type=' + encodeURIComponent(auth.responseType)
				+ '&display=popup';
		let wparam = this.popupParameter();

		this.signinWindow = window.open(url, 'SignIn', wparam);
		setTimeout(() => this.checkLoginStatus(), 500);
		this.signinWindow.focus();
	}

	// centers popup over window
	private popupParameter():string
	{
		let w = 780; let h = 410;
		let left = window.screenX + window.outerWidth / 2 - w / 2;
		let top = window.screenY + window.outerHeight / 2 - h / 2;
		let wparam = 'width=' + w + ',height=' + h;
		wparam = wparam + ',left=' + left + ',top=' + top;
		wparam = wparam + ',toolbar=0,scrollbars=0,status=0,resizable=0,location=0,menuBar=0';
		return wparam;
	}

	// timed polling to see if the login has happened yet
	private checkLoginStatus():void
	{
		if (!this.signinWindow.closed)
		{
			setTimeout(() => this.checkLoginStatus(), 500);
			return;
		}
		if (localStorage.getItem('oauth'))
		{
			let result = JSON.parse(localStorage.getItem('oauth'));
			localStorage.removeItem('oauth');
			if (result.state == this.state)
			{
				this.authenticated(result);
				return;
			}
			// in case user is already logged in skip displaying the alert
			if (Authentication.isLoggedIn()) return;
		}
		alert('Authentication unsuccessful.');
	}

	// received the approval message from the server, with login details: respond accordingly
	private authenticated(session:LoginSession):void
	{
		Authentication.writeSession(session);
		this.renderAuthentication();
	}

	// disconnect the current login
	private logout():void
	{
		Authentication.removeSession();
		this.renderAuthentication();
	}

	// return true if the user can modify his profile and false otherwise
	private isEditable(session:LoginSession):boolean
	{
		let canEdit = !session.curatorID.startsWith('trusted');
		return canEdit;
	}

	// writes the given session info into the cookie store
	private static writeSession(session:LoginSession):void
	{
		// it's sufficient to keep the accessToken for reauthentication in the cookies
		// this information is no longer required on JavaScript
		if (session.accessToken)
		{
			Authentication.set('accessToken', session.accessToken);
			delete session.accessToken;
		}

		Authentication.set('type', session.type);
		Authentication.set('curatorID', session.curatorID);
		Authentication.set('status', session.status);
		Authentication.set('serviceName', session.serviceName);
		Authentication.set('userID', session.userID);
		Authentication.set('userName', session.userName);
		Authentication.set('email', session.email);
	}

	// figures out what login information is encoded in the cookies; null means nothing/invalid; note that the login
	// is not necessarily valid: it may have gone stale, or been inserted by some nefarious means
	private static readSession():LoginSession
	{
		let session:LoginSession =
		{
			'type': this.get('type'),
			'curatorID': this.get('curatorID'),
			'status': this.get('status'),
			'serviceName': this.get('serviceName'),
			'userID': this.get('userID'),
			'userName': this.get('userName'),
			'email': this.get('email'),
		};
		if (!session.curatorID || !session.serviceName || !session.userID) return null;
		return session;
	}

	// clear login information
	private static removeSession():void
	{
		Authentication.remove('accessToken');
		Authentication.remove('type');
		Authentication.remove('curatorID');
		Authentication.remove('status');
		Authentication.remove('serviceName');
		Authentication.remove('userID');
		Authentication.remove('userName');
		Authentication.remove('email');
	}

	// one-at-a-time cookie manipulation
	private static get(key:string):string
	{
		let value = '; ' + document.cookie;
		let parts = value.split('; ' + key + '=');
		if (parts.length == 2) return decodeURIComponent(parts.pop().split(';').shift());
		return null;
	}
	private static set(key:string, val:string):void
	{
		document.cookie = key + '=' + encodeURIComponent(val);
	}
	private static remove(key:string):void
	{
		document.cookie = key + '=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
	}
}

/* EOF */ }

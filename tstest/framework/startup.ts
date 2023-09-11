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

///<reference path='../../../WebMolKit/src/decl/corrections.d.ts'/>
///<reference path='../../../WebMolKit/src/decl/jquery/index.d.ts'/>
///<reference path='../../../WebMolKit/src/util/util.ts'/>

///<reference path='../../ts/support/util.ts'/>

///<reference path='ExecuteTests.ts'/>

///<reference path='../decl/node.d.ts'/>

import bae = BioAssayExpress;

namespace BAETest /* BOF */ {

//$ = (window as any)['$'] || require('./jquery.js');
$ = require('./jquery.js');

export let ON_DESKTOP = false; // by default assume it's running in a regular web page; switch to true if it's the locally executed window version

/*
	Startup: gets the ball rolling, and provide some high level window handling.
*/

// runs the tests, based on the assumption that there is a browser window in play
export function runTestsWeb(resURL:string, rootID:string):void
{
	let root = $('#' + rootID);
	let divMain = $('<div/>').appendTo(root).css({'padding': '0.5em'});

	ON_DESKTOP = true;

	const process = require('process');
	process.env['ELECTRON_DISABLE_SECURITY_WARNINGS'] = true;

	new ExecuteTestsWeb(divMain).run();
}

// run the tests in console mode, assuming no browser; returns true if the tests all succeeded
export function runTestsConsole(callback:(success:boolean) => void):void
{
	(async () =>
	{
		let exec = new ExecuteTestsConsole();
		await exec.run();
		callback(exec.numFailed == 0);
	})();
}

module.exports =
{
	'runTestsWeb': runTestsWeb,
	'runTestsConsole': runTestsConsole,
};

/* EOF */ }

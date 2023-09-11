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

try {require('source-map-support').install()} catch (ex) { }

process.env['ELECTRON_DISABLE_SECURITY_WARNINGS'] = true;

// dig through command line parameters
let argv = process.argv.slice(0);

let useConsole = false;

while (argv.length > 0)
{
	let arg = argv.shift();
	if (arg.endsWith('app/main.js')) break; // anything after this is fair game
}
for (let n = 0; n < argv.length; n++)
{
	if (argv[n] == '--console') useConsole = true;
	// (... consider other options...)
}

if (useConsole)
{
	const bae = require('./bae-test-build.js');
	bae.runTestsConsole((success) => process.exit(success ? 0 : 1));
	return;
}

const electron = require('electron');
const {app, BrowserWindow} = electron;

app.on('window-all-closed', function()
{
	if (process.platform != 'darwin') app.quit();
});

const WEBPREF = {'nodeIntegration': true};
const BROWSER_PARAMS = {'width': 900, 'height': 800, 'webPreferences': WEBPREF};
const INIT_URL = 'file://' + __dirname + '/index.html';

let mainWindows = [];

app.on('window-all-closed', function()
{
	/*if (process.platform != 'darwin')*/ app.quit();
});

app.on('ready', function()
{
	let wnd = new BrowserWindow(BROWSER_PARAMS);
	let url = INIT_URL;
	wnd.loadURL(url);
	wnd.on('closed', () =>
	{
		wnd.removeAllListeners();
		for (let n = 0; n < mainWindows.length; n++) if (mainWindows[n] === wnd) {mainWindows.splice(n, 1); break;}
	});
});

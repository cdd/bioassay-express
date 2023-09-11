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
	Aesthetic settings.
*/

export class Theme
{
	// normal foreground colour (i.e. black, almost)
	public static NORMAL_RGB = 0x313AA4;
	public static NORMAL_HTML = '#313A44';

	// strong action colour: deep blue that is intended to catch the eye when used as a background, and for a slight contrast when
	// used as foreground
	public static STRONG_RGB = 0x1362B3;
	public static STRONG_HTML = '#1362B3';

	// weak action colour: used as a background for less prominent tools (particularly buttons)
	public static WEAK_RGB = 0xE6EDF2;
	public static WEAK_HTML = '#E6EDF2';

	// midpoint between strong & weak
	public static STRONGWEAK_RGB = 0x7DA8D3;
	public static STRONGWEAK_HTML = '#7DA8D3';

	// issue colour: used to call attention to something that is out of place
	public static ISSUE_RGB = 0xFCDB86;
	public static ISSUE_HTML = '#FCDB86';
	
	// time to wait before popover, for most of the controls
	public static POPOVER_WAIT = 500;
	public static POPOVER_DELAY = {'show': Theme.POPOVER_WAIT, 'hide': 0};
}

/* EOF */ }
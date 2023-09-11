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

/*
	Mock WEB: provide some definitions that usually come in through the web page. These go into the raw namespace,
	to simulate the web-style invocation.
*/

let PREFIX_MAP =
{
	'bao:': 'http://www.bioassayontology.org/bao#',
	'bat:': 'http://www.bioassayontology.org/bat#',
	'bas:': 'http://www.bioassayontology.org/bas#',
	'bae:': 'http://www.bioassayexpress.org/bae#',
	'src:': 'http://www.bioassayexpress.org/sources#',
	'obo:': 'http://purl.obolibrary.org/obo/',
	'rdf:': 'http://www.w3.org/1999/02/22-rdf-syntax-ns#',
	'rdfs:': 'http://www.w3.org/2000/01/rdf-schema#',
	'xsd:': 'http://www.w3.org/2001/XMLSchema#',
	'owl:': 'http://www.w3.org/2002/07/owl#',
	'uo:': 'http://purl.org/obo/owl/UO#',
	'dto:': 'http://www.drugtargetontology.org/dto/',
	'geneid:': 'http://www.bioassayontology.org/geneid#',
	'taxon:': 'http://www.bioassayontology.org/taxon#',
	'protein:': 'http://www.bioassayontology.org/protein#',
	'prov:': 'http://www.w3.org/ns/prov#',
	'az:': 'http://rdf.astrazeneca.com/bae#',
	'user:': 'http://www.bioassayexpress.com/bao_provisional#'
};

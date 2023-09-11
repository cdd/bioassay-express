# BioAssay Express

The _BioAssay Express_ (BAE) project allows bioassay protocols to be conveniently annotated using semantic web terminology, from a variety of ontologies (e.g. BAO, DTO, CLO, GO, etc). It provides a web-based interface that makes selection of terms straightforward, and leverages natural language models to try and propose the most likely options. Also provides hosting of data from PubChem assays, and features for searching the assays.

See the [Production Server](https://www.bioassayexpress.com) for an example of it running in real life.

## Requisites

The primary codebase is written in **Java 8**. Web content uses **HTML5**, and works with evergreen browsers (ca. 2016). Development is done with **Eclipse EE** and **Visual Studio Code**. Production builds use **Ant**. The server runs with **Apache Tomcat 8**, and uses **MongoDB** for its content. Supporting _JavaScript_ libraries are written in **TypeScript** and cross-compiled. All of these tools should be installed in their usual way. The full software stack has been tested on Linux and macOS, and should not be sensitive to minor versions.

## Docker

Probably the easiest way to get running with a local instance of _BioAssay Express_ is to use Docker.

* download the contents of [BioAssay Template](https://github.com/cdd/bioassay-template) into a directory with the name `bax` (the name is important), which we will refer to as `${GIT}/bax`: this is a dependency that is referenced by relative paths
* download the contents of [BioAssay Express](https://github.com/cdd/bioassay-express) into a directory with the name `bae`, which we will refer to as `${GIT}/bae`
* make sure that `docker` is installed on your computer
* make sure that Java JDK 16 or later is installed, as well as Node Package Manager: to update the npm libraries, change to `${BAE}/bae/docker` and run `npm i`
* change to the _docker_ directory `${BAE}/bae/docker`
* run `fetch_apache.sh` to make sure a local copy of the Apache Tomcat bundle is available
* ensure that Apache Ant is installed and executable on the command line as `ant`
* run `make pkg` to compile the whole _BioAssay Express_ project, and prepare a customized bundle for this purpose
* run `make build` to setup the docker container and copy over all the necessary files
* start the docker container with `make start`

Note that the docker container contains templates, vocabulary and assays: the assay data that is uploaded into the BAE instance on starting is the publicly available curated content from the active [public server](https://beta.bioassayexpress.com), and can be browsed and manipulated locally.

## Setup

Several steps are necessary before BAE can be run on a development or production environment. This assumes a Unix-like environment.

### Directories

The default project initialisation is setup to use `/opt/bae` as the base directory for all of the supporting files, which are not part of the build project. The base directory can be changed using the `-D` parameter to specify the location of the `config.json` file, e.g. `-Dbae.cfg=/somewhere/else/config.json`. The base directory is taken to be the location of the specified configuration file.

There are a number of files and subdirectories for various resources, listed below as their default locations (these can be configured to point to different locations):

* `/opt/bae/cfg/config.json`: general configuration; this file is parsed at the very beginning of the service lifetime.

* `/opt/bae/cfg/authentication.json`: a list of services that can be used to authenticate a user, which is required before making assay submissions.

* `/opt/bae/cfg/identifier.json`: a list of 'unique identifier' prefixes that are available (e.g. PubChem's AID number).

* `/opt/bae/cfg/vocab.dump`: a single file that contains extracts from all of the ontologies and templates (above), in a tree format that can be spooled into memory far more quickly than semantic triples can be loaded, which greatly reduces the time needed to start the service; it is regenerated with a command line invocation.

* `/opt/bae/cfg/template/`: holds any bioassay template schema files that are used by the service; at minimum this is the `schema.json` file, which is nominally the _Common Assay Template_ (CAT), which is the lowest common denominator for annotating assays to a moderate level of granularity.

* `/opt/bae/cfg/nlp/`: natural language processing libraries are stored here; they are taken from the default settings for the **OpenNLP** project which is linked into the service; these files will be updated rarely, if ever

* `/opt/bae/assays/`: assays that have been downloaded from _PubChem_ are kept here; this directory can be left empty initially, as it will automatically populate itself with the latest content from the source; does not need to be present if automatic downloading from _PubChem_ is not used

* `/opt/bae/cfg/custom/`: override files, which take precedence over the default HTML resources, allowing configuration of front page content, icons, etc.


### Configuration

The main configuration file is `/opt/bae/cfg/config.json` (or an alternate file if specified on the command line when starting up Tomcat). This is an ordinary JSON file that consists of a top-level dictionary, divided up into sections listed below. Note that many of the values refer to files or directories, and these are all relative to the path where the configuration file is located.

* `"database"`: allows the connectivity to the _MongoDB_ database server to be specified; standard factory settings are typically used (localhost, default port, no security - presumed to be firewalled)
	* `"host"`: hostname of server (*null* for localhost)
	* `"port"`: access port of server (0 means default)
	* `"name"`: database name within server (recommended `bae` as the default choice)
	* `"user"`: user name for database access (optional)
	* `"password"`: password for database access (optional)

* `"modules"`: optional extra functionality to enable
	* `"vault"`: synchronization with *CDD Vault* content (see later)
	* `"pubchem"`: downloading content from *PubChem* (see later)
 
* `"authentication"`: indicate source of methods for authenticating users
	* `"filename"`: references a list of authentication options, if any (see later)

* `"identifier"`: indicate source of unique identifiers
	* `"filename"`: references a list of unique identifier options (see later)

* `"templates"`: provide assay templates needed for annotation and analysis
	* `"directory"`: the template directory which contains at least one template schema, with the suffix `.ttl`
	* `"files"`: a list of template filenames within the above directory; this is optional, since all valid schema files are loaded and used, but this parameter allows the order to be specified; by default the _Common Assay Template_ (`schema.ttl`) is at the top of the list

* `"forms"`: provide forms for data entry rendering (optional)
	* `"directory"`: the directory which contains data entry forms
	* `"files"`: a data entry forms within the above directory; only files specified in this list are included

* `"transliterate"`: provide templates for auto-generation of assay descriptions
	* `"directory"`: the directory which contains data entry forms
	* `"files"`: templates within the above directory; only files specified in this list are included

* `"schema"`: the ontology hierarchies used by the template(s) are preprocessed and stored in a binary file which is much smaller and quicker to load than the original ontology source files; assembling this file is done using the schema editor
	* `"filename"`: specifies the compiled vocabulary file (typically called `vocab.dump`)

* `"nlp"`: several instruction files are needed to support the natural language processing functionality
	* `"directory"`: the location of the files

* `"provisional"`: the default prefix for newly proposed ontology terms
	* `"baseURI"`: baseline prefix (e.g. a value of `http://www.bioassayexpress.org/bae#` might be prepended to `BAE_0000001`, etc.)

* `"customWeb"`: an optional directory that mirrors the default web bundle (see below)
	* `"directory"`: the location of the override files

* `"baseURL"`: an optional common URL in case the default determination fails on the server

* `"production"`: set this flag to true on production systems for increased security

#### Authentication

The BAE project currently has no access control of any kind with regard to reading content, but it does provide a limited ability to gate the submission of data. In order to be considered for having write access, a user must authenticate by one of the methods listed in the authentication file. The authentication configuration file (typically `authentication.json`) is a JSON array that can be empty, or it can have any number of options. The absence of authentication options means that there is no write permission of any kind.

For internal deployments where all users are trusted and there is no desire to trace changes supplied by individual users, there is a special category type called `Trusted`. To setup the authentication in this way, the file should contain:

	[
		{
			"name": "Trusted",
			"prefix": "trusted:",
			"type": "Trusted",
			"status": "admin"
		}
	]

For proper authentication, *OAuth* is the method of choice. For example, specifying _Open Researcher Contributor ID_ (ORCID) as the identifier technique:

	{
		"name": "ORCID",
		"prefix": "orcid:",
		"type": "OAuth",
		"authURL": "http://orcid.org/oauth/authorize",
		"tokenURL": "https://orcid.org/oauth/token",
		"scope": "/authenticate",
		"responseType": "code",
		"clientID": "APP-????",
		"clientSecret": "{wouldn't you like to know}",
		"userID": "orcid",
		"userName": "name"
	}

#### Unique Identifiers

The BAE assigns an arbitrary sequence identifier to each new assay, but most assays are associated with an external reference database of some kind. These are called *Unique ID*s, and are expected to be globally disambiguated. Having a `uniqueID` is recommended but not required. Also despite the name, the BAE does not enforce uniqueness: this characteristic is expected to hold true _externally_, so reusing the same `uniqueID` within BAE is permitted, but not recommended.

The configuration file (typically `identifier.json`) is a simple JSON array containing any number of options, e.g.

	[
		{
			"name": "PubChem Assay ID",
			"shortName": "AID",
			"prefix": "pubchemAID:",
			"baseURL": "https://pubchem.ncbi.nlm.nih.gov/bioassay/"
		},
		{
			"name": "Vault Protocol ID",
			"shortName": "VPID",
			"prefix": "vaultPID:",
			"baseURL": "https://app.collaborativedrug.com/vaults/1234/protocols/"
		}
	]

In each case the `name` is used for long-form display, while the `shortName` is used where space is at a premium, such as for column headings. The `prefix` is the most important parameter, because it must not be changed after data creation, since this is used within the database representation. It should be followed by a colon, such that the prefixes are completely disambiguated from each other. The `baseURL` is used to compose a link to the original data.

Note that the two examples given above - for _PubChem_ and _Vault_ - use prefixes that have internal meaning to optional modules.

#### Custom Web Content

The BAE installation bundle contains its own web directory which is served up to the user. The directory includes HTML-like JSP files, images, CSS, JavaScript, etc. The custom web content directory can be used to override any of the static files: the server will search this directory and provide the custom version in preference to the version that was provided as part of the bundle.

Assuming that the custom directory is `/opt/bae/cfg/custom`, files of particular interest are:

* `/opt/bae/cfg/custom/index.snippet`: a section of HTML that is inserted into the main part of the home page, which is an opportunity to describe the project within the deployment context

* `/opt/bae/cfg/custom/images/MainIcon.png`: the browser tab icon, which can be modified to suit

#### Vault Synchronization

To connect BAE to *CDD Vault*, configure the `vault` module with the appropriate login identifiers and an API key:

	"modules":
	{
		"vault": 
		{
			"vaultIDList": [1234],
			"apiKey": "{wouldn't you like to know}",
			"propertyMap":
			{
				"field": "http://www.bioassayontology.org/bao#BAX_0000015",
				"units": "http://www.bioassayontology.org/bao#BAO_0002874",
				"operator": "http://www.bioassayontology.org/bao#BAX_0000016",
				"threshold": "http://www.bioassayontology.org/bao#BAO_0002916"
			},
			"unitsMap":
			{
				"M": "http://purl.obolibrary.org/obo/UO_0000062",
				"mM": "http://purl.obolibrary.org/obo/UO_0000063",
				"uM": "http://purl.obolibrary.org/obo/UO_0000064",
				"nM": "http://purl.obolibrary.org/obo/UO_0000065",
				"pM": "http://purl.obolibrary.org/obo/UO_0000066",
				"logM": "http://www.bioassayontology.org/bao#BAO_0000101",
				"perM": "http://www.bioassayontology.org/bao#BAO_0000102",
				"%": "http://purl.obolibrary.org/obo/UO_0000187"
			},
			"operatorMap":
			{
				"=": "http://purl.obolibrary.org/obo/GENEPIO_0001004",
				">": "http://purl.obolibrary.org/obo/GENEPIO_0001006",
				"<": "http://purl.obolibrary.org/obo/GENEPIO_0001002",
				">=": "http://purl.obolibrary.org/obo/GENEPIO_0001005",
				"<=": "http://purl.obolibrary.org/obo/GENEPIO_0001003"
			}
		}
	}


When this module is running, _Vault_ will be periodically polled for new content, which will be integrated into the BAE instance. Both assay protocols and molecular data are imported. The downloader will only process protocols that have at least one *hit condition* defined, and skip any that do not.

Some additional configuration options are required to instruct the mapping between the _Vault_ datastructures and the Common Assay Template.

#### PubChem Downloading

The BAE can be configured to automatically download assays from _PubChem_.

	"modules":
	{
		"pubchem": 
		{
			"assays": true,
			"compounds": true,
			"directory": "assays"
		}
	}

There are two parts: one is downloading of _assays_, which requires a directory, which is used to store *all* of the _PubChem_ assay archives as ZIP files, downloaded from the FTP site. This approach is the lesser evil of various evils. New assays are imported as un-curated.

The second feature involves downloading the _compounds_ that are associated with each _PubChem_ assay. This in turn has two parts: first the measurements are downloaded, and secondly the corresponding chemical structures are obtained. This process is done via the APIs, rather than by storing archives locally. It does take a very long time to download measurements and structures (the baseline set of 3500 annotated assays takes more than a week of background downloading to update all of the measurements and structures).

Either of these features can be switched on or off as a valid use case: obtaining assays but not compounds is a way to disable a resource intensive feature. Downloading compounds but not assays is also valid if the annotated assays are being imported from another instance. The assays themselves are concise, but the compounds are massive.

### Database

The same computer that hosts the Tomcat webserver should also be running MongoDB locally, with default settings. Any recent version should suffice.

NOTE: At the present time, there is no way to specify a different server, or security parameters. For the moment the two hosting scenarios are development and a single production server that only exposes ports 80 & 8080.

The use of MongoDB creates a single database category called `bae` (by default). The appropriate collections and indexes are created automatically whenever the service starts, so there is no need to run any preparation scripts. As long as the database is being stored in a volume that has enough space, no maintenance is required.

### Server

**Apache Tomcat** version 8 should be downloaded and installed. The easiest way to do this is to un-tar the files and make sure the primary directory is `/opt/tomcat`. The service will be instructed to use port 8080, so it can run with regular user permissions.

NOTE: the production server runs the regular **nginx** web server on port 80 with administrator permissions, with a directive to internally re-route certain subdirectories to the **Tomcat** server on port 8080. This is a relatively convenient way to expose a single server:port to the user, while retaining the convenience of updating the main web application server with regular user permissions.

## Build

The BAE project provides all of its own dependencies, except for the [BioAssay Template Editor](https://github.com/cdd/bioassay-template) project, which it leverages for some common functionality. The easiest way to setup a build system is to setup two directories side by side, e.g.

* `~/workspace/bax`: pull from [BioAssay Template Editor](https://github.com/cdd/bioassay-template)
* `~/workspace/bae`: pull from [BioAssay Express](https://github.com/cdd/bioassay-express)

Build the `bax` project first by running **ant** in that directory, then do likewise for `bae`. This will create a `.war` file in the `pkg/` subdirectory (in this example `~/workspace/bae/pkg/BioAssayExpress.war`).

If there are any problems, such as missing files, examine the **ant** build script (`build.xml`) to see what is required.

## Deploy

Deploying the deliverable web archive (`BioAssayExpress.war`) package is a relatively straightforward matter of copying the file to the appropriate directory and restarting the **Tomcat** service. Note that **Apache Tomcat** has a built in feature to automatically detect when a web archive is updated to a newer version, but since the BAE project runs a number of background processing tasks, the automatic restart can get finicky, and so it is better to shut it down manually first. This creates a brief interruption to service.

The post-build server refresh looks thus:

* `/opt/tomcat/bin/shutdown.sh`
* `cp BioAssayExpress.war /opt/tomcat/webapps`
* `/opt/tomcat/bin/startup.sh`
* `tail -n 1000 -f /opt/tomcat/logs/catalina.out`

The last line examines the ongoing log file, and is a good way to make sure that the server was restarted properly.

## Development

The recommended way to work on the BAE project is to use **Eclipse** with the **J2EE** plugins installed. The GitHub repository contains the appropriate configuration files for **Eclipse**. The source is divided up into Java resources of several types (such as servlets, background tasks, RESTful API calls) and Web resources (such as JavaScript, CSS and JSP). The web package can be setup within **Eclipse** so that a development instance of **Tomcat** is used to sync the source code with the local instance.

## Testing

See document [TESTING.md](TESTING.md)

## Maintenance

At the present time, the web interface for BAE is completely read only, which allows it to be deployed on the open internet with no security other than restricting access to a single port. Most of the updating of assay data is done by background tasks that initiate downloads from PubChem. Any user-initiated actions, such as curation of assays, modifying the schema, or updating the service are done on an admin level by manually copying files into the appropriate directories.

## Console

For debugging and ongoing development purposes, the package can be invoked with a command line entrypoint, by treating the web archive bundle just like the constituent Java classes. As long as the service has been deployed in the appropriate **Tomcat** directory (i.e. **Tomcat** has been given a chance to unpack the content into its rightful place), the console can be invoked with the syntax:

* `java -cp /opt/tomcat/webapps/BioAssayExpress/WEB-INF/classes com.cdd.bae.main.Main`

By providing no further parameters, the entrypoint will default to providing a list of options that apply. This technique is often used for low level metrics, temporary experimental code, or for retroactively updating internal data content. Ideally this invocation will never be necessary for the final production version, but in the meanwhile it is useful.


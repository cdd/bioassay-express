# Required modification to setup:
The following requirements are not available on the test server:
```
sudo apt install ant
sudo apt-get install openjfx
export JAVA_HOME=
```
Install node and typescript:
```
sudo apt-get install npm
sudo apt-get install nodejs
sudo npm install -g typescript
sudo ln -s `which nodejs` /usr/bin/node
```

# Installation of BioAssay Express
The following files are required for the installation:

- optbae.zip
- log4j.properties
- curation20170603.zip (timestamp may vary)

In the following it is assumed that these files are in user directory.
**NOTE:**  if `log4j.properties` is not found, the BAE service will fail at start-up;
see `Common.java` for loading details.

## Export master from github
```
mkdir git
cd git
git clone https://github.com/cdd/bioassay-express.git bae
git clone https://github.com/cdd/bioassay-template.git bax
git clone https://github.com/aclarkxyz/web_molkit.git WebMolKit
```

## Compiling bioassay-template (bax, optional)
```
cd bax
ant
```
Use precompiled `pkg/BioAssayTemplate.jar` file.

## Compiling bae
```
cd ~/git/bae
ant
```

## Copy the default configuration
```
sudo mkdir /opt/bae
cd /opt/bae
sudo cp  ~/log4j.properties .
sudo unzip ~/optbae.zip
sudo mkdir assays
```

## Run the junit tests
```
cd ~/git/bae
ant junit
```

## Load the curated assay information
```
cd ~/git/bae
csh baeconsole transfer importassays ~/curation20170603.zip --init /opt/bae
```

## Add the log directory
```
sudo mkdir /opt/bae/logs
sudo chmod 777 /opt/bae/logs
```

## Correct user/group credentials

## Install the war file
```
sudo cp pkg/BioAssayExpress.war /var/lib/tomcat8/webapps
```

## Check authentication settings on mongo DB
- Server must be password protected and `mongod` started with the `--auth` flag 
- provide the user credentials either in the `config.json` file or as environment variables

## Add `baseURI` to `config.json`
To prevent the possibility of Redirect/Phishing via HTTP Host Injection (CDD-01-003 Web) it is recommended to add `baseURI` to `config.json`.

# Access the server
Server installations:

- (private test BioAssay Express)[https://private-test.bioassayexpress.com/]
- (public test BioAssay Express)[https://public-test.bioassayexpress.com/]

# Updating an existing server
If you update the software on an existing server and run into a problem, it is best to clean up the existing installation first.
```
ant clean
```

# Authentication support
Check that the OAuth keys are correct.

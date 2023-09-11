# Deployment WAR

The standard Ant build process makes `BioAssayExpress.war` which is designed to read its configuration from a standard location, e.g. `/opt/bae` where it finds all of its hardwired configuration information. For deploying on remote servers this is not very convenient, so it is possible to use the console to _augment_ the bundle to include configuration information within it. This makes it possible to basically just copy the .war file to the Tomcat `/webapp` directory and let it do its thing.

To make a direct deployable bundle:

`baeconsole deploy pkg/BioAssayExpress.war /where/BioAssayExpress_acme.war /opt/bae-acme`

This assumes that `/opt/bae-acme` has a configuration that is customised for the benefit of Acme Corp. It must also contain a file called `packwar.json` (see example in this directory) which lists the necessary files and directories that need to be added to the .war file. These additional files are embedded in the `opt/` subdirectory within the archive.

Part of the deployment processing is to modify the embedded `web.xml` file in order to specify the location of the configuration file, which is now `$BUNDLE/opt/config.json`, whereby the prefix gets replaced with the deployment location.

The default packing instructions also instruct the embedding of the `log4j.properties` file, which needs to point to a location for writing log files, which is specified as `/var/log/tomcat8`. This assumes that the Tomcat server is already using this directory for its own logs and, as the name suggests, it has to be Tomcat version 8. It should be noted that Tomcat 9 is not compatible with BAE as of May 2021 (primarily due to a new security sandbox featuroid). This is appropriate for a default installation on Ubuntu, e.g. by `apt-get install tomcat8`.

# Other settings

Certain parameters are not appropriate for a preconfigured bundle, such as the MongoDB connection details. These should be overridden using environment variables. For a default installation of Tomcat 8 on Ubuntu, the file `/usr/share/tomcat8/bin/setenv.sh` (which for security reasons should only readable by the Tomcat user) is executed prior to starting up Tomcat. Edit or create this file, and add lines such as:

```
export MONGO_HOST=mongo.acme.com
export MONGO_PORT=27107
export MONGO_NAME=acme_BAE_db
export MONGO_USER=acme_user
export MONGO_PASSWORD=acme_password
```

Note that when the BAE instance starts up under Tomcat, the main Tomcat logfile (`/var/log/tomcat8/catalina.out`) shows the database connection settings, so this is the best way to confirm that the environment variables were read correctly.
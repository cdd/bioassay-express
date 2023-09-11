# Java testing

We rely on JUnit5 for testing.  For our JUnit5 tests to work right, you should take these steps:

1. Install `Ant 1.10.3` or better.
1. Copy the files `lib/junit-platform-commons-1.5.2.jar`, `lib/junit-platform-engine-1.5.2.jar`, `lib/junit-platform-launcher-1.5.2.jar`, and `lib/opentest4j-1.1.1.jar` to `$ANT_HOME/lib`.

## Junit tests
### Run tests using ant
If you want to run the junit tests using ant, you will need to provide the following files to ant at startup:

- junit-platform-commons-<version>.jar
- junit-platform-engine-<version>.jar
- junit-platform-launcher-<version>.jar
- opentest4j-<version>.jar

You can either copy them to the `ANT_HOME/lib` directory or start ant using the command
```
ant junit -lib lib
```
More recent versions of `ant` allow specifying the jar files in the build.xml file. Until, we switch to these use the copy option.

### Run tests from Eclipse
Remember to switch the test runner to `junit 5` if you have existing test configurations.


## Selenium tests
### Requirements
The selenium test require the following setup:

* BAE server running
* one of the supported web drivers

### Configuration
The test configuration can be controlled using system properties (these need to be set on the VM not the application level).

If the server is not running on `http://localhost:8080/bae` use the following system properties:

- BAE server: `-DbaseURL=<server>` (default: `http://localhost:8080/bae`)

### Webdriver

#### Firefox (default)
Install browser and geckodriver (download from `https://github.com/mozilla/geckodriver/releases`)

The Firefox configuration can be modified with the following system properties:

- geckodriver executable: `-DgeckoPath=<path to geckodriver>` (default: `/usr/local/bin`)
- Firefox executable: `-DfirefoxPath=<path to firefox>`

When running from Eclipse, the JUnit _Run_ configuration needs two sections filled out in the **Arguments** tab:

- Program arguments: `-u http://localhost:8080/bae -f /Applications/Firefox.app/Contents/MacOS/firefox -g /opt/bin/geckodriver`
- VM arguments: `-ea -Dwebdriver.gecko.driver=/opt/bin/geckodriver`

The above example is specific to a _macOS_ workstation with _Firefox_ installed in the default system folder, and the `geckodriver` binary installed in `/opt/bin`.

#### Chrome (GUI or headless)
Install browser and chromedriver (download from `http://chromedriver.chromium.org/downloads`)

Switch test to chrome using `-Dchrome` (GUI) or `-Dheadless` (non-GUI). Use the following system properties to modify the configuration if necessary.

- chromedriver executable: `-DchromedriverPath=<path to chromedriver>`
   (default: `/usr/local/bin`)

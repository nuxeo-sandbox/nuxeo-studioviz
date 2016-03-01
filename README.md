# nuxeo-studioviz
===================
<img src="screenshot1.png"/>
This plug-in enables the visualization of the studio configuration based on graphviz.

## List of Items Displayed
There are 3 types of graphs:
- The Data Model: Document Types, Schemas and Facets
- The View for Content Views, Forms Layouts and Tabs related to the Document Types
- The Business Rules showing the relations between User Actions, Automation Chains/Scriptings and Event Handlers

## GraphViz
You need to install GraphViz (http://www.graphviz.org/Download.php) in order to generate the graphics.
- For MacOs: http://www.graphviz.org/Download_macos.php (If you have `brew` installed, you can also `brew install graphviz`
- For Linux: `sudo apt-get install graphviz`
- For Windows: http://www.graphviz.org/Download_windows.php

## Build
### Install dependencies

#### Quick-start (for experienced users)

With Node.js installed, run the following one liner from nuxeo-studioviz-ui:

```sh
npm install -g gulp bower && npm install && bower install
```

#### Prerequisites (for everyone)

Building nuxeo-studioviz-ui requires the following major dependencies:

- Node.js, used to run JavaScript tools from the command line.
- npm, the node package manager, installed with Node.js and used to install Node.js packages.
- gulp, a Node.js-based build tool.
- bower, a Node.js-based package manager used to install front-end packages (like Polymer).

**To install dependencies (from command line):**

1)  Check your Node.js version.

```
node --version
```

The version must be >= 4.2.3.

2)  If you don't have Node.js installed, or you have a lower version, go to [nodejs.org](https://nodejs.org) and click on the big green Install button.

3)  Install `gulp` and `bower`.

```
npm install -g gulp
npm install bower
```

This lets you run `gulp` and `bower` from the command line.

4)  Install the app's local `npm` and `bower` dependencies.

```
cd nuxeo-studioviz/nuxeo-studioviz-ui
npm install
bower install
```

This installs the element sets (Paper, Iron, Platinum) and tools the starter kit requires to build and serve apps.


5)  Build nuxeo-studioviz-operations

Assuming `maven` is correctly setup on your computer go under the nuxeo-studioviz-operations folder and run:

```
mvn install
```

6)  Build, or rebuild everything

Assuming `maven` is correctly setup on your computer go under the nuxeo-studioviz folder and run:

```
mvn -o clean install -DskipCleanCache
```

The Nuxeo Package is at nuxeo-studioviz-mp/target/nuxeo-studioviz-mp-1.0-SNAPSHOT.zip and can be installed on your Nuxeo Server.

**Notice**: Here, we build offline (`-o`) and skip the cleanning of the cache (`-DskipCleanCache`). This saves a _lot_ of time and can be done because we already mde a full build of each part (nuxeo-studioviz-ui and nuxeo-studioviz-operations).

## About Nuxeo

Nuxeo provides a modular, extensible Java-based [open source software platform for enterprise content management](http://www.nuxeo.com/en/products/ep) and packaged applications for [document management](http://www.nuxeo.com/en/products/document-management), [digital asset management](http://www.nuxeo.com/en/products/dam) and [case management](http://www.nuxeo.com/en/products/case-management). Designed by developers for developers, the Nuxeo platform offers a modern architecture, a powerful plug-in model and extensive packaging capabilities for building content applications.

More information at <http://www.nuxeo.com/>

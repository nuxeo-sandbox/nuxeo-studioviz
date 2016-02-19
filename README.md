# nuxeo-studioviz
===================
<img src="screenshot1.png"/>
This plug-in enables the visualization of the studio configuration based on graphviz.

## List of Items Displayed
There are 3 types of graphs:
- The Data Model containing : Document Types, Schemas and Facets
- The View for Content Views, Forms Layouts and Tabs related to the Document Types
- The Business Rules showing the relations between User Actions, Automation Chains/Scriptings and Event Handlers

## GraphViz
You need to install GraphViz (http://www.graphviz.org/Download.php) in order to generate the graphics.


## Build
### Install dependencies

#### Quick-start (for experienced users)

With Node.js installed, run the following one liner from the root of your Polymer Starter Kit download:

```sh
npm install -g gulp bower && npm install && bower install
```

#### Prerequisites (for everyone)

The full starter kit requires the following major dependencies:

- Node.js, used to run JavaScript tools from the command line.
- npm, the node package manager, installed with Node.js and used to install Node.js packages.
- gulp, a Node.js-based build tool.
- bower, a Node.js-based package manager used to install front-end packages (like Polymer).

**To install dependencies: (from command line)**

1)  Check your Node.js version.

```
node --version
```

The version should be at or above 4.2.3.

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

Assuming maven is correctly setup on your computer go under the nuxeo-es-synonyms folder and run:

```
mvn install
```

## About Nuxeo

Nuxeo provides a modular, extensible Java-based [open source software platform for enterprise content management](http://www.nuxeo.com/en/products/ep) and packaged applications for [document management](http://www.nuxeo.com/en/products/document-management), [digital asset management](http://www.nuxeo.com/en/products/dam) and [case management](http://www.nuxeo.com/en/products/case-management). Designed by developers for developers, the Nuxeo platform offers a modern architecture, a powerful plug-in model and extensive packaging capabilities for building content applications.

More information at <http://www.nuxeo.com/>

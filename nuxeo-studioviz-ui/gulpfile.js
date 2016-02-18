/*
 * (C) Copyright 2006-2016 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Contributors:
 *     nuxeo
 */

'use strict';

// Include Gulp & tools we'll use
var gulp = require('gulp');
var $ = require('gulp-load-plugins')();
var del = require('del');
var runSequence = require('run-sequence');
var browserSync = require('browser-sync');
var reload = browserSync.reload;
var merge = require('merge-stream');
var path = require('path');
var fs = require('fs');
var glob = require('glob-all');
var historyApiFallback = require('connect-history-api-fallback');
var packageJson = require('./package.json');
var crypto = require('crypto');

var AUTOPREFIXER_BROWSERS = [
  'ie >= 10',
  'ie_mob >= 10',
  'ff >= 30',
  'chrome >= 34',
  'safari >= 7',
  'opera >= 23',
  'ios >= 7',
  'android >= 4.4',
  'bb >= 10'
];

var APP = 'src/main/app';
var DIST = 'target/classes/nuxeo.war/studioviz';

var pathIfPresent = function(root, subpath) {
  return !subpath ? root : path.join(root, subpath);
};

var appl = function(subpath) {
  return pathIfPresent(APP, subpath);
};

var dist = function(subpath) {
  return pathIfPresent(DIST, subpath);
};

var styleTask = function(stylesPath, srcs) {
  return gulp.src(srcs.map(function(src) {
      return path.join(APP, stylesPath, src);
    }))
    .pipe($.changed(stylesPath, {extension: '.css'}))
    .pipe($.autoprefixer(AUTOPREFIXER_BROWSERS))
    .pipe(gulp.dest('.tmp/' + stylesPath))
    .pipe($.minifyCss())
    .pipe(gulp.dest(dist(stylesPath)))
    .pipe($.size({title: stylesPath}));
};

var imageOptimizeTask = function(src, dest) {
  return gulp.src(src)
    .pipe($.imagemin({
      progressive: true,
      interlaced: true
    }))
    .pipe(gulp.dest(dest))
    .pipe($.size({
      title: 'images'
    }));
};

var optimizeHtmlTask = function(src, dest) {
  var assets = $.useref.assets({
    searchPath: ['.tmp', appl(), dist()]
  });

  return gulp.src(src)
    // Replace path for vulcanized assets
    .pipe($.if('*.html', $.replace('elements/elements.html', 'elements/elements.vulcanized.html')))
    .pipe(assets)
    // Concatenate and minify JavaScript
    .pipe($.if('*.js', $.uglify({preserveComments: 'some'})))
    // Concatenate and minify styles
    // In case you are still using useref build blocks
    .pipe($.if('*.css', $.minifyCss()))
    .pipe(assets.restore())
    .pipe($.useref())
    // Minify any HTML
    .pipe($.if('*.html', $.minifyHtml({
      quotes: true,
      empty: true,
      spare: true
    })))
    // Output files
    .pipe(gulp.dest(dest))
    .pipe($.size({title: 'html'}));
};

// Compile and automatically prefix stylesheets
gulp.task('styles', function() {
  return styleTask('styles', ['**/*.css']);
});

gulp.task('elements', function() {
  return styleTask('elements', ['**/*.css']);
});

// Lint JavaScript
gulp.task('lint', function() {
  return gulp.src([
      appl('scripts/**/*.js'),
      appl('elements/**/*.js'),
      appl('elements/**/*.html')
    ])
    .pipe(reload({stream: true, once: true}))

    // JSCS has not yet a extract option
    .pipe($.if('*.html', $.htmlExtract()))
    .pipe($.jshint())
    .pipe($.jscs())
    .pipe($.jscsStylish.combineWithHintResults())
    .pipe($.jshint.reporter('jshint-stylish'))
    .pipe($.if(!browserSync.active, $.jshint.reporter('fail')));
});

// Optimize images
gulp.task('images', function() {
  return imageOptimizeTask(appl('images/**/*'), dist('images'));
});

// Copy all files at the root level (app)
gulp.task('copy', function() {
  var app = gulp.src([
    appl('*'),
    '!' + appl('test'),
    '!' + appl('cache-config.json')
  ], {
    dot: true
  }).pipe(gulp.dest(dist()));

  var bower = gulp.src([
    'bower_components/**/*'
  ]).pipe(gulp.dest(dist('bower_components')));

  var elements = gulp.src([appl('elements/**/*.html'),
      appl('elements/**/*.css'),
      appl('elements/**/*.js')
    ])
    .pipe(gulp.dest(dist('elements')));

  //var swBootstrap = gulp.src(['bower_components/platinum-sw/bootstrap/*.js'])
  //  .pipe(gulp.dest(dist('elements/bootstrap')));
  //
  //var swToolbox = gulp.src(['bower_components/sw-toolbox/*.js'])
  //  .pipe(gulp.dest(dist('sw-toolbox')));

  var vulcanized = gulp.src([appl('elements/elements.html')])
    .pipe($.rename('elements.vulcanized.html'))
    .pipe(gulp.dest(dist('elements')));

  return merge(app, bower, elements, vulcanized)
    .pipe($.size({title: 'copy'}));
});

// Copy web fonts to dist
gulp.task('fonts', function() {
  return gulp.src([appl('fonts/**')])
    .pipe(gulp.dest(dist('fonts')))
    .pipe($.size({title: 'fonts'}));
});

// Scan your HTML for assets & optimize them
gulp.task('html', function() {
  return optimizeHtmlTask(
    [appl('**/*.html'), '!' + appl('{elements,test}/**/*.html')],
    dist());
});

// Vulcanize granular configuration
gulp.task('vulcanize', function() {
  var DEST_DIR = dist('elements');
  return gulp.src(dist('elements/elements.vulcanized.html'))
    .pipe($.vulcanize({
      stripComments: true,
      inlineCss: true,
      inlineScripts: true
    }))
    //.pipe($.minifyInline())
    .pipe(gulp.dest(DEST_DIR))
    .pipe($.size({title: 'vulcanize'}));
});

// Generate config data for the <sw-precache-cache> element.
// This include a list of files that should be precached, as well as a (hopefully unique) cache
// id that ensure that multiple PSK projects don't share the same Cache Storage.
// This task does not run by default, but if you are interested in using service worker caching
// in your project, please enable it within the 'default' task.
// See https://github.com/PolymerElements/polymer-starter-kit#enable-service-worker-support
// for more context.
gulp.task('cache-config', function(callback) {
  var dir = dist();
  var config = {
    cacheId: packageJson.name || path.basename(__dirname),
    disabled: false
  };

  glob([
    'index.html',
    './',
    'bower_components/webcomponentsjs/webcomponents-lite.min.js',
    '{elements,scripts,styles}/**/*.*'
  ], {
    cwd: dir
  }, function(error, files) {
    if (error) {
      callback(error);
    } else {
      config.precache = files;

      var md5 = crypto.createHash('md5');
      md5.update(JSON.stringify(config.precache));
      config.precacheFingerprint = md5.digest('hex');

      var configPath = path.join(dir, 'cache-config.json');
      fs.writeFile(configPath, JSON.stringify(config), callback);
    }
  });
});

// Clean output directory
gulp.task('clean', function() {
  return del(['.tmp', dist()]);
});

// Watch files for changes & reload
gulp.task('serve', ['styles', 'elements', 'images'], function() {

  // setup our local proxy
  var proxyOptions = require('url').parse('http://localhost:8080/nuxeo');
  proxyOptions.route = '/nuxeo';

  browserSync({
    port: 5000,
    notify: false,
    logPrefix: 'PSK',
    snippetOptions: {
      rule: {
        match: '<span id="browser-sync-binding"></span>',
        fn: function(snippet) {
          return snippet;
        }
      }
    },
    // Run as an https by uncommenting 'https: true'
    // Note: this uses an unsigned certificate which on first access
    //       will present a certificate warning in the browser.
    // https: true,
    server: {
      baseDir: ['.tmp', appl()],
      middleware: [historyApiFallback(), require('proxy-middleware')(proxyOptions)],
      routes: {
        '/bower_components': 'bower_components'
      }
    }
  });

  gulp.watch([appl('**/*.html')], reload);
  gulp.watch([appl('styles/**/*.css')], ['styles', reload]);
  gulp.watch([appl('elements/**/*.css')], ['elements', reload]);
  gulp.watch([appl('{scripts,elements}/**/{*.js,*.html}')], ['lint']);
  gulp.watch([appl('images/**/*')], reload);
});

// Build and serve the output from the dist build
gulp.task('serve:dist', ['default'], function() {
  browserSync({
    port: 5001,
    notify: false,
    logPrefix: 'PSK',
    snippetOptions: {
      rule: {
        match: '<span id="browser-sync-binding"></span>',
        fn: function(snippet) {
          return snippet;
        }
      }
    },
    // Run as an https by uncommenting 'https: true'
    // Note: this uses an unsigned certificate which on first access
    //       will present a certificate warning in the browser.
    // https: true,
    server: dist(),
    middleware: [historyApiFallback()]
  });
});

// Build production files, the default task
gulp.task('default', ['clean'], function(cb) {
  // Uncomment 'cache-config' if you are going to use service workers.
  runSequence(
    ['copy', 'styles'],
    'elements', ['images', 'fonts', 'html'],
    'vulcanize', // 'cache-config',
    cb);
});

// Load tasks for web-component-tester
// Adds tasks for `gulp test:local` and `gulp test:remote`
require('web-component-tester').gulp.init(gulp);

// Load custom tasks from the `tasks` directory
try {require('require-dir')('tasks');} catch (err) {}

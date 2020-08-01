#!/usr/bin/env node

/*
 * A hook to add resources class (R.java) import to Android classes which uses it.
 */

const fs = require('fs'),
    path = require('path');

const PACKAGE_RE = /package ([^;]+);/;
const R_RE = /[^\.\w]R\./;
const BUILDCONFIG_RE = /[^\.\w]BuildConfig\./;
const MAINACT_RE = /[^\.\w]MainActivity\./;


// Adapted from 
// https://stackoverflow.com/a/5827895
// Using this and not one of the wrapper modules
// e.g. `file` or `node-dir` because I don't see an obvious way to add
// javascript modules using plugin.xml, and this uses only standard modules

var walk = function(ctx, dir, done) {
  let results = [];

  fs.readdir(dir, function(err, list) {
    if (err) return done(err);
    var pending = list.length;
    if (!pending) return done(null, results);
    list.forEach(function(file) {
      file = path.resolve(dir, file);
      fs.stat(file, function(err, stat) {
        if (stat && stat.isDirectory()) {
          walk(ctx, file, function(err, res) {
            results = results.concat(res);
            if (!--pending) done(null, results);
          });
        } else {
          results.push(file);
          if (!--pending) done(null, results);
        }
      });
    });
  });
}

module.exports = function (ctx) {
    // If Android platform is not installed, don't even execute
    if (ctx.opts.cordova.platforms.indexOf('android') < 0)
        return;

    const platformSourcesRoot = path.join(ctx.opts.projectRoot, 'platforms/android/app/src/main/java/');
    const pluginSourcesRoot = path.join(ctx.opts.plugin.dir, 'src/android');

    const androidPluginsData = JSON.parse(fs.readFileSync(path.join(ctx.opts.projectRoot, 'plugins', 'android.json'), 'utf8'));
    const appPackage = androidPluginsData.installed_plugins[ctx.opts.plugin.id]['PACKAGE_NAME'];

    walk(ctx, pluginSourcesRoot, function (err, files) {
        console.log("walk callback with files = "+files);
        if (err) {
            console.error('Error when reading file:', err)
            return
        }

        var deferrals = [];

        files.filter(function (file) { return path.extname(file) === '.java'; })
            .forEach(function (file) {
                const cp = new Promise(function(resolve, reject) {
                    // console.log("Considering file "+file);
                    const filename = path.basename(file);
                    // console.log("basename "+filename);
                    // var file = path.join(pluginSourcesRoot, filename);
                    // console.log("newfile"+file);
                    fs.readFile(file, 'utf-8', function (err, contents) {
                        if (err) {
                            console.error('Error when reading file:', err)
                            reject();
                        }

                        if (contents.match(R_RE) || contents.match(BUILDCONFIG_RE) || contents.match(MAINACT_RE)) {
                            console.log('file '+filename+' needs to be rewritten, checking package');
                            const packages = contents.match(PACKAGE_RE);
                            if (packages.length > 2) {
                                console.error('Java source files must have only one package, found ', packages.length);
                                reject();
                            }

                            const pkg = packages[1];
                            console.log('Handling package:', pkg);
                            try {
                                const sourceFile = path.join(platformSourcesRoot, pkg.replace(/\./g, '/'), filename)
                                console.log('sourceFile:', sourceFile);
                                if (!fs.existsSync(sourceFile)) 
                                    throw 'Can\'t find file in installed platform directory: "' + sourceFile + '".';

                                const sourceFileContents = fs.readFileSync(sourceFile, 'utf8');
                                if (!sourceFileContents) 
                                    throw 'Can\'t read file contents.';

                                let newContents = sourceFileContents;

                                if (contents.match(R_RE)) {
                                    newContents = sourceFileContents
                                        .replace(/(import ([^;]+).R;)/g, '')
                                        .replace(/(package ([^;]+);)/g, '$1 \n// Auto fixed by post-plugin hook \nimport ' + appPackage + '.R;');
                                }

                                // replace BuildConfig as well
                                if (contents.match(BUILDCONFIG_RE)) {
                                    newContents = newContents
                                        .replace(/(import ([^;]+).BuildConfig;)/g, '')
                                        .replace(/(package ([^;]+);)/g, '$1 \n// Auto fixed by post-plugin hook \nimport ' + appPackage + '.BuildConfig;');
                                }

                                // replace MainActivity as well
                                if (contents.match(MAINACT_RE)) {
                                    newContents = newContents
                                        .replace(/(import ([^;]+).MainActivity;)/g, '')
                                        .replace(/(package ([^;]+);)/g, '$1 \n// Auto fixed by post-plugin hook \nimport ' + appPackage + '.MainActivity;');
                                }

                                fs.writeFileSync(sourceFile, newContents, 'utf8');
                                resolve();
                            }
                            catch (ex) {
                                console.log('Could not add import to "' +  filename + '" using package "' + package + '". ' + ex);
                                reject();
                            }
                            // we should never really get here because we return
                            // from both the try and the catch blocks. But in case we do,
                            // let's reject so we can debug
                            reject();
                        } else {
                            // the file had no BuildConfig or R dependencies, no need
                            // to rewrite it. We can potentially get rid of this check
                            // since we re-check for the imports before re-writing them
                            // but it avoid unnecessary file rewrites, so we retain
                            // it for now
                            resolve();
                        }
                    });
                });

                deferrals.push(cp);
            });

        Promise.all(deferrals)
            .then(function() {
                console.log('Done with the hook!');
            })
    });
}

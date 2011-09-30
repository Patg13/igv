/*
 * Copyright (c) 2007-2011 by The Broad Institute of MIT and Harvard.  All Rights Reserved.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 *
 * THE SOFTWARE IS PROVIDED "AS IS." THE BROAD AND MIT MAKE NO REPRESENTATIONS OR
 * WARRANTES OF ANY KIND CONCERNING THE SOFTWARE, EXPRESS OR IMPLIED, INCLUDING,
 * WITHOUT LIMITATION, WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE, NONINFRINGEMENT, OR THE ABSENCE OF LATENT OR OTHER DEFECTS, WHETHER
 * OR NOT DISCOVERABLE.  IN NO EVENT SHALL THE BROAD OR MIT, OR THEIR RESPECTIVE
 * TRUSTEES, DIRECTORS, OFFICERS, EMPLOYEES, AND AFFILIATES BE LIABLE FOR ANY DAMAGES
 * OF ANY KIND, INCLUDING, WITHOUT LIMITATION, INCIDENTAL OR CONSEQUENTIAL DAMAGES,
 * ECONOMIC DAMAGES OR INJURY TO PROPERTY AND LOST PROFITS, REGARDLESS OF WHETHER
 * THE BROAD OR MIT SHALL BE ADVISED, SHALL HAVE OTHER REASON TO KNOW, OR IN FACT
 * SHALL KNOW OF THE POSSIBILITY OF THE FOREGOING.
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.broad.igv;

import org.apache.log4j.Logger;
import org.broad.igv.util.ParsingUtils;

import java.io.*;
import java.util.Hashtable;
import java.util.Map;

/**
 * An alternative to the preference API.  Introduced to work around limits on
 * string length in the default Linux implementation.
 * of Preferences
 *
 * @author jrobinso
 */
public class IGVPreferences {

    private static Logger log = Logger.getLogger(IGVPreferences.class);

    /**
     * Cache for preferences which should persist for this session only
     */
    static Hashtable<String, String> sessionCache = new Hashtable();

    static Hashtable<String, String> userPreferences = null;

    public void put(String key, String value) {

        // Remove from session only, explicitly setting this overrides
        sessionCache.remove(key);

        if (userPreferences == null) {
            loadUserPreferences();
        }
        if (value == null) {
            userPreferences.remove(key);
        } else {
            userPreferences.put(key, value);
        }
        storePreferences();
    }

    public void putOverride(String key, String value) {
        sessionCache.put(key, value);
    }

    public String get(String key, String defaultValue) {
        if (sessionCache.containsKey(key)) {
            return sessionCache.get(key);
        }
        String val = get(key);
        return val == null ? defaultValue : val;
    }


    private String get(String key) {
        if (userPreferences == null) {
            loadUserPreferences();
        }
        return userPreferences.get(key);
    }

    public void remove(String key) {
        sessionCache.remove(key);
        userPreferences.remove(key);
        storePreferences();
    }

    public void clear() {
        userPreferences.clear();
        storePreferences();

    }

    /**
     * Load user preferences.
     */
    private synchronized void loadUserPreferences() {
        userPreferences = new Hashtable();
        File rootDir = Globals.getIgvDirectory();
        if (!rootDir.exists()) {
            rootDir.mkdir();
        }
        File prefFile = new File(rootDir, "prefs.properties");

        if (prefFile.exists()) {
            String prefFileName = prefFile.getAbsolutePath();
            load(prefFileName, false);
        }
    }

    /**
     * Load an override preference file.  These preferences to not persist as user preferences.
     *
     * @param path
     */
    public void loadOverrides(String path) {
        load(path, true);
    }

    private void load(String prefFileName, boolean override) {
        BufferedReader reader = null;
        try {
            reader = ParsingUtils.openBufferedReader(prefFileName);
            String nextLine = null;
            while ((nextLine = reader.readLine()) != null) {
                int idx = nextLine.indexOf('=');
                if (idx > 0) {
                    String key = nextLine.substring(0, idx);
                    String value = nextLine.substring(idx + 1);
                    if (!value.equals("null")) {
                        if (override) {
                            log.info("Overriding preference: " + key + "=" + value);
                            sessionCache.put(key, value);
                        } else {
                            if (userPreferences == null) {
                                loadUserPreferences();
                            }
                            userPreferences.put(key, value);
                        }

                    }
                }
            }
        } catch (IOException e) {
            log.error("Error loading preferences", e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ex) {
                log.error("Error closing preferences file", ex);
            }
        }
    }

    private synchronized void storePreferences() {

        if (userPreferences != null) {
            PrintWriter pw = null;
            try {
                File rootDir = Globals.getIgvDirectory();
                if (!rootDir.exists()) {
                    rootDir.mkdir();
                }
                File prefFile = new File(rootDir, "prefs.properties");
                pw = new PrintWriter(new BufferedWriter(new FileWriter(prefFile)));
                for (Map.Entry<String, String> entry : userPreferences.entrySet()) {
                    pw.print(entry.getKey());
                    pw.print("=");
                    pw.println(entry.getValue());
                }
            } catch (IOException e) {
                log.error("Error loading preferences", e);
            } finally {
                if (pw != null) {
                    pw.close();
                }
            }

        }
    }
}

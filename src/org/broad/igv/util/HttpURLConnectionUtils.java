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

package org.broad.igv.util;


import org.apache.log4j.Logger;
import org.broad.igv.PreferenceManager;
import org.broad.igv.gs.GSUtils;
import org.broad.igv.ui.IGV;
import org.broad.igv.util.ftp.FTPClient;
import org.broad.igv.util.ftp.FTPStream;
import org.broad.igv.util.ftp.FTPUtils;
import sun.misc.BASE64Encoder;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.Map;

/**
 * Notes -- 401 => client authentication,  407 => proxy authentication,  403 => forbidden
 *
 * @author Jim Robinson
 * @date 9/22/11
 */
public class HttpURLConnectionUtils extends HttpUtils {

    private static Logger log = Logger.getLogger(IGVHttpClientUtils.class);

    private static ProxySettings proxySettings = null;

    private static HttpURLConnectionUtils instance;
    public static final int MAX_REDIRECTS = 5;

    static {
        synchronized (HttpURLConnectionUtils.class) {
            instance = new HttpURLConnectionUtils();
        }
    }

    public static HttpURLConnectionUtils getInstance() {
        return instance;
    }

    private HttpURLConnectionUtils() {
        Authenticator.setDefault(new IGVAuthenticator());

    }

    /**
     * Code for disabling SSL certification
     */
    private void disableCertificateValidation() {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
        };

        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
        }

    }

    public void shutdown() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * Return the contents of the url as a String.  This method should only be used for queries expected to return
     * a small amount of data.
     *
     * @param url
     * @return
     */
    public String getContentsAsString(URL url) throws IOException {

        InputStream is = null;
        StringBuffer contents = new StringBuffer();
        HttpURLConnection conn = openConnection(url, null);

        try {
            is = conn.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String nextLine;
            while ((nextLine = br.readLine()) != null) {
                contents.append(nextLine);
                contents.append("\n");
                System.out.println(nextLine);
            }

        } finally {
            if (is != null) is.close();
        }

        return contents.toString();
    }

    public InputStream openConnectionStream(URL url) throws IOException {
        if (url.getProtocol().toUpperCase().equals("FTP")) {
            String userInfo = url.getUserInfo();
            String host = url.getHost();
            String file = url.getPath();
            FTPClient ftp = FTPUtils.connect(host, userInfo);
            ftp.pasv();
            ftp.retr(file);
            return new FTPStream(ftp);

        } else {
            return openConnectionStream(url, null);
        }
    }

    public InputStream openConnectionStream(URL url, boolean abortOnClose) throws IOException {
        return openConnection(url, null).getInputStream();
    }

    public InputStream openConnectionStream(URL url, Map<String, String> requestProperties) throws IOException {

        HttpURLConnection conn = openConnection(url, requestProperties);
        return conn.getInputStream();

    }

    public InputStream openConnectionStream(URL url, boolean abortOnClose, Map<String, String> requestProperties) throws IOException {
        HttpURLConnection conn = openConnection(url, requestProperties);
        return conn.getInputStream();
    }

    public boolean resourceAvailable(URL url) {

        try {
            HttpURLConnection conn = openConnection(url, null, "HEAD");
            int code = conn.getResponseCode();
            return code == 200;
        } catch (IOException e) {
            return false;
        }
    }

    public String getHeaderField(URL url, String key) throws IOException {
        HttpURLConnection conn = openConnection(url, null, "HEAD");
        int code = conn.getResponseCode();
        // TODO -- check code
        return conn.getHeaderField(key);
    }

    public long getContentLength(URL url) throws IOException {
        String contentLengthString = "";

        contentLengthString = getHeaderField(url, "Content-Length");
        if (contentLengthString == null) {
            return -1;
        } else {
            return Long.parseLong(contentLengthString);
        }
    }

    public void updateProxySettings() {
        boolean useProxy;
        String proxyHost;
        int proxyPort = -1;
        boolean auth = false;
        String user = null;
        String pw = null;

        PreferenceManager prefMgr = PreferenceManager.getInstance();
        useProxy = prefMgr.getAsBoolean(PreferenceManager.USE_PROXY);
        proxyHost = prefMgr.get(PreferenceManager.PROXY_HOST, null);
        try {
            proxyPort = Integer.parseInt(prefMgr.get(PreferenceManager.PROXY_PORT, "-1"));
        } catch (NumberFormatException e) {
            proxyPort = -1;
        }
        auth = prefMgr.getAsBoolean(PreferenceManager.PROXY_AUTHENTICATE);
        user = prefMgr.get(PreferenceManager.PROXY_USER, null);
        String pwString = prefMgr.get(PreferenceManager.PROXY_PW, null);
        if (pwString != null) {
            pw = Utilities.base64Decode(pwString);
        }

        proxySettings = new ProxySettings(useProxy, user, pw, auth, proxyHost, proxyPort);
    }

    public boolean downloadFile(String url, File outputFile) throws IOException {

        log.info("Downloading " + url + " to " + outputFile.getAbsolutePath());

        HttpURLConnection conn = openConnection(new URL(url), null);
        int code = conn.getResponseCode();
        // TODO -- check code

        long contentLength = -1;
        String contentLengthString = conn.getHeaderField("Content-Length");
        if (contentLengthString != null) {
            contentLength = Long.parseLong(contentLengthString);
        }


        log.info("Content length = " + contentLength);

        InputStream is = null;
        OutputStream out = null;

        try {
            is = conn.getInputStream();
            out = new FileOutputStream(outputFile);

            byte[] buf = new byte[64 * 1024];
            int downloaded = 0;
            int bytesRead = 0;
            while ((bytesRead = is.read(buf)) != -1) {
                out.write(buf, 0, bytesRead);
                downloaded += bytesRead;
            }
            log.info("Download complete.  Total bytes downloaded = " + downloaded);
        } finally {
            if (is != null) is.close();
            if (out != null) {
                out.flush();
                out.close();
            }
        }
        long fileLength = outputFile.length();

        return contentLength <= 0 || contentLength == fileLength;
    }

    @Override
    public void uploadGenomeSpaceFile(URI uri, File localFile, Map<String, String> headers) throws IOException {

        URLConnection urlconnection = null;
        try {
            File file = new File("C:/test.txt");
            URL url = uri.toURL();

            urlconnection = openConnection(url, null, "PUT");

            urlconnection = url.openConnection();
            urlconnection.setDoOutput(true);
            urlconnection.setDoInput(true);

            if (urlconnection instanceof HttpURLConnection) {
                try {
                    ((HttpURLConnection) urlconnection).setRequestMethod("PUT");
                    ((HttpURLConnection) urlconnection).setRequestProperty("Content-type", "text/html");
                    ((HttpURLConnection) urlconnection).connect();


                } catch (ProtocolException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }


            BufferedOutputStream bos = new BufferedOutputStream(urlconnection.getOutputStream());
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
            int i;
            // read byte by byte until end of stream
            while ((i = bis.read()) > 0) {
                bos.write(i);
            }
            System.out.println(((HttpURLConnection) urlconnection).getResponseMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {

            InputStream inputStream;
            int responseCode = ((HttpURLConnection) urlconnection).getResponseCode();
            if ((responseCode >= 200) && (responseCode <= 202)) {
                inputStream = ((HttpURLConnection) urlconnection).getInputStream();
                int j;
                while ((j = inputStream.read()) > 0) {
                    System.out.println(j);
                }

            } else {
                inputStream = ((HttpURLConnection) urlconnection).getErrorStream();
            }
            ((HttpURLConnection) urlconnection).disconnect();

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


//        HttpPut put = new HttpPut(uri);
//        try {
//            FileEntity entity = new FileEntity(file, "text");
//            put.setEntity(entity);
//            if (headers != null) {
//                for (Map.Entry<String, String> entry : headers.entrySet()) {
//                    put.addHeader(entry.getKey(), entry.getValue());
//                }
//            }
//
//            HttpResponse response = client.execute(put);
//            EntityUtils.consume(response.getEntity());
//
//            final int statusCode = response.getStatusLine().getStatusCode();
//            if (statusCode == 401) {
//                // Try again
//                client.getCredentialsProvider().clear();
//                login(uri.toURL());
//                uploadGenomeSpaceFile(uri, file, headers);
//            } else if (statusCode == 404 || statusCode == 410) {
//                put.abort();
//                throw new FileNotFoundException();
//            } else if (statusCode >= 400) {
//                put.abort();
//                throw new HttpResponseException(statusCode);
//            }
//
//        } catch (RuntimeException e) {
//            // An unexpected exception  -- abort the HTTP request in order to shut down the underlying
//            // connection immediately. THis happens automatically for an IOException
//            if (put != null) put.abort();
//            throw e;
//        }
    }

    @Override
    public String createGenomeSpaceDirectory(URL url, String body) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    private static HttpURLConnection openConnection(URL url, Map<String, String> requestProperties) throws IOException {
        return openConnection(url, requestProperties, "GET");
    }


    private static HttpURLConnection openConnection(URL url, Map<String, String> requestProperties, String method) throws IOException {
        return openConnection(url, requestProperties, method, 0);
    }

    /**
     * The "real" connection method
     *
     * @param url
     * @param requestProperties
     * @param method
     * @return
     * @throws IOException
     */
    private static HttpURLConnection openConnection(
            URL url, Map<String, String> requestProperties, String method, int redirectCount) throws IOException {

        boolean useProxy = proxySettings != null && proxySettings.useProxy && proxySettings.proxyHost != null &&
                proxySettings.proxyPort > 0;

        HttpURLConnection conn = null;
        if (useProxy) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxySettings.proxyHost, proxySettings.proxyPort));
            conn = (HttpURLConnection) url.openConnection(proxy);

            if (proxySettings.auth && proxySettings.user != null && proxySettings.pw != null) {
                byte[] bytes = (proxySettings.user + ":" + proxySettings.pw).getBytes();
                String encodedUserPwd = (new BASE64Encoder()).encode(bytes);
                conn.setRequestProperty("Proxy-Authorization", "Basic " + encodedUserPwd);
            }
        } else {
            conn = (HttpURLConnection) url.openConnection();
        }

        if (GSUtils.isGenomeSpace(url.toString())) {
            checkForCookie(conn);
            // Manually follow redirects for GS requests.  Can't recall why we do this.
            conn.setRequestProperty("Accept", "application/json,application/text");
            conn.setInstanceFollowRedirects(false);
        }

        conn.setConnectTimeout(10000);
        conn.setReadTimeout(60000);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Connection", "close");
        if (requestProperties != null) {
            for (Map.Entry<String, String> prop : requestProperties.entrySet()) {
                conn.setRequestProperty(prop.getKey(), prop.getValue());
            }
        }

        int code = conn.getResponseCode();

        // Redirects.  These can occur even if followRedirects == true if there is a change in protocol,
        // for example http -> https.
        if (code > 300 && code < 400) {

            if (redirectCount > MAX_REDIRECTS) {
                throw new IOException("Too many redirects");
            }
//
//            Map<String, java.util.List<String>> props = conn.getRequestProperties();
//            if (requestProperties == null) {
//                requestProperties = new HashMap<String, String>();
//                for (Map.Entry<String, java.util.List<String>> entry : props.entrySet()) {
//                    String key = entry.getKey();
//                    java.util.List<String> value = entry.getValue();
//                    if (value.size() > 0) {
//                        requestProperties.put(key, value.get(0));
//                    }
//                }
//            }

            String newLocation = conn.getHeaderField("Location");
            if (newLocation != null) {
                log.debug("Redirecting to " + newLocation);
                return openConnection(new URL(newLocation), requestProperties, method, redirectCount++);
            } else {
                throw new IOException("Server indicated redirect but Location header is missing");
            }
        }

        // TODO -- handle other response codes.
        if (code > 400) {
            throw new IOException("Server returned error code " + code);
        }

        return conn;
    }

    public static void checkForCookie(URLConnection conn) {

        File file = GSUtils.getTokenFile();
        if (file.exists()) {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader(file));
                String token = br.readLine();
                if (token != null) {
                    String cookie = GSUtils.AUTH_TOKEN_COOKIE_NAME + "=" + token;
                    conn.setRequestProperty("Cookie", cookie);
                }
            } catch (IOException e) {
                log.error("Error reading GS cookie", e);
            } finally {
                if (br != null) try {
                    br.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }


    public static class ProxySettings {
        boolean auth = false;
        String user;
        String pw;
        boolean useProxy;
        String proxyHost;
        int proxyPort = -1;

        public ProxySettings(boolean useProxy, String user, String pw, boolean auth, String proxyHost, int proxyPort) {
            this.auth = auth;
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.pw = pw;
            this.useProxy = useProxy;
            this.user = user;
        }
    }

    /**
     * The default authenticator
     */
    public static class IGVAuthenticator extends Authenticator {

        /**
         * Called when password authentcation is needed.
         *
         * @return
         */
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {

            Authenticator.RequestorType type = getRequestorType();
            URL url = this.getRequestingURL();

            boolean isProxyChallenge = type == RequestorType.PROXY;
            if (isProxyChallenge) {
                if (proxySettings.auth && proxySettings.user != null && proxySettings.pw != null) {
                    return new PasswordAuthentication(proxySettings.user, proxySettings.pw.toCharArray());
                }
            }


            Frame owner = IGV.hasInstance() ? IGV.getMainFrame() : null;
            LoginDialog dlg = new LoginDialog(owner, false, url.toString(), isProxyChallenge);
            dlg.setVisible(true);
            if (dlg.isCanceled()) {
                return null;
            } else {
                final String userString = dlg.getUsername();
                final char[] userPass = dlg.getPassword();

                if (isProxyChallenge) {
                    proxySettings.user = userString;
                    proxySettings.pw = new String(userPass);
                } else if (GSUtils.isGenomeSpace(url.getHost())) {
                    String token = null;
                    try {
                        token = getInstance().getContentsAsString(new URL(PreferenceManager.getInstance().get(PreferenceManager.GENOME_SPACE_ID_SERVER)));
                    } catch (IOException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                    if (token != null && token.length() > 0) {
                        GSUtils.saveGSLogin(token, userString);
                    }

                }

                return new PasswordAuthentication(userString, userPass);
            }
        }
    }

}

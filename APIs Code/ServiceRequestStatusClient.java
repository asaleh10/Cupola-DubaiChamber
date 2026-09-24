package flow;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * GetSRSummary - service request status by SR number, or list of SRs by CSN.
 *
 * - Never throws: every outcome comes back in the String[] (see IDX_* and the STATUS_/CODE_ constants).
 * - Daily log file: <logDir>/ServiceRequestStatusClient/ServiceRequestStatusClient_yyyy-MM-dd.log
 * - Configuration via JVM properties (-Ddc.srstatus.url, -Ddc.api.apiKey, -Ddc.api.connectTimeoutMs,
 *   -Ddc.api.readTimeoutMs, -Ddc.api.trustAll, -Ddc.api.logDir, -Ddc.api.logRetentionDays,
 *   -Ddc.api.consoleLogging, -Ddc.api.logMaskSecrets) or the static setters.
 * - CLI: java -cp "out;lib/json-20240303.jar" flow.ServiceRequestStatusClient sr 120802411138 [--trustall]
 *        java -cp "out;lib/json-20240303.jar" flow.ServiceRequestStatusClient csn 1298 [pageSize] [startRowNum]
 */
public final class ServiceRequestStatusClient {

    // ------------------------------------------------------------------
    // Configuration (defaults = SIT)
    // ------------------------------------------------------------------
    private static volatile String apiUrl = System.getProperty("dc.srstatus.url",
            "https://apisit.dubaichamber.com/dcci/DCCICPIntegration/SRSummary_REST/GetSRSummary");
    private static volatile String apiKey = System.getProperty("dc.api.apiKey",
            "_5oGKrI3be5GtHMn4WjALPsEvzjpn3T-4nGwSvrNcCE");
    private static volatile int connectTimeoutMs = Integer.getInteger("dc.api.connectTimeoutMs", 5000);
    private static volatile int readTimeoutMs = Integer.getInteger("dc.api.readTimeoutMs", 10000);
    private static volatile boolean trustAllCertificates = !"false".equalsIgnoreCase(System.getProperty("dc.api.trustAll"));
    private static volatile String logDir = System.getProperty("dc.api.logDir", defaultLogDir());
    private static volatile int logRetentionDays = Integer.getInteger("dc.api.logRetentionDays", 30);
    private static volatile boolean consoleLogging = Boolean.getBoolean("dc.api.consoleLogging");
    private static volatile boolean maskSecrets = !"false".equalsIgnoreCase(System.getProperty("dc.api.logMaskSecrets"));

    private static final String LOG_NAME = "ServiceRequestStatusClient";
    private static final String PROCESS_NAME = "DC Get SR Summary Mob App WF";
    private static final int MAX_LOGGED_BODY = 4000;
    private static SSLSocketFactory trustAllFactory;

    public static final String DEFAULT_LANGUAGE = "ENU";
    public static final String DEFAULT_NO_DAYS = "15";
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int DEFAULT_START_ROW = 0;

    private ServiceRequestStatusClient() { }

    public static void setApiUrl(String url)              { apiUrl = url; }
    public static void setApiKey(String key)              { apiKey = key; }
    public static void setConnectTimeoutMs(int ms)        { connectTimeoutMs = ms; }
    public static void setReadTimeoutMs(int ms)           { readTimeoutMs = ms; }
    public static void setTrustAllCertificates(boolean b) { trustAllCertificates = b; }
    public static void setLogDir(String dir)              { logDir = dir; }
    public static void setLogRetentionDays(int days)      { logRetentionDays = days; }
    public static void setConsoleLogging(boolean b)       { consoleLogging = b; }
    public static void setMaskSecrets(boolean b)          { maskSecrets = b; }

    // ------------------------------------------------------------------
    // Status and codes - branch on these in Orchestration Designer
    // ------------------------------------------------------------------
    public static final String STATUS_SUCCESS = "SUCCESS";   // HTTP 2xx and API "Error Code" empty or 0
    public static final String STATUS_FAILED = "FAILED";     // API answered but rejected the request
    public static final String STATUS_ERROR = "ERROR";       // technical problem, API did not answer properly

    public static final String CODE_INVALID_INPUT = "INVALID_INPUT";       // rejected locally, API not called
    public static final String CODE_INVALID_CONFIG = "INVALID_CONFIG";     // URL empty/invalid, API not called
    public static final String CODE_NOT_FOUND = "NOT_FOUND";               // by-SR: 2xx, no error code, no status
    public static final String CODE_AUTH_ERROR = "AUTH_ERROR";             // HTTP 401/403 (api-key problem)
    public static final String CODE_HTTP_ERROR = "HTTP_ERROR";             // any other non-2xx HTTP status
    public static final String CODE_INVALID_RESPONSE = "INVALID_RESPONSE"; // 2xx but body is not JSON
    public static final String CODE_TIMEOUT = "TIMEOUT";
    public static final String CODE_CONNECTION_ERROR = "CONNECTION_ERROR";
    public static final String CODE_SSL_ERROR = "SSL_ERROR";               // certificate / TLS problem
    public static final String CODE_ERROR = "ERROR";                       // anything unexpected

    // ------------------------------------------------------------------
    // Result indexes
    // ------------------------------------------------------------------
    public static final int IDX_STATUS = 0;
    public static final int IDX_CODE = 1;
    public static final int IDX_MESSAGE = 2;
    public static final int IDX_SR_NUMBER = 3;        // "SRNum" (by-SR mode)
    public static final int IDX_SR_STATUS = 4;        // "Status" (by-SR mode)
    public static final int IDX_LANGUAGE = 5;
    public static final int IDX_START_ROW_NUM = 6;
    public static final int IDX_TOTAL_RECORDS = 7;    // total SRs for the CSN (by-CSN mode)
    public static final int IDX_PAGE_SIZE = 8;
    public static final int IDX_NO_DAYS = 9;
    public static final int IDX_APP_ID = 10;
    public static final int IDX_NEW_QUERY = 11;
    public static final int IDX_COO_DATE_RANGE = 12;
    public static final int IDX_MESSAGE_ID = 13;
    public static final int IDX_INT_OBJECT_NAME = 14;
    public static final int IDX_INT_OBJECT_FORMAT = 15;
    public static final int IDX_MESSAGE_TYPE = 16;
    public static final int IDX_SR_COUNT = 17;        // SRs in this page
    public static final int IDX_SR_LIST_JSON = 18;    // JSON array text, read with getServiceRequest()
    public static final int IDX_RAW_RESPONSE = 19;    // full response text as received
    public static final int IDX_HTTP_STATUS = 20;     // "200", "" when no HTTP answer
    public static final int IDX_ELAPSED_MS = 21;
    public static final int IDX_CALL_ID = 22;         // id used in the log lines of this call
    public static final int RESULT_SIZE = 23;

    public static final String[] LABELS = {
            "status", "code", "message", "srNumber", "srStatus", "language", "startRowNum", "totalRecords",
            "pageSize", "noDays", "appId", "newQuery", "cooDateRange", "messageId", "intObjectName",
            "intObjectFormat", "messageType", "srCount", "srListJson", "rawResponse", "httpStatus", "elapsedMs",
            "callId" };

    public static final int SR_IDX_SR_NUMBER = 0;
    public static final int SR_IDX_STATUS = 1;
    public static final int SR_IDX_SR_TYPE = 2;
    public static final int SR_IDX_SR_SUB_TYPE = 3;
    public static final int SR_IDX_CSN = 4;
    public static final int SR_IDX_SR_ID = 5;
    public static final int SR_IDX_MEMBER_NAME = 6;
    public static final int SR_IDX_CREATED = 7;
    public static final int SR_IDX_DESCRIPTION = 8;
    public static final int SR_IDX_RECEIPT_AMOUNT = 9;
    public static final int SR_IDX_RECEIPT_URL = 10;
    public static final int SR_IDX_INVOICE_NUMBER = 11;
    public static final int SR_IDX_INVOICE_DATE = 12;
    public static final int SR_IDX_INVOICE_AMOUNT = 13;
    public static final int SR_IDX_COO_NUMBER = 14;
    public static final int SR_IDX_DOWNLOAD_URL = 15;
    public static final int SR_IDX_ONLINE_PAY_FLAG = 16;
    public static final int SR_IDX_CURRENT_MONTH_SR = 17;
    public static final int SR_IDX_LICENSE_REG_NUM = 18;
    public static final int SR_IDX_EXPORTER_NAME_EN = 19;
    public static final int SR_IDX_EXPORTER_NAME_AR = 20;
    public static final int SR_IDX_RESPONDANT_NAME = 21;
    public static final int SR_RESULT_SIZE = 22;

    public static final String[] SR_LABELS = {
            "srNumber", "status", "srType", "srSubType", "csn", "srId", "memberName", "created", "description",
            "receiptAmount", "receiptUrl", "invoiceNumber", "invoiceDate", "invoiceAmount", "cooNumber",
            "downloadUrl", "onlinePayFlag", "currentMonthSr", "licenseRegNum", "exporterNameEn",
            "exporterNameAr", "respondantName" };

    // ------------------------------------------------------------------
    // Main API calls
    // ------------------------------------------------------------------

    /** srNumber digits only, e.g. "120802411138"; the class inserts "-" after the first digit. Never throws. */
    public static String[] getServiceRequestStatus(String srNumber) {
        return getServiceRequestStatus(srNumber, DEFAULT_LANGUAGE, DEFAULT_NO_DAYS);
    }

    public static String[] getServiceRequestStatus(String srNumber, String language, String noDays) {
        long start = System.currentTimeMillis();
        String[] r = newResult();
        String callId = newCallId();
        r[IDX_CALL_ID] = callId;
        log("INFO", callId, "START getServiceRequestStatus srNumber=" + srNumber + " language=" + language
                + " noDays=" + noDays);

        srNumber = formatSrNumber(srNumber);
        if (srNumber.isEmpty()) {
            return finish(r, STATUS_FAILED, CODE_INVALID_INPUT, "srNumber is required", start);
        }
        JSONObject sr = new JSONObject();
        sr.put("Account Id", "");
        sr.put("SR Number", srNumber);
        sr.put("SR Type", "");
        sr.put("SR Sub Type", "");
        sr.put("Status", "");
        sr.put("Created By Name", "");
        return call(r, start, sr, language, noDays, DEFAULT_PAGE_SIZE, DEFAULT_START_ROW, true);
    }

    /** All SRs of a membership (CSN), first page of DEFAULT_PAGE_SIZE. Never throws. */
    public static String[] getServiceRequestsByCsn(String csn) {
        return getServiceRequestsByCsn(csn, DEFAULT_LANGUAGE, DEFAULT_NO_DAYS, DEFAULT_PAGE_SIZE, DEFAULT_START_ROW);
    }

    public static String[] getServiceRequestsByCsn(String csn, String language, String noDays, int pageSize,
            int startRowNum) {
        long start = System.currentTimeMillis();
        String[] r = newResult();
        String callId = newCallId();
        r[IDX_CALL_ID] = callId;
        log("INFO", callId, "START getServiceRequestsByCsn csn=" + csn + " language=" + language + " noDays=" + noDays
                + " pageSize=" + pageSize + " startRowNum=" + startRowNum);

        csn = nz(csn);
        if (csn.isEmpty()) {
            return finish(r, STATUS_FAILED, CODE_INVALID_INPUT, "csn is required", start);
        }
        JSONObject sr = new JSONObject();
        sr.put("Account Id", "");
        sr.put("CSN", csn);
        sr.put("SR Type", "");
        sr.put("SR Sub Type", "");
        sr.put("Status", "");
        sr.put("Created By Name", "");
        return call(r, start, sr, language, noDays, pageSize, startRowNum, false);
    }

    /** SR at index from result[IDX_SR_LIST_JSON]; all "" when out of range. */
    public static String[] getServiceRequest(String srListJson, int index) {
        String[] s = new String[SR_RESULT_SIZE];
        Arrays.fill(s, "");
        try {
            JSONArray list = new JSONArray(srListJson == null || srListJson.isEmpty() ? "[]" : srListJson);
            if (index < 0 || index >= list.length()) {
                return s;
            }
            JSONObject o = list.optJSONObject(index);
            s[SR_IDX_SR_NUMBER] = str(o, "SR Number");
            s[SR_IDX_STATUS] = str(o, "Status");
            s[SR_IDX_SR_TYPE] = str(o, "SR Type");
            s[SR_IDX_SR_SUB_TYPE] = str(o, "SR Sub Type");
            s[SR_IDX_CSN] = str(o, "CSN");
            s[SR_IDX_SR_ID] = str(o, "DC SR Id");
            s[SR_IDX_MEMBER_NAME] = str(o, "DC Member Name (English)");
            s[SR_IDX_CREATED] = str(o, "DC Created");
            s[SR_IDX_DESCRIPTION] = str(o, "Description");
            s[SR_IDX_RECEIPT_AMOUNT] = str(o, "DC Receipt Amount");
            s[SR_IDX_RECEIPT_URL] = str(o, "DC eReceipt Download URL");
            s[SR_IDX_INVOICE_NUMBER] = str(o, "DC Invoice Number");
            s[SR_IDX_INVOICE_DATE] = str(o, "DC Invoice Date");
            s[SR_IDX_INVOICE_AMOUNT] = str(o, "DC Invoice Amount");
            s[SR_IDX_COO_NUMBER] = str(o, "DC COO Number");
            s[SR_IDX_DOWNLOAD_URL] = str(o, "DCDownloadURL");
            s[SR_IDX_ONLINE_PAY_FLAG] = str(o, "DC Online Pay Flag");
            s[SR_IDX_CURRENT_MONTH_SR] = str(o, "DC Current Month SR");
            s[SR_IDX_LICENSE_REG_NUM] = str(o, "DC License Registration Num Member Level");
            s[SR_IDX_EXPORTER_NAME_EN] = str(o, "DC Exporter Name (Eng)");
            s[SR_IDX_EXPORTER_NAME_AR] = str(o, "DC Exporter Name (Ara)");
            s[SR_IDX_RESPONDANT_NAME] = str(o, "DC Respondant Name");
        } catch (Exception e) {
            log("WARN", "-", "getServiceRequest(" + index + ") could not parse SR list JSON: " + e);
        }
        return s;
    }

    /** "120802411138" -> "1-20802411138". Leaves a value that already contains "-" unchanged. */
    public static String formatSrNumber(String srNumber) {
        String s = nz(srNumber).replace(" ", "");
        if (s.length() > 1 && !s.contains("-")) {
            s = s.charAt(0) + "-" + s.substring(1);
        }
        return s;
    }

    private static String[] call(String[] r, long start, JSONObject serviceRequest, String language, String noDays,
            int pageSize, int startRowNum, boolean singleSr) {
        String callId = r[IDX_CALL_ID];
        language = nz(language).isEmpty() ? DEFAULT_LANGUAGE : nz(language);
        noDays = nz(noDays).isEmpty() ? DEFAULT_NO_DAYS : nz(noDays);

        String url = nz(apiUrl);
        if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
            return finish(r, STATUS_ERROR, CODE_INVALID_CONFIG, "API URL is empty or invalid: '" + url + "'", start);
        }

        try {
            JSONObject listOf = new JSONObject();
            listOf.put("Service Request", serviceRequest);
            JSONObject incoming = new JSONObject();
            incoming.put("IntObjectName", "DCGetSRSummaryIO");
            incoming.put("MessageType", "Integration Object");
            incoming.put("ListOfDCGetSRSummaryIO", listOf);
            JSONObject body = new JSONObject();
            body.put("ProcessName", PROCESS_NAME);
            body.put("IncomingHierarchy", incoming);
            body.put("Language", language);
            body.put("PageSize", pageSize);
            body.put("StartRowNum", startRowNum);
            body.put("NewQuery", true);
            body.put("NoDays", noDays);
            JSONObject request = new JSONObject();
            request.put("body", body);

            String[] http = httpPost(callId, url, request.toString(), start);
            int status = Integer.parseInt(http[0]);
            String resp = http[1];
            r[IDX_HTTP_STATUS] = http[0];
            r[IDX_RAW_RESPONSE] = resp;

            if (status == 401 || status == 403) {
                return finish(r, STATUS_ERROR, CODE_AUTH_ERROR, "HTTP " + status + " - api-key rejected", start);
            }
            if (status < 200 || status >= 300) {
                return finish(r, STATUS_ERROR, CODE_HTTP_ERROR, "HTTP " + status + " " + truncate(resp, 300), start);
            }
            JSONObject json;
            try {
                json = new JSONObject(resp);
            } catch (Exception e) {
                return finish(r, STATUS_ERROR, CODE_INVALID_RESPONSE,
                        "HTTP " + status + " but response is not JSON: " + e.getMessage(), start);
            }

            boolean failed = applyApiStatus(r, json);
            r[IDX_SR_NUMBER] = str(json, "SRNum");
            r[IDX_SR_STATUS] = str(json, "Status");
            r[IDX_LANGUAGE] = str(json, "Language");
            r[IDX_START_ROW_NUM] = str(json, "StartRowNum");
            r[IDX_TOTAL_RECORDS] = str(json, "TotalRecords");
            r[IDX_PAGE_SIZE] = str(json, "PageSize");
            r[IDX_NO_DAYS] = str(json, "NoDays");
            r[IDX_APP_ID] = str(json, "AppId");
            r[IDX_NEW_QUERY] = str(json, "NewQuery");
            r[IDX_COO_DATE_RANGE] = str(json, "COODteRange");

            JSONObject siebelMessage = obj(json, "SiebelMessage");
            r[IDX_MESSAGE_ID] = str(siebelMessage, "MessageId");
            r[IDX_INT_OBJECT_NAME] = str(siebelMessage, "IntObjectName");
            r[IDX_INT_OBJECT_FORMAT] = str(siebelMessage, "IntObjectFormat");
            r[IDX_MESSAGE_TYPE] = str(siebelMessage, "MessageType");

            JSONArray list = arr(siebelMessage, "Service Request");
            r[IDX_SR_COUNT] = String.valueOf(list.length());
            r[IDX_SR_LIST_JSON] = list.toString();

            if (!failed && singleSr && r[IDX_SR_STATUS].isEmpty() && list.length() == 0) {
                return finish(r, STATUS_FAILED, CODE_NOT_FOUND,
                        "No status returned for SR " + str(serviceRequest, "SR Number"), start);
            }
            return finish(r, r[IDX_STATUS], r[IDX_CODE], r[IDX_MESSAGE], start);

        } catch (SocketTimeoutException e) {
            return finish(r, STATUS_ERROR, CODE_TIMEOUT, "Timeout: " + e.getMessage(), start);
        } catch (UnknownHostException | ConnectException | NoRouteToHostException e) {
            return finish(r, STATUS_ERROR, CODE_CONNECTION_ERROR, "Cannot reach API: " + e, start);
        } catch (SSLException e) {
            return finish(r, STATUS_ERROR, CODE_SSL_ERROR, "SSL/TLS error: " + e.getMessage()
                    + " (certificate validation is on; set dc.api.trustAll=true to skip it)", start);
        } catch (IOException e) {
            return finish(r, STATUS_ERROR, CODE_CONNECTION_ERROR, "I/O error: " + e, start);
        } catch (Exception e) {
            log("ERROR", callId, "Unexpected error" + System.lineSeparator() + stackTrace(e));
            return finish(r, STATUS_ERROR, CODE_ERROR, "Unexpected error: " + e, start);
        }
    }

    // ------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------

    /** POSTs the JSON text. Returns { httpStatus, responseBody }. Throws on transport problems. */
    private static String[] httpPost(String callId, String url, String requestJson, long start) throws Exception {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            if (trustAllCertificates && con instanceof HttpsURLConnection) {
                HttpsURLConnection https = (HttpsURLConnection) con;
                https.setSSLSocketFactory(getTrustAllFactory());
                https.setHostnameVerifier((host, session) -> true);
            }
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setUseCaches(false);
            con.setConnectTimeout(connectTimeoutMs);
            con.setReadTimeout(readTimeoutMs);
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("api-key", apiKey);

            byte[] bytes = requestJson.getBytes(StandardCharsets.UTF_8);
            con.setFixedLengthStreamingMode(bytes.length);

            log("INFO", callId, "REQUEST POST " + url + " (connectTimeout=" + connectTimeoutMs
                    + "ms, readTimeout=" + readTimeoutMs + "ms, trustAll=" + trustAllCertificates + ")");
            log("INFO", callId, "REQUEST HEADERS Content-Type=application/json; charset=UTF-8, Accept=application/json"
                    + ", api-key=" + (maskSecrets ? mask(apiKey) : apiKey));
            log("INFO", callId, "REQUEST BODY " + requestJson);

            try (OutputStream os = con.getOutputStream()) {
                os.write(bytes);
            }

            int status = con.getResponseCode();
            String resp = readAll(status >= 400 ? con.getErrorStream() : con.getInputStream());
            log(status >= 200 && status < 300 ? "INFO" : "ERROR", callId,
                    "RESPONSE HTTP " + status + " in " + (System.currentTimeMillis() - start) + " ms");
            log("INFO", callId, "RESPONSE BODY " + oneLine(truncate(resp, MAX_LOGGED_BODY)));
            return new String[] { String.valueOf(status), resp };
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    private static synchronized SSLSocketFactory getTrustAllFactory() throws Exception {
        if (trustAllFactory == null) {
            TrustManager[] tm = new TrustManager[] { new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) { }
                public void checkServerTrusted(X509Certificate[] c, String a) { }
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            } };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tm, new SecureRandom());
            trustAllFactory = ctx.getSocketFactory();
        }
        return trustAllFactory;
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        try (InputStream in = is) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------
    // Result helpers
    // ------------------------------------------------------------------

    private static String[] newResult() {
        String[] r = new String[RESULT_SIZE];
        Arrays.fill(r, "");
        r[IDX_SR_COUNT] = "0";
        r[IDX_SR_LIST_JSON] = "[]";
        return r;
    }

    /** Sets status/code/message and elapsed time, logs the END line, returns r. */
    private static String[] finish(String[] r, String status, String code, String message, long start) {
        r[IDX_STATUS] = status;
        r[IDX_CODE] = code == null ? "" : code;
        r[IDX_MESSAGE] = message == null ? "" : message;
        r[IDX_ELAPSED_MS] = String.valueOf(System.currentTimeMillis() - start);
        String level = STATUS_SUCCESS.equals(status) ? "INFO" : STATUS_FAILED.equals(status) ? "WARN" : "ERROR";
        log(level, r[IDX_CALL_ID], "END status=" + status + " code=" + r[IDX_CODE] + " message=" + r[IDX_MESSAGE]
                + " http=" + r[IDX_HTTP_STATUS] + " srStatus=" + r[IDX_SR_STATUS] + " srCount=" + r[IDX_SR_COUNT]
                + " elapsedMs=" + r[IDX_ELAPSED_MS]);
        return r;
    }

    /** "Error Code" null, "" or "0" = SUCCESS, otherwise FAILED with the API code and message as-is. */
    private static boolean applyApiStatus(String[] r, JSONObject json) {
        String code = str(json, "Error Code");
        boolean failed = !code.isEmpty() && !"0".equals(code);
        r[IDX_STATUS] = failed ? STATUS_FAILED : STATUS_SUCCESS;
        r[IDX_CODE] = code;
        r[IDX_MESSAGE] = str(json, "Error Message");
        return failed;
    }

    private static String str(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) {
            return "";
        }
        return String.valueOf(o.get(key));
    }

    private static JSONObject obj(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) {
            return null;
        }
        Object v = o.get(key);
        return (v instanceof JSONObject) ? (JSONObject) v : null;
    }

    /** Siebel sends one child as an object, several as an array. */
    private static JSONArray arr(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) {
            return new JSONArray();
        }
        Object v = o.get(key);
        if (v instanceof JSONArray) {
            return (JSONArray) v;
        }
        JSONArray a = new JSONArray();
        a.put(v);
        return a;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...[truncated " + (s.length() - max) + " chars]";
    }

    /** Collapses line breaks so one log entry stays on one line. */
    private static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("\\s*\\r?\\n\\s*", " ");
    }

    private static String mask(String key) {
        if (key == null) {
            return "null";
        }
        if (key.length() <= 15) {
            return "****";
        }
        return key.substring(0, 11) + "****" + key.substring(key.length() - 4);
    }

    private static String newCallId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    // ------------------------------------------------------------------
    // Logging (daily file per class, UTF-8, never throws)
    // ------------------------------------------------------------------

    private static final SimpleDateFormat LOG_TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static final SimpleDateFormat LOG_DAY = new SimpleDateFormat("yyyy-MM-dd");
    private static String lastCleanupDay = "";

    private static synchronized void log(String level, String callId, String msg) {
        Date now = new Date();
        String day = LOG_DAY.format(now);
        String line = LOG_TS.format(now) + " " + level + " [" + callId + "] " + msg;
        try {
            File dir = new File(logDir, LOG_NAME);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, LOG_NAME + "_" + day + ".log");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
                w.write(line);
                w.write(System.lineSeparator());
            }
            if (!day.equals(lastCleanupDay)) {
                lastCleanupDay = day;
                deleteOldLogs(dir, now);
            }
        } catch (Exception e) {
            System.err.println("[" + LOG_NAME + "] cannot write log file (" + e + "): " + line);
        }
        if (consoleLogging) {
            System.out.println(line);
        }
    }

    /** Keeps today's file plus logRetentionDays-1 previous days; 0 or less keeps everything. */
    private static void deleteOldLogs(File dir, Date now) {
        if (logRetentionDays <= 0) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        try {
            long cutoff = LOG_DAY.parse(LOG_DAY.format(now)).getTime() - logRetentionDays * 86400000L;
            String prefix = LOG_NAME + "_";
            for (File f : files) {
                String n = f.getName();
                if (!n.startsWith(prefix) || !n.endsWith(".log")) {
                    continue;
                }
                try {
                    if (LOG_DAY.parse(n.substring(prefix.length(), n.length() - 4)).getTime() <= cutoff) {
                        f.delete();
                    }
                } catch (Exception ignored) {
                    // not one of our dated files
                }
            }
        } catch (Exception e) {
            System.err.println("[" + LOG_NAME + "] log cleanup failed: " + e);
        }
    }

    /** <catalina.base>/logs inside Tomcat, otherwise ./logs. The class adds its own sub-folder. */
    private static String defaultLogDir() {
        String catalina = System.getProperty("catalina.base");
        if (catalina != null && !catalina.isEmpty()) {
            return catalina + File.separator + "logs";
        }
        return "logs";
    }

    // ------------------------------------------------------------------
    // Command-line test
    //   java -cp "out;lib/json-20240303.jar" flow.ServiceRequestStatusClient sr 120802411138 [--trustall] [--url URL] [--apikey KEY] [--logdir DIR]
    //   java -cp "out;lib/json-20240303.jar" flow.ServiceRequestStatusClient csn 1298 [pageSize] [startRowNum] [--trustall] ...
    // ------------------------------------------------------------------
    public static void main(String[] args) {
        try {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));
        } catch (Exception ignored) {
            // keep default console encoding
        }
        String mode = null;
        String value = null;
        String pageSize = null;
        String startRow = null;
        for (int a = 0; a < args.length; a++) {
            String arg = args[a];
            boolean hasNext = a + 1 < args.length;
            if ("--trustall".equalsIgnoreCase(arg)) {
                setTrustAllCertificates(true);
            } else if ("--url".equalsIgnoreCase(arg) && hasNext) {
                setApiUrl(args[++a]);
            } else if ("--apikey".equalsIgnoreCase(arg) && hasNext) {
                setApiKey(args[++a]);
            } else if ("--logdir".equalsIgnoreCase(arg) && hasNext) {
                setLogDir(args[++a]);
            } else if (arg.startsWith("--")) {
                System.out.println("Ignoring unknown argument: " + arg);
            } else if (mode == null) {
                mode = arg;
            } else if (value == null) {
                value = arg;
            } else if (pageSize == null) {
                pageSize = arg;
            } else if (startRow == null) {
                startRow = arg;
            }
        }
        if (mode == null || value == null || !("sr".equalsIgnoreCase(mode) || "csn".equalsIgnoreCase(mode))) {
            System.out.println("Usage: flow.ServiceRequestStatusClient sr <srNumberDigits> [--trustall] [--url URL] [--apikey KEY] [--logdir DIR]");
            System.out.println("       flow.ServiceRequestStatusClient csn <csn> [pageSize] [startRowNum] [--trustall] ...");
            System.exit(2);
            return;
        }
        setConsoleLogging(true);
        System.out.println("Log folder: " + new File(logDir, LOG_NAME).getAbsolutePath());

        String[] r;
        if ("sr".equalsIgnoreCase(mode)) {
            r = getServiceRequestStatus(value);
        } else {
            int ps = pageSize == null ? DEFAULT_PAGE_SIZE : Integer.parseInt(pageSize);
            int sr = startRow == null ? DEFAULT_START_ROW : Integer.parseInt(startRow);
            r = getServiceRequestsByCsn(value, DEFAULT_LANGUAGE, DEFAULT_NO_DAYS, ps, sr);
        }
        print(LABELS, r);
        int count = 0;
        try {
            count = Integer.parseInt(r[IDX_SR_COUNT]);
        } catch (NumberFormatException ignored) {
        }
        for (int i = 0; i < count; i++) {
            System.out.println("ServiceRequest[" + i + "]");
            print(SR_LABELS, getServiceRequest(r[IDX_SR_LIST_JSON], i));
        }
        System.exit(STATUS_SUCCESS.equals(r[IDX_STATUS]) ? 0 : 1);
    }

    private static void print(String[] labels, String[] values) {
        System.out.println("---------------- RESULT ----------------");
        for (int i = 0; i < labels.length && i < values.length; i++) {
            System.out.println(String.format("%-24s = %s", labels[i], values[i]));
        }
        System.out.println("----------------------------------------");
    }
}

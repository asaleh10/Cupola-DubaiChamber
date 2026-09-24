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
 * GenericGetUserProfileAPI - find a user by mobile number and list their accounts.
 *
 * - Never throws: every outcome comes back in the String[] (see IDX_* and the STATUS_/CODE_ constants).
 * - Daily log file: <logDir>/UserProfileClient/UserProfileClient_yyyy-MM-dd.log
 * - Configuration via JVM properties (-Ddc.userprofile.url, -Ddc.api.apiKey, -Ddc.api.connectTimeoutMs,
 *   -Ddc.api.readTimeoutMs, -Ddc.api.trustAll, -Ddc.api.logDir, -Ddc.api.logRetentionDays,
 *   -Ddc.api.consoleLogging, -Ddc.api.logMaskSecrets) or the static setters.
 * - CLI: java -cp "out;lib/json-20240303.jar" flow.UserProfileClient 97121234234 [--trustall] [--logdir DIR]
 */
public final class UserProfileClient {

    // ------------------------------------------------------------------
    // Configuration (defaults = SIT)
    // ------------------------------------------------------------------
    private static volatile String apiUrl = System.getProperty("dc.userprofile.url",
            "https://apisit.dubaichamber.com/dcci/DCCICPIntegration/GenericGetUserProfileAPI");
    private static volatile String apiKey = System.getProperty("dc.api.apiKey",
            "_5oGKrI3be5GtHMn4WjALPsEvzjpn3T-4nGwSvrNcCE");
    private static volatile int connectTimeoutMs = Integer.getInteger("dc.api.connectTimeoutMs", 5000);
    private static volatile int readTimeoutMs = Integer.getInteger("dc.api.readTimeoutMs", 10000);
    private static volatile boolean trustAllCertificates = Boolean.getBoolean("dc.api.trustAll");
    private static volatile String logDir = System.getProperty("dc.api.logDir", defaultLogDir());
    private static volatile int logRetentionDays = Integer.getInteger("dc.api.logRetentionDays", 30);
    private static volatile boolean consoleLogging = Boolean.getBoolean("dc.api.consoleLogging");
    private static volatile boolean maskSecrets = !"false".equalsIgnoreCase(System.getProperty("dc.api.logMaskSecrets"));

    private static final String LOG_NAME = "UserProfileClient";
    private static final String PROCESS_NAME = "DC Get User Profile Details Generic WF";
    private static final String EMIRATES_ID = "";
    private static final String EMAIL_ADDR = "";
    private static final String LOGIN_NAME = "";
    private static final int MAX_LOGGED_BODY = 4000;
    private static SSLSocketFactory trustAllFactory;

    private UserProfileClient() { }

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
    public static final String CODE_NOT_FOUND = "NOT_FOUND";               // 2xx, no error code, but no User block
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
    public static final int IDX_FIRST_NAME = 3;
    public static final int IDX_LAST_NAME = 4;
    public static final int IDX_EMAIL = 5;
    public static final int IDX_PHONE = 6;
    public static final int IDX_HOME_PHONE = 7;
    public static final int IDX_EMIRATES_ID = 8;
    public static final int IDX_DATE_OF_BIRTH = 9;
    public static final int IDX_NATIONALITY = 10;
    public static final int IDX_JOB_TITLE = 11;
    public static final int IDX_LOGIN_NAME = 12;
    public static final int IDX_USER_STATUS = 13;
    public static final int IDX_USER_TYPE = 14;
    public static final int IDX_REGISTRATION_SOURCE = 15;
    public static final int IDX_ACCOUNT_COUNT = 16;
    public static final int IDX_FIRST_ACCOUNT_CSN = 17;
    public static final int IDX_FIRST_ACCOUNT_NAME = 18;
    public static final int IDX_ACCOUNTS_JSON = 19;   // JSON array text, read with getAccount()
    public static final int IDX_MESSAGE_ID = 20;
    public static final int IDX_INT_OBJECT_NAME = 21;
    public static final int IDX_INT_OBJECT_FORMAT = 22;
    public static final int IDX_MESSAGE_TYPE = 23;
    public static final int IDX_RAW_RESPONSE = 24;    // full response text as received
    public static final int IDX_HTTP_STATUS = 25;     // "200", "" when no HTTP answer
    public static final int IDX_ELAPSED_MS = 26;
    public static final int IDX_CALL_ID = 27;         // id used in the log lines of this call
    public static final int RESULT_SIZE = 28;

    public static final String[] LABELS = {
            "status", "code", "message", "firstName", "lastName", "email", "phone", "homePhone", "emiratesId",
            "dateOfBirth", "nationality", "jobTitle", "loginName", "userStatus", "userType",
            "registrationSource", "accountCount", "firstAccountCsn", "firstAccountName", "accountsJson",
            "messageId", "intObjectName", "intObjectFormat", "messageType", "rawResponse", "httpStatus",
            "elapsedMs", "callId" };

    public static final int ACC_IDX_CSN = 0;
    public static final int ACC_IDX_NAME_EN = 1;
    public static final int ACC_IDX_NAME_AR = 2;
    public static final int ACC_IDX_TYPE = 3;
    public static final int ACC_IDX_STATUS = 4;
    public static final int ACC_IDX_EXPIRY_DATE = 5;
    public static final int ACC_IDX_LICENSE_NO = 6;
    public static final int ACC_IDX_LICENSE_AUTH = 7;
    public static final int ACC_IDX_PLATINUM_FLAG = 8;
    public static final int ACC_IDX_MAIN_PHONE = 9;
    public static final int ACC_IDX_MAIN_FAX = 10;
    public static final int ACC_IDX_RM_NAME = 11;
    public static final int ACC_IDX_RM_EMAIL = 12;
    public static final int ACC_IDX_RM_PHONE = 13;
    public static final int ACC_RESULT_SIZE = 14;

    public static final String[] ACC_LABELS = {
            "csn", "nameEn", "nameAr", "accountType", "accountStatus", "expiryDate", "licenseNo", "licenseAuth",
            "platinumFlag", "mainPhone", "mainFax", "rmName", "rmEmail", "rmPhone" };

    // ------------------------------------------------------------------
    // Main API call
    // ------------------------------------------------------------------

    /** mobileNumber with country code, no "+", e.g. "97121234234". Never throws. */
    public static String[] getUserProfileByMobile(String mobileNumber) {
        long start = System.currentTimeMillis();
        String[] r = newResult();
        String callId = newCallId();
        r[IDX_CALL_ID] = callId;
        log("INFO", callId, "START getUserProfileByMobile mobileNumber=" + mobileNumber);

        mobileNumber = nz(mobileNumber);
        if (mobileNumber.isEmpty()) {
            return finish(r, STATUS_FAILED, CODE_INVALID_INPUT, "mobileNumber is required", start);
        }
        String url = nz(apiUrl);
        if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
            return finish(r, STATUS_ERROR, CODE_INVALID_CONFIG, "API URL is empty or invalid: '" + url + "'", start);
        }

        try {
            JSONObject body = new JSONObject();
            body.put("ProcessName", PROCESS_NAME);
            body.put("EmiratesId", EMIRATES_ID);
            body.put("EmailAddr", EMAIL_ADDR);
            body.put("MobileNumber", mobileNumber);
            body.put("LoginName", LOGIN_NAME);
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

            JSONObject siebMsg = obj(json, "SiebMsg");
            r[IDX_MESSAGE_ID] = str(siebMsg, "MessageId");
            r[IDX_INT_OBJECT_NAME] = str(siebMsg, "IntObjectName");
            r[IDX_INT_OBJECT_FORMAT] = str(siebMsg, "IntObjectFormat");
            r[IDX_MESSAGE_TYPE] = str(siebMsg, "MessageType");

            JSONObject user = obj(siebMsg, "User");
            if (user == null) {
                if (!failed) {
                    return finish(r, STATUS_FAILED, CODE_NOT_FOUND, "No user profile returned", start);
                }
                return finish(r, r[IDX_STATUS], r[IDX_CODE], r[IDX_MESSAGE], start);
            }

            r[IDX_FIRST_NAME] = str(user, "FirstName");
            r[IDX_LAST_NAME] = str(user, "LastName");
            r[IDX_EMAIL] = str(user, "EMailAddr");
            r[IDX_PHONE] = str(user, "Phone");
            r[IDX_HOME_PHONE] = str(user, "HomePhone");
            r[IDX_EMIRATES_ID] = str(user, "EmiratesID");
            r[IDX_DATE_OF_BIRTH] = str(user, "DateofBirth");
            r[IDX_NATIONALITY] = str(user, "Nationality");
            r[IDX_JOB_TITLE] = str(user, "JobTitle");
            r[IDX_LOGIN_NAME] = str(user, "LoginName");
            r[IDX_USER_STATUS] = str(user, "UserStatus");
            r[IDX_USER_TYPE] = str(user, "UserType");
            r[IDX_REGISTRATION_SOURCE] = str(user, "RegistrationSourceAppName");

            JSONArray accounts = arr(user, "Account");
            r[IDX_ACCOUNT_COUNT] = String.valueOf(accounts.length());
            r[IDX_ACCOUNTS_JSON] = accounts.toString();
            if (accounts.length() > 0) {
                JSONObject first = accounts.optJSONObject(0);
                r[IDX_FIRST_ACCOUNT_CSN] = str(first, "CSN");
                r[IDX_FIRST_ACCOUNT_NAME] = str(first, "DCNameEnglish");
            }
            return finish(r, r[IDX_STATUS], r[IDX_CODE], r[IDX_MESSAGE], start);

        } catch (SocketTimeoutException e) {
            return finish(r, STATUS_ERROR, CODE_TIMEOUT, "Timeout: " + e.getMessage(), start);
        } catch (UnknownHostException | ConnectException | NoRouteToHostException e) {
            return finish(r, STATUS_ERROR, CODE_CONNECTION_ERROR, "Cannot reach API: " + e, start);
        } catch (SSLException e) {
            return finish(r, STATUS_ERROR, CODE_SSL_ERROR, "SSL/TLS error: " + e.getMessage()
                    + " (set dc.api.trustAll=true to skip certificate checks in test environments)", start);
        } catch (IOException e) {
            return finish(r, STATUS_ERROR, CODE_CONNECTION_ERROR, "I/O error: " + e, start);
        } catch (Exception e) {
            log("ERROR", callId, "Unexpected error" + System.lineSeparator() + stackTrace(e));
            return finish(r, STATUS_ERROR, CODE_ERROR, "Unexpected error: " + e, start);
        }
    }

    /** Account at index from result[IDX_ACCOUNTS_JSON]; all "" when out of range. */
    public static String[] getAccount(String accountsJson, int index) {
        String[] acc = new String[ACC_RESULT_SIZE];
        Arrays.fill(acc, "");
        try {
            JSONArray accounts = new JSONArray(accountsJson == null || accountsJson.isEmpty() ? "[]" : accountsJson);
            if (index < 0 || index >= accounts.length()) {
                return acc;
            }
            JSONObject a = accounts.optJSONObject(index);
            acc[ACC_IDX_CSN] = str(a, "CSN");
            acc[ACC_IDX_NAME_EN] = str(a, "DCNameEnglish");
            acc[ACC_IDX_NAME_AR] = str(a, "DCNameArabic");
            acc[ACC_IDX_TYPE] = str(a, "AccountTypeCode");
            acc[ACC_IDX_STATUS] = str(a, "AccountStatus");
            acc[ACC_IDX_EXPIRY_DATE] = str(a, "DCExpiryDate");
            acc[ACC_IDX_LICENSE_NO] = str(a, "LicenseNumber");
            acc[ACC_IDX_LICENSE_AUTH] = str(a, "LicenseIssuingAuthority");
            acc[ACC_IDX_PLATINUM_FLAG] = str(a, "PlatinumFlag");
            acc[ACC_IDX_MAIN_PHONE] = str(a, "MainPhoneNumber");
            acc[ACC_IDX_MAIN_FAX] = str(a, "MainFaxNumber");
            acc[ACC_IDX_RM_NAME] = str(a, "RelationshipManagerName");
            acc[ACC_IDX_RM_EMAIL] = str(a, "RelationshipManagerEmailAddr");
            acc[ACC_IDX_RM_PHONE] = str(a, "RelationshipManagerPhone");
        } catch (Exception e) {
            log("WARN", "-", "getAccount(" + index + ") could not parse accounts JSON: " + e);
        }
        return acc;
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
        r[IDX_ACCOUNT_COUNT] = "0";
        r[IDX_ACCOUNTS_JSON] = "[]";
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
                + " http=" + r[IDX_HTTP_STATUS] + " accountCount=" + r[IDX_ACCOUNT_COUNT]
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
    //   java -cp "out;lib/json-20240303.jar" flow.UserProfileClient 97121234234 [--trustall] [--url URL] [--apikey KEY] [--logdir DIR]
    // ------------------------------------------------------------------
    public static void main(String[] args) {
        try {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));
        } catch (Exception ignored) {
            // keep default console encoding
        }
        String input = null;
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
            } else if (input == null && !arg.startsWith("--")) {
                input = arg;
            } else {
                System.out.println("Ignoring unknown argument: " + arg);
            }
        }
        if (input == null) {
            System.out.println("Usage: flow.UserProfileClient <mobileNumber> [--trustall] [--url URL] [--apikey KEY] [--logdir DIR]");
            System.exit(2);
            return;
        }
        setConsoleLogging(true);
        System.out.println("Log folder: " + new File(logDir, LOG_NAME).getAbsolutePath());

        String[] r = getUserProfileByMobile(input);
        print(LABELS, r);
        int count = 0;
        try {
            count = Integer.parseInt(r[IDX_ACCOUNT_COUNT]);
        } catch (NumberFormatException ignored) {
        }
        for (int i = 0; i < count; i++) {
            System.out.println("Account[" + i + "]");
            print(ACC_LABELS, getAccount(r[IDX_ACCOUNTS_JSON], i));
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

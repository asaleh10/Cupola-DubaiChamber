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

import org.json.JSONObject;

/**
 * GenSendPaymentLink - generate an ePay link for an SR and e-mail it to the user.
 * NOT read-only: each successful call creates a transaction and sends an e-mail.
 *
 * - Never throws: every outcome comes back in the String[] (see IDX_* and the STATUS_/CODE_ constants).
 * - Daily log file: <logDir>/PaymentLinkClient/PaymentLinkClient_yyyy-MM-dd.log
 * - Configuration via JVM properties (-Ddc.payment.url, -Ddc.api.apiKey, -Ddc.api.connectTimeoutMs,
 *   -Ddc.api.readTimeoutMs, -Ddc.api.trustAll, -Ddc.api.logDir, -Ddc.api.logRetentionDays,
 *   -Ddc.api.consoleLogging, -Ddc.api.logMaskSecrets) or the static setters.
 * - CLI: java -cp "out;lib/json-20240303.jar" flow.PaymentLinkClient 120804978308 [--trustall] [--logdir DIR]
 */
public final class PaymentLinkClient {

    // ------------------------------------------------------------------
    // Configuration (defaults = SIT)
    // ------------------------------------------------------------------
    private static volatile String apiUrl = System.getProperty("dc.payment.url",
            "https://apisit.dubaichamber.com/dcci/DCCICPIntegration/GenSendPaymentLink_REST/GenSendPaymentLink");
    private static volatile String apiKey = System.getProperty("dc.api.apiKey",
            "_5oGKrI3be5GtHMn4WjALPsEvzjpn3T-4nGwSvrNcCE");
    private static volatile int connectTimeoutMs = Integer.getInteger("dc.api.connectTimeoutMs", 5000);
    private static volatile int readTimeoutMs = Integer.getInteger("dc.api.readTimeoutMs", 10000);
    private static volatile boolean trustAllCertificates = Boolean.getBoolean("dc.api.trustAll");
    private static volatile String logDir = System.getProperty("dc.api.logDir", defaultLogDir());
    private static volatile int logRetentionDays = Integer.getInteger("dc.api.logRetentionDays", 30);
    private static volatile boolean consoleLogging = Boolean.getBoolean("dc.api.consoleLogging");
    private static volatile boolean maskSecrets = !"false".equalsIgnoreCase(System.getProperty("dc.api.logMaskSecrets"));

    private static final String LOG_NAME = "PaymentLinkClient";
    private static final String PROCESS_NAME = "DC Generate Send ePay URL WF";
    private static final String LOGIN_NAME = "TESTUSERSIT";
    private static final String PAYMENT_TYPE = "DubaiPay";
    private static final int MAX_LOGGED_BODY = 4000;
    private static SSLSocketFactory trustAllFactory;

    private PaymentLinkClient() { }

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
    public static final int IDX_SR_NUMBER = 3;
    public static final int IDX_TRANSACTION_ID = 4;
    public static final int IDX_PAYMENT_URL = 5;
    public static final int IDX_FINAL_AMOUNT = 6;
    public static final int IDX_PAYMENT_TYPE = 7;
    public static final int IDX_SR_TYPE = 8;
    public static final int IDX_EMAIL_ADDR = 9;
    public static final int IDX_EMAIL_STATUS = 10;
    public static final int IDX_SEND_EMAIL_TO = 11;
    public static final int IDX_REDIRECTION_URL = 12;
    public static final int IDX_LOGIN_NAME = 13;
    public static final int IDX_RAW_RESPONSE = 14;   // full response text as received
    public static final int IDX_HTTP_STATUS = 15;    // "200", "" when no HTTP answer
    public static final int IDX_ELAPSED_MS = 16;
    public static final int IDX_CALL_ID = 17;        // id used in the log lines of this call
    public static final int RESULT_SIZE = 18;

    public static final String[] LABELS = {
            "status", "code", "message", "srNumber", "transactionId", "paymentUrl", "finalAmount",
            "paymentType", "srType", "emailAddr", "emailStatus", "sendEmailTo", "redirectionUrl", "loginName",
            "rawResponse", "httpStatus", "elapsedMs", "callId" };

    // ------------------------------------------------------------------
    // Main API call
    // ------------------------------------------------------------------

    /** srNumber digits only, e.g. "120804978308"; the class inserts "-" after the first digit. Never throws. */
    public static String[] generateAndSendPaymentLink(String srNumber) {
        long start = System.currentTimeMillis();
        String[] r = newResult();
        String callId = newCallId();
        r[IDX_CALL_ID] = callId;
        log("INFO", callId, "START generateAndSendPaymentLink srNumber=" + srNumber);

        srNumber = formatSrNumber(srNumber);
        if (srNumber.isEmpty()) {
            return finish(r, STATUS_FAILED, CODE_INVALID_INPUT, "srNumber is required", start);
        }
        String url = nz(apiUrl);
        if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
            return finish(r, STATUS_ERROR, CODE_INVALID_CONFIG, "API URL is empty or invalid: '" + url + "'", start);
        }

        try {
            JSONObject body = new JSONObject();
            body.put("ProcessName", PROCESS_NAME);
            body.put("SR Number", srNumber);
            body.put("LoginName", LOGIN_NAME);
            body.put("PaymentType", PAYMENT_TYPE);
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

            applyApiStatus(r, json);
            r[IDX_SR_NUMBER] = str(json, "SR Number");
            r[IDX_TRANSACTION_ID] = str(json, "Transaction Id");
            r[IDX_PAYMENT_URL] = str(json, "PaymentURL");
            r[IDX_FINAL_AMOUNT] = str(json, "Final Amount");
            r[IDX_PAYMENT_TYPE] = str(json, "PaymentType");
            r[IDX_SR_TYPE] = str(json, "SRType");
            r[IDX_EMAIL_ADDR] = str(json, "EmailAddr");
            r[IDX_EMAIL_STATUS] = str(json, "EmailStatus");
            r[IDX_SEND_EMAIL_TO] = str(json, "SendEmailTo");
            r[IDX_REDIRECTION_URL] = str(json, "redirectionurl");
            r[IDX_LOGIN_NAME] = str(json, "LoginName");
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

    /** "120804978308" -> "1-20804978308". Leaves a value that already contains "-" unchanged. */
    public static String formatSrNumber(String srNumber) {
        String s = nz(srNumber).replace(" ", "");
        if (s.length() > 1 && !s.contains("-")) {
            s = s.charAt(0) + "-" + s.substring(1);
        }
        return s;
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
                + " http=" + r[IDX_HTTP_STATUS] + " elapsedMs=" + r[IDX_ELAPSED_MS]);
        return r;
    }

    /** "Error Code" null, "" or "0" = SUCCESS, otherwise FAILED with the API code and message as-is. */
    private static void applyApiStatus(String[] r, JSONObject json) {
        String code = str(json, "Error Code");
        boolean failed = !code.isEmpty() && !"0".equals(code);
        r[IDX_STATUS] = failed ? STATUS_FAILED : STATUS_SUCCESS;
        r[IDX_CODE] = code;
        r[IDX_MESSAGE] = str(json, "Error Message");
    }

    private static String str(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) {
            return "";
        }
        return String.valueOf(o.get(key));
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
    // Command-line test (sends a real e-mail)
    //   java -cp "out;lib/json-20240303.jar" flow.PaymentLinkClient 120804978308 [--trustall] [--url URL] [--apikey KEY] [--logdir DIR]
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
            System.out.println("Usage: flow.PaymentLinkClient <srNumberDigits> [--trustall] [--url URL] [--apikey KEY] [--logdir DIR]");
            System.exit(2);
            return;
        }
        setConsoleLogging(true);
        System.out.println("Log folder: " + new File(logDir, LOG_NAME).getAbsolutePath());

        String[] r = generateAndSendPaymentLink(input);
        System.out.println("---------------- RESULT ----------------");
        for (int i = 0; i < LABELS.length; i++) {
            System.out.println(String.format("%-24s = %s", LABELS[i], r[i]));
        }
        System.out.println("----------------------------------------");
        System.exit(STATUS_SUCCESS.equals(r[IDX_STATUS]) ? 0 : 1);
    }
}

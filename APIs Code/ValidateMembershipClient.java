package flow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import org.json.JSONObject;

/**
 * ValidateAccount - validate a membership (CSN) number and return member / licence details.
 * Returns String[]: [0]=status SUCCESS|FAILED|ERROR, [1]=code, [2]=message, [3..]=every response field (see IDX_*).
 * Logs every call to <LOG_DIR>/ValidateMembershipClient/ValidateMembershipClient_yyyy-MM-dd.log.
 */
public class ValidateMembershipClient {

    private static final String API_URL =
            "https://apisit.dubaichamber.com/dcci/DCCICPIntegration/ValidateAccount_REST/ValidateAccount";
    private static final String API_KEY = "_5oGKrI3be5GtHMn4WjALPsEvzjpn3T-4nGwSvrNcCE";
    private static final String PROCESS_NAME = "DC Validate Member Info WF";

    private static final String LICENSE_NO = "";
    private static final String LICENSE_AUTH = "";
    private static final String LICENSE_TYPE = "";

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;

    // Logging. Defaults below; override with JVM properties -Ddc.api.log.dir, -Ddc.api.log.retentionDays,
    // -Ddc.api.log.enabled, -Ddc.api.log.console, -Ddc.api.log.maskApiKey (e.g. in Tomcat setenv / JAVA_OPTS).
    private static final String LOG_NAME = "ValidateMembershipClient";
    private static final String LOG_DIR = System.getProperty("dc.api.log.dir",
            System.getProperty("catalina.base", System.getProperty("user.dir")) + File.separator + "logs");
    private static final int LOG_RETENTION_DAYS = intProp("dc.api.log.retentionDays", 30);   // 0 = keep forever
    private static final boolean LOG_ENABLED = boolProp("dc.api.log.enabled", true);
    private static final boolean LOG_TO_CONSOLE = boolProp("dc.api.log.console", true);
    private static final boolean LOG_MASK_API_KEY = boolProp("dc.api.log.maskApiKey", false);

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_ERROR = "ERROR";

    public static final String CODE_INVALID_INPUT = "INVALID_INPUT";
    public static final String CODE_TIMEOUT = "TIMEOUT";
    public static final String CODE_EXCEPTION = "EXCEPTION";

    public static final int IDX_STATUS = 0;
    public static final int IDX_CODE = 1;
    public static final int IDX_MESSAGE = 2;
    public static final int IDX_MEMBER_NO = 3;
    public static final int IDX_MEMBER_NAME = 4;
    public static final int IDX_MEMBER_NAME_AR = 5;
    public static final int IDX_MEMBER_STATUS = 6;
    public static final int IDX_LICENSE_NO = 7;
    public static final int IDX_LICENSE_TYPE = 8;
    public static final int IDX_LICENSE_AUTH = 9;
    public static final int IDX_EXPIRY_DATE = 10;
    public static final int IDX_PLATINUM_FLAG = 11;
    public static final int IDX_OBJECT_ID = 12;
    public static final int IDX_SIEBEL_OPERATION_OBJECT_ID = 13;
    public static final int IDX_PROCESS_INSTANCE_ID = 14;
    public static final int IDX_RAW_RESPONSE = 15;   // full response text as received
    public static final int RESULT_SIZE = 16;

    public static final String[] LABELS = {
            "status", "code", "message", "memberNo", "memberName", "memberNameAr", "memberStatus",
            "licenseNo", "licenseType", "licenseAuth", "expiryDate", "platinumFlag", "objectId",
            "siebelOperationObjectId", "processInstanceId", "rawResponse" };

    /** memberNo = membership / CSN number, e.g. "1298". */
    public static String[] validateMember(String memberNo) {
        String callId = newCallId();
        log(callId, "INFO", "CALL validateMember memberNo=" + memberNo);

        memberNo = nz(memberNo);
        if (memberNo.isEmpty()) {
            return finish(callId, failedResult(CODE_INVALID_INPUT, "memberNo is required"));
        }

        try {
            JSONObject body = new JSONObject();
            body.put("ProcessName", PROCESS_NAME);
            body.put("LicenseNo", LICENSE_NO);
            body.put("LicenseAuth", LICENSE_AUTH);
            body.put("MemberNo", memberNo);
            body.put("LicenseType", LICENSE_TYPE);

            JSONObject request = new JSONObject();
            request.put("body", body);

            String[] http = httpPost(callId, request.toString());
            int statusCode = Integer.parseInt(http[0]);
            String responseText = http[1];

            if (statusCode < 200 || statusCode >= 300) {
                return finish(callId, errorResult("HTTP_" + statusCode, "Non-2xx response", responseText));
            }

            JSONObject json = new JSONObject(responseText);
            String[] r = newResult();
            applyApiStatus(r, json);
            r[IDX_RAW_RESPONSE] = responseText;

            r[IDX_MEMBER_NO] = str(json, "MemberNo");
            r[IDX_MEMBER_NAME] = str(json, "MemberName");
            r[IDX_MEMBER_NAME_AR] = str(json, "MemberNameAra");
            r[IDX_MEMBER_STATUS] = str(json, "Status");
            r[IDX_LICENSE_NO] = str(json, "LicenseNo");
            r[IDX_LICENSE_TYPE] = str(json, "LicenseType");
            r[IDX_LICENSE_AUTH] = str(json, "LicenseAuth");
            r[IDX_EXPIRY_DATE] = str(json, "ExpiryDate");
            r[IDX_PLATINUM_FLAG] = str(json, "PlatinumFlag");
            r[IDX_OBJECT_ID] = str(json, "Object Id");
            r[IDX_SIEBEL_OPERATION_OBJECT_ID] = str(json, "Siebel Operation Object Id");
            r[IDX_PROCESS_INSTANCE_ID] = str(json, "Process Instance Id");
            return finish(callId, r);

        } catch (SocketTimeoutException e) {
            log(callId, "ERROR", "TIMEOUT " + e + System.lineSeparator() + stackTrace(e));
            return finish(callId, errorResult(CODE_TIMEOUT, e.getMessage(), ""));
        } catch (Exception e) {
            log(callId, "ERROR", "EXCEPTION " + e + System.lineSeparator() + stackTrace(e));
            return finish(callId, errorResult(CODE_EXCEPTION, e.getClass().getSimpleName() + ": " + e.getMessage(), ""));
        }
    }

    private static String[] httpPost(String callId, String requestJson) throws IOException {
        byte[] requestBytes = requestJson.getBytes(StandardCharsets.UTF_8);
        log(callId, "INFO", "REQUEST POST " + API_URL);
        log(callId, "INFO", "REQUEST headers Content-Type=application/json; charset=UTF-8 | Accept=application/json"
                + " | api-key=" + (LOG_MASK_API_KEY ? mask(API_KEY) : API_KEY));
        log(callId, "INFO", "REQUEST body " + requestJson);
        long started = System.currentTimeMillis();

        HttpURLConnection conn = null;
        try {
            URL url = new URL(API_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("api-key", API_KEY);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBytes);
                os.flush();
            }

            int statusCode = conn.getResponseCode();
            InputStream is = (statusCode >= 200 && statusCode < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();
            String responseText = readStream(is);

            long ms = System.currentTimeMillis() - started;
            log(callId, statusCode >= 200 && statusCode < 300 ? "INFO" : "ERROR",
                    "RESPONSE HTTP " + statusCode + " in " + ms + " ms");
            log(callId, "INFO", "RESPONSE body " + responseText);
            return new String[] { String.valueOf(statusCode), responseText };
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String[] newResult() {
        String[] r = new String[RESULT_SIZE];
        Arrays.fill(r, "");
        return r;
    }

    private static String[] errorResult(String code, String message, String rawResponse) {
        String[] r = newResult();
        r[IDX_STATUS] = STATUS_ERROR;
        r[IDX_CODE] = code;
        r[IDX_MESSAGE] = message == null ? "" : message;
        r[IDX_RAW_RESPONSE] = rawResponse == null ? "" : rawResponse;
        return r;
    }

    private static String[] failedResult(String code, String message) {
        String[] r = newResult();
        r[IDX_STATUS] = STATUS_FAILED;
        r[IDX_CODE] = code;
        r[IDX_MESSAGE] = message;
        return r;
    }

    /** "Error Code" null, "" or "0" = success. */
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

    // ---------------------------------------------------------------- logging

    private static final Object LOG_LOCK = new Object();
    private static String lastCleanupDay = "";

    /** Logs the final result line and returns r unchanged. */
    private static String[] finish(String callId, String[] r) {
        String level = STATUS_ERROR.equals(r[IDX_STATUS]) ? "ERROR"
                : STATUS_FAILED.equals(r[IDX_STATUS]) ? "WARN" : "INFO";
        log(callId, level, "RESULT status=" + r[IDX_STATUS] + " code=" + r[IDX_CODE] + " message=" + r[IDX_MESSAGE]);
        return r;
    }

    /** Appends one line to today's file; never throws. */
    private static void log(String callId, String level, String message) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
        String line = ts + " " + level + " [" + callId + "] " + message;
        if (LOG_TO_CONSOLE) {
            System.out.println(LOG_NAME + ": " + line);
        }
        if (!LOG_ENABLED) {
            return;
        }
        try {
            String day = ts.substring(0, 10);
            File dir = new File(LOG_DIR, LOG_NAME);
            synchronized (LOG_LOCK) {
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
                    deleteOldLogs(dir, day);
                }
            }
        } catch (Exception e) {
            System.err.println(LOG_NAME + ": cannot write log file: " + e);
        }
    }

    /** Keeps today's file plus LOG_RETENTION_DAYS-1 previous days; 0 or less keeps everything. */
    private static void deleteOldLogs(File dir, String today) {
        if (LOG_RETENTION_DAYS <= 0) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        try {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
            long cutoff = df.parse(today).getTime() - LOG_RETENTION_DAYS * 86400000L;
            String prefix = LOG_NAME + "_";
            for (File f : files) {
                String n = f.getName();
                if (!n.startsWith(prefix) || !n.endsWith(".log")) {
                    continue;
                }
                try {
                    Date fileDay = df.parse(n.substring(prefix.length(), n.length() - 4));
                    if (fileDay.getTime() <= cutoff) {
                        f.delete();
                    }
                } catch (Exception ignored) {
                    // not one of our dated files
                }
            }
        } catch (Exception e) {
            System.err.println(LOG_NAME + ": log cleanup failed: " + e);
        }
    }

    private static String newCallId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String mask(String secret) {
        if (secret == null || secret.length() < 8) {
            return "****";
        }
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static int intProp(String name, int def) {
        try {
            return Integer.parseInt(System.getProperty(name, String.valueOf(def)).trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean boolProp(String name, boolean def) {
        return Boolean.parseBoolean(System.getProperty(name, String.valueOf(def)).trim());
    }

    // CLI: java -cp "out;lib/json-20240303.jar" flow.ValidateMembershipClient 1298
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: flow.ValidateMembershipClient <memberNo>");
            System.exit(2);
        }
        String[] r = validateMember(args[0]);
        System.out.println("---------------- RESULT ----------------");
        for (int i = 0; i < LABELS.length; i++) {
            System.out.println(String.format("%-16s = %s", LABELS[i], r[i]));
        }
        System.out.println("----------------------------------------");
        System.out.println("Log file: " + new File(new File(LOG_DIR, LOG_NAME),
                LOG_NAME + "_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log").getAbsolutePath());
        System.exit(STATUS_SUCCESS.equals(r[IDX_STATUS]) ? 0 : 1);
    }
}

package flow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * GetSRSummary - service request status by SR number, or list of SRs by CSN.
 * Returns String[]: [0]=status SUCCESS|FAILED|ERROR, [1]=code, [2]=message, [3..]=every response field (see IDX_*).
 */
public class ServiceRequestStatusClient {

    private static final String API_URL =
            "https://apisit.dubaichamber.com/dcci/DCCICPIntegration/SRSummary_REST/GetSRSummary";
    private static final String API_KEY = "_5oGKrI3be5GtHMn4WjALPsEvzjpn3T-4nGwSvrNcCE";
    private static final String PROCESS_NAME = "DC Get SR Summary Mob App WF";

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;

    public static boolean DEBUG = true;

    public static final String DEFAULT_LANGUAGE = "ENU";
    public static final String DEFAULT_NO_DAYS = "15";
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int DEFAULT_START_ROW = 0;

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_ERROR = "ERROR";

    public static final String CODE_INVALID_INPUT = "INVALID_INPUT";
    public static final String CODE_NOT_FOUND = "NOT_FOUND";
    public static final String CODE_TIMEOUT = "TIMEOUT";
    public static final String CODE_EXCEPTION = "EXCEPTION";

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
    public static final int RESULT_SIZE = 20;

    public static final String[] LABELS = {
            "status", "code", "message", "srNumber", "srStatus", "language", "startRowNum", "totalRecords",
            "pageSize", "noDays", "appId", "newQuery", "cooDateRange", "messageId", "intObjectName",
            "intObjectFormat", "messageType", "srCount", "srListJson", "rawResponse" };

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

    /** srNumber digits only, e.g. "120802411138"; the class inserts "-" after the first digit. */
    public static String[] getServiceRequestStatus(String srNumber) {
        return getServiceRequestStatus(srNumber, DEFAULT_LANGUAGE, DEFAULT_NO_DAYS);
    }

    public static String[] getServiceRequestStatus(String srNumber, String language, String noDays) {
        srNumber = formatSrNumber(srNumber);
        if (srNumber.isEmpty()) {
            return failedResult(CODE_INVALID_INPUT, "srNumber is required");
        }
        JSONObject sr = new JSONObject();
        sr.put("Account Id", "");
        sr.put("SR Number", srNumber);
        sr.put("SR Type", "");
        sr.put("SR Sub Type", "");
        sr.put("Status", "");
        sr.put("Created By Name", "");
        return call(sr, language, noDays, DEFAULT_PAGE_SIZE, DEFAULT_START_ROW, true);
    }

    /** All SRs of a membership (CSN), first page of DEFAULT_PAGE_SIZE. */
    public static String[] getServiceRequestsByCsn(String csn) {
        return getServiceRequestsByCsn(csn, DEFAULT_LANGUAGE, DEFAULT_NO_DAYS, DEFAULT_PAGE_SIZE, DEFAULT_START_ROW);
    }

    public static String[] getServiceRequestsByCsn(String csn, String language, String noDays, int pageSize,
            int startRowNum) {
        csn = nz(csn);
        if (csn.isEmpty()) {
            return failedResult(CODE_INVALID_INPUT, "csn is required");
        }
        JSONObject sr = new JSONObject();
        sr.put("Account Id", "");
        sr.put("CSN", csn);
        sr.put("SR Type", "");
        sr.put("SR Sub Type", "");
        sr.put("Status", "");
        sr.put("Created By Name", "");
        return call(sr, language, noDays, pageSize, startRowNum, false);
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
            // malformed JSON -> empty record
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

    private static String[] call(JSONObject serviceRequest, String language, String noDays, int pageSize,
            int startRowNum, boolean singleSr) {
        language = nz(language).isEmpty() ? DEFAULT_LANGUAGE : nz(language);
        noDays = nz(noDays).isEmpty() ? DEFAULT_NO_DAYS : nz(noDays);

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

            String[] http = httpPost(request.toString());
            int statusCode = Integer.parseInt(http[0]);
            String responseText = http[1];

            if (statusCode < 200 || statusCode >= 300) {
                return errorResult("HTTP_" + statusCode, "Non-2xx response", responseText);
            }

            JSONObject json = new JSONObject(responseText);
            String[] r = newResult();
            boolean failed = applyApiStatus(r, json);
            r[IDX_RAW_RESPONSE] = responseText;

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
                r[IDX_STATUS] = STATUS_FAILED;
                r[IDX_CODE] = CODE_NOT_FOUND;
                if (r[IDX_MESSAGE].isEmpty()) {
                    r[IDX_MESSAGE] = "No status returned for SR " + str(serviceRequest, "SR Number");
                }
            }
            return r;

        } catch (SocketTimeoutException e) {
            return errorResult(CODE_TIMEOUT, e.getMessage(), "");
        } catch (Exception e) {
            return errorResult(CODE_EXCEPTION, e.getClass().getSimpleName() + ": " + e.getMessage(), "");
        }
    }

    private static String[] httpPost(String requestJson) throws IOException {
        byte[] requestBytes = requestJson.getBytes(StandardCharsets.UTF_8);
        if (DEBUG) {
            log("POST " + API_URL);
            log("Request body -> " + requestJson);
        }
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

            if (DEBUG) {
                log("Response HTTP status -> " + statusCode);
                log("Response body -> " + responseText);
            }
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
        r[IDX_SR_COUNT] = "0";
        r[IDX_SR_LIST_JSON] = "[]";
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

    /** "Error Code" null, "" or "0" = success. Returns true on API error. */
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

    private static void log(String message) {
        System.out.println("ServiceRequestStatusClient: " + message);
    }

    // CLI: java -cp "out;lib/json-20240303.jar" flow.ServiceRequestStatusClient sr 120802411138
    //      java -cp "out;lib/json-20240303.jar" flow.ServiceRequestStatusClient csn 1298 [pageSize] [startRowNum]
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: flow.ServiceRequestStatusClient sr <srNumberDigits>");
            System.out.println("       flow.ServiceRequestStatusClient csn <csn> [pageSize] [startRowNum]");
            System.exit(2);
        }
        String[] r;
        if ("sr".equalsIgnoreCase(args[0])) {
            r = getServiceRequestStatus(args[1]);
        } else if ("csn".equalsIgnoreCase(args[0])) {
            int pageSize = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_PAGE_SIZE;
            int startRow = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_START_ROW;
            r = getServiceRequestsByCsn(args[1], DEFAULT_LANGUAGE, DEFAULT_NO_DAYS, pageSize, startRow);
        } else {
            System.out.println("Unknown mode '" + args[0] + "'. Use sr | csn");
            System.exit(2);
            return;
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
            System.out.println(String.format("%-16s = %s", labels[i], values[i]));
        }
        System.out.println("----------------------------------------");
    }
}

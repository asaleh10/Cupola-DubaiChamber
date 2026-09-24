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
 * GenericGetUserProfileAPI - find a user by mobile number and list their accounts.
 * Returns String[]: [0]=status SUCCESS|FAILED|ERROR, [1]=code, [2]=message, [3..]=every response field (see IDX_*).
 */
public class UserProfileClient {

    private static final String API_URL =
            "https://apisit.dubaichamber.com/dcci/DCCICPIntegration/GenericGetUserProfileAPI";
    private static final String API_KEY = "_5oGKrI3be5GtHMn4WjALPsEvzjpn3T-4nGwSvrNcCE";
    private static final String PROCESS_NAME = "DC Get User Profile Details Generic WF";

    private static final String EMIRATES_ID = "";
    private static final String EMAIL_ADDR = "";
    private static final String LOGIN_NAME = "";

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;

    public static boolean DEBUG = true;

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
    public static final int RESULT_SIZE = 25;

    public static final String[] LABELS = {
            "status", "code", "message", "firstName", "lastName", "email", "phone", "homePhone", "emiratesId",
            "dateOfBirth", "nationality", "jobTitle", "loginName", "userStatus", "userType",
            "registrationSource", "accountCount", "firstAccountCsn", "firstAccountName", "accountsJson",
            "messageId", "intObjectName", "intObjectFormat", "messageType", "rawResponse" };

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

    /** mobileNumber with country code, no "+", e.g. "97121234234". */
    public static String[] getUserProfileByMobile(String mobileNumber) {
        mobileNumber = nz(mobileNumber);
        if (mobileNumber.isEmpty()) {
            return failedResult(CODE_INVALID_INPUT, "mobileNumber is required");
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

            String[] http = httpPost(request.toString());
            int statusCode = Integer.parseInt(http[0]);
            String responseText = http[1];

            if (statusCode < 200 || statusCode >= 300) {
                return errorResult("HTTP_" + statusCode, "Non-2xx response", responseText);
            }

            JSONObject json = new JSONObject(responseText);
            String[] r = newResult();
            boolean failed = applyApiStatus(r, json);
            r[IDX_ACCOUNT_COUNT] = "0";
            r[IDX_ACCOUNTS_JSON] = "[]";
            r[IDX_RAW_RESPONSE] = responseText;

            JSONObject siebMsg = obj(json, "SiebMsg");
            r[IDX_MESSAGE_ID] = str(siebMsg, "MessageId");
            r[IDX_INT_OBJECT_NAME] = str(siebMsg, "IntObjectName");
            r[IDX_INT_OBJECT_FORMAT] = str(siebMsg, "IntObjectFormat");
            r[IDX_MESSAGE_TYPE] = str(siebMsg, "MessageType");

            JSONObject user = obj(siebMsg, "User");
            if (user == null) {
                if (!failed) {
                    r[IDX_STATUS] = STATUS_FAILED;
                    r[IDX_CODE] = CODE_NOT_FOUND;
                    if (r[IDX_MESSAGE].isEmpty()) {
                        r[IDX_MESSAGE] = "No user profile returned";
                    }
                }
                return r;
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
            return r;

        } catch (SocketTimeoutException e) {
            return errorResult(CODE_TIMEOUT, e.getMessage(), "");
        } catch (Exception e) {
            return errorResult(CODE_EXCEPTION, e.getClass().getSimpleName() + ": " + e.getMessage(), "");
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
            // malformed JSON -> empty account
        }
        return acc;
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
        return r;
    }

    private static String[] errorResult(String code, String message, String rawResponse) {
        String[] r = newResult();
        r[IDX_STATUS] = STATUS_ERROR;
        r[IDX_CODE] = code;
        r[IDX_MESSAGE] = message == null ? "" : message;
        r[IDX_ACCOUNT_COUNT] = "0";
        r[IDX_ACCOUNTS_JSON] = "[]";
        r[IDX_RAW_RESPONSE] = rawResponse == null ? "" : rawResponse;
        return r;
    }

    private static String[] failedResult(String code, String message) {
        String[] r = newResult();
        r[IDX_STATUS] = STATUS_FAILED;
        r[IDX_CODE] = code;
        r[IDX_MESSAGE] = message;
        r[IDX_ACCOUNT_COUNT] = "0";
        r[IDX_ACCOUNTS_JSON] = "[]";
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
        System.out.println("UserProfileClient: " + message);
    }

    // CLI: java -cp "out;lib/json-20240303.jar" flow.UserProfileClient 97121234234
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: flow.UserProfileClient <mobileNumber>");
            System.exit(2);
        }
        String[] r = getUserProfileByMobile(args[0]);
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
            System.out.println(String.format("%-16s = %s", labels[i], values[i]));
        }
        System.out.println("----------------------------------------");
    }
}

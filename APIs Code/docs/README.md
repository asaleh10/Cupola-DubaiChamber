# Dubai Chamber IVR – Integration API Classes

Four self-contained Java classes that wrap the Dubai Chamber (DCCI) REST APIs used by the IVR, written in
the same style as the ADPF `SmsApiClient` so each one can be dropped into an Avaya Orchestration Designer
(OD) project on its own and called from a `servletImplementation` block.

| Class | API | Purpose |
|---|---|---|
| [ValidateMembershipClient](ValidateMembershipClient.md) | ValidateAccount | Validate a membership (CSN) number and return member / licence details |
| [UserProfileClient](UserProfileClient.md) | GenericGetUserProfileAPI | Identify a caller by mobile number and list their accounts |
| [ServiceRequestStatusClient](ServiceRequestStatusClient.md) | GetSRSummary | Status of one SR by number, or the list of SRs of a CSN |
| [PaymentLinkClient](PaymentLinkClient.md) | GenSendPaymentLink | Generate an ePay link for an SR and e-mail it to the user |

Each class is independent: it carries its own URL, key, HTTP code and JSON parsing. No class needs another.
Every class returns every field of the API response, plus the raw response text as the last element, so the
flow engineer can pick what the call flow needs.

## Files

```
APIs Code/
  ValidateMembershipClient.java
  UserProfileClient.java
  ServiceRequestStatusClient.java
  PaymentLinkClient.java
  lib/json-20240303.jar             org.json (same library the SMS sample uses)
  build.cmd / build.sh              compile everything into ./out for CLI testing
  docs/                             this documentation
```

All classes are in package `flow`, Java 8 compatible, and depend only on `org.json`.

## Adding to the OD project

1. Copy the `.java` file(s) you need into the OD project's `src/flow` folder (same place as `SmsApiClient`).
2. Make sure `org.json` is on the project classpath (`WEB-INF/lib/json-*.jar`). If the SMS client already
   compiles in the project, it is already there.
3. Call the static method from a servlet block, read the `String[]` result and copy the values you need into
   project variables. Example:

```java
String[] r = ValidateMembershipClient.validateMember(memberNo);

mySession.getVariableField(IProjectVariables.API__MEMBER__STATUS)
         .setValue(r[ValidateMembershipClient.IDX_STATUS]);          // SUCCESS / FAILED / ERROR
mySession.getVariableField(IProjectVariables.API__MEMBER__NAME)
         .setValue(r[ValidateMembershipClient.IDX_MEMBER_NAME]);
```

## Configuration

Every setting has a default inside the class (SIT values) and can be overridden **without recompiling**, either with
a JVM system property or with a static setter called from an OD servlet block. The same property names work for all
four classes, except the URL which is per class.

| Property | Setter | Default | Meaning |
|---|---|---|---|
| `dc.validate.url` / `dc.userprofile.url` / `dc.srstatus.url` / `dc.payment.url` | `setApiUrl(String)` | SIT endpoint of each API | Full endpoint URL. |
| `dc.api.apiKey` | `setApiKey(String)` | SIT key | Sent as HTTP header `api-key`. |
| `dc.api.connectTimeoutMs` | `setConnectTimeoutMs(int)` | `5000` | TCP / TLS connect timeout. |
| `dc.api.readTimeoutMs` | `setReadTimeoutMs(int)` | `10000` | Wait for the response. |
| `dc.api.trustAll` | `setTrustAllCertificates(boolean)` | `true` | Skips certificate and hostname checks, so self-signed or expired certificates do not block the call. Set `false` to enforce certificate validation. |
| `dc.api.logDir` | `setLogDir(String)` | `<catalina.base>/logs`, else `./logs` | Root log folder. Each class creates its own sub-folder under it. |
| `dc.api.logRetentionDays` | `setLogRetentionDays(int)` | `30` | Daily files to keep: today plus N-1 previous days. `7` = one week, `365` = one year, `0` = never delete. |
| `dc.api.consoleLogging` | `setConsoleLogging(boolean)` | `false` | Also print every log line to stdout (catalina.out). The CLI turns it on. |
| `dc.api.logMaskSecrets` | `setMaskSecrets(boolean)` | `true` | Write the api-key as `_5oGKrI3be5****NcCE` in the log. `false` writes it in full. |

Tomcat, in `bin/setenv.bat` (Windows) or `bin/setenv.sh`:

```bat
set CATALINA_OPTS=%CATALINA_OPTS% -Ddc.api.logRetentionDays=7 -Ddc.api.trustAll=false
```

Or from OD, once, for example in the first servlet block of the application:

```java
ValidateMembershipClient.setTrustAllCertificates(false);
ValidateMembershipClient.setLogRetentionDays(7);
```

Fixed request values that are not configuration (`ProcessName`, the empty licence fields, `LoginName` /
`PaymentType` of the payment API) are constants at the top of each class.
## Result convention (all four classes)

Every method returns a `String[]`. Elements are never `null`; a missing value is `""`.

| Index | Constant | Meaning |
|---|---|---|
| 0 | `IDX_STATUS` | `SUCCESS` – call worked and data is present. `FAILED` – the API answered but rejected the request. `ERROR` – technical problem, see the code table below. |
| 1 | `IDX_CODE` | On `SUCCESS` / `FAILED`: the API `Error Code` as-is, or `INVALID_INPUT` / `NOT_FOUND`. On `ERROR`: one of the technical codes below. |
| 2 | `IDX_MESSAGE` | The API `Error Message`, or the technical error text. |
| 3.. | class specific | See each class document. |
| last three | `IDX_HTTP_STATUS`, `IDX_ELAPSED_MS`, `IDX_CALL_ID` | HTTP status (`""` when no answer), call duration in ms, and the id used in the log lines of this call. |

### Codes to branch on

| Status | Code | Meaning | API called? |
|---|---|---|---|
| `SUCCESS` | `` or `0` | Data returned | yes |
| `FAILED` | API code, e.g. `1`, `(SBL-...)` | Backend rejected the request; message is the API text | yes |
| `FAILED` | `NOT_FOUND` | HTTP 200 without error code but without the expected data block | yes |
| `FAILED` | `INVALID_INPUT` | Empty input, rejected locally | no |
| `ERROR` | `INVALID_CONFIG` | URL empty or not http(s) | no |
| `ERROR` | `AUTH_ERROR` | HTTP 401 / 403, api-key wrong or expired | yes |
| `ERROR` | `HTTP_ERROR` | Any other non-2xx HTTP status | yes |
| `ERROR` | `INVALID_RESPONSE` | HTTP 2xx but body is not JSON | yes |
| `ERROR` | `TIMEOUT` | Connect or read timeout | yes |
| `ERROR` | `CONNECTION_ERROR` | Host unknown, connection refused, no route, other I/O error | attempted |
| `ERROR` | `SSL_ERROR` | Certificate or TLS failure; only possible when `dc.api.trustAll=false` | attempted |
| `ERROR` | `ERROR` | Anything unexpected; stack trace in the log | – |

Verified on 24 Sep 2026 against SIT: 404 gives `HTTP_ERROR`, a wrong key gives `AUTH_ERROR` (HTTP 403), an unknown
host gives `CONNECTION_ERROR`, an expired certificate passes the TLS step with the default `trustAll=true` and gives
`SSL_ERROR` with `trustAll=false`.

The status strings are also available as constants on each class (`STATUS_SUCCESS`, `STATUS_FAILED`,
`STATUS_ERROR`). `result[IDX_STATUS]` tells whether the call itself worked. Business values such as a member
status or an SR status are returned exactly as the API sends them; the call flow decides what they mean.

## Logging

Each class writes its own daily file, UTF-8, one entry per line:

```
<logDir>/<ClassName>/<ClassName>_yyyy-MM-dd.log

e.g.  C:\Tomcat\logs\ValidateMembershipClient\ValidateMembershipClient_2026-09-24.log
      C:\Tomcat\logs\UserProfileClient\UserProfileClient_2026-09-24.log
      C:\Tomcat\logs\ServiceRequestStatusClient\ServiceRequestStatusClient_2026-09-24.log
      C:\Tomcat\logs\PaymentLinkClient\PaymentLinkClient_2026-09-24.log
```

Every call produces these lines, all with the same 8-character call id (also returned in `IDX_CALL_ID`):

```
2026-09-24 23:14:53.820 INFO [0b21fe01] START validateMember memberNo=1298
2026-09-24 23:14:53.826 INFO [0b21fe01] REQUEST POST https://apisit.dubaichamber.com/.../ValidateAccount (connectTimeout=5000ms, readTimeout=10000ms, trustAll=false)
2026-09-24 23:14:53.826 INFO [0b21fe01] REQUEST HEADERS Content-Type=application/json; charset=UTF-8, Accept=application/json, api-key=_5oGKrI3be5****NcCE
2026-09-24 23:14:53.826 INFO [0b21fe01] REQUEST BODY {"body":{"LicenseNo":"","ProcessName":"DC Validate Member Info WF",...}}
2026-09-24 23:14:55.450 INFO [0b21fe01] RESPONSE HTTP 200 in 1624 ms
2026-09-24 23:14:55.451 INFO [0b21fe01] RESPONSE BODY { "Error Code" : 0, "LicenseNo" : "1000011111", ... }
2026-09-24 23:14:55.452 INFO [0b21fe01] END status=SUCCESS code=0 message=2010-07-10 http=200 elapsedMs=1632
```

Levels: `INFO` normal, `WARN` the API rejected the request (`FAILED`), `ERROR` technical failure (`ERROR`); an
unexpected exception is logged with its stack trace. Response bodies are cut at 4000 characters in the log; the
full body is always in `IDX_RAW_RESPONSE`.

Retention runs on the first write of each day, per class folder, and deletes only that class's dated files older
than `logRetentionDays`. Other files in the folder are untouched. A logging failure never affects the API call: the
call returns normally and one line goes to stderr.

## SSL / TLS

Certificate and hostname checks are **skipped by default** (`dc.api.trustAll` = `true`), so a self-signed or expired
certificate on the API side does not block calls. To enforce certificate validation set `dc.api.trustAll=false`
(or call `setTrustAllCertificates(false)`); with validation on, a bad certificate returns `ERROR` / `SSL_ERROR`.
## Building and testing from the command line

Requirements: a JDK (8 or newer) on the PATH. The machine must be able to reach `apisit.dubaichamber.com`.

Windows:

```bat
cd "APIs Code"
build.cmd
java -cp "out;lib\json-20240303.jar" flow.ValidateMembershipClient 1298
```

Linux / macOS:

```sh
cd "APIs Code"
sh build.sh
java -cp out:lib/json-20240303.jar flow.ValidateMembershipClient 1298
```

To test a single class you can also compile just that file:

```bat
javac -encoding UTF-8 -cp lib\json-20240303.jar -d out ValidateMembershipClient.java
```

Each class prints its log lines to the console, then a labelled result table. The process exit code is `0` for
`SUCCESS`, `1` for `FAILED` / `ERROR`, `2` for wrong CLI arguments. Every CLI accepts the options `--trustall`,
`--url URL`, `--apikey KEY` and `--logdir DIR` after the input value, and writes its log under `.\logs\<ClassName>\`.

Windows PowerShell notes:

- Quote JVM options that contain a dot: `java "-Ddc.api.trustAll=false" -cp ...`.
- An empty argument must be written as `'""'`, otherwise PowerShell drops it.

## Verification log

Tested live against SIT on 21 Sep 2026 with the CLI:

| Class | Input | Result |
|---|---|---|
| ValidateMembershipClient | `1298` | SUCCESS, member DUBAI CHAMBER COMMERCE, status Hold |
| ValidateMembershipClient | `246884` | SUCCESS, AL MARWAN HEAVY EQUIP & MACHINERY TR, status Active - Renew |
| ValidateMembershipClient | `12222298` | FAILED, code 1, "Invalid Member/License No" |
| UserProfileClient | `97121234234` | SUCCESS, Chandan Gowda, login TESTUSERSIT, 15 accounts |
| UserProfileClient | `971506584588` | FAILED, code 1, "User details not found for the given input." |
| ServiceRequestStatusClient | `sr 120802411138` | SUCCESS, "Approved and Payment Awaited" |
| ServiceRequestStatusClient | `csn 1298` | SUCCESS, 464 total records, 10 returned in page |
| ServiceRequestStatusClient | `sr 199999999999` | FAILED, Siebel code SBL-CMI-00122 "No record matching..." |
| PaymentLinkClient | `120804978308` | Compiled and verified against the Postman capture only. Not executed from the CLI, because every call creates a transaction and sends an e-mail. |

Note: SIT test data changes from day to day. The Postman collection mobile `971506584588` stopped resolving before
21 Sep; `97121234234` resolved on 21 and 22 Sep but returned "User details not found" on 24 Sep. Member 1298 showed
status `Hold` on 21 Sep and `Active` on 24 Sep. Re-check a value in Postman before treating a `FAILED` as a code
problem.

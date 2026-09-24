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

## Environment settings

Each class has its configuration at the top of the file. Change these in every class you deploy when moving
from SIT to UAT / PROD:

| Constant | Current value (SIT) | In which classes |
|---|---|---|
| `API_URL` | `https://apisit.dubaichamber.com/dcci/DCCICPIntegration/...` | all |
| `API_KEY` | `_5oGKrI3be5GtHMn4WjALPsEvzjpn3T-4nGwSvrNcCE`, sent as HTTP header `api-key`. The only header besides Content-Type / Accept. | all |
| `CONNECT_TIMEOUT_MS` / `READ_TIMEOUT_MS` | 5000 / 10000 | all |
| `LOG_*` | logging settings, see [Logging](#logging) below | all |
| `LOGIN_NAME` / `PAYMENT_TYPE` | `TESTUSERSIT` / `DubaiPay` | PaymentLinkClient |
| `LICENSE_NO` / `LICENSE_AUTH` / `LICENSE_TYPE` | `""` | ValidateMembershipClient |
| `EMIRATES_ID` / `EMAIL_ADDR` / `LOGIN_NAME` | `""` | UserProfileClient |

## Result convention (all four classes)

Every method returns a `String[]`. Elements are never `null`; a missing value is `""`.

| Index | Constant | Meaning |
|---|---|---|
| 0 | `IDX_STATUS` | `SUCCESS` – call worked and data is present. `FAILED` – the API answered but reported an error or no data. `ERROR` – HTTP non-2xx, timeout or exception. |
| 1 | `IDX_CODE` | `SUCCESS`/`FAILED`: the API `Error Code`, or `INVALID_INPUT`, `NOT_FOUND`. `ERROR`: `HTTP_<status>`, `TIMEOUT` or `EXCEPTION`. |
| 2 | `IDX_MESSAGE` | The API `Error Message`, or the technical error text. |
| 3.. | class specific | See each class document. |

The status strings are also available as constants on each class (`STATUS_SUCCESS`, `STATUS_FAILED`,
`STATUS_ERROR`). `result[IDX_STATUS]` tells whether the call itself worked. Business values such as a member
status or an SR status are returned exactly as the API sends them; the call flow decides what they mean.

## Logging

Each class writes its own daily log file:

```
<LOG_DIR>/<ClassName>/<ClassName>_yyyy-MM-dd.log

e.g.  C:\Tomcat\logs\ValidateMembershipClient\ValidateMembershipClient_2026-09-24.log
      C:\Tomcat\logs\UserProfileClient\UserProfileClient_2026-09-24.log
      C:\Tomcat\logs\ServiceRequestStatusClient\ServiceRequestStatusClient_2026-09-24.log
      C:\Tomcat\logs\PaymentLinkClient\PaymentLinkClient_2026-09-24.log
```

`LOG_DIR` defaults to `<catalina.base>/logs`, which is the Tomcat `logs` folder when the class runs inside OD /
Tomcat. Outside Tomcat (CLI tests) it falls back to `<working directory>/logs`.

Every call writes these lines, all carrying the same 8-character call id so one call can be followed end to end:

```
2026-09-24 10:02:06.758 INFO  [c00c913e] CALL validateMember memberNo=1298
2026-09-24 10:02:06.766 INFO  [c00c913e] REQUEST POST https://apisit.dubaichamber.com/dcci/DCCICPIntegration/ValidateAccount_REST/ValidateAccount
2026-09-24 10:02:06.766 INFO  [c00c913e] REQUEST headers Content-Type=application/json; charset=UTF-8 | Accept=application/json | api-key=_5oG...
2026-09-24 10:02:06.766 INFO  [c00c913e] REQUEST body {"body":{"LicenseNo":"","ProcessName":"DC Validate Member Info WF",...}}
2026-09-24 10:02:08.214 INFO  [c00c913e] RESPONSE HTTP 200 in 1448 ms
2026-09-24 10:02:08.215 INFO  [c00c913e] RESPONSE body {  "Error Code" : 0,  "LicenseNo" : "1000011111", ...}
2026-09-24 10:02:08.216 INFO  [c00c913e] RESULT status=SUCCESS code=0 message=2010-07-10
```

Line format: `date time LEVEL [callId] message`. Levels: `INFO` normal flow, `WARN` the API answered with a business
error (result `FAILED`), `ERROR` HTTP non-2xx, timeout or exception (result `ERROR`). Exceptions are logged with the
full stack trace. Files are UTF-8; open them with a UTF-8 aware editor to see Arabic names correctly.

### Configuration

Defaults are constants at the top of each class. They can be overridden without recompiling through JVM system
properties, for Tomcat in `bin/setenv.bat` or `bin/setenv.sh`:

```bat
set CATALINA_OPTS=%CATALINA_OPTS% -Ddc.api.log.retentionDays=7 -Ddc.api.log.console=false -Ddc.api.log.maskApiKey=true
```

| Property | Default | Meaning |
|---|---|---|
| `dc.api.log.dir` | `<catalina.base>/logs`, else `<working dir>/logs` | Root folder. Each class creates its own sub-folder under it. |
| `dc.api.log.retentionDays` | `30` | Daily files to keep: today plus N-1 previous days. `7` keeps one week, `365` one year, `0` never deletes. |
| `dc.api.log.enabled` | `true` | `false` stops writing files (console echo still follows `dc.api.log.console`). |
| `dc.api.log.console` | `true` | Also echo every line to stdout (catalina.out). Set `false` in production to avoid double logging. |
| `dc.api.log.maskApiKey` | `false` | `true` writes the api-key as `_5oG****NcCE` instead of the full value. |

The same property names are read by all four classes, so one setting covers all of them.

### Retention

On the first write of each day, each class deletes files in its own folder whose name matches
`<ClassName>_yyyy-MM-dd.log` and whose date is outside the retention window. Other files in the folder are not
touched. Verified on 24 Sep 2026 with `retentionDays=7`: files dated 09-10 and 09-17 were removed, 09-18 and an
unrelated `other.txt` were kept.

A logging problem never affects the API call. If the folder cannot be created or written, the call still returns
its normal result and a one-line notice `<ClassName>: cannot write log file: ...` goes to stderr. Verified with an
unreachable drive letter as `dc.api.log.dir`.

## Error handling

What the classes already do:

- **Never throw.** Every failure comes back inside the `String[]` so an OD servlet block cannot crash on it.
- **Input validation** before the call: empty input returns `FAILED` / `INVALID_INPUT` without touching the network.
- **Timeouts**: 5 s to connect, 10 s to read (`CONNECT_TIMEOUT_MS`, `READ_TIMEOUT_MS`). A timeout returns
  `ERROR` / `TIMEOUT`.
- **HTTP failures**: any non-2xx returns `ERROR` / `HTTP_<status>` with the error body kept in `IDX_RAW_RESPONSE`.
- **API business errors**: a non-zero `Error Code` returns `FAILED` with the API code and message as-is.
- **Unparseable response** or any other exception returns `ERROR` / `EXCEPTION` with the exception text; the stack
  trace is in the log.
- **Everything logged** as described above, with a call id.

Recommended handling in the call flow, by `result[IDX_STATUS]`:

| Status | Typical codes | Meaning | Suggested flow behaviour |
|---|---|---|---|
| `SUCCESS` | `` or `0` | Call worked, data present | Continue. Business values (member status, SR status) are as-is; the flow decides. |
| `FAILED` | API code e.g. `1`, Siebel `SBL-...`, `NOT_FOUND` | Backend understood the request but found nothing / rejected it | Tell the caller the number was not found; allow re-entry a limited number of times (e.g. 3), then offer an agent. |
| `FAILED` | `INVALID_INPUT` | Empty input reached the class | Treat as a flow bug: check the variable feeding the class. |
| `ERROR` | `TIMEOUT`, `HTTP_5xx`, `EXCEPTION` | Technical problem, backend or network | Play a technical-difficulty prompt and offer a transfer. Never read `message` or `rawResponse` to the caller. |
| `ERROR` | `HTTP_401` / `HTTP_403` | api-key wrong or expired | Same as above for the caller; alert operations, it will affect every call. |

Other recommendations:

- **Retry**: at most one retry, only on `ERROR` with `TIMEOUT` or `HTTP_5xx`, and only for the read-only classes
  (`ValidateMembershipClient`, `UserProfileClient`, `ServiceRequestStatusClient`). Never retry `PaymentLinkClient`
  automatically: a timeout may have happened after the backend already created the transaction and sent the e-mail.
- **Caller wait time**: worst case a call can take about 15 s (connect + read timeout). Play a "please wait" prompt
  before the servlet block, or lower the timeouts in the class if the IVR budget is tighter.
- **Prompts**: map `SUCCESS` / `FAILED` / `ERROR` to three fixed prompt groups in the flow so every API behaves the
  same to the caller.
- **Monitoring**: count `ERROR` lines per day in the log folders; a spike usually means a backend or network issue.
- **Production**: set `dc.api.log.console=false` and `dc.api.log.maskApiKey=true`; keep file logging on.
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

Each class prints the outgoing request, the raw response, a labelled result table and the path of the log file it
wrote (under `.\logs\<ClassName>\` when run from the CLI). The process exit code is `0` for `SUCCESS`, `1` for
`FAILED` / `ERROR`, `2` for wrong CLI arguments.

Windows PowerShell notes:

- Quote JVM options that contain a dot: `java "-Dfile.encoding=UTF-8" -cp ...` (needed to print Arabic names correctly).
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

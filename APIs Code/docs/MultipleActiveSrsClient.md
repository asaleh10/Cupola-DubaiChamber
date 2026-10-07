# MultipleActiveSrsClient

E-mails the member the list of their active service requests and returns whether there are any, how many, and
the SR number when there is exactly one.

| | |
|---|---|
| Class | `flow.MultipleActiveSrsClient` |
| Endpoint | `POST https://apisit.dubaichamber.com/dcci/DCCICPINTEGRATION/DCCICXPROJECTIVR_APIS/1.0/MultipleActiveSRs` |
| Process name | `DC Active SR IVR WF` |
| Headers | `api-key` only (masked in the log by default) |
| Side effects | **Yes.** Each successful call sends an e-mail to the member. |

## Methods

```java
public static String[] emailActiveServiceRequests(String membershipNumber, String callerId, String action)
```

## Inputs

| Parameter | Required | Example | Sent as | Notes |
|---|---|---|---|---|
| `membershipNumber` | Yes | `1298` | `Membership Number` | Member number (CSN). |
| `callerId` | Yes | `TESTUSERSIT` | `Caller ID` | Login name of the caller, from `UserProfileClient` result `IDX_LOGIN_NAME`. |
| `action` | Yes | `EMAIL` | `Action` | Normally `EMAIL`; the constant `ACTION_EMAIL` holds it. |

`ProcessName` is a constant inside the class. If any of the three inputs is empty the method returns `FAILED` /
`INVALID_INPUT` without calling the API.

## Output – `String[16]`

Every response field is mapped. Values are never `null`; a JSON `null` becomes `""`.

| Index | Constant | Label | Source field | Example |
|---|---|---|---|---|
| 0 | `IDX_STATUS` | status | – | `SUCCESS` |
| 1 | `IDX_CODE` | code | `Error Code` | `0` |
| 2 | `IDX_MESSAGE` | message | `Error Message` | `` |
| 3 | `IDX_HAS_ACTIVE_SR` | hasActiveSr | `Has Active SR` | `true` / `false`, as returned |
| 4 | `IDX_ACTIVE_SR_COUNT` | activeSrCount | `Active SR Count` | `250` |
| 5 | `IDX_SINGLE_SR_NUMBER` | singleSrNumber | `Single SR Number` | `` (filled when exactly one SR is active) |
| 6 | `IDX_EMAIL_STATUS` | emailStatus | `Email Status` | `Email Initiated to the User` |
| 7 | `IDX_MEMBERSHIP_NUMBER` | membershipNumber | `Membership Number` | `1298` |
| 8 | `IDX_CALLER_ID` | callerId | `Caller ID` | `TESTUSERSIT` |
| 9 | `IDX_SIEBEL_OPERATION_OBJECT_ID` | siebelOperationObjectId | `Siebel Operation Object Id` | `1-9K9IOVB` |
| 10 | `IDX_PROCESS_INSTANCE_ID` | processInstanceId | `Process Instance Id` | `1-9K9IOVA` |
| 11 | `IDX_OBJECT_ID` | objectId | `Object Id` | `` |
| 12 | `IDX_RAW_RESPONSE` | rawResponse | whole response body as received | |
| 13 | `IDX_HTTP_STATUS` | httpStatus | HTTP status, `""` when no answer | `200` |
| 14 | `IDX_ELAPSED_MS` | elapsedMs | call duration in milliseconds | |
| 15 | `IDX_CALL_ID` | callId | id used in the log lines of this call | |

`hasActiveSr`, `activeSrCount`, `singleSrNumber` and `emailStatus` are returned exactly as the API sends them; the
call flow decides what to do with them.

Call status values:

- `SUCCESS` – HTTP 200 and `Error Code` `0` (or empty).
- `FAILED` / API error code – backend rejected the request, see `message`.
- `FAILED` / `INVALID_INPUT` – an input is empty, API not called.
- `ERROR` / `INVALID_CONFIG`, `AUTH_ERROR`, `HTTP_ERROR`, `INVALID_RESPONSE`, `TIMEOUT`, `CONNECTION_ERROR`, `SSL_ERROR`, `ERROR` – technical problem, see [README – Codes to branch on](README.md#codes-to-branch-on).

## Configuration and logging

URL: property `dc.multiplesr.url` or `setApiUrl()`. Key, timeouts, trust-all SSL and logging are the shared `dc.api.*` properties / setters, see [README – Configuration](README.md#configuration). Every call writes to `<logDir>/MultipleActiveSrsClient/MultipleActiveSrsClient_yyyy-MM-dd.log` with the call id returned in `IDX_CALL_ID`.

## Usage in an OD servlet block

Call once, then copy the values you need into project variables using the `IDX_*` constants.

```java
String csn      = mySession.getVariableField(IProjectVariables.API__USER__CSN).getStringValue();
String callerId = mySession.getVariableField(IProjectVariables.API__USER__LOGIN_NAME).getStringValue();

String[] r = MultipleActiveSrsClient.emailActiveServiceRequests(csn, callerId, MultipleActiveSrsClient.ACTION_EMAIL);

mySession.getVariableField(IProjectVariables.API__MSR__CALL_STATUS).setValue(r[MultipleActiveSrsClient.IDX_STATUS]);   // SUCCESS / FAILED / ERROR
mySession.getVariableField(IProjectVariables.API__MSR__CODE).setValue(r[MultipleActiveSrsClient.IDX_CODE]);
mySession.getVariableField(IProjectVariables.API__MSR__MESSAGE).setValue(r[MultipleActiveSrsClient.IDX_MESSAGE]);
mySession.getVariableField(IProjectVariables.API__MSR__HAS_ACTIVE).setValue(r[MultipleActiveSrsClient.IDX_HAS_ACTIVE_SR]);   // "true" / "false"
mySession.getVariableField(IProjectVariables.API__MSR__COUNT).setValue(r[MultipleActiveSrsClient.IDX_ACTIVE_SR_COUNT]);
mySession.getVariableField(IProjectVariables.API__MSR__SINGLE_SR).setValue(r[MultipleActiveSrsClient.IDX_SINGLE_SR_NUMBER]);
```

Any other field is read the same way with its `IDX_*` constant from the table above.

## CLI test

Sends a real e-mail to the member each time. Only run it with the SIT test member.

```bat
build.cmd
java -cp "out;lib\json-20240303.jar" flow.MultipleActiveSrsClient 1298 TESTUSERSIT EMAIL
```

Usage: `flow.MultipleActiveSrsClient <membershipNumber> <callerId> <action> [--trustall] [--url URL] [--apikey KEY] [--logdir DIR]`

Expected output, based on the response captured in Postman on 7 Oct 2026 (not executed from the CLI, because
every call sends an e-mail):

```
---------------- RESULT ----------------
status                   = SUCCESS
code                     = 0
message                  =
hasActiveSr              = true
activeSrCount            = 250
singleSrNumber           =
emailStatus              = Email Initiated to the User
membershipNumber         = 1298
callerId                 = TESTUSERSIT
siebelOperationObjectId  = 1-9K9IOVB
processInstanceId        = 1-9K9IOVA
objectId                 =
rawResponse              = {"Error Code":"0", ...}
httpStatus               = 200
elapsedMs                = ...
callId                   = ...
----------------------------------------
```

## Equivalent curl (Git Bash / Linux)

```sh
curl -s -X POST "https://apisit.dubaichamber.com/dcci/DCCICPINTEGRATION/DCCICXPROJECTIVR_APIS/1.0/MultipleActiveSRs" \
  -H "Content-Type: application/json" \
  -H "api-key: _5oGKrI3be5GtHMn4WjALPsEvzjpn3T-4nGwSvrNcCE" \
  -d '{"body":{"ProcessName":"DC Active SR IVR WF","Action":"EMAIL","Caller ID":"TESTUSERSIT","Membership Number":"1298"}}'
```

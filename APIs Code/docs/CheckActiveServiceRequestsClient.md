# CheckActiveServiceRequestsClient

Tells whether a member has active service requests, how many, and the SR number when there is exactly one.

| | |
|---|---|
| Class | `flow.CheckActiveServiceRequestsClient` |
| Endpoint | `POST https://apisit.dubaichamber.com/dcci/DCCICPINTEGRATION/DCCICXPROJECTIVR_APIS/1.0/CheckActiveServiceRequests` |
| Process name | `DC Active SR IVR WF` |
| Headers | `api-key` only (masked in the log by default) |
| Side effects | None with action `CHECK` |

## Methods

```java
public static String[] checkActiveServiceRequests(String membershipNumber, String callerId, String action)
```

## Inputs

| Parameter | Required | Example | Sent as | Notes |
|---|---|---|---|---|
| `membershipNumber` | Yes | `1298` | `Membership Number` | Member number (CSN). |
| `callerId` | Yes | `TESTUSERSIT` | `Caller ID` | Login name of the caller, from `UserProfileClient` result `IDX_LOGIN_NAME`. |
| `action` | Yes | `CHECK` | `Action` | Normally `CHECK`; the constant `ACTION_CHECK` holds it. |

`ProcessName` is a constant inside the class. If any of the three inputs is empty the method returns `FAILED` /
`INVALID_INPUT` without calling the API.

## Output – `String[16]`

Every response field is mapped. Values are never `null`; a JSON `null` becomes `""`.

| Index | Constant | Label | Source field | Example |
|---|---|---|---|---|
| 0 | `IDX_STATUS` | status | – | `SUCCESS` |
| 1 | `IDX_CODE` | code | `Error Code` | `` |
| 2 | `IDX_MESSAGE` | message | `Error Message` | `` |
| 3 | `IDX_HAS_ACTIVE_SR` | hasActiveSr | `Has Active SR` | `true` / `false`, as returned |
| 4 | `IDX_ACTIVE_SR_COUNT` | activeSrCount | `Active SR Count` | `248` |
| 5 | `IDX_SINGLE_SR_NUMBER` | singleSrNumber | `Single SR Number` | `` (filled when exactly one SR is active) |
| 6 | `IDX_EMAIL_STATUS` | emailStatus | `Email Status` | `NOT_REQUESTED` |
| 7 | `IDX_MEMBERSHIP_NUMBER` | membershipNumber | `Membership Number` | `1298` |
| 8 | `IDX_CALLER_ID` | callerId | `Caller ID` | `TESTUSERSIT` |
| 9 | `IDX_SIEBEL_OPERATION_OBJECT_ID` | siebelOperationObjectId | `Siebel Operation Object Id` | `1-9K9IOV6` |
| 10 | `IDX_PROCESS_INSTANCE_ID` | processInstanceId | `Process Instance Id` | `1-9K9IOV5` |
| 11 | `IDX_OBJECT_ID` | objectId | `Object Id` | `` |
| 12 | `IDX_RAW_RESPONSE` | rawResponse | whole response body as received | |
| 13 | `IDX_HTTP_STATUS` | httpStatus | HTTP status, `""` when no answer | `200` |
| 14 | `IDX_ELAPSED_MS` | elapsedMs | call duration in milliseconds | |
| 15 | `IDX_CALL_ID` | callId | id used in the log lines of this call | |

`hasActiveSr`, `activeSrCount` and `singleSrNumber` are returned exactly as the API sends them; the call flow
decides what to do with them.

Call status values:

- `SUCCESS` – HTTP 200 and `Error Code` empty (or `0`).
- `FAILED` / code `1` – `Invalid Membership Number`.
- `FAILED` / `INVALID_INPUT` – an input is empty, API not called.
- `ERROR` / `INVALID_CONFIG`, `AUTH_ERROR`, `HTTP_ERROR`, `INVALID_RESPONSE`, `TIMEOUT`, `CONNECTION_ERROR`, `SSL_ERROR`, `ERROR` – technical problem, see [README – Codes to branch on](README.md#codes-to-branch-on).

## Configuration and logging

URL: property `dc.activesr.url` or `setApiUrl()`. Key, timeouts, trust-all SSL and logging are the shared `dc.api.*` properties / setters, see [README – Configuration](README.md#configuration). Every call writes to `<logDir>/CheckActiveServiceRequestsClient/CheckActiveServiceRequestsClient_yyyy-MM-dd.log` with the call id returned in `IDX_CALL_ID`.

## Usage in an OD servlet block

Call once, then copy the values you need into project variables using the `IDX_*` constants.

```java
String csn      = mySession.getVariableField(IProjectVariables.API__USER__CSN).getStringValue();
String callerId = mySession.getVariableField(IProjectVariables.API__USER__LOGIN_NAME).getStringValue();

String[] r = CheckActiveServiceRequestsClient.checkActiveServiceRequests(csn, callerId, CheckActiveServiceRequestsClient.ACTION_CHECK);

mySession.getVariableField(IProjectVariables.API__ASR__CALL_STATUS).setValue(r[CheckActiveServiceRequestsClient.IDX_STATUS]);   // SUCCESS / FAILED / ERROR
mySession.getVariableField(IProjectVariables.API__ASR__CODE).setValue(r[CheckActiveServiceRequestsClient.IDX_CODE]);
mySession.getVariableField(IProjectVariables.API__ASR__MESSAGE).setValue(r[CheckActiveServiceRequestsClient.IDX_MESSAGE]);
mySession.getVariableField(IProjectVariables.API__ASR__HAS_ACTIVE).setValue(r[CheckActiveServiceRequestsClient.IDX_HAS_ACTIVE_SR]);   // "true" / "false"
mySession.getVariableField(IProjectVariables.API__ASR__COUNT).setValue(r[CheckActiveServiceRequestsClient.IDX_ACTIVE_SR_COUNT]);
mySession.getVariableField(IProjectVariables.API__ASR__SINGLE_SR).setValue(r[CheckActiveServiceRequestsClient.IDX_SINGLE_SR_NUMBER]);
```

Any other field is read the same way with its `IDX_*` constant from the table above.

## CLI test

```bat
build.cmd
java -cp "out;lib\json-20240303.jar" flow.CheckActiveServiceRequestsClient 1298 TESTUSERSIT CHECK
```

Usage: `flow.CheckActiveServiceRequestsClient <membershipNumber> <callerId> <action> [--trustall] [--url URL] [--apikey KEY] [--logdir DIR]`

Expected output (SIT, 7 Oct 2026):

```
---------------- RESULT ----------------
status                   = SUCCESS
code                     =
message                  =
hasActiveSr              = true
activeSrCount            = 248
singleSrNumber           =
emailStatus              = NOT_REQUESTED
membershipNumber         = 1298
callerId                 = TESTUSERSIT
siebelOperationObjectId  = 1-9K9IOV6
processInstanceId        = 1-9K9IOV5
objectId                 =
rawResponse              = {"Error Code":"", ...}
httpStatus               = 200
elapsedMs                = ...
callId                   = ...
----------------------------------------
```

Negative test:

```bat
java -cp "out;lib\json-20240303.jar" flow.CheckActiveServiceRequestsClient 12222298 TESTUSERSIT CHECK
```
```
status                   = FAILED
code                     = 1
message                  = Invalid Membership Number
```

## Equivalent curl (Git Bash / Linux)

```sh
curl -s -X POST "https://apisit.dubaichamber.com/dcci/DCCICPINTEGRATION/DCCICXPROJECTIVR_APIS/1.0/CheckActiveServiceRequests" \
  -H "Content-Type: application/json" \
  -H "api-key: _5oGKrI3be5GtHMn4WjALPsEvzjpn3T-4nGwSvrNcCE" \
  -d '{"body":{"ProcessName":"DC Active SR IVR WF","Action":"CHECK","Caller ID":"TESTUSERSIT","Membership Number":"1298"}}'
```

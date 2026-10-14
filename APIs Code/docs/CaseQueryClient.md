# CaseQueryClient

Counts a member's open cases, split into cases within SLA and cases outside SLA.

| | |
|---|---|
| Class | `flow.CaseQueryClient` |
| Endpoint | `POST https://apisit.dubaichamber.com/dcci/DCCICPINTEGRATION/DCCICXPROJECTIVR_APIS/1.0/CaseQuery` |
| Process name | `DC Case Query IVR WF` |
| Headers | `api-key` only (masked in the log by default) |
| Side effects | None (read-only) |

## Methods

```java
public static String[] queryCases(String membershipNumber)
```

## Inputs

| Parameter | Required | Example | Sent as | Notes |
|---|---|---|---|---|
| `membershipNumber` | Yes | `1298` | `Membership Number` | Member number (CSN). |

`ProcessName` is a constant inside the class. Empty `membershipNumber` returns `FAILED` / `INVALID_INPUT`
without calling the API.

## Output – `String[14]`

Every response field is mapped. Values are never `null`; a JSON `null` becomes `""`.

| Index | Constant | Label | Source field | Example |
|---|---|---|---|---|
| 0 | `IDX_STATUS` | status | – | `SUCCESS` |
| 1 | `IDX_CODE` | code | `Error Code` | `` |
| 2 | `IDX_MESSAGE` | message | `Error Message` | `` |
| 3 | `IDX_TOTAL_OPEN_CASES` | totalOpenCases | `Total Open Cases` | `7` |
| 4 | `IDX_CASES_WITHIN_SLA` | casesWithinSla | `Cases Within SLA` | `0` |
| 5 | `IDX_CASES_OUTSIDE_SLA` | casesOutsideSla | `Cases Outside SLA` | `7` |
| 6 | `IDX_MEMBERSHIP_NUMBER` | membershipNumber | `Membership Number` | `1298` |
| 7 | `IDX_SIEBEL_OPERATION_OBJECT_ID` | siebelOperationObjectId | `Siebel Operation Object Id` | `1-29L-3967` |
| 8 | `IDX_PROCESS_INSTANCE_ID` | processInstanceId | `Process Instance Id` | `1-9KC5N6N` |
| 9 | `IDX_OBJECT_ID` | objectId | `Object Id` | `` |
| 10 | `IDX_RAW_RESPONSE` | rawResponse | whole response body as received | |
| 11 | `IDX_HTTP_STATUS` | httpStatus | HTTP status, `""` when no answer | `200` |
| 12 | `IDX_ELAPSED_MS` | elapsedMs | call duration in milliseconds | |
| 13 | `IDX_CALL_ID` | callId | id used in the log lines of this call | |

The three counts are returned exactly as the API sends them, as text; the call flow decides what to do with them.

Call status values:

- `SUCCESS` – HTTP 200 and `Error Code` empty (or `0`).
- `FAILED` / code `1` – `Invalid Membership Number`.
- `FAILED` / `INVALID_INPUT` – membershipNumber empty, API not called.
- `ERROR` / `INVALID_CONFIG`, `AUTH_ERROR`, `HTTP_ERROR`, `INVALID_RESPONSE`, `TIMEOUT`, `CONNECTION_ERROR`, `SSL_ERROR`, `ERROR` – technical problem, see [README – Codes to branch on](README.md#codes-to-branch-on).

## Configuration and logging

URL: property `dc.casequery.url` or `setApiUrl()`. Key, timeouts, trust-all SSL and logging are the shared `dc.api.*` properties / setters, see [README – Configuration](README.md#configuration). Every call writes to `<logDir>/CaseQueryClient/CaseQueryClient_yyyy-MM-dd.log` with the call id returned in `IDX_CALL_ID`.

## Usage in an OD servlet block

Call once, then copy the values you need into project variables using the `IDX_*` constants.

```java
String csn = mySession.getVariableField(IProjectVariables.API__USER__CSN).getStringValue();

String[] r = CaseQueryClient.queryCases(csn);

mySession.getVariableField(IProjectVariables.API__CASE__CALL_STATUS).setValue(r[CaseQueryClient.IDX_STATUS]);   // SUCCESS / FAILED / ERROR
mySession.getVariableField(IProjectVariables.API__CASE__CODE).setValue(r[CaseQueryClient.IDX_CODE]);
mySession.getVariableField(IProjectVariables.API__CASE__MESSAGE).setValue(r[CaseQueryClient.IDX_MESSAGE]);
mySession.getVariableField(IProjectVariables.API__CASE__TOTAL_OPEN).setValue(r[CaseQueryClient.IDX_TOTAL_OPEN_CASES]);
mySession.getVariableField(IProjectVariables.API__CASE__WITHIN_SLA).setValue(r[CaseQueryClient.IDX_CASES_WITHIN_SLA]);
mySession.getVariableField(IProjectVariables.API__CASE__OUTSIDE_SLA).setValue(r[CaseQueryClient.IDX_CASES_OUTSIDE_SLA]);
```

Any other field is read the same way with its `IDX_*` constant from the table above.

## CLI test

```bat
build.cmd
java -cp "out;lib\json-20240303.jar" flow.CaseQueryClient 1298
```

Usage: `flow.CaseQueryClient <membershipNumber> [--trustall] [--url URL] [--apikey KEY] [--logdir DIR]`

Expected output (SIT, 14 Oct 2026):

```
---------------- RESULT ----------------
status                   = SUCCESS
code                     =
message                  =
totalOpenCases           = 7
casesWithinSla           = 0
casesOutsideSla          = 7
membershipNumber         = 1298
siebelOperationObjectId  = 1-29L-3967
processInstanceId        = 1-9KC5N6N
objectId                 =
rawResponse              = {"Error Code":"", ...}
httpStatus               = 200
elapsedMs                = ...
callId                   = ...
----------------------------------------
```

Negative test:

```bat
java -cp "out;lib\json-20240303.jar" flow.CaseQueryClient 12222298
```
```
status                   = FAILED
code                     = 1
message                  = Invalid Membership Number
```

## Equivalent curl (Git Bash / Linux)

```sh
curl -s -X POST "https://apisit.dubaichamber.com/dcci/DCCICPINTEGRATION/DCCICXPROJECTIVR_APIS/1.0/CaseQuery" \
  -H "Content-Type: application/json" \
  -H "api-key: _5oGKrI3be5GtHMn4WjALPsEvzjpn3T-4nGwSvrNcCE" \
  -d '{"body":{"ProcessName":"DC Case Query IVR WF","Membership Number":"1298"}}'
```

# SpeakToRelationshipManagerClient

Registers a platinum member's request to speak with their relationship manager. The backend creates a record
in Siebel and returns its ids.

| | |
|---|---|
| Class | `flow.SpeakToRelationshipManagerClient` |
| Endpoint | `POST https://apisit.dubaichamber.com/dcci/DCCICPINTEGRATION/DCCICXPROJECTIVR_APIS/1.0/SpeakToARelationshipManager` |
| Process name | `DC Speak With Relationship Manager IVR WF` |
| Headers | `api-key` only (masked in the log by default) |
| Side effects | **Yes.** Each successful call creates a record in Siebel. |

## Methods

```java
public static String[] speakToRelationshipManager(String membershipNumber, String callerId, String mobileNumber, String userId)
```

## Inputs

| Parameter | Required | Example | Sent as | Notes |
|---|---|---|---|---|
| `membershipNumber` | Yes | `1298` | `Membership Number` | Platinum member number (CSN). |
| `callerId` | Yes | `TESTUSERIT` | `Caller ID` | Caller name. |
| `mobileNumber` | Yes | `971506584588` | `Mobile Number` | Contact number, country code first, no `+`. |
| `userId` | Yes | `TESTUSERIT` | `User Id` | User name / login. |

If any of the four is empty the method returns `FAILED` / `INVALID_INPUT` without calling the API.

## Output – `String[15]`

Every response field is mapped. Values are never `null`; a JSON `null` becomes `""`.

| Index | Constant | Label | Source field | Example |
|---|---|---|---|---|
| 0 | `IDX_STATUS` | status | – | `SUCCESS` |
| 1 | `IDX_CODE` | code | `Error Code` | `` |
| 2 | `IDX_MESSAGE` | message | `Error Message` | `` |
| 3 | `IDX_USER_ID` | userId | `User Id` | `TESTUSERIT` |
| 4 | `IDX_NOTES_ID` | notesId | `NotesId` | `` |
| 5 | `IDX_MOBILE_NUMBER` | mobileNumber | `Mobile Number` | `971506584588` |
| 6 | `IDX_MEMBERSHIP_NUMBER` | membershipNumber | `Membership Number` | `1298` |
| 7 | `IDX_SIEBEL_OPERATION_OBJECT_ID` | siebelOperationObjectId | `Siebel Operation Object Id` | `1-9K9ALOV` |
| 8 | `IDX_CALLER_ID` | callerId | `Caller ID` | `TESTUSERIT` |
| 9 | `IDX_PROCESS_INSTANCE_ID` | processInstanceId | `Process Instance Id` | `1-9K9ALOU` |
| 10 | `IDX_OBJECT_ID` | objectId | `Object Id` | `` |
| 11 | `IDX_RAW_RESPONSE` | rawResponse | whole response body as received | |
| 12 | `IDX_HTTP_STATUS` | httpStatus | HTTP status, `""` when no answer | `200` |
| 13 | `IDX_ELAPSED_MS` | elapsedMs | call duration in milliseconds | |
| 14 | `IDX_CALL_ID` | callId | id used in the log lines of this call | |

Call status values:

- `SUCCESS` – HTTP 200 and `Error Code` empty (or `0`). This is the success condition agreed with Dubai Chamber.
- `FAILED` / API error code – backend rejected the request, see `message`.
- `FAILED` / `INVALID_INPUT` – an input is empty, API not called.
- `ERROR` / `INVALID_CONFIG`, `AUTH_ERROR`, `HTTP_ERROR`, `INVALID_RESPONSE`, `TIMEOUT`, `CONNECTION_ERROR`, `SSL_ERROR`, `ERROR` – technical problem, see [README – Codes to branch on](README.md#codes-to-branch-on).

## Configuration and logging

URL: property `dc.speakrm.url` or `setApiUrl()`. Key, timeouts, trust-all SSL and logging are the shared `dc.api.*` properties / setters, see [README – Configuration](README.md#configuration). Every call writes to `<logDir>/SpeakToRelationshipManagerClient/SpeakToRelationshipManagerClient_yyyy-MM-dd.log` with the call id returned in `IDX_CALL_ID`.

## Usage in an OD servlet block

Call once, then copy the values you need into project variables using the `IDX_*` constants.

```java
String csn       = mySession.getVariableField(IProjectVariables.API__USER__CSN).getStringValue();
String callerId  = mySession.getVariableField(IProjectVariables.API__USER__FIRST_NAME).getStringValue();
String mobile    = mySession.getVariableField(IProjectVariables.SESSION, IProjectVariables.SESSION_FIELD_ANI).getStringValue();
String userId    = mySession.getVariableField(IProjectVariables.API__USER__LOGIN_NAME).getStringValue();

String[] r = SpeakToRelationshipManagerClient.speakToRelationshipManager(csn, callerId, mobile, userId);

mySession.getVariableField(IProjectVariables.API__RM__CALL_STATUS).setValue(r[SpeakToRelationshipManagerClient.IDX_STATUS]);   // SUCCESS / FAILED / ERROR
mySession.getVariableField(IProjectVariables.API__RM__CODE).setValue(r[SpeakToRelationshipManagerClient.IDX_CODE]);
mySession.getVariableField(IProjectVariables.API__RM__MESSAGE).setValue(r[SpeakToRelationshipManagerClient.IDX_MESSAGE]);
mySession.getVariableField(IProjectVariables.API__RM__NOTES_ID).setValue(r[SpeakToRelationshipManagerClient.IDX_NOTES_ID]);
mySession.getVariableField(IProjectVariables.API__RM__OBJECT_ID).setValue(r[SpeakToRelationshipManagerClient.IDX_SIEBEL_OPERATION_OBJECT_ID]);
```

Any other field is read the same way with its `IDX_*` constant from the table above.

## CLI test

Creates a record in SIT each time.

```bat
build.cmd
java -cp "out;lib\json-20240303.jar" flow.SpeakToRelationshipManagerClient 1298 TESTUSERIT 971506584588 TESTUSERIT
```

Usage: `flow.SpeakToRelationshipManagerClient <membershipNumber> <callerId> <mobileNumber> <userId> [--trustall] [--url URL] [--apikey KEY] [--logdir DIR]`

Expected output (SIT, 6 Oct 2026):

```
---------------- RESULT ----------------
status                   = SUCCESS
code                     =
message                  =
userId                   = TESTUSERIT
notesId                  =
mobileNumber             = 971506584588
membershipNumber         = 1298
siebelOperationObjectId  = 1-9K9ALOV
callerId                 = TESTUSERIT
processInstanceId        = 1-9K9ALOU
objectId                 =
rawResponse              = {"Error Code":"", ...}
httpStatus               = 200
elapsedMs                = ...
callId                   = ...
----------------------------------------
```

## Equivalent curl (Git Bash / Linux)

```sh
curl -s -X POST "https://apisit.dubaichamber.com/dcci/DCCICPINTEGRATION/DCCICXPROJECTIVR_APIS/1.0/SpeakToARelationshipManager" \
  -H "Content-Type: application/json" \
  -H "api-key: _5oGKrI3be5GtHMn4WjALPsEvzjpn3T-4nGwSvrNcCE" \
  -d '{"body":{"ProcessName":"DC Speak With Relationship Manager IVR WF","Membership Number":"1298","Caller ID":"TESTUSERIT","Mobile Number":"971506584588","User Id":"TESTUSERIT"}}'
```

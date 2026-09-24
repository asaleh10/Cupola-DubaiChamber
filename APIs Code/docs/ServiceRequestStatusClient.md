# ServiceRequestStatusClient

Two lookups on the same endpoint:

- **By SR number**: the status of one service request.
- **By CSN**: the list of service requests of a membership, paged.

| | |
|---|---|
| Class | `flow.ServiceRequestStatusClient` |
| Endpoint | `POST https://apisit.dubaichamber.com/dcci/DCCICPIntegration/SRSummary_REST/GetSRSummary` |
| Process name | `DC Get SR Summary Mob App WF` |
| Headers | `api-key` only (masked in the log by default) |
| Side effects | None (read-only) |

## Methods

```java
// by SR number
public static String[] getServiceRequestStatus(String srNumber)
public static String[] getServiceRequestStatus(String srNumber, String language, String noDays)

// by CSN
public static String[] getServiceRequestsByCsn(String csn)
public static String[] getServiceRequestsByCsn(String csn, String language, String noDays, int pageSize, int startRowNum)

// one SR out of result[IDX_SR_LIST_JSON]
public static String[] getServiceRequest(String srListJson, int index)

// "120802411138" -> "1-20802411138"
public static String formatSrNumber(String srNumber)
```

## Inputs

| Parameter | Example | Notes |
|---|---|---|
| `srNumber` | `120802411138` | Digits only as collected from the caller. The class inserts `-` after the first digit and sends `1-20802411138`. A value that already contains `-` is sent unchanged. |
| `csn` | `1298` | Membership number. |
| `language` | `ENU` | `ENU` or `ARA`. `""` means `ENU`. |
| `noDays` | `15` | Sent as given. `""` means `15`. |
| `pageSize` | `10` | By-CSN only. Number of SRs per page. |
| `startRowNum` | `0` | By-CSN only. First row of the page, `0` based. |

Empty `srNumber` or `csn` returns `FAILED` / `INVALID_INPUT` without calling the API.

## Output – `String[23]`

Every top-level response field is mapped. Values are never `null`; a JSON `null` becomes `""`.

| Index | Constant | Label | Source field | By SR | By CSN |
|---|---|---|---|---|---|
| 0 | `IDX_STATUS` | status | – | `SUCCESS` | `SUCCESS` |
| 1 | `IDX_CODE` | code | `Error Code` | `` | `` |
| 2 | `IDX_MESSAGE` | message | `Error Message` | `` | `` |
| 3 | `IDX_SR_NUMBER` | srNumber | `SRNum` | `1-20802411138` | `` |
| 4 | `IDX_SR_STATUS` | srStatus | `Status` | `Approved and Payment Awaited` | `` |
| 5 | `IDX_LANGUAGE` | language | `Language` | `English` | `ENU` |
| 6 | `IDX_START_ROW_NUM` | startRowNum | `StartRowNum` | `0` | `0` |
| 7 | `IDX_TOTAL_RECORDS` | totalRecords | `TotalRecords` | `` | `464` |
| 8 | `IDX_PAGE_SIZE` | pageSize | `PageSize` | `10` | `10` |
| 9 | `IDX_NO_DAYS` | noDays | `NoDays` | `15` | `15` |
| 10 | `IDX_APP_ID` | appId | `AppId` | `` | `` |
| 11 | `IDX_NEW_QUERY` | newQuery | `NewQuery` | `true` | `true` |
| 12 | `IDX_COO_DATE_RANGE` | cooDateRange | `COODteRange` | `09/06/2026 00:00:00` | same |
| 13 | `IDX_MESSAGE_ID` | messageId | `SiebelMessage.MessageId` | `1-3W46FO` | `1-3W46FP` |
| 14 | `IDX_INT_OBJECT_NAME` | intObjectName | `SiebelMessage.IntObjectName` | `DCGetSRSummaryIO` | same |
| 15 | `IDX_INT_OBJECT_FORMAT` | intObjectFormat | `SiebelMessage.IntObjectFormat` | `Siebel Hierarchical` | same |
| 16 | `IDX_MESSAGE_TYPE` | messageType | `SiebelMessage.MessageType` | `Integration Object` | same |
| 17 | `IDX_SR_COUNT` | srCount | size of `SiebelMessage."Service Request"` | `0` | `10` |
| 18 | `IDX_SR_LIST_JSON` | srListJson | `"Service Request"` as JSON array text | `[]` | `[{...},...]` |
| 19 | `IDX_RAW_RESPONSE` | rawResponse | whole response body as received | | |
| 20 | `IDX_HTTP_STATUS` | httpStatus | HTTP status, `"`" when no answer | | |
| 21 | `IDX_ELAPSED_MS` | elapsedMs | call duration in milliseconds | | |
| 22 | `IDX_CALL_ID` | callId | id used in the log lines of this call | | |

By SR number the answer is in `srStatus`; the list is empty. By CSN the answer is the list; `srStatus` is empty.

### SR record – `getServiceRequest(srListJson, index)` returns `String[22]`

| Index | Constant | Label | Source field | Example |
|---|---|---|---|---|
| 0 | `SR_IDX_SR_NUMBER` | srNumber | `SR Number` | `1-20808518008` |
| 1 | `SR_IDX_STATUS` | status | `Status` | as returned; seen so far: `Draft`, `Paid`, `In Progress`, `Approved and Payment Awaited` |
| 2 | `SR_IDX_SR_TYPE` | srType | `SR Type` | `Certificate Of Origin`, `ATA Carnet` |
| 3 | `SR_IDX_SR_SUB_TYPE` | srSubType | `SR Sub Type` | `Amendment`, `Refund`, `Substitute Carnet` |
| 4 | `SR_IDX_CSN` | csn | `CSN` | `1298` |
| 5 | `SR_IDX_SR_ID` | srId | `DC SR Id` | `1-9K4UR94` |
| 6 | `SR_IDX_MEMBER_NAME` | memberName | `DC Member Name (English)` | `DUBAI CHAMBER COMMERCE` |
| 7 | `SR_IDX_CREATED` | created | `DC Created` | `09/21/2026 18:38:30` |
| 8 | `SR_IDX_DESCRIPTION` | description | `Description` | `` |
| 9 | `SR_IDX_RECEIPT_AMOUNT` | receiptAmount | `DC Receipt Amount` | `100` |
| 10 | `SR_IDX_RECEIPT_URL` | receiptUrl | `DC eReceipt Download URL` | URL or `Document Generation InProgress` |
| 11 | `SR_IDX_INVOICE_NUMBER` | invoiceNumber | `DC Invoice Number` | `26SIM000128` |
| 12 | `SR_IDX_INVOICE_DATE` | invoiceDate | `DC Invoice Date` | `07/23/2026` |
| 13 | `SR_IDX_INVOICE_AMOUNT` | invoiceAmount | `DC Invoice Amount` | `19712.31` |
| 14 | `SR_IDX_COO_NUMBER` | cooNumber | `DC COO Number` | `23710961` |
| 15 | `SR_IDX_DOWNLOAD_URL` | downloadUrl | `DCDownloadURL` | URL or `Available after Payment` |
| 16 | `SR_IDX_ONLINE_PAY_FLAG` | onlinePayFlag | `DC Online Pay Flag` | `N` |
| 17 | `SR_IDX_CURRENT_MONTH_SR` | currentMonthSr | `DC Current Month SR` | `Y` |
| 18 | `SR_IDX_LICENSE_REG_NUM` | licenseRegNum | `DC License Registration Num Member Level` | `1000011111` |
| 19 | `SR_IDX_EXPORTER_NAME_EN` | exporterNameEn | `DC Exporter Name (Eng)` | `DUBAI CHAMBER COMMERCE` |
| 20 | `SR_IDX_EXPORTER_NAME_AR` | exporterNameAr | `DC Exporter Name (Ara)` | `غرفة تجارة دبي` |
| 21 | `SR_IDX_RESPONDANT_NAME` | respondantName | `DC Respondant Name` | `` |

Call status values:

- `SUCCESS` – API answered without an error code.
- `FAILED` / Siebel code such as `(SBL-BPR-00162)--(SBL-CMI-00122)` – SR number does not exist.
- `FAILED` / `NOT_FOUND` – by-SR call answered without error code but with no status.
- `FAILED` / `INVALID_INPUT` – input empty, API not called.
- `ERROR` / `INVALID_CONFIG`, `AUTH_ERROR`, `HTTP_ERROR`, `INVALID_RESPONSE`, `TIMEOUT`, `CONNECTION_ERROR`, `SSL_ERROR`, `ERROR` – technical problem, see [README – Codes to branch on](README.md#codes-to-branch-on).

## Configuration and logging

URL: property `dc.srstatus.url` or `setApiUrl()`. Key, timeouts, trust-all SSL and logging are the shared `dc.api.*` properties / setters, see [README – Configuration](README.md#configuration). Every call writes to `<logDir>/ServiceRequestStatusClient/ServiceRequestStatusClient_yyyy-MM-dd.log` with the call id returned in `IDX_CALL_ID`.

## Usage in an OD servlet block

Call once, then copy the values you need into project variables using the `IDX_*` constants. What each SR
status means is decided in the call flow, not in the class.

By SR number, digits collected from the caller:

```java
String srDigits = mySession.getVariableField(IProjectVariables.API__SR__DIGITS).getStringValue();

String[] r = ServiceRequestStatusClient.getServiceRequestStatus(srDigits);

mySession.getVariableField(IProjectVariables.API__SR__CALL_STATUS).setValue(r[ServiceRequestStatusClient.IDX_STATUS]);   // SUCCESS / FAILED / ERROR
mySession.getVariableField(IProjectVariables.API__SR__CODE).setValue(r[ServiceRequestStatusClient.IDX_CODE]);
mySession.getVariableField(IProjectVariables.API__SR__MESSAGE).setValue(r[ServiceRequestStatusClient.IDX_MESSAGE]);
mySession.getVariableField(IProjectVariables.API__SR__NUMBER).setValue(r[ServiceRequestStatusClient.IDX_SR_NUMBER]);
mySession.getVariableField(IProjectVariables.API__SR__STATUS).setValue(r[ServiceRequestStatusClient.IDX_SR_STATUS]);   // as returned, e.g. Approved and Payment Awaited
```

By CSN:

```java
String csn = mySession.getVariableField(IProjectVariables.API__USER__CSN).getStringValue();

String[] r = ServiceRequestStatusClient.getServiceRequestsByCsn(csn);

mySession.getVariableField(IProjectVariables.API__SR__CALL_STATUS).setValue(r[ServiceRequestStatusClient.IDX_STATUS]);
mySession.getVariableField(IProjectVariables.API__SR__TOTAL).setValue(r[ServiceRequestStatusClient.IDX_TOTAL_RECORDS]);
mySession.getVariableField(IProjectVariables.API__SR__COUNT).setValue(r[ServiceRequestStatusClient.IDX_SR_COUNT]);
mySession.getVariableField(IProjectVariables.API__SR__LIST_JSON).setValue(r[ServiceRequestStatusClient.IDX_SR_LIST_JSON]);
```

Reading one SR from the list, here the first one (index `0`). The same call with another index reads the next SR:

```java
String listJson = mySession.getVariableField(IProjectVariables.API__SR__LIST_JSON).getStringValue();

String[] sr = ServiceRequestStatusClient.getServiceRequest(listJson, 0);

mySession.getVariableField(IProjectVariables.API__SR__NUMBER).setValue(sr[ServiceRequestStatusClient.SR_IDX_SR_NUMBER]);
mySession.getVariableField(IProjectVariables.API__SR__STATUS).setValue(sr[ServiceRequestStatusClient.SR_IDX_STATUS]);   // as returned, e.g. Draft, Paid, In Progress
mySession.getVariableField(IProjectVariables.API__SR__TYPE).setValue(sr[ServiceRequestStatusClient.SR_IDX_SR_TYPE]);
mySession.getVariableField(IProjectVariables.API__SR__SUB_TYPE).setValue(sr[ServiceRequestStatusClient.SR_IDX_SR_SUB_TYPE]);
mySession.getVariableField(IProjectVariables.API__SR__CREATED).setValue(sr[ServiceRequestStatusClient.SR_IDX_CREATED]);
mySession.getVariableField(IProjectVariables.API__SR__RECEIPT_AMOUNT).setValue(sr[ServiceRequestStatusClient.SR_IDX_RECEIPT_AMOUNT]);
```

Any other field is read the same way with its `IDX_*` or `SR_IDX_*` constant from the tables above.

## CLI test

```bat
build.cmd
java "-Dfile.encoding=UTF-8" -cp "out;lib\json-20240303.jar" flow.ServiceRequestStatusClient sr 120802411138
java "-Dfile.encoding=UTF-8" -cp "out;lib\json-20240303.jar" flow.ServiceRequestStatusClient csn 1298
java "-Dfile.encoding=UTF-8" -cp "out;lib\json-20240303.jar" flow.ServiceRequestStatusClient csn 1298 5 10
```

Usage: `sr <srNumberDigits>` or `csn <csn> [pageSize] [startRowNum]`, each followed by optional `[--trustall] [--url URL] [--apikey KEY] [--logdir DIR]`

Expected output by SR (SIT, 21 Sep 2026):

```
status           = SUCCESS
code             =
message          =
srNumber         = 1-20802411138
srStatus         = Approved and Payment Awaited
language         = English
...
srCount          = 0
```

Expected output by CSN, first lines:

```
status           = SUCCESS
totalRecords     = 464
srCount          = 10
ServiceRequest[0]
srNumber         = 1-20808518008
status           = Draft
srType           = Certificate Of Origin
...
```

Negative test:

```bat
java -cp "out;lib\json-20240303.jar" flow.ServiceRequestStatusClient sr 199999999999
```
```
status           = FAILED
code             = (SBL-BPR-00162)--(SBL-CMI-00122)
```

## Equivalent curl (Git Bash / Linux)

By SR number:

```sh
curl -s -X POST "https://apisit.dubaichamber.com/dcci/DCCICPIntegration/SRSummary_REST/GetSRSummary" \
  -H "Content-Type: application/json" \
  -H "api-key: _5oGKrI3be5GtHMn4WjALPsEvzjpn3T-4nGwSvrNcCE" \
  -d '{"body":{"ProcessName":"DC Get SR Summary Mob App WF","IncomingHierarchy":{"IntObjectName":"DCGetSRSummaryIO","MessageType":"Integration Object","ListOfDCGetSRSummaryIO":{"Service Request":{"Account Id":"","SR Number":"1-20802411138","SR Type":"","SR Sub Type":"","Status":"","Created By Name":""}}},"Language":"ENU","PageSize":10,"StartRowNum":0,"NewQuery":true,"NoDays":"15"}}'
```

By CSN:

```sh
curl -s -X POST "https://apisit.dubaichamber.com/dcci/DCCICPIntegration/SRSummary_REST/GetSRSummary" \
  -H "Content-Type: application/json" \
  -H "api-key: _5oGKrI3be5GtHMn4WjALPsEvzjpn3T-4nGwSvrNcCE" \
  -d '{"body":{"ProcessName":"DC Get SR Summary Mob App WF","IncomingHierarchy":{"IntObjectName":"DCGetSRSummaryIO","MessageType":"Integration Object","ListOfDCGetSRSummaryIO":{"Service Request":{"Account Id":"","CSN":"1298","SR Type":"","SR Sub Type":"","Status":"","Created By Name":""}}},"Language":"ENU","PageSize":10,"StartRowNum":0,"NewQuery":true,"NoDays":"15"}}'
```

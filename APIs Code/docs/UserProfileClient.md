# UserProfileClient

Looks up a registered Dubai Chamber user by mobile number and returns the user's details plus every account
(company / membership) linked to that user. In the IVR this identifies the caller from the ANI.

| | |
|---|---|
| Class | `flow.UserProfileClient` |
| Endpoint | `POST https://apisit.dubaichamber.com/dcci/DCCICPIntegration/GenericGetUserProfileAPI` |
| Process name | `DC Get User Profile Details Generic WF` |
| Headers | `api-key` only (masked in the log by default) |
| Side effects | None (read-only) |

## Methods

```java
public static String[] getUserProfileByMobile(String mobileNumber)

// reads account number `index` out of result[IDX_ACCOUNTS_JSON]
public static String[] getAccount(String accountsJson, int index)
```

## Inputs

| Parameter | Required | Example | Notes |
|---|---|---|---|
| `mobileNumber` | Yes | `97121234234` | Country code + number, no `+`, no leading `00`. The IVR ANI usually arrives in this form already. |

`EmiratesId`, `EmailAddr` and `LoginName` are sent as `""` from constants inside the class (`EMIRATES_ID`,
`EMAIL_ADDR`, `LOGIN_NAME`). Empty `mobileNumber` returns `FAILED` / `INVALID_INPUT` without calling the API.

## Output – `String[28]`

Every field the API returns is mapped. Values are never `null`; a JSON `null` becomes `""`.

| Index | Constant | Label | Source field | Example |
|---|---|---|---|---|
| 0 | `IDX_STATUS` | status | – | `SUCCESS` |
| 1 | `IDX_CODE` | code | `Error Code` | `` (empty on success) |
| 2 | `IDX_MESSAGE` | message | `Error Message` | `` |
| 3 | `IDX_FIRST_NAME` | firstName | `SiebMsg.User.FirstName` | `Chandan` |
| 4 | `IDX_LAST_NAME` | lastName | `User.LastName` | `Gowda` |
| 5 | `IDX_EMAIL` | email | `User.EMailAddr` | `puneet.shet@dubaichamber.com` |
| 6 | `IDX_PHONE` | phone | `User.Phone` | `97121234234` |
| 7 | `IDX_HOME_PHONE` | homePhone | `User.HomePhone` | `` |
| 8 | `IDX_EMIRATES_ID` | emiratesId | `User.EmiratesID` | `78419887513792` |
| 9 | `IDX_DATE_OF_BIRTH` | dateOfBirth | `User.DateofBirth` | `` |
| 10 | `IDX_NATIONALITY` | nationality | `User.Nationality` | `United Arab Emirates` |
| 11 | `IDX_JOB_TITLE` | jobTitle | `User.JobTitle` | `` |
| 12 | `IDX_LOGIN_NAME` | loginName | `User.LoginName` | `TESTUSERSIT` (needed later by PaymentLinkClient) |
| 13 | `IDX_USER_STATUS` | userStatus | `User.UserStatus` | `Active` |
| 14 | `IDX_USER_TYPE` | userType | `User.UserType` | `Web Registered User` |
| 15 | `IDX_REGISTRATION_SOURCE` | registrationSource | `User.RegistrationSourceAppName` | `` |
| 16 | `IDX_ACCOUNT_COUNT` | accountCount | size of `User.Account` | `15` (text) |
| 17 | `IDX_FIRST_ACCOUNT_CSN` | firstAccountCsn | `User.Account[0].CSN` | `246884` |
| 18 | `IDX_FIRST_ACCOUNT_NAME` | firstAccountName | `User.Account[0].DCNameEnglish` | `AL MARWAN HEAVY EQUIP & MACHINERY TR` |
| 19 | `IDX_ACCOUNTS_JSON` | accountsJson | `User.Account` as JSON array text | `[{...},{...}]` – pass to `getAccount()` |
| 20 | `IDX_MESSAGE_ID` | messageId | `SiebMsg.MessageId` | `1-3W46FD` |
| 21 | `IDX_INT_OBJECT_NAME` | intObjectName | `SiebMsg.IntObjectName` | `DCGetUserAccntRESTIO` |
| 22 | `IDX_INT_OBJECT_FORMAT` | intObjectFormat | `SiebMsg.IntObjectFormat` | `Siebel Hierarchical` |
| 23 | `IDX_MESSAGE_TYPE` | messageType | `SiebMsg.MessageType` | `Integration Object` |
| 24 | `IDX_RAW_RESPONSE` | rawResponse | whole response body as received | `{"Error Code":null,...}` |
| 25 | `IDX_HTTP_STATUS` | httpStatus | HTTP status, `""` when no answer | |
| 26 | `IDX_ELAPSED_MS` | elapsedMs | call duration in milliseconds | |
| 27 | `IDX_CALL_ID` | callId | id used in the log lines of this call | |

### Account record – `getAccount(accountsJson, index)` returns `String[14]`

| Index | Constant | Label | Source field | Example |
|---|---|---|---|---|
| 0 | `ACC_IDX_CSN` | csn | `CSN` | `1298` (may be `""` for accounts still In Progress) |
| 1 | `ACC_IDX_NAME_EN` | nameEn | `DCNameEnglish` | `DUBAI CHAMBER COMMERCE` |
| 2 | `ACC_IDX_NAME_AR` | nameAr | `DCNameArabic` | `غرفة تجارة دبي` |
| 3 | `ACC_IDX_TYPE` | accountType | `AccountTypeCode` | `Member`, `Non Member`, `Business Council` |
| 4 | `ACC_IDX_STATUS` | accountStatus | `AccountStatus` | as returned; seen so far: `Active`, `Hold`, `Active - Renew`, `Active - Amend`, `In Progress`, `Cancelled` |
| 5 | `ACC_IDX_EXPIRY_DATE` | expiryDate | `DCExpiryDate` | `09/30/2029` (MM/dd/yyyy) |
| 6 | `ACC_IDX_LICENSE_NO` | licenseNo | `LicenseNumber` | `1000011111` |
| 7 | `ACC_IDX_LICENSE_AUTH` | licenseAuth | `LicenseIssuingAuthority` | `Jebel Ali Free Zone Authority` |
| 8 | `ACC_IDX_PLATINUM_FLAG` | platinumFlag | `PlatinumFlag` | `Y` or `""` |
| 9 | `ACC_IDX_MAIN_PHONE` | mainPhone | `MainPhoneNumber` | `971501234567` |
| 10 | `ACC_IDX_MAIN_FAX` | mainFax | `MainFaxNumber` | `+97142260123` |
| 11 | `ACC_IDX_RM_NAME` | rmName | `RelationshipManagerName` | `Mohammad AlMazrouei` |
| 12 | `ACC_IDX_RM_EMAIL` | rmEmail | `RelationshipManagerEmailAddr` | `mohammad.almazrouei@dubaichamber.com` |
| 13 | `ACC_IDX_RM_PHONE` | rmPhone | `RelationshipManagerPhone` | `` |

An out-of-range index returns an array of empty strings (never `null`).

Call status values:

- `SUCCESS` – API answered without an error code.
- `FAILED` / code `1` – `User details not found for the given input.`
- `FAILED` / `NOT_FOUND` – HTTP 200 without error code but without a `User` block.
- `FAILED` / `INVALID_INPUT` – input empty, API not called.
- `ERROR` / `INVALID_CONFIG`, `AUTH_ERROR`, `HTTP_ERROR`, `INVALID_RESPONSE`, `TIMEOUT`, `CONNECTION_ERROR`, `SSL_ERROR`, `ERROR` – technical problem, see [README – Codes to branch on](README.md#codes-to-branch-on).

## Configuration and logging

URL: property `dc.userprofile.url` or `setApiUrl()`. Key, timeouts, trust-all SSL and logging are the shared `dc.api.*` properties / setters, see [README – Configuration](README.md#configuration). Every call writes to `<logDir>/UserProfileClient/UserProfileClient_yyyy-MM-dd.log` with the call id returned in `IDX_CALL_ID`.

## Usage in an OD servlet block

Call once, then copy the values you need into project variables using the `IDX_*` constants. Which account to
use and what each `accountStatus` means is decided in the call flow, not in the class.

```java
String ani = mySession.getVariableField(IProjectVariables.SESSION, IProjectVariables.SESSION_FIELD_ANI).getStringValue();

String[] r = UserProfileClient.getUserProfileByMobile(ani);

mySession.getVariableField(IProjectVariables.API__USER__CALL_STATUS).setValue(r[UserProfileClient.IDX_STATUS]);   // SUCCESS / FAILED / ERROR
mySession.getVariableField(IProjectVariables.API__USER__CODE).setValue(r[UserProfileClient.IDX_CODE]);
mySession.getVariableField(IProjectVariables.API__USER__MESSAGE).setValue(r[UserProfileClient.IDX_MESSAGE]);
mySession.getVariableField(IProjectVariables.API__USER__FIRST_NAME).setValue(r[UserProfileClient.IDX_FIRST_NAME]);
mySession.getVariableField(IProjectVariables.API__USER__LAST_NAME).setValue(r[UserProfileClient.IDX_LAST_NAME]);
mySession.getVariableField(IProjectVariables.API__USER__LOGIN_NAME).setValue(r[UserProfileClient.IDX_LOGIN_NAME]);
mySession.getVariableField(IProjectVariables.API__USER__STATUS).setValue(r[UserProfileClient.IDX_USER_STATUS]);
mySession.getVariableField(IProjectVariables.API__USER__ACCOUNT_COUNT).setValue(r[UserProfileClient.IDX_ACCOUNT_COUNT]);
mySession.getVariableField(IProjectVariables.API__USER__ACCOUNTS_JSON).setValue(r[UserProfileClient.IDX_ACCOUNTS_JSON]);
```

Reading one account, here the first one (index `0`). The same call with another index reads the next account:

```java
String accountsJson = mySession.getVariableField(IProjectVariables.API__USER__ACCOUNTS_JSON).getStringValue();

String[] acc = UserProfileClient.getAccount(accountsJson, 0);

mySession.getVariableField(IProjectVariables.API__ACC__CSN).setValue(acc[UserProfileClient.ACC_IDX_CSN]);
mySession.getVariableField(IProjectVariables.API__ACC__NAME).setValue(acc[UserProfileClient.ACC_IDX_NAME_EN]);
mySession.getVariableField(IProjectVariables.API__ACC__NAME_AR).setValue(acc[UserProfileClient.ACC_IDX_NAME_AR]);
mySession.getVariableField(IProjectVariables.API__ACC__STATUS).setValue(acc[UserProfileClient.ACC_IDX_STATUS]);   // as returned, e.g. Active, Hold, Active - Renew
mySession.getVariableField(IProjectVariables.API__ACC__TYPE).setValue(acc[UserProfileClient.ACC_IDX_TYPE]);
mySession.getVariableField(IProjectVariables.API__ACC__EXPIRY).setValue(acc[UserProfileClient.ACC_IDX_EXPIRY_DATE]);
```

Any other field is read the same way with its `IDX_*` or `ACC_IDX_*` constant from the tables above.

## CLI test

```bat
build.cmd
java "-Dfile.encoding=UTF-8" -cp "out;lib\json-20240303.jar" flow.UserProfileClient 97121234234
```

Usage: `flow.UserProfileClient <mobileNumber> [--trustall] [--url URL] [--apikey KEY] [--logdir DIR]`

The CLI prints the user result table followed by one `Account[n]` table per linked account.

Expected output, first part (SIT, 21 Sep 2026):

```
---------------- RESULT ----------------
status           = SUCCESS
code             =
message          =
firstName        = Chandan
lastName         = Gowda
email            = puneet.shet@dubaichamber.com
phone            = 97121234234
emiratesId       = 78419887513792
loginName        = TESTUSERSIT
userStatus       = Active
userType         = Web Registered User
nationality      = United Arab Emirates
accountCount     = 15
firstAccountCsn  = 246884
firstAccountName = AL MARWAN HEAVY EQUIP & MACHINERY TR
accountsJson     = [{"CSN":"246884", ...}]
----------------------------------------
Account[0]
---------------- RESULT ----------------
csn              = 246884
nameEn           = AL MARWAN HEAVY EQUIP & MACHINERY TR
...
```

Negative test:

```bat
java -cp "out;lib\json-20240303.jar" flow.UserProfileClient 971506584588
```

```
status           = FAILED
code             = 1
message          = User details not found for the given input.
```

## Equivalent curl (Git Bash / Linux)

```sh
curl -s -X POST "https://apisit.dubaichamber.com/dcci/DCCICPIntegration/GenericGetUserProfileAPI" \
  -H "Content-Type: application/json" \
  -H "api-key: _5oGKrI3be5GtHMn4WjALPsEvzjpn3T-4nGwSvrNcCE" \
  -d '{"body":{"ProcessName":"DC Get User Profile Details Generic WF","EmiratesId":"","EmailAddr":"","MobileNumber":"97121234234","LoginName":""}}'
```

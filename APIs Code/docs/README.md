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
| `DEBUG` | `true` prints request and response to the console; set `false` for production | all |
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

Each class prints the outgoing request, the raw response and then a labelled result table. The process exit
code is `0` for `SUCCESS`, `1` for `FAILED` / `ERROR`, `2` for wrong CLI arguments.

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

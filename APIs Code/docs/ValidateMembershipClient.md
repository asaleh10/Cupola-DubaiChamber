# ValidateMembershipClient

Validates a Dubai Chamber membership (CSN) number and returns the member's name, status and licence details.

| | |
|---|---|
| Class | `flow.ValidateMembershipClient` |
| Endpoint | `POST https://apisit.dubaichamber.com/dcci/DCCICPIntegration/ValidateAccount_REST/ValidateAccount` |
| Process name | `DC Validate Member Info WF` |
| Headers | `api-key` only |
| Side effects | None (read-only) |

## Methods

```java
public static String[] validateMember(String memberNo)
```

## Inputs

| Parameter | Required | Example | Notes |
|---|---|---|---|
| `memberNo` | Yes | `1298` | Membership / CSN number as entered by the caller. |

`LicenseNo`, `LicenseAuth` and `LicenseType` are sent as `""` from constants inside the class (`LICENSE_NO`,
`LICENSE_AUTH`, `LICENSE_TYPE`). Empty `memberNo` returns `FAILED` / `INVALID_INPUT` without calling the API.

## Output – `String[16]`

Every response field is mapped. Values are never `null`; a JSON `null` becomes `""`.

| Index | Constant | Label | Source field | 1298 | 246884 | 12222298 (not found) |
|---|---|---|---|---|---|---|
| 0 | `IDX_STATUS` | status | – | `SUCCESS` | `SUCCESS` | `FAILED` |
| 1 | `IDX_CODE` | code | `Error Code` | `0` | `0` | `1` |
| 2 | `IDX_MESSAGE` | message | `Error Message` | `2010-07-10` | `2015-02-08` | `Invalid Member/License No` |
| 3 | `IDX_MEMBER_NO` | memberNo | `MemberNo` | `1298` | `246884` | `12222298` |
| 4 | `IDX_MEMBER_NAME` | memberName | `MemberName` | `DUBAI CHAMBER COMMERCE` | `AL MARWAN HEAVY EQUIP & MACHINERY TR` | `` |
| 5 | `IDX_MEMBER_NAME_AR` | memberNameAr | `MemberNameAra` | `غرفة تجارة دبي` | `شركة المروان لتجارة المعدات والاليات الثقيلة` | `` |
| 6 | `IDX_MEMBER_STATUS` | memberStatus | `Status` | `Hold` | `Active - Renew` | `` |
| 7 | `IDX_LICENSE_NO` | licenseNo | `LicenseNo` | `1000011111` | `140926` | `` |
| 8 | `IDX_LICENSE_TYPE` | licenseType | `LicenseType` | `Commercial License` | `Trading` | `` |
| 9 | `IDX_LICENSE_AUTH` | licenseAuth | `LicenseAuth` | `Jebel Ali Free Zone Authority` | `Jebel Ali Free Zone Authority` | `` |
| 10 | `IDX_EXPIRY_DATE` | expiryDate | `ExpiryDate` | `2029-09-30` | `2027-03-31` | `` |
| 11 | `IDX_PLATINUM_FLAG` | platinumFlag | `PlatinumFlag` | `Y` | `` | `` |
| 12 | `IDX_OBJECT_ID` | objectId | `Object Id` | `1-29L-3967` | `1-B60FXD` | `` |
| 13 | `IDX_SIEBEL_OPERATION_OBJECT_ID` | siebelOperationObjectId | `Siebel Operation Object Id` | `*` | `*` | `` |
| 14 | `IDX_PROCESS_INSTANCE_ID` | processInstanceId | `Process Instance Id` | `1-9K4V1BP` | `1-9K4ZJRF` | `1-9K4V1BQ` |
| 15 | `IDX_RAW_RESPONSE` | rawResponse | whole response body as received | | | |

`memberStatus` is returned exactly as the API sends it. Values seen so far in SIT: `Active`, `Hold`,
`Active - Renew`, `Active - Amend`, `In Progress`, `Cancelled`. The call flow decides what each one means.

On success the backend puts a date in `Error Message` (the member's registration date). `code` is the
success indicator, not `message`.

Call status values:

- `SUCCESS` – member found, `Error Code` was `0`.
- `FAILED` / code `1` – `Invalid Member/License No`. The number does not exist.
- `FAILED` / `INVALID_INPUT` – memberNo empty.
- `ERROR` – network / HTTP problem.

## Usage in an OD servlet block

Call once, then copy the values you need into project variables using the `IDX_*` constants. What to do with
the values (for example which `memberStatus` counts as valid) is decided in the call flow, not in the class.

```java
String memberNo = mySession.getVariableField(IProjectVariables.API__MEMBER__NO).getStringValue();

String[] r = ValidateMembershipClient.validateMember(memberNo);

mySession.getVariableField(IProjectVariables.API__MEMBER__CALL_STATUS).setValue(r[ValidateMembershipClient.IDX_STATUS]);   // SUCCESS / FAILED / ERROR
mySession.getVariableField(IProjectVariables.API__MEMBER__CODE).setValue(r[ValidateMembershipClient.IDX_CODE]);
mySession.getVariableField(IProjectVariables.API__MEMBER__MESSAGE).setValue(r[ValidateMembershipClient.IDX_MESSAGE]);
mySession.getVariableField(IProjectVariables.API__MEMBER__STATUS).setValue(r[ValidateMembershipClient.IDX_MEMBER_STATUS]); // as returned, e.g. Active, Hold, Active - Renew
mySession.getVariableField(IProjectVariables.API__MEMBER__NAME).setValue(r[ValidateMembershipClient.IDX_MEMBER_NAME]);
mySession.getVariableField(IProjectVariables.API__MEMBER__NAME_AR).setValue(r[ValidateMembershipClient.IDX_MEMBER_NAME_AR]);
mySession.getVariableField(IProjectVariables.API__MEMBER__EXPIRY).setValue(r[ValidateMembershipClient.IDX_EXPIRY_DATE]);
mySession.getVariableField(IProjectVariables.API__MEMBER__PLATINUM).setValue(r[ValidateMembershipClient.IDX_PLATINUM_FLAG]);
```

Any other field is read the same way with its `IDX_*` constant from the table above.

## CLI test

```bat
build.cmd
java "-Dfile.encoding=UTF-8" -cp "out;lib\json-20240303.jar" flow.ValidateMembershipClient 1298
```

Usage: `flow.ValidateMembershipClient <memberNo>`

Expected output (SIT, 21 Sep 2026):

```
---------------- RESULT ----------------
status           = SUCCESS
code             = 0
message          = 2010-07-10
memberNo         = 1298
memberName       = DUBAI CHAMBER COMMERCE
memberNameAr     = غرفة تجارة دبي
memberStatus     = Hold
licenseNo        = 1000011111
licenseType      = Commercial License
licenseAuth      = Jebel Ali Free Zone Authority
expiryDate       = 2029-09-30
platinumFlag     = Y
objectId         = 1-29L-3967
siebelOperationObjectId = *
processInstanceId = 1-9K4V1BP
rawResponse      = {"Error Code":0,...}
----------------------------------------
```

Negative test:

```bat
java -cp "out;lib\json-20240303.jar" flow.ValidateMembershipClient 12222298
```

```
status           = FAILED
code             = 1
message          = Invalid Member/License No
```

## Equivalent curl (Git Bash / Linux)

```sh
curl -s -X POST "https://apisit.dubaichamber.com/dcci/DCCICPIntegration/ValidateAccount_REST/ValidateAccount" \
  -H "Content-Type: application/json" \
  -H "api-key: _5oGKrI3be5GtHMn4WjALPsEvzjpn3T-4nGwSvrNcCE" \
  -d '{"body":{"ProcessName":"DC Validate Member Info WF","LicenseNo":"","LicenseAuth":"","MemberNo":"1298","LicenseType":""}}'
```

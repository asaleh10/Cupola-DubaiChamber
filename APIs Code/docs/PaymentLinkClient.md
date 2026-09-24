# PaymentLinkClient

Generates an ePay (DubaiPay) payment URL for a service request and e-mails it to the registered address of
the login user.

| | |
|---|---|
| Class | `flow.PaymentLinkClient` |
| Endpoint | `POST https://apisit.dubaichamber.com/dcci/DCCICPIntegration/GenSendPaymentLink_REST/GenSendPaymentLink` |
| Process name | `DC Generate Send ePay URL WF` |
| Headers | `api-key` only |
| Side effects | **Yes.** Each successful call creates a payment transaction and sends an e-mail. Do not call it in a loop or for testing without a test SR. |

## Methods

```java
public static String[] generateAndSendPaymentLink(String srNumber)

// "120804978308" -> "1-20804978308"
public static String formatSrNumber(String srNumber)
```

## Inputs

| Parameter | Required | Example | Notes |
|---|---|---|---|
| `srNumber` | Yes | `120804978308` | Digits only as collected from the caller. The class inserts `-` after the first digit and sends `1-20804978308`. A value that already contains `-` is sent unchanged. |

`LoginName` (`TESTUSERSIT`) and `PaymentType` (`DubaiPay`) are constants inside the class, `LOGIN_NAME` and
`PAYMENT_TYPE`. Empty `srNumber` returns `FAILED` / `INVALID_INPUT` without calling the API.

## Output – `String[15]`

Every response field is mapped. Values are never `null`; a JSON `null` becomes `""`.

| Index | Constant | Label | Source field | Example |
|---|---|---|---|---|
| 0 | `IDX_STATUS` | status | – | `SUCCESS` |
| 1 | `IDX_CODE` | code | `Error Code` | `` |
| 2 | `IDX_MESSAGE` | message | `Error Message` | `` |
| 3 | `IDX_SR_NUMBER` | srNumber | `SR Number` | `1-20804978308` |
| 4 | `IDX_TRANSACTION_ID` | transactionId | `Transaction Id` | `334391847` |
| 5 | `IDX_PAYMENT_URL` | paymentUrl | `PaymentURL` | `https://epayment.qa.dubai.ae/ePayHub/...token=...` |
| 6 | `IDX_FINAL_AMOUNT` | finalAmount | `Final Amount` | `0` |
| 7 | `IDX_PAYMENT_TYPE` | paymentType | `PaymentType` | `DubaiPay` |
| 8 | `IDX_SR_TYPE` | srType | `SRType` | `Payment Services` |
| 9 | `IDX_EMAIL_ADDR` | emailAddr | `EmailAddr` | `puneet.shet@dubaichamber.com` |
| 10 | `IDX_EMAIL_STATUS` | emailStatus | `EmailStatus` | `Successfully sent the email` |
| 11 | `IDX_SEND_EMAIL_TO` | sendEmailTo | `SendEmailTo` | `` |
| 12 | `IDX_REDIRECTION_URL` | redirectionUrl | `redirectionurl` | `` |
| 13 | `IDX_LOGIN_NAME` | loginName | `LoginName` | `TESTUSERSIT` |
| 14 | `IDX_RAW_RESPONSE` | rawResponse | whole response body as received | |

Call status values:

- `SUCCESS` – link generated. `emailStatus` and `paymentUrl` are returned as-is for the call flow to use.
- `FAILED` / API error code – see `message`.
- `FAILED` / `INVALID_INPUT` – srNumber empty.
- `ERROR` – network / HTTP problem.

## Usage in an OD servlet block

Call once, then copy the values you need into project variables using the `IDX_*` constants.

```java
String srDigits = mySession.getVariableField(IProjectVariables.API__SR__DIGITS).getStringValue();

String[] r = PaymentLinkClient.generateAndSendPaymentLink(srDigits);

mySession.getVariableField(IProjectVariables.API__PAY__CALL_STATUS).setValue(r[PaymentLinkClient.IDX_STATUS]);   // SUCCESS / FAILED / ERROR
mySession.getVariableField(IProjectVariables.API__PAY__CODE).setValue(r[PaymentLinkClient.IDX_CODE]);
mySession.getVariableField(IProjectVariables.API__PAY__MESSAGE).setValue(r[PaymentLinkClient.IDX_MESSAGE]);
mySession.getVariableField(IProjectVariables.API__PAY__TRANSACTION_ID).setValue(r[PaymentLinkClient.IDX_TRANSACTION_ID]);
mySession.getVariableField(IProjectVariables.API__PAY__EMAIL_STATUS).setValue(r[PaymentLinkClient.IDX_EMAIL_STATUS]);   // as returned, e.g. Successfully sent the email
mySession.getVariableField(IProjectVariables.API__PAY__EMAIL).setValue(r[PaymentLinkClient.IDX_EMAIL_ADDR]);
mySession.getVariableField(IProjectVariables.API__PAY__AMOUNT).setValue(r[PaymentLinkClient.IDX_FINAL_AMOUNT]);
mySession.getVariableField(IProjectVariables.API__PAY__URL).setValue(r[PaymentLinkClient.IDX_PAYMENT_URL]);
```

Any other field is read the same way with its `IDX_*` constant from the table above.

## Logging

Every call writes to `<LOG_DIR>/PaymentLinkClient/PaymentLinkClient_yyyy-MM-dd.log` (Tomcat `logs` folder when running in OD):
CALL, REQUEST url / headers / body, RESPONSE status / time / body, RESULT, all with one call id. Retention and other
settings are JVM properties `dc.api.log.*`, see [README – Logging](README.md#logging).

## CLI test

Only run this against SIT with a test SR and test login. It sends a real e-mail.

```bat
build.cmd
java -cp "out;lib\json-20240303.jar" flow.PaymentLinkClient 120804978308
```

Usage: `flow.PaymentLinkClient <srNumberDigits>`

Expected output, based on the response captured in Postman on 21 Sep 2026:

```
---------------- RESULT ----------------
status           = SUCCESS
code             =
message          =
srNumber         = 1-20804978308
transactionId    = 334391847
paymentUrl       = https://epayment.qa.dubai.ae/ePayHub/Authentication/SPServlet?token=...
finalAmount      = 0
paymentType      = DubaiPay
srType           = Payment Services
emailAddr        = puneet.shet@dubaichamber.com
emailStatus      = Successfully sent the email
sendEmailTo      =
redirectionUrl   =
loginName        = TESTUSERSIT
rawResponse      = {"Error Code":null,...}
----------------------------------------
```

## Equivalent curl (Git Bash / Linux)

```sh
curl -s -X POST "https://apisit.dubaichamber.com/dcci/DCCICPIntegration/GenSendPaymentLink_REST/GenSendPaymentLink" \
  -H "Content-Type: application/json" \
  -H "api-key: _5oGKrI3be5GtHMn4WjALPsEvzjpn3T-4nGwSvrNcCE" \
  -d '{"body":{"ProcessName":"DC Generate Send ePay URL WF","SR Number":"1-20804978308","LoginName":"TESTUSERSIT","PaymentType":"DubaiPay"}}'
```

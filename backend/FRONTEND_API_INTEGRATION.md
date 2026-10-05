# NexusHR Frontend API Integration (Attendance Import + Auto Payroll)

This guide provides ready-to-use API calls for the new backend features.

## 1. Base Setup
- API Base URL: https://hr-api.cyond.com/api/v1
- Auth: Bearer token from login API

Example login:

```bash
curl -X POST "https://hr-api.cyond.com/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"E1980","password":"Cyond@2026"}'
```

Save accessToken and send it in Authorization header:

```text
Authorization: Bearer <accessToken>
```

## 2. HR Upload Attendance Excel
Endpoint:
- POST /attendance/import-excel?payMonth=YYYY-MM
- Content-Type: multipart/form-data
- Form field name for file: file

Excel format (first sheet):
- Column A: employeeCode
- Column B: attendanceDate (YYYY-MM-DD or day number like 15)
- Column C: checkInTime (HH:mm)
- Column D: checkOutTime (HH:mm)

cURL:

```bash
curl -X POST "https://hr-api.cyond.com/api/v1/attendance/import-excel?payMonth=2026-10" \
  -H "Authorization: Bearer <accessToken>" \
  -F "file=@attendance-oct-2026.xlsx"
```

Expected response shape:

```json
{
  "payMonth": "2026-10",
  "totalRows": 120,
  "importedCount": 90,
  "updatedCount": 20,
  "skippedCount": 10,
  "errors": [
    {
      "rowNumber": 21,
      "employeeCode": "E1005",
      "message": "Invalid time value: 9.5 (expected HH:mm)"
    }
  ]
}
```

## 3. Get Monthly Attendance Summary (All Employees)
Endpoint:
- GET /attendance/monthly-summary?payMonth=YYYY-MM

cURL:

```bash
curl "https://hr-api.cyond.com/api/v1/attendance/monthly-summary?payMonth=2026-10" \
  -H "Authorization: Bearer <accessToken>"
```

Use this response to show real data on payroll/dashboard page:
- workedHours
- expectedHours
- shortfallHours
- totalWorkedMinutes and totalShortfallMinutes

## 4. Get Auto Payroll Components for One Employee
Endpoint:
- GET /payroll/auto-components?employeeId=<id>&payMonth=YYYY-MM

cURL:

```bash
curl "https://hr-api.cyond.com/api/v1/payroll/auto-components?employeeId=11&payMonth=2026-10" \
  -H "Authorization: Bearer <accessToken>"
```

Expected response shape:

```json
{
  "employeeId": 11,
  "employeeCode": "E1980",
  "payMonth": "2026-10",
  "businessDays": 23,
  "expectedWorkMinutes": 11040,
  "workedMinutes": 10320,
  "shortfallMinutes": 720,
  "overtimeMinutes": 180,
  "leaveDeduction": 3913.04,
  "overtimeAmount": 978.26
}
```

## 5. Create Payroll with Automatic Attendance-Based Deduction
Endpoint:
- POST /payroll

Set autoFromAttendance to true.
If leaveDeduction and overtime are not sent, backend computes them automatically from attendance.

cURL:

```bash
curl -X POST "https://hr-api.cyond.com/api/v1/payroll" \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "employeeId": 11,
    "payMonth": "2026-10",
    "autoFromAttendance": true,
    "hra": 5000,
    "bonus": 0,
    "pf": 1800,
    "otherDeductions": 0
  }'
```

## 6. Suggested Frontend Wiring (Payroll Screen)
1. Add Upload Excel button in Payroll page.
2. On upload success, call monthly-summary API and refresh table/cards.
3. On employee selection + payMonth change, call auto-components API.
4. Pre-fill overtime and leave deduction fields with response values.
5. On Generate Payroll, send autoFromAttendance=true in create payroll request.

## 7. Error Handling
- 400: invalid file format, invalid month/time values.
- 401/403: token missing or role not allowed.
- 404: employee code in sheet not found.
- 409: payroll already exists for employee and month.

## 8. Role Access
- HR, ADMIN, MANAGER can access these endpoints.
- EMPLOYEE cannot upload attendance or generate payroll.

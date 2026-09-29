export type EmployeeStatus =
  | "Active"
  | "On Leave"
  | "Probation"
  | "Notice Period"
  | "Offboarding";

export type EmploymentType =
  | "Full Time"
  | "Part Time"
  | "Contract";

export interface Employee {
  id: string;
  employeeCode: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  department: string;
  designation: string;
  role: string;
  manager: string;
  employmentType: EmploymentType;
  status: EmployeeStatus;
  joiningDate: string;
  location: string;
  salary: number;
  avatarInitials: string;
}

export const employees: Employee[] = [
  {
    id: "EMP001",
    employeeCode: "CYD-001",
    firstName: "Arjun",
    lastName: "Reddy",
    email: "arjun.reddy@cyond.local",
    phone: "+91 90000 10001",
    department: "Engineering",
    designation: "Senior Software Engineer",
    role: "Employee",
    manager: "Anil Kumar",
    employmentType: "Full Time",
    status: "Active",
    joiningDate: "2023-06-12",
    location: "Hyderabad",
    salary: 1200000,
    avatarInitials: "AR",
  },
  {
    id: "EMP002",
    employeeCode: "CYD-002",
    firstName: "Priya",
    lastName: "Sharma",
    email: "priya.sharma@cyond.local",
    phone: "+91 90000 10002",
    department: "Human Resources",
    designation: "HR Manager",
    role: "HR Admin",
    manager: "Anil Kumar",
    employmentType: "Full Time",
    status: "Active",
    joiningDate: "2022-04-18",
    location: "Hyderabad",
    salary: 1100000,
    avatarInitials: "PS",
  },
  {
    id: "EMP003",
    employeeCode: "CYD-003",
    firstName: "Rahul",
    lastName: "Kumar",
    email: "rahul.kumar@cyond.local",
    phone: "+91 90000 10003",
    department: "Engineering",
    designation: "Software Engineer",
    role: "Employee",
    manager: "Arjun Reddy",
    employmentType: "Full Time",
    status: "Probation",
    joiningDate: "2026-07-01",
    location: "Bengaluru",
    salary: 750000,
    avatarInitials: "RK",
  },
  {
    id: "EMP004",
    employeeCode: "CYD-004",
    firstName: "Sneha",
    lastName: "Patel",
    email: "sneha.patel@cyond.local",
    phone: "+91 90000 10004",
    department: "Finance",
    designation: "Financial Analyst",
    role: "Employee",
    manager: "Vikram Singh",
    employmentType: "Full Time",
    status: "Active",
    joiningDate: "2024-01-15",
    location: "Hyderabad",
    salary: 850000,
    avatarInitials: "SP",
  },
  {
    id: "EMP005",
    employeeCode: "CYD-005",
    firstName: "Vikram",
    lastName: "Singh",
    email: "vikram.singh@cyond.local",
    phone: "+91 90000 10005",
    department: "Finance",
    designation: "Finance Manager",
    role: "Manager",
    manager: "Anil Kumar",
    employmentType: "Full Time",
    status: "Active",
    joiningDate: "2021-08-09",
    location: "Mumbai",
    salary: 1350000,
    avatarInitials: "VS",
  },
  {
    id: "EMP006",
    employeeCode: "CYD-006",
    firstName: "Meera",
    lastName: "Nair",
    email: "meera.nair@cyond.local",
    phone: "+91 90000 10006",
    department: "Product",
    designation: "Product Manager",
    role: "Manager",
    manager: "Anil Kumar",
    employmentType: "Full Time",
    status: "On Leave",
    joiningDate: "2022-11-21",
    location: "Pune",
    salary: 1400000,
    avatarInitials: "MN",
  },
  {
    id: "EMP007",
    employeeCode: "CYD-007",
    firstName: "Kiran",
    lastName: "Rao",
    email: "kiran.rao@cyond.local",
    phone: "+91 90000 10007",
    department: "Engineering",
    designation: "DevOps Engineer",
    role: "Employee",
    manager: "Arjun Reddy",
    employmentType: "Full Time",
    status: "Notice Period",
    joiningDate: "2023-03-06",
    location: "Hyderabad",
    salary: 1050000,
    avatarInitials: "KR",
  },
  {
    id: "EMP008",
    employeeCode: "CYD-008",
    firstName: "Ananya",
    lastName: "Das",
    email: "ananya.das@cyond.local",
    phone: "+91 90000 10008",
    department: "Sales",
    designation: "Sales Executive",
    role: "Employee",
    manager: "Rohit Mehta",
    employmentType: "Full Time",
    status: "Active",
    joiningDate: "2024-07-22",
    location: "Chennai",
    salary: 700000,
    avatarInitials: "AD",
  },
  {
    id: "EMP009",
    employeeCode: "CYD-009",
    firstName: "Rohit",
    lastName: "Mehta",
    email: "rohit.mehta@cyond.local",
    phone: "+91 90000 10009",
    department: "Sales",
    designation: "Sales Manager",
    role: "Manager",
    manager: "Anil Kumar",
    employmentType: "Full Time",
    status: "Active",
    joiningDate: "2020-10-12",
    location: "Delhi",
    salary: 1250000,
    avatarInitials: "RM",
  },
  {
    id: "EMP010",
    employeeCode: "CYD-010",
    firstName: "Anil",
    lastName: "Kumar",
    email: "anil.kumar@cyond.local",
    phone: "+91 90000 10010",
    department: "Executive",
    designation: "Chief People Officer",
    role: "HR Admin",
    manager: "Executive Board",
    employmentType: "Full Time",
    status: "Active",
    joiningDate: "2019-02-04",
    location: "Hyderabad",
    salary: 2200000,
    avatarInitials: "AK",
  },
];

export const departments = [
  "Engineering",
  "Human Resources",
  "Finance",
  "Product",
  "Sales",
  "Executive",
];

export const employeeStatuses: EmployeeStatus[] = [
  "Active",
  "On Leave",
  "Probation",
  "Notice Period",
  "Offboarding",
];

export const employmentTypes: EmploymentType[] = [
  "Full Time",
  "Part Time",
  "Contract",
];

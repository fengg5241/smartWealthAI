"""Generate English test Excel for RAG verification."""
from openpyxl import Workbook

wb = Workbook()

# --- Sheet 1: Product Profit Report ---
ws1 = wb.active
ws1.title = "Product Profit Report"
ws1.append(["Product Name", "Category", "Revenue(K)", "Cost(K)", "Profit(K)", "Margin"])
data1 = [
    ["Smartphone Pro", "Electronics", 5800.5, 3900.0, 1900.5, "32.8%"],
    ["Tablet Air", "Electronics", 3200.0, 2100.0, 1100.0, "34.4%"],
    ["Smartwatch Lite", "Wearables", 1800.0, 950.0, 850.0, "47.2%"],
    ["Wireless Earbuds", "Wearables", 2400.0, 1200.0, 1200.0, "50.0%"],
    ["Laptop Pro", "Electronics", 8600.0, 6200.0, 2400.0, "27.9%"],
    ["Smart Speaker Home", "Smart Home", 1500.0, 900.0, 600.0, "40.0%"],
    ["Router Max", "Smart Home", 800.0, 450.0, 350.0, "43.8%"],
    ["AI Camera", "Smart Home", 1200.0, 680.0, 520.0, "43.3%"],
    ["Fitness Band", "Wearables", 950.0, 480.0, 470.0, "49.5%"],
    ["Fast Charger", "Accessories", 600.0, 280.0, 320.0, "53.3%"],
    ["USB-C Cable", "Accessories", 350.0, 150.0, 200.0, "57.1%"],
    ["Power Bank", "Accessories", 780.0, 420.0, 360.0, "46.2%"],
    ["Smart Bulb", "Smart Home", 450.0, 250.0, 200.0, "44.4%"],
    ["Smart Door Lock", "Smart Home", 2200.0, 1300.0, 900.0, "40.9%"],
    ["Car Charger Dual", "Accessories", 280.0, 120.0, 160.0, "57.1%"],
]
for row in data1:
    ws1.append(row)

# --- Sheet 2: Employee Roster ---
ws2 = wb.create_sheet("Employee Roster")
ws2.append(["Name", "Department", "Position", "Hire Date", "Monthly Salary"])
data2 = [
    ["Alice Johnson", "Engineering", "Senior Engineer", "2020-03-15", 35000],
    ["Bob Smith", "Marketing", "Marketing Manager", "2019-07-01", 28000],
    ["Charlie Wang", "Engineering", "Architect", "2018-01-10", 45000],
    ["Diana Zhao", "Finance", "Finance Lead", "2021-06-20", 25000],
    ["Edward Qian", "Sales", "Sales Director", "2017-09-01", 40000],
    ["Fiona Sun", "Engineering", "Junior Engineer", "2023-01-05", 12000],
    ["George Zhou", "HR", "HR Manager", "2020-11-18", 22000],
    ["Helen Wu", "Sales", "Sales Representative", "2022-04-08", 15000],
    ["Ivan Zheng", "Engineering", "QA Engineer", "2021-08-25", 20000],
    ["Julia Feng", "Marketing", "Content Strategist", "2023-06-01", 18000],
]
for row in data2:
    ws2.append(row)

# --- Sheet 3: Quarterly Summary ---
ws3 = wb.create_sheet("Quarterly Summary")
ws3.append(["Quarter", "Total Revenue(K)", "Total Cost(K)", "Net Profit(K)", "YoY Growth"])
data3 = [
    ["2024Q1", 5200.0, 3400.0, 1800.0, "+15%"],
    ["2024Q2", 6100.0, 3900.0, 2200.0, "+22%"],
    ["2024Q3", 5800.0, 3700.0, 2100.0, "+18%"],
    ["2024Q4", 7200.0, 4500.0, 2700.0, "+31%"],
    ["2025Q1", 6400.0, 4000.0, 2400.0, "+23%"],
    ["2025Q2", 7500.0, 4600.0, 2900.0, "+32%"],
]
for row in data3:
    ws3.append(row)

# Auto-width
for ws in wb.worksheets:
    for col in ws.columns:
        max_len = 0
        col_letter = col[0].column_letter
        for cell in col:
            if cell.value:
                max_len = max(max_len, len(str(cell.value)))
        ws.column_dimensions[col_letter].width = min(max_len + 4, 30)

import os
output = "test_files/test_financial_data_en.xlsx"
os.makedirs(os.path.dirname(output), exist_ok=True)
wb.save(output)
print(f"Generated: {output}")
print(f"Sheets: {wb.sheetnames}")
for ws in wb.worksheets:
    print(f"  {ws.title}: {ws.max_row} rows x {ws.max_column} cols")

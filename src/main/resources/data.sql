delete from financial_transaction;
delete from savings_goal;
delete from user_portfolio_holding;
delete from financial_product;
delete from user_profile;

insert into user_profile (id, full_name, risk_level, available_savings_balance)
values
    (1, 'Olivia Zhao', 'CONSERVATIVE', 3000.00),
    (2, 'Lina Chen', 'MODERATE', 52000.00),
    (3, 'Ethan Xu', 'AGGRESSIVE', 98000.00)
on conflict (id) do update
set full_name = excluded.full_name,
    risk_level = excluded.risk_level,
    available_savings_balance = excluded.available_savings_balance;

insert into savings_goal (id, user_id, goal_name, target_amount, target_date)
values
    (1, 1, 'Emergency Reserve Upgrade', 90000.00, (current_date + interval '12 month')::date),
    (2, 2, 'Family Travel Fund', 120000.00, (current_date + interval '10 month')::date),
    (3, 3, 'Home Down Payment Booster', 300000.00, (current_date + interval '18 month')::date)
on conflict (id) do update
set user_id = excluded.user_id,
    goal_name = excluded.goal_name,
    target_amount = excluded.target_amount,
    target_date = excluded.target_date;

insert into financial_product (
    id, product_code, product_name, product_category, supported_risk_level, annual_return_rate,
    min_holding_days, liquidity_level, currency, minimum_investment_amount, description, compliance_note
)
values
    (
        1, 'CON-001', 'Cash Shield Income', 'CASH_MANAGEMENT', 'CONSERVATIVE', 0.0280, 30, 'HIGH', 'SGD', 1000.00,
        'Low-volatility cash management product intended for emergency reserve and principal stability needs.',
        'Suitable only for conservative risk clients. Returns are not guaranteed and liquidity depends on contract rules.'
    ),
    (
        2, 'CON-002', 'Stable Bond Ladder', 'BOND', 'CONSERVATIVE', 0.0320, 120, 'MEDIUM', 'SGD', 5000.00,
        'Investment-grade bond ladder product balancing steady income and moderate duration risk.',
        'Suitable only for conservative risk clients. Interest-rate and credit risks remain.'
    ),
    (
        3, 'CON-003', 'Treasury Plus Plan', 'FIXED_DEPOSIT', 'CONSERVATIVE', 0.0300, 90, 'HIGH', 'SGD', 10000.00,
        'Treasury and policy-bank oriented income product focused on capital preservation and predictable cash management.',
        'Suitable only for conservative risk clients. Investors should still review redemption rules and interest-rate sensitivity.'
    ),
    (
        4, 'SG9999013486', 'LIONGLOBAL SINGAPORE DIVIDEND EQUITY FUND USD-H', 'EQUITY_FUND', 'MODERATE', 0.5124, 90, 'HIGH', 'USD', 1000.00,
        '狮城全球新加坡股息权益基金（美元对冲版），聚焦新加坡股息型股票，每季度派息，股息率5.17%，适合追求稳定收益与亚洲市场增长的平衡型投资者。',
        '风险等级：平衡型（Balanced）。ESG评级：A。最低一次性投资：USD 1,000，月定投：USD 100。销售费用0.88%。注册地：新加坡。对冲版本可减少汇率风险，但不能完全消除。'
    ),
    (
        5, 'SG9999013478', 'LIONGLOBAL SINGAPORE DIVIDEND EQUITY FUND USD', 'EQUITY_FUND', 'MODERATE', 0.5085, 90, 'HIGH', 'USD', 1000.00,
        '狮城全球新加坡股息权益基金（美元版），聚焦新加坡股息型股票，每季度派息，股息率5.32%，3年回报107.74%，适合寻求亚洲市场收益与增值的平衡型投资者。',
        '风险等级：平衡型（Balanced）。ESG评级：A。最低一次性投资：USD 1,000，月定投：USD 100。销售费用0.88%。注册地：新加坡。股息水平可能随市场变化而波动。'
    ),
    (
        6, 'SG9999013460', 'LIONGLOBAL SINGAPORE DIVIDEND EQUITY FUND SGD', 'EQUITY_FUND', 'MODERATE', 0.4714, 90, 'HIGH', 'SGD', 1000.00,
        '狮城全球新加坡股息权益基金（新加坡元版），聚焦亚洲发达市场股息型股票，每季度派息，股息率5.21%，5年回报68.07%，适合新币计价的平衡型投资者。',
        '风险等级：平衡型（Balanced）。ESG评级：A。最低一次性投资：SGD 1,000，月定投：SGD 100。销售费用0.88%。注册地：新加坡。适合寻求定期收入与中长期增长的投资者。'
    ),
    (
        7, 'SG9999011415', 'LIONGLOBAL JAPAN GROWTH FUND (USD HEDGED)', 'EQUITY_FUND', 'MODERATE', 0.4585, 90, 'MEDIUM', 'USD', 1000.00,
        '狮城全球日本成长基金（美元对冲版），专注于日本股票市场，累积型权益基金，5年回报高达137.42%，适合看好日本市场长期增长的平衡型投资者。',
        '风险等级：平衡型（Balanced）。ESG评级：AA。最低一次性投资：USD 1,000，月定投：USD 100。销售费用0.88%。注册地：新加坡。日元汇率风险通过对冲部分缓解，但市场风险仍存。'
    ),
    (
        8, 'LU0548575426', 'FIDELITY EMERGING MARKETS FUND A USD', 'EQUITY_FUND', 'AGGRESSIVE', 0.6318, 90, 'MEDIUM', 'USD', 1000.00,
        '富达新兴市场基金（美元），专注于EMEA地区权益类资产，采用累积型策略，适合寻求长期资本增值的投资者。3年回报81.26%，5年回报17.23%。',
        '风险等级：成长型（Growth）。ESG评级：A。最低一次性投资：USD 1,000，月定投：USD 100。销售费用0.88%。注册地：卢森堡。投资者需承担新兴市场波动风险及汇率风险。'
    ),
    (
        9, 'LU0251143458', 'FIDELITY EMERGING MARKETS FUND A SGD', 'EQUITY_FUND', 'AGGRESSIVE', 0.5919, 90, 'MEDIUM', 'SGD', 1000.00,
        '富达新兴市场基金（新加坡元），专注于EMEA地区权益类资产，采用累积型策略，提供年化股息0.56%，适合新币计价的长期增值需求投资者。',
        '风险等级：成长型（Growth）。ESG评级：A。最低一次性投资：SGD 1,000，月定投：SGD 100。销售费用0.88%。注册地：卢森堡。投资者需承担新兴市场波动风险。'
    ),
    (
        10, 'SG9999000251', 'SCHRODER EMERGING MARKETS FUND SGD', 'EQUITY_FUND', 'AGGRESSIVE', 0.5656, 90, 'MEDIUM', 'SGD', 1000.00,
        '施罗德新兴市场基金（新加坡元），国际化权益类配置，每年派息，适合希望定期获得收益同时参与全球新兴市场增长的投资者。3年回报75.51%，5年回报23.39%。',
        '风险等级：成长型（Growth）。无ESG评级。最低一次性投资：SGD 1,000，月定投：SGD 100。销售费用0.88%。注册地：新加坡。新兴市场投资存在较高波动性风险。'
    ),
    (
        11, 'SG9999003342', 'ABRDN GLOBAL EMERGING MARKETS FUND SGD', 'EQUITY_FUND', 'AGGRESSIVE', 0.4943, 90, 'MEDIUM', 'SGD', 1000.00,
        '安本全球新兴市场基金（新加坡元），全球新兴市场权益累积型基金，ESG评级A，适合关注可持续投资并寻求长期资本增值的积极型投资者。',
        '风险等级：成长型（Growth）。ESG评级：A。最低一次性投资：SGD 1,000，月定投：SGD 100。销售费用0.88%。注册地：新加坡。新兴市场投资波动较大，需具备较强风险承受能力。'
    ),
    (
        12, 'LU0192582467', 'SCHRODER ISF ASIAN EQUITY YIELD A (DIS) USD', 'EQUITY_FUND', 'AGGRESSIVE', 0.4535, 90, 'MEDIUM', 'USD', 1000.00,
        '施罗德亚洲股息基金（派息型，美元），专注亚太（除日本）地区月度派息权益基金，股息率4%，适合希望每月获得收益并参与亚洲市场增长的投资者。',
        '风险等级：成长型（Growth）。ESG评级：A。最低一次性投资：USD 1,000，月定投：USD 100。销售费用0.88%。注册地：卢森堡。每月分红金额不保证固定，受市场表现影响。'
    ),
    (
        13, 'LU0188438110', 'SCHRODER ISF ASIAN EQUITY YIELD A(ACC) USD', 'EQUITY_FUND', 'AGGRESSIVE', 0.4535, 90, 'MEDIUM', 'USD', 1000.00,
        '施罗德亚洲股息基金（累积型，美元），专注亚太（除日本）地区权益累积基金，总资产规模USD 7.23亿，3年回报67.11%，适合追求长期资本增值的积极型投资者。',
        '风险等级：成长型（Growth）。ESG评级：A。最低一次性投资：USD 1,000，月定投：USD 100。销售费用0.88%。注册地：卢森堡。累积型基金不派发股息，收益自动再投资。'
    ),
    (
        14, 'CON-FND-001', 'Prudent Income Fund SGD', 'FUND', 'CONSERVATIVE', 0.0345, 30, 'HIGH', 'SGD', 1000.00,
        'Conservative income fund focused on short-duration deposits, treasury bills, and high-grade money-market instruments for capital stability.',
        'Suitable only for conservative risk clients. Returns are not guaranteed. Investors should review income distribution policy and redemption rules.'
    ),
    (
        15, 'CON-FND-002', 'Prudent Income Fund USD', 'FUND', 'CONSERVATIVE', 0.0340, 30, 'HIGH', 'USD', 1000.00,
        'Conservative USD income fund focused on short-duration cash instruments and high-quality fixed-income exposure with low volatility.',
        'Suitable only for conservative risk clients. USD exposure and interest-rate movements may still affect outcomes.'
    ),
    (
        16, 'CON-FND-003', 'Capital Stable Multi-Asset Fund SGD', 'MIXED_FUND', 'CONSERVATIVE', 0.0405, 120, 'MEDIUM', 'SGD', 1000.00,
        'Low-volatility multi-asset income fund combining short-duration bonds, defensive dividend assets, and liquidity reserves.',
        'Suitable only for conservative risk clients. Moderate market fluctuations remain possible and investors should assess holding-period fit.'
    )
on conflict (id) do update
set product_code = excluded.product_code,
    product_name = excluded.product_name,
    product_category = excluded.product_category,
    supported_risk_level = excluded.supported_risk_level,
    annual_return_rate = excluded.annual_return_rate,
    min_holding_days = excluded.min_holding_days,
    liquidity_level = excluded.liquidity_level,
    currency = excluded.currency,
    minimum_investment_amount = excluded.minimum_investment_amount,
    description = excluded.description,
    compliance_note = excluded.compliance_note;

insert into financial_transaction (user_id, transaction_date, transaction_type, category, amount, description)
values
    (1, (date_trunc('month', current_date - interval '5 month') + interval '2 day')::date, 'INCOME', 'Salary', 17500.00, 'Monthly salary'),
    (1, (date_trunc('month', current_date - interval '5 month') + interval '4 day')::date, 'INCOME', 'Interest', 420.00, 'Deposit interest'),
    (1, (date_trunc('month', current_date - interval '5 month') + interval '5 day')::date, 'EXPENSE', 'Housing', 5200.00, 'Rent'),
    (1, (date_trunc('month', current_date - interval '5 month') + interval '8 day')::date, 'EXPENSE', 'Food', 1650.00, 'Groceries'),
    (1, (date_trunc('month', current_date - interval '5 month') + interval '12 day')::date, 'EXPENSE', 'Medical', 680.00, 'Checkup'),
    (1, (date_trunc('month', current_date - interval '5 month') + interval '18 day')::date, 'EXPENSE', 'Family', 2100.00, 'Parents support'),
    (1, (date_trunc('month', current_date - interval '5 month') + interval '22 day')::date, 'EXPENSE', 'Transport', 420.00, 'Commute'),

    (1, (date_trunc('month', current_date - interval '4 month') + interval '2 day')::date, 'INCOME', 'Salary', 17500.00, 'Monthly salary'),
    (1, (date_trunc('month', current_date - interval '4 month') + interval '4 day')::date, 'INCOME', 'Interest', 450.00, 'Deposit interest'),
    (1, (date_trunc('month', current_date - interval '4 month') + interval '5 day')::date, 'EXPENSE', 'Housing', 5200.00, 'Rent'),
    (1, (date_trunc('month', current_date - interval '4 month') + interval '9 day')::date, 'EXPENSE', 'Food', 1720.00, 'Groceries'),
    (1, (date_trunc('month', current_date - interval '4 month') + interval '13 day')::date, 'EXPENSE', 'Insurance', 900.00, 'Medical insurance'),
    (1, (date_trunc('month', current_date - interval '4 month') + interval '18 day')::date, 'EXPENSE', 'Family', 2200.00, 'Parents support'),
    (1, (date_trunc('month', current_date - interval '4 month') + interval '23 day')::date, 'EXPENSE', 'Transport', 460.00, 'Commute'),

    (1, (date_trunc('month', current_date - interval '3 month') + interval '2 day')::date, 'INCOME', 'Salary', 17800.00, 'Monthly salary'),
    (1, (date_trunc('month', current_date - interval '3 month') + interval '4 day')::date, 'INCOME', 'Interest', 460.00, 'Deposit interest'),
    (1, (date_trunc('month', current_date - interval '3 month') + interval '5 day')::date, 'EXPENSE', 'Housing', 5200.00, 'Rent'),
    (1, (date_trunc('month', current_date - interval '3 month') + interval '9 day')::date, 'EXPENSE', 'Food', 1690.00, 'Groceries'),
    (1, (date_trunc('month', current_date - interval '3 month') + interval '12 day')::date, 'EXPENSE', 'Medical', 500.00, 'Medicine'),
    (1, (date_trunc('month', current_date - interval '3 month') + interval '18 day')::date, 'EXPENSE', 'Family', 2200.00, 'Parents support'),
    (1, (date_trunc('month', current_date - interval '3 month') + interval '24 day')::date, 'EXPENSE', 'Utilities', 620.00, 'Utilities'),

    (1, (date_trunc('month', current_date - interval '2 month') + interval '2 day')::date, 'INCOME', 'Salary', 17800.00, 'Monthly salary'),
    (1, (date_trunc('month', current_date - interval '2 month') + interval '4 day')::date, 'INCOME', 'Interest', 470.00, 'Deposit interest'),
    (1, (date_trunc('month', current_date - interval '2 month') + interval '5 day')::date, 'EXPENSE', 'Housing', 5200.00, 'Rent'),
    (1, (date_trunc('month', current_date - interval '2 month') + interval '8 day')::date, 'EXPENSE', 'Food', 1760.00, 'Groceries'),
    (1, (date_trunc('month', current_date - interval '2 month') + interval '11 day')::date, 'EXPENSE', 'Insurance', 900.00, 'Insurance'),
    (1, (date_trunc('month', current_date - interval '2 month') + interval '17 day')::date, 'EXPENSE', 'Family', 2250.00, 'Parents support'),
    (1, (date_trunc('month', current_date - interval '2 month') + interval '23 day')::date, 'EXPENSE', 'Transport', 430.00, 'Commute'),

    (1, (date_trunc('month', current_date - interval '1 month') + interval '2 day')::date, 'INCOME', 'Salary', 18000.00, 'Monthly salary'),
    (1, (date_trunc('month', current_date - interval '1 month') + interval '4 day')::date, 'INCOME', 'Interest', 480.00, 'Deposit interest'),
    (1, (date_trunc('month', current_date - interval '1 month') + interval '5 day')::date, 'EXPENSE', 'Housing', 5200.00, 'Rent'),
    (1, (date_trunc('month', current_date - interval '1 month') + interval '8 day')::date, 'EXPENSE', 'Food', 1810.00, 'Groceries'),
    (1, (date_trunc('month', current_date - interval '1 month') + interval '12 day')::date, 'EXPENSE', 'Medical', 760.00, 'Medical'),
    (1, (date_trunc('month', current_date - interval '1 month') + interval '18 day')::date, 'EXPENSE', 'Family', 2300.00, 'Parents support'),
    (1, (date_trunc('month', current_date - interval '1 month') + interval '23 day')::date, 'EXPENSE', 'Transport', 450.00, 'Commute'),

    (1, (date_trunc('month', current_date) + interval '2 day')::date, 'INCOME', 'Salary', 18000.00, 'Monthly salary'),
    (1, (date_trunc('month', current_date) + interval '4 day')::date, 'INCOME', 'Interest', 500.00, 'Deposit interest'),
    (1, (date_trunc('month', current_date) + interval '5 day')::date, 'EXPENSE', 'Housing', 5200.00, 'Rent'),
    (1, (date_trunc('month', current_date) + interval '8 day')::date, 'EXPENSE', 'Food', 1750.00, 'Groceries'),
    (1, (date_trunc('month', current_date) + interval '12 day')::date, 'EXPENSE', 'Insurance', 900.00, 'Insurance'),
    (1, (date_trunc('month', current_date) + interval '18 day')::date, 'EXPENSE', 'Family', 2300.00, 'Parents support'),
    (1, (date_trunc('month', current_date) + interval '24 day')::date, 'EXPENSE', 'Transport', 440.00, 'Commute'),

    (2, (date_trunc('month', current_date - interval '5 month') + interval '2 day')::date, 'INCOME', 'Salary', 28000.00, 'Monthly salary'),
    (2, (date_trunc('month', current_date - interval '5 month') + interval '4 day')::date, 'INCOME', 'Freelance', 2400.00, 'Consulting'),
    (2, (date_trunc('month', current_date - interval '5 month') + interval '5 day')::date, 'EXPENSE', 'Housing', 8200.00, 'Rent'),
    (2, (date_trunc('month', current_date - interval '5 month') + interval '9 day')::date, 'EXPENSE', 'Food', 2600.00, 'Dining and groceries'),
    (2, (date_trunc('month', current_date - interval '5 month') + interval '12 day')::date, 'EXPENSE', 'Transport', 820.00, 'Transport'),
    (2, (date_trunc('month', current_date - interval '5 month') + interval '17 day')::date, 'EXPENSE', 'Shopping', 2500.00, 'Shopping'),
    (2, (date_trunc('month', current_date - interval '5 month') + interval '22 day')::date, 'EXPENSE', 'Learning', 1100.00, 'Course'),

    (2, (date_trunc('month', current_date - interval '4 month') + interval '2 day')::date, 'INCOME', 'Salary', 28200.00, 'Monthly salary'),
    (2, (date_trunc('month', current_date - interval '4 month') + interval '4 day')::date, 'INCOME', 'Freelance', 2600.00, 'Consulting'),
    (2, (date_trunc('month', current_date - interval '4 month') + interval '5 day')::date, 'EXPENSE', 'Housing', 8200.00, 'Rent'),
    (2, (date_trunc('month', current_date - interval '4 month') + interval '8 day')::date, 'EXPENSE', 'Food', 2550.00, 'Dining and groceries'),
    (2, (date_trunc('month', current_date - interval '4 month') + interval '12 day')::date, 'EXPENSE', 'Transport', 840.00, 'Transport'),
    (2, (date_trunc('month', current_date - interval '4 month') + interval '17 day')::date, 'EXPENSE', 'Travel', 3200.00, 'Weekend trip'),
    (2, (date_trunc('month', current_date - interval '4 month') + interval '23 day')::date, 'EXPENSE', 'Insurance', 1800.00, 'Insurance'),

    (2, (date_trunc('month', current_date - interval '3 month') + interval '2 day')::date, 'INCOME', 'Salary', 28500.00, 'Monthly salary'),
    (2, (date_trunc('month', current_date - interval '3 month') + interval '4 day')::date, 'INCOME', 'Bonus', 2200.00, 'Project bonus'),
    (2, (date_trunc('month', current_date - interval '3 month') + interval '5 day')::date, 'EXPENSE', 'Housing', 8200.00, 'Rent'),
    (2, (date_trunc('month', current_date - interval '3 month') + interval '8 day')::date, 'EXPENSE', 'Food', 2480.00, 'Dining and groceries'),
    (2, (date_trunc('month', current_date - interval '3 month') + interval '11 day')::date, 'EXPENSE', 'Transport', 810.00, 'Transport'),
    (2, (date_trunc('month', current_date - interval '3 month') + interval '16 day')::date, 'EXPENSE', 'Shopping', 2800.00, 'Shopping'),
    (2, (date_trunc('month', current_date - interval '3 month') + interval '22 day')::date, 'EXPENSE', 'Learning', 1200.00, 'Course'),

    (2, (date_trunc('month', current_date - interval '2 month') + interval '2 day')::date, 'INCOME', 'Salary', 28500.00, 'Monthly salary'),
    (2, (date_trunc('month', current_date - interval '2 month') + interval '4 day')::date, 'INCOME', 'Freelance', 3000.00, 'Consulting'),
    (2, (date_trunc('month', current_date - interval '2 month') + interval '5 day')::date, 'EXPENSE', 'Housing', 8200.00, 'Rent'),
    (2, (date_trunc('month', current_date - interval '2 month') + interval '7 day')::date, 'EXPENSE', 'Food', 2520.00, 'Dining and groceries'),
    (2, (date_trunc('month', current_date - interval '2 month') + interval '10 day')::date, 'EXPENSE', 'Transport', 790.00, 'Transport'),
    (2, (date_trunc('month', current_date - interval '2 month') + interval '15 day')::date, 'EXPENSE', 'Travel', 1800.00, 'Trip'),
    (2, (date_trunc('month', current_date - interval '2 month') + interval '22 day')::date, 'EXPENSE', 'Learning', 1000.00, 'Course'),

    (2, (date_trunc('month', current_date - interval '1 month') + interval '2 day')::date, 'INCOME', 'Salary', 28800.00, 'Monthly salary'),
    (2, (date_trunc('month', current_date - interval '1 month') + interval '4 day')::date, 'INCOME', 'Bonus', 2500.00, 'Performance bonus'),
    (2, (date_trunc('month', current_date - interval '1 month') + interval '5 day')::date, 'EXPENSE', 'Housing', 8200.00, 'Rent'),
    (2, (date_trunc('month', current_date - interval '1 month') + interval '8 day')::date, 'EXPENSE', 'Food', 2600.00, 'Dining and groceries'),
    (2, (date_trunc('month', current_date - interval '1 month') + interval '12 day')::date, 'EXPENSE', 'Transport', 900.00, 'Transport'),
    (2, (date_trunc('month', current_date - interval '1 month') + interval '18 day')::date, 'EXPENSE', 'Shopping', 3500.00, 'Shopping'),
    (2, (date_trunc('month', current_date - interval '1 month') + interval '22 day')::date, 'EXPENSE', 'Insurance', 1800.00, 'Insurance'),

    (2, (date_trunc('month', current_date) + interval '2 day')::date, 'INCOME', 'Salary', 28800.00, 'Monthly salary'),
    (2, (date_trunc('month', current_date) + interval '4 day')::date, 'INCOME', 'Freelance', 2800.00, 'Consulting'),
    (2, (date_trunc('month', current_date) + interval '5 day')::date, 'EXPENSE', 'Housing', 8200.00, 'Rent'),
    (2, (date_trunc('month', current_date) + interval '7 day')::date, 'EXPENSE', 'Food', 2450.00, 'Dining and groceries'),
    (2, (date_trunc('month', current_date) + interval '10 day')::date, 'EXPENSE', 'Transport', 780.00, 'Transport'),
    (2, (date_trunc('month', current_date) + interval '11 day')::date, 'EXPENSE', 'Travel', 1600.00, 'Trip'),
    (2, (date_trunc('month', current_date) + interval '13 day')::date, 'EXPENSE', 'Learning', 1200.00, 'Certification course'),

    (3, (date_trunc('month', current_date - interval '5 month') + interval '2 day')::date, 'INCOME', 'Salary', 42000.00, 'Monthly salary'),
    (3, (date_trunc('month', current_date - interval '5 month') + interval '4 day')::date, 'INCOME', 'Bonus', 9500.00, 'Quarter bonus'),
    (3, (date_trunc('month', current_date - interval '5 month') + interval '5 day')::date, 'EXPENSE', 'Housing', 12800.00, 'Mortgage'),
    (3, (date_trunc('month', current_date - interval '5 month') + interval '8 day')::date, 'EXPENSE', 'Food', 4200.00, 'Dining and groceries'),
    (3, (date_trunc('month', current_date - interval '5 month') + interval '12 day')::date, 'EXPENSE', 'Travel', 3500.00, 'Travel'),
    (3, (date_trunc('month', current_date - interval '5 month') + interval '18 day')::date, 'EXPENSE', 'Investment Learning', 2600.00, 'Professional program'),
    (3, (date_trunc('month', current_date - interval '5 month') + interval '23 day')::date, 'EXPENSE', 'Entertainment', 1800.00, 'Leisure'),

    (3, (date_trunc('month', current_date - interval '4 month') + interval '2 day')::date, 'INCOME', 'Salary', 42500.00, 'Monthly salary'),
    (3, (date_trunc('month', current_date - interval '4 month') + interval '4 day')::date, 'INCOME', 'Freelance', 6000.00, 'Side project'),
    (3, (date_trunc('month', current_date - interval '4 month') + interval '5 day')::date, 'EXPENSE', 'Housing', 12800.00, 'Mortgage'),
    (3, (date_trunc('month', current_date - interval '4 month') + interval '8 day')::date, 'EXPENSE', 'Food', 4100.00, 'Dining and groceries'),
    (3, (date_trunc('month', current_date - interval '4 month') + interval '11 day')::date, 'EXPENSE', 'Travel', 4800.00, 'Travel'),
    (3, (date_trunc('month', current_date - interval '4 month') + interval '17 day')::date, 'EXPENSE', 'Shopping', 5200.00, 'Electronics'),
    (3, (date_trunc('month', current_date - interval '4 month') + interval '24 day')::date, 'EXPENSE', 'Transport', 1500.00, 'Ride and fuel'),

    (3, (date_trunc('month', current_date - interval '3 month') + interval '2 day')::date, 'INCOME', 'Salary', 43000.00, 'Monthly salary'),
    (3, (date_trunc('month', current_date - interval '3 month') + interval '4 day')::date, 'INCOME', 'Bonus', 12000.00, 'Performance bonus'),
    (3, (date_trunc('month', current_date - interval '3 month') + interval '5 day')::date, 'EXPENSE', 'Housing', 12800.00, 'Mortgage'),
    (3, (date_trunc('month', current_date - interval '3 month') + interval '8 day')::date, 'EXPENSE', 'Food', 4250.00, 'Dining and groceries'),
    (3, (date_trunc('month', current_date - interval '3 month') + interval '12 day')::date, 'EXPENSE', 'Travel', 2800.00, 'Travel'),
    (3, (date_trunc('month', current_date - interval '3 month') + interval '17 day')::date, 'EXPENSE', 'Investment Learning', 3000.00, 'Seminar'),
    (3, (date_trunc('month', current_date - interval '3 month') + interval '22 day')::date, 'EXPENSE', 'Entertainment', 2200.00, 'Social'),

    (3, (date_trunc('month', current_date - interval '2 month') + interval '2 day')::date, 'INCOME', 'Salary', 43000.00, 'Monthly salary'),
    (3, (date_trunc('month', current_date - interval '2 month') + interval '4 day')::date, 'INCOME', 'Freelance', 6800.00, 'Side project'),
    (3, (date_trunc('month', current_date - interval '2 month') + interval '5 day')::date, 'EXPENSE', 'Housing', 12800.00, 'Mortgage'),
    (3, (date_trunc('month', current_date - interval '2 month') + interval '7 day')::date, 'EXPENSE', 'Food', 4180.00, 'Dining and groceries'),
    (3, (date_trunc('month', current_date - interval '2 month') + interval '10 day')::date, 'EXPENSE', 'Travel', 5200.00, 'Travel'),
    (3, (date_trunc('month', current_date - interval '2 month') + interval '14 day')::date, 'EXPENSE', 'Shopping', 4600.00, 'Tech equipment'),
    (3, (date_trunc('month', current_date - interval '2 month') + interval '22 day')::date, 'EXPENSE', 'Transport', 1650.00, 'Fuel'),

    (3, (date_trunc('month', current_date - interval '1 month') + interval '2 day')::date, 'INCOME', 'Salary', 43800.00, 'Monthly salary'),
    (3, (date_trunc('month', current_date - interval '1 month') + interval '4 day')::date, 'INCOME', 'Bonus', 11500.00, 'Performance bonus'),
    (3, (date_trunc('month', current_date - interval '1 month') + interval '5 day')::date, 'EXPENSE', 'Housing', 12800.00, 'Mortgage'),
    (3, (date_trunc('month', current_date - interval '1 month') + interval '8 day')::date, 'EXPENSE', 'Food', 4320.00, 'Dining and groceries'),
    (3, (date_trunc('month', current_date - interval '1 month') + interval '11 day')::date, 'EXPENSE', 'Travel', 3900.00, 'Travel'),
    (3, (date_trunc('month', current_date - interval '1 month') + interval '16 day')::date, 'EXPENSE', 'Investment Learning', 3500.00, 'Workshop'),
    (3, (date_trunc('month', current_date - interval '1 month') + interval '22 day')::date, 'EXPENSE', 'Entertainment', 2400.00, 'Leisure'),

    (3, (date_trunc('month', current_date) + interval '2 day')::date, 'INCOME', 'Salary', 43800.00, 'Monthly salary'),
    (3, (date_trunc('month', current_date) + interval '4 day')::date, 'INCOME', 'Freelance', 7200.00, 'Advisory project'),
    (3, (date_trunc('month', current_date) + interval '5 day')::date, 'EXPENSE', 'Housing', 12800.00, 'Mortgage'),
    (3, (date_trunc('month', current_date) + interval '7 day')::date, 'EXPENSE', 'Food', 4280.00, 'Dining and groceries'),
    (3, (date_trunc('month', current_date) + interval '10 day')::date, 'EXPENSE', 'Travel', 4300.00, 'Travel'),
    (3, (date_trunc('month', current_date) + interval '13 day')::date, 'EXPENSE', 'Shopping', 5800.00, 'Devices'),
    (3, (date_trunc('month', current_date) + interval '18 day')::date, 'EXPENSE', 'Transport', 1700.00, 'Fuel')
on conflict do nothing;

insert into user_portfolio_holding (
    id, user_id, product_id, position_name, currency, invested_amount, current_value, units, allocation_percent, opened_at
)
values
    (1, 1, 1, 'Emergency Cash Buffer', 'SGD', 12000.00, 12180.00, 12180.0000, 55.00, (current_date - interval '14 month')::date),
    (2, 1, 2, 'Income Bond Core', 'SGD', 7000.00, 7150.00, 7150.0000, 32.00, (current_date - interval '9 month')::date),
    (3, 1, 3, 'Fixed Deposit Tranche', 'SGD', 3000.00, 3060.00, 3060.0000, 13.00, (current_date - interval '6 month')::date),

    (4, 2, 6, 'Singapore Dividend Income', 'SGD', 18000.00, 19450.00, 1420.5000, 38.00, (current_date - interval '16 month')::date),
    (5, 2, 7, 'Japan Growth Satellite', 'USD', 12000.00, 13480.00, 910.2000, 26.00, (current_date - interval '11 month')::date),
    (6, 2, 2, 'Bond Stability Sleeve', 'SGD', 9000.00, 9180.00, 9180.0000, 18.00, (current_date - interval '8 month')::date),
    (7, 2, 3, 'Fixed Deposit Reserve', 'SGD', 8000.00, 8160.00, 8160.0000, 18.00, (current_date - interval '5 month')::date),

    (8, 3, 8, 'Emerging Markets Growth', 'USD', 26000.00, 30100.00, 1880.4000, 34.00, (current_date - interval '20 month')::date),
    (9, 3, 10, 'EM Dividend Allocation', 'SGD', 18000.00, 20150.00, 1580.6000, 23.00, (current_date - interval '15 month')::date),
    (10, 3, 12, 'Asia Yield Sleeve', 'USD', 14000.00, 15120.00, 1201.9000, 17.00, (current_date - interval '10 month')::date),
    (11, 3, 2, 'Bond Shock Absorber', 'SGD', 12000.00, 12160.00, 12160.0000, 14.00, (current_date - interval '7 month')::date),
    (12, 3, 1, 'Liquidity Parking', 'SGD', 9000.00, 9135.00, 9135.0000, 12.00, (current_date - interval '4 month')::date)
on conflict (id) do update
set user_id = excluded.user_id,
    product_id = excluded.product_id,
    position_name = excluded.position_name,
    currency = excluded.currency,
    invested_amount = excluded.invested_amount,
    current_value = excluded.current_value,
    units = excluded.units,
    allocation_percent = excluded.allocation_percent,
    opened_at = excluded.opened_at;

select setval('user_profile_id_seq', greatest((select max(id) from user_profile), 1));
select setval('savings_goal_id_seq', greatest((select max(id) from savings_goal), 1));
select setval('financial_product_id_seq', greatest((select max(id) from financial_product), 1));
select setval('financial_transaction_id_seq', greatest((select max(id) from financial_transaction), 1));
select setval('user_portfolio_holding_id_seq', greatest((select max(id) from user_portfolio_holding), 1));

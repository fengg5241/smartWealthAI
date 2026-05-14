insert into user_profile (id, full_name, risk_level)
values
    (1, 'Olivia Zhao', 'CONSERVATIVE'),
    (2, 'Lina Chen', 'BALANCED'),
    (3, 'Ethan Xu', 'AGGRESSIVE')
on conflict do nothing;

insert into savings_goal (id, user_id, goal_name, target_amount, target_date)
values
    (1, 1, 'Emergency Reserve Upgrade', 90000.00, (current_date + interval '12 month')::date),
    (2, 2, 'Family Travel Fund', 120000.00, (current_date + interval '10 month')::date),
    (3, 3, 'Home Down Payment Booster', 300000.00, (current_date + interval '18 month')::date)
on conflict do nothing;

insert into financial_product (
    id, product_code, product_name, supported_risk_level, annual_return_rate,
    min_holding_days, liquidity_level, description, compliance_note
)
values
    (
        1, 'CON-001', 'Cash Shield Income', 'CONSERVATIVE', 0.0280, 30, 'HIGH',
        'Low-volatility cash management product intended for emergency reserve and principal stability needs.',
        'Suitable only for conservative risk clients. Returns are not guaranteed and liquidity depends on contract rules.'
    ),
    (
        2, 'CON-002', 'Stable Bond Ladder', 'CONSERVATIVE', 0.0320, 120, 'MEDIUM',
        'Investment-grade bond ladder product balancing steady income and moderate duration risk.',
        'Suitable only for conservative risk clients. Interest-rate and credit risks remain.'
    ),
    (
        3, 'CON-003', 'Treasury Plus Plan', 'CONSERVATIVE', 0.0300, 90, 'HIGH',
        'Treasury and policy-bank oriented income product focused on capital preservation and predictable cash management.',
        'Suitable only for conservative risk clients. Investors should still review redemption rules and interest-rate sensitivity.'
    ),
    (
        4, 'BAL-001', 'Balanced Growth Portfolio', 'BALANCED', 0.0480, 180, 'MEDIUM',
        'Hybrid allocation product combining fixed-income stability and moderate equity upside for medium-risk investors.',
        'Suitable only for balanced risk clients. Net value may fluctuate and past performance does not guarantee future returns.'
    ),
    (
        5, 'BAL-002', 'Target Saver Plus', 'BALANCED', 0.0420, 90, 'HIGH',
        'Short-to-mid term balanced product focused on liquidity and steady accumulation toward medium-term savings goals.',
        'Suitable only for balanced risk clients. Investors should review product terms and redemption rules before purchase.'
    ),
    (
        6, 'BAL-003', 'Quality Dividend Select', 'BALANCED', 0.0550, 365, 'LOW',
        'Balanced strategy emphasizing dividend quality and controlled volatility for long-horizon wealth accumulation.',
        'Suitable only for balanced risk clients. Longer holding period and market fluctuation risks apply.'
    ),
    (
        7, 'GRO-001', 'Growth Momentum Mix', 'GROWTH', 0.0720, 365, 'LOW',
        'Growth-oriented mixed allocation product designed for clients seeking stronger medium-to-long term capital appreciation.',
        'Suitable only for growth risk clients. Higher volatility and drawdown risk must be accepted.'
    ),
    (
        8, 'GRO-002', 'Innovation Leaders Fund', 'GROWTH', 0.0810, 540, 'LOW',
        'Equity-biased thematic product investing in innovation-led sectors for long-horizon asset growth.',
        'Suitable only for growth risk clients. Sector concentration and market volatility risks are material.'
    ),
    (
        9, 'AGG-001', 'Alpha Equity Opportunity', 'AGGRESSIVE', 0.1080, 540, 'LOW',
        'High-risk active equity strategy targeting aggressive capital appreciation across cyclical and emerging sectors.',
        'Suitable only for aggressive risk clients. Large short-term fluctuations and principal loss risk are significant.'
    ),
    (
        10, 'AGG-002', 'Global Tech Acceleration', 'AGGRESSIVE', 0.1250, 720, 'LOW',
        'Aggressive long-term growth product with global technology and frontier growth exposure.',
        'Suitable only for aggressive risk clients. High volatility, valuation risk and drawdown risk apply.'
    ),
    (
        11, 'AGG-003', 'Dynamic Sector Rotation', 'AGGRESSIVE', 0.0980, 365, 'MEDIUM',
        'High-beta tactical allocation product rotating among sectors to capture medium-term market opportunities.',
        'Suitable only for aggressive risk clients. Tactical losses and rapid market swings may occur.'
    )
on conflict do nothing;

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

select setval('user_profile_id_seq', greatest((select max(id) from user_profile), 1));
select setval('savings_goal_id_seq', greatest((select max(id) from savings_goal), 1));
select setval('financial_product_id_seq', greatest((select max(id) from financial_product), 1));
select setval('financial_transaction_id_seq', greatest((select max(id) from financial_transaction), 1));

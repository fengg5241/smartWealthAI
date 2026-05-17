create extension if not exists vector;

create table if not exists user_profile (
    id bigserial primary key,
    full_name varchar(100) not null,
    risk_level varchar(32) not null,
    available_savings_balance numeric(18, 2) not null default 0,
    created_at timestamp not null default current_timestamp
);

alter table user_profile
    add column if not exists available_savings_balance numeric(18, 2) not null default 0;

create table if not exists savings_goal (
    id bigserial primary key,
    user_id bigint not null references user_profile(id),
    goal_name varchar(120) not null,
    target_amount numeric(18, 2) not null,
    target_date date not null,
    created_at timestamp not null default current_timestamp
);

create table if not exists financial_product (
    id bigserial primary key,
    product_code varchar(50) not null unique,
    product_name varchar(120) not null,
    product_category varchar(32) not null default 'FUND',
    supported_risk_level varchar(32) not null,
    annual_return_rate numeric(8, 4) not null,
    min_holding_days integer not null,
    liquidity_level varchar(32) not null,
    currency varchar(10) not null default 'SGD',
    minimum_investment_amount numeric(18, 2) not null default 1000,
    description text not null,
    compliance_note text not null,
    created_at timestamp not null default current_timestamp
);

alter table financial_product
    add column if not exists product_category varchar(32) not null default 'FUND';

alter table financial_product
    add column if not exists currency varchar(10) not null default 'SGD';

alter table financial_product
    add column if not exists minimum_investment_amount numeric(18, 2) not null default 1000;

create table if not exists financial_transaction (
    id bigserial primary key,
    user_id bigint not null references user_profile(id),
    transaction_date date not null,
    transaction_type varchar(32) not null,
    category varchar(64) not null,
    amount numeric(18, 2) not null,
    description varchar(255)
);

create index if not exists idx_financial_transaction_user_date
    on financial_transaction (user_id, transaction_date);

create index if not exists idx_financial_product_risk
    on financial_product (supported_risk_level);

create table if not exists user_portfolio_holding (
    id bigserial primary key,
    user_id bigint not null references user_profile(id),
    product_id bigint not null references financial_product(id),
    position_name varchar(120) not null,
    currency varchar(10) not null,
    invested_amount numeric(18, 2) not null,
    current_value numeric(18, 2) not null,
    units numeric(18, 4),
    allocation_percent numeric(8, 2) not null,
    opened_at date not null,
    created_at timestamp not null default current_timestamp
);

create index if not exists idx_user_portfolio_holding_user
    on user_portfolio_holding (user_id);

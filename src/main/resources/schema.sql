create extension if not exists vector;

create table if not exists user_profile (
    id bigserial primary key,
    full_name varchar(100) not null,
    risk_level varchar(32) not null,
    created_at timestamp not null default current_timestamp
);

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
    supported_risk_level varchar(32) not null,
    annual_return_rate numeric(8, 4) not null,
    min_holding_days integer not null,
    liquidity_level varchar(32) not null,
    description text not null,
    compliance_note text not null,
    created_at timestamp not null default current_timestamp
);

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

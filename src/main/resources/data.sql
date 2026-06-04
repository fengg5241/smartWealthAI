INSERT INTO tenant (tenant_id, name, config) VALUES
('acme_corp', 'ACME Corp', '{"industry": "manufacturing", "plan": "enterprise"}'),
('globex_inc', 'Globex Inc', '{"industry": "technology", "plan": "pro"}')
ON CONFLICT (tenant_id) DO NOTHING;
